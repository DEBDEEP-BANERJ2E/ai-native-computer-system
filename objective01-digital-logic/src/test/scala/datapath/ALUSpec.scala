package objective01.datapath

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class ALUSpec extends AnyFlatSpec with ChiselScalatestTester {
  private val operations = Seq(
    ALUOpcode.ADD, ALUOpcode.SUB, ALUOpcode.AND, ALUOpcode.OR, ALUOpcode.XOR,
    ALUOpcode.SLL, ALUOpcode.SRL, ALUOpcode.SRA, ALUOpcode.SLT, ALUOpcode.SLTU,
    ALUOpcode.MUL
  )

  behavior of "ALU"

  it should "implement the RISC-V-oriented operation set" in {
    test(new ALU(32)) { dut =>
      val random = new scala.util.Random(3)
      for (_ <- 0 until 1000) {
        val a = random.nextInt()
        val b = random.nextInt()
        val shift = b & 31
        val expected = operations.map { opcode =>
          val result = opcode.litValue.toInt match {
            case 0 => a.toLong + b.toLong
            case 1 => a.toLong - b.toLong
            case 2 => a & b
            case 3 => a | b
            case 4 => a ^ b
            case 5 => a << shift
            case 6 => (a & 0xffffffffL) >>> shift
            case 7 => a >> shift
            case 8 => if (a < b) 1 else 0
            case 9 => if ((a.toLong & 0xffffffffL) < (b.toLong & 0xffffffffL)) 1 else 0
            case 10 => a.toLong * b.toLong
          }
          (opcode, result & 0xffffffffL)
        }
        dut.io.a.poke((a.toLong & 0xffffffffL).U)
        dut.io.b.poke((b.toLong & 0xffffffffL).U)
        expected.foreach { case (opcode, result) =>
          dut.io.opcode.poke(opcode)
          dut.io.result.expect(result.U)
          dut.io.zero.expect((result == 0).B)
          dut.io.negative.expect((result < 0x80000000L).!=(true).B)
          dut.io.busy.expect(false.B)
          dut.io.done.expect(true.B)
          dut.io.valid.expect(true.B)
        }
      }
    }
  }

  it should "report signed add and subtract overflow" in {
    test(new ALU(32)) { dut =>
      dut.io.a.poke(0x7fffffffL.U)
      dut.io.b.poke(1.U)
      dut.io.opcode.poke(ALUOpcode.ADD)
      dut.io.result.expect(0x80000000L.U)
      dut.io.overflow.expect(true.B)

      dut.io.a.poke(0x80000000L.U)
      dut.io.b.poke(1.U)
      dut.io.opcode.poke(ALUOpcode.SUB)
      dut.io.result.expect(0x7fffffffL.U)
      dut.io.overflow.expect(true.B)
    }
  }
}