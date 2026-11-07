package objective03.trap

import chisel3._
import chisel3.util._
import objective03.csr.Privilege
import objective03.csr.TrapCause

// =========================================================================
// TrapController — AN32-System-v1 trap routing & delegation.
//
// Consumes raw trap events from the pipeline (exceptions: ecall, illegal
// instruction, breakpoint, misaligned access, page faults; interrupts from
// timer/external sources) plus delegation registers, and produces:
//   - trapEntry  : atomic CSR-file update + privilege switch request
//   - trapVector : handler PC (mtvec or stvec, honoring vectored mode bit0)
//   - interruptPending / interruptEnable : for WFI and observability
// =========================================================================

class TrapEventBundle extends Bundle {
  val valid      = Bool()
  val isInterrupt = Bool()
  val cause      = UInt(32.W) // exception code (no interrupt bit) or interrupt code
  val tval       = UInt(32.W)
  val epc        = UInt(32.W)
}

class TrapControllerIO extends Bundle {
  // Raw trap requests (OR of all sources, priority-resolved upstream)
  val trapEvent = Input(new TrapEventBundle)

  // CSR / state inputs (read from CSRFile outputs — wire CSRFile to expose)
  val privLevel      = Input(UInt(2.W))
  val medeleg        = Input(UInt(32.W))
  val mideleg        = Input(UInt(32.W))
  val mstatusMIE     = Input(Bool())
  val sstatusSIE     = Input(Bool())
  val mtvec          = Input(UInt(32.W))
  val stvec          = Input(UInt(32.W))
  val mip            = Input(UInt(32.W)) // machine interrupt pending
  val mie            = Input(UInt(32.W)) // machine interrupt enable
  val sip            = Input(UInt(32.W)) // supervisor interrupt pending
  val sie            = Input(UInt(32.W)) // supervisor interrupt enable

  // External interrupt request lines (timer block etc.)
  val mtipExt = Input(Bool()) // mip.MTIP level from TimerBlock
  val stipExt = Input(Bool()) // sip.STIP level from TimerBlock

  // Current retirement PC — interrupt epc must be the next instruction,
  // supplied by the pipeline (exception events carry their own epc)
  val curPc = Input(UInt(32.W))

  // Interrupt accept qualifier: interrupts are only taken at an
  // architectural retirement boundary (WB valid in SystemCore)
  val intrQualify = Input(Bool())

  // Outputs
  val trapEntry   = Output(new objective03.csr.TrapEntryBundle)
  val trapVector  = Output(UInt(32.W))
  val trapTaken   = Output(Bool())
  val anyInterruptPending = Output(Bool())
}

class TrapController extends Module {
  val io = IO(new TrapControllerIO)

  // ---- Interrupt pending composition ----
  // mip: bit7=MTIP (from timer), bit3=MSIP (software, from mip reg write)
  // mie: bit7=MTIE, bit3=MSIE
  // sip: bit5=STIP (timer delegated), bit1=SSIP
  // sie: bit5=STIE, bit1=SSIE
  // MTIP is mip bit 7: Cat(msb..lsb) -> 24 zeros | mtipExt(bit7) | 7 zeros
  val mipEff = io.mip | Cat(0.U(24.W), io.mtipExt, 0.U(7.W))
  // STIP is NOT driven by mtimecmp: M-mode handler sets it via sip write.
  val sipEff = io.sip

  // Pending+enabled per IRQ (unified machine view): mipEff & mie.
  // Delegation (mideleg) selects the TARGET privilege, not mere redirection:
  //   target = S iff mideleg[irq]=1, else M.
  // Takeable iff (priv < target)  -- higher-priv interrupt preempts lower
  //              OR (priv == target && globalIE(priv)).
  // In M, an S-target interrupt is never taken (cannot trap M->S).
  val isM = io.privLevel === Privilege.M.U(2.W)
  val isS = io.privLevel === Privilege.S.U(2.W)
  val isU = io.privLevel === Privilege.U.U(2.W)
  // globalIE for the CURRENT privilege (U has no implemented UIE -> 0)
  val curGlobalIE = Mux(isM, io.mstatusMIE, Mux(isS, io.sstatusSIE, false.B))

