package objective03.pipeline

import chisel3._
import chisel3.util._

// =========================================================================
// System sideband pipeline registers.
//
// The frozen objective02 stage bundles cannot carry privileged metadata,
// so System-v1 system-op state travels in this parallel register chain,
// clocked by the same stall/flush controls as the main pipeline.
//
// CSR semantics are committed at WB (single retirement point): the sideband
// carries only decoded metadata — address, op, write intent, and the
// resolved data operand — so CSR RMW is naturally program-ordered.
// =========================================================================

class SystemPipeBundle extends Bundle {
  val valid       = Bool()
  val sysOp       = UInt(4.W)  // SysOp.* (0 = not a system op)
  val csrAddr     = UInt(12.W)
  val csrWrite    = Bool()      // architectural write intent
  val csrData     = UInt(32.W)  // rs1 value or zimm
  val csrIsImm    = Bool()      // true => csrData is zimm (no forwarding needed)
  val isEcall     = Bool()
  val isEbreak    = Bool()
  val isMret      = Bool()
  val isSret      = Bool()
  val isSfenceVma = Bool()
  val isFenceI    = Bool()
  val isFence     = Bool()
  val sfenceRs1   = UInt(5.W)
  val sfenceRs2   = UInt(5.W)
  val isSys       = Bool()      // any system instruction (serialization marker)
}

object SystemPipe {
  def nop(): SystemPipeBundle = {
    val b = Wire(new SystemPipeBundle)
    b.valid       := false.B
    b.sysOp       := 0.U
    b.csrAddr     := 0.U
    b.csrWrite    := false.B
    b.csrData     := 0.U
    b.csrIsImm    := false.B
    b.isEcall     := false.B
    b.isEbreak    := false.B
    b.isMret      := false.B
    b.isSret      := false.B
    b.isSfenceVma := false.B
    b.isFenceI    := false.B
    b.isFence     := false.B
    b.sfenceRs1   := 0.U
    b.sfenceRs2   := 0.U
    b.isSys       := false.B
    b
  }
}

// Generic 1-stage system sideband register
class SystemPipeReg extends Module {
  val io = IO(new Bundle {
    val stall = Input(Bool())
    val flush = Input(Bool())
    val in    = Input(new SystemPipeBundle)
    val out   = Output(new SystemPipeBundle)
  })

  val reg = RegInit(SystemPipe.nop())

  when(io.flush) {
    reg := SystemPipe.nop()
  }.elsewhen(!io.stall) {
    reg := io.in
  }

  io.out := reg
}
