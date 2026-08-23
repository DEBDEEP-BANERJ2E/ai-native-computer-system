package objective02

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import objective02.datapath.RegisterFile

class RegisterFileSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "RegisterFile"

  it should "initialize all registers to zero and maintain hardwired x0 = 0" in {
    test(new RegisterFile) { dut =>
      // Check initial state of all 32 registers
      for (r <- 0 until 32) {
        dut.io.rs1Address.poke(r.U)
        dut.io.rs1Data.expect(0.U)
      }

      // Attempt to write non-zero value to x0
      dut.io.writeEnable.poke(true.B)
      dut.io.rdAddress.poke(0.U)
      dut.io.writeData.poke("hDEADBEEF".U)
      dut.clock.step(1)

      // x0 must still be 0
      dut.io.writeEnable.poke(false.B)
      dut.io.rs1Address.poke(0.U)
      dut.io.rs1Data.expect(0.U)
      dut.io.rs2Address.poke(0.U)
      dut.io.rs2Data.expect(0.U)
    }
  }

  it should "correctly write and read all 31 registers (x1 to x31)" in {
    test(new RegisterFile) { dut =>
      // Write unique pattern to each register x1..x31
      dut.io.writeEnable.poke(true.B)
      for (r <- 1 until 32) {
        val testVal = (0x10000000L + r * 0x1111111L) & 0xFFFFFFFFL
        dut.io.rdAddress.poke(r.U)
        dut.io.writeData.poke(testVal.U)
        dut.clock.step(1)
      }

      // Read back and verify via both rs1 and rs2 ports
      dut.io.writeEnable.poke(false.B)
      for (r <- 1 until 32) {
        val expected = (0x10000000L + r * 0x1111111L) & 0xFFFFFFFFL
        dut.io.rs1Address.poke(r.U)
        dut.io.rs2Address.poke((32 - r).U)

        dut.io.rs1Data.expect(expected.U)
        val expected2 = (0x10000000L + (32 - r) * 0x1111111L) & 0xFFFFFFFFL
        dut.io.rs2Data.expect(expected2.U)
      }
    }
  }

  it should "support internal same-cycle write-first forwarding" in {
    test(new RegisterFile) { dut =>
      dut.io.writeEnable.poke(true.B)
      dut.io.rdAddress.poke(7.U)
      dut.io.writeData.poke("hCAFE1234".U)

      // Simultaneously read rs1=7 and rs2=7 on the same clock cycle
      dut.io.rs1Address.poke(7.U)
      dut.io.rs2Address.poke(7.U)

      // Must see the newly written value immediately via internal forwarding
      dut.io.rs1Data.expect("hCAFE1234".U)
      dut.io.rs2Data.expect("hCAFE1234".U)

      dut.clock.step(1)

      // After clock tick, it persists in the register array
      dut.io.writeEnable.poke(false.B)
      dut.io.rs1Address.poke(7.U)
      dut.io.rs1Data.expect("hCAFE1234".U)
    }
  }
}
