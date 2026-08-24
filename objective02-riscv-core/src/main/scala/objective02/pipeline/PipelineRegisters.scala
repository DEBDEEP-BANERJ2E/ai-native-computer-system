package objective02.pipeline

import chisel3._
import chisel3.util._
import objective02.decode._
import objective02.capability.CapabilityLite

// =========================================================================
// 1. IF/ID Pipeline Register Bundle & Module
// =========================================================================
class IF_ID_Bundle extends Bundle {
  val valid       = Bool()
  val pc          = UInt(32.W)
  val pcPlus4     = UInt(32.W)
  val instruction = UInt(32.W)
}

class IF_ID_Register extends Module {
  val io = IO(new Bundle {
    val stall = Input(Bool())
    val flush = Input(Bool())
    val in    = Input(new IF_ID_Bundle)
    val out   = Output(new IF_ID_Bundle)
  })

  val reg = RegInit({
    val b = Wire(new IF_ID_Bundle)
    b.valid       := false.B
    b.pc          := 0.U(32.W)
    b.pcPlus4     := 0.U(32.W)
    b.instruction := "h00000013".U(32.W) // NOP: addi x0, x0, 0
    b
  })

  when(io.flush) {
    reg.valid       := false.B
    reg.pc          := 0.U(32.W)
    reg.pcPlus4     := 0.U(32.W)
    reg.instruction := "h00000013".U(32.W)
  }.elsewhen(!io.stall) {
    reg := io.in
  }

  io.out := reg
}

// =========================================================================
// 2. ID/EX Pipeline Register Bundle & Module
// =========================================================================
class ID_EX_Bundle extends Bundle {
  val valid       = Bool()
  val pc          = UInt(32.W)
  val pcPlus4     = UInt(32.W)
  val instruction = UInt(32.W)

  // Operands and bitfield addresses
  val rs1         = UInt(5.W)
  val rs2         = UInt(5.W)
  val rd          = UInt(5.W)
  val rs1Data     = UInt(32.W)
  val rs2Data     = UInt(32.W)
  val imm         = UInt(32.W)

  // Capability operands
  val capRs1Data  = new CapabilityLite
  val capRd       = UInt(3.W)
  val capCs1      = UInt(3.W)

  // Decoded Control Signals
  val controls    = new ControlSignalsBundle
}

class ID_EX_Register extends Module {
  val io = IO(new Bundle {
    val stall = Input(Bool())
    val flush = Input(Bool())
    val in    = Input(new ID_EX_Bundle)
    val out   = Output(new ID_EX_Bundle)
  })

  val reg = RegInit({
    val b = Wire(new ID_EX_Bundle)
    b.valid                       := false.B
    b.pc                          := 0.U(32.W)
    b.pcPlus4                     := 0.U(32.W)
    b.instruction                 := "h00000013".U(32.W)
    b.rs1                         := 0.U(5.W)
    b.rs2                         := 0.U(5.W)
    b.rd                          := 0.U(5.W)
    b.rs1Data                     := 0.U(32.W)
    b.rs2Data                     := 0.U(32.W)
    b.imm                         := 0.U(32.W)
    b.capRs1Data                  := CapabilityLite.nullCapability()
    b.capRd                       := 0.U(3.W)
    b.capCs1                      := 0.U(3.W)
    b.controls.regWrite           := false.B
    b.controls.aluSrcA            := ALUSrcA.ZERO
    b.controls.aluSrcB            := ALUSrcB.IMM
    b.controls.aluOp              := ALUOps.ADD
    b.controls.isMul              := false.B
    b.controls.mOp                := MOp.NONE
    b.controls.isSecurityOp       := false.B
    b.controls.isCapOp            := false.B
    b.controls.capOp              := CapOp.NONE
    b.controls.isCapMem           := false.B
    b.controls.capRegWrite        := false.B
    b.controls.usesCapRs1         := false.B
    b.controls.usesIntRs1         := false.B
    b.controls.usesIntRs2         := false.B
    b.controls.memRead            := false.B
    b.controls.memWrite           := false.B
    b.controls.memWidth           := MemWidth.WORD
    b.controls.branchType         := BranchType.NONE
    b.controls.jumpType           := JumpType.NONE
    b.controls.wbSource           := WBSource.ALU
    b.controls.illegalInstruction := false.B
    b
  })

