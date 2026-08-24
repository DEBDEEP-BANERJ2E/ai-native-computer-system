package objective02.pipeline

import chisel3._
import chisel3.util._
import objective02.decode._
import objective02.datapath.{BranchJumpUnit, ProgramCounter, RegisterFile}
import objective02.memory.{DataMemory, InstructionMemory}
import objective02.execute.{RV32MMultiplier, IterativeDivider}
import objective02.capability.{CapabilityLite, CapabilityPerms, CapabilityRegFile, CapabilityChecker}
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

// =========================================================================
// Objective 2 Phase 8 Trap Observability Bundle
// =========================================================================
class TrapObservabilityIO extends Bundle {
  val trapTaken  = Output(Bool())
  val trapTarget = Output(UInt(32.W))
  val trapEpc    = Output(UInt(32.W))
  val trapCause  = Output(UInt(32.W))
  val trapAddr   = Output(UInt(32.W))
  val trapActive = Output(Bool())
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
  val capHazard      = Output(Bool())
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
  val currentContext       = Output(UInt(32.W))

  // Phase 8 Precise Trap Observability
  val trap                 = new TrapObservabilityIO
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
  val decoder        = Module(new Decoder(enableFullM = true, enableCapabilities = true))
  val rf             = Module(new RegisterFile)
  val capRf          = Module(new CapabilityRegFile(dmemSizeBytes))
  val alu            = Module(new ALU(32)) // Reusing Objective 1 verified arithmetic datapath
  val bju            = Module(new BranchJumpUnit)
  val capChecker     = Module(new CapabilityChecker)
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
  val branchTaken     = Wire(Bool())
  val redirectTarget  = Wire(UInt(32.W))
  val takePreciseTrap = Wire(Bool())
  val takeTrapReturn  = Wire(Bool())
  val wbRegWrite      = Wire(Bool())
  val wbRd            = Wire(UInt(5.W))
  val wbData          = Wire(UInt(32.W))

  // =========================================================================
  // 2. HAZARD DETECTION (ID Stage Consumer vs EX/MEM Stage Producers)
  // =========================================================================
  val idValid       = ifIdReg.io.out.valid
  val idInstruction = ifIdReg.io.out.instruction
  val idPc          = ifIdReg.io.out.pc
  val idPcPlus4     = ifIdReg.io.out.pcPlus4

  decoder.io.instruction := idInstruction

  hazardUnit.io.idValid          := idValid
  hazardUnit.io.idRs1            := decoder.io.rs1
  hazardUnit.io.idRs2            := decoder.io.rs2
  hazardUnit.io.idUsesRs1        := decoder.io.controls.usesIntRs1
  hazardUnit.io.idUsesRs2        := decoder.io.controls.usesIntRs2
  hazardUnit.io.idCs1            := decoder.io.rs1(2, 0)
  hazardUnit.io.idUsesCapRs1     := decoder.io.controls.usesCapRs1

  hazardUnit.io.idExValid        := idExReg.io.out.valid
  hazardUnit.io.idExMemRead      := idExReg.io.out.controls.memRead
  hazardUnit.io.idExRd           := idExReg.io.out.rd
  hazardUnit.io.idExCapRegWrite  := idExReg.io.out.controls.capRegWrite
  hazardUnit.io.idExCapRd        := idExReg.io.out.capRd

  hazardUnit.io.exMemValid       := exMemReg.io.out.valid
  hazardUnit.io.exMemCapRegWrite := exMemReg.io.out.capRegWrite
  hazardUnit.io.exMemCapRd       := exMemReg.io.out.capRd

  hazardUnit.io.branchTaken      := branchTaken
  hazardUnit.io.trapTaken        := takePreciseTrap
  hazardUnit.io.trapReturn       := takeTrapReturn

  // Divider EX Hold Logic (killed if younger than trap or return)
  val exValid       = idExReg.io.out.valid
  val exControls    = idExReg.io.out.controls
  val isDivOp       = exControls.mOp === MOp.DIV || exControls.mOp === MOp.DIVU || exControls.mOp === MOp.REM || exControls.mOp === MOp.REMU
  val divHold       = exValid && isDivOp && !divRem.io.done && !takePreciseTrap && !takeTrapReturn

