package objective02

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import objective02.datapath.ProgramCounter

class ProgramCounterSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "ProgramCounter"

  it should "initialize to bootAddress and increment sequentially by 4" in {
    test(new ProgramCounter(bootAddress = 0x00001000L)) { dut =>
      dut.io.stall.poke(false.B)
      dut.io.jumpBranchTaken.poke(false.B)
      dut.io.jumpBranchTarget.poke(0.U)

      // Cycle 0: initial boot address
      dut.io.pc.expect("h00001000".U)
      dut.io.pcPlus4.expect("h00001004".U)

      // Cycle 1: PC = 0x1004
      dut.clock.step(1)
      dut.io.pc.expect("h00001004".U)
      dut.io.pcPlus4.expect("h00001008".U)

      // Cycle 2: PC = 0x1008
      dut.clock.step(1)
      dut.io.pc.expect("h00001008".U)
      dut.io.pcPlus4.expect("h0000100C".U)
    }
  }

  it should "hold PC value when stall is asserted" in {
    test(new ProgramCounter(bootAddress = 0x00000000L)) { dut =>
      dut.io.stall.poke(false.B)
      dut.io.jumpBranchTaken.poke(false.B)
      dut.clock.step(2) // PC = 0x08

      dut.io.pc.expect("h00000008".U)

      // Assert stall for 3 cycles
      dut.io.stall.poke(true.B)
      dut.clock.step(1)
      dut.io.pc.expect("h00000008".U)
      dut.clock.step(1)
      dut.io.pc.expect("h00000008".U)
      dut.clock.step(1)
      dut.io.pc.expect("h00000008".U)

      // Release stall -> continues to 0x0C
      dut.io.stall.poke(false.B)
      dut.clock.step(1)
      dut.io.pc.expect("h0000000C".U)
    }
  }

  it should "load target address when jumpBranchTaken is asserted" in {
    test(new ProgramCounter(bootAddress = 0x00000000L)) { dut =>
      dut.io.stall.poke(false.B)
      dut.io.jumpBranchTaken.poke(false.B)
      dut.clock.step(1) // PC = 0x04

      // Take branch to 0x00000040
      dut.io.jumpBranchTaken.poke(true.B)
      dut.io.jumpBranchTarget.poke("h00000040".U)
      dut.clock.step(1)

      dut.io.pc.expect("h00000040".U)
      dut.io.pcPlus4.expect("h00000044".U)

      // Resume sequential execution from new target
      dut.io.jumpBranchTaken.poke(false.B)
      dut.clock.step(1)
      dut.io.pc.expect("h00000044".U)
    }
  }

  it should "prioritize jump/branch redirect over stall when both are asserted simultaneously" in {
    test(new ProgramCounter(bootAddress = 0x00000100L)) { dut =>
      dut.io.stall.poke(false.B)
      dut.io.jumpBranchTaken.poke(false.B)
      dut.io.pc.expect("h00000100".U)

      // Assert BOTH stall and jumpBranchTaken simultaneously
      dut.io.stall.poke(true.B)
      dut.io.jumpBranchTaken.poke(true.B)
      dut.io.jumpBranchTarget.poke("h00000800".U)
      dut.clock.step(1)

      // Redirect MUST win (PC becomes 0x800, not 0x100)
      dut.io.pc.expect("h00000800".U)
      dut.io.pcPlus4.expect("h00000804".U)
    }
  }
}
