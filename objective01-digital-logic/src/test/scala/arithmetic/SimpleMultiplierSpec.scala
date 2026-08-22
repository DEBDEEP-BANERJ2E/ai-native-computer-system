package objective01.arithmetic

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class SimpleMultiplierSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "SimpleMultiplier"

  it should "match every unsigned 8-bit multiplication" in {
    test(new SimpleMultiplier(8)) { dut =>
      for {
        a <- 0 until 256
        b <- 0 until 256
      } {
        dut.io.a.poke(a.U)
        dut.io.b.poke(b.U)
        dut.io.product.expect((a * b).U)
      }
    }
  }

  it should "support a wider datapath" in {
    test(new SimpleMultiplier(16)) { dut =>
      val random = new scala.util.Random(1)
      for (_ <- 0 until 1000) {
        val a = random.nextInt(1 << 16)
        val b = random.nextInt(1 << 16)
        dut.io.a.poke(a.U)
        dut.io.b.poke(b.U)
        dut.io.product.expect((a.toLong * b.toLong).U)
      }
    }
  }
}