  divRem.io.kill    := takePreciseTrap || takeTrapReturn

  val stallIF   = hazardUnit.io.stallIF || divHold
  val stallID   = hazardUnit.io.stallID || divHold
  val flushIFID = hazardUnit.io.flushIFID || takePreciseTrap || takeTrapReturn
  val flushIDEX = hazardUnit.io.flushIDEX || takePreciseTrap || takeTrapReturn
  val flushEXMEM= takePreciseTrap || takeTrapReturn

  // =========================================================================
  // 3. STAGE 1: INSTRUCTION FETCH (IF)
  // Priority: MEM Precise Trap > MEM Trap-Return > EX Branch/Jump > Stall > PC+4
  // =========================================================================
  val programLengthBytes = if (initialProgram.nonEmpty) initialProgram.length * 4 else imemDepthWords * 4
  imem.io.address := pc.io.pc
  val ifInstruction = imem.io.instruction
  val ifValid = (pc.io.pc < programLengthBytes.U)

  val pcRedirectTaken  = takePreciseTrap || takeTrapReturn || branchTaken
  val pcRedirectTarget = Mux(takePreciseTrap, systemMMIO.io.trapVector,
                         Mux(takeTrapReturn, systemMMIO.io.trapEpc, redirectTarget))

  pc.io.stall            := stallIF
  pc.io.jumpBranchTaken  := pcRedirectTaken
  pc.io.jumpBranchTarget := pcRedirectTarget

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

  // Capability Register Read & Hazard Bypass
  capRf.io.raddr1 := decoder.io.rs1(2, 0)

  // WB -> ID Same-Cycle GPR Register Bypass
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
  idExReg.io.in.capRs1Data  := capRf.io.rdata1
  idExReg.io.in.capRd       := decoder.io.rd(2, 0)
  idExReg.io.in.capCs1      := decoder.io.rs1(2, 0)
  idExReg.io.in.controls    := decoder.io.controls

  // =========================================================================
  // 5. STAGE 3: EXECUTE, FORWARDING & CAPABILITY DERIVATION (EX)
  // =========================================================================
  val exPc          = idExReg.io.out.pc
  val exPcPlus4     = idExReg.io.out.pcPlus4
  val exInstruction = idExReg.io.out.instruction

  // Data Forwarding Unit Connections (Integer Registers)
  forwardingUnit.io.idExRs1       := idExReg.io.out.rs1
  forwardingUnit.io.idExRs2       := idExReg.io.out.rs2
  forwardingUnit.io.exMemValid    := exMemReg.io.out.valid
  forwardingUnit.io.exMemRegWrite := exMemReg.io.out.regWrite
  forwardingUnit.io.exMemMemRead  := exMemReg.io.out.memRead
  forwardingUnit.io.exMemRd       := exMemReg.io.out.rd
  forwardingUnit.io.memWbValid    := memWbReg.io.out.valid
  forwardingUnit.io.memWbRegWrite := memWbReg.io.out.regWrite
  forwardingUnit.io.memWbRd       := memWbReg.io.out.rd

  val forwardedRs1 = MuxLookup(forwardingUnit.io.forwardA, idExReg.io.out.rs1Data)(Seq(
    0.U -> idExReg.io.out.rs1Data,
    1.U -> wbData,
    2.U -> exMemReg.io.out.aluResult
  ))

  val forwardedRs2 = MuxLookup(forwardingUnit.io.forwardB, idExReg.io.out.rs2Data)(Seq(
    0.U -> idExReg.io.out.rs2Data,
    1.U -> wbData,
    2.U -> exMemReg.io.out.aluResult
  ))

  // ALU Input Operand Selection
  val aluOperandA = MuxLookup(exControls.aluSrcA, forwardedRs1)(Seq(
    ALUSrcA.RS1  -> forwardedRs1,
    ALUSrcA.PC   -> exPc,
    ALUSrcA.ZERO -> 0.U(32.W)
  ))

  val aluOperandB = MuxLookup(exControls.aluSrcB, forwardedRs2)(Seq(
    ALUSrcB.RS2  -> forwardedRs2,
    ALUSrcB.IMM  -> idExReg.io.out.imm,
    ALUSrcB.FOUR -> 4.U(32.W)
  ))

