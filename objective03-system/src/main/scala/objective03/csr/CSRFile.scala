package objective03.csr

import chisel3._
import chisel3.util._
import objective03.csr.CSR._
import objective03.csr.Privilege._

// =========================================================================
// CSRFile — AN32-System-v1 privileged Control & Status Register file.
//
// Privilege legality follows the RISC-V CSR address convention:
//   csr[9:8]  = minimum privilege required (0=U,1=S,2=H,3=M)
//   csr[11:10]= access type (01 = read-only)
//   U-mode: no implemented CSR is legal.
//   S-mode: supervisor CSRs (0x100 range) RW; machine CSRs (0x300 range)
//           are ILLEGAL for both reads and writes.
//   M-mode: all implemented CSRs legal; read-only CSRs reject writes.
//
// sstatus / sie / sip are NOT independent registers: they are restricted
// views of mstatus / mie / mip. Reads return the aliased machine bits;
// writes update only the supervisor-visible bit positions.
// =========================================================================

class CSRFileIO extends Bundle {
  val csrAddr    = Input(UInt(32.W))
  val accessType = Input(UInt(2.W)) // 0=read,1=write,2=set,3=clear
  val rs1Data    = Input(UInt(32.W))
  val rdData     = Output(UInt(32.W))
  val illegal    = Output(Bool())
  val writeEn    = Input(Bool())

  val trapEntry  = Input(new TrapEntryBundle)
  val trapReturn = Input(new TrapReturnBundle)

  val privLevel       = Output(UInt(2.W))
  val interruptEnable = Output(Bool())

  // Machine-state observability for TrapController
  val mstatusOut  = Output(UInt(32.W))
  val mieOut      = Output(UInt(32.W))
  val mipOut      = Output(UInt(32.W))
  val medelegOut  = Output(UInt(32.W))
  val midelegOut  = Output(UInt(32.W))
  val mtvecOut    = Output(UInt(32.W))
  val stvecOut    = Output(UInt(32.W))
  val mepcOut     = Output(UInt(32.W))
  val sepcOut     = Output(UInt(32.W))
  val sieViewOut  = Output(UInt(32.W))
  val sipViewOut  = Output(UInt(32.W))

  // Legality probe: combinational check for an arbitrary access
  val probeAddr    = Input(UInt(32.W))
  val probeIsWrite = Input(Bool())
  val probeIllegal = Output(Bool())
}

class TrapEntryBundle extends Bundle {
  val valid      = Bool()
  val cause      = UInt(32.W)
  val tval       = UInt(32.W)
  val epc        = UInt(32.W)
  val targetPriv = UInt(2.W)
}

class TrapReturnBundle extends Bundle {
  val valid = Bool()
  val toM   = Bool()
}

class CSRFile extends Module {
  val io = IO(new CSRFileIO)

  // ---- Machine CSRs (authoritative state) ----
  val mstatus    = RegInit(0.U(32.W))
  val misa       = RegInit(Misa.RV32IMSU_VALUE.U(32.W))
  val medeleg    = RegInit(0.U(32.W))
  val mideleg    = RegInit(0.U(32.W))
  val mie        = RegInit(0.U(32.W))
  val mtvec      = RegInit(0.U(32.W))
  val mcounteren = RegInit(0.U(32.W))
  val mscratch   = RegInit(0.U(32.W))
  val mepc       = RegInit(0.U(32.W))
  val mcause     = RegInit(0.U(32.W))
  val mtval      = RegInit(0.U(32.W))
  val mip        = RegInit(0.U(32.W))

  // ---- Supervisor dedicated regs (real storage; sstatus/sie/sip are aliases) ----
  val stvec      = RegInit(0.U(32.W))
  val scounteren = RegInit(0.U(32.W))
  val sscratch   = RegInit(0.U(32.W))
  val sepc       = RegInit(0.U(32.W))
  val scause     = RegInit(0.U(32.W))
  val stval      = RegInit(0.U(32.W))
  val satp       = RegInit(0.U(32.W))

  val priv = RegInit(Privilege.M.U(2.W))

