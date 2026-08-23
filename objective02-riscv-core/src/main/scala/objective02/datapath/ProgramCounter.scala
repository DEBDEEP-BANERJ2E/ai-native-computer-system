package objective02.datapath

import chisel3._

class ProgramCounterIO extends Bundle {
  val stall            = Input(Bool())
  val jumpBranchTaken  = Input(Bool())
  val jumpBranchTarget = Input(UInt(32.W))
  val pc               = Output(UInt(32.W))
  val pcPlus4          = Output(UInt(32.W))
}

class ProgramCounter(bootAddress: BigInt = 0x00000000L) extends Module {
  val io = IO(new ProgramCounterIO)

  val pcReg = RegInit(bootAddress.U(32.W))

  val nextPc = Wire(UInt(32.W))

  when(io.stall) {
    nextPc := pcReg
  }.elsewhen(io.jumpBranchTaken) {
    nextPc := io.jumpBranchTarget
  }.otherwise {
    nextPc := pcReg + 4.U(32.W)
  }

  pcReg := nextPc

  io.pc := pcReg
  io.pcPlus4 := pcReg + 4.U(32.W)
}