  when(io.flush) {
    reg.valid                     := false.B
    reg.pc                        := 0.U(32.W)
    reg.pcPlus4                   := 0.U(32.W)
    reg.instruction               := "h00000013".U(32.W)
    reg.rs1                       := 0.U(5.W)
    reg.rs2                       := 0.U(5.W)
    reg.rd                        := 0.U(5.W)
    reg.rs1Data                   := 0.U(32.W)
    reg.rs2Data                   := 0.U(32.W)
    reg.imm                       := 0.U(32.W)
    reg.capRs1Data                := CapabilityLite.nullCapability()
    reg.capRd                     := 0.U(3.W)
    reg.capCs1                    := 0.U(3.W)
    reg.controls.regWrite           := false.B
    reg.controls.aluSrcA            := ALUSrcA.ZERO
    reg.controls.aluSrcB            := ALUSrcB.IMM
    reg.controls.aluOp              := ALUOps.ADD
    reg.controls.isMul              := false.B
    reg.controls.mOp                := MOp.NONE
    reg.controls.isSecurityOp       := false.B
    reg.controls.isCapOp            := false.B
    reg.controls.capOp              := CapOp.NONE
    reg.controls.isCapMem           := false.B
    reg.controls.capRegWrite        := false.B
    reg.controls.usesCapRs1         := false.B
    reg.controls.usesIntRs1         := false.B
    reg.controls.usesIntRs2         := false.B
    reg.controls.memRead            := false.B
    reg.controls.memWrite           := false.B
    reg.controls.memWidth           := MemWidth.WORD
    reg.controls.branchType         := BranchType.NONE
    reg.controls.jumpType           := JumpType.NONE
    reg.controls.wbSource           := WBSource.ALU
    reg.controls.illegalInstruction := false.B
  }.elsewhen(!io.stall) {
    reg := io.in
  }

  io.out := reg
}

// =========================================================================
// 3. EX/MEM Pipeline Register Bundle & Module
// =========================================================================
class EX_MEM_Bundle extends Bundle {
  val valid       = Bool()
  val pc          = UInt(32.W)
  val pcPlus4     = UInt(32.W)
  val instruction = UInt(32.W)
  val rd          = UInt(5.W)

  // Execution outputs
  val aluResult   = UInt(32.W)
  val rs2Data     = UInt(32.W) // Store payload
  val imm         = UInt(32.W)

  // Capability execution outputs & metadata
  val capRegWrite          = Bool()
  val capRd                = UInt(3.W)
  val capWriteData         = new CapabilityLite
  val isCapMem             = Bool()
  val capSource            = new CapabilityLite
  val capViolationValid    = Bool()
  val capViolationReason   = UInt(4.W)
  val capViolationAddress  = UInt(32.W)
  val capViolationAccessType = UInt(2.W)

  // Downstream Controls
  val regWrite           = Bool()
  val memRead            = Bool()
  val memWrite           = Bool()
  val memWidth           = UInt(3.W)
  val wbSource           = UInt(2.W)
  val illegalInstruction = Bool()

  // Telemetry metadata
  val telemetryValid     = Bool()
  val telemetryClaActive = Bool()
  val telemetryMulActive = Bool()
  val telemetryResult    = UInt(32.W)
}

class EX_MEM_Register extends Module {
  val io = IO(new Bundle {
    val stall = Input(Bool())
    val flush = Input(Bool())
    val in    = Input(new EX_MEM_Bundle)
    val out   = Output(new EX_MEM_Bundle)
  })

  val reg = RegInit({
    val b = Wire(new EX_MEM_Bundle)
    b.valid                  := false.B
    b.pc                     := 0.U(32.W)
    b.pcPlus4                := 0.U(32.W)
    b.instruction            := "h00000013".U(32.W)
    b.rd                     := 0.U(5.W)
    b.aluResult              := 0.U(32.W)
    b.rs2Data                := 0.U(32.W)
    b.imm                    := 0.U(32.W)
    b.capRegWrite            := false.B
    b.capRd                  := 0.U(3.W)
    b.capWriteData           := CapabilityLite.nullCapability()
    b.isCapMem               := false.B
    b.capSource              := CapabilityLite.nullCapability()
    b.capViolationValid      := false.B
    b.capViolationReason     := 0.U(4.W)
    b.capViolationAddress    := 0.U(32.W)
    b.capViolationAccessType := 0.U(2.W)
    b.regWrite               := false.B
    b.memRead                := false.B
    b.memWrite               := false.B
    b.memWidth               := MemWidth.WORD
    b.wbSource               := WBSource.ALU
    b.illegalInstruction     := false.B
    b.telemetryValid         := false.B
    b.telemetryClaActive     := false.B
    b.telemetryMulActive     := false.B
    b.telemetryResult        := 0.U(32.W)
    b
  })

