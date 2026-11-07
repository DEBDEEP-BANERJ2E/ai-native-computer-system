package objective03.memory

import chisel3._
import chisel3.util._

// =========================================================================
// WritableInstructionMemory — runtime-loadable instruction memory for
// AN32-System-v1. Unlike the frozen Bare-v1 InstructionMemory (elaboration
// ROM), this is a Reg-backed array with an explicit load port so the
// platform/loader can write instructions at runtime (Stage B+).
// =========================================================================

class WritableInstructionMemoryIO extends Bundle {
  // Fetch port (combinational read, IF stage)
  val address     = Input(UInt(32.W))
  val instruction = Output(UInt(32.W))

  // Load port (platform/loader writes one word per cycle)
  val loadAddr    = Input(UInt(32.W))
  val loadData    = Input(UInt(32.W))
  val loadEn      = Input(Bool())
  val loadDone    = Output(Bool())
}

class WritableInstructionMemory(val depthWords: Int = 4096) extends Module {
  val io = IO(new WritableInstructionMemoryIO)

  val mem = RegInit(VecInit(Seq.fill(depthWords)(0.U(32.W))))

  val wordIndex = io.address >> 2
  val inBounds  = wordIndex < depthWords.U

  // Combinational fetch with NOP default (matches Bare-v1 semantics)
  val NOP = "h00000013".U(32.W)
  io.instruction := Mux(inBounds, mem(wordIndex), NOP)

  // Synchronous write port
  val loadIndex = io.loadAddr >> 2
  when(io.loadEn && (loadIndex < depthWords.U)) {
    mem(loadIndex) := io.loadData
  }
  io.loadDone := io.loadEn
}
