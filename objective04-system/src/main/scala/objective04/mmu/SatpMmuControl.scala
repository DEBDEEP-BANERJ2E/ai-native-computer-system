package objective04.mmu

import chisel3._
import chisel3.util._

/** satp + ASID + SFENCE.VMA semantics (O4-E).
  *
  * Models the RV32 satp CSR and the SFENCE.VMA instruction's TLB effect so the
  * privileged architecture (O3 CSRFile) can delegate real translation state.
  *
  *   satp (RV32, 32-bit): MODE[31:30] | ASID[29:22] | PPN[21:0]
  *     MODE: 0=bare 1=Sv32
  *     ASID: address-space id (8b)
  *     PPN : root page-table physical frame
  *
  *   SFENCE.VMA rs1, rs2  (rs1=VA|x0, rs2=ASID|x0)
  *     both x0      -> invalidate all non-global entries
  *     rs1=VA,rs2=x0-> invalidate that VA in current/all ASIDs
  *     rs1=x0,rs2=asid -> invalidate that ASID
  *     rs1=VA,rs2=asid -> invalidate that VA+ASID
  */
class SatpMmuControl extends Module {

  val io = IO(new Bundle {
    // CSR write port (from privileged CSRFile)
    val csrWrite = Input(Bool())
    val csrData  = Input(UInt(32.W))
    // SFENCE.VMA operands (already read from rs1/rs2 by the core)
    val sfence   = Input(Bool())
    val sfVa     = Input(UInt(32.W))   // rs1 value (VA) or all-ones marker
    val sfVaAll  = Input(Bool())       // rs1 == x0
    val sfAsid   = Input(UInt(8.W))    // rs2 value
    val sfAsidAll= Input(Bool())       // rs2 == x0
    // TLB invalidate outputs (to Tlb.io.inv*)
    val tlbInv   = Output(Bool())
    val tlbInvAsid = Output(UInt(16.W))
    val tlbInvVpn  = Output(UInt(20.W))
    val tlbInvAll  = Output(Bool())
    // current translation context for the walker/TLB
    val mode     = Output(UInt(2.W))
    val asid     = Output(UInt(8.W))
    val rootPpn  = Output(UInt(22.W))
    val vmEnable = Output(Bool())
  })

  // satp register
  val satp = RegInit(0.U(32.W))
  when(io.csrWrite) { satp := io.csrData }

  // field decode: MODE[31:30] ASID[29:22] PPN[21:0]
  val mode  = satp(31, 30)
  val asid  = satp(29, 22)
  val ppn   = satp(21, 0)

  io.mode    := mode
  io.asid    := asid
  io.rootPpn := ppn
  io.vmEnable := mode === 1.U   // Sv32 active

  // SFENCE.VMA decode -> TLB invalidate
  // vpn for TLB match = VA[31:12]
  val sfVpn = io.sfVa(31, 12)
  // Determine invalidate scope
  val bothAll = io.sfVaAll && io.sfAsidAll
  val vaOnly  = !io.sfVaAll && io.sfAsidAll
  val asidOnly= io.sfVaAll && !io.sfAsidAll

  io.tlbInv    := io.sfence
  io.tlbInvAll := bothAll
  // ASID-targeted flush uses the sfence ASID operand
  io.tlbInvAsid := Mux(asidOnly || (!io.sfVaAll && !io.sfAsidAll),
                       Cat(0.U(8.W), io.sfAsid), Cat(0.U(8.W), asid))
  // VPN-targeted flush uses the VA's vpn (0 when VA-all)
  io.tlbInvVpn  := Mux(io.sfVaAll, 0.U(20.W), sfVpn)
}
