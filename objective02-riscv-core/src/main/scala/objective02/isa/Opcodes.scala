package objective02.isa

import chisel3._

object Opcodes {
  // 7-bit Major Opcode fields (inst[6:0])
  val OP_R_TYPE   = "b0110011".U(7.W) // 0x33: ADD, SUB, SLL, SLT, SLTU, XOR, SRL, SRA, OR, AND, MUL, etc.
  val OP_I_TYPE   = "b0010011".U(7.W) // 0x13: ADDI, SLTI, SLTIU, XORI, ORI, ANDI, SLLI, SRLI, SRAI
  val OP_LOAD     = "b0000011".U(7.W) // 0x03: LB, LH, LW, LBU, LHU
  val OP_STORE    = "b0100011".U(7.W) // 0x23: SB, SH, SW
  val OP_BRANCH   = "b1100011".U(7.W) // 0x63: BEQ, BNE, BLT, BGE, BLTU, BGEU
  val OP_JALR     = "b1100111".U(7.W) // 0x67: JALR
  val OP_JAL      = "b1101111".U(7.W) // 0x6F: JAL
  val OP_LUI      = "b0110111".U(7.W) // 0x37: LUI (Load Upper Immediate)
  val OP_AUIPC    = "b0010111".U(7.W) // 0x17: AUIPC (Add Upper Immediate to PC)
  val OP_SYSTEM   = "b1110011".U(7.W) // 0x73: ECALL, EBREAK, CSRRW, CSRRS, CSRRC
  val OP_SECURITY = "b0001011".U(7.W) // 0x0B: Custom capability/security check instructions

  // 3-bit Funct3 fields for R-Type / I-Type Arithmetic & Logic
  val FUNCT3_ADD_SUB = "b000".U(3.W) // ADD / SUB / ADDI
  val FUNCT3_SLL     = "b001".U(3.W) // Shift Left Logical / SLLI
  val FUNCT3_SLT     = "b010".U(3.W) // Set Less Than (signed) / SLTI
  val FUNCT3_SLTU    = "b011".U(3.W) // Set Less Than Unsigned / SLTIU
  val FUNCT3_XOR     = "b100".U(3.W) // Bitwise XOR / XORI
  val FUNCT3_SRL_SRA = "b101".U(3.W) // Shift Right Logical/Arithmetic / SRLI / SRAI
  val FUNCT3_OR      = "b110".U(3.W) // Bitwise OR / ORI
  val FUNCT3_AND     = "b111".U(3.W) // Bitwise AND / ANDI

  // 3-bit Funct3 fields for Branch Instructions
  val FUNCT3_BEQ  = "b000".U(3.W) // Branch Equal
  val FUNCT3_BNE  = "b001".U(3.W) // Branch Not Equal
  val FUNCT3_BLT  = "b100".U(3.W) // Branch Less Than (signed)
  val FUNCT3_BGE  = "b101".U(3.W) // Branch Greater Equal (signed)
  val FUNCT3_BLTU = "b110".U(3.W) // Branch Less Than Unsigned
  val FUNCT3_BGEU = "b111".U(3.W) // Branch Greater Equal Unsigned

  // 3-bit Funct3 fields for Load / Store Memory Widths
  val FUNCT3_BYTE   = "b000".U(3.W) // LB / SB (8-bit signed / store)
  val FUNCT3_HALF   = "b001".U(3.W) // LH / SH (16-bit signed / store)
  val FUNCT3_WORD   = "b010".U(3.W) // LW / SW (32-bit word)
  val FUNCT3_BYTE_U = "b100".U(3.W) // LBU (8-bit unsigned)
  val FUNCT3_HALF_U = "b101".U(3.W) // LHU (16-bit unsigned)

  // 3-bit Funct3 fields for RV32M Extension (when funct7 == 0x01)
  val FUNCT3_MUL    = "b000".U(3.W) // MUL (lower 32-bits of signed * signed)
  val FUNCT3_MULH   = "b001".U(3.W) // MULH (upper 32-bits of signed * signed)
  val FUNCT3_MULHSU = "b010".U(3.W) // MULHSU (upper 32-bits of signed * unsigned)
  val FUNCT3_MULHU  = "b011".U(3.W) // MULHU (upper 32-bits of unsigned * unsigned)
  val FUNCT3_DIV    = "b100".U(3.W) // DIV (signed division)
  val FUNCT3_DIVU   = "b101".U(3.W) // DIVU (unsigned division)
  val FUNCT3_REM    = "b110".U(3.W) // REM (signed remainder)
  val FUNCT3_REMU   = "b111".U(3.W) // REMU (unsigned remainder)

  // 7-bit Funct7 fields (inst[31:25])
  val FUNCT7_STANDARD = "b0000000".U(7.W) // Standard operation (ADD, SRL, etc.)
  val FUNCT7_ALT      = "b0100000".U(7.W) // Alternate operation (SUB, SRA)
  val FUNCT7_MULDIV   = "b0000001".U(7.W) // RV32M Extension (MUL, DIV, REM)
}
