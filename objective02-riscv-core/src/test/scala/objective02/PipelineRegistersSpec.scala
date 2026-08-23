package objective02

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import objective02.pipeline._
import objective02.decode._

class PipelineRegistersSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "PipelineRegisters"

  it should "correctly latch, stall, and flush IF/ID pipeline register" in {
    test(new IF_ID_Register) { dut =>
      // Initial state: valid = false, instruction = NOP
      dut.io.out.valid.expect(false.B)
      dut.io.out.instruction.expect("h00000013".U)

      // Latch new instruction
      dut.io.stall.poke(false.B)
      dut.io.flush.poke(false.B)
      dut.io.in.valid.poke(true.B)
      dut.io.in.pc.poke("h1000".U)
      dut.io.in.pcPlus4.poke("h1004".U)
      dut.io.in.instruction.poke("h00a00093".U)
      dut.clock.step(1)

      dut.io.out.valid.expect(true.B)
      dut.io.out.pc.expect("h1000".U)
      dut.io.out.instruction.expect("h00a00093".U)

      // Stall: input changes but register holds old value
      dut.io.stall.poke(true.B)
      dut.io.in.pc.poke("h2000".U)
      dut.io.in.instruction.poke("h01400113".U)
      dut.clock.step(1)

      dut.io.out.valid.expect(true.B)
      dut.io.out.pc.expect("h1000".U)
      dut.io.out.instruction.expect("h00a00093".U)

      // Flush: turns into invalid bubble
      dut.io.stall.poke(false.B)
      dut.io.flush.poke(true.B)
      dut.clock.step(1)

      dut.io.out.valid.expect(false.B)
      dut.io.out.instruction.expect("h00000013".U)
    }
  }

  it should "correctly latch, stall, and flush ID/EX pipeline register" in {
    test(new ID_EX_Register) { dut =>
      dut.io.out.valid.expect(false.B)

      dut.io.stall.poke(false.B)
      dut.io.flush.poke(false.B)
      dut.io.in.valid.poke(true.B)
      dut.io.in.pc.poke("h00000008".U)
      dut.io.in.pcPlus4.poke("h0000000C".U)
      dut.io.in.instruction.poke("h002081b3".U)
      dut.io.in.rs1.poke(1.U)
      dut.io.in.rs2.poke(2.U)
      dut.io.in.rd.poke(3.U)
      dut.io.in.rs1Data.poke(10.U)
      dut.io.in.rs2Data.poke(20.U)
      dut.io.in.imm.poke(0.U)
      dut.io.in.controls.regWrite.poke(true.B)
      dut.io.in.controls.aluSrcA.poke(ALUSrcA.RS1)
      dut.io.in.controls.aluSrcB.poke(ALUSrcB.RS2)
      dut.io.in.controls.aluOp.poke(ALUOps.ADD)
      dut.io.in.controls.isMul.poke(false.B)
      dut.io.in.controls.mOp.poke(MOp.NONE)
      dut.io.in.controls.isSecurityOp.poke(false.B)
      dut.io.in.controls.memRead.poke(false.B)
      dut.io.in.controls.memWrite.poke(false.B)
      dut.io.in.controls.memWidth.poke(MemWidth.WORD)
      dut.io.in.controls.branchType.poke(BranchType.NONE)
      dut.io.in.controls.jumpType.poke(JumpType.NONE)
      dut.io.in.controls.wbSource.poke(WBSource.ALU)
      dut.io.in.controls.illegalInstruction.poke(false.B)
      dut.clock.step(1)

      dut.io.out.valid.expect(true.B)
      dut.io.out.rd.expect(3.U)
      dut.io.out.rs1Data.expect(10.U)
      dut.io.out.rs2Data.expect(20.U)
      dut.io.out.controls.regWrite.expect(true.B)

      // Flush on branch redirect
      dut.io.flush.poke(true.B)
      dut.clock.step(1)

      dut.io.out.valid.expect(false.B)
      dut.io.out.controls.regWrite.expect(false.B)
    }
  }

  it should "correctly latch and flush EX/MEM and MEM/WB pipeline registers" in {
    test(new EX_MEM_Register) { dut =>
      dut.io.stall.poke(false.B)
      dut.io.flush.poke(false.B)
      dut.io.in.valid.poke(true.B)
      dut.io.in.pc.poke("h0000000C".U)
      dut.io.in.pcPlus4.poke("h00000010".U)
      dut.io.in.instruction.poke("h002081b3".U)
      dut.io.in.rd.poke(4.U)
      dut.io.in.aluResult.poke(30.U)
      dut.io.in.rs2Data.poke(20.U)
      dut.io.in.imm.poke(0.U)
      dut.io.in.regWrite.poke(true.B)
      dut.io.in.memRead.poke(false.B)
      dut.io.in.memWrite.poke(false.B)
      dut.io.in.memWidth.poke(MemWidth.WORD)
      dut.io.in.wbSource.poke(WBSource.ALU)
      dut.io.in.illegalInstruction.poke(false.B)
      dut.clock.step(1)

      dut.io.out.valid.expect(true.B)
      dut.io.out.rd.expect(4.U)
      dut.io.out.aluResult.expect(30.U)
      dut.io.out.regWrite.expect(true.B)
    }

    test(new MEM_WB_Register) { dut =>
      dut.io.stall.poke(false.B)
      dut.io.flush.poke(false.B)
      dut.io.in.valid.poke(true.B)
      dut.io.in.pc.poke("h00000010".U)
      dut.io.in.pcPlus4.poke("h00000014".U)
      dut.io.in.instruction.poke("h003222b3".U)
      dut.io.in.rd.poke(5.U)
      dut.io.in.aluResult.poke(1.U)
      dut.io.in.memReadData.poke(0.U)
      dut.io.in.imm.poke(0.U)
      dut.io.in.memRead.poke(false.B)
      dut.io.in.memReadReq.poke(false.B)
      dut.io.in.memWrite.poke(false.B)
      dut.io.in.memWriteReq.poke(false.B)
      dut.io.in.memAddress.poke(0.U)
      dut.io.in.memWriteData.poke(0.U)
      dut.io.in.regWrite.poke(true.B)
      dut.io.in.wbSource.poke(WBSource.ALU)
      dut.io.in.illegalInstruction.poke(false.B)
      dut.clock.step(1)

      dut.io.out.valid.expect(true.B)
      dut.io.out.rd.expect(5.U)
      dut.io.out.aluResult.expect(1.U)
      dut.io.out.regWrite.expect(true.B)
    }
  }
}
