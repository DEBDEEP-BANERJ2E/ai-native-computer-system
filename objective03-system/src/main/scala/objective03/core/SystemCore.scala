package objective03.core

import chisel3._
import chisel3.util._
import objective02.decode._
import objective02.datapath.{BranchJumpUnit, ProgramCounter, RegisterFile}
import objective02.memory.{DataMemory}
import objective02.execute.{RV32MMultiplier, IterativeDivider}
import objective02.capability.{CapabilityLite, CapabilityRegFile, CapabilityChecker}
import objective02.system.{SystemMMIO, SecurityViolationEvent, AccessType, SecurityReason}
import objective02.pipeline.{HazardUnit, ForwardingUnit, IF_ID_Register,
                            ID_EX_Register, EX_MEM_Register, MEM_WB_Register,
                            StageProbeBundle, CommitBundle, TrapObservabilityIO}
import objective03.decode.{SystemDecoder, SysOp, SystemControlSignals}
import objective03.csr.{CSRFile, TrapEntryBundle, TrapReturnBundle, Privilege, TrapCause, CSR, MstatusBits, SstatusView}
import objective03.trap.{TrapController, TrapEventBundle}
import objective03.timer.TimerBlock
import objective03.memory.WritableInstructionMemory
import objective03.pipeline.{SystemPipeBundle, SystemPipeReg, SystemPipe}
import objective01.datapath.ALU

// =========================================================================
// SystemCore — AN32-System-v1 privileged core.
//
// Reuses frozen objective02 leaf modules and stage-register semantics, but
// owns its pipeline wiring. Key System-v1 differences from PipelinedCore:
//   - SystemDecoder (OP_SYSTEM + FENCE.I) with SystemControlSignals
//   - CSRFile: atomic CSR RMW committed at WB (single retirement point =>
//     program-ordered without forwarding)
//   - TrapController: exceptions at MEM (precise, suppress faulting instr),
//     interrupts only at WB retirement boundary
//   - MRET/SRET: flush + redirect + CSRFile.trapReturn
//   - TimerBlock (MTIP) + SystemMMIO share the 0x8000xxxx window
//   - WritableInstructionMemory (runtime-loadable imem)
// =========================================================================

class SystemCoreIO extends Bundle {
  val commit = new CommitBundle
  val stageIF = new StageProbeBundle
  val stageID = new StageProbeBundle
  val stageEX = new StageProbeBundle
  val stageMEM = new StageProbeBundle
  val stageWB = new StageProbeBundle
  val flushIFID      = Output(Bool())
  val flushIDEX      = Output(Bool())
  val branchTaken    = Output(Bool())
  val redirectTarget = Output(UInt(32.W))
  val forwardA       = Output(UInt(2.W))
  val forwardB       = Output(UInt(2.W))
  val loadUseHazard  = Output(Bool())
  val capHazard      = Output(Bool())
  val stallIF        = Output(Bool())
  val stallID        = Output(Bool())
  val mOp            = Output(UInt(4.W))
  val mulActive      = Output(Bool())
  val dividerBusy    = Output(Bool())
  val dividerDone    = Output(Bool())
  val dividerIteration = Output(UInt(6.W))
  val schedHint      = Output(UInt(32.W))
  val processBehaviorClass = Output(UInt(32.W))
  val currentContext = Output(UInt(32.W))
  val trap           = new TrapObservabilityIO

  // System-v1 observability
  val privLevel      = Output(UInt(2.W))
  val trapTakenSys   = Output(Bool())
  val sysTrapVector  = Output(UInt(32.W))
  val sysTrapEpc     = Output(UInt(32.W))
  val sysTrapCause   = Output(UInt(32.W))
  val timerMtip      = Output(Bool())
  val imemLoadAddr   = Input(UInt(32.W))
  val imemLoadData   = Input(UInt(32.W))
  val imemLoadEn     = Input(Bool())
}

