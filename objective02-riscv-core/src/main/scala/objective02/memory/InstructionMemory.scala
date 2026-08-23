package objective02.memory

import chisel3._
import chisel3.util._

class InstructionMemoryIO extends Bundle {
  val address     = Input(UInt(32.W))
  val instruction = Output(UInt(32.W))
}

class InstructionMemory(val depthWords: Int = 1024, val initialProgram: Seq[BigInt] = Seq.empty) extends Module {
  val io = IO(new InstructionMemoryIO)

  // NOP: addi x0, x0, 0
  val NOP = "h00000013".U(32.W)

  // Pad initial program to depthWords with NOPs
  val programPadded: Seq[BigInt] = if (initialProgram.length >= depthWords) {
    initialProgram.take(depthWords)
  } else {
    initialProgram ++ Seq.fill(depthWords - initialProgram.length)(BigInt(0x00000013L))
  }

  // Combinational instruction ROM array initialized with preloaded program
  val mem = VecInit(programPadded.map(_.U(32.W)))

  // Word-aligned index (address[31:2])
  val wordIndex = io.address(31, 2)

  // Guard against out-of-bounds address access
  when(wordIndex < depthWords.U) {
    io.instruction := mem(wordIndex)
  }.otherwise {
    io.instruction := NOP
  }
}
