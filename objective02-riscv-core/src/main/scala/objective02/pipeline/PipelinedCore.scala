package objective02.pipeline

import chisel3._
import chisel3.util._
import objective02.decode._
import objective02.datapath.{BranchJumpUnit, ProgramCounter, RegisterFile}
import objective02.memory.{DataMemory, InstructionMemory}
import objective02.execute.{RV32MMultiplier, IterativeDivider}
import objective02.system.{SystemMMIO, SecurityViolationEvent, AccessType, SecurityReason}
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

  // Forwarding & Hazard Observability
  val forwardA       = Output(UInt(2.W))
  val forwardB       = Output(UInt(2.W))
  val loadUseHazard  = Output(Bool())
  val stallIF        = Output(Bool())
  val stallID        = Output(Bool())

  // M-extension Observability
  val mOp              = Output(UInt(4.W))
  val mulActive        = Output(Bool())
  val dividerBusy      = Output(Bool())
  val dividerDone      = Output(Bool())
  val dividerIteration = Output(UInt(6.W))

  // System & Cross-Layer Interface Observability
  val schedHint            = Output(UInt(32.W))
  val processBehaviorClass = Output(UInt(32.W))
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
  val pc             = Module(new ProgramCounter(bootAddress))
  val imem           = Module(new InstructionMemory(imemDepthWords, initialProgram))
  val decoder        = Module(new Decoder(enableFullM = true))
  val rf             = Module(new RegisterFile)
  val alu            = Module(new ALU(32)) // Reusing Objective 1 verified arithmetic datapath
  val bju            = Module(new BranchJumpUnit)
  val dmem           = Module(new DataMemory(dmemSizeBytes))
  val systemMMIO     = Module(new SystemMMIO)
  val forwardingUnit = Module(new ForwardingUnit)
  val hazardUnit     = Module(new HazardUnit)
  val rv32mMult      = Module(new RV32MMultiplier)
  val divRem         = Module(new IterativeDivider)

  // Pipeline Registers
  val ifIdReg  = Module(new IF_ID_Register)
  val idExReg  = Module(new ID_EX_Register)
  val exMemReg = Module(new EX_MEM_Register)
  val memWbReg = Module(new MEM_WB_Register)

  // Forward declarations for control signals
  val branchTaken    = Wire(Bool())
  val redirectTarget = Wire(UInt(32.W))
  val wbRegWrite     = Wire(Bool())
  val wbRd           = Wire(UInt(5.W))
  val wbData         = Wire(UInt(32.W))

  // =========================================================================
  // 2. HAZARD DETECTION (ID Stage Consumer vs EX Stage Load Producer)
  // =========================================================================
  val idValid       = ifIdReg.io.out.valid
  val idInstruction = ifIdReg.io.out.instruction
  val idPc          = ifIdReg.io.out.pc
  val idPcPlus4     = ifIdReg.io.out.pcPlus4

  decoder.io.instruction := idInstruction

  // Precise consumer source register usage detection
  val idUsesRs1 = idValid && (
    decoder.io.controls.aluSrcA === ALUSrcA.RS1 ||
    decoder.io.controls.branchType =/= BranchType.NONE ||
    decoder.io.controls.jumpType === JumpType.JALR
  )

  val idUsesRs2 = idValid && (
    decoder.io.controls.aluSrcB === ALUSrcB.RS2 ||
    decoder.io.controls.branchType =/= BranchType.NONE ||
    decoder.io.controls.memWrite
  )

  hazardUnit.io.idValid     := idValid
  hazardUnit.io.idRs1       := decoder.io.rs1
  hazardUnit.io.idRs2       := decoder.io.rs2
  hazardUnit.io.idUsesRs1   := idUsesRs1
  hazardUnit.io.idUsesRs2   := idUsesRs2
  hazardUnit.io.idExValid   := idExReg.io.out.valid
  hazardUnit.io.idExMemRead := idExReg.io.out.controls.memRead
  hazardUnit.io.idExRd      := idExReg.io.out.rd
  hazardUnit.io.branchTaken := branchTaken

  // Divider EX Hold Logic
  val exValid       = idExReg.io.out.valid
  val exControls    = idExReg.io.out.controls
  val isDivOp       = exControls.mOp === MOp.DIV || exControls.mOp === MOp.DIVU || exControls.mOp === MOp.REM || exControls.mOp === MOp.REMU
  val divHold       = exValid && isDivOp && !divRem.io.done

  val stallIF   = hazardUnit.io.stallIF || divHold
  val stallID   = hazardUnit.io.stallID || divHold
  val flushIFID = hazardUnit.io.flushIFID
  val flushIDEX = hazardUnit.io.flushIDEX

  // =========================================================================
  // 3. STAGE 1: INSTRUCTION FETCH (IF)
  // =========================================================================
  val programLengthBytes = if (initialProgram.nonEmpty) initialProgram.length * 4 else imemDepthWords * 4
  imem.io.address := pc.io.pc
  val ifInstruction = imem.io.instruction
  val ifValid = (pc.io.pc < programLengthBytes.U)

  pc.io.stall            := stallIF
  pc.io.jumpBranchTaken  := branchTaken
  pc.io.jumpBranchTarget := redirectTarget

  ifIdReg.io.stall := stallID
  ifIdReg.io.flush := flushIFID
  ifIdReg.io.in.valid       := ifValid && !flushIFID
  ifIdReg.io.in.pc          := pc.io.pc
  ifIdReg.io.in.pcPlus4     := pc.io.pcPlus4
  ifIdReg.io.in.instruction := ifInstruction

  // =========================================================================
  // 4. STAGE 2: INSTRUCTION DECODE & OPERAND FETCH (ID)
  // =========================================================================
  rf.io.rs1Address := decoder.io.rs1
  rf.io.rs2Address := decoder.io.rs2

  // WB -> ID Same-Cycle Register Bypass
  val idRs1Val = Mux(wbRegWrite && (wbRd =/= 0.U) && (wbRd === decoder.io.rs1), wbData, rf.io.rs1Data)
  val idRs2Val = Mux(wbRegWrite && (wbRd =/= 0.U) && (wbRd === decoder.io.rs2), wbData, rf.io.rs2Data)

  idExReg.io.stall := divHold
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
  // 5. STAGE 3: EXECUTE, FORWARDING & BRANCH EVALUATION (EX)
  // =========================================================================
  val exPc          = idExReg.io.out.pc
  val exPcPlus4     = idExReg.io.out.pcPlus4
  val exInstruction = idExReg.io.out.instruction

  // Data Forwarding Unit Connections
  forwardingUnit.io.idExRs1       := idExReg.io.out.rs1
  forwardingUnit.io.idExRs2       := idExReg.io.out.rs2
  forwardingUnit.io.exMemValid    := exMemReg.io.out.valid
  forwardingUnit.io.exMemRegWrite := exMemReg.io.out.regWrite
  forwardingUnit.io.exMemMemRead  := exMemReg.io.out.memRead
  forwardingUnit.io.exMemRd       := exMemReg.io.out.rd
  forwardingUnit.io.memWbValid    := memWbReg.io.out.valid
  forwardingUnit.io.memWbRegWrite := memWbReg.io.out.regWrite
  forwardingUnit.io.memWbRd       := memWbReg.io.out.rd

  // EX/MEM forwarding data (excluding loads)
  val exMemForwardData = MuxLookup(exMemReg.io.out.wbSource, exMemReg.io.out.aluResult)(Seq(
    WBSource.ALU       -> exMemReg.io.out.aluResult,
    WBSource.PC_PLUS_4 -> exMemReg.io.out.pcPlus4,
    WBSource.IMM       -> exMemReg.io.out.imm
  ))

  // Resolved forwarded source register values
  val forwardedRs1 = Mux(forwardingUnit.io.forwardA === 2.U, exMemForwardData,
                     Mux(forwardingUnit.io.forwardA === 1.U, wbData, idExReg.io.out.rs1Data))

  val forwardedRs2 = Mux(forwardingUnit.io.forwardB === 2.U, exMemForwardData,
                     Mux(forwardingUnit.io.forwardB === 1.U, wbData, idExReg.io.out.rs2Data))

  // Operand selection using forwarded register values
  val operandA = MuxLookup(exControls.aluSrcA, 0.U(32.W))(Seq(
    ALUSrcA.RS1  -> forwardedRs1,
    ALUSrcA.PC   -> exPc,
    ALUSrcA.ZERO -> 0.U(32.W)
  ))

  val operandB = MuxLookup(exControls.aluSrcB, 0.U(32.W))(Seq(
    ALUSrcB.RS2  -> forwardedRs2,
    ALUSrcB.IMM  -> idExReg.io.out.imm,
    ALUSrcB.FOUR -> 4.U(32.W)
  ))

  // Objective 1 ALU execution
  alu.io.a      := operandA
  alu.io.b      := operandB
  alu.io.opcode := exControls.aluOp

  // Phase 5A: RV32M Multiplier
  rv32mMult.io.rs1 := forwardedRs1
  rv32mMult.io.rs2 := forwardedRs2
  rv32mMult.io.mOp := exControls.mOp

  // Phase 5B: Iterative Divider
  divRem.io.dividend := forwardedRs1
  divRem.io.divisor  := forwardedRs2
  divRem.io.isSigned := (exControls.mOp === MOp.DIV) || (exControls.mOp === MOp.REM)
  divRem.io.start    := exValid && isDivOp && !divRem.io.busy

  // Result Multiplexing
  val exResult = Mux(exControls.isMul, rv32mMult.io.result,
                 Mux(isDivOp, Mux(exControls.mOp === MOp.DIV || exControls.mOp === MOp.DIVU, divRem.io.quotient, divRem.io.remainder),
                 alu.io.result))

  // Branch and Jump evaluation using forwarded register values
  bju.io.pc         := exPc
  bju.io.rs1Data    := forwardedRs1
  bju.io.rs2Data    := forwardedRs2
  bju.io.imm        := idExReg.io.out.imm
  bju.io.branchType := exControls.branchType
  bju.io.jumpType   := exControls.jumpType

  branchTaken    := exValid && bju.io.taken
  redirectTarget := bju.io.targetAddress

  // Telemetry metadata derivation in EX stage
  val exTelemetryValid     = WireDefault(false.B)
  val exTelemetryClaActive = WireDefault(false.B)
  val exTelemetryMulActive = WireDefault(false.B)
  val exTelemetryResult    = exResult

  when(exValid && !divHold && !exControls.illegalInstruction && !exControls.isSecurityOp) {
    when(exControls.isMul) {
      exTelemetryValid     := true.B
      exTelemetryMulActive := true.B
      exTelemetryClaActive := false.B
    }.elsewhen(isDivOp) {
      exTelemetryValid     := false.B
      exTelemetryMulActive := false.B
      exTelemetryClaActive := false.B
    }.otherwise {
      exTelemetryValid     := (exControls.wbSource === WBSource.ALU) || exControls.memRead || exControls.memWrite || (exControls.branchType =/= BranchType.NONE)
      exTelemetryClaActive := (exControls.aluOp === ALUOps.ADD || exControls.aluOp === ALUOps.SUB)
      exTelemetryMulActive := false.B
    }
  }

  exMemReg.io.stall := false.B
  exMemReg.io.flush := false.B
  exMemReg.io.in.valid              := exValid && !divHold
  exMemReg.io.in.pc                 := exPc
  exMemReg.io.in.pcPlus4            := exPcPlus4
  exMemReg.io.in.instruction        := exInstruction
  exMemReg.io.in.rd                 := idExReg.io.out.rd
  exMemReg.io.in.aluResult          := exResult
  exMemReg.io.in.rs2Data            := forwardedRs2 // Forwarded store payload
  exMemReg.io.in.imm                := idExReg.io.out.imm
  exMemReg.io.in.regWrite           := exControls.regWrite
  exMemReg.io.in.memRead            := exControls.memRead
  exMemReg.io.in.memWrite           := exControls.memWrite
  exMemReg.io.in.memWidth           := exControls.memWidth
  exMemReg.io.in.wbSource           := exControls.wbSource
  exMemReg.io.in.illegalInstruction := exControls.illegalInstruction
  exMemReg.io.in.telemetryValid     := exTelemetryValid
  exMemReg.io.in.telemetryClaActive := exTelemetryClaActive
  exMemReg.io.in.telemetryMulActive := exTelemetryMulActive
  exMemReg.io.in.telemetryResult    := exTelemetryResult

  // =========================================================================
  // 6. STAGE 4: MEMORY ACCESS (MEM)
  // =========================================================================
  val memValid       = exMemReg.io.out.valid
  val memPc          = exMemReg.io.out.pc
  val memPcPlus4     = exMemReg.io.out.pcPlus4
  val memInstruction = exMemReg.io.out.instruction

  // SystemMMIO Interception
  systemMMIO.io.address     := exMemReg.io.out.aluResult
  systemMMIO.io.memReadReq  := memValid && exMemReg.io.out.memRead
  systemMMIO.io.memWriteReq := memValid && exMemReg.io.out.memWrite
  systemMMIO.io.writeData   := exMemReg.io.out.rs2Data
  systemMMIO.io.memWidth    := exMemReg.io.out.memWidth

  // DataMemory Access (suppressed when MMIO window hits)
  dmem.io.address   := exMemReg.io.out.aluResult
  dmem.io.writeData := exMemReg.io.out.rs2Data
  dmem.io.memRead   := memValid && exMemReg.io.out.memRead && !systemMMIO.io.windowHit
  dmem.io.memWrite  := memValid && exMemReg.io.out.memWrite && !systemMMIO.io.windowHit
  dmem.io.memWidth  := exMemReg.io.out.memWidth

  val memReadData = Mux(systemMMIO.io.windowHit, systemMMIO.io.readData, dmem.io.readData)

  memWbReg.io.stall := false.B
  memWbReg.io.flush := false.B
  memWbReg.io.in.valid              := memValid
  memWbReg.io.in.pc                 := memPc
  memWbReg.io.in.pcPlus4            := memPcPlus4
  memWbReg.io.in.instruction        := memInstruction
  memWbReg.io.in.rd                 := exMemReg.io.out.rd
  memWbReg.io.in.aluResult          := exMemReg.io.out.aluResult
  memWbReg.io.in.memReadData        := memReadData
  memWbReg.io.in.imm                := exMemReg.io.out.imm
  memWbReg.io.in.memRead            := memValid && exMemReg.io.out.memRead && Mux(systemMMIO.io.windowHit, systemMMIO.io.readAccepted, !dmem.io.misaligned)
  memWbReg.io.in.memReadReq         := memValid && exMemReg.io.out.memRead
  memWbReg.io.in.memWrite           := memValid && exMemReg.io.out.memWrite && Mux(systemMMIO.io.windowHit, systemMMIO.io.writeAccepted, !dmem.io.misaligned)
  memWbReg.io.in.memWriteReq        := memValid && exMemReg.io.out.memWrite
  memWbReg.io.in.memAddress         := exMemReg.io.out.aluResult
  memWbReg.io.in.memWriteData       := exMemReg.io.out.rs2Data
  memWbReg.io.in.regWrite           := exMemReg.io.out.regWrite && Mux(exMemReg.io.out.memRead, Mux(systemMMIO.io.windowHit, systemMMIO.io.readAccepted, !dmem.io.misaligned), true.B)
  memWbReg.io.in.wbSource           := exMemReg.io.out.wbSource
  memWbReg.io.in.illegalInstruction := exMemReg.io.out.illegalInstruction
  memWbReg.io.in.telemetryValid     := exMemReg.io.out.telemetryValid
  memWbReg.io.in.telemetryClaActive := exMemReg.io.out.telemetryClaActive
  memWbReg.io.in.telemetryMulActive := exMemReg.io.out.telemetryMulActive
  memWbReg.io.in.telemetryResult    := exMemReg.io.out.telemetryResult

  // =========================================================================
  // 7. STAGE 5: WRITEBACK & RETIREMENT (WB)
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

  // Telemetry Retirement Update
  systemMMIO.io.telemetryValid      := wbValid && memWbReg.io.out.telemetryValid
  systemMMIO.io.telemetryClaActive  := memWbReg.io.out.telemetryClaActive
  systemMMIO.io.telemetryMulActive  := memWbReg.io.out.telemetryMulActive
  systemMMIO.io.telemetryResult     := memWbReg.io.out.telemetryResult

  // System Performance Counters
  systemMMIO.io.retireEvent         := wbValid
  systemMMIO.io.commitPc            := wbPc
  systemMMIO.io.branchTaken         := exValid && bju.io.taken && (exControls.branchType =/= BranchType.NONE) && (idExReg.io.out.controls.jumpType === JumpType.NONE)
  systemMMIO.io.loadUseStall        := hazardUnit.io.stallIF
  systemMMIO.io.dividerBusy         := divRem.io.busy
  systemMMIO.io.pipelineStall       := stallIF

  // Security Violation Event Interface (inactive for Phase 6)
  val secEvent = Wire(new SecurityViolationEvent)
  secEvent.valid      := false.B
  secEvent.pc         := 0.U(32.W)
  secEvent.address    := 0.U(32.W)
  secEvent.accessType := AccessType.READ
  secEvent.reason     := SecurityReason.NONE
  secEvent.context    := systemMMIO.io.schedHint
  systemMMIO.io.securityEvent := secEvent

  // =========================================================================
  // 8. Architectural Commit / Retirement Interface
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
  // 9. Pipeline Observability Probes
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

  io.forwardA       := forwardingUnit.io.forwardA
  io.forwardB       := forwardingUnit.io.forwardB
  io.loadUseHazard  := hazardUnit.io.loadUseHazard
  io.stallIF        := stallIF
  io.stallID        := stallID

  io.mOp              := exControls.mOp
  io.mulActive        := exControls.isMul
  io.dividerBusy      := divRem.io.busy
  io.dividerDone      := divRem.io.done
  io.dividerIteration := divRem.io.iteration

  io.schedHint            := systemMMIO.io.schedHint
  io.processBehaviorClass := systemMMIO.io.processBehaviorClass
}
