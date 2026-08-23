package objective02.pipeline

import chisel3._
import chisel3.util._
import objective02.decode._
import objective02.datapath.{BranchJumpUnit, ProgramCounter, RegisterFile}
import objective02.memory.{DataMemory, InstructionMemory}
import objective01.datapath.ALU

// =========================================================================
// Pipeline Stage Probe Bundle
// =========================================================================
class StageProbeBundle extends Bundle {
  val valid       = Output(Bool())
  val pc          = Output(UInt(32.W))
  val instruction = Output(UInt(32.W))
}

// =========================================================================
// Architectural Retirement / Commit Bundle
// =========================================================================
class CommitBundle extends Bundle {
  val valid        = Output(Bool())
  val pc           = Output(UInt(32.W))
  val instruction  = Output(UInt(32.W))
  val rd           = Output(UInt(5.W))
  val regWrite     = Output(Bool())
  val writeData    = Output(UInt(32.W))
  val memRead      = Output(Bool())
  val memReadReq   = Output(Bool())
  val memWrite     = Output(Bool())
  val memWriteReq  = Output(Bool())
  val memAddress   = Output(UInt(32.W))
  val memWriteData = Output(UInt(32.W))
  val illegal      = Output(Bool())
}

class PipelinedCoreIO extends Bundle {
  // Architectural Retirement Port (originates at WB stage)
  val commit = new CommitBundle

  // Pipeline Observability / Stage Probes (for visualization & debugging)
  val stageIF = new StageProbeBundle
  val stageID = new StageProbeBundle
  val stageEX = new StageProbeBundle
  val stageMEM = new StageProbeBundle
  val stageWB = new StageProbeBundle

  // Control Flow Visibility
  val flushIFID      = Output(Bool())
  val flushIDEX      = Output(Bool())
  val branchTaken    = Output(Bool())
  val redirectTarget = Output(UInt(32.W))
}