  // ---- Supervisor alias masks ----
  // sstatus view of mstatus: SIE(1), SPIE(5), SPP(8), SUM(18), MXR(19), SD(31)
  val SSTATUS_ALIAS_MASK = "h800C0322".U // bits 31,19,18,8,5,1
  // sie view of mie: SSIE(1), STIE(5), SEIE(9)
  val SIE_ALIAS_MASK     = "h00000222".U // bits 9,5,1
  // sip view of mip: SSIP(1), STIP(5), SEIP(9)
  val SIP_ALIAS_MASK     = "h00000222".U

  // Supervisor CSR reads are computed views
  def sstatusView = mstatus & SSTATUS_ALIAS_MASK
  // Supervisor interrupt views expose only delegated bits (mideleg-gated)
  def sieView     = mie & SIE_ALIAS_MASK & mideleg
  def sipView     = mip & SIP_ALIAS_MASK & mideleg

  // Supervisor CSR writes update only aliased bits of machine regs
  def aliasWrite(old: UInt, mask: UInt, data: UInt, t: UInt): UInt = {
    val wrVal = Mux(t === 1.U, data, Mux(t === 2.U, old | data, old & ~data))
    (old & ~mask) | (wrVal & mask)
  }

  // ---- Combinational read mux ----
  val rd = WireDefault(0.U(32.W))
  switch(io.csrAddr) {
    is(CSR.SSTATUS.U)    { rd := sstatusView }
    is(CSR.SIE.U)        { rd := sieView }
    is(CSR.STVEC.U)      { rd := stvec }
    is(CSR.SCOUNTEREN.U) { rd := scounteren }
    is(CSR.SSCRATCH.U)   { rd := sscratch }
    is(CSR.SEPC.U)       { rd := sepc }
    is(CSR.SCAUSE.U)     { rd := scause }
    is(CSR.STVAL.U)      { rd := stval }
    is(CSR.SIP.U)        { rd := sipView }
    is(CSR.SATP.U)       { rd := satp }
    is(CSR.MSTATUS.U)    { rd := mstatus }
    is(CSR.MISA.U)       { rd := misa }
    is(CSR.MEDELEG.U)    { rd := medeleg }
    is(CSR.MIDELEG.U)    { rd := mideleg }
    is(CSR.MIE.U)        { rd := mie }
    is(CSR.MTVEC.U)      { rd := mtvec }
    is(CSR.MCOUNTEREN.U) { rd := mcounteren }
    is(CSR.MSCRATCH.U)   { rd := mscratch }
    is(CSR.MEPC.U)       { rd := mepc }
    is(CSR.MCAUSE.U)     { rd := mcause }
    is(CSR.MTVAL.U)      { rd := mtval }
    is(CSR.MIP.U)        { rd := mip }
  }
  io.rdData := rd

  // ---- Implemented-CSR decode ----
  val a = io.csrAddr
  val isMSTATUS = a === CSR.MSTATUS.U;  val isMMEDELEG = a === CSR.MEDELEG.U
  val isMMIDELEG = a === CSR.MIDELEG.U; val isMMIE = a === CSR.MIE.U
  val isMMTVEC   = a === CSR.MTVEC.U;   val isMMISA = a === CSR.MISA.U
  val isMMCNT    = a === CSR.MCOUNTEREN.U; val isMMSCRATCH = a === CSR.MSCRATCH.U
  val isMMEPC    = a === CSR.MEPC.U;    val isMMCAUSE = a === CSR.MCAUSE.U
  val isMMTVAL   = a === CSR.MTVAL.U;   val isMMIP = a === CSR.MIP.U
  val isMachineCSR = isMSTATUS || isMMEDELEG || isMMIDELEG || isMMIE ||
    isMMTVEC || isMMISA || isMMCNT || isMMSCRATCH || isMMEPC || isMMCAUSE ||
    isMMTVAL || isMMIP

  val isSSSTATUS = a === CSR.SSTATUS.U; val isSSIE = a === CSR.SIE.U
  val isSSTVEC   = a === CSR.STVEC.U;   val isSSCNT = a === CSR.SCOUNTEREN.U
  val isSSSCRATCH= a === CSR.SSCRATCH.U;val isSSEPC = a === CSR.SEPC.U
  val isSSCAUSE  = a === CSR.SCAUSE.U;  val isSSTVAL = a === CSR.STVAL.U
  val isSSIP     = a === CSR.SIP.U;     val isSSATP = a === CSR.SATP.U
  val isSupervCSR = isSSSTATUS || isSSIE || isSSTVEC || isSSCNT || isSSSCRATCH ||
    isSSEPC || isSSCAUSE || isSSTVAL || isSSIP || isSSATP
  val knownCSR    = isMachineCSR || isSupervCSR

