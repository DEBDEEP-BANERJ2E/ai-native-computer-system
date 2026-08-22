package objective01.arithmetic

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class WallaceTreeSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "CarrySaveCompressor"

  it should "preserve the sum of three rows" in {
    test(new CarrySaveCompressor(9)) { dut =>
      for {
        a <- 0 until 256
        b <- 0 until 256 by 17
        c <- 0 until 256 by 31
      } {
        dut.io.a.poke(a.U)
        dut.io.b.poke(b.U)
        dut.io.c.poke(c.U)
        val actual = dut.io.sum.peek().litValue + dut.io.carry.peek().litValue
        assert(actual == a + b + c)
      }
    }
  }

  behavior of "WallaceTree"

  it should "reduce eight rows to their exact sum" in {
    test(new WallaceTree(16, 8)) { dut =>
      val random = new scala.util.Random(2)
      for (_ <- 0 until 1000) {
        val values = Seq.fill(8)(random.nextInt(1 << 12))
        values.zipWithIndex.foreach { case (value, index) => dut.io.inputs(index).poke(value.U) }
        dut.io.result.expect(values.sum.U)
      }
    }
  }
}