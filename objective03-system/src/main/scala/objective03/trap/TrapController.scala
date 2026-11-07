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
  val mipEff = io.mip | Cat(0.U(25.W), io.mtipExt, 0.U(6.W)) // set MTIP bit7
  // STIP is NOT driven by mtimecmp: M-mode handler sets it via sip write.
  val sipEff = io.sip

  val mTimerPending = mipEff(TrapCause.MTIP) && io.mie(TrapCause.MTIP)
  val sTimerPending = sipEff(TrapCause.STIP) && io.sie(TrapCause.STIP)
  val mSwPending    = mipEff(TrapCause.MSIP) && io.mie(TrapCause.MSIP)
  val sSwPending    = sipEff(TrapCause.SSIP) && io.sie(TrapCause.SSIP)

  // Pending visible at current privilege:
  //   M-mode sees mip&mie; S/U see sip&sie (only delegated bits)
  val pendingForCurrent = Mux(io.privLevel === Privilege.M.U(2.W),
    (mipEff & io.mie) =/= 0.U,
    (sipEff & io.sie) =/= 0.U)

  io.anyInterruptPending := pendingForCurrent

  // ---- Global interrupt enable ----
  val globalIE = Mux(io.privLevel === Privilege.M.U(2.W), io.mstatusMIE, io.sstatusSIE)

  // ---- Interrupt cause selection (highest priority first: MEI>MSI>MTI>SEI>SSI>STI) ----
  val intrCause = Wire(UInt(32.W))
  intrCause := 0.U
  val hasMExI = mipEff(TrapCause.MEIP) && io.mie(TrapCause.MEIP)
  val hasMSI  = mSwPending
  val hasMTI  = mTimerPending
  val hasSEI  = sipEff(TrapCause.SEIP) && io.sie(TrapCause.SEIP)
  val hasSSI  = sSwPending
  val hasSTI  = sTimerPending

  // Priority mux (arbitrary but fixed order)
  val mIntrSel = Mux(hasMExI, TrapCause.MEIP.U, Mux(hasMSI, TrapCause.MSIP.U, TrapCause.MTIP.U))
  val hasAnyMIntr = hasMExI || hasMSI || hasMTI
  val sIntrSel = Mux(hasSEI, TrapCause.SEIP.U, Mux(hasSSI, TrapCause.SSIP.U, TrapCause.STIP.U))
  val hasAnySIntr = hasSEI || hasSSI || hasSTI

  val intrValid = globalIE && pendingForCurrent && io.intrQualify &&
    Mux(io.privLevel === Privilege.M.U(2.W), hasAnyMIntr, hasAnySIntr)

  val selectedIntrCause = Mux(io.privLevel === Privilege.M.U(2.W),
    mIntrSel, sIntrSel)

  // ---- Exception path (synchronous, from pipeline) ----
  val exc = io.trapEvent
  val excValid = exc.valid && !exc.isInterrupt

  // Delegation: exception delegated to S iff medeleg[cause]=1 AND priv < M
  val excDelegated = excValid && io.privLevel =/= Privilege.M.U(2.W) &&
    (io.medeleg & (1.U(32.W) << exc.cause(3, 0))) =/= 0.U // delegated bit for this cause

  // Interrupt delegation: mideleg[irq]=1 AND priv < M
  val intrDelegated = intrValid && io.privLevel =/= Privilege.M.U(2.W) &&
    (io.mideleg & (1.U(32.W) << selectedIntrCause(3, 0))) =/= 0.U

  // Trap destination: delegated -> S, else M
  val toSupervisor = excDelegated || intrDelegated

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

  // Vector: vectored mode (bit0=1) -> base + 4*cause; else base
  val base = Mux(toSupervisor, io.stvec, io.mtvec)
  val vectored = base(0)
  val vecIndex = Mux(isIntrWin, selectedIntrCause, exc.cause)
  io.trapVector := Mux(vectored, (base & "hFFFFFFFC".U) + (vecIndex << 2.U),
                       base & "hFFFFFFFC".U)
}
