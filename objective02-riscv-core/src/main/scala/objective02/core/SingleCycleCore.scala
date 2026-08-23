package objective02.core

import chisel3._
import chisel3.util._
import objective02.decode._
import objective02.datapath.{BranchJumpUnit, ProgramCounter, RegisterFile}
import objective02.memory.{DataMemory, InstructionMemory}
import objective01.datapath.ALU

class SingleCycleCoreIO extends Bundle {
  // Architectural Commit & Debug Interface
  val debugPc           = Output(UInt(32.W))
  val debugInstruction  = Output(UInt(32.W))
  val debugRd           = Output(UInt(5.W))
  val debugWriteData    = Output(UInt(32.W))
  val debugRegWrite     = Output(Bool())
  val debugIllegal      = Output(Bool())
  val debugMemRead      = Output(Bool())
  val debugMemWrite     = Output(Bool())
  val debugMemAddress   = Output(UInt(32.W))
  val debugMemWriteData = Output(UInt(32.W))
  val debugMemReadData  = Output(UInt(32.W))
}

class SingleCycleCore(
  val initialProgram: Seq[BigInt] = Seq.empty,
  val imemDepthWords: Int = 1024,
  val dmemSizeBytes: Int = 4096,
  val bootAddress: BigInt = 0x00000000L
) extends Module {
  val io = IO(new SingleCycleCoreIO)

  // -------------------------------------------------------------
  // 1. Submodule Instantiations
  // -------------------------------------------------------------
  val pc      = Module(new ProgramCounter(bootAddress))
  val imem    = Module(new InstructionMemory(imemDepthWords, initialProgram))
  val decoder = Module(new Decoder)
  val rf      = Module(new RegisterFile)
  val alu     = Module(new ALU(32)) // Reusing Objective 1's verified arithmetic core
  val bju     = Module(new BranchJumpUnit)
  val dmem    = Module(new DataMemory(dmemSizeBytes))

  // -------------------------------------------------------------
  // 2. Instruction Fetch & Decode
  // -------------------------------------------------------------
  imem.io.address        := pc.io.pc
  decoder.io.instruction := imem.io.instruction

  // -------------------------------------------------------------
  // 3. Register File Read
  // -------------------------------------------------------------
  rf.io.rs1Address := decoder.io.rs1
  rf.io.rs2Address := decoder.io.rs2

  // -------------------------------------------------------------
  // 4. Operand Muxing
  // -------------------------------------------------------------
  val operandA = MuxLookup(decoder.io.controls.aluSrcA, 0.U(32.W))(Seq(
    ALUSrcA.RS1  -> rf.io.rs1Data,
    ALUSrcA.PC   -> pc.io.pc,
    ALUSrcA.ZERO -> 0.U(32.W)
  ))

  val operandB = MuxLookup(decoder.io.controls.aluSrcB, 0.U(32.W))(Seq(
    ALUSrcB.RS2  -> rf.io.rs2Data,
    ALUSrcB.IMM  -> decoder.io.imm,
    ALUSrcB.FOUR -> 4.U(32.W)
  ))

  // -------------------------------------------------------------
  // 5. Execution (Objective 1 ALU & BranchJumpUnit)
  // -------------------------------------------------------------
  alu.io.a      := operandA
  alu.io.b      := operandB
  alu.io.opcode := decoder.io.controls.aluOp

  bju.io.pc         := pc.io.pc
  bju.io.rs1Data    := rf.io.rs1Data
  bju.io.rs2Data    := rf.io.rs2Data
  bju.io.imm        := decoder.io.imm
  bju.io.branchType := decoder.io.controls.branchType
  bju.io.jumpType   := decoder.io.controls.jumpType

  // PC next address selection
  pc.io.stall            := false.B // Single-cycle architectural core does not stall
  pc.io.jumpBranchTaken  := bju.io.taken
  pc.io.jumpBranchTarget := bju.io.targetAddress

  // -------------------------------------------------------------
  // 6. Data Memory Access
  // -------------------------------------------------------------
  dmem.io.address   := alu.io.result
  dmem.io.writeData := rf.io.rs2Data
  dmem.io.memRead   := decoder.io.controls.memRead
  dmem.io.memWrite  := decoder.io.controls.memWrite
  dmem.io.memWidth  := decoder.io.controls.memWidth

  // -------------------------------------------------------------
  // 7. Writeback Selection & Register File Write
  // -------------------------------------------------------------
  val writebackData = MuxLookup(decoder.io.controls.wbSource, 0.U(32.W))(Seq(
    WBSource.ALU       -> alu.io.result,
    WBSource.MEM       -> dmem.io.readData,
    WBSource.PC_PLUS_4 -> pc.io.pcPlus4,
    WBSource.IMM       -> decoder.io.imm
  ))

  rf.io.rdAddress   := decoder.io.rd
  rf.io.writeData   := writebackData
  // In case of memory misalignment on load/store, suppress writeback
  rf.io.writeEnable := decoder.io.controls.regWrite && !dmem.io.misaligned

  // -------------------------------------------------------------
  // 8. Architectural Commit / Debug Visibility
  // -------------------------------------------------------------
  io.debugPc           := pc.io.pc
  io.debugInstruction  := imem.io.instruction
  io.debugRd           := decoder.io.rd
  io.debugWriteData    := writebackData
  io.debugRegWrite     := rf.io.writeEnable
  io.debugIllegal      := decoder.io.controls.illegalInstruction
  io.debugMemRead      := decoder.io.controls.memRead
  io.debugMemWrite     := decoder.io.controls.memWrite
  io.debugMemAddress   := alu.io.result
  io.debugMemWriteData := rf.io.rs2Data
  io.debugMemReadData  := dmem.io.readData
}