  // csr[11:10]: 01 => read-only. MISA is our only RO CSR.
  val isRO = io.csrAddr(11, 10) === "b01".U || io.csrAddr === CSR.MISA.U

  // csr[9:8]: minimum privilege. Machine CSRs require M; supervisor CSRs require S.
  val minPriv = Mux(isMachineCSR, Privilege.M.U(2.W),
               Mux(isSupervCSR, Privilege.S.U(2.W), 0.U(2.W)))

  val isWrite = io.accessType =/= 0.U
  // Access legal iff known && priv >= minPriv && (!write || !isRO)
  val privOK  = priv >= minPriv
  val wrOK    = !isWrite || !isRO
  io.illegal := !knownCSR || !privOK || !wrOK

  // ---- Registered CSR writes ----
  def applyW(old: UInt, t: UInt): UInt =
    Mux(t === 1.U, io.rs1Data, Mux(t === 2.U, old | io.rs1Data, old & ~io.rs1Data))

  when(io.writeEn && !io.illegal && isWrite) {
    // Supervisor aliases write through to machine state
    when(io.csrAddr === CSR.SSTATUS.U) {
      mstatus := aliasWrite(mstatus, SSTATUS_ALIAS_MASK, io.rs1Data, io.accessType)
    }
    when(io.csrAddr === CSR.SIE.U) {
      mie := aliasWrite(mie, SIE_ALIAS_MASK, io.rs1Data, io.accessType)
    }
    when(io.csrAddr === CSR.SIP.U) {
      mip := aliasWrite(mip, SIP_ALIAS_MASK, io.rs1Data, io.accessType)
    }
    // Dedicated supervisor regs
    switch(io.csrAddr) {
      is(CSR.STVEC.U)      { stvec      := io.rs1Data & "hFFFFFFFC".U }
      is(CSR.SCOUNTEREN.U) { scounteren := applyW(scounteren, io.accessType) }
      is(CSR.SSCRATCH.U)   { sscratch   := applyW(sscratch,   io.accessType) }
      is(CSR.SEPC.U)       { sepc       := io.rs1Data & "hFFFFFFFC".U }
      is(CSR.SCAUSE.U)     { scause     := applyW(scause,     io.accessType) }
      is(CSR.STVAL.U)      { stval      := applyW(stval,      io.accessType) }
      is(CSR.SATP.U)       { satp       := applyW(satp,       io.accessType) }
      // Machine regs
      is(CSR.MSTATUS.U)    { mstatus    := applyW(mstatus,    io.accessType) }
      is(CSR.MEDELEG.U)    { medeleg    := applyW(medeleg,    io.accessType) }
      is(CSR.MIDELEG.U)    { mideleg    := applyW(mideleg,    io.accessType) }
      is(CSR.MIE.U)        { mie        := applyW(mie,        io.accessType) }
      is(CSR.MTVEC.U)      { mtvec      := io.rs1Data & "hFFFFFFFC".U }
      is(CSR.MCOUNTEREN.U) { mcounteren := applyW(mcounteren, io.accessType) }
      is(CSR.MSCRATCH.U)   { mscratch   := applyW(mscratch,   io.accessType) }
      is(CSR.MEPC.U)       { mepc       := io.rs1Data & "hFFFFFFFC".U }
      is(CSR.MCAUSE.U)     { mcause     := applyW(mcause,     io.accessType) }
      is(CSR.MTVAL.U)      { mtval      := applyW(mtval,      io.accessType) }
      is(CSR.MIP.U)        { mip        := applyW(mip,        io.accessType) }
    }
  }

