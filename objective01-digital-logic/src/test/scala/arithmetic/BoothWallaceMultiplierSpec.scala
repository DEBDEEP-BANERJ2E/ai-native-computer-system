package objective01.arithmetic

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class BoothWallaceMultiplierSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "BoothWallaceMultiplier"

  it should "match every signed 8-bit multiplication" in {
    test(new BoothWallaceMultiplier(8)) { dut =>
      for {
        a <- -128 to 127
        b <- -128 to 127
      } {
        dut.io.a.poke((a & 0xff).U)
        dut.io.b.poke((b & 0xff).U)
        dut.io.product.expect((a * b & 0xffff).U)
      }
    }
  }
}