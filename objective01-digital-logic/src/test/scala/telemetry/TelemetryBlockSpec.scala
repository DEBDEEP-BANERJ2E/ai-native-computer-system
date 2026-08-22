package objective01.telemetry

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class TelemetryBlockSpec extends AnyFlatSpec with ChiselScalatestTester {
  private def read(dut: TelemetryBlock, address: UInt): BigInt = {
    dut.io.readAddress.poke(address)
    dut.io.readData.peek().litValue
  }

  behavior of "TelemetryBlock"

  it should "count operations and switching activity at the mapped addresses" in {
    test(new TelemetryBlock(8)) { dut =>
      dut.io.operationValid.poke(true.B)
      dut.io.reversibleOperation.poke(true.B)
      dut.io.claActive.poke(true.B)
      dut.io.multiplierActive.poke(false.B)
      dut.io.result.poke(0.U)
      dut.clock.step()

      dut.io.reversibleOperation.poke(false.B)
      dut.io.result.poke("b10110000".U)
      dut.clock.step()

      dut.io.result.poke("b11100100".U)
      dut.clock.step()

      assert(read(dut, TelemetryAddress.RevEnergyAcc) == 1)
      assert(read(dut, TelemetryAddress.ClaSwitching) == 6)
      assert(read(dut, TelemetryAddress.MulThermal) == 0)
    }
  }

  it should "separate multiplier activity and compute the EDP proxy" in {
    test(new TelemetryBlock(8)) { dut =>
      dut.io.operationValid.poke(true.B)
      dut.io.reversibleOperation.poke(false.B)
      dut.io.claActive.poke(false.B)
      dut.io.multiplierActive.poke(true.B)
      dut.io.result.poke(0.U)
      dut.clock.step()
      dut.io.result.poke(0xff.U)
      dut.clock.step()

      assert(read(dut, TelemetryAddress.MulThermal) == 8)
      assert(read(dut, TelemetryAddress.EdpCurrent) == 8)
    }
  }
}