  def irqPend(bit: Int) = mipEff(bit) && io.mie(bit)
  def irqDeleg(bit: Int) = (io.mideleg & (1.U(32.W) << bit)) =/= 0.U

  // Each candidate: (irqBit, causeCode). Priority high->low.
  val cands = Seq(
    (TrapCause.MEIP, TrapCause.MEIP), (TrapCause.MSIP, TrapCause.MSIP),
    (TrapCause.MTIP, TrapCause.MTIP), (TrapCause.SEIP, TrapCause.SEIP),
    (TrapCause.SSIP, TrapCause.SSIP), (TrapCause.STIP, TrapCause.STIP))

  // takeable_i and targetPriv_i per candidate; select highest-priority takeable.
  // Iterate LOWEST priority first so a later (higher-priority) takeable
  // candidate overwrites the selection.
  val bestCause   = Wire(UInt(32.W))
  val bestTarget  = Wire(UInt(2.W))
  val anyTake     = Wire(Bool())
  bestCause  := 0.U
  bestTarget := Privilege.M.U(2.W)
  anyTake    := false.B
  cands.reverse.foreach { case (bit, cause) =>
    val pend = irqPend(bit)
    val deleg = irqDeleg(bit)
    val target = Mux(deleg, Privilege.S.U(2.W), Privilege.M.U(2.W))
    val privNum = io.privLevel
    val tgtNum  = target
    val belowTarget = privNum < tgtNum          // U(0)<S(1)<M(3)
    val atTargetIE  = (privNum === tgtNum) && curGlobalIE
    val takeable = pend && io.intrQualify && (belowTarget || atTargetIE)
    when(takeable) {
      bestCause  := cause.U(32.W)
      bestTarget := target
      anyTake    := true.B
    }
  }

  io.anyInterruptPending := (mipEff & io.mie) =/= 0.U

  val intrValid = anyTake
  val selectedIntrCause = bestCause
  val intrTargetPriv = bestTarget

  // ---- Exception path (synchronous, from pipeline) ----
  val exc = io.trapEvent
  val excValid = exc.valid && !exc.isInterrupt

  // Delegation: exception delegated to S iff medeleg[cause]=1 AND priv < M
  val excDelegated = excValid && io.privLevel =/= Privilege.M.U(2.W) &&
    (io.medeleg & (1.U(32.W) << exc.cause(3, 0))) =/= 0.U // delegated bit for this cause

  // Interrupt target privilege was resolved during candidate selection
  // (mideleg -> S else M). Exceptions delegate via medeleg when priv < M.
  val intrToS = intrValid && intrTargetPriv === Privilege.S.U(2.W)
  val toSupervisor = excDelegated || intrToS

  // Trap cause value: interrupt gets MSB set; exception keeps code
  val finalCause = Mux(intrValid && !excValid,
    selectedIntrCause | "h80000000".U,
    exc.cause)

  // ---- Outputs ----
  // Exception has priority over simultaneous interrupt; interrupt epc is
  // the pipeline-supplied current PC (next instruction to execute).
  val isIntrWin = intrValid && !excValid
  val trapFire = excValid || intrValid

  io.trapEntry.valid      := trapFire
  io.trapEntry.cause      := finalCause
  io.trapEntry.tval       := Mux(isIntrWin, 0.U, exc.tval)
  io.trapEntry.epc        := Mux(isIntrWin, io.curPc, exc.epc)
  io.trapEntry.targetPriv := Mux(toSupervisor, Privilege.S.U(2.W), Privilege.M.U(2.W))

  io.trapTaken := trapFire

  // Vector: MODE==1 (vectored) -> base + 4*cause for INTERRUPTS only;
  // synchronous exceptions ALWAYS target BASE regardless of MODE.
  // MODE 0 direct -> base; MODE 2/3 reserved -> AN32 defines as direct.
  val base = Mux(toSupervisor, io.stvec, io.mtvec)
  val vectoredIntr = (base(1, 0) === 1.U) && isIntrWin
  io.trapVector := (base & "hFFFFFFFC".U) +
                   Mux(vectoredIntr, selectedIntrCause << 2.U, 0.U)
}