  alu.io.a      := aluOperandA
  alu.io.b      := aluOperandB
  alu.io.opcode := exControls.aluOp

  // Multiplier Unit Connections (RV32M MUL, MULH, MULHSU, MULHU)
  rv32mMult.io.rs1 := forwardedRs1
  rv32mMult.io.rs2 := forwardedRs2
  rv32mMult.io.mOp := exControls.mOp

  // Divider Unit Connections (RV32M DIV, DIVU, REM, REMU)
  divRem.io.start    := exValid && isDivOp && !divRem.io.busy && !divRem.io.done
  divRem.io.dividend := forwardedRs1
  divRem.io.divisor  := forwardedRs2
  divRem.io.isSigned := (exControls.mOp === MOp.DIV || exControls.mOp === MOp.REM)

  // Capability Derivation Logic in EX Stage
  val inCap = idExReg.io.out.capRs1Data

  // 1. CSETBOUNDS cd, cs1, rs2 (rs2 = length)
  val requestedLength = forwardedRs2
  val currentRemainingLength = Mux(inCap.offset <= inCap.length, inCap.length - inCap.offset, 0.U)
  val csetBoundsOk = (inCap.offset <= inCap.length) && (requestedLength <= currentRemainingLength)
  val csetSuccess  = inCap.tag && csetBoundsOk

  val csetDerivedCap = Wire(new CapabilityLite)
  csetDerivedCap.tag    := csetSuccess
  csetDerivedCap.base   := inCap.base + inCap.offset
  csetDerivedCap.length := requestedLength
  csetDerivedCap.perms  := inCap.perms
  csetDerivedCap.offset := 0.U(32.W)

  // 2. CANDPERM cd, cs1, rs2 (rs2 = perms mask)
  val candDerivedCap = Wire(new CapabilityLite)
  candDerivedCap.tag    := inCap.tag
  candDerivedCap.base   := inCap.base
  candDerivedCap.length := inCap.length
  candDerivedCap.perms  := inCap.perms & forwardedRs2(3, 0)
  candDerivedCap.offset := inCap.offset

  // 3. CINCOFFSET cd, cs1, rs2 (rs2 = signed delta)
  val offsetExtS   = Cat(0.U(2.W), inCap.offset).asSInt // 34-bit zero-extended positive signed
  val deltaExtS    = Cat(forwardedRs2(31), forwardedRs2(31), forwardedRs2).asSInt // 34-bit sign-extended
  val newOffsetS   = offsetExtS +& deltaExtS
  val capLenExtS   = Cat(0.U(1.W), inCap.length).asSInt // 33-bit positive signed
  val cincOffsetOk = (newOffsetS >= 0.S) && (newOffsetS <= capLenExtS)
  val cincSuccess  = inCap.tag && cincOffsetOk

  val cincDerivedCap = Wire(new CapabilityLite)
  cincDerivedCap.tag    := cincSuccess
  cincDerivedCap.base   := inCap.base
  cincDerivedCap.length := inCap.length
  cincDerivedCap.perms  := inCap.perms
  cincDerivedCap.offset := newOffsetS(31, 0).asUInt

  // Derivation violation metadata pipeline latches
  val exCapViolationValid      = WireDefault(false.B)
  val exCapViolationReason     = WireDefault(SecurityReason.NONE)
  val exCapViolationAddress    = WireDefault(0.U(32.W))
  val exCapViolationAccessType = AccessType.CAPABILITY_OPERATION

  val exCapWriteData = WireDefault(CapabilityLite.nullCapability())
  val exCapRegWrite  = WireDefault(false.B)