  // ---- Atomic trap entry ----
  when(io.trapEntry.valid) {
    when(io.trapEntry.targetPriv === Privilege.S.U(2.W)) {
      sepc   := io.trapEntry.epc
      scause := io.trapEntry.cause
      stval  := io.trapEntry.tval
      // sstatus.SPP:=oldPriv, SPIE:=SIE, SIE:=0  (aliased mstatus bits 8,5,1)
      val sppBit = Mux(priv === Privilege.S.U(2.W), 1.U, 0.U)
      val ns = mstatus & ~("h00000322".U) |
        (Cat(0.U(31.W), sppBit) << SstatusView.SPP.U) |
        (Cat(0.U(31.W), mstatus(SstatusView.SIE)) << SstatusView.SPIE.U)
      mstatus := ns
      priv    := Privilege.S.U(2.W)
    }.otherwise {
      mepc   := io.trapEntry.epc
      mcause := io.trapEntry.cause
      mtval  := io.trapEntry.tval
      // mstatus: MPIE:=MIE(3), MIE:=0, MPP:=oldPriv
      val nm = mstatus & ~("h00001888".U) |
        (Cat(0.U(30.W), priv) << MstatusBits.MPP_LSB.U) |
        (Cat(0.U(31.W), mstatus(MstatusBits.MIE)) << MstatusBits.MPIE.U)
      mstatus := nm
      priv    := Privilege.M.U(2.W)
    }
  }

  // ---- Trap return ----
  when(io.trapReturn.valid) {
    when(io.trapReturn.toM) {
      // MRET: priv:=MPP; MIE:=MPIE; MPIE:=1; MPP:=0
      val mpp = mstatus(MstatusBits.MPP + 1, MstatusBits.MPP_LSB)
      val nm = mstatus & ~("h00001888".U) |
        (Cat(0.U(31.W), mstatus(MstatusBits.MPIE)) << MstatusBits.MIE.U) |
        (1.U << MstatusBits.MPIE.U)
      mstatus := nm
      priv    := mpp
    }.otherwise {
      // SRET: priv:=SPP?S:U; SIE:=SPIE; SPIE:=1; SPP:=0  (mstatus bits 8,5,1)
      val spp = mstatus(SstatusView.SPP)
      val ns = mstatus & ~("h00000322".U) |
        (Cat(0.U(31.W), mstatus(SstatusView.SPIE)) << SstatusView.SIE.U) |
        (1.U << SstatusView.SPIE.U)
      mstatus := ns
      priv    := Mux(spp === 1.U, Privilege.S.U(2.W), Privilege.U.U(2.W))
    }
  }

  // ---- Status outputs ----
  io.privLevel := priv
  io.interruptEnable := Mux(priv === Privilege.M.U(2.W),
                            mstatus(MstatusBits.MIE),
                            mstatus(SstatusView.SIE))

  io.mstatusOut  := mstatus
  io.mieOut      := mie
  io.mipOut      := mip
  io.medelegOut  := medeleg
  io.midelegOut  := mideleg
  io.mtvecOut    := mtvec
  io.stvecOut    := stvec
  io.mepcOut     := mepc
  io.sepcOut     := sepc
  io.sieViewOut  := mie & SIE_ALIAS_MASK & mideleg
  io.sipViewOut  := mip & SIP_ALIAS_MASK & mideleg

  // Probe legality (same rules as io.illegal, on probe inputs)
  val pa = io.probeAddr
  val pIsM = pa === CSR.MSTATUS.U || pa === CSR.MEDELEG.U || pa === CSR.MIDELEG.U ||
             pa === CSR.MIE.U || pa === CSR.MTVEC.U || pa === CSR.MISA.U ||
             pa === CSR.MCOUNTEREN.U || pa === CSR.MSCRATCH.U || pa === CSR.MEPC.U ||
             pa === CSR.MCAUSE.U || pa === CSR.MTVAL.U || pa === CSR.MIP.U
  val pIsS = pa === CSR.SSTATUS.U || pa === CSR.SIE.U || pa === CSR.STVEC.U ||
             pa === CSR.SCOUNTEREN.U || pa === CSR.SSCRATCH.U || pa === CSR.SEPC.U ||
             pa === CSR.SCAUSE.U || pa === CSR.STVAL.U || pa === CSR.SIP.U || pa === CSR.SATP.U
  val pKnown = pIsM || pIsS
  val pRO = pa(11, 10) === "b01".U || pa === CSR.MISA.U
  val pMinPriv = Mux(pIsM, Privilege.M.U(2.W), Mux(pIsS, Privilege.S.U(2.W), 0.U(2.W)))
  io.probeIllegal := !pKnown || !(priv >= pMinPriv) || (io.probeIsWrite && pRO)
}

// Supervisor-view bit positions (shared with mstatus bit positions)
object SstatusView {
  val SIE  = 1
  val SPIE = 5
  val SPP  = 8
  val SUM  = 18
  val MXR  = 19
  val SD   = 31
}
