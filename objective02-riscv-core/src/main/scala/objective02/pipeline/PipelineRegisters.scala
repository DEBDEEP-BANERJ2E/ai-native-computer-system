package objective02.pipeline

import chisel3._
import chisel3.util._
import objective02.decode._

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

  // Synchronous pipeline register initialized to invalid bubble
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
    b.valid       := false.B
    b.pc          := 0.U(32.W)
    b.pcPlus4     := 0.U(32.W)
    b.instruction := "h00000013".U(32.W)
    b.rs1         := 0.U(5.W)
    b.rs2         := 0.U(5.W)
    b.rd          := 0.U(5.W)
    b.rs1Data     := 0.U(32.W)
    b.rs2Data     := 0.U(32.W)
    b.imm         := 0.U(32.W)
    b.controls.regWrite           := false.B
    b.controls.aluSrcA            := ALUSrcA.ZERO
    b.controls.aluSrcB            := ALUSrcB.IMM
    b.controls.aluOp              := ALUOps.ADD
    b.controls.isMul              := false.B
    b.controls.mOp                := MOp.NONE
    b.controls.isSecurityOp       := false.B
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
    reg.controls.regWrite           := false.B
    reg.controls.aluSrcA            := ALUSrcA.ZERO
    reg.controls.aluSrcB            := ALUSrcB.IMM
    reg.controls.aluOp              := ALUOps.ADD
    reg.controls.isMul              := false.B
    reg.controls.mOp                := MOp.NONE
    reg.controls.isSecurityOp       := false.B
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

  // Downstream Controls
  val regWrite           = Bool()
  val memRead            = Bool()
  val memWrite           = Bool()
  val memWidth           = UInt(3.W)
  val wbSource           = UInt(2.W)
  val illegalInstruction = Bool()
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
    b.valid              := false.B
    b.pc                 := 0.U(32.W)
    b.pcPlus4            := 0.U(32.W)
    b.instruction        := "h00000013".U(32.W)
    b.rd                 := 0.U(5.W)
    b.aluResult          := 0.U(32.W)
    b.rs2Data            := 0.U(32.W)
    b.imm                := 0.U(32.W)
    b.regWrite           := false.B
    b.memRead            := false.B
    b.memWrite           := false.B
    b.memWidth           := MemWidth.WORD
    b.wbSource           := WBSource.ALU
    b.illegalInstruction := false.B
    b
  })

  when(io.flush) {
    reg.valid              := false.B
    reg.pc                 := 0.U(32.W)
    reg.pcPlus4            := 0.U(32.W)
    reg.instruction        := "h00000013".U(32.W)
    reg.rd                 := 0.U(5.W)
    reg.aluResult          := 0.U(32.W)
    reg.rs2Data            := 0.U(32.W)
    reg.imm                := 0.U(32.W)
    reg.regWrite           := false.B
    reg.memRead            := false.B
    reg.memWrite           := false.B
    reg.memWidth           := MemWidth.WORD
    reg.wbSource           := WBSource.ALU
    reg.illegalInstruction := false.B
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
    b.memRead            := false.B
    b.memReadReq         := false.B
    b.memWrite           := false.B
    b.memWriteReq        := false.B
    b.memAddress         := 0.U(32.W)
    b.memWriteData       := 0.U(32.W)
    b.regWrite           := false.B
    b.wbSource           := WBSource.ALU
    b.illegalInstruction := false.B
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
    reg.memRead            := false.B
    reg.memReadReq         := false.B
    reg.memWrite           := false.B
    reg.memWriteReq        := false.B
    reg.memAddress         := 0.U(32.W)
    reg.memWriteData       := 0.U(32.W)
    reg.regWrite           := false.B
    reg.wbSource           := WBSource.ALU
    reg.illegalInstruction := false.B
  }.elsewhen(!io.stall) {
    reg := io.in
  }

  io.out := reg
}
