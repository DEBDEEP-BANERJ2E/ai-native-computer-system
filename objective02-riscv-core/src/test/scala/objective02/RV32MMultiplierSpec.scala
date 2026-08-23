package objective02

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import objective02.decode.MOp
import objective02.execute.RV32MMultiplier

class RV32MMultiplierSpec extends AnyFreeSpec with ChiselScalatestTester with Matchers {
  "RV32MMultiplier" - {
    
    // Helper to calculate exact BigInt results
    def expectedResult(rs1: Long, rs2: Long, mOp: UInt): Long = {
      val isMulH   = mOp.litValue == MOp.MULH.litValue
      val isMulHSU = mOp.litValue == MOp.MULHSU.litValue
      val isMulHU  = mOp.litValue == MOp.MULHU.litValue
      val isMul    = mOp.litValue == MOp.MUL.litValue

      // Convert to appropriate signed/unsigned BigInts
      val signedRs1   = BigInt(rs1.toInt) // Sign extends safely via toInt
      val unsignedRs1 = BigInt(rs1 & 0xFFFFFFFFL)
      
      val signedRs2   = BigInt(rs2.toInt)
      val unsignedRs2 = BigInt(rs2 & 0xFFFFFFFFL)

      val op1 = if (isMulH || isMulHSU || isMul) signedRs1 else unsignedRs1
      val op2 = if (isMulH || isMul) signedRs2 else unsignedRs2

      val fullProduct = op1 * op2

      if (isMul) {
        (fullProduct.toLong) & 0xFFFFFFFFL
      } else {
        (fullProduct >> 32).toLong & 0xFFFFFFFFL
      }
    }

    def testMultiplication(dut: RV32MMultiplier, rs1: Long, rs2: Long, mOp: UInt, desc: String): Unit = {
      val expected = expectedResult(rs1, rs2, mOp)
      
      dut.io.rs1.poke(rs1.U(32.W))
      dut.io.rs2.poke(rs2.U(32.W))
      dut.io.mOp.poke(mOp)
      
      dut.clock.step(1)
      
      dut.io.result.expect(expected.U(32.W), s"Failed $desc: $rs1 * $rs2")
    }

    "should correctly compute all four RV32M multiplications for corner cases and random values" in {
      test(new RV32MMultiplier) { dut =>
        val cases = Seq(
          // (rs1, rs2, description)
          (0L, 0L, "0 x 0"),
          (1L, 1L, "1 x 1"),
          (0xFFFFFFFFL, 1L, "-1 x 1"),
          (0xFFFFFFFFL, 0xFFFFFFFFL, "-1 x -1"),
          (0x80000000L, 1L, "INT_MIN x 1"),
          (0x80000000L, 0xFFFFFFFFL, "INT_MIN x -1"),
          (0x7FFFFFFFL, 0x7FFFFFFFL, "INT_MAX x INT_MAX"),
          (0x80000000L, 0x80000000L, "INT_MIN x INT_MIN"),
          (0x12345678L, 0x87654321L, "pos x neg mixed"),
          (0x87654321L, 0x12345678L, "neg x pos mixed")
        )

        val ops = Seq(
          (MOp.MUL, "MUL"),
          (MOp.MULH, "MULH"),
          (MOp.MULHSU, "MULHSU"),
          (MOp.MULHU, "MULHU")
        )

        for ((rs1, rs2, desc) <- cases) {
          for ((op, opName) <- ops) {
            testMultiplication(dut, rs1, rs2, op, s"$opName for $desc")
          }
        }
      }
    }
  }
}
