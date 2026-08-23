package objective02

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import objective02.pipeline.ForwardingUnit

class ForwardingUnitSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "ForwardingUnit"

  it should "forward from EX/MEM to EX stage (forwardA = 2, forwardB = 2)" in {
    test(new ForwardingUnit) { dut =>
      // Set EX consumer rs1=1, rs2=2
      dut.io.idExRs1.poke(1.U)
      dut.io.idExRs2.poke(2.U)

      // Set EX/MEM producer rd=1 (writes to x1)
      dut.io.exMemValid.poke(true.B)
      dut.io.exMemRegWrite.poke(true.B)
      dut.io.exMemMemRead.poke(false.B)
      dut.io.exMemRd.poke(1.U)

      // Set MEM/WB inactive
      dut.io.memWbValid.poke(false.B)
      dut.io.memWbRegWrite.poke(false.B)
      dut.io.memWbRd.poke(0.U)

      dut.io.forwardA.expect(2.U) // EX/MEM forward
      dut.io.forwardB.expect(0.U) // No forward
    }
  }

  it should "forward from MEM/WB to EX stage (forwardA = 1, forwardB = 1)" in {
    test(new ForwardingUnit) { dut =>
      dut.io.idExRs1.poke(1.U)
      dut.io.idExRs2.poke(2.U)

      dut.io.exMemValid.poke(false.B)
      dut.io.exMemRegWrite.poke(false.B)
      dut.io.exMemMemRead.poke(false.B)
      dut.io.exMemRd.poke(0.U)

      dut.io.memWbValid.poke(true.B)
      dut.io.memWbRegWrite.poke(true.B)
      dut.io.memWbRd.poke(2.U)

      dut.io.forwardA.expect(0.U)
      dut.io.forwardB.expect(1.U) // MEM/WB forward
    }
  }

  it should "prioritize EX/MEM over MEM/WB when both stages write to the same destination register" in {
    test(new ForwardingUnit) { dut =>
      dut.io.idExRs1.poke(5.U)
      dut.io.idExRs2.poke(0.U)

      // Both EX/MEM and MEM/WB write to x5
      dut.io.exMemValid.poke(true.B)
      dut.io.exMemRegWrite.poke(true.B)
      dut.io.exMemMemRead.poke(false.B)
      dut.io.exMemRd.poke(5.U)

      dut.io.memWbValid.poke(true.B)
      dut.io.memWbRegWrite.poke(true.B)
      dut.io.memWbRd.poke(5.U)

      // EX/MEM must win
      dut.io.forwardA.expect(2.U)
    }
  }

  it should "never forward when the producer writes to x0" in {
    test(new ForwardingUnit) { dut =>
      dut.io.idExRs1.poke(0.U)
      dut.io.idExRs2.poke(0.U)

      dut.io.exMemValid.poke(true.B)
      dut.io.exMemRegWrite.poke(true.B)
      dut.io.exMemMemRead.poke(false.B)
      dut.io.exMemRd.poke(0.U)

      dut.io.memWbValid.poke(true.B)
      dut.io.memWbRegWrite.poke(true.B)
      dut.io.memWbRd.poke(0.U)

      dut.io.forwardA.expect(0.U)
      dut.io.forwardB.expect(0.U)
    }
  }

  it should "inhibit EX/MEM forwarding when the producer is a Load instruction (exMemMemRead = true)" in {
    test(new ForwardingUnit) { dut =>
      dut.io.idExRs1.poke(3.U)
      dut.io.idExRs2.poke(0.U)

      // Load in EX/MEM writes to x3
      dut.io.exMemValid.poke(true.B)
      dut.io.exMemRegWrite.poke(true.B)
      dut.io.exMemMemRead.poke(true.B) // LOAD!
      dut.io.exMemRd.poke(3.U)

      dut.io.memWbValid.poke(false.B)
      dut.io.memWbRegWrite.poke(false.B)
      dut.io.memWbRd.poke(0.U)

      // Must NOT forward from EX/MEM because ALU result is load address, not data
      dut.io.forwardA.expect(0.U)
    }
  }
}
