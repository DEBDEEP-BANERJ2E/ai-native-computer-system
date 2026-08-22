package objective01.gates

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class GatesSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Fredkin"

  it should "swap b and c only when control is high" in {
    test(new Fredkin) { dut =>
      for (input <- 0 until 8) {
        val control = (input & 4) != 0
        val b = (input & 2) != 0
        val c = (input & 1) != 0
        dut.io.control.poke(control.B)
        dut.io.b.poke(b.B)
        dut.io.c.poke(c.B)
        dut.io.p.expect(control.B)
        dut.io.q.expect((if (control) c else b).B)
        dut.io.r.expect((if (control) b else c).B)
      }
    }
  }

  it should "map every input vector to a unique output vector" in {
    val outputs = (0 until 8).map { input =>
      val control = (input & 4) != 0
      val b = (input & 2) != 0
      val c = (input & 1) != 0
      Seq(control, if (control) c else b, if (control) b else c)
    }
    assert(outputs.distinct.size == 8)
  }

  behavior of "Toffoli"

  it should "flip c only when a and b are high" in {
    test(new Toffoli) { dut =>
      for (input <- 0 until 8) {
        val a = (input & 4) != 0
        val b = (input & 2) != 0
        val c = (input & 1) != 0
        dut.io.a.poke(a.B)
        dut.io.b.poke(b.B)
        dut.io.c.poke(c.B)
        dut.io.p.expect(a.B)
        dut.io.q.expect(b.B)
        dut.io.r.expect((c ^ (a && b)).B)
      }
    }
  }
}