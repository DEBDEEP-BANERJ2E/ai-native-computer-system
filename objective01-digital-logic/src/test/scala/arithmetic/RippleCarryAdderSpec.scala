package objective01.arithmetic

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class RippleCarryAdderSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "RippleCarryAdder"

  it should "match integer addition for every 8-bit input and carry" in {
    test(new RippleCarryAdder(8)) { dut =>
      for {
        a <- 0 until 256
        b <- 0 until 256
        carryIn <- 0 until 2
      } {
        val expected = a + b + carryIn
        dut.io.a.poke(a.U)
        dut.io.b.poke(b.U)
        dut.io.carryIn.poke(carryIn.B)
        dut.io.sum.expect((expected & 0xff).U)
        dut.io.carryOut.expect((expected > 0xff).B)
      }
    }
  }
}