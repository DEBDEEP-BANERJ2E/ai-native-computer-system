package objective02

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import objective02.pipeline.HazardUnit

class HazardUnitSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "HazardUnit"

  it should "detect Load-Use hazard when consumer in ID reads rs1 loaded in EX" in {
    test(new HazardUnit) { dut =>
      // Consumer in ID uses rs1=5
      dut.io.idValid.poke(true.B)
      dut.io.idRs1.poke(5.U)
      dut.io.idRs2.poke(6.U)
      dut.io.idUsesRs1.poke(true.B)
      dut.io.idUsesRs2.poke(false.B)

      // Producer in EX is a Load writing to rd=5
      dut.io.idExValid.poke(true.B)
      dut.io.idExMemRead.poke(true.B)
      dut.io.idExRd.poke(5.U)

      dut.io.branchTaken.poke(false.B)

      dut.io.loadUseHazard.expect(true.B)
      dut.io.stallIF.expect(true.B)
      dut.io.stallID.expect(true.B)
      dut.io.flushIFID.expect(false.B)
      dut.io.flushIDEX.expect(true.B) // Insert bubble into EX
    }
  }

  it should "detect Load-Use hazard when consumer in ID reads rs2 loaded in EX (including store instructions)" in {
    test(new HazardUnit) { dut =>
      // Consumer in ID (e.g. Store or R-type) uses rs2=8
      dut.io.idValid.poke(true.B)
      dut.io.idRs1.poke(1.U)
      dut.io.idRs2.poke(8.U)
      dut.io.idUsesRs1.poke(true.B)
      dut.io.idUsesRs2.poke(true.B)

      // Producer in EX is a Load writing to rd=8
      dut.io.idExValid.poke(true.B)
      dut.io.idExMemRead.poke(true.B)
      dut.io.idExRd.poke(8.U)

      dut.io.branchTaken.poke(false.B)

      dut.io.loadUseHazard.expect(true.B)
      dut.io.stallIF.expect(true.B)
      dut.io.stallID.expect(true.B)
      dut.io.flushIDEX.expect(true.B)
    }
  }

  it should "NOT stall when consumer in ID does not actually use the matching rs2 bitfield (e.g. ADDI, LUI, JAL)" in {
    test(new HazardUnit) { dut =>
      // ADDI instruction: bits 24..20 happen to match 5, but idUsesRs2 is FALSE!
      dut.io.idValid.poke(true.B)
      dut.io.idRs1.poke(1.U)
      dut.io.idRs2.poke(5.U) // False match in raw bitfield
      dut.io.idUsesRs1.poke(true.B)
      dut.io.idUsesRs2.poke(false.B) // NOT used!

      // Producer in EX is a Load writing to rd=5
      dut.io.idExValid.poke(true.B)
      dut.io.idExMemRead.poke(true.B)
      dut.io.idExRd.poke(5.U)

      dut.io.branchTaken.poke(false.B)

      dut.io.loadUseHazard.expect(false.B)
      dut.io.stallIF.expect(false.B)
      dut.io.stallID.expect(false.B)
      dut.io.flushIDEX.expect(false.B)
    }
  }

  it should "prioritize taken branch redirect/flush over simultaneous load-use stall" in {
    test(new HazardUnit) { dut =>
      // Apparent load-use hazard in ID
      dut.io.idValid.poke(true.B)
      dut.io.idRs1.poke(5.U)
      dut.io.idRs2.poke(0.U)
      dut.io.idUsesRs1.poke(true.B)
      dut.io.idUsesRs2.poke(false.B)

      dut.io.idExValid.poke(true.B)
      dut.io.idExMemRead.poke(true.B)
      dut.io.idExRd.poke(5.U)

      // Simultaneous taken branch in EX!
      dut.io.branchTaken.poke(true.B)

      // Branch redirect must override stall
      dut.io.loadUseHazard.expect(true.B)
      dut.io.stallIF.expect(false.B) // No stall!
      dut.io.stallID.expect(false.B) // No stall!
      dut.io.flushIFID.expect(true.B) // Flush wrong-path IF/ID
      dut.io.flushIDEX.expect(true.B) // Flush wrong-path ID/EX
    }
  }
}
