package objective02.system

import chisel3._
import chisel3.util._
import objective01.telemetry.TelemetryBlock
import objective02.decode.MemWidth

class SystemMMIOIO extends Bundle {
  // Memory stage bus interface
  val address                = Input(UInt(32.W))
  val memReadReq             = Input(Bool())
  val memWriteReq            = Input(Bool())
  val writeData              = Input(UInt(32.W))
  val memWidth               = Input(UInt(3.W))
  val readData               = Output(UInt(32.W))
  val windowHit              = Output(Bool())
  val readAccepted           = Output(Bool())
  val writeAccepted          = Output(Bool())

  // Software hint outputs to hardware
  val schedHint              = Output(UInt(32.W))
  val processBehaviorClass   = Output(UInt(32.W))
  val currentContext         = Output(UInt(32.W))

  // Hardware event inputs (pipeline monitoring)
  val retireEvent            = Input(Bool())
  val commitPc               = Input(UInt(32.W))
  val branchTaken            = Input(Bool())
  val loadUseStall           = Input(Bool())
  val dividerBusy            = Input(Bool())
  val pipelineStall          = Input(Bool())

  // Telemetry update interface (driven at WB stage upon instruction retirement)
  val telemetryValid         = Input(Bool())
  val telemetryClaActive     = Input(Bool())
  val telemetryMulActive     = Input(Bool())
  val telemetryResult        = Input(UInt(32.W))

  // Hardware security violation event interface
  val securityEvent          = Input(new SecurityViolationEvent)

  // Objective 2 Phase 8 Precise Trap Interface
  val trapEnable             = Output(Bool())
  val trapActive             = Output(Bool())
  val trapVector             = Output(UInt(32.W))
  val trapEpc                = Output(UInt(32.W))
  val takeTrapReturn         = Output(Bool())
  val trapDoubleFault        = Output(Bool())
  val trapCause              = Output(UInt(32.W))
  val trapAddr               = Output(UInt(32.W))
}

class SystemMMIO extends Module {
  val io = IO(new SystemMMIOIO)

  // =========================================================================
  // 1. Objective 1 Telemetry Block Instance
  // =========================================================================
  val telemetry = Module(new TelemetryBlock(32))
  telemetry.io.operationValid      := io.telemetryValid
  telemetry.io.reversibleOperation := false.B
  telemetry.io.claActive           := io.telemetryClaActive
  telemetry.io.multiplierActive    := io.telemetryMulActive
  telemetry.io.result              := io.telemetryResult
  telemetry.io.readAddress         := io.address

  // =========================================================================
  // 2. Objective 2 System Control & Telemetry Registers
  // =========================================================================
  val processBehaviorClassReg = RegInit(0.U(32.W))
  val schedHintReg            = RegInit(0.U(32.W))
  val currentContextReg       = RegInit(0.U(32.W))
  val retiredCountReg         = RegInit(0.U(32.W))
  val branchTakenCountReg     = RegInit(0.U(32.W))
  val loadUseStallCountReg     = RegInit(0.U(32.W))
  val divBusyCyclesReg        = RegInit(0.U(32.W))
  val pipelineStallCountReg   = RegInit(0.U(32.W))
  val lastCommitPcReg         = RegInit(0.U(32.W))

  io.schedHint            := schedHintReg
  io.processBehaviorClass := processBehaviorClassReg
  io.currentContext       := currentContextReg

  // Performance event counter updates
  when(io.retireEvent) {
    retiredCountReg := retiredCountReg + 1.U
    lastCommitPcReg := io.commitPc
  }
  when(io.branchTaken) {
    branchTakenCountReg := branchTakenCountReg + 1.U
  }
  when(io.loadUseStall) {
    loadUseStallCountReg := loadUseStallCountReg + 1.U
  }
  when(io.dividerBusy) {
    divBusyCyclesReg := divBusyCyclesReg + 1.U
  }
  when(io.pipelineStall) {
    pipelineStallCountReg := pipelineStallCountReg + 1.U
  }

  // =========================================================================
  // 3. Address Decoding & Alignment
  // =========================================================================
  val windowHit = (io.address(31, 16) === "h8000".U)
  io.windowHit := windowHit

  val isAlignedWord = (io.address(1, 0) === 0.U) && (io.memWidth === MemWidth.WORD)

  // =========================================================================
  // 4. Security Event Sticky Logger Window (Phase 6 Audit Trail)
  // =========================================================================
  val secPendingReg    = RegInit(false.B)
  val secPcReg         = RegInit(0.U(32.W))
  val secAddrReg       = RegInit(0.U(32.W))
  val secAccessTypeReg = RegInit(0.U(2.W))
  val secReasonReg     = RegInit(0.U(4.W))
  val secContextReg    = RegInit(0.U(32.W))

  // Write-1-to-clear request on SEC_STATUS[0]
  val w1cClear = isAlignedWord && io.memWriteReq && (io.address === MMIOAddress.SEC_STATUS) && (io.writeData(0) === 1.U)

