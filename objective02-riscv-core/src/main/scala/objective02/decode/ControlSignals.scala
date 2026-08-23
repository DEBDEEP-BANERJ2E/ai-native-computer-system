package objective02.decode

import chisel3._

object ALUSrcA {
  val RS1  = 0.U(2.W) // Use Register File rs1 data
  val PC   = 1.U(2.W) // Use current Program Counter (AUIPC, JAL)
  val ZERO = 2.U(2.W) // Use 0 (LUI)
}

object ALUSrcB {
  val RS2  = 0.U(2.W) // Use Register File rs2 data (R-type, Branch)
  val IMM  = 1.U(2.W) // Use decoded 32-bit immediate (I-type, Load, Store, LUI, AUIPC)
  val FOUR = 2.U(2.W) // Use constant 4 (PC + 4 for JAL / JALR link)
}

object BranchType {
  val NONE = 0.U(3.W) // No branch
  val BEQ  = 1.U(3.W) // Branch on Equal (rs1 == rs2)
  val BNE  = 2.U(3.W) // Branch on Not Equal (rs1 != rs2)
  val BLT  = 3.U(3.W) // Branch on Less Than Signed (rs1 < rs2)
  val BGE  = 4.U(3.W) // Branch on Greater Equal Signed (rs1 >= rs2)
  val BLTU = 5.U(3.W) // Branch on Less Than Unsigned (rs1 < rs2)
  val BGEU = 6.U(3.W) // Branch on Greater Equal Unsigned (rs1 >= rs2)
}

object JumpType {
  val NONE = 0.U(2.W) // No jump
  val JAL  = 1.U(2.W) // Jump and Link (unconditional PC-relative)
  val JALR = 2.U(2.W) // Jump and Link Register (unconditional rs1 + imm)
}

object MemWidth {
  val BYTE   = 0.U(3.W) // 8-bit signed load / 8-bit store (LB / SB)
  val HALF   = 1.U(3.W) // 16-bit signed load / 16-bit store (LH / SH)
  val WORD   = 2.U(3.W) // 32-bit word load / store (LW / SW)
  val BYTE_U = 3.U(3.W) // 8-bit unsigned load (LBU)
  val HALF_U = 4.U(3.W) // 16-bit unsigned load (LHU)
}

object WBSource {
  val ALU       = 0.U(2.W) // Writeback from ALU / Multiplier result
  val MEM       = 1.U(2.W) // Writeback from Data Memory read data
  val PC_PLUS_4 = 2.U(2.W) // Writeback return address PC + 4 (JAL / JALR)
  val IMM       = 3.U(2.W) // Writeback immediate directly (LUI)
}

object ALUOps {
  // Directly maps to Objective 1's 11-opcode ALU (ALU.scala)
  val ADD  = 0.U(4.W) // Hierarchical Carry-Lookahead Addition
  val SUB  = 1.U(4.W) // Two's complement Subtraction
  val AND  = 2.U(4.W) // Bitwise Conjunction
  val OR   = 3.U(4.W) // Bitwise Disjunction
  val XOR  = 4.U(4.W) // Bitwise Exclusive-OR
  val SLL  = 5.U(4.W) // Shift Left Logical
  val SRL  = 6.U(4.W) // Shift Right Logical
  val SRA  = 7.U(4.W) // Shift Right Arithmetic
  val SLT  = 8.U(4.W) // Set Less Than (signed)
  val SLTU = 9.U(4.W) // Set Less Than Unsigned
  val MUL  = 10.U(4.W)// Radix-4 Booth & Wallace 3:2 Multiplication
}

object MOp {
  val NONE   = 0.U(4.W) // Not an M-extension operation
  val MUL    = 1.U(4.W) // Signed 32x32 -> lower 32-bit (Objective 1 Booth-Wallace)
  val MULH   = 2.U(4.W) // Signed 32x32 -> upper 32-bit (pending Phase 5)
  val MULHSU = 3.U(4.W) // Signed x Unsigned -> upper 32-bit (pending Phase 5)
  val MULHU  = 4.U(4.W) // Unsigned x Unsigned -> upper 32-bit (pending Phase 5)
  val DIV    = 5.U(4.W) // Signed division (pending Phase 5)
  val DIVU   = 6.U(4.W) // Unsigned division (pending Phase 5)
  val REM    = 7.U(4.W) // Signed remainder (pending Phase 5)
  val REMU   = 8.U(4.W) // Unsigned remainder (pending Phase 5)
}

class ControlSignalsBundle extends Bundle {
  val aluOp              = UInt(4.W)
  val aluSrcA            = UInt(2.W)
  val aluSrcB            = UInt(2.W)
  val regWrite           = Bool()
  val memRead            = Bool()
  val memWrite           = Bool()
  val memWidth           = UInt(3.W)
  val branchType         = UInt(3.W)
  val jumpType           = UInt(2.W)
  val wbSource           = UInt(2.W)
  val isMul              = Bool()
  val mOp                = UInt(4.W)
  val isSecurityOp       = Bool()
  val illegalInstruction = Bool()
}
