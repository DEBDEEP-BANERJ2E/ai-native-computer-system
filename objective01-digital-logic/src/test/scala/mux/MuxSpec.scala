package objective01.mux

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class MuxSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Mux2"

  it should "select either input" in {
    test(new Mux2(8)) { dut =>
      dut.io.a.poke(0x12.U)
      dut.io.b.poke(0xA5.U)
      dut.io.select.poke(false.B)
      dut.io.y.expect(0x12.U)
      dut.io.select.poke(true.B)
      dut.io.y.expect(0xA5.U)
    }
  }

  behavior of "Mux4"

  it should "select all four inputs" in {
    test(new Mux4(8)) { dut =>
      val values = Seq(0x10, 0x20, 0x40, 0x80)
      dut.io.in0.poke(values(0).U)
      dut.io.in1.poke(values(1).U)
      dut.io.in2.poke(values(2).U)
      dut.io.in3.poke(values(3).U)
      for (select <- 0 until 4) {
        dut.io.select.poke(select.U)
        dut.io.y.expect(values(select).U)
      }
    }
  }
}