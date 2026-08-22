package objective01.arithmetic

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class BoothMultiplierSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "BoothMultiplier"

  it should "match every signed 8-bit multiplication" in {
    test(new BoothMultiplier(8)) { dut =>
      for {
        a <- -128 to 127
        b <- -128 to 127
      } {
        val encodedA = a & 0xff
        val encodedB = b & 0xff
        val expected = a * b
        dut.io.a.poke(encodedA.U)
        dut.io.b.poke(encodedB.U)
        dut.io.product.expect((expected & 0xffff).U)
      }
    }
  }
}