package objective01.datapath

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class RegisterShifterSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Register"

  it should "hold its value while disabled and update while enabled" in {
    test(new Register(8)) { dut =>
      dut.io.enable.poke(false.B)
      dut.io.d.poke(0xA5.U)
      dut.clock.step()
      dut.io.q.expect(0.U)
      dut.io.enable.poke(true.B)
      dut.clock.step()
      dut.io.q.expect(0xA5.U)
      dut.io.enable.poke(false.B)
      dut.io.d.poke(0x12.U)
      dut.clock.step()
      dut.io.q.expect(0xA5.U)
    }
  }

  behavior of "Shifter"

  it should "implement logical and arithmetic shifts" in {
    test(new Shifter(8)) { dut =>
      dut.io.input.poke(0x81.U)
      dut.io.amount.poke(1.U)
      dut.io.operation.poke(ShiftOperation.LEFT)
      dut.io.output.expect(0x02.U)
      dut.io.operation.poke(ShiftOperation.LOGICAL_RIGHT)
      dut.io.output.expect(0x40.U)
      dut.io.operation.poke(ShiftOperation.ARITHMETIC_RIGHT)
      dut.io.output.expect(0xC0.U)
    }
  }
}