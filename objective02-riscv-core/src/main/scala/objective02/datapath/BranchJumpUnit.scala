package objective02.datapath

import chisel3._
import chisel3.util._
import objective02.decode.{BranchType, JumpType}

class BranchJumpUnitIO extends Bundle {
  val pc            = Input(UInt(32.W))
  val rs1Data       = Input(UInt(32.W))
  val rs2Data       = Input(UInt(32.W))
  val imm           = Input(UInt(32.W))
  val branchType    = Input(UInt(3.W))
  val jumpType      = Input(UInt(2.W))

  val taken         = Output(Bool())
  val targetAddress = Output(UInt(32.W))
}

class BranchJumpUnit extends Module {
  val io = IO(new BranchJumpUnitIO)

  // 1. Evaluate Branch Condition
  val branchTaken = WireDefault(false.B)

  val eq  = io.rs1Data === io.rs2Data
  val ne  = !eq
  val lt  = io.rs1Data.asSInt < io.rs2Data.asSInt
  val ge  = !lt
  val ltu = io.rs1Data < io.rs2Data
  val geu = !ltu

  switch(io.branchType) {
    is(BranchType.BEQ)  { branchTaken := eq }
    is(BranchType.BNE)  { branchTaken := ne }
    is(BranchType.BLT)  { branchTaken := lt }
    is(BranchType.BGE)  { branchTaken := ge }
    is(BranchType.BLTU) { branchTaken := ltu }
    is(BranchType.BGEU) { branchTaken := geu }
  }

  // 2. Evaluate Unconditional Jumps
  val isJump = io.jumpType === JumpType.JAL || io.jumpType === JumpType.JALR

  io.taken := branchTaken || isJump

  // 3. Compute Target Address
  // JALR: (rs1 + imm) & ~1 (clears LSB to maintain alignment)
  // Branch / JAL: pc + imm
  val jalrTarget = (io.rs1Data + io.imm) & "hFFFFFFFE".U(32.W)
  val pcTarget   = io.pc + io.imm

  io.targetAddress := Mux(io.jumpType === JumpType.JALR, jalrTarget, pcTarget)
}