  when(w1cClear) {
    secPendingReg := false.B
  }

  // Sticky first-event capture with new-event priority over simultaneous W1C clear
  when(io.securityEvent.valid && (!secPendingReg || w1cClear)) {
    secPendingReg    := true.B
    secPcReg         := io.securityEvent.pc
    secAddrReg       := io.securityEvent.address
    secAccessTypeReg := io.securityEvent.accessType
    secReasonReg     := io.securityEvent.reason
    secContextReg    := io.securityEvent.context
  }

  // =========================================================================
  // 5. Objective 2 Phase 8 Dedicated Architectural Trap State Machine
  // =========================================================================
  val trapEnableReg      = RegInit(false.B)
  val trapActiveReg      = RegInit(false.B)
  val trapDoubleFaultReg = RegInit(false.B)
  val trapVectorReg      = RegInit("h00000800".U(32.W))
  val trapEpcReg         = RegInit(0.U(32.W))
  val trapCauseReg       = RegInit(0.U(32.W))
  val trapAddrReg        = RegInit(0.U(32.W))
  val trapContextReg     = RegInit(0.U(32.W))

  // Combinational trap trigger condition from current MEM-stage fault
  val takePreciseTrap = io.securityEvent.valid && trapEnableReg && !trapActiveReg
  val nestedFault     = io.securityEvent.valid && trapEnableReg && trapActiveReg

  // Trap Return Command: SW writes 1 to TRAP_RETURN (0x80002130) while ACTIVE=1
  val trapReturnReq  = isAlignedWord && io.memWriteReq && (io.address === MMIOAddress.TRAP_RETURN) && (io.writeData(0) === 1.U) && trapActiveReg
  val takeTrapReturn = trapReturnReq

  // W1C on TRAP_STATUS.DOUBLE_FAULT (bit 1)
  val w1cDoubleFault = isAlignedWord && io.memWriteReq && (io.address === MMIOAddress.TRAP_STATUS) && (io.writeData(1) === 1.U)

  when(w1cDoubleFault) {
    trapDoubleFaultReg := false.B
  }
  // New nested fault beats simultaneous W1C clear!
  when(nestedFault) {
    trapDoubleFaultReg := true.B
  }

  // Primary trap capture: latches fresh TRAP_* evidence when takePreciseTrap occurs
  when(takePreciseTrap) {
    trapActiveReg  := true.B
    trapEpcReg     := io.securityEvent.pc
    trapCauseReg   := Cat(0.U(26.W), io.securityEvent.accessType, io.securityEvent.reason)
    trapAddrReg    := io.securityEvent.address
    trapContextReg := io.securityEvent.context
  }.elsewhen(takeTrapReturn) {
    trapActiveReg  := false.B
  }

  io.trapEnable      := trapEnableReg
  io.trapActive      := trapActiveReg
  io.trapVector      := trapVectorReg
  io.trapEpc         := trapEpcReg
  io.takeTrapReturn  := takeTrapReturn
  io.trapDoubleFault := trapDoubleFaultReg
  io.trapCause       := trapCauseReg
  io.trapAddr        := trapAddrReg

  // Default bus response values
  val readDataWire      = WireDefault(0.U(32.W))
  val readAcceptedWire  = WireDefault(false.B)
  val writeAcceptedWire = WireDefault(false.B)

