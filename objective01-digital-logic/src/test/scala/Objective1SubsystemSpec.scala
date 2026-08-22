import chisel3._
import chiseltest._
import objective01.datapath.ALUOpcode
import objective01.telemetry.TelemetryAddress
import org.scalatest.flatspec.AnyFlatSpec

class Objective1SubsystemSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Objective1Subsystem"

  it should "connect ALU results and telemetry events" in {
    test(new Objective1Subsystem(8)) { dut =>
      dut.io.a.poke(2.U)
      dut.io.b.poke(3.U)
      dut.io.opcode.poke(ALUOpcode.ADD)
      dut.io.operationValid.poke(true.B)
      dut.io.telemetryAddress.poke(TelemetryAddress.ClaSwitching)
      dut.clock.step()

      dut.io.result.expect(5.U)
      dut.io.done.expect(true.B)
      dut.io.busy.expect(false.B)
      dut.io.valid.expect(true.B)
      dut.io.telemetryData.expect(2.U)
    }
  }

  it should "keep invalid operations out of telemetry" in {
    test(new Objective1Subsystem(8)) { dut =>
      dut.io.operationValid.poke(false.B)
      dut.io.a.poke(1.U)
      dut.io.b.poke(1.U)
      dut.io.opcode.poke(ALUOpcode.ADD)
      dut.io.telemetryAddress.poke(TelemetryAddress.ClaSwitching)
      dut.clock.step()
      dut.io.telemetryData.expect(0.U)
    }
  }
}