class PipelinedCore(
  val initialProgram: Seq[BigInt] = Seq.empty,
  val imemDepthWords: Int = 1024,
  val dmemSizeBytes: Int = 4096,
  val bootAddress: BigInt = 0x00000000L
) extends Module {
  val io = IO(new PipelinedCoreIO)

  // =========================================================================
  // 1. Submodule Instantiations
  // =========================================================================
  val pc       = Module(new ProgramCounter(bootAddress))
  val imem     = Module(new InstructionMemory(imemDepthWords, initialProgram))
  val decoder  = Module(new Decoder)
  val rf       = Module(new RegisterFile)
  val alu      = Module(new ALU(32)) // Reusing Objective 1 verified arithmetic datapath
  val bju      = Module(new BranchJumpUnit)
  val dmem     = Module(new DataMemory(dmemSizeBytes))

  // Pipeline Registers
  val ifIdReg  = Module(new IF_ID_Register)
  val idExReg  = Module(new ID_EX_Register)
  val exMemReg = Module(new EX_MEM_Register)
  val memWbReg = Module(new MEM_WB_Register)

  // Control signals for stalls and flushes
  val flushIFID = Wire(Bool())
  val flushIDEX = Wire(Bool())
  val stallIF   = WireDefault(false.B)
  val stallID   = WireDefault(false.B)
  val stallEX   = WireDefault(false.B)
  val stallMEM  = WireDefault(false.B)
  val stallWB   = WireDefault(false.B)

  // =========================================================================
  // 2. STAGE 1: INSTRUCTION FETCH (IF)
  // =========================================================================
  val programLengthBytes = if (initialProgram.nonEmpty) initialProgram.length * 4 else imemDepthWords * 4
  imem.io.address := pc.io.pc
  val ifInstruction = imem.io.instruction
  val ifValid = (pc.io.pc < programLengthBytes.U)

  ifIdReg.io.stall := stallID
  ifIdReg.io.flush := flushIFID
  ifIdReg.io.in.valid       := ifValid && !flushIFID
  ifIdReg.io.in.pc          := pc.io.pc
  ifIdReg.io.in.pcPlus4     := pc.io.pcPlus4
  ifIdReg.io.in.instruction := ifInstruction

  // =========================================================================
  // 3. STAGE 2: INSTRUCTION DECODE & OPERAND FETCH (ID)
  // =========================================================================
  val idValid       = ifIdReg.io.out.valid
  val idPc          = ifIdReg.io.out.pc
  val idPcPlus4     = ifIdReg.io.out.pcPlus4
  val idInstruction = ifIdReg.io.out.instruction

  decoder.io.instruction := idInstruction
  rf.io.rs1Address       := decoder.io.rs1
  rf.io.rs2Address       := decoder.io.rs2

  // WB -> ID Same-Cycle Register Bypass (when WB writes to rd on the same cycle ID reads it)
  val wbRegWrite = Wire(Bool())
  val wbRd       = Wire(UInt(5.W))
  val wbData     = Wire(UInt(32.W))

  val idRs1Val = Mux(wbRegWrite && (wbRd =/= 0.U) && (wbRd === decoder.io.rs1), wbData, rf.io.rs1Data)
  val idRs2Val = Mux(wbRegWrite && (wbRd =/= 0.U) && (wbRd === decoder.io.rs2), wbData, rf.io.rs2Data)

  idExReg.io.stall := stallEX
  idExReg.io.flush := flushIDEX
  idExReg.io.in.valid       := idValid && !flushIDEX
  idExReg.io.in.pc          := idPc
  idExReg.io.in.pcPlus4     := idPcPlus4
  idExReg.io.in.instruction := idInstruction
  idExReg.io.in.rs1         := decoder.io.rs1
  idExReg.io.in.rs2         := decoder.io.rs2
  idExReg.io.in.rd          := decoder.io.rd
  idExReg.io.in.rs1Data     := idRs1Val
  idExReg.io.in.rs2Data     := idRs2Val
  idExReg.io.in.imm         := decoder.io.imm
  idExReg.io.in.controls    := decoder.io.controls

  // =========================================================================
  // 4. STAGE 3: EXECUTE & BRANCH EVALUATION (EX)
  // =========================================================================
  val exValid       = idExReg.io.out.valid
  val exPc          = idExReg.io.out.pc
  val exPcPlus4     = idExReg.io.out.pcPlus4
  val exInstruction = idExReg.io.out.instruction
  val exControls    = idExReg.io.out.controls

  // Operand selection
  val operandA = MuxLookup(exControls.aluSrcA, 0.U(32.W))(Seq(
    ALUSrcA.RS1  -> idExReg.io.out.rs1Data,
    ALUSrcA.PC   -> exPc,
    ALUSrcA.ZERO -> 0.U(32.W)
  ))

  val operandB = MuxLookup(exControls.aluSrcB, 0.U(32.W))(Seq(
    ALUSrcB.RS2  -> idExReg.io.out.rs2Data,
    ALUSrcB.IMM  -> idExReg.io.out.imm,
    ALUSrcB.FOUR -> 4.U(32.W)
  ))

  // Objective 1 ALU
  alu.io.a      := operandA
  alu.io.b      := operandB
  alu.io.opcode := exControls.aluOp

  // Branch and Jump evaluation
  bju.io.pc         := exPc
  bju.io.rs1Data    := idExReg.io.out.rs1Data
  bju.io.rs2Data    := idExReg.io.out.rs2Data
  bju.io.imm        := idExReg.io.out.imm
  bju.io.branchType := exControls.branchType
  bju.io.jumpType   := exControls.jumpType

  val branchTaken    = exValid && bju.io.taken
  val redirectTarget = bju.io.targetAddress

  // Connect PC redirect and control flush
  pc.io.stall            := stallIF
  pc.io.jumpBranchTaken  := branchTaken
  pc.io.jumpBranchTarget := redirectTarget

  flushIFID := branchTaken
  flushIDEX := branchTaken

  exMemReg.io.stall := stallMEM
  exMemReg.io.flush := false.B // No flush needed in MEM
  exMemReg.io.in.valid              := exValid
  exMemReg.io.in.pc                 := exPc
  exMemReg.io.in.pcPlus4            := exPcPlus4
  exMemReg.io.in.instruction        := exInstruction
  exMemReg.io.in.rd                 := idExReg.io.out.rd
  exMemReg.io.in.aluResult          := alu.io.result
  exMemReg.io.in.rs2Data            := idExReg.io.out.rs2Data
  exMemReg.io.in.imm                := idExReg.io.out.imm
  exMemReg.io.in.regWrite           := exControls.regWrite
  exMemReg.io.in.memRead            := exControls.memRead
  exMemReg.io.in.memWrite           := exControls.memWrite
  exMemReg.io.in.memWidth           := exControls.memWidth
  exMemReg.io.in.wbSource           := exControls.wbSource
  exMemReg.io.in.illegalInstruction := exControls.illegalInstruction

  // =========================================================================
  // 5. STAGE 4: MEMORY ACCESS (MEM)
  // =========================================================================
  val memValid       = exMemReg.io.out.valid
  val memPc          = exMemReg.io.out.pc
  val memPcPlus4     = exMemReg.io.out.pcPlus4
  val memInstruction = exMemReg.io.out.instruction

  dmem.io.address   := exMemReg.io.out.aluResult
  dmem.io.writeData := exMemReg.io.out.rs2Data
  dmem.io.memRead   := memValid && exMemReg.io.out.memRead
  dmem.io.memWrite  := memValid && exMemReg.io.out.memWrite
  dmem.io.memWidth  := exMemReg.io.out.memWidth

  memWbReg.io.stall := stallWB
  memWbReg.io.flush := false.B
  memWbReg.io.in.valid              := memValid
  memWbReg.io.in.pc                 := memPc
  memWbReg.io.in.pcPlus4            := memPcPlus4
  memWbReg.io.in.instruction        := memInstruction
  memWbReg.io.in.rd                 := exMemReg.io.out.rd
  memWbReg.io.in.aluResult          := exMemReg.io.out.aluResult
  memWbReg.io.in.memReadData        := dmem.io.readData
  memWbReg.io.in.imm                := exMemReg.io.out.imm
  memWbReg.io.in.memRead            := memValid && exMemReg.io.out.memRead && !dmem.io.misaligned
  memWbReg.io.in.memReadReq         := memValid && exMemReg.io.out.memRead
  memWbReg.io.in.memWrite           := memValid && exMemReg.io.out.memWrite && !dmem.io.misaligned
  memWbReg.io.in.memWriteReq        := memValid && exMemReg.io.out.memWrite
  memWbReg.io.in.memAddress         := exMemReg.io.out.aluResult
  memWbReg.io.in.memWriteData       := exMemReg.io.out.rs2Data
  memWbReg.io.in.regWrite           := exMemReg.io.out.regWrite && !dmem.io.misaligned
  memWbReg.io.in.wbSource           := exMemReg.io.out.wbSource
  memWbReg.io.in.illegalInstruction := exMemReg.io.out.illegalInstruction

  // =========================================================================
  // 6. STAGE 5: WRITEBACK & RETIREMENT (WB)
  // =========================================================================
  val wbValid       = memWbReg.io.out.valid
  val wbPc          = memWbReg.io.out.pc
  val wbInstruction = memWbReg.io.out.instruction

  wbData := MuxLookup(memWbReg.io.out.wbSource, 0.U(32.W))(Seq(
    WBSource.ALU       -> memWbReg.io.out.aluResult,
    WBSource.MEM       -> memWbReg.io.out.memReadData,
    WBSource.PC_PLUS_4 -> memWbReg.io.out.pcPlus4,
    WBSource.IMM       -> memWbReg.io.out.imm
  ))

  wbRd       := memWbReg.io.out.rd
  wbRegWrite := wbValid && memWbReg.io.out.regWrite && (memWbReg.io.out.rd =/= 0.U)

  rf.io.rdAddress   := wbRd
  rf.io.writeData   := wbData
  rf.io.writeEnable := wbRegWrite

  // =========================================================================
  // 7. Architectural Commit / Retirement Interface
  // =========================================================================
  io.commit.valid        := wbValid
  io.commit.pc           := wbPc
  io.commit.instruction  := wbInstruction
  io.commit.rd           := wbRd
  io.commit.regWrite     := wbRegWrite
  io.commit.writeData    := wbData
  io.commit.memRead      := memWbReg.io.out.memRead
  io.commit.memReadReq   := memWbReg.io.out.memReadReq
  io.commit.memWrite     := memWbReg.io.out.memWrite
  io.commit.memWriteReq  := memWbReg.io.out.memWriteReq
  io.commit.memAddress   := memWbReg.io.out.memAddress
  io.commit.memWriteData := memWbReg.io.out.memWriteData
  io.commit.illegal      := memWbReg.io.out.illegalInstruction

  // =========================================================================
  // 8. Pipeline Observability Probes
  // =========================================================================
  io.stageIF.valid       := ifValid
  io.stageIF.pc          := pc.io.pc
  io.stageIF.instruction := ifInstruction

  io.stageID.valid       := idValid
  io.stageID.pc          := idPc
  io.stageID.instruction := idInstruction

  io.stageEX.valid       := exValid
  io.stageEX.pc          := exPc
  io.stageEX.instruction := exInstruction

  io.stageMEM.valid       := memValid
  io.stageMEM.pc          := memPc
  io.stageMEM.instruction := memInstruction

  io.stageWB.valid       := wbValid
  io.stageWB.pc          := wbPc
  io.stageWB.instruction := wbInstruction

  io.flushIFID      := flushIFID
  io.flushIDEX      := flushIDEX
  io.branchTaken    := branchTaken
  io.redirectTarget := redirectTarget
}
