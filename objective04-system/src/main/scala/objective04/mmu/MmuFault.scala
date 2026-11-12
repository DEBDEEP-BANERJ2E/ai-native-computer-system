package objective04.mmu

import chisel3._
import chisel3.util._

/** MMU fault delivery (O4-F).
  *
  * Captures the architectural context of a translation fault so the O3 trap
  * path can raise it with correct sepc/scause/stval. The PTW already produces
  * the cause; this stage latches the faulting PC and VA and presents them in
  * the trap-frame format.
  *
  *   inst page fault = 12, load = 13, store = 15, access = 1
  *   stval = faulting VA, sepc = faulting instruction PC
  */
class MmuFault extends Module {

  val io = IO(new Bundle {
    // PTW fault inputs
    val ptwFault  = Input(Bool())
    val ptwCause  = Input(UInt(6.W))
    val ptwVa     = Input(UInt(32.W))   // faulting VA -> stval
    val curPc     = Input(UInt(32.W))   // faulting PC -> sepc
    val curPrivU  = Input(Bool())
    // registered trap-frame outputs (consumed by O3 trap entry)
    val pending   = Output(Bool())
    val sepc      = Output(UInt(32.W))
    val scause    = Output(UInt(32.W))  // cause | interrupt-bit=0 (sync exc)
    val stval     = Output(UInt(32.W))
    val privU     = Output(Bool())
    val clear     = Input(Bool())       // trap taken
  })

  val pend = RegInit(false.B)
  val sEpc = Reg(UInt(32.W))
  val sCause = Reg(UInt(32.W))
  val sTval = Reg(UInt(32.W))
  val sPriv = Reg(Bool())

  when(io.ptwFault && !pend) {
    pend := true.B
    sEpc := io.curPc
    sCause := Cat(0.U(26.W), io.ptwCause)   // synchronous exception, no int bit
    sTval := io.ptwVa
    sPriv := io.curPrivU
  }
  when(io.clear) { pend := false.B }

  io.pending := pend
  io.sepc := sEpc
  io.scause := sCause
  io.stval := sTval
  io.privU := sPriv
}
