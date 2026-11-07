package objective03

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import objective03.decode._

class SystemDecoderSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "SystemDecoder"

  it should "decode CSRRW (funct3=001)" in {
    test(new SystemDecoder) { dut =>
      dut.io.rs1Data.poke(0xAA.U); dut.io.rs2Data.poke(0.U)
      // csrrw x5, mscratch(0x340), x6
      val inst = ((0x340 << 20) | (6 << 15) | (1 << 12) | (5 << 7) | 0x73).U
      dut.io.instruction.poke(inst)
      dut.clock.step(1)
      assert(dut.io.controls.sysOp.peek().litValue == 1)
      assert(dut.io.controls.csrAddr.peek().litValue == 0x340)
      assert(dut.io.controls.base.illegalInstruction.peek().asUInt.litValue == 0)
      assert(dut.io.controls.csrWrite.peek().asUInt.litValue == 1)
    }
  }

  it should "decode CSRRS read-only intent when rs1=x0" in {
    test(new SystemDecoder) { dut =>
      dut.io.rs1Data.poke(0.U); dut.io.rs2Data.poke(0.U)
      val inst = ((0x340 << 20) | (0 << 15) | (2 << 12) | (5 << 7) | 0x73).U
      dut.io.instruction.poke(inst)
      dut.clock.step(1)
      assert(dut.io.controls.sysOp.peek().litValue == 2)
      assert(dut.io.controls.csrWrite.peek().asUInt.litValue == 0)
    }
  }

  it should "decode CSRRS write intent when rs1!=x0" in {
    test(new SystemDecoder) { dut =>
      dut.io.rs1Data.poke(1.U); dut.io.rs2Data.poke(0.U)
      val inst = ((0x340 << 20) | (3 << 15) | (2 << 12) | (5 << 7) | 0x73).U
      dut.io.instruction.poke(inst)
      dut.clock.step(1)
      assert(dut.io.controls.csrWrite.peek().asUInt.litValue == 1)
    }
  }

  it should "decode CSRRWI always-write" in {
    test(new SystemDecoder) { dut =>
      dut.io.rs1Data.poke(0.U); dut.io.rs2Data.poke(0.U)
      val inst = ((0x341 << 20) | (7 << 15) | (5 << 12) | (5 << 7) | 0x73).U
      dut.io.instruction.poke(inst)
      dut.clock.step(1)
      assert(dut.io.controls.sysOp.peek().litValue == 4)
      assert(dut.io.controls.csrWrite.peek().asUInt.litValue == 1)
    }
  }

  it should "decode CSRRSI write only when zimm!=0" in {
    test(new SystemDecoder) { dut =>
      dut.io.rs1Data.poke(0.U); dut.io.rs2Data.poke(0.U)
      val i0 = ((0x344 << 20) | (0 << 15) | (6 << 12) | (0 << 7) | 0x73).U
      dut.io.instruction.poke(i0)
      dut.clock.step(1)
      assert(dut.io.controls.csrWrite.peek().asUInt.litValue == 0)
      val i3 = ((0x344 << 20) | (3 << 15) | (6 << 12) | (0 << 7) | 0x73).U
      dut.io.instruction.poke(i3)
      dut.clock.step(1)
      assert(dut.io.controls.csrWrite.peek().asUInt.litValue == 1)
    }
  }

  it should "decode ECALL / EBREAK / MRET / SRET" in {
    test(new SystemDecoder) { dut =>
      dut.io.rs1Data.poke(0.U); dut.io.rs2Data.poke(0.U)
      dut.io.instruction.poke("h00000073".U); dut.clock.step(1)
      assert(dut.io.controls.isEcall.peek().asUInt.litValue == 1)
      dut.io.instruction.poke("h00100073".U); dut.clock.step(1)
      assert(dut.io.controls.isEbreak.peek().asUInt.litValue == 1)
      dut.io.instruction.poke("h30200073".U); dut.clock.step(1)
      assert(dut.io.controls.isMret.peek().asUInt.litValue == 1)
      dut.io.instruction.poke("h10200073".U); dut.clock.step(1)
      assert(dut.io.controls.isSret.peek().asUInt.litValue == 1)
    }
  }

  it should "decode SFENCE.VMA preserving rs1/rs2 fields" in {
    test(new SystemDecoder) { dut =>
      dut.io.rs1Data.poke(0.U); dut.io.rs2Data.poke(0.U)
      val inst = ((0x09 << 25) | (6 << 20) | (5 << 15) | (0 << 12) | (0 << 7) | 0x73).U
      dut.io.instruction.poke(inst)
      dut.clock.step(1)
      assert(dut.io.controls.isSfenceVma.peek().asUInt.litValue == 1)
      assert(dut.io.controls.sfenceRs1.peek().litValue == 5)
      assert(dut.io.controls.sfenceRs2.peek().litValue == 6)
      assert(dut.io.controls.base.illegalInstruction.peek().asUInt.litValue == 0)
    }
  }

  it should "decode FENCE.I under opcode 0x0F funct3=001" in {
    test(new SystemDecoder) { dut =>
      dut.io.rs1Data.poke(0.U); dut.io.rs2Data.poke(0.U)
      val inst = (1 << 12 | 0x0F).U
      dut.io.instruction.poke(inst)
      dut.clock.step(1)
      assert(dut.io.controls.isFenceI.peek().asUInt.litValue == 1)
      assert(dut.io.controls.isFence.peek().asUInt.litValue == 1)
      assert(dut.io.controls.sysOp.peek().litValue == 0)
      assert(dut.io.controls.base.illegalInstruction.peek().asUInt.litValue == 0)
    }
  }

  it should "mark reserved SYSTEM funct3=100 illegal" in {
    test(new SystemDecoder) { dut =>
      dut.io.rs1Data.poke(0.U); dut.io.rs2Data.poke(0.U)
      val inst = ((0x340 << 20) | (4 << 12) | 0x73).U
      dut.io.instruction.poke(inst)
      dut.clock.step(1)
      assert(dut.io.controls.base.illegalInstruction.peek().asUInt.litValue == 1)
    }
  }

  it should "mark unknown SYSTEM funct12 illegal" in {
    test(new SystemDecoder) { dut =>
      dut.io.rs1Data.poke(0.U); dut.io.rs2Data.poke(0.U)
      val inst = (BigInt(0x999) << 20 | BigInt(0x73)).U
      dut.io.instruction.poke(inst)
      dut.clock.step(1)
      assert(dut.io.controls.base.illegalInstruction.peek().asUInt.litValue == 1)
    }
  }

  it should "pass through normal RV32I decode unchanged" in {
    test(new SystemDecoder) { dut =>
      dut.io.rs1Data.poke(0.U); dut.io.rs2Data.poke(0.U)
      dut.io.instruction.poke("h00128293".U) // addi x5, x6, 1
      dut.clock.step(1)
      assert(dut.io.controls.sysOp.peek().litValue == 0)
      assert(dut.io.controls.base.aluOp.peek().litValue == 0)
      assert(dut.io.controls.base.illegalInstruction.peek().asUInt.litValue == 0)
    }
  }
}
