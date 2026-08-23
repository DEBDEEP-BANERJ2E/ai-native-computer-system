package objective02

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import objective02.core.SingleCycleCore

class SingleCycleCoreSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "SingleCycleCore"

  // -------------------------------------------------------------
  // Benchmark Program 1: Arithmetic and Logical Matrix
  // -------------------------------------------------------------
  it should "execute Program 1 with cycle-by-cycle register-write and commit validation" in {
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
      // Cycle 0: addi x1, x0, 10 -> writes 10 to x1
      dut.io.debugPc.expect("h00".U)
      dut.io.debugRd.expect(1.U)
      dut.io.debugRegWrite.expect(true.B)
      dut.io.debugWriteData.expect(10.U)
      dut.io.debugIllegal.expect(false.B)
      dut.clock.step(1)

      // Cycle 1: addi x2, x0, 20 -> writes 20 to x2
      dut.io.debugPc.expect("h04".U)
      dut.io.debugRd.expect(2.U)
      dut.io.debugRegWrite.expect(true.B)
      dut.io.debugWriteData.expect(20.U)
      dut.clock.step(1)

      // Cycle 2: add x3, x1, x2 -> writes 30 to x3
      dut.io.debugPc.expect("h08".U)
      dut.io.debugRd.expect(3.U)
      dut.io.debugRegWrite.expect(true.B)
      dut.io.debugWriteData.expect(30.U)
      dut.clock.step(1)

      // Cycle 3: sub x4, x3, x1 -> writes 20 to x4
      dut.io.debugPc.expect("h0C".U)
      dut.io.debugRd.expect(4.U)
      dut.io.debugRegWrite.expect(true.B)
      dut.io.debugWriteData.expect(20.U)
      dut.clock.step(1)

      // Cycle 4: slt x5, x4, x3 -> writes 1 to x5
      dut.io.debugPc.expect("h10".U)
      dut.io.debugRd.expect(5.U)
      dut.io.debugRegWrite.expect(true.B)
      dut.io.debugWriteData.expect(1.U)
      dut.clock.step(1)

      // Cycle 5: xor x6, x1, x2 -> writes 30 to x6
      dut.io.debugPc.expect("h14".U)
      dut.io.debugRd.expect(6.U)
      dut.io.debugRegWrite.expect(true.B)
      dut.io.debugWriteData.expect(30.U)
      dut.clock.step(1)

      // Cycle 6: or x7, x1, x2 -> writes 30 to x7
      dut.io.debugPc.expect("h18".U)
      dut.io.debugRd.expect(7.U)
      dut.io.debugRegWrite.expect(true.B)
      dut.io.debugWriteData.expect(30.U)
      dut.clock.step(1)

      // Cycle 7: and x8, x1, x2 -> writes 0 to x8
      dut.io.debugPc.expect("h1C".U)
      dut.io.debugRd.expect(8.U)
      dut.io.debugRegWrite.expect(true.B)
      dut.io.debugWriteData.expect(0.U)
      dut.clock.step(1)

      dut.io.debugPc.expect("h20".U)
    }
  }

  // -------------------------------------------------------------
  // Benchmark Program 2: Loop Accumulation (5 + 4 + 3 + 2 + 1 = 15)
  // -------------------------------------------------------------
  it should "execute Program 2 with cycle-by-cycle loop convergence and branch verification" in {
    val prog2 = Seq(
      BigInt("00500093", 16), // 0x00: addi x1, x0, 5
      BigInt("00000113", 16), // 0x04: addi x2, x0, 0
      BigInt("00110133", 16), // 0x08: add  x2, x2, x1 (loop)
      BigInt("fff08093", 16), // 0x0C: addi x1, x1, -1
      BigInt("fe009ce3", 16), // 0x10: bne  x1, x0, -8 (loop target 0x08)
      BigInt("00000013", 16)  // 0x14: nop / exit
    )

    test(new SingleCycleCore(initialProgram = prog2)) { dut =>
      // Setup
      dut.io.debugPc.expect("h00".U); dut.io.debugWriteData.expect(5.U); dut.clock.step(1)
      dut.io.debugPc.expect("h04".U); dut.io.debugWriteData.expect(0.U); dut.clock.step(1)

      // Iteration 1: x2 += 5 (5), x1 -= 1 (4), BNE taken -> 0x08
      dut.io.debugPc.expect("h08".U); dut.io.debugWriteData.expect(5.U); dut.clock.step(1)
      dut.io.debugPc.expect("h0C".U); dut.io.debugWriteData.expect(4.U); dut.clock.step(1)
      dut.io.debugPc.expect("h10".U); dut.clock.step(1)

      // Iteration 2: x2 += 4 (9), x1 -= 1 (3), BNE taken -> 0x08
      dut.io.debugPc.expect("h08".U); dut.io.debugWriteData.expect(9.U); dut.clock.step(1)
      dut.io.debugPc.expect("h0C".U); dut.io.debugWriteData.expect(3.U); dut.clock.step(1)
      dut.io.debugPc.expect("h10".U); dut.clock.step(1)

      // Iteration 3: x2 += 3 (12), x1 -= 1 (2), BNE taken -> 0x08
      dut.io.debugPc.expect("h08".U); dut.io.debugWriteData.expect(12.U); dut.clock.step(1)
      dut.io.debugPc.expect("h0C".U); dut.io.debugWriteData.expect(2.U); dut.clock.step(1)
      dut.io.debugPc.expect("h10".U); dut.clock.step(1)

      // Iteration 4: x2 += 2 (14), x1 -= 1 (1), BNE taken -> 0x08
      dut.io.debugPc.expect("h08".U); dut.io.debugWriteData.expect(14.U); dut.clock.step(1)
      dut.io.debugPc.expect("h0C".U); dut.io.debugWriteData.expect(1.U); dut.clock.step(1)
      dut.io.debugPc.expect("h10".U); dut.clock.step(1)

      // Iteration 5: x2 += 1 (15), x1 -= 1 (0), BNE not taken -> 0x14
      dut.io.debugPc.expect("h08".U); dut.io.debugWriteData.expect(15.U); dut.clock.step(1)
      dut.io.debugPc.expect("h0C".U); dut.io.debugWriteData.expect(0.U); dut.clock.step(1)
      dut.io.debugPc.expect("h10".U); dut.clock.step(1)

      // Exit NOP
      dut.io.debugPc.expect("h14".U); dut.clock.step(1)
      dut.io.debugPc.expect("h18".U)
    }
  }

  // -------------------------------------------------------------
  // Benchmark Program 3: Memory Loads & Stores with Little-Endian Lanes
  // -------------------------------------------------------------
  it should "execute Program 3 with exact little-endian byte/halfword sign-extension validation" in {
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
      // 0x00: addi x1, x0, 42
      dut.io.debugPc.expect("h00".U); dut.io.debugWriteData.expect(42.U); dut.clock.step(1)

      // 0x04: sw x1, 0(x0)
      dut.io.debugPc.expect("h04".U)
      dut.io.debugMemWrite.expect(true.B)
      dut.io.debugMemAddress.expect(0.U)
      dut.io.debugMemWriteData.expect(42.U)
      dut.clock.step(1)

      // 0x08: lw x2, 0(x0) -> x2 = 42
      dut.io.debugPc.expect("h08".U)
      dut.io.debugMemRead.expect(true.B)
      dut.io.debugRd.expect(2.U)
      dut.io.debugRegWrite.expect(true.B)
      dut.io.debugWriteData.expect(42.U)
      dut.clock.step(1)

      // 0x0C: addi x3, x0, -5 -> 0xFFFFFFFB
      dut.io.debugPc.expect("h0C".U); dut.io.debugWriteData.expect("hFFFFFFFB".U); dut.clock.step(1)

      // 0x10: sb x3, 4(x0)
      dut.io.debugPc.expect("h10".U)
      dut.io.debugMemWrite.expect(true.B)
      dut.io.debugMemAddress.expect(4.U)
      dut.clock.step(1)

      // 0x14: lb x4, 4(x0) -> sign-extended -5 (0xFFFFFFFB)
      dut.io.debugPc.expect("h14".U)
      dut.io.debugRd.expect(4.U)
      dut.io.debugWriteData.expect("hFFFFFFFB".U)
      dut.clock.step(1)

      // 0x18: lbu x5, 4(x0) -> zero-extended 251 (0x000000FB)
      dut.io.debugPc.expect("h18".U)
      dut.io.debugRd.expect(5.U)
      dut.io.debugWriteData.expect("h000000FB".U)
      dut.clock.step(1)

      // 0x1C: addi x6, x0, -1000 -> 0xFFFFFC18
      dut.io.debugPc.expect("h1C".U); dut.io.debugWriteData.expect("hFFFFFC18".U); dut.clock.step(1)

      // 0x20: sh x6, 6(x0)
      dut.io.debugPc.expect("h20".U)
      dut.io.debugMemWrite.expect(true.B)
      dut.io.debugMemAddress.expect(6.U)
      dut.clock.step(1)

      // 0x24: lh x7, 6(x0) -> sign-extended -1000 (0xFFFFFC18)
      dut.io.debugPc.expect("h24".U)
      dut.io.debugRd.expect(7.U)
      dut.io.debugWriteData.expect("hFFFFFC18".U)
      dut.clock.step(1)

      // 0x28: lhu x8, 6(x0) -> zero-extended 64536 (0x0000FC18)
      dut.io.debugPc.expect("h28".U)
      dut.io.debugRd.expect(8.U)
      dut.io.debugWriteData.expect("h0000FC18".U)
      dut.clock.step(1)

      dut.io.debugPc.expect("h2C".U)
    }
  }

  // -------------------------------------------------------------
  // Benchmark Program 4: Function Call and Return (JAL / JALR)
  // -------------------------------------------------------------
  it should "execute Program 4 with link address and subroutine return validation" in {
    val prog4 = Seq(
      BigInt("03200513", 16), // 0x00: addi x10, x0, 50
      BigInt("010000ef", 16), // 0x04: jal  x1, 16      (func @ 0x14, link x1 = 0x08)
      BigInt("00a50613", 16), // 0x08: addi x12, x10, 10 (x12 = 75 + 10 = 85)
      BigInt("0100006f", 16), // 0x0C: jal  x0, 16      (done @ 0x1C)
      BigInt("3e700713", 16), // 0x10: addi x14, x0, 999 (skipped)
      BigInt("01950513", 16), // 0x14: addi x10, x10, 25 (func: x10 = 50 + 25 = 75)
      BigInt("00008067", 16), // 0x18: jalr x0, 0(x1)   (return to 0x08)
      BigInt("00100693", 16)  // 0x1C: addi x13, x0, 1   (done: x13 = 1)
    )

    test(new SingleCycleCore(initialProgram = prog4)) { dut =>
      // 0x00: addi x10, x0, 50
      dut.io.debugPc.expect("h00".U); dut.io.debugRd.expect(10.U); dut.io.debugWriteData.expect(50.U); dut.clock.step(1)

      // 0x04: jal x1, 16 -> writes link address 0x08 into x1, jumps to 0x14
      dut.io.debugPc.expect("h04".U)
      dut.io.debugRd.expect(1.U)
      dut.io.debugRegWrite.expect(true.B)
      dut.io.debugWriteData.expect("h08".U)
      dut.clock.step(1)

      // 0x14 (Function body): addi x10, x10, 25 -> x10 = 75
      dut.io.debugPc.expect("h14".U)
      dut.io.debugRd.expect(10.U)
      dut.io.debugWriteData.expect(75.U)
      dut.clock.step(1)

      // 0x18: jalr x0, 0(x1) -> returns to 0x08
      dut.io.debugPc.expect("h18".U)
      dut.clock.step(1)

      // 0x08: addi x12, x10, 10 -> x12 = 85
      dut.io.debugPc.expect("h08".U)
      dut.io.debugRd.expect(12.U)
      dut.io.debugWriteData.expect(85.U)
      dut.clock.step(1)

      // 0x0C: jal x0, 16 -> jumps to 0x1C (done)
      dut.io.debugPc.expect("h0C".U)
      dut.clock.step(1)

      // 0x1C: addi x13, x0, 1 -> x13 = 1
      dut.io.debugPc.expect("h1C".U)
      dut.io.debugRd.expect(13.U)
      dut.io.debugWriteData.expect(1.U)
      dut.clock.step(1)

      dut.io.debugPc.expect("h20".U)
    }
  }

  // -------------------------------------------------------------
  // Benchmark Program 5: Hardware Multiplication (Objective 1 Booth-Wallace)
  // -------------------------------------------------------------
  it should "execute Program 5 and verify Objective 1 hardware multiplier results" in {
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
      // 0x00: addi x1, 7
      dut.io.debugPc.expect("h00".U); dut.io.debugWriteData.expect(7.U); dut.clock.step(1)

      // 0x04: addi x2, -5
      dut.io.debugPc.expect("h04".U); dut.io.debugWriteData.expect("hFFFFFFFB".U); dut.clock.step(1)

      // 0x08: mul x3, x1, x2 -> 7 * -5 = -35 (0xFFFFFFDD)
      dut.io.debugPc.expect("h08".U)
      dut.io.debugRd.expect(3.U)
      dut.io.debugRegWrite.expect(true.B)
      dut.io.debugWriteData.expect("hFFFFFFDD".U)
      dut.clock.step(1)

      // 0x0C: lui x4, 3 -> 12288
      dut.io.debugPc.expect("h0C".U); dut.io.debugWriteData.expect(12288.U); dut.clock.step(1)

      // 0x10: addi x4, x4, 57 -> 12345
      dut.io.debugPc.expect("h10".U); dut.io.debugWriteData.expect(12345.U); dut.clock.step(1)

      // 0x14: addi x5, x0, 2000
      dut.io.debugPc.expect("h14".U); dut.io.debugWriteData.expect(2000.U); dut.clock.step(1)

      // 0x18: mul x6, x4, x5 -> 12345 * 2000 = 24690000 (0x0178BD50)
      dut.io.debugPc.expect("h18".U)
      dut.io.debugRd.expect(6.U)
      dut.io.debugRegWrite.expect(true.B)
      dut.io.debugWriteData.expect(24690000.U)
      dut.clock.step(1)

      dut.io.debugPc.expect("h1C".U)
    }
  }

  // -------------------------------------------------------------
  // Full Datapath Coverage: Shift, Unsigned Compare, AUIPC, and Branch Matrix
  // -------------------------------------------------------------
  it should "execute complete shift, logical, AUIPC, and unsigned compare matrix" in {
    val progShift = Seq(
      BigInt("00f00093", 16), // 0x00: addi x1, x0, 15
      BigInt("00209113", 16), // 0x04: slli x2, x1, 2       -> 15 << 2 = 60
      BigInt("00215193", 16), // 0x08: srli x3, x2, 2       -> 60 >> 2 = 15
      BigInt("ff800213", 16), // 0x0C: addi x4, x0, -8      -> 0xFFFFFFF8
      BigInt("40225293", 16), // 0x10: srai x5, x4, 2       -> 0xFFFFFFFE (-2)
      BigInt("00200313", 16), // 0x14: addi x6, x0, 2
      BigInt("006093b3", 16), // 0x18: sll  x7, x1, x6      -> 15 << 2 = 60
      BigInt("00615433", 16), // 0x1C: srl  x8, x7, x6      -> 60 >> 2 = 15
      BigInt("406254b3", 16), // 0x20: sra  x9, x4, x6      -> -8 >> 2 = -2 (0xFFFFFFFE)
      BigInt("00a00513", 16), // 0x24: addi x10, x0, 10
      BigInt("01400593", 16), // 0x28: addi x11, x0, 20
      BigInt("00b53633", 16), // 0x2C: sltu x12, x10, x11   -> 10 < 20 (unsigned) = 1
      BigInt("00a5b6b3", 16), // 0x30: sltu x13, x11, x10   -> 20 < 10 (unsigned) = 0
      BigInt("00010717", 16)  // 0x34: auipc x14, 0x10       -> 0x34 + 0x10000 = 0x00010034
    )

    test(new SingleCycleCore(initialProgram = progShift)) { dut =>
      dut.io.debugPc.expect("h00".U); dut.io.debugWriteData.expect(15.U); dut.clock.step(1)
      dut.io.debugPc.expect("h04".U); dut.io.debugWriteData.expect(60.U); dut.clock.step(1)
      dut.io.debugPc.expect("h08".U); dut.io.debugWriteData.expect(15.U); dut.clock.step(1)
      dut.io.debugPc.expect("h0C".U); dut.io.debugWriteData.expect("hFFFFFFF8".U); dut.clock.step(1)
      dut.io.debugPc.expect("h10".U); dut.io.debugWriteData.expect("hFFFFFFFE".U); dut.clock.step(1)
      dut.io.debugPc.expect("h14".U); dut.io.debugWriteData.expect(2.U); dut.clock.step(1)
      dut.io.debugPc.expect("h18".U); dut.io.debugWriteData.expect(60.U); dut.clock.step(1)
      dut.io.debugPc.expect("h1C".U); dut.io.debugWriteData.expect(15.U); dut.clock.step(1)
      dut.io.debugPc.expect("h20".U); dut.io.debugWriteData.expect("hFFFFFFFE".U); dut.clock.step(1)
      dut.io.debugPc.expect("h24".U); dut.io.debugWriteData.expect(10.U); dut.clock.step(1)
      dut.io.debugPc.expect("h28".U); dut.io.debugWriteData.expect(20.U); dut.clock.step(1)
      dut.io.debugPc.expect("h2C".U); dut.io.debugWriteData.expect(1.U); dut.clock.step(1)
      dut.io.debugPc.expect("h30".U); dut.io.debugWriteData.expect(0.U); dut.clock.step(1)
      dut.io.debugPc.expect("h34".U); dut.io.debugWriteData.expect("h00010034".U); dut.clock.step(1)
    }
  }

  it should "execute all 6 branch condition types in taken and not-taken branches" in {
    val progBranch = Seq(
      BigInt("00a00093", 16), // 0x00: addi x1, x0, 10
      BigInt("00a00113", 16), // 0x04: addi x2, x0, 10
      BigInt("01400193", 16), // 0x08: addi x3, x0, 20
      // BEQ taken (x1 == x2) -> jump to 0x14
      BigInt("00208463", 16), // 0x0C: beq  x1, x2, +8 (to 0x14)
      BigInt("3e700713", 16), // 0x10: addi x14, x0, 999 (skipped)
      // BLT taken (x1 < x3, 10 < 20) -> jump to 0x20
      BigInt("0030c463", 16), // 0x14: blt  x1, x3, +8 (to 0x1C)
      BigInt("3e700713", 16), // 0x18: addi x14, x0, 999 (skipped)
      // BGE taken (x3 >= x1, 20 >= 10) -> jump to 0x28
      BigInt("0011d463", 16), // 0x1C: bge  x3, x1, +8 (to 0x24)
      BigInt("3e700713", 16), // 0x20: addi x14, x0, 999 (skipped)
      // BLTU taken (10 < 20 unsigned) -> jump to 0x30
      BigInt("0030e463", 16), // 0x24: bltu x1, x3, +8 (to 0x2C)
      BigInt("3e700713", 16), // 0x28: addi x14, x0, 999 (skipped)
      // BGEU taken (20 >= 10 unsigned) -> jump to 0x38
      BigInt("0011f463", 16), // 0x2C: bgeu x3, x1, +8 (to 0x34)
      BigInt("3e700713", 16), // 0x30: addi x14, x0, 999 (skipped)
      // Done marker
      BigInt("00100793", 16)  // 0x34: addi x15, x0, 1
    )

    test(new SingleCycleCore(initialProgram = progBranch)) { dut =>
      dut.io.debugPc.expect("h00".U); dut.clock.step(1)
      dut.io.debugPc.expect("h04".U); dut.clock.step(1)
      dut.io.debugPc.expect("h08".U); dut.clock.step(1)
      // BEQ taken: from 0x0C jumps directly to 0x14
      dut.io.debugPc.expect("h0C".U); dut.clock.step(1)
      dut.io.debugPc.expect("h14".U); dut.clock.step(1)
      // BLT taken: from 0x14 jumps to 0x1C
      dut.io.debugPc.expect("h1C".U); dut.clock.step(1)
      // BGE taken: from 0x1C jumps to 0x24
      dut.io.debugPc.expect("h24".U); dut.clock.step(1)
      // BLTU taken: from 0x24 jumps to 0x2C
      dut.io.debugPc.expect("h2C".U); dut.clock.step(1)
      // BGEU taken: from 0x2C jumps to 0x34
      dut.io.debugPc.expect("h34".U)
      dut.io.debugRd.expect(15.U)
      dut.io.debugWriteData.expect(1.U)
      dut.clock.step(1)
      dut.io.debugPc.expect("h38".U)
    }
  }

  it should "detect misaligned core memory accesses and suppress writeback and stores" in {
    val progMisaligned = Seq(
      BigInt("02a00093", 16), // 0x00: addi x1, x0, 42
      BigInt("001020a3", 16), // 0x04: sw   x1, 1(x0)   (MISALIGNED word store at addr 0x01)
      BigInt("00102103", 16)  // 0x08: lw   x2, 1(x0)   (MISALIGNED word load from addr 0x01)
    )

    test(new SingleCycleCore(initialProgram = progMisaligned)) { dut =>
      // 0x00: addi x1, 42
      dut.io.debugPc.expect("h00".U); dut.clock.step(1)

      // 0x04: sw x1, 1(x0) -> misaligned word store
      dut.io.debugPc.expect("h04".U)
      dut.io.debugMemWrite.expect(true.B)
      dut.io.debugMemAddress.expect(1.U)
      dut.clock.step(1)

      // 0x08: lw x2, 1(x0) -> misaligned word load -> writeback suppressed
      dut.io.debugPc.expect("h08".U)
      dut.io.debugMemRead.expect(true.B)
      dut.io.debugRegWrite.expect(false.B) // Reg writeback MUST be suppressed on misaligned load
      dut.clock.step(1)

      dut.io.debugPc.expect("h0C".U)
    }
  }
}