  when(exValid && exControls.isCapOp) {
    switch(exControls.capOp) {
      is(CapOp.CSETBOUNDS) {
        when(csetSuccess) {
          exCapWriteData := csetDerivedCap
          exCapRegWrite  := true.B
        }.otherwise {
          exCapViolationValid   := true.B
          exCapViolationReason  := Mux(!inCap.tag, SecurityReason.INVALID_CAPABILITY, SecurityReason.MONOTONICITY)
          exCapViolationAddress := inCap.base + inCap.offset
        }
      }
      is(CapOp.CANDPERM) {
        when(inCap.tag) {
          exCapWriteData := candDerivedCap
          exCapRegWrite  := true.B
        }.otherwise {
          exCapViolationValid   := true.B
          exCapViolationReason  := SecurityReason.INVALID_CAPABILITY
          exCapViolationAddress := inCap.base
        }
      }
      is(CapOp.CINCOFFSET) {
        when(cincSuccess) {
          exCapWriteData := cincDerivedCap
          exCapRegWrite  := true.B
        }.otherwise {
          exCapViolationValid   := true.B
          exCapViolationReason  := Mux(!inCap.tag, SecurityReason.INVALID_CAPABILITY, SecurityReason.BOUNDS)
          exCapViolationAddress := inCap.base + inCap.offset
        }
      }
      is(CapOp.CCLEAR) {
        exCapWriteData := CapabilityLite.nullCapability()
        exCapRegWrite  := true.B
      }
    }
  }

  // Capability protected memory effective address (cursor + immediate)
  val capEffectiveAddress = (inCap.base + inCap.offset) + idExReg.io.out.imm

  // Result Multiplexing for EX Stage
  val exResult = Mux(exControls.isCapMem, capEffectiveAddress,
                 Mux(exControls.isCapOp, MuxLookup(exControls.capOp, 0.U(32.W))(Seq(
                   CapOp.CGETBASE   -> inCap.base,
                   CapOp.CGETLEN    -> inCap.length,
                   CapOp.CGETTAG    -> Cat(0.U(31.W), inCap.tag),
                   CapOp.CGETPERM   -> Cat(0.U(29.W), inCap.perms),
                   CapOp.CGETOFFSET -> inCap.offset
                 )),
                 Mux(exControls.isMul, rv32mMult.io.result,
                 Mux(isDivOp, Mux(exControls.mOp === MOp.DIV || exControls.mOp === MOp.DIVU, divRem.io.quotient, divRem.io.remainder),
                 alu.io.result))))

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

