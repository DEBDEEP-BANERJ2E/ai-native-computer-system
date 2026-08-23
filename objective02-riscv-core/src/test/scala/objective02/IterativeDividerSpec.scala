package objective02

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import objective02.execute.IterativeDivider

class IterativeDividerSpec extends AnyFreeSpec with ChiselScalatestTester with Matchers {
  "IterativeDivider" - {

    def testDivision(dut: IterativeDivider, dividend: Long, divisor: Long, isSigned: Boolean, desc: String): Unit = {
      val divUnsigned = dividend & 0xFFFFFFFFL
      val divrUnsigned = divisor & 0xFFFFFFFFL

      val divSigned = dividend.toInt
      val divrSigned = divisor.toInt

      val (expectedQuotient, expectedRemainder) = if (divrUnsigned == 0) {
        (0xFFFFFFFFL, divUnsigned)
      } else if (isSigned && divSigned == 0x80000000 && divrSigned == -1) {
        (0x80000000L, 0L)
      } else if (isSigned) {
        ((divSigned / divrSigned) & 0xFFFFFFFFL, (divSigned % divrSigned) & 0xFFFFFFFFL)
      } else {
        (divUnsigned / divrUnsigned, divUnsigned % divrUnsigned)
      }

      dut.io.dividend.poke((dividend & 0xFFFFFFFFL).U(32.W))
      dut.io.divisor.poke((divisor & 0xFFFFFFFFL).U(32.W))
      dut.io.isSigned.poke(isSigned.B)
      dut.io.start.poke(true.B)
      
      dut.clock.step(1)
      dut.io.start.poke(false.B)
      
      var cycles = 0
      while(!dut.io.done.peek().litToBoolean && cycles < 40) {
        dut.clock.step(1)
        cycles += 1
      }
      
      assert(dut.io.done.peek().litToBoolean, s"Divider did not finish for $desc")
      dut.io.quotient.expect(expectedQuotient.U(32.W), s"Failed Quotient for $desc")
      dut.io.remainder.expect(expectedRemainder.U(32.W), s"Failed Remainder for $desc")
      
      // Step one more to return to Idle
      dut.clock.step(1)
      dut.io.busy.expect(false.B)
    }

    "should correctly compute signed and unsigned division including corner cases" in {
      test(new IterativeDivider).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val cases = Seq(
          // (dividend, divisor, isSigned, description)
          (7L, 3L, true, "7 / 3 signed"),
          (-7L, 3L, true, "-7 / 3 signed"),
          (7L, -3L, true, "7 / -3 signed"),
          (-7L, -3L, true, "-7 / -3 signed"),
          
          (7L, 3L, false, "7 / 3 unsigned"),
          (0xFFFFFFF9L, 3L, false, "-7 / 3 unsigned"), // -7 unsigned is 4294967289
          
          (0x7FFFFFFFL, 1L, true, "INT_MAX / 1 signed"),
          (0x80000000L, 1L, true, "INT_MIN / 1 signed"),
          
          (5L, 0L, true, "divide by zero signed"),
          (5L, 0L, false, "divide by zero unsigned"),
          
          (0x80000000L, 0xFFFFFFFFL, true, "signed overflow"),
          (0x80000000L, 0xFFFFFFFFL, false, "unsigned no overflow")
        )

        // Add deterministic pseudo-random test vectors
        val rand = new scala.util.Random(1337)
        val randCases = (1 to 100).flatMap { i =>
          val r1 = rand.nextLong() & 0xFFFFFFFFL
          val r2 = rand.nextLong() & 0xFFFFFFFFL
          Seq(
            (r1, r2, true, s"random case $i signed"),
            (r1, r2, false, s"random case $i unsigned")
          )
        }

        val allCases = cases ++ randCases

        for ((dividend, divisor, isSigned, desc) <- allCases) {
          testDivision(dut, dividend, divisor, isSigned, desc)
        }
      }
    }
  }
}
