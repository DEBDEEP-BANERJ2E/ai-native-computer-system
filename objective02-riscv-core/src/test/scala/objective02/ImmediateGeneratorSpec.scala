package objective02

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import objective02.decode.ImmediateGenerator

class ImmediateGeneratorSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "ImmediateGenerator"

  it should "correctly decode positive and negative I-type immediates (ADDI, LW, JALR)" in {
    test(new ImmediateGenerator) { dut =>
      // ADDI x1, x2, 42 -> imm = 42 (0x02A) -> inst[31:20] = 0x02A
      // inst = 0x02A10093
      dut.io.instruction.poke("h02A10093".U)
      dut.io.immI.expect(42.U)
      dut.io.immOut.expect(42.U)

      // ADDI x1, x2, -1 -> imm = -1 (0xFFF) -> inst[31:20] = 0xFFF
      // inst = 0xFFF10093
      dut.io.instruction.poke("hFFF10093".U)
      dut.io.immI.expect("hFFFFFFFF".U)
      dut.io.immOut.expect("hFFFFFFFF".U)

      // ADDI x1, x2, -2048 (min 12-bit signed: 0x800)
      // inst[31:20] = 0x800 -> inst = 0x80010093
      dut.io.instruction.poke("h80010093".U)
      dut.io.immI.expect("hFFFFF800".U)
      dut.io.immOut.expect("hFFFFF800".U)
    }
  }

  it should "correctly decode positive and negative S-type store offsets (SW, SH, SB)" in {
    test(new ImmediateGenerator) { dut =>
      // SW x3, 8(x2): imm = +8 (0x008) -> imm[11:5]=0x00, imm[4:0]=0x08
      // opcode = 0x23 (0100011), funct3 = 0x2 (SW), rs1=2, rs2=3, rd/imm[4:0]=0x08
      // inst = 0x00312423
      dut.io.instruction.poke("h00312423".U)
      dut.io.immS.expect(8.U)
      dut.io.immOut.expect(8.U)

      // SW x3, -4(x2): imm = -4 (0xFFC) -> imm[11:5]=0x7F, imm[4:0]=0x1C
      // inst = 0xFE312E23
      dut.io.instruction.poke("hFE312E23".U)
      dut.io.immS.expect("hFFFFFFFC".U)
      dut.io.immOut.expect("hFFFFFFFC".U)
    }
  }

  it should "correctly decode B-type branch target offsets with LSB=0 (BEQ, BNE, BLT)" in {
    test(new ImmediateGenerator) { dut =>
      // BEQ x1, x2, +16: imm = 16 (0x010) -> imm[12]=0, imm[11]=0, imm[10:5]=0, imm[4:1]=8, imm[0]=0
      // inst[31:25]=0x00, inst[11:7]=0x10, rs2=2, rs1=1, funct3=0, opcode=0x63
      // inst = 0x00208863
      dut.io.instruction.poke("h00208863".U)
      dut.io.immB.expect(16.U)
      dut.io.immOut.expect(16.U)

      // BEQ x1, x2, -4: imm = -4 (0xFFFFFFFC)
      // inst = 0xFE208EE3
      dut.io.instruction.poke("hFE208EE3".U)
      dut.io.immB.expect("hFFFFFFFC".U)
      dut.io.immOut.expect("hFFFFFFFC".U)
    }
  }

  it should "correctly decode U-type upper immediates (LUI, AUIPC)" in {
    test(new ImmediateGenerator) { dut =>
      // LUI x1, 0x12345 -> imm = 0x12345000
      // inst[31:12] = 0x12345, rd=1, opcode=0x37 -> inst = 0x123450B7
      dut.io.instruction.poke("h123450B7".U)
      dut.io.immU.expect("h12345000".U)
      dut.io.immOut.expect("h12345000".U)

      // AUIPC x2, 0x80000 -> imm = 0x80000000
      // inst = 0x80000117
      dut.io.instruction.poke("h80000117".U)
      dut.io.immU.expect("h80000000".U)
      dut.io.immOut.expect("h80000000".U)
    }
  }

  it should "correctly decode J-type jump targets with LSB=0 (JAL)" in {
    test(new ImmediateGenerator) { dut =>
      // JAL x1, +100 (jump +100 bytes -> imm = 100 / 0x64)
      // inst = 0x064000EF
      dut.io.instruction.poke("h064000EF".U)
      dut.io.immJ.expect(100.U)
      dut.io.immOut.expect(100.U)

      // JAL x0, -2 (jump -2 bytes -> imm = 0xFFFFFFFE)
      // inst = 0xFFFFFF6F
      dut.io.instruction.poke("hFFFFFF6F".U)
      dut.io.immJ.expect("hFFFFFFFE".U)
      dut.io.immOut.expect("hFFFFFFFE".U)

      // JAL x1, -4 (jump -4 bytes -> imm = 0xFFFFFFFC)
      // inst = 0xFFDFF0EF
      dut.io.instruction.poke("hFFDFF0EF".U)
      dut.io.immJ.expect("hFFFFFFFC".U)
      dut.io.immOut.expect("hFFFFFFFC".U)
    }
  }
}
