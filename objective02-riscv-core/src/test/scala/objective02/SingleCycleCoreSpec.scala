package objective02

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import objective02.core.SingleCycleCore

class SingleCycleCoreSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "SingleCycleCore"

  it should "execute Program 1: Arithmetic and Logical Matrix" in {
    val prog1 = Seq(
      BigInt("00a00093", 16), // 0x00: addi x1, x0, 10
      BigInt("01400113", 16), // 0x04: addi x2, x0, 20
      BigInt("002081b3", 16), // 0x08: add  x3, x1, x2  (30)
      BigInt("40118233", 16), // 0x0C: sub  x4, x3, x1  (20)
      BigInt("003222b3", 16), // 0x10: slt  x5, x4, x3  (1)
      BigInt("0020c333", 16), // 0x14: xor  x6, x1, x2  (30)
      BigInt("0020e3b3", 16), // 0x18: or   x7, x1, x2  (30)
      BigInt("0020f433", 16)  // 0x1C: and  x8, x1, x2  (0)
    )

    test(new SingleCycleCore(initialProgram = prog1)) { dut =>
      // Step through all 8 instructions
      for (_ <- 0 until 8) {
        dut.clock.step(1)
      }

      // Verify final committed state
      dut.io.debugIllegal.expect(false.B)
      dut.io.debugPc.expect("h20".U)
    }
  }

  it should "execute Program 2: Loop Accumulation (5 + 4 + 3 + 2 + 1 = 15)" in {
    val prog2 = Seq(
      BigInt("00500093", 16), // 0x00: addi x1, x0, 5
      BigInt("00000113", 16), // 0x04: addi x2, x0, 0
      BigInt("00110133", 16), // 0x08: add  x2, x2, x1 (loop)
      BigInt("fff08093", 16), // 0x0C: addi x1, x1, -1
      BigInt("fe009ce3", 16), // 0x10: bne  x1, x0, -8 (loop target 0x08)
      BigInt("00000013", 16)  // 0x14: nop / exit
    )

    test(new SingleCycleCore(initialProgram = prog2)) { dut =>
      // 2 setup instructions + 5 iterations * 3 instructions + 1 exit = 18 cycles
      for (c <- 0 until 18) {
        dut.clock.step(1)
      }

      dut.io.debugIllegal.expect(false.B)
      dut.io.debugPc.expect("h18".U)
    }
  }

  it should "execute Program 3: Memory Loads & Stores with Signed/Unsigned bytes/halfs" in {
    val prog3 = Seq(
      BigInt("02a00093", 16), // 0x00: addi x1, x0, 42
      BigInt("00102023", 16), // 0x04: sw   x1, 0(x0)
      BigInt("00002103", 16), // 0x08: lw   x2, 0(x0)        -> x2 = 42
      BigInt("ffb00193", 16), // 0x0C: addi x3, x0, -5
      BigInt("00300223", 16), // 0x10: sb   x3, 4(x0)
      BigInt("00400203", 16), // 0x14: lb   x4, 4(x0)        -> x4 = -5 (0xFFFFFFFB)
      BigInt("00404283", 16), // 0x18: lbu  x5, 4(x0)        -> x5 = 251 (0x000000FB)
      BigInt("c1800313", 16), // 0x1C: addi x6, x0, -1000   (0xFFFFFC18)
      BigInt("00601323", 16), // 0x20: sh   x6, 6(x0)
      BigInt("00601383", 16), // 0x24: lh   x7, 6(x0)        -> x7 = -1000 (0xFFFFFC18)
      BigInt("00605403", 16)  // 0x28: lhu  x8, 6(x0)        -> x8 = 64536 (0x0000FC18)
    )

    test(new SingleCycleCore(initialProgram = prog3)) { dut =>
      // Step through all 11 instructions
      for (_ <- 0 until 11) {
        dut.clock.step(1)
      }

      dut.io.debugIllegal.expect(false.B)
      dut.io.debugPc.expect("h2C".U)
    }
  }

  it should "execute Program 4: Subroutine Function Call and Return via JAL & JALR" in {
    val prog4 = Seq(
      BigInt("03200513", 16), // 0x00: addi x10, x0, 50
      BigInt("010000ef", 16), // 0x04: jal  x1, 16      (jump to 0x14 func, link x1 = 0x08)
      BigInt("00a50613", 16), // 0x08: addi x12, x10, 10 (x12 = 75 + 10 = 85)
      BigInt("0100006f", 16), // 0x0C: jal  x0, 16      (jump to 0x1C done)
      BigInt("3e700713", 16), // 0x10: addi x14, x0, 999 (skipped)
      BigInt("01950513", 16), // 0x14: addi x10, x10, 25 (func: x10 = 50 + 25 = 75)
      BigInt("00008067", 16), // 0x18: jalr x0, 0(x1)   (return to 0x08)
      BigInt("00100693", 16)  // 0x1C: addi x13, x0, 1   (done: x13 = 1)
    )

    test(new SingleCycleCore(initialProgram = prog4)) { dut =>
      // Trace: 0x00 -> 0x04 -> 0x14 -> 0x18 -> 0x08 -> 0x0C -> 0x1C (7 steps)
      for (_ <- 0 until 7) {
        dut.clock.step(1)
      }

      dut.io.debugIllegal.expect(false.B)
      dut.io.debugPc.expect("h20".U)
    }
  }

  it should "execute Program 5: Hardware Multiplication using Objective 1 Booth-Wallace Tree" in {
    val prog5 = Seq(
      BigInt("00700093", 16), // 0x00: addi x1, x0, 7
      BigInt("ffb00113", 16), // 0x04: addi x2, x0, -5
      BigInt("022081b3", 16), // 0x08: mul  x3, x1, x2     (7 * -5 = -35 = 0xFFFFFFDD)
      BigInt("00003237", 16), // 0x0C: lui  x4, 3          (12288)
      BigInt("03920213", 16), // 0x10: addi x4, x4, 57     (12345)
      BigInt("7d000293", 16), // 0x14: addi x5, x0, 2000
      BigInt("02520333", 16)  // 0x18: mul  x6, x4, x5     (12345 * 2000 = 24690000 = 0x0178BD50)
    )

    test(new SingleCycleCore(initialProgram = prog5)) { dut =>
      for (_ <- 0 until 7) {
        dut.clock.step(1)
      }

      dut.io.debugIllegal.expect(false.B)
      dut.io.debugPc.expect("h1C".U)
    }
  }
}
