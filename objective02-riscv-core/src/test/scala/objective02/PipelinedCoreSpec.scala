package objective02

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import objective02.pipeline.PipelinedCore
import java.io.{File, PrintWriter}

class PipelinedCoreSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "PipelinedCore"

  // Helper to record pipeline retirement trace to JSON file
  def recordPipelineTrace(progName: String, trace: Seq[String]): Unit = {
    val dir = new File("test_traces")
    if (!dir.exists()) dir.mkdirs()
    val writer = new PrintWriter(new File(s"test_traces/$progName.json"))
    try {
      writer.write("[\n" + trace.mkString(",\n") + "\n]\n")
    } finally {
      writer.close()
    }
  }

  // Helper to capture retirement event if commit.valid is true
  def captureRetirementEvent(dut: PipelinedCore): Option[String] = {
    val valid = dut.io.commit.valid.peek().litToBoolean
    if (valid) {
      val pc = dut.io.commit.pc.peek().litValue
      val inst = dut.io.commit.instruction.peek().litValue
      val rd = dut.io.commit.rd.peek().litValue
      val regWrite = dut.io.commit.regWrite.peek().litToBoolean
      val writeData = dut.io.commit.writeData.peek().litValue
      val memRead = dut.io.commit.memRead.peek().litToBoolean
      val memReadReq = dut.io.commit.memReadReq.peek().litToBoolean
      val memWrite = dut.io.commit.memWrite.peek().litToBoolean
      val memWriteReq = dut.io.commit.memWriteReq.peek().litToBoolean
      val memAddr = dut.io.commit.memAddress.peek().litValue
      val memWriteData = dut.io.commit.memWriteData.peek().litValue
      val illegal = dut.io.commit.illegal.peek().litToBoolean

      Some(s"""  {"pc": $pc, "instruction": $inst, "rd": $rd, "regWrite": $regWrite, "writeData": $writeData, "memRead": $memRead, "memReadReq": $memReadReq, "memWrite": $memWrite, "memWriteReq": $memWriteReq, "memAddress": $memAddr, "memWriteData": $memWriteData, "illegal": $illegal}""")
    } else {
      None
    }
  }

  // -------------------------------------------------------------
  // Test 1: Pipeline Filling, Single Instruction Traversal, and Drain
  // -------------------------------------------------------------
  it should "correctly traverse a single instruction through all 5 stages and commit exactly once in WB" in {
    val progSingle = Seq(
      BigInt("00a00093", 16) // 0x00: addi x1, x0, 10
    )

    test(new PipelinedCore(initialProgram = progSingle)) { dut =>
      // Cycle 0: In IF stage
      dut.io.stageIF.valid.expect(true.B)
      dut.io.stageIF.pc.expect("h00".U)
      dut.io.commit.valid.expect(false.B)
      dut.clock.step(1)

      // Cycle 1: In ID stage
      dut.io.stageID.valid.expect(true.B)
      dut.io.stageID.pc.expect("h00".U)
      dut.io.commit.valid.expect(false.B)
      dut.clock.step(1)

      // Cycle 2: In EX stage
      dut.io.stageEX.valid.expect(true.B)
      dut.io.stageEX.pc.expect("h00".U)
      dut.io.commit.valid.expect(false.B)
      dut.clock.step(1)

      // Cycle 3: In MEM stage
      dut.io.stageMEM.valid.expect(true.B)
      dut.io.stageMEM.pc.expect("h00".U)
      dut.io.commit.valid.expect(false.B)
      dut.clock.step(1)

      // Cycle 4: In WB stage -> Commits architecturally!
      dut.io.stageWB.valid.expect(true.B)
      dut.io.stageWB.pc.expect("h00".U)
      dut.io.commit.valid.expect(true.B)
      dut.io.commit.pc.expect("h00".U)
      dut.io.commit.rd.expect(1.U)
      dut.io.commit.regWrite.expect(true.B)
      dut.io.commit.writeData.expect(10.U)
      dut.clock.step(1)

      // Cycle 5: Pipeline drains
      dut.io.commit.valid.expect(false.B)
    }
  }

  // -------------------------------------------------------------
  // Test 2: Multiple Independent Instructions & Steady-State Throughput
  // -------------------------------------------------------------
  it should "execute multiple independent instructions with 1 instruction retired per cycle after fill" in {
    val progIndep = Seq(
      BigInt("00100093", 16), // 0x00: addi x1, x0, 1
      BigInt("00200113", 16), // 0x04: addi x2, x0, 2
      BigInt("00300193", 16), // 0x08: addi x3, x0, 3
      BigInt("00400213", 16)  // 0x0C: addi x4, x0, 4
    )

    test(new PipelinedCore(initialProgram = progIndep)) { dut =>
      // Cycles 0..3: Pipeline fill
      for (c <- 0 until 4) {
        dut.io.commit.valid.expect(false.B)
        dut.clock.step(1)
      }

      // Cycle 4: Retirement of inst 1 (x1 = 1)
      dut.io.commit.valid.expect(true.B)
      dut.io.commit.pc.expect("h00".U)
      dut.io.commit.rd.expect(1.U)
      dut.io.commit.writeData.expect(1.U)
      dut.clock.step(1)

      // Cycle 5: Retirement of inst 2 (x2 = 2)
      dut.io.commit.valid.expect(true.B)
      dut.io.commit.pc.expect("h04".U)
      dut.io.commit.rd.expect(2.U)
      dut.io.commit.writeData.expect(2.U)
      dut.clock.step(1)

      // Cycle 6: Retirement of inst 3 (x3 = 3)
      dut.io.commit.valid.expect(true.B)
      dut.io.commit.pc.expect("h08".U)
      dut.io.commit.rd.expect(3.U)
      dut.io.commit.writeData.expect(3.U)
      dut.clock.step(1)

      // Cycle 7: Retirement of inst 4 (x4 = 4)
      dut.io.commit.valid.expect(true.B)
      dut.io.commit.pc.expect("h0C".U)
      dut.io.commit.rd.expect(4.U)
      dut.io.commit.writeData.expect(4.U)
      dut.clock.step(1)

      // Cycle 8: Pipeline drained
      dut.io.commit.valid.expect(false.B)
    }
  }

  // -------------------------------------------------------------
  // Test 3: Branch Redirection and Wrong-Path Flushing
  // -------------------------------------------------------------
  it should "correctly flush IF/ID and ID/EX on taken branch and prevent wrong-path instructions from retiring" in {
    val progBranch = Seq(
      BigInt("00a00093", 16), // 0x00: addi x1, x0, 10
      BigInt("00a00113", 16), // 0x04: addi x2, x0, 10
      BigInt("00000013", 16), // 0x08: nop
      BigInt("00000013", 16), // 0x0C: nop
      BigInt("00208863", 16), // 0x10: beq  x1, x2, +16 (target = 0x20)
      BigInt("3e700713", 16), // 0x14: addi x14, x0, 999 (wrong path 1 -> MUST BE FLUSHED)
      BigInt("37800713", 16), // 0x18: addi x14, x0, 888 (wrong path 2 -> MUST BE FLUSHED)
      BigInt("00000013", 16), // 0x1C: nop
      BigInt("02a00193", 16)  // 0x20: addi x3, x0, 42  (target instruction!)
    )

    test(new PipelinedCore(initialProgram = progBranch)) { dut =>
      val retiredPcs = scala.collection.mutable.ArrayBuffer[BigInt]()
      val retiredRds = scala.collection.mutable.ArrayBuffer[BigInt]()
      val retiredVals = scala.collection.mutable.ArrayBuffer[BigInt]()

      for (c <- 0 until 18) {
        if (dut.io.commit.valid.peek().litToBoolean) {
          retiredPcs += dut.io.commit.pc.peek().litValue
          retiredRds += dut.io.commit.rd.peek().litValue
          retiredVals += dut.io.commit.writeData.peek().litValue
        }
        dut.clock.step(1)
      }

      // Verify that 0x14 and 0x18 were NEVER committed!
      retiredPcs should contain (BigInt(0x00))
      retiredPcs should contain (BigInt(0x04))
      retiredPcs should contain (BigInt(0x10))
      retiredPcs should contain (BigInt(0x20))
      retiredPcs should not contain (BigInt(0x14))
      retiredPcs should not contain (BigInt(0x18))

      // Verify that rd=14 (999 or 888) was NEVER written!
      retiredRds should not contain (BigInt(14))
      retiredVals should not contain (BigInt(999))
      retiredVals should not contain (BigInt(888))
    }
  }

  // -------------------------------------------------------------
  // Test 4: JAL Control Flow and Link Register Commit
  // -------------------------------------------------------------
  it should "correctly execute JAL with link register writeback and branch target redirect" in {
    val progJal = Seq(
      BigInt("010000ef", 16), // 0x00: jal  x1, 16      (target = 0x10, link = 0x04)
      BigInt("3e700713", 16), // 0x04: addi x14, x0, 999 (wrong path 1)
      BigInt("37800713", 16), // 0x08: addi x14, x0, 888 (wrong path 2)
      BigInt("00000013", 16), // 0x0C: nop
      BigInt("06400113", 16)  // 0x10: addi x2, x0, 100  (target)
    )

    test(new PipelinedCore(initialProgram = progJal)) { dut =>
      val retiredPcs = scala.collection.mutable.ArrayBuffer[BigInt]()
      val retiredRds = scala.collection.mutable.ArrayBuffer[BigInt]()

      for (c <- 0 until 12) {
        if (dut.io.commit.valid.peek().litToBoolean) {
          retiredPcs += dut.io.commit.pc.peek().litValue
          retiredRds += dut.io.commit.rd.peek().litValue
        }
        dut.clock.step(1)
      }

      retiredPcs should contain (BigInt(0x00))
      retiredPcs should contain (BigInt(0x10))
      retiredPcs should not contain (BigInt(0x04))
      retiredPcs should not contain (BigInt(0x08))
      retiredRds should contain (BigInt(1))
      retiredRds should contain (BigInt(2))
      retiredRds should not contain (BigInt(14))
    }
  }

  // -------------------------------------------------------------
  // Test 5: JALR Indirect Jump with LSB=0 Masking and Link Writeback
  // -------------------------------------------------------------
  it should "correctly execute JALR with (rs1 + imm) & ~1 target address masking and wrong-path flush" in {
    val progJalr = Seq(
      BigInt("01100293", 16), // 0x00: addi x5, x0, 0x11 (odd address 17)
      BigInt("00000013", 16), // 0x04: nop
      BigInt("00000013", 16), // 0x08: nop
      BigInt("00000013", 16), // 0x0C: nop
      BigInt("000280e7", 16), // 0x10: jalr x1, 0(x5)    (target: (0x11 + 0) & ~1 = 0x10; link x1 = 0x14)
      BigInt("3e700713", 16), // 0x14: addi x14, x0, 999 (wrong path 1)
      BigInt("37800713", 16), // 0x18: addi x14, x0, 888 (wrong path 2)
      BigInt("00000013", 16), // 0x1C: nop
      BigInt("04d00113", 16)  // 0x10 (overwritten by jump target): addi x2, x0, 77
    )

    // Note: To jump to an forward target, let's use base 0x21 (33) -> (0x21) & ~1 = 0x20
    val progJalrForward = Seq(
      BigInt("02100293", 16), // 0x00: addi x5, x0, 0x21 (odd address 33)
      BigInt("00000013", 16), // 0x04: nop
      BigInt("00000013", 16), // 0x08: nop
      BigInt("00000013", 16), // 0x0C: nop
      BigInt("000280e7", 16), // 0x10: jalr x1, 0(x5)    (link x1 = 0x14; target (0x21) & ~1 = 0x20)
      BigInt("3e700713", 16), // 0x14: addi x14, x0, 999 (wrong path 1 -> killed)
      BigInt("37800713", 16), // 0x18: addi x14, x0, 888 (wrong path 2 -> killed)
      BigInt("00000013", 16), // 0x1C: nop
      BigInt("04d00113", 16)  // 0x20: addi x2, x0, 77   (target instruction!)
    )

    test(new PipelinedCore(initialProgram = progJalrForward)) { dut =>
      val retiredPcs = scala.collection.mutable.ArrayBuffer[BigInt]()
      val retiredRds = scala.collection.mutable.ArrayBuffer[BigInt]()
      var linkVal: BigInt = 0
      var targetVal: BigInt = 0

      for (c <- 0 until 18) {
        if (dut.io.commit.valid.peek().litToBoolean) {
          val pc = dut.io.commit.pc.peek().litValue
          val rd = dut.io.commit.rd.peek().litValue
          retiredPcs += pc
          retiredRds += rd
          if (pc == BigInt(0x10) && rd == BigInt(1)) {
            linkVal = dut.io.commit.writeData.peek().litValue
          }
          if (pc == BigInt(0x20) && rd == BigInt(2)) {
            targetVal = dut.io.commit.writeData.peek().litValue
          }
        }
        dut.clock.step(1)
      }

      // Link register x1 must receive PC + 4 = 0x14
      linkVal shouldBe BigInt(0x14)
      // Target instruction must execute and produce x2 = 77
      targetVal shouldBe BigInt(77)

      // Verify wrong-path instructions were flushed
      retiredPcs should not contain (BigInt(0x14))
      retiredPcs should not contain (BigInt(0x18))
      retiredRds should not contain (BigInt(14))
    }
  }

  // -------------------------------------------------------------
  // Test 6: Hazard-Free Memory Operations (Word SW/LW and Byte SB/LB)
  // -------------------------------------------------------------
  it should "execute hazard-free word and byte memory operations (SW, LW, SB, LB)" in {
    val progMem = Seq(
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
      BigInt("00002183", 16), // 0x28: lw   x3, 0(x0)   -> x3 = 42
      BigInt("00400203", 16)  // 0x2C: lb   x4, 4(x0)   -> x4 = -5 (0xFFFFFFFB)
    )

    test(new PipelinedCore(initialProgram = progMem)) { dut =>
      var sawSw = false
      var sawSb = false
      var sawLw = false
      var sawLb = false

      for (c <- 0 until 24) {
        if (dut.io.commit.valid.peek().litToBoolean) {
          val pc = dut.io.commit.pc.peek().litValue
          if (pc == BigInt(0x14)) {
            dut.io.commit.memWrite.expect(true.B)
            dut.io.commit.memAddress.expect(0.U)
            dut.io.commit.memWriteData.expect(42.U)
            sawSw = true
          }
          if (pc == BigInt(0x18)) {
            dut.io.commit.memWrite.expect(true.B)
            dut.io.commit.memAddress.expect(4.U)
            sawSb = true
          }
          if (pc == BigInt(0x28)) {
            dut.io.commit.memRead.expect(true.B)
            dut.io.commit.rd.expect(3.U)
            dut.io.commit.writeData.expect(42.U)
            sawLw = true
          }
          if (pc == BigInt(0x2C)) {
            dut.io.commit.memRead.expect(true.B)
            dut.io.commit.rd.expect(4.U)
            dut.io.commit.writeData.expect("hFFFFFFFB".U)
            sawLb = true
          }
        }
        dut.clock.step(1)
      }

      sawSw shouldBe true
      sawSb shouldBe true
      sawLw shouldBe true
      sawLb shouldBe true
    }
  }

  // -------------------------------------------------------------
  // Test 7: Hardware Multiplier (Objective 1 Booth-Wallace Radix-4 Tree in EX)
  // -------------------------------------------------------------
  it should "execute hardware multiplication through EX stage and commit correct product" in {
    val progMul = Seq(
      BigInt("00700093", 16), // 0x00: addi x1, x0, 7
      BigInt("ffb00113", 16), // 0x04: addi x2, x0, -5
      BigInt("00000013", 16), // 0x08: nop
      BigInt("00000013", 16), // 0x0C: nop
      BigInt("022081b3", 16)  // 0x10: mul  x3, x1, x2 (7 * -5 = -35 = 0xFFFFFFDD)
    )

    test(new PipelinedCore(initialProgram = progMul)) { dut =>
      var sawMulCommit = false

      for (c <- 0 until 14) {
        if (dut.io.commit.valid.peek().litToBoolean) {
          val pc = dut.io.commit.pc.peek().litValue
          if (pc == BigInt(0x10)) {
            dut.io.commit.rd.expect(3.U)
            dut.io.commit.writeData.expect("hFFFFFFDD".U)
            sawMulCommit = true
          }
        }
        dut.clock.step(1)
      }

      sawMulCommit shouldBe true
    }
  }

  // -------------------------------------------------------------
  // Test 8: Export 3-Way Differential Benchmark Traces
  // -------------------------------------------------------------
  it should "export pipeline benchmark traces 1, 2, and 3 for 3-way differential verification" in {
    // Benchmark 1: Arithmetic & MUL
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

    test(new PipelinedCore(initialProgram = pipeProg1)) { dut =>
      val trace = scala.collection.mutable.ArrayBuffer[String]()
      for (_ <- 0 until 20) {
        captureRetirementEvent(dut).foreach(trace += _)
        dut.clock.step(1)
      }
      recordPipelineTrace("pipelined_core_prog1", trace)
    }

    // Benchmark 2: Memory Operations
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

    test(new PipelinedCore(initialProgram = pipeProg2)) { dut =>
      val trace = scala.collection.mutable.ArrayBuffer[String]()
      for (_ <- 0 until 18) {
        captureRetirementEvent(dut).foreach(trace += _)
        dut.clock.step(1)
      }
      recordPipelineTrace("pipelined_core_prog2", trace)
    }

    // Benchmark 3: Control Flow (BEQ, JALR)
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

    test(new PipelinedCore(initialProgram = pipeProg3)) { dut =>
      val trace = scala.collection.mutable.ArrayBuffer[String]()
      for (_ <- 0 until 20) {
        captureRetirementEvent(dut).foreach(trace += _)
        dut.clock.step(1)
      }
      recordPipelineTrace("pipelined_core_prog3", trace)
    }
  }
}
