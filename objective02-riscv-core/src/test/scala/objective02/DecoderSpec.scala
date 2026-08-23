package objective02

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import objective02.decode._

class DecoderSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Decoder"

  it should "correctly decode R-type arithmetic and logic instructions" in {
    test(new Decoder) { dut =>
      // ADD x1, x2, x3 -> funct7=0x00, rs2=3, rs1=2, funct3=0, rd=1, opcode=0x33
      // inst = 0x003100B3
      dut.io.instruction.poke("h003100B3".U)
      dut.io.rd.expect(1.U)
      dut.io.rs1.expect(2.U)
      dut.io.rs2.expect(3.U)
      dut.io.controls.regWrite.expect(true.B)
      dut.io.controls.aluOp.expect(ALUOps.ADD)
      dut.io.controls.aluSrcA.expect(ALUSrcA.RS1)
      dut.io.controls.aluSrcB.expect(ALUSrcB.RS2)
      dut.io.controls.wbSource.expect(WBSource.ALU)
      dut.io.controls.illegalInstruction.expect(false.B)

      // SUB x4, x5, x6 -> funct7=0x20, rs2=6, rs1=5, funct3=0, rd=4, opcode=0x33
      // inst = 0x40628233
      dut.io.instruction.poke("h40628233".U)
      dut.io.rd.expect(4.U)
      dut.io.rs1.expect(5.U)
      dut.io.rs2.expect(6.U)
      dut.io.controls.regWrite.expect(true.B)
      dut.io.controls.aluOp.expect(ALUOps.SUB)
      dut.io.controls.illegalInstruction.expect(false.B)

      // SRA x7, x8, x9 -> funct7=0x20, funct3=5, opcode=0x33
      // inst = 0x409453B3
      dut.io.instruction.poke("h409453B3".U)
      dut.io.controls.aluOp.expect(ALUOps.SRA)

      // MUL x10, x11, x12 -> funct7=0x01, funct3=0, opcode=0x33
      // inst = 0x02C58533
      dut.io.instruction.poke("h02C58533".U)
      dut.io.controls.aluOp.expect(ALUOps.MUL)
      dut.io.controls.isMul.expect(true.B)
      dut.io.controls.illegalInstruction.expect(false.B)
    }
  }

  it should "correctly decode I-type ALU instructions and shifts" in {
    test(new Decoder) { dut =>
      // ADDI x1, x2, 100 -> imm=100, rs1=2, funct3=0, rd=1, opcode=0x13
      // inst = 0x06410093
      dut.io.instruction.poke("h06410093".U)
      dut.io.rd.expect(1.U)
      dut.io.rs1.expect(2.U)
      dut.io.imm.expect(100.U)
      dut.io.controls.regWrite.expect(true.B)
      dut.io.controls.aluOp.expect(ALUOps.ADD)
      dut.io.controls.aluSrcA.expect(ALUSrcA.RS1)
      dut.io.controls.aluSrcB.expect(ALUSrcB.IMM)
      dut.io.controls.wbSource.expect(WBSource.ALU)

      // SLLI x3, x4, 4 -> shamt=4, funct7=0x00, funct3=1, opcode=0x13
      // inst = 0x00421193
      dut.io.instruction.poke("h00421193".U)
      dut.io.controls.aluOp.expect(ALUOps.SLL)
      dut.io.controls.illegalInstruction.expect(false.B)

      // SRAI x5, x6, 8 -> shamt=8, funct7=0x20, funct3=5, opcode=0x13
      // inst = 0x40835293
      dut.io.instruction.poke("h40835293".U)
      dut.io.controls.aluOp.expect(ALUOps.SRA)
      dut.io.controls.illegalInstruction.expect(false.B)
    }
  }

  it should "correctly decode Load instructions (LW, LB, LBU, LH, LHU)" in {
    test(new Decoder) { dut =>
      // LW x1, 4(x2) -> opcode=0x03, funct3=2, rs1=2, rd=1, imm=4
      // inst = 0x00412083
      dut.io.instruction.poke("h00412083".U)
      dut.io.rd.expect(1.U)
      dut.io.rs1.expect(2.U)
      dut.io.imm.expect(4.U)
      dut.io.controls.regWrite.expect(true.B)
      dut.io.controls.memRead.expect(true.B)
      dut.io.controls.memWrite.expect(false.B)
      dut.io.controls.memWidth.expect(MemWidth.WORD)
      dut.io.controls.wbSource.expect(WBSource.MEM)
      dut.io.controls.aluOp.expect(ALUOps.ADD)

      // LBU x5, 0(x6) -> opcode=0x03, funct3=4
      // inst = 0x00034283
      dut.io.instruction.poke("h00034283".U)
      dut.io.controls.memWidth.expect(MemWidth.BYTE_U)
      dut.io.controls.memRead.expect(true.B)
    }
  }

  it should "correctly decode Store instructions (SW, SH, SB)" in {
    test(new Decoder) { dut =>
      // SW x3, 8(x2) -> opcode=0x23, funct3=2, rs1=2, rs2=3, imm=8
      // inst = 0x00312423
      dut.io.instruction.poke("h00312423".U)
      dut.io.rs1.expect(2.U)
      dut.io.rs2.expect(3.U)
      dut.io.imm.expect(8.U)
      dut.io.controls.regWrite.expect(false.B)
      dut.io.controls.memRead.expect(false.B)
      dut.io.controls.memWrite.expect(true.B)
      dut.io.controls.memWidth.expect(MemWidth.WORD)
      dut.io.controls.aluOp.expect(ALUOps.ADD)
    }
  }

  it should "correctly decode Conditional Branches (BEQ, BNE, BLT, BGE, BLTU, BGEU)" in {
    test(new Decoder) { dut =>
      // BEQ x1, x2, 16 -> opcode=0x63, funct3=0
      // inst = 0x00208863
      dut.io.instruction.poke("h00208863".U)
      dut.io.controls.branchType.expect(BranchType.BEQ)
      dut.io.controls.regWrite.expect(false.B)

      // BLT x1, x2, 16 -> opcode=0x63, funct3=4
      // inst = 0x0020C863
      dut.io.instruction.poke("h0020C863".U)
      dut.io.controls.branchType.expect(BranchType.BLT)
      dut.io.controls.aluOp.expect(ALUOps.SLT)
    }
  }

  it should "correctly decode JAL and JALR instructions" in {
    test(new Decoder) { dut =>
      // JAL x1, 100 -> opcode=0x6F, rd=1
      dut.io.instruction.poke("h064000EF".U)
      dut.io.rd.expect(1.U)
      dut.io.controls.jumpType.expect(JumpType.JAL)
      dut.io.controls.regWrite.expect(true.B)
      dut.io.controls.wbSource.expect(WBSource.PC_PLUS_4)

      // JALR x1, 0(x2) -> opcode=0x67, funct3=0, rd=1, rs1=2
      // inst = 0x000100E7
      dut.io.instruction.poke("h000100E7".U)
      dut.io.rd.expect(1.U)
      dut.io.rs1.expect(2.U)
      dut.io.controls.jumpType.expect(JumpType.JALR)
      dut.io.controls.regWrite.expect(true.B)
      dut.io.controls.wbSource.expect(WBSource.PC_PLUS_4)
    }
  }

  it should "correctly decode LUI, AUIPC, and Security extension" in {
    test(new Decoder) { dut =>
      // LUI x1, 0x12345 -> opcode=0x37
      dut.io.instruction.poke("h123450B7".U)
      dut.io.rd.expect(1.U)
      dut.io.controls.regWrite.expect(true.B)
      dut.io.controls.wbSource.expect(WBSource.IMM)

      // AUIPC x2, 0x80000 -> opcode=0x17
      dut.io.instruction.poke("h80000117".U)
      dut.io.rd.expect(2.U)
      dut.io.controls.aluSrcA.expect(ALUSrcA.PC)
      dut.io.controls.aluSrcB.expect(ALUSrcB.IMM)
      dut.io.controls.wbSource.expect(WBSource.ALU)

      // OP_SECURITY (0x0B)
      dut.io.instruction.poke("h0000000B".U)
      dut.io.controls.isSecurityOp.expect(true.B)
    }
  }

  it should "flag invalid opcodes as illegal instructions" in {
    test(new Decoder) { dut =>
      // Invalid opcode 0x00000000
      dut.io.instruction.poke("h00000000".U)
      dut.io.controls.illegalInstruction.expect(true.B)

      // Invalid opcode 0xFFFFFFFF
      dut.io.instruction.poke("hFFFFFFFF".U)
      dut.io.controls.illegalInstruction.expect(true.B)
    }
  }
}
