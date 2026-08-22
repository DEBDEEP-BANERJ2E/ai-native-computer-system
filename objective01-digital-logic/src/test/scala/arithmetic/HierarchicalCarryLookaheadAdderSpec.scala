package objective01.arithmetic

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class HierarchicalCarryLookaheadAdderSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "HierarchicalCarryLookaheadAdder"

  it should "match integer addition for every 8-bit input and carry" in {
    test(new HierarchicalCarryLookaheadAdder(8)) { dut =>
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

  it should "support randomized 32-bit CPU datapath inputs" in {
    test(new HierarchicalCarryLookaheadAdder(32)) { dut =>
      val random = new scala.util.Random(5)
      for (_ <- 0 until 1000) {
        val a = random.nextInt()
        val b = random.nextInt()
        val carryIn = random.nextInt(2)
        val expected = (a.toLong & 0xffffffffL) + (b.toLong & 0xffffffffL) + carryIn
        dut.io.a.poke((a.toLong & 0xffffffffL).U)
        dut.io.b.poke((b.toLong & 0xffffffffL).U)
        dut.io.carryIn.poke(carryIn.B)
        dut.io.sum.expect((expected & 0xffffffffL).U)
        dut.io.carryOut.expect((expected > 0xffffffffL).B)
      }
    }
  }
}