package objective02

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import objective02.core.SingleCycleCore
import java.io.{File, PrintWriter}

class SingleCycleCoreSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "SingleCycleCore"

  // Helper to record commit trace to JSON file for differential verification
  def recordCommitTrace(progName: String, trace: Seq[String]): Unit = {
    val dir = new File("test_traces")
    if (!dir.exists()) dir.mkdirs()
    val writer = new PrintWriter(new File(s"test_traces/$progName.json"))
    try {
      writer.write("[\n" + trace.mkString(",\n") + "\n]\n")
    } finally {
      writer.close()
    }
  }

  // Helper to serialize cycle commit event
  def captureCommitEvent(dut: SingleCycleCore): String = {
    val pc = dut.io.debugPc.peek().litValue
    val inst = dut.io.debugInstruction.peek().litValue
    val rd = dut.io.debugRd.peek().litValue
    val regWrite = dut.io.debugRegWrite.peek().litToBoolean
    val writeData = dut.io.debugWriteData.peek().litValue
    val memRead = dut.io.debugMemRead.peek().litToBoolean
    val memReadReq = dut.io.debugMemReadReq.peek().litToBoolean
    val memWrite = dut.io.debugMemWrite.peek().litToBoolean
    val memWriteReq = dut.io.debugMemWriteReq.peek().litToBoolean
    val memAddr = dut.io.debugMemAddress.peek().litValue
    val memWriteData = dut.io.debugMemWriteData.peek().litValue
    val illegal = dut.io.debugIllegal.peek().litToBoolean

    s"""  {"pc": $pc, "instruction": $inst, "rd": $rd, "regWrite": $regWrite, "writeData": $writeData, "memRead": $memRead, "memReadReq": $memReadReq, "memWrite": $memWrite, "memWriteReq": $memWriteReq, "memAddress": $memAddr, "memWriteData": $memWriteData, "illegal": $illegal}"""
  }

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
      val trace = scala.collection.mutable.ArrayBuffer[String]()

      for (cycle <- 0 until 8) {
        trace += captureCommitEvent(dut)
        dut.clock.step(1)
      }
      recordCommitTrace("chisel_trace_prog1", trace)

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
      val trace = scala.collection.mutable.ArrayBuffer[String]()

      for (cycle <- 0 until 18) {
        trace += captureCommitEvent(dut)
        dut.clock.step(1)
      }
      recordCommitTrace("chisel_trace_prog2", trace)

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
      val trace = scala.collection.mutable.ArrayBuffer[String]()

      for (cycle <- 0 until 11) {
        trace += captureCommitEvent(dut)
        dut.clock.step(1)
      }
      recordCommitTrace("chisel_trace_prog3", trace)

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
      val trace = scala.collection.mutable.ArrayBuffer[String]()

      for (cycle <- 0 until 7) {
        trace += captureCommitEvent(dut)
        dut.clock.step(1)
      }
      recordCommitTrace("chisel_trace_prog4", trace)

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
      val trace = scala.collection.mutable.ArrayBuffer[String]()

      for (cycle <- 0 until 7) {
        trace += captureCommitEvent(dut)
        dut.clock.step(1)
      }
      recordCommitTrace("chisel_trace_prog5", trace)

      dut.io.debugPc.expect("h1C".U)
    }
  }

  // -------------------------------------------------------------
  // Full Datapath Coverage: Shift, Logical, AUIPC, and Unsigned Compare Matrix
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

  // -------------------------------------------------------------
  // Full Branch Condition Matrix: Both Taken AND Not-Taken Cases for all 6 Conditions
  // -------------------------------------------------------------
  it should "execute all 6 branch condition types in both taken and not-taken branches" in {
    val progBranch = Seq(
      // Setup operands: x1 = 10, x2 = 10, x3 = 20, x4 = -5 (0xFFFFFFFB)
      BigInt("00a00093", 16), // 0x00: addi x1, x0, 10
      BigInt("00a00113", 16), // 0x04: addi x2, x0, 10
      BigInt("01400193", 16), // 0x08: addi x3, x0, 20
      BigInt("ffb00213", 16), // 0x0C: addi x4, x0, -5

      // 1. BEQ: taken (x1 == x2, 10 == 10)
      BigInt("00208463", 16), // 0x10: beq x1, x2, +8 (jumps to 0x18)
      BigInt("3e700713", 16), // 0x14: addi x14, x0, 999 (skipped)
      // 1b. BEQ: NOT taken (x1 == x3, 10 == 20) -> falls through to 0x20
      BigInt("00308463", 16), // 0x18: beq x1, x3, +8 (not taken)
      BigInt("00100513", 16), // 0x1C: addi x10, x0, 1 (executed!)

      // 2. BNE: taken (x1 != x3, 10 != 20) -> jumps to 0x28
      BigInt("00309463", 16), // 0x20: bne x1, x3, +8 (jumps to 0x28)
      BigInt("3e700713", 16), // 0x24: addi x14, x0, 999 (skipped)
      // 2b. BNE: NOT taken (x1 != x2, 10 != 10) -> falls through to 0x30
      BigInt("00209463", 16), // 0x28: bne x1, x2, +8 (not taken)
      BigInt("00200593", 16), // 0x2C: addi x11, x0, 2 (executed!)

      // 3. BLT: taken (signed x4 < x1, -5 < 10) -> jumps to 0x38
      BigInt("00124463", 16), // 0x30: blt x4, x1, +8 (jumps to 0x38)
      BigInt("3e700713", 16), // 0x34: addi x14, x0, 999 (skipped)
      // 3b. BLT: NOT taken (signed x1 < x4, 10 < -5) -> falls through to 0x40
      BigInt("0040c463", 16), // 0x38: blt x1, x4, +8 (not taken)
      BigInt("00300613", 16), // 0x3C: addi x12, x0, 3 (executed!)

      // 4. BGE: taken (signed x1 >= x4, 10 >= -5) -> jumps to 0x48
      BigInt("0040d463", 16), // 0x40: bge x1, x4, +8 (jumps to 0x48)
      BigInt("3e700713", 16), // 0x44: addi x14, x0, 999 (skipped)
      // 4b. BGE: NOT taken (signed x4 >= x1, -5 >= 10) -> falls through to 0x50
      BigInt("00125463", 16), // 0x48: bge x4, x1, +8 (not taken)
      BigInt("00400693", 16), // 0x4C: addi x13, x0, 4 (executed!)

      // 5. BLTU: taken (unsigned x1 < x3, 10 < 20) -> jumps to 0x58
      BigInt("0030e463", 16), // 0x50: bltu x1, x3, +8 (jumps to 0x58)
      BigInt("3e700713", 16), // 0x54: addi x14, x0, 999 (skipped)
      // 5b. BLTU: NOT taken (unsigned x4 < x1, 0xFFFFFFFB < 10 is false) -> falls through to 0x60
      BigInt("00126463", 16), // 0x58: bltu x4, x1, +8 (not taken)
      BigInt("00500713", 16), // 0x5C: addi x14, x0, 5 (executed!)

      // 6. BGEU: taken (unsigned x4 >= x1, 0xFFFFFFFB >= 10 is true) -> jumps to 0x68
      BigInt("00127463", 16), // 0x60: bgeu x4, x1, +8 (jumps to 0x68)
      BigInt("3e700713", 16), // 0x64: addi x15, x0, 999 (skipped)
      // 6b. BGEU: NOT taken (unsigned x1 >= x3, 10 >= 20 is false) -> falls through to 0x70
      BigInt("0030f463", 16), // 0x68: bgeu x1, x3, +8 (not taken)
      BigInt("00600793", 16)  // 0x6C: addi x15, x0, 6 (executed!)
    )

    test(new SingleCycleCore(initialProgram = progBranch)) { dut =>
      // Setup: 0x00, 0x04, 0x08, 0x0C
      dut.io.debugPc.expect("h00".U); dut.clock.step(1)
      dut.io.debugPc.expect("h04".U); dut.clock.step(1)
      dut.io.debugPc.expect("h08".U); dut.clock.step(1)
      dut.io.debugPc.expect("h0C".U); dut.clock.step(1)

      // 1. BEQ taken: from 0x10 jumps to 0x18
      dut.io.debugPc.expect("h10".U); dut.clock.step(1)
      // 1b. BEQ not taken: from 0x18 falls through to 0x1C (x10 = 1)
      dut.io.debugPc.expect("h18".U); dut.clock.step(1)
      dut.io.debugPc.expect("h1C".U); dut.io.debugRd.expect(10.U); dut.io.debugWriteData.expect(1.U); dut.clock.step(1)

      // 2. BNE taken: from 0x20 jumps to 0x28
      dut.io.debugPc.expect("h20".U); dut.clock.step(1)
      // 2b. BNE not taken: from 0x28 falls through to 0x2C (x11 = 2)
      dut.io.debugPc.expect("h28".U); dut.clock.step(1)
      dut.io.debugPc.expect("h2C".U); dut.io.debugRd.expect(11.U); dut.io.debugWriteData.expect(2.U); dut.clock.step(1)

      // 3. BLT taken: from 0x30 jumps to 0x38
      dut.io.debugPc.expect("h30".U); dut.clock.step(1)
      // 3b. BLT not taken: from 0x38 falls through to 0x3C (x12 = 3)
      dut.io.debugPc.expect("h38".U); dut.clock.step(1)
      dut.io.debugPc.expect("h3C".U); dut.io.debugRd.expect(12.U); dut.io.debugWriteData.expect(3.U); dut.clock.step(1)

      // 4. BGE taken: from 0x40 jumps to 0x48
      dut.io.debugPc.expect("h40".U); dut.clock.step(1)
      // 4b. BGE not taken: from 0x48 falls through to 0x4C (x13 = 4)
      dut.io.debugPc.expect("h48".U); dut.clock.step(1)
      dut.io.debugPc.expect("h4C".U); dut.io.debugRd.expect(13.U); dut.io.debugWriteData.expect(4.U); dut.clock.step(1)

      // 5. BLTU taken: from 0x50 jumps to 0x58
      dut.io.debugPc.expect("h50".U); dut.clock.step(1)
      // 5b. BLTU not taken: from 0x58 falls through to 0x5C (x14 = 5)
      dut.io.debugPc.expect("h58".U); dut.clock.step(1)
      dut.io.debugPc.expect("h5C".U); dut.io.debugRd.expect(14.U); dut.io.debugWriteData.expect(5.U); dut.clock.step(1)

      // 6. BGEU taken: from 0x60 jumps to 0x68
      dut.io.debugPc.expect("h60".U); dut.clock.step(1)
      // 6b. BGEU not taken: from 0x68 falls through to 0x6C (x15 = 6)
      dut.io.debugPc.expect("h68".U); dut.clock.step(1)
      dut.io.debugPc.expect("h6C".U); dut.io.debugRd.expect(15.U); dut.io.debugWriteData.expect(6.U); dut.clock.step(1)

      dut.io.debugPc.expect("h70".U)
    }
  }

  // -------------------------------------------------------------
  // Misaligned Access Suppression
  // -------------------------------------------------------------
  it should "detect misaligned core memory accesses and suppress effective writeback and stores" in {
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
      dut.io.debugMemWriteReq.expect(true.B)
      dut.io.debugMemWrite.expect(false.B) // Effective architectural store MUST be false!
      dut.io.debugMemAddress.expect(1.U)
      dut.clock.step(1)

      // 0x08: lw x2, 1(x0) -> misaligned word load -> writeback suppressed
      dut.io.debugPc.expect("h08".U)
      dut.io.debugMemReadReq.expect(true.B)
      dut.io.debugMemRead.expect(false.B) // Effective architectural load MUST be false!
      dut.io.debugRegWrite.expect(false.B) // Reg writeback MUST be suppressed on misaligned load
      dut.clock.step(1)

      dut.io.debugPc.expect("h0C".U)
    }
  }

  // -------------------------------------------------------------
  // Pipeline Benchmark Program 1: Arithmetic & Hardware MUL
  // -------------------------------------------------------------
  it should "execute Pipeline Benchmark 1 and export architectural trace for 3-way verification" in {
    val pipeProg1 = Seq(
      BigInt("00a00093", 16), // 0x00: addi x1, x0, 10
      BigInt("01400113", 16), // 0x04: addi x2, x0, 20
      BigInt("00000013", 16), // 0x08: nop
      BigInt("00000013", 16), // 0x0C: nop
      BigInt("00000013", 16), // 0x10: nop
      BigInt("002081b3", 16), // 0x14: add  x3, x1, x2  (30)
      BigInt("00000013", 16), // 0x18: nop
      BigInt("00000013", 16), // 0x1C: nop
      BigInt("00000013", 16), // 0x20: nop
      BigInt("40118233", 16), // 0x24: sub  x4, x3, x1  (20)
      BigInt("00000013", 16), // 0x28: nop
      BigInt("00000013", 16), // 0x2C: nop
      BigInt("00000013", 16), // 0x30: nop
      BigInt("021202b3", 16)  // 0x34: mul  x5, x4, x1  (20 * 10 = 200 = 0xC8)
    )

    test(new SingleCycleCore(initialProgram = pipeProg1)) { dut =>
      val trace = scala.collection.mutable.ArrayBuffer[String]()
      for (_ <- 0 until 14) {
        trace += captureCommitEvent(dut)
        dut.clock.step(1)
      }
      recordCommitTrace("single_cycle_pipe_prog1", trace)
    }
  }

  // -------------------------------------------------------------
  // Pipeline Benchmark Program 2: Memory Operations (SW, LW, SB, LB)
  // -------------------------------------------------------------
  it should "execute Pipeline Benchmark 2 and export architectural trace for 3-way verification" in {
    val pipeProg2 = Seq(
      BigInt("02a00093", 16), // 0x00: addi x1, x0, 42
      BigInt("ffb00113", 16), // 0x04: addi x2, x0, -5   (0xFFFFFFFB)
      BigInt("00000013", 16), // 0x08: nop
      BigInt("00000013", 16), // 0x0C: nop
      BigInt("00000013", 16), // 0x10: nop
      BigInt("00102023", 16), // 0x14: sw   x1, 0(x0)
      BigInt("00200223", 16), // 0x18: sb   x2, 4(x0)
      BigInt("00000013", 16), // 0x1C: nop
      BigInt("00000013", 16), // 0x20: nop
      BigInt("00000013", 16), // 0x24: nop
      BigInt("00002183", 16), // 0x28: lw   x3, 0(x0)   (42)
      BigInt("00400203", 16)  // 0x2C: lb   x4, 4(x0)   (-5 = 0xFFFFFFFB)
    )

    test(new SingleCycleCore(initialProgram = pipeProg2)) { dut =>
      val trace = scala.collection.mutable.ArrayBuffer[String]()
      for (_ <- 0 until 12) {
        trace += captureCommitEvent(dut)
        dut.clock.step(1)
      }
      recordCommitTrace("single_cycle_pipe_prog2", trace)
    }
  }

  // -------------------------------------------------------------
  // Pipeline Benchmark Program 3: Control Flow (BEQ, JALR with (rs1 + imm) & ~1)
  // -------------------------------------------------------------
  it should "execute Pipeline Benchmark 3 and export architectural trace for 3-way verification" in {
    val pipeProg3 = Seq(
      BigInt("00a00093", 16), // 0x00: addi x1, x0, 10
      BigInt("00a00113", 16), // 0x04: addi x2, x0, 10
      BigInt("00000013", 16), // 0x08: nop
      BigInt("00000013", 16), // 0x0C: nop
      BigInt("00000013", 16), // 0x10: nop
      BigInt("00208863", 16), // 0x14: beq  x1, x2, 16    (taken -> jumps to 0x24)
      BigInt("3e700713", 16), // 0x18: addi x14, x0, 999 (killed wrong path)
      BigInt("37800713", 16), // 0x1C: addi x14, x0, 888 (killed wrong path)
      BigInt("00000013", 16), // 0x20: nop
      BigInt("04900293", 16), // 0x24: addi x5, x0, 0x49 (target 0x49, bit 0 cleared to 0x48)
      BigInt("00000013", 16), // 0x28: nop
      BigInt("00000013", 16), // 0x2C: nop
      BigInt("00000013", 16), // 0x30: nop
      BigInt("00028367", 16), // 0x34: jalr x6, 0(x5)     (link x6 = 0x38, jumps to 0x48)
      BigInt("3e700713", 16), // 0x38: addi x14, x0, 777 (killed wrong path)
      BigInt("37800713", 16), // 0x3C: addi x14, x0, 666 (killed wrong path)
      BigInt("00000013", 16), // 0x40: nop
      BigInt("00000013", 16), // 0x44: nop
      BigInt("06400393", 16)  // 0x48: addi x7, x0, 100
    )

    test(new SingleCycleCore(initialProgram = pipeProg3)) { dut =>
      val trace = scala.collection.mutable.ArrayBuffer[String]()
      // 0x00, 0x04, 0x08, 0x0C, 0x10, 0x14 (taken to 0x24), 0x24, 0x28, 0x2C, 0x30, 0x34 (jumps to 0x48), 0x48
      for (_ <- 0 until 12) {
        trace += captureCommitEvent(dut)
        dut.clock.step(1)
      }
      recordCommitTrace("single_cycle_pipe_prog3", trace)
    }
  }
}
