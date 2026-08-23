package objective02

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import objective02.datapath.BranchJumpUnit
import objective02.decode.{BranchType, JumpType}

class BranchJumpUnitSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "BranchJumpUnit"

  it should "correctly evaluate signed and unsigned branch conditions" in {
    test(new BranchJumpUnit) { dut =>
      dut.io.pc.poke("h00001000".U)
      dut.io.imm.poke("h00000020".U) // +32 bytes
      dut.io.jumpType.poke(JumpType.NONE)

      // Test BEQ
      dut.io.branchType.poke(BranchType.BEQ)
      dut.io.rs1Data.poke(42.U)
      dut.io.rs2Data.poke(42.U)
      dut.io.taken.expect(true.B)
      dut.io.targetAddress.expect("h00001020".U)

      dut.io.rs2Data.poke(43.U)
      dut.io.taken.expect(false.B)

      // Test BNE
      dut.io.branchType.poke(BranchType.BNE)
      dut.io.taken.expect(true.B)

      // Test Signed BLT (-5 < 7 is TRUE)
      dut.io.branchType.poke(BranchType.BLT)
      dut.io.rs1Data.poke("hFFFFFFFB".U) // -5
      dut.io.rs2Data.poke("h00000007".U) // +7
      dut.io.taken.expect(true.B)

      // Test Signed BGE (-5 >= 7 is FALSE)
      dut.io.branchType.poke(BranchType.BGE)
      dut.io.taken.expect(false.B)

      // Test Unsigned BLTU (0xFFFFFFFB < 7 is FALSE because 0xFFFFFFFB > 7 unsigned)
      dut.io.branchType.poke(BranchType.BLTU)
      dut.io.taken.expect(false.B)

      // Test Unsigned BGEU (0xFFFFFFFB >= 7 is TRUE)
      dut.io.branchType.poke(BranchType.BGEU)
      dut.io.taken.expect(true.B)
    }
  }

  it should "correctly compute JAL and JALR target addresses with JALR bit 0 cleared" in {
    test(new BranchJumpUnit) { dut =>
      dut.io.branchType.poke(BranchType.NONE)

      // JAL: target = pc + imm
      dut.io.jumpType.poke(JumpType.JAL)
      dut.io.pc.poke("h00002000".U)
      dut.io.imm.poke("h00000100".U)
      dut.io.taken.expect(true.B)
      dut.io.targetAddress.expect("h00002100".U)

      // JALR: target = (rs1 + imm) & ~1 (LSB cleared!)
      dut.io.jumpType.poke(JumpType.JALR)
      dut.io.rs1Data.poke("h00004001".U) // Odd address
      dut.io.imm.poke("h00000004".U)     // 0x4001 + 4 = 0x4005 -> LSB cleared = 0x4004
      dut.io.taken.expect(true.B)
      dut.io.targetAddress.expect("h00004004".U)
    }
  }
}