class SystemCore(
  val imemDepthWords: Int = 4096,
  val dmemSizeBytes: Int = 65536,
  val bootAddress: BigInt = 0x00000000L
) extends Module {
  val io = IO(new SystemCoreIO)

  // =========================================================================
  // 1. Submodules
  // =========================================================================
  val pc          = Module(new ProgramCounter(bootAddress))
  val imem        = Module(new WritableInstructionMemory(imemDepthWords))
  val decoder     = Module(new SystemDecoder(enableFullM = true, enableCapabilities = true))
  val rf          = Module(new RegisterFile)
  val capRf       = Module(new CapabilityRegFile(dmemSizeBytes))
  val alu         = Module(new ALU(32))
  val bju         = Module(new BranchJumpUnit)
  val capChecker  = Module(new CapabilityChecker)
  val dmem        = Module(new DataMemory(dmemSizeBytes))
  val systemMMIO  = Module(new SystemMMIO)
  val timer       = Module(new TimerBlock)
  val csrFile     = Module(new CSRFile)
  val trapCtrl    = Module(new TrapController)
  val forwardingUnit = Module(new ForwardingUnit)
  val hazardUnit     = Module(new HazardUnit)
  val rv32mMult   = Module(new RV32MMultiplier)
  val divRem      = Module(new IterativeDivider)

  val ifIdReg  = Module(new IF_ID_Register)
  val idExReg  = Module(new ID_EX_Register)
  val exMemReg = Module(new EX_MEM_Register)
  val memWbReg = Module(new MEM_WB_Register)

  // System sideband registers (ID->EX, EX->MEM, MEM->WB)
  val sysIdEx  = Module(new SystemPipeReg)
  val sysExMem = Module(new SystemPipeReg)
  val sysMemWb = Module(new SystemPipeReg)

  // Forward-declared combinational wires (driven later in the module)
  val branchTakenW    = Wire(Bool())
  val redirectTargetW = Wire(UInt(32.W))
  val wbDataW         = Wire(UInt(32.W))
  val wbRdW           = Wire(UInt(5.W))
  val wbRegWriteW     = Wire(Bool())
  val wbPcPlus4W      = memWbReg.io.out.pcPlus4
  val sysReturnTarget = Wire(UInt(32.W))

  // =========================================================================
  // 2. Decode & ID stage
  // =========================================================================
  val idValid       = ifIdReg.io.out.valid
  val idInstruction = ifIdReg.io.out.instruction
  val idPc          = ifIdReg.io.out.pc
  val idPcPlus4     = ifIdReg.io.out.pcPlus4

  decoder.io.instruction := idInstruction

  hazardUnit.io.idValid          := idValid
  hazardUnit.io.idRs1            := decoder.io.rs1
  hazardUnit.io.idRs2            := decoder.io.rs2
  hazardUnit.io.idUsesRs1        := decoder.io.controls.base.usesIntRs1
  hazardUnit.io.idUsesRs2        := decoder.io.controls.base.usesIntRs2
  hazardUnit.io.idCs1            := decoder.io.rs1(2, 0)
  hazardUnit.io.idUsesCapRs1     := decoder.io.controls.base.usesCapRs1

  hazardUnit.io.idExValid        := idExReg.io.out.valid
  hazardUnit.io.idExMemRead      := idExReg.io.out.controls.memRead
  hazardUnit.io.idExRd           := idExReg.io.out.rd
  hazardUnit.io.idExCapRegWrite  := idExReg.io.out.controls.capRegWrite
  hazardUnit.io.idExCapRd        := idExReg.io.out.capRd

  hazardUnit.io.exMemValid       := exMemReg.io.out.valid
  hazardUnit.io.exMemCapRegWrite := exMemReg.io.out.capRegWrite
  hazardUnit.io.exMemCapRd       := exMemReg.io.out.capRd

  // Trap inputs to hazard unit: any precise trap (security or system)
  val takePreciseTrap = Wire(Bool())
  val takeSysTrap     = Wire(Bool())
  val takeTrapReturn  = Wire(Bool())
  val takeSysReturn   = Wire(Bool())
  hazardUnit.io.trapTaken   := takePreciseTrap || takeSysTrap
  hazardUnit.io.trapReturn  := takeTrapReturn || takeSysReturn

  // =========================================================================
  // 3. IF stage
  // =========================================================================
  imem.io.address  := pc.io.pc
  imem.io.loadAddr := io.imemLoadAddr
  imem.io.loadData := io.imemLoadData
  imem.io.loadEn   := io.imemLoadEn

  val programLengthBytes = (imemDepthWords * 4).U
  val ifInstruction = imem.io.instruction
  val ifValid = (pc.io.pc < programLengthBytes)

  val exValid       = idExReg.io.out.valid
  val exControls    = idExReg.io.out.controls
  val isDivOp       = exControls.mOp === MOp.DIV || exControls.mOp === MOp.DIVU ||
                      exControls.mOp === MOp.REM || exControls.mOp === MOp.REMU
  val divHold       = exValid && isDivOp && !divRem.io.done &&
                      !(takePreciseTrap || takeSysTrap) && !(takeTrapReturn || takeSysReturn)

  divRem.io.kill    := (takePreciseTrap || takeSysTrap) || (takeTrapReturn || takeSysReturn)

  val stallIF   = hazardUnit.io.stallIF || divHold
  val stallID   = hazardUnit.io.stallID || divHold
  val flushIFID = hazardUnit.io.flushIFID || takePreciseTrap || takeSysTrap ||
                  takeTrapReturn || takeSysReturn
  val flushIDEX = hazardUnit.io.flushIDEX || takePreciseTrap || takeSysTrap ||
                  takeTrapReturn || takeSysReturn
  val flushEXMEM= takePreciseTrap || takeSysTrap || takeTrapReturn || takeSysReturn

  // PC redirect: precise traps > trap/sys returns > branch
  val pcRedirectTaken  = takePreciseTrap || takeSysTrap || takeTrapReturn ||
                        takeSysReturn || branchTakenW
  val pcRedirectTarget = Mux(takePreciseTrap, systemMMIO.io.trapVector,
                       Mux(takeSysTrap, trapCtrl.io.trapVector,
                       Mux(takeTrapReturn, systemMMIO.io.trapEpc,
                       Mux(takeSysReturn, sysReturnTarget,
                            redirectTargetW))))

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
  // 4. ID stage — register reads + system sideband capture
  // =========================================================================
  rf.io.rs1Address := decoder.io.rs1
  rf.io.rs2Address := decoder.io.rs2
  capRf.io.raddr1   := decoder.io.rs1(2, 0)

  // CSR operands use the same WB->ID bypass as integer rs1
  decoder.io.rs1Data := Mux(wbRegWriteW && (wbRdW =/= 0.U) && (wbRdW === decoder.io.rs1),
                            wbDataW, rf.io.rs1Data)
  decoder.io.rs2Data := rf.io.rs2Data

  val idRs1Val = Mux(wbRegWriteW && (wbRdW =/= 0.U) && (wbRdW === decoder.io.rs1),
                     wbDataW, rf.io.rs1Data)
  val idRs2Val = Mux(wbRegWriteW && (wbRdW =/= 0.U) && (wbRdW === decoder.io.rs2),
                     wbDataW, rf.io.rs2Data)

  val sysDecCtrl = decoder.io.controls

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
  idExReg.io.in.controls    := sysDecCtrl.base

  // System sideband ID->EX: capture decoded metadata + resolved CSR data
  val sysIsCsrOp = sysDecCtrl.sysOp =/= SysOp.NONE
  val sysIdExIn = Wire(new SystemPipeBundle)
  // Sideband must stay valid for illegal instructions too: they
  // still reach MEM to raise the synchronous illegal-instruction trap.
  sysIdExIn.valid       := idValid && !flushIDEX
  sysIdExIn.sysOp       := sysDecCtrl.sysOp
  sysIdExIn.csrAddr     := sysDecCtrl.csrAddr
  sysIdExIn.csrWrite    := sysDecCtrl.csrWrite
  sysIdExIn.csrData     := sysDecCtrl.csrRs1Data
  sysIdExIn.isEcall     := sysDecCtrl.isEcall
  sysIdExIn.isEbreak    := sysDecCtrl.isEbreak
  sysIdExIn.isMret      := sysDecCtrl.isMret
  sysIdExIn.isSret      := sysDecCtrl.isSret
  sysIdExIn.isSfenceVma := sysDecCtrl.isSfenceVma
  sysIdExIn.isFenceI    := sysDecCtrl.isFenceI
  sysIdExIn.isFence     := sysDecCtrl.isFence
  sysIdExIn.sfenceRs1   := sysDecCtrl.sfenceRs1
  sysIdExIn.sfenceRs2   := sysDecCtrl.sfenceRs2
  sysIdExIn.isSys       := sysDecCtrl.sysOp =/= SysOp.NONE || sysDecCtrl.isEcall ||
                          sysDecCtrl.isEbreak || sysDecCtrl.isMret || sysDecCtrl.isSret ||
                          sysDecCtrl.isSfenceVma || sysDecCtrl.isFenceI || sysDecCtrl.isFence
  sysIdEx.io.stall := divHold
  sysIdEx.io.flush := flushIDEX
  sysIdEx.io.in    := sysIdExIn

  // =========================================================================
  // 5. EX stage — ALU/mem/capability (unchanged semantics from PipelinedCore)
  // =========================================================================
  val exPc          = idExReg.io.out.pc
  val exPcPlus4     = idExReg.io.out.pcPlus4
  val exInstruction = idExReg.io.out.instruction

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
    1.U -> wbDataW,
    2.U -> exMemReg.io.out.aluResult
  ))
  val forwardedRs2 = MuxLookup(forwardingUnit.io.forwardB, idExReg.io.out.rs2Data)(Seq(
    0.U -> idExReg.io.out.rs2Data,
    1.U -> wbDataW,
    2.U -> exMemReg.io.out.aluResult
  ))

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

  rv32mMult.io.rs1 := forwardedRs1
  rv32mMult.io.rs2 := forwardedRs2
  rv32mMult.io.mOp := exControls.mOp

  divRem.io.start    := exValid && isDivOp && !divRem.io.busy && !divRem.io.done
  divRem.io.dividend := forwardedRs1
  divRem.io.divisor  := forwardedRs2
  divRem.io.isSigned := (exControls.mOp === MOp.DIV || exControls.mOp === MOp.REM)

  val inCap = idExReg.io.out.capRs1Data
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

  val candDerivedCap = Wire(new CapabilityLite)
  candDerivedCap.tag    := inCap.tag
  candDerivedCap.base   := inCap.base
  candDerivedCap.length := inCap.length
  candDerivedCap.perms  := inCap.perms & forwardedRs2(3, 0)
  candDerivedCap.offset := inCap.offset

  val offsetExtS   = Cat(0.U(2.W), inCap.offset).asSInt
  val deltaExtS    = Cat(forwardedRs2(31), forwardedRs2(31), forwardedRs2).asSInt
  val newOffsetS   = offsetExtS +& deltaExtS
  val capLenExtS   = Cat(0.U(1.W), inCap.length).asSInt
  val cincOffsetOk = (newOffsetS >= 0.S) && (newOffsetS <= capLenExtS)
  val cincSuccess  = inCap.tag && cincOffsetOk

  val cincDerivedCap = Wire(new CapabilityLite)
  cincDerivedCap.tag    := cincSuccess
  cincDerivedCap.base   := inCap.base
  cincDerivedCap.length := inCap.length
  cincDerivedCap.perms  := inCap.perms
  cincDerivedCap.offset := newOffsetS(31, 0).asUInt

  val exCapViolationValid      = WireDefault(false.B)
  val exCapViolationReason     = WireDefault(SecurityReason.NONE)
  val exCapViolationAddress    = WireDefault(0.U(32.W))
  val exCapViolationAccessType = AccessType.CAPABILITY_OPERATION

  val exCapWriteData = WireDefault(CapabilityLite.nullCapability())
  val exCapRegWrite  = WireDefault(false.B)

  when(exValid && exControls.isCapOp) {
    switch(exControls.capOp) {
      is(CapOp.CSETBOUNDS) {
        when(csetSuccess) { exCapWriteData := csetDerivedCap; exCapRegWrite := true.B }
        .otherwise {
          exCapViolationValid   := true.B
          exCapViolationReason  := Mux(!inCap.tag, SecurityReason.INVALID_CAPABILITY, SecurityReason.MONOTONICITY)
          exCapViolationAddress := inCap.base + inCap.offset
        }
      }
      is(CapOp.CANDPERM) {
        when(inCap.tag) { exCapWriteData := candDerivedCap; exCapRegWrite := true.B }
        .otherwise { exCapViolationValid := true.B; exCapViolationReason := SecurityReason.INVALID_CAPABILITY; exCapViolationAddress := inCap.base }
      }
      is(CapOp.CINCOFFSET) {
        when(cincSuccess) { exCapWriteData := cincDerivedCap; exCapRegWrite := true.B }
        .otherwise {
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

  val capEffectiveAddress = (inCap.base + inCap.offset) + idExReg.io.out.imm

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

  bju.io.pc         := exPc
  bju.io.rs1Data    := forwardedRs1
  bju.io.rs2Data    := forwardedRs2
  bju.io.imm        := idExReg.io.out.imm
  bju.io.branchType := exControls.branchType
  bju.io.jumpType   := exControls.jumpType

  branchTakenW    := exValid && bju.io.taken
  redirectTargetW := bju.io.targetAddress
  hazardUnit.io.branchTaken := branchTakenW

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
  exMemReg.io.in.rs2Data                := forwardedRs2
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

  sysExMem.io.stall := false.B
  sysExMem.io.flush := flushEXMEM
  sysExMem.io.in    := sysIdEx.io.out

  // =========================================================================
  // 6. MEM stage — capability auth + system trap events + MMIO/timer
  // =========================================================================
  val memValid       = exMemReg.io.out.valid
  val memPc          = exMemReg.io.out.pc
  val memPcPlus4     = exMemReg.io.out.pcPlus4
  val memInstruction = exMemReg.io.out.instruction

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

  val secEvent = Wire(new SecurityViolationEvent)
  secEvent.valid      := isCapMemViolation || isDerivationViolation
  secEvent.pc         := exMemReg.io.out.pc
  secEvent.address    := Mux(isCapMemViolation, exMemReg.io.out.aluResult, exMemReg.io.out.capViolationAddress)
  secEvent.accessType := Mux(isCapMemViolation, Mux(exMemReg.io.out.memWrite, AccessType.WRITE, AccessType.READ), exMemReg.io.out.capViolationAccessType)
  secEvent.reason     := Mux(isCapMemViolation, capChecker.io.reason, exMemReg.io.out.capViolationReason)
  secEvent.context    := systemMMIO.io.currentContext

  systemMMIO.io.securityEvent := secEvent
  takePreciseTrap := secEvent.valid && systemMMIO.io.trapEnable && !systemMMIO.io.trapActive
  takeTrapReturn  := systemMMIO.io.takeTrapReturn

  // ---- System trap events at MEM (synchronous exceptions) ----
  val sysExMemOut = sysExMem.io.out
  val sysIsCsrAtMem = sysExMemOut.isSys && sysExMemOut.sysOp =/= SysOp.NONE

  // CSR legality probe at MEM (privilege + RO + known)
  csrFile.io.probeAddr    := sysExMemOut.csrAddr
  csrFile.io.probeIsWrite := sysExMemOut.csrWrite
  val csrIllegalAtMem = sysIsCsrAtMem && csrFile.io.probeIllegal

  // ECALL cause depends on current privilege
  val ecallCause = Mux(csrFile.io.privLevel === Privilege.U.U(2.W), TrapCause.ECALL_U.U,
                  Mux(csrFile.io.privLevel === Privilege.S.U(2.W), TrapCause.ECALL_S.U,
                      TrapCause.ECALL_M.U))

  val sysExcValid = memValid && sysExMemOut.valid &&
    (sysExMemOut.isEcall || sysExMemOut.isEbreak ||
     exMemReg.io.out.illegalInstruction || csrIllegalAtMem)

  val sysExcCause = Mux(exMemReg.io.out.illegalInstruction || csrIllegalAtMem,
                    TrapCause.ILLEGAL_INSTRUCTION.U,
                    Mux(sysExMemOut.isEbreak, TrapCause.BREAKPOINT.U, ecallCause))
  val sysExcTval  = Mux(exMemReg.io.out.illegalInstruction || csrIllegalAtMem,
                    memInstruction, 0.U)

  val sysTrapEvent = Wire(new TrapEventBundle)
  sysTrapEvent.valid      := sysExcValid
  sysTrapEvent.isInterrupt := false.B
  sysTrapEvent.cause      := sysExcCause
  sysTrapEvent.tval       := sysExcTval
  sysTrapEvent.epc        := memPc

  // ---- TrapController: exceptions (MEM) + interrupts (WB boundary) ----
  trapCtrl.io.trapEvent      := sysTrapEvent
  trapCtrl.io.privLevel      := csrFile.io.privLevel
  trapCtrl.io.medeleg        := csrFile.io.medelegOut
  trapCtrl.io.mideleg        := csrFile.io.midelegOut
  trapCtrl.io.mstatusMIE     := csrFile.io.mstatusOut(MstatusBits.MIE)
  trapCtrl.io.sstatusSIE     := csrFile.io.mstatusOut(SstatusView.SIE)
  trapCtrl.io.mtvec          := csrFile.io.mtvecOut
  trapCtrl.io.stvec          := csrFile.io.stvecOut
  trapCtrl.io.mip            := csrFile.io.mipOut
  trapCtrl.io.mie            := csrFile.io.mieOut
  trapCtrl.io.sip            := csrFile.io.sipViewOut
  trapCtrl.io.sie            := csrFile.io.sieViewOut
  trapCtrl.io.mtipExt        := timer.io.mtip
  trapCtrl.io.stipExt        := false.B // STIP is software-set via mip.STIP
  trapCtrl.io.curPc          := wbPcPlus4W
  trapCtrl.io.intrQualify    := memWbReg.io.out.valid // interrupts only at WB retirement boundary

  // trapCtrl.trapTaken covers BOTH the synchronous exception (from
  // sysTrapEvent) and asynchronous interrupts (intrQualify=wbValid). Both
  // flush younger stages and redirect through trapCtrl.io.trapVector.
  takeSysTrap := trapCtrl.io.trapTaken
  val sysTrapIsInterrupt = trapCtrl.io.trapTaken && !sysTrapEvent.valid

  // MRET/SRET at MEM: flush + redirect + trapReturn
  val isMretAtMem = memValid && sysExMemOut.isMret
  val isSretAtMem = memValid && sysExMemOut.isSret
  takeSysReturn := isMretAtMem || isSretAtMem
  sysReturnTarget := Mux(isMretAtMem, csrFile.io.mepcOut, csrFile.io.sepcOut)

  // ---- MMIO: SystemMMIO + TimerBlock share 0x8000xxxx ----
  systemMMIO.io.address     := exMemReg.io.out.aluResult
  val timerHit = timer.io.windowHit
  systemMMIO.io.memReadReq  := memValid && exMemReg.io.out.memRead && capAccessAllow && !timerHit
  systemMMIO.io.memWriteReq := memValid && exMemReg.io.out.memWrite && capAccessAllow && !timerHit
  systemMMIO.io.writeData   := exMemReg.io.out.rs2Data
  systemMMIO.io.memWidth    := exMemReg.io.out.memWidth

  timer.io.address     := exMemReg.io.out.aluResult
  timer.io.memReadReq  := memValid && exMemReg.io.out.memRead && capAccessAllow && timerHit
  timer.io.memWriteReq := memValid && exMemReg.io.out.memWrite && capAccessAllow && timerHit
  timer.io.writeData   := exMemReg.io.out.rs2Data
  timer.io.memWidth    := exMemReg.io.out.memWidth
  io.timerMtip        := timer.io.mtip

  val anyMmioHit = systemMMIO.io.windowHit // covers 0x8000xxxx incl. timer

  dmem.io.address   := exMemReg.io.out.aluResult
  dmem.io.writeData := exMemReg.io.out.rs2Data
  dmem.io.memRead   := memValid && exMemReg.io.out.memRead && capAccessAllow && !anyMmioHit
  dmem.io.memWrite  := memValid && exMemReg.io.out.memWrite && capAccessAllow && !anyMmioHit
  dmem.io.memWidth  := exMemReg.io.out.memWidth

  val memReadData = Mux(timerHit, timer.io.readData,
                    Mux(systemMMIO.io.windowHit, systemMMIO.io.readData, dmem.io.readData))

  // ---- CSRFile trap ports ----
  // trapEntry: driven for BOTH sync exceptions (epc=memPc) and interrupts
  // (epc=wbPcPlus4). TrapController already selected targetPriv/vector.
  csrFile.io.trapEntry := trapCtrl.io.trapEntry
  csrFile.io.trapReturn.valid := takeSysReturn
  csrFile.io.trapReturn.toM   := isMretAtMem

  // Interrupt vs exception redirect target: exceptions use trapCtrl vector
  // (already correct); interrupts also use trapCtrl vector. takeSysTrap
  // covers both — redirect uses trapCtrl.io.trapVector for either.
  // (renamed: sysTrap covers exception+interrupt since both come from trapCtrl)

  // ---- MEM->WB register ----
  val memEnterWbValid = memValid && !takePreciseTrap && !takeSysTrap
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
  memWbReg.io.in.capRegWrite        := exMemReg.io.out.capRegWrite && capAccessAllow && !takePreciseTrap && !takeSysTrap
  memWbReg.io.in.capRd              := exMemReg.io.out.capRd
  memWbReg.io.in.capWriteData       := exMemReg.io.out.capWriteData
  memWbReg.io.in.memRead            := memValid && exMemReg.io.out.memRead && capAccessAllow && Mux(timerHit || systemMMIO.io.windowHit, Mux(timerHit, timer.io.readAccepted, systemMMIO.io.readAccepted), !dmem.io.misaligned)
  memWbReg.io.in.memReadReq         := memValid && exMemReg.io.out.memRead
  memWbReg.io.in.memWrite           := memValid && exMemReg.io.out.memWrite && capAccessAllow && Mux(timerHit || systemMMIO.io.windowHit, Mux(timerHit, timer.io.writeAccepted, systemMMIO.io.writeAccepted), !dmem.io.misaligned)
  memWbReg.io.in.memWriteReq        := memValid && exMemReg.io.out.memWrite
  memWbReg.io.in.memAddress         := exMemReg.io.out.aluResult
  memWbReg.io.in.memWriteData       := exMemReg.io.out.rs2Data
  memWbReg.io.in.regWrite           := exMemReg.io.out.regWrite && !takePreciseTrap && !takeSysTrap && Mux(exMemReg.io.out.memRead, capAccessAllow && Mux(timerHit || systemMMIO.io.windowHit, Mux(timerHit, timer.io.readAccepted, systemMMIO.io.readAccepted), !dmem.io.misaligned), true.B)
  memWbReg.io.in.wbSource           := exMemReg.io.out.wbSource
  memWbReg.io.in.illegalInstruction := exMemReg.io.out.illegalInstruction
  memWbReg.io.in.telemetryValid     := exMemReg.io.out.telemetryValid && capAccessAllow && !takePreciseTrap && !takeSysTrap
  memWbReg.io.in.telemetryClaActive := exMemReg.io.out.telemetryClaActive
  memWbReg.io.in.telemetryMulActive := exMemReg.io.out.telemetryMulActive
  memWbReg.io.in.telemetryResult    := exMemReg.io.out.telemetryResult

  sysMemWb.io.stall := false.B
  sysMemWb.io.flush := false.B
  sysMemWb.io.in    := sysExMem.io.out

  // =========================================================================
  // 7. WB stage — CSR atomic RMW commit + writeback
  // =========================================================================
  val wbValid       = memWbReg.io.out.valid
  val wbPc          = memWbReg.io.out.pc
  val wbPcPlus4     = memWbReg.io.out.pcPlus4
  val wbInstruction = memWbReg.io.out.instruction

  // Forward declarations used above (chisel3 requires wires before use in
  // some contexts; these are combinationally resolved)
  // wbDataW / wbRdW / wbRegWriteW defined below — see note at bottom.

  // ---- CSR commit at WB (single retirement point => program order) ----
  val sysWb = sysMemWb.io.out
  val wbIsCsr = wbValid && sysWb.valid && sysWb.sysOp =/= SysOp.NONE

  // accessType: WRITE for CSRRW/I, SET for CSRRS/I, CLEAR for CSRRC/I;
  // read-only intent (rs1=x0 / zimm=0) collapses to READ
  val csrOpAccess = Mux(sysWb.sysOp === SysOp.CSRRW || sysWb.sysOp === SysOp.CSRRWI, 1.U(2.W),
                    Mux(sysWb.sysOp === SysOp.CSRRS || sysWb.sysOp === SysOp.CSRRSI, 2.U(2.W),
                        3.U(2.W)))
  val csrEffAccess = Mux(sysWb.csrWrite, csrOpAccess, 0.U(2.W))

  csrFile.io.csrAddr    := sysWb.csrAddr
  csrFile.io.accessType := csrEffAccess
  csrFile.io.rs1Data    := sysWb.csrData
  csrFile.io.writeEn    := wbIsCsr && sysWb.csrWrite

  val csrReadData = csrFile.io.rdData

  wbDataW := Mux(wbIsCsr, csrReadData,
            MuxLookup(memWbReg.io.out.wbSource, 0.U(32.W))(Seq(
              WBSource.ALU       -> memWbReg.io.out.aluResult,
              WBSource.MEM       -> memWbReg.io.out.memReadData,
              WBSource.PC_PLUS_4 -> wbPcPlus4,
              WBSource.IMM       -> memWbReg.io.out.imm
            )))

  wbRdW       := memWbReg.io.out.rd
  wbRegWriteW := wbValid && memWbReg.io.out.regWrite && (memWbReg.io.out.rd =/= 0.U)

  rf.io.rdAddress   := wbRdW
  rf.io.writeData   := wbDataW
  rf.io.writeEnable := wbRegWriteW

  capRf.io.wen   := wbValid && memWbReg.io.out.capRegWrite
  capRf.io.waddr := memWbReg.io.out.capRd
  capRf.io.wdata := memWbReg.io.out.capWriteData

  // =========================================================================
  // 8. Commit / observability
  // =========================================================================
  systemMMIO.io.telemetryValid      := wbValid && memWbReg.io.out.telemetryValid
  systemMMIO.io.telemetryClaActive  := memWbReg.io.out.telemetryClaActive
  systemMMIO.io.telemetryMulActive  := memWbReg.io.out.telemetryMulActive
  systemMMIO.io.telemetryResult     := memWbReg.io.out.telemetryResult

  systemMMIO.io.retireEvent         := wbValid
  systemMMIO.io.commitPc            := wbPc
  systemMMIO.io.branchTaken         := exValid && bju.io.taken && (exControls.branchType =/= BranchType.NONE) && (idExReg.io.out.controls.jumpType === JumpType.NONE)
  systemMMIO.io.loadUseStall        := hazardUnit.io.loadUseHazard
  systemMMIO.io.dividerBusy         := divRem.io.busy
  systemMMIO.io.pipelineStall       := stallIF

  io.commit.valid        := wbValid
  io.commit.pc           := wbPc
  io.commit.instruction  := wbInstruction
  io.commit.rd           := wbRdW
  io.commit.regWrite     := wbRegWriteW
  io.commit.writeData    := wbDataW
  io.commit.memRead      := memWbReg.io.out.memRead
  io.commit.memReadReq   := memWbReg.io.out.memReadReq
  io.commit.memWrite     := memWbReg.io.out.memWrite
  io.commit.memWriteReq  := memWbReg.io.out.memWriteReq
  io.commit.memAddress   := memWbReg.io.out.memAddress
  io.commit.memWriteData := memWbReg.io.out.memWriteData
  io.commit.illegal      := memWbReg.io.out.illegalInstruction

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
  io.branchTaken    := branchTakenW
  io.redirectTarget := redirectTargetW
  io.forwardA       := forwardingUnit.io.forwardA
  io.forwardB       := forwardingUnit.io.forwardB
  io.loadUseHazard  := hazardUnit.io.loadUseHazard
  io.capHazard      := hazardUnit.io.capHazard
  io.stallIF        := stallIF
  io.stallID        := stallID
  io.mOp            := exControls.mOp
  io.mulActive      := exControls.isMul
  io.dividerBusy    := divRem.io.busy
  io.dividerDone    := divRem.io.done
  io.dividerIteration := divRem.io.iteration
  io.schedHint      := systemMMIO.io.schedHint
  io.processBehaviorClass := systemMMIO.io.processBehaviorClass
  io.currentContext := systemMMIO.io.currentContext
  io.trap.trapTaken  := takePreciseTrap || takeSysTrap
  io.trap.trapTarget := Mux(takePreciseTrap, systemMMIO.io.trapVector, trapCtrl.io.trapVector)
  io.trap.trapEpc    := Mux(takePreciseTrap, systemMMIO.io.trapEpc, wbPcPlus4W)
  io.trap.trapCause  := Mux(takePreciseTrap, systemMMIO.io.trapCause, sysExcCause)
  io.trap.trapAddr   := Mux(takePreciseTrap, systemMMIO.io.trapAddr, sysExcTval)
  io.trap.trapActive := systemMMIO.io.trapActive || sysTrapIsInterrupt || sysExcValid

  io.privLevel      := csrFile.io.privLevel
  io.trapTakenSys   := takeSysTrap || sysTrapIsInterrupt
  io.sysTrapVector  := trapCtrl.io.trapVector
  io.sysTrapEpc     := Mux(sysExcValid, memPc, wbPcPlus4W)
  io.sysTrapCause   := sysExcCause

  // =========================================================================
  // Forward-declared wires (must be declared before use in Chisel3 strict mode;
  // they are combinationally driven above/below)
  // =========================================================================
  // These are declared here to satisfy ordering; actual drivers above.
}