  when(exValid && !divHold && !exControls.illegalInstruction && !exControls.isSecurityOp && !exControls.isCapOp && !exControls.isCapMem) {
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
  exMemReg.io.flush := flushEXMEM
  exMemReg.io.in.valid                  := exValid && !divHold && !flushEXMEM
  exMemReg.io.in.pc                     := exPc
  exMemReg.io.in.pcPlus4                := exPcPlus4
  exMemReg.io.in.instruction            := exInstruction
  exMemReg.io.in.rd                     := idExReg.io.out.rd
  exMemReg.io.in.aluResult              := exResult
  exMemReg.io.in.rs2Data                := forwardedRs2 // Forwarded store payload
  exMemReg.io.in.imm                    := idExReg.io.out.imm
  exMemReg.io.in.capRegWrite            := exCapRegWrite
  exMemReg.io.in.capRd                  := idExReg.io.out.capRd
  exMemReg.io.in.capWriteData           := exCapWriteData
  exMemReg.io.in.isCapMem               := exControls.isCapMem
  exMemReg.io.in.capSource              := inCap
  exMemReg.io.in.capViolationValid      := exCapViolationValid
  exMemReg.io.in.capViolationReason     := exCapViolationReason
  exMemReg.io.in.capViolationAddress    := exCapViolationAddress
  exMemReg.io.in.capViolationAccessType := exCapViolationAccessType
  exMemReg.io.in.regWrite               := exControls.regWrite
  exMemReg.io.in.memRead                := exControls.memRead
  exMemReg.io.in.memWrite               := exControls.memWrite
  exMemReg.io.in.memWidth               := exControls.memWidth
  exMemReg.io.in.wbSource               := exControls.wbSource
  exMemReg.io.in.illegalInstruction     := exControls.illegalInstruction
  exMemReg.io.in.telemetryValid         := exTelemetryValid
  exMemReg.io.in.telemetryClaActive     := exTelemetryClaActive
  exMemReg.io.in.telemetryMulActive     := exTelemetryMulActive
  exMemReg.io.in.telemetryResult        := exTelemetryResult

  // =========================================================================
  // 6. STAGE 4: MEMORY ACCESS & CAPABILITY AUTHORIZATION (MEM)
  // =========================================================================
  val memValid       = exMemReg.io.out.valid
  val memPc          = exMemReg.io.out.pc
  val memPcPlus4     = exMemReg.io.out.pcPlus4
  val memInstruction = exMemReg.io.out.instruction

  // Capability Authorization Checker in MEM Stage
  capChecker.io.cap              := exMemReg.io.out.capSource
  capChecker.io.effectiveAddress := exMemReg.io.out.aluResult
  capChecker.io.accessSize       := Mux(exMemReg.io.out.memWidth === MemWidth.BYTE || exMemReg.io.out.memWidth === MemWidth.BYTE_U, 1.U,
                                    Mux(exMemReg.io.out.memWidth === MemWidth.HALF || exMemReg.io.out.memWidth === MemWidth.HALF_U, 2.U, 4.U))
  capChecker.io.isRead           := exMemReg.io.out.memRead
  capChecker.io.isWrite          := exMemReg.io.out.memWrite

  val isCapMemAccess = memValid && exMemReg.io.out.isCapMem
  val capAccessAllow = Mux(isCapMemAccess, capChecker.io.allow, true.B)
  val isCapMemViolation = isCapMemAccess && capChecker.io.violation
  val isDerivationViolation = memValid && exMemReg.io.out.capViolationValid

  // Unified Security Violation Event generation in MEM stage
  val secEvent = Wire(new SecurityViolationEvent)
  secEvent.valid      := isCapMemViolation || isDerivationViolation
  secEvent.pc         := exMemReg.io.out.pc // Offending instruction PC!
  secEvent.address    := Mux(isCapMemViolation, exMemReg.io.out.aluResult, exMemReg.io.out.capViolationAddress)
  secEvent.accessType := Mux(isCapMemViolation, Mux(exMemReg.io.out.memWrite, AccessType.WRITE, AccessType.READ), exMemReg.io.out.capViolationAccessType)
  secEvent.reason     := Mux(isCapMemViolation, capChecker.io.reason, exMemReg.io.out.capViolationReason)
  secEvent.context    := systemMMIO.io.currentContext

  systemMMIO.io.securityEvent := secEvent

  // Objective 2 Phase 8 Combinational Precise Trap & Return Redirects
  takePreciseTrap := secEvent.valid && systemMMIO.io.trapEnable && !systemMMIO.io.trapActive
  takeTrapReturn  := systemMMIO.io.takeTrapReturn

  // SystemMMIO Interception (suppressed if capability access is denied)
  systemMMIO.io.address     := exMemReg.io.out.aluResult
  systemMMIO.io.memReadReq  := memValid && exMemReg.io.out.memRead && capAccessAllow
  systemMMIO.io.memWriteReq := memValid && exMemReg.io.out.memWrite && capAccessAllow
  systemMMIO.io.writeData   := exMemReg.io.out.rs2Data
  systemMMIO.io.memWidth    := exMemReg.io.out.memWidth

  // DataMemory Access (suppressed when MMIO window hits or capability denied)
  dmem.io.address   := exMemReg.io.out.aluResult
  dmem.io.writeData := exMemReg.io.out.rs2Data
  dmem.io.memRead   := memValid && exMemReg.io.out.memRead && capAccessAllow && !systemMMIO.io.windowHit
  dmem.io.memWrite  := memValid && exMemReg.io.out.memWrite && capAccessAllow && !systemMMIO.io.windowHit
  dmem.io.memWidth  := exMemReg.io.out.memWidth

  val memReadData = Mux(systemMMIO.io.windowHit, systemMMIO.io.readData, dmem.io.readData)

  // In Phase 8 precise trap mode, the faulting MEM instruction does NOT enter WB / retire
  val memEnterWbValid = memValid && !takePreciseTrap

  memWbReg.io.stall := false.B
  memWbReg.io.flush := false.B
  memWbReg.io.in.valid              := memEnterWbValid
  memWbReg.io.in.pc                 := memPc
  memWbReg.io.in.pcPlus4            := memPcPlus4
  memWbReg.io.in.instruction        := memInstruction
  memWbReg.io.in.rd                 := exMemReg.io.out.rd
  memWbReg.io.in.aluResult          := exMemReg.io.out.aluResult
  memWbReg.io.in.memReadData        := memReadData
  memWbReg.io.in.imm                := exMemReg.io.out.imm
  memWbReg.io.in.capRegWrite        := exMemReg.io.out.capRegWrite && capAccessAllow && !takePreciseTrap
  memWbReg.io.in.capRd              := exMemReg.io.out.capRd
  memWbReg.io.in.capWriteData       := exMemReg.io.out.capWriteData
  memWbReg.io.in.memRead            := memValid && exMemReg.io.out.memRead && capAccessAllow && Mux(systemMMIO.io.windowHit, systemMMIO.io.readAccepted, !dmem.io.misaligned)
  memWbReg.io.in.memReadReq         := memValid && exMemReg.io.out.memRead
  memWbReg.io.in.memWrite           := memValid && exMemReg.io.out.memWrite && capAccessAllow && Mux(systemMMIO.io.windowHit, systemMMIO.io.writeAccepted, !dmem.io.misaligned)
  memWbReg.io.in.memWriteReq        := memValid && exMemReg.io.out.memWrite
  memWbReg.io.in.memAddress         := exMemReg.io.out.aluResult
  memWbReg.io.in.memWriteData       := exMemReg.io.out.rs2Data
  memWbReg.io.in.regWrite           := exMemReg.io.out.regWrite && !takePreciseTrap && Mux(exMemReg.io.out.memRead, capAccessAllow && Mux(systemMMIO.io.windowHit, systemMMIO.io.readAccepted, !dmem.io.misaligned), true.B)
  memWbReg.io.in.wbSource           := exMemReg.io.out.wbSource
  memWbReg.io.in.illegalInstruction := exMemReg.io.out.illegalInstruction
  memWbReg.io.in.telemetryValid     := exMemReg.io.out.telemetryValid && capAccessAllow && !takePreciseTrap
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

  // Capability Register Writeback at WB
  capRf.io.wen   := wbValid && memWbReg.io.out.capRegWrite
  capRf.io.waddr := memWbReg.io.out.capRd
  capRf.io.wdata := memWbReg.io.out.capWriteData

  // Telemetry Retirement Update
  systemMMIO.io.telemetryValid      := wbValid && memWbReg.io.out.telemetryValid
  systemMMIO.io.telemetryClaActive  := memWbReg.io.out.telemetryClaActive
  systemMMIO.io.telemetryMulActive  := memWbReg.io.out.telemetryMulActive
  systemMMIO.io.telemetryResult     := memWbReg.io.out.telemetryResult

  // System Performance Counters
  systemMMIO.io.retireEvent         := wbValid
  systemMMIO.io.commitPc            := wbPc
  systemMMIO.io.branchTaken         := exValid && bju.io.taken && (exControls.branchType =/= BranchType.NONE) && (idExReg.io.out.controls.jumpType === JumpType.NONE)
  systemMMIO.io.loadUseStall        := hazardUnit.io.loadUseHazard
  systemMMIO.io.dividerBusy         := divRem.io.busy
  systemMMIO.io.pipelineStall       := stallIF

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
  io.capHazard      := hazardUnit.io.capHazard
  io.stallIF        := stallIF
  io.stallID        := stallID

  io.mOp              := exControls.mOp
  io.mulActive        := exControls.isMul
  io.dividerBusy      := divRem.io.busy
  io.dividerDone      := divRem.io.done
  io.dividerIteration := divRem.io.iteration

  io.schedHint            := systemMMIO.io.schedHint
  io.processBehaviorClass := systemMMIO.io.processBehaviorClass
  io.currentContext       := systemMMIO.io.currentContext

  // Phase 8 Precise Trap Observability
  io.trap.trapTaken  := takePreciseTrap
  io.trap.trapTarget := systemMMIO.io.trapVector
  io.trap.trapEpc    := systemMMIO.io.trapEpc
  io.trap.trapCause  := systemMMIO.io.trapCause
  io.trap.trapAddr   := systemMMIO.io.trapAddr
  io.trap.trapActive := systemMMIO.io.trapActive
}
