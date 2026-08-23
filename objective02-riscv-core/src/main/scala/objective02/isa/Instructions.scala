package objective02.isa

import chisel3._

object Instructions {
  // Extract major 7-bit opcode [6:0]
  def opcode(inst: UInt): UInt = inst(6, 0)

  // Extract destination register index rd [11:7]
  def rd(inst: UInt): UInt = inst(11, 7)

  // Extract 3-bit secondary opcode funct3 [14:12]
  def funct3(inst: UInt): UInt = inst(14, 12)

  // Extract source register 1 index rs1 [19:15]
  def rs1(inst: UInt): UInt = inst(19, 15)

  // Extract source register 2 index rs2 [24:20]
  def rs2(inst: UInt): UInt = inst(24, 20)

  // Extract 7-bit tertiary opcode funct7 [31:25]
  def funct7(inst: UInt): UInt = inst(31, 25)

  // Extract 5-bit shift amount for I-type shifts (SLLI, SRLI, SRAI) [24:20]
  def shamt(inst: UInt): UInt = inst(24, 20)

  // Extract 12-bit CSR address [31:20]
  def csrAddress(inst: UInt): UInt = inst(31, 20)
}