  when(io.flush) {
    reg.valid                  := false.B
    reg.pc                     := 0.U(32.W)
    reg.pcPlus4                := 0.U(32.W)
    reg.instruction            := "h00000013".U(32.W)
    reg.rd                     := 0.U(5.W)
    reg.aluResult              := 0.U(32.W)
    reg.rs2Data                := 0.U(32.W)
    reg.imm                    := 0.U(32.W)
    reg.capRegWrite            := false.B
    reg.capRd                  := 0.U(3.W)
    reg.capWriteData           := CapabilityLite.nullCapability()
    reg.isCapMem               := false.B
    reg.capSource              := CapabilityLite.nullCapability()
    reg.capViolationValid      := false.B
    reg.capViolationReason     := 0.U(4.W)
    reg.capViolationAddress    := 0.U(32.W)
    reg.capViolationAccessType := 0.U(2.W)
    reg.regWrite               := false.B
    reg.memRead                := false.B
    reg.memWrite               := false.B
    reg.memWidth               := MemWidth.WORD
    reg.wbSource               := WBSource.ALU
    reg.illegalInstruction     := false.B
    reg.telemetryValid         := false.B
    reg.telemetryClaActive     := false.B
    reg.telemetryMulActive     := false.B
    reg.telemetryResult        := 0.U(32.W)
  }.elsewhen(!io.stall) {
    reg := io.in
  }

  io.out := reg
}

// =========================================================================
// 4. MEM/WB Pipeline Register Bundle & Module
// =========================================================================
class MEM_WB_Bundle extends Bundle {
  val valid       = Bool()
  val pc          = UInt(32.W)
  val pcPlus4     = UInt(32.W)
  val instruction = UInt(32.W)
  val rd          = UInt(5.W)

  // Computed results and memory read data
  val aluResult   = UInt(32.W)
  val memReadData = UInt(32.W)
  val imm         = UInt(32.W)

  // Capability writeback state
  val capRegWrite  = Bool()
  val capRd        = UInt(3.W)
  val capWriteData = new CapabilityLite

  // Memory transaction visibility (for exact architectural commit export)
  val memRead            = Bool()
  val memReadReq         = Bool()
  val memWrite           = Bool()
  val memWriteReq        = Bool()
  val memAddress         = UInt(32.W)
  val memWriteData       = UInt(32.W)

  // Writeback controls
  val regWrite           = Bool()
  val wbSource           = UInt(2.W)
  val illegalInstruction = Bool()

  // Telemetry metadata
  val telemetryValid     = Bool()
  val telemetryClaActive = Bool()
  val telemetryMulActive = Bool()
  val telemetryResult    = UInt(32.W)
}

class MEM_WB_Register extends Module {
  val io = IO(new Bundle {
    val stall = Input(Bool())
    val flush = Input(Bool())
    val in    = Input(new MEM_WB_Bundle)
    val out   = Output(new MEM_WB_Bundle)
  })

  val reg = RegInit({
    val b = Wire(new MEM_WB_Bundle)
    b.valid              := false.B
    b.pc                 := 0.U(32.W)
    b.pcPlus4            := 0.U(32.W)
    b.instruction        := "h00000013".U(32.W)
    b.rd                 := 0.U(5.W)
    b.aluResult          := 0.U(32.W)
    b.memReadData        := 0.U(32.W)
    b.imm                := 0.U(32.W)
    b.capRegWrite        := false.B
    b.capRd              := 0.U(3.W)
    b.capWriteData       := CapabilityLite.nullCapability()
    b.memRead            := false.B
    b.memReadReq         := false.B
    b.memWrite           := false.B
    b.memWriteReq        := false.B
    b.memAddress         := 0.U(32.W)
    b.memWriteData       := 0.U(32.W)
    b.regWrite           := false.B
    b.wbSource           := WBSource.ALU
    b.illegalInstruction := false.B
    b.telemetryValid     := false.B
    b.telemetryClaActive := false.B
    b.telemetryMulActive := false.B
    b.telemetryResult    := 0.U(32.W)
    b
  })

  when(io.flush) {
    reg.valid              := false.B
    reg.pc                 := 0.U(32.W)
    reg.pcPlus4            := 0.U(32.W)
    reg.instruction        := "h00000013".U(32.W)
    reg.rd                 := 0.U(5.W)
    reg.aluResult          := 0.U(32.W)
    reg.memReadData        := 0.U(32.W)
    reg.imm                := 0.U(32.W)
    reg.capRegWrite        := false.B
    reg.capRd              := 0.U(3.W)
    reg.capWriteData       := CapabilityLite.nullCapability()
    reg.memRead            := false.B
    reg.memReadReq         := false.B
    reg.memWrite           := false.B
    reg.memWriteReq        := false.B
    reg.memAddress         := 0.U(32.W)
    reg.memWriteData       := 0.U(32.W)
    reg.regWrite           := false.B
    reg.wbSource           := WBSource.ALU
    reg.illegalInstruction := false.B
    reg.telemetryValid     := false.B
    reg.telemetryClaActive := false.B
    reg.telemetryMulActive := false.B
    reg.telemetryResult    := 0.U(32.W)
  }.elsewhen(!io.stall) {
    reg := io.in
  }

  io.out := reg
}
