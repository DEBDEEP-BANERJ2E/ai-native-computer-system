package objective02.decode

import chisel3._
import chisel3.util._
import objective02.isa.Opcodes._
import objective02.isa.Instructions._

class ImmediateGeneratorIO extends Bundle {
  val instruction = Input(UInt(32.W))
  val immI = Output(UInt(32.W))
  val immS = Output(UInt(32.W))
  val immB = Output(UInt(32.W))
  val immU = Output(UInt(32.W))
  val immJ = Output(UInt(32.W))
  val immCSR = Output(UInt(32.W))
  val immOut = Output(UInt(32.W)) // Automatically multiplexed based on opcode
}

class ImmediateGenerator extends Module {
  val io = IO(new ImmediateGeneratorIO)

  val inst = io.instruction

  // Raw Format Decoders
  io.immI := ImmediateGenerator.decodeI(inst)
  io.immS := ImmediateGenerator.decodeS(inst)
  io.immB := ImmediateGenerator.decodeB(inst)
  io.immU := ImmediateGenerator.decodeU(inst)
  io.immJ := ImmediateGenerator.decodeJ(inst)
  io.immCSR := ImmediateGenerator.decodeCSR(inst)

  // Multiplexed immediate based on opcode
  val op = opcode(inst)
  io.immOut := MuxLookup(op, 0.U(32.W))(Seq(
    OP_I_TYPE   -> io.immI,
    OP_LOAD     -> io.immI,
    OP_JALR     -> io.immI,
    OP_STORE    -> io.immS,
    OP_BRANCH   -> io.immB,
    OP_LUI      -> io.immU,
    OP_AUIPC    -> io.immU,
    OP_JAL      -> io.immJ,
    OP_SYSTEM   -> io.immCSR
  ))
}

object ImmediateGenerator {
  // I-Type Immediate: sign-extended inst[31:20] (12 bits -> 32 bits)
  def decodeI(inst: UInt): UInt = {
    Cat(Fill(20, inst(31)), inst(31, 20))
  }

  // S-Type Immediate: sign-extended {inst[31:25], inst[11:7]} (12 bits -> 32 bits)
  def decodeS(inst: UInt): UInt = {
    Cat(Fill(20, inst(31)), inst(31, 25), inst(11, 7))
  }

  // B-Type Immediate: sign-extended {inst[31], inst[7], inst[30:25], inst[11:8], 1'b0} (13 bits -> 32 bits)
  def decodeB(inst: UInt): UInt = {
    Cat(Fill(19, inst(31)), inst(31), inst(7), inst(30, 25), inst(11, 8), 0.U(1.W))
  }

  // U-Type Immediate: {inst[31:12], 12'b0} (20 upper bits -> 32 bits)
  def decodeU(inst: UInt): UInt = {
    Cat(inst(31, 12), 0.U(12.W))
  }

  // J-Type Immediate: sign-extended {inst[31], inst[19:12], inst[20], inst[30:21], 1'b0} (21 bits -> 32 bits)
  def decodeJ(inst: UInt): UInt = {
    Cat(Fill(11, inst(31)), inst(31), inst(19, 12), inst(20), inst(30, 21), 0.U(1.W))
  }

  // CSR / Zicsr Zero-Extended 5-bit Immediate: {27'b0, inst[19:15]}
  def decodeCSR(inst: UInt): UInt = {
    Cat(0.U(27.W), inst(19, 15))
  }
}