  when(isAlignedWord) {
    // -----------------------------------------------------------------------
    // Memory-Mapped Read Decoding
    // -----------------------------------------------------------------------
    when(io.memReadReq) {
      switch(io.address) {
        // Objective 1 Telemetry Block Window
        is(MMIOAddress.REV_ENERGY_ACC) {
          readDataWire     := telemetry.io.readData
          readAcceptedWire := true.B
        }
        is(MMIOAddress.CLA_SWITCHING) {
          readDataWire     := telemetry.io.readData
          readAcceptedWire := true.B
        }
        is(MMIOAddress.MUL_THERMAL) {
          readDataWire     := telemetry.io.readData
          readAcceptedWire := true.B
        }
        is(MMIOAddress.EDP_CURRENT) {
          readDataWire     := telemetry.io.readData
          readAcceptedWire := true.B
        }
        is(MMIOAddress.EDP_CONFIG) {
          readDataWire     := telemetry.io.readData
          readAcceptedWire := true.B
        }

        // Objective 2 System Control / Performance Counter Window
        is(MMIOAddress.BRANCH_CONFIDENCE) {
          readDataWire     := 0.U // Reserved, read-only
          readAcceptedWire := true.B
        }
        is(MMIOAddress.PROCESS_BEHAVIOR_CLASS) {
          readDataWire     := processBehaviorClassReg
          readAcceptedWire := true.B
        }
        is(MMIOAddress.SCHED_HINT) {
          readDataWire     := schedHintReg
          readAcceptedWire := true.B
        }
        is(MMIOAddress.RETIRED_COUNT) {
          readDataWire     := retiredCountReg
          readAcceptedWire := true.B
        }
        is(MMIOAddress.BRANCH_TAKEN_COUNT) {
          readDataWire     := branchTakenCountReg
          readAcceptedWire := true.B
        }
        is(MMIOAddress.LOAD_USE_STALL_COUNT) {
          readDataWire     := loadUseStallCountReg
          readAcceptedWire := true.B
        }
        is(MMIOAddress.DIV_BUSY_CYCLES) {
          readDataWire     := divBusyCyclesReg
          readAcceptedWire := true.B
        }
        is(MMIOAddress.PIPELINE_STALL_COUNT) {
          readDataWire     := pipelineStallCountReg
          readAcceptedWire := true.B
        }
        is(MMIOAddress.LAST_COMMIT_PC) {
          readDataWire     := lastCommitPcReg
          readAcceptedWire := true.B
        }
        is(MMIOAddress.CURRENT_CONTEXT) {
          readDataWire     := currentContextReg
          readAcceptedWire := true.B
        }

        // Objective 2 Security Event Sticky Logger Window (Phase 6 Audit Trail)
        is(MMIOAddress.SEC_STATUS) {
          readDataWire     := Cat(0.U(31.W), secPendingReg)
          readAcceptedWire := true.B
        }
        is(MMIOAddress.SEC_PC) {
          readDataWire     := secPcReg
          readAcceptedWire := true.B
        }
        is(MMIOAddress.SEC_ADDR) {
          readDataWire     := secAddrReg
          readAcceptedWire := true.B
        }
        is(MMIOAddress.SEC_INFO) {
          readDataWire     := Cat(0.U(26.W), secAccessTypeReg, secReasonReg)
          readAcceptedWire := true.B
        }
        is(MMIOAddress.SEC_CONTEXT) {
          readDataWire     := secContextReg
          readAcceptedWire := true.B
        }

        // Objective 2 Phase 8 Dedicated Precise Trap Window
        is(MMIOAddress.TRAP_CONTROL) {
          readDataWire     := Cat(0.U(31.W), trapEnableReg)
          readAcceptedWire := true.B
        }
        is(MMIOAddress.TRAP_STATUS) {
          readDataWire     := Cat(0.U(30.W), trapDoubleFaultReg, trapActiveReg)
          readAcceptedWire := true.B
        }
        is(MMIOAddress.TRAP_VECTOR) {
          readDataWire     := trapVectorReg
          readAcceptedWire := true.B
        }
        is(MMIOAddress.TRAP_EPC) {
          readDataWire     := trapEpcReg
          readAcceptedWire := true.B
        }
        is(MMIOAddress.TRAP_CAUSE) {
          readDataWire     := trapCauseReg
          readAcceptedWire := true.B
        }
        is(MMIOAddress.TRAP_ADDR) {
          readDataWire     := trapAddrReg
          readAcceptedWire := true.B
        }
        is(MMIOAddress.TRAP_CONTEXT) {
          readDataWire     := trapContextReg
          readAcceptedWire := true.B
        }
        is(MMIOAddress.TRAP_RETURN) {
          readDataWire     := 0.U // Write-only
          readAcceptedWire := true.B
        }
      }
    }

    // -----------------------------------------------------------------------
    // Memory-Mapped Write Decoding
    // -----------------------------------------------------------------------
    when(io.memWriteReq) {
      switch(io.address) {
        is(MMIOAddress.PROCESS_BEHAVIOR_CLASS) {
          processBehaviorClassReg := io.writeData
          writeAcceptedWire       := true.B
        }
        is(MMIOAddress.SCHED_HINT) {
          schedHintReg      := io.writeData
          writeAcceptedWire := true.B
        }
        is(MMIOAddress.CURRENT_CONTEXT) {
          currentContextReg := io.writeData
          writeAcceptedWire := true.B
        }
        is(MMIOAddress.SEC_STATUS) {
          writeAcceptedWire := true.B
        }
        is(MMIOAddress.TRAP_CONTROL) {
          trapEnableReg     := io.writeData(0)
          writeAcceptedWire := true.B
        }
        is(MMIOAddress.TRAP_STATUS) {
          writeAcceptedWire := true.B
        }
        is(MMIOAddress.TRAP_VECTOR) {
          trapVectorReg     := io.writeData & "hFFFFFFFC".U(32.W) // 4-byte aligned
          writeAcceptedWire := true.B
        }
        is(MMIOAddress.TRAP_EPC) {
          when(trapActiveReg) {
            trapEpcReg := io.writeData & "hFFFFFFFC".U(32.W) // writeable by handler while ACTIVE
          }
          writeAcceptedWire := true.B
        }
        is(MMIOAddress.TRAP_RETURN) {
          writeAcceptedWire := true.B // Command store accepted and retires normally!
        }
      }
    }
  }

  io.readData      := readDataWire
  io.readAccepted  := readAcceptedWire
  io.writeAccepted := writeAcceptedWire
}
