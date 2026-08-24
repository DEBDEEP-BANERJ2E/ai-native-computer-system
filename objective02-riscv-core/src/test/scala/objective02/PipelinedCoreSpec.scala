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
  // Test 1: RAW Data Hazard Forwarding (EX/MEM -> EX & MEM/WB -> EX)
  // -------------------------------------------------------------
  it should "correctly resolve back-to-back RAW data hazards via EX/MEM and MEM/WB forwarding" in {
    val progRaw = Seq(
      BigInt("00a00093", 16), // 0x00: addi x1, x0, 10
      BigInt("01400113", 16), // 0x04: addi x2, x0, 20
      BigInt("002081b3", 16), // 0x08: add  x3, x1, x2 (EX/MEM forward x2, MEM/WB forward x1 -> 30)
      BigInt("40118233", 16), // 0x0C: sub  x4, x3, x1 (EX/MEM forward x3 -> 20)
      BigInt("003222b3", 16)  // 0x10: slt  x5, x4, x3 (EX/MEM forward x4, MEM/WB forward x3 -> 1)
    )

    test(new PipelinedCore(initialProgram = progRaw)) { dut =>
      val retiredPcs = scala.collection.mutable.ArrayBuffer[BigInt]()
      val retiredVals = scala.collection.mutable.ArrayBuffer[BigInt]()

      for (_ <- 0 until 12) {
        if (dut.io.commit.valid.peek().litToBoolean) {
          retiredPcs += dut.io.commit.pc.peek().litValue
          retiredVals += dut.io.commit.writeData.peek().litValue
        }
        dut.clock.step(1)
      }

      retiredPcs shouldBe Seq(BigInt(0x00), BigInt(0x04), BigInt(0x08), BigInt(0x0C), BigInt(0x10))
      retiredVals shouldBe Seq(BigInt(10), BigInt(20), BigInt(30), BigInt(20), BigInt(1))
    }
  }

  // -------------------------------------------------------------
  // Test 2: RAW Forwarding into Branch & JALR Units
  // -------------------------------------------------------------
  it should "forward newly computed register values directly into Branch and JALR units" in {
    val progBranchForward = Seq(
      BigInt("00500093", 16), // 0x00: addi x1, x0, 5
      BigInt("00500113", 16), // 0x04: addi x2, x0, 5
      BigInt("00208863", 16), // 0x08: beq  x1, x2, 16 (EX/MEM forwards x2=5, MEM/WB forwards x1=5 -> jumps to 0x18)
      BigInt("3e700713", 16), // 0x0C: addi x14, x0, 999 (killed)
      BigInt("37800713", 16), // 0x10: addi x14, x0, 888 (killed)
      BigInt("00000013", 16), // 0x14: nop
      BigInt("02400293", 16), // 0x18: addi x5, x0, 0x24 (target 0x24)
      BigInt("00028367", 16), // 0x1C: jalr x6, 0(x5) (EX/MEM forwards x5=0x24 -> jumps to 0x24, link x6=0x20)
      BigInt("30900713", 16), // 0x20: addi x14, x0, 777 (killed)
      BigInt("04d00393", 16)  // 0x24: addi x7, x0, 77   (target reached!)
    )

    test(new PipelinedCore(initialProgram = progBranchForward)) { dut =>
      val retiredPcs = scala.collection.mutable.ArrayBuffer[BigInt]()
      val retiredRds = scala.collection.mutable.ArrayBuffer[BigInt]()
      val retiredVals = scala.collection.mutable.ArrayBuffer[BigInt]()

      for (_ <- 0 until 18) {
        if (dut.io.commit.valid.peek().litToBoolean) {
          retiredPcs += dut.io.commit.pc.peek().litValue
          retiredRds += dut.io.commit.rd.peek().litValue
          retiredVals += dut.io.commit.writeData.peek().litValue
        }
        dut.clock.step(1)
      }

      retiredPcs should not contain (BigInt(0x0C))
      retiredPcs should not contain (BigInt(0x10))
      retiredRds should not contain (BigInt(14))
      retiredRds should contain (BigInt(7))
      retiredVals should contain (BigInt(77))
    }
  }

  // -------------------------------------------------------------
  // Test 3: Load-Use Hazard 1-Cycle Stall & MEM/WB Forwarding
  // -------------------------------------------------------------
  it should "detect Load-Use hazard, stall IF/ID for exactly 1 cycle, and forward loaded data from MEM/WB" in {
    val progLoadUse = Seq(
      BigInt("02a00093", 16), // 0x00: addi x1, x0, 42
      BigInt("00102023", 16), // 0x04: sw   x1, 0(x0)  (store 42 at addr 0)
      BigInt("00002103", 16), // 0x08: lw   x2, 0(x0)  (load 42 into x2)
      BigInt("00a10193", 16)  // 0x0C: addi x3, x2, 10 (LOAD-USE HAZARD on x2! Stalls 1 cycle, then x3 = 52)
    )

    test(new PipelinedCore(initialProgram = progLoadUse)) { dut =>
      val retiredVals = scala.collection.mutable.ArrayBuffer[BigInt]()
      var sawLoadUseStall = false
      var loadUseStallCount = 0

      for (_ <- 0 until 14) {
        if (dut.io.loadUseHazard.peek().litToBoolean) {
          sawLoadUseStall = true
          loadUseStallCount += 1
        }
        if (dut.io.commit.valid.peek().litToBoolean && dut.io.commit.regWrite.peek().litToBoolean) {
          retiredVals += dut.io.commit.writeData.peek().litValue
        }
        dut.clock.step(1)
      }

      sawLoadUseStall shouldBe true
      loadUseStallCount shouldBe 1
      retiredVals should contain (BigInt(42))
      retiredVals should contain (BigInt(52)) // 42 + 10 = 52
    }
  }

  // -------------------------------------------------------------
  // Test 4: Load -> Store Dependency (Stalls 1 cycle and forwards store payload)
  // -------------------------------------------------------------
  it should "detect Load -> Store dependency, stall 1 cycle, and forward store payload" in {
    val progLoadStore = Seq(
      BigInt("02a00093", 16), // 0x00: addi x1, x0, 42
      BigInt("00102023", 16), // 0x04: sw   x1, 0(x0)  (addr 0 = 42)
      BigInt("00002103", 16), // 0x08: lw   x2, 0(x0)  (load 42 into x2)
      BigInt("00202223", 16), // 0x0C: sw   x2, 4(x0)  (store x2 into addr 4 -> LOAD-USE STALL!)
      BigInt("00402183", 16)  // 0x10: lw   x3, 4(x0)  (load from addr 4 -> x3 = 42)
    )

    test(new PipelinedCore(initialProgram = progLoadStore)) { dut =>
      var sawX3_42 = false

      for (_ <- 0 until 18) {
        if (dut.io.commit.valid.peek().litToBoolean) {
          val pc = dut.io.commit.pc.peek().litValue
          val rd = dut.io.commit.rd.peek().litValue
          val data = dut.io.commit.writeData.peek().litValue
          if (pc == BigInt(0x10) && rd == BigInt(3) && data == BigInt(42)) {
            sawX3_42 = true
          }
        }
        dut.clock.step(1)
      }

      sawX3_42 shouldBe true
    }
  }

  // -------------------------------------------------------------
  // Test 4.1: Load-Use Hazard into Branch (lw -> beq)
  // -------------------------------------------------------------
  it should "detect Load-Use hazard and forward loaded data into Branch unit" in {
    val progLwBeq = Seq(
      BigInt("00a00093", 16), // 0x00: addi x1, x0, 10
      BigInt("00102023", 16), // 0x04: sw   x1, 0(x0)  (store 10 at addr 0)
      BigInt("00002283", 16), // 0x08: lw   x5, 0(x0)  (load 10 into x5)
      BigInt("00508463", 16), // 0x0C: beq  x1, x5, 8  (LOAD-USE on x5! Stall 1 cycle, then forward MEM/WB(10) == x1(10) -> jumps to 0x14)
      BigInt("3e700713", 16), // 0x10: addi x14, x0, 999 (killed)
      BigInt("04d00393", 16)  // 0x14: addi x7, x0, 77   (target reached!)
    )

    test(new PipelinedCore(initialProgram = progLwBeq)) { dut =>
      val retiredPcs = scala.collection.mutable.ArrayBuffer[BigInt]()
      var loadUseStallCount = 0

      for (_ <- 0 until 18) {
        if (dut.io.loadUseHazard.peek().litToBoolean) {
          loadUseStallCount += 1
        }
        if (dut.io.commit.valid.peek().litToBoolean) {
          retiredPcs += dut.io.commit.pc.peek().litValue
        }
        dut.clock.step(1)
      }

      loadUseStallCount shouldBe 1
      retiredPcs should not contain (BigInt(0x10))
      retiredPcs should contain (BigInt(0x14))
    }
  }

  // -------------------------------------------------------------
  // Test 4.2: Load-Use Hazard into JALR (lw -> jalr)
  // -------------------------------------------------------------
  it should "detect Load-Use hazard and forward loaded data into JALR unit" in {
    val progLwJalr = Seq(
      BigInt("01400093", 16), // 0x00: addi x1, x0, 0x14
      BigInt("00102023", 16), // 0x04: sw   x1, 0(x0)  (store 0x14 at addr 0)
      BigInt("00002283", 16), // 0x08: lw   x5, 0(x0)  (load 0x14 into x5)
      BigInt("00028067", 16), // 0x0C: jalr x0, 0(x5)  (LOAD-USE on x5! Stall 1 cycle, then forward MEM/WB(0x14) -> jumps to 0x14)
      BigInt("3e700713", 16), // 0x10: addi x14, x0, 999 (killed)
      BigInt("04d00393", 16)  // 0x14: addi x7, x0, 77   (target reached!)
    )

    test(new PipelinedCore(initialProgram = progLwJalr)) { dut =>
      val retiredPcs = scala.collection.mutable.ArrayBuffer[BigInt]()
      var loadUseStallCount = 0

      for (_ <- 0 until 18) {
        if (dut.io.loadUseHazard.peek().litToBoolean) {
          loadUseStallCount += 1
        }
        if (dut.io.commit.valid.peek().litToBoolean) {
          retiredPcs += dut.io.commit.pc.peek().litValue
        }
        dut.clock.step(1)
      }

      loadUseStallCount shouldBe 1
      retiredPcs should not contain (BigInt(0x10))
      retiredPcs should contain (BigInt(0x14))
    }
  }

  // -------------------------------------------------------------
  // Test 5: Execute Original Benchmark Program 1 (Zero NOP spacing)
  // -------------------------------------------------------------
  it should "execute original Benchmark Program 1 without NOP spacing and export trace" in {
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

    test(new PipelinedCore(initialProgram = prog1)) { dut =>
      val trace = scala.collection.mutable.ArrayBuffer[String]()
      for (_ <- 0 until 14) {
        captureRetirementEvent(dut).foreach(trace += _)
        dut.clock.step(1)
      }
      recordPipelineTrace("pipeline_original_prog1", trace)
      trace.length shouldBe 8
    }
  }

  // -------------------------------------------------------------
  // Test 6: Execute Original Benchmark Program 2 (Loop Accumulation, Zero NOPs)
  // -------------------------------------------------------------
  it should "execute original Benchmark Program 2 (Loop Accumulation) and export trace" in {
    val prog2 = Seq(
      BigInt("00500093", 16), // 0x00: addi x1, x0, 5
      BigInt("00000113", 16), // 0x04: addi x2, x0, 0
      BigInt("00110133", 16), // 0x08: add  x2, x2, x1 (loop)
      BigInt("fff08093", 16), // 0x0C: addi x1, x1, -1
      BigInt("fe009ce3", 16), // 0x10: bne  x1, x0, -8 (loop target 0x08)
      BigInt("00000013", 16)  // 0x14: nop / exit
    )

    test(new PipelinedCore(initialProgram = prog2)) { dut =>
      val trace = scala.collection.mutable.ArrayBuffer[String]()
      for (_ <- 0 until 35) {
        captureRetirementEvent(dut).foreach(trace += _)
        dut.clock.step(1)
      }
      recordPipelineTrace("pipeline_original_prog2", trace)
      trace.length shouldBe 18
    }
  }

  // -------------------------------------------------------------
  // Test 7: Execute Original Benchmark Program 3 (Memory Operations, Zero NOPs)
  // -------------------------------------------------------------
  it should "execute original Benchmark Program 3 (Memory Operations) and export trace" in {
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

    test(new PipelinedCore(initialProgram = prog3)) { dut =>
      val trace = scala.collection.mutable.ArrayBuffer[String]()
      for (_ <- 0 until 20) {
        captureRetirementEvent(dut).foreach(trace += _)
        dut.clock.step(1)
      }
      recordPipelineTrace("pipeline_original_prog3", trace)
      trace.length shouldBe 11
    }
  }

  // -------------------------------------------------------------
  // Test 8: Execute Original Benchmark Program 4 (Function Link & Return, Zero NOPs)
  // -------------------------------------------------------------
  it should "execute original Benchmark Program 4 (Function Link & Return) and export trace" in {
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

    test(new PipelinedCore(initialProgram = prog4)) { dut =>
      val trace = scala.collection.mutable.ArrayBuffer[String]()
      for (_ <- 0 until 18) {
        captureRetirementEvent(dut).foreach(trace += _)
        dut.clock.step(1)
      }
      recordPipelineTrace("pipeline_original_prog4", trace)
      trace.length shouldBe 7
    }
  }

  // -------------------------------------------------------------
  // Test 9: Execute Original Benchmark Program 5 (Hardware Multiplier, Zero NOPs)
  // -------------------------------------------------------------
  it should "execute original Benchmark Program 5 (Hardware Multiplier) and export trace" in {
    val prog5 = Seq(
      BigInt("00700093", 16), // 0x00: addi x1, x0, 7
      BigInt("ffb00113", 16), // 0x04: addi x2, x0, -5
      BigInt("022081b3", 16), // 0x08: mul  x3, x1, x2     (7 * -5 = -35 = 0xFFFFFFDD)
      BigInt("00003237", 16), // 0x0C: lui  x4, 3          (12288)
      BigInt("03920213", 16), // 0x10: addi x4, x4, 57     (12345)
      BigInt("7d000293", 16), // 0x14: addi x5, x0, 2000
      BigInt("02520333", 16)  // 0x18: mul  x6, x4, x5     (12345 * 2000 = 24690000 = 0x0178BD50)
    )

    test(new PipelinedCore(initialProgram = prog5)) { dut =>
      val trace = scala.collection.mutable.ArrayBuffer[String]()
      for (_ <- 0 until 15) {
        captureRetirementEvent(dut).foreach(trace += _)
        dut.clock.step(1)
      }
      recordPipelineTrace("pipeline_original_prog5", trace)
      trace.length shouldBe 7
    }
  }

  // -------------------------------------------------------------
  // Test 10: Export 3-Way Hazard-Free Benchmark Traces (Preserved from Phase 3.1)
  // -------------------------------------------------------------
  it should "export pipeline benchmark traces 1, 2, and 3 for 3-way differential verification" in {
    val pipeProg1 = Seq(
      BigInt("00a00093", 16), BigInt("01400113", 16), BigInt("00000013", 16), BigInt("00000013", 16),
      BigInt("00000013", 16), BigInt("002081b3", 16), BigInt("00000013", 16), BigInt("00000013", 16),
      BigInt("00000013", 16), BigInt("40118233", 16), BigInt("00000013", 16), BigInt("00000013", 16),
      BigInt("00000013", 16), BigInt("021202b3", 16)
    )

    test(new PipelinedCore(initialProgram = pipeProg1)) { dut =>
      val trace = scala.collection.mutable.ArrayBuffer[String]()
      for (_ <- 0 until 20) {
        captureRetirementEvent(dut).foreach(trace += _)
        dut.clock.step(1)
      }
      recordPipelineTrace("pipelined_core_prog1", trace)
    }

    val pipeProg2 = Seq(
      BigInt("02a00093", 16), BigInt("ffb00113", 16), BigInt("00000013", 16), BigInt("00000013", 16),
      BigInt("00000013", 16), BigInt("00102023", 16), BigInt("00200223", 16), BigInt("00000013", 16),
      BigInt("00000013", 16), BigInt("00000013", 16), BigInt("00002183", 16), BigInt("00400203", 16)
    )

    test(new PipelinedCore(initialProgram = pipeProg2)) { dut =>
      val trace = scala.collection.mutable.ArrayBuffer[String]()
      for (_ <- 0 until 18) {
        captureRetirementEvent(dut).foreach(trace += _)
        dut.clock.step(1)
      }
      recordPipelineTrace("pipelined_core_prog2", trace)
    }

    val pipeProg3 = Seq(
      BigInt("00a00093", 16), BigInt("00a00113", 16), BigInt("00000013", 16), BigInt("00000013", 16),
      BigInt("00000013", 16), BigInt("00208863", 16), BigInt("3e700713", 16), BigInt("37800713", 16),
      BigInt("00000013", 16), BigInt("04900293", 16), BigInt("00000013", 16), BigInt("00000013", 16),
      BigInt("00000013", 16), BigInt("00028367", 16), BigInt("3e700713", 16), BigInt("37800713", 16),
      BigInt("00000013", 16), BigInt("00000013", 16), BigInt("06400393", 16)
    )

    test(new PipelinedCore(initialProgram = pipeProg3)) { dut =>
      val trace = scala.collection.mutable.ArrayBuffer[String]()
      for (_ <- 0 until 20) {
        captureRetirementEvent(dut).foreach(trace += _)
        dut.clock.step(1)
      }
      recordPipelineTrace("pipelined_core_prog3", trace)
    }
  } // Added missing closing brace

  // -------------------------------------------------------------
  // Test 5: RV32M Divider Multi-cycle Pipeline Integration
  // -------------------------------------------------------------
  it should "execute all eight RV32M instructions including high-multiply variants and divider immediate forwarding" in {
    val progDiv = Seq(
      BigInt("ffe00093", 16), // 0x00: addi x1, x0, -2
      BigInt("00300113", 16), // 0x04: addi x2, x0, 3
      BigInt("022091b3", 16), // 0x08: mulh x3, x1, x2 (x3 = 0xFFFFFFFF)
      BigInt("ffe00093", 16), // 0x0C: addi x1, x0, -2
      BigInt("fff00113", 16), // 0x10: addi x2, x0, -1
      BigInt("0220a233", 16), // 0x14: mulhsu x4, x1, x2 (x4 = 0xFFFFFFFE)
      BigInt("fff00093", 16), // 0x18: addi x1, x0, -1
      BigInt("fff00113", 16), // 0x1C: addi x2, x0, -1
      BigInt("0220b2b3", 16), // 0x20: mulhu x5, x1, x2 (x5 = 0xFFFFFFFE)
      BigInt("00a00093", 16), // 0x24: addi x1, x0, 10
      BigInt("00300113", 16), // 0x28: addi x2, x0, 3
      BigInt("0220c333", 16), // 0x2C: div x6, x1, x2 (x6 = 3)
      BigInt("002303b3", 16), // 0x30: add x7, x6, x2 (x7 = 6) - immediate forwarding
      BigInt("fff00093", 16), // 0x34: addi x1, x0, -1
      BigInt("00300113", 16), // 0x38: addi x2, x0, 3
      BigInt("0220d433", 16), // 0x3C: divu x8, x1, x2 (x8 = 0x55555555)
      BigInt("00a00093", 16), // 0x40: addi x1, x0, 10
      BigInt("00300113", 16), // 0x44: addi x2, x0, 3
      BigInt("0220e4b3", 16), // 0x48: rem x9, x1, x2 (x9 = 1)
      BigInt("fff00093", 16), // 0x4C: addi x1, x0, -1
      BigInt("00300113", 16), // 0x50: addi x2, x0, 3
      BigInt("0220f533", 16), // 0x54: remu x10, x1, x2 (x10 = 0)
      BigInt("022085b3", 16), // 0x58: mul x11, x1, x2 (x11 = -3)
      BigInt("00500093", 16), // 0x5C: addi x1, x0, 5
      BigInt("0200c633", 16), // 0x60: div x12, x1, x0 (x12 = -1)
      BigInt("0200e6b3", 16), // 0x64: rem x13, x1, x0 (x13 = 5)
      BigInt("0200d733", 16), // 0x68: divu x14, x1, x0 (x14 = -1)
      BigInt("0200f7b3", 16), // 0x6C: remu x15, x1, x0 (x15 = 5)
      BigInt("800000b7", 16), // 0x70: lui x1, 0x80000
      BigInt("fff00113", 16), // 0x74: addi x2, x0, -1
      BigInt("0220c833", 16), // 0x78: div x16, x1, x2 (x16 = INT_MIN)
      BigInt("0220e8b3", 16)  // 0x7C: rem x17, x1, x2 (x17 = 0)
    )

    test(new PipelinedCore(initialProgram = progDiv)) { dut =>
      val retiredPcs = scala.collection.mutable.ArrayBuffer[BigInt]()
      val retiredVals = scala.collection.mutable.ArrayBuffer[BigInt]()
      val expectedPcs = Seq(
        0x00, 0x04, 0x08, 0x0C, 0x10, 0x14, 0x18, 0x1C, 0x20, 0x24, 0x28, 0x2C, 0x30, 0x34, 0x38, 0x3C, 0x40, 0x44, 0x48, 0x4C, 0x50, 0x54, 0x58, 0x5C, 0x60, 0x64, 0x68, 0x6C, 0x70, 0x74, 0x78, 0x7C
      ).map(BigInt(_))
        val trace = scala.collection.mutable.ArrayBuffer[String]()
        var cycles = 0
        while (retiredPcs.length < expectedPcs.length && cycles < 800) {
          val ret = captureRetirementEvent(dut)
          if (ret.isDefined) {
            retiredPcs += dut.io.commit.pc.peek().litValue
            retiredVals += dut.io.commit.writeData.peek().litValue
            trace += ret.get
          }
          dut.clock.step(1)
          cycles += 1
        }
        // Capture any final retirement event if present
        val finalRet = captureRetirementEvent(dut)
        if (finalRet.isDefined) {
          retiredPcs += dut.io.commit.pc.peek().litValue
          retiredVals += dut.io.commit.writeData.peek().litValue
          trace += finalRet.get
        }
        recordPipelineTrace("progDiv", trace)
      val expectedVals = Seq(
        BigInt("FFFFFFFFFFFFFFFE", 16) & 0xFFFFFFFFL, // x1 = -2
        BigInt(3), // x2 = 3
        BigInt("FFFFFFFF", 16), // x3 = -1
        BigInt("FFFFFFFFFFFFFFFE", 16) & 0xFFFFFFFFL, // x1 = -2
        BigInt("FFFFFFFFFFFFFFFF", 16) & 0xFFFFFFFFL, // x2 = -1
        BigInt("FFFFFFFE", 16), // x4 = -2
        BigInt("FFFFFFFFFFFFFFFF", 16) & 0xFFFFFFFFL, // x1 = -1
        BigInt("FFFFFFFFFFFFFFFF", 16) & 0xFFFFFFFFL, // x2 = -1
        BigInt("FFFFFFFE", 16), // x5 = -2
        BigInt(10), // x1 = 10
        BigInt(3), // x2 = 3
        BigInt(3), // x6 = 3
        BigInt(6), // x7 = 6
        BigInt("FFFFFFFFFFFFFFFF", 16) & 0xFFFFFFFFL, // x1 = -1
        BigInt(3), // x2 = 3
        BigInt("55555555", 16), // x8
        BigInt(10), // x1 = 10
        BigInt(3), // x2 = 3
        BigInt(1), // x9 = 1
        BigInt("FFFFFFFFFFFFFFFF", 16) & 0xFFFFFFFFL, // x1 = -1
        BigInt(3), // x2 = 3
        BigInt(0), // x10 = 0
        BigInt("FFFFFFFD", 16), // x11 = -3
        BigInt(5), // x1 = 5
        BigInt("FFFFFFFF", 16), // x12 = -1
        BigInt(5), // x13 = 5
        BigInt("FFFFFFFF", 16), // x14 = -1
        BigInt(5), // x15 = 5
        BigInt("80000000", 16), // x1 = 0x80000000
        BigInt("FFFFFFFF", 16), // x2 = -1
        BigInt("80000000", 16), // x16 = 0x80000000
        BigInt(0) // x17 = 0
      )

      retiredPcs shouldBe expectedPcs
      retiredVals shouldBe expectedVals
    }
  }

  // -------------------------------------------------------------
  // Test 14: Phase 6 Cross-Layer System MMIO, Telemetry, and Counters
  // -------------------------------------------------------------
  it should "support software RW to PROCESS_BEHAVIOR_CLASS and SCHED_HINT and expose telemetry and counters via MMIO" in {
    val progMMIO = Seq(
      BigInt("80002537", 16), // 0x00: lui x10, 0x80002
      BigInt("80001a37", 16), // 0x04: lui x20, 0x80001
      BigInt("02a00093", 16), // 0x08: addi x1, x0, 42
      BigInt("00152223", 16), // 0x0C: sw x1, 4(x10) (write PROCESS_BEHAVIOR_CLASS)
      BigInt("00452183", 16), // 0x10: lw x3, 4(x10) (readback 42)
      BigInt("00300113", 16), // 0x14: addi x2, x0, 3
      BigInt("00252423", 16), // 0x18: sw x2, 8(x10) (write SCHED_HINT)
      BigInt("00852203", 16), // 0x1C: lw x4, 8(x10) (readback 3)
      BigInt("00a00293", 16), // 0x20: addi x5, x0, 10
      BigInt("01400313", 16), // 0x24: addi x6, x0, 20
      BigInt("006283b3", 16), // 0x28: add x7, x5, x6 (30)
      BigInt("02628433", 16), // 0x2C: mul x8, x5, x6 (200)
      BigInt("00c00493", 16), // 0x30: addi x9, x0, 12
      BigInt("00802023", 16), // 0x34: sw x8, 0(x0) (RAM write)
      BigInt("00002583", 16), // 0x38: lw x11, 0(x0) (RAM read -> load-use hazard on next inst)
      BigInt("00958633", 16), // 0x3C: add x12, x11, x9 (212)
      BigInt("025346b3", 16), // 0x40: div x13, x6, x5 (2)
      BigInt("00c52703", 16), // 0x44: lw x14, 12(x10) (read RETIRED_COUNT)
      BigInt("01052783", 16), // 0x48: lw x15, 16(x10) (read BRANCH_TAKEN_COUNT)
      BigInt("01452803", 16), // 0x4C: lw x16, 20(x10) (read LOAD_USE_STALL_COUNT)
      BigInt("01852883", 16), // 0x50: lw x17, 24(x10) (read DIV_BUSY_CYCLES)
      BigInt("01c52903", 16), // 0x54: lw x18, 28(x10) (read PIPELINE_STALL_COUNT)
      BigInt("004a2983", 16), // 0x58: lw x19, 4(x20) (read CLA_SWITCHING)
      BigInt("008a2a83", 16), // 0x5C: lw x21, 8(x20) (read MUL_THERMAL)
      BigInt("00ca2b03", 16), // 0x60: lw x22, 12(x20) (read EDP_CURRENT)
      BigInt("010a2b83", 16)  // 0x64: lw x23, 16(x20) (read EDP_CONFIG)
    )

    test(new PipelinedCore(initialProgram = progMMIO)) { dut =>
      val expectedPcs = (0 until progMMIO.length).map(i => BigInt(i * 4))
      val retiredPcs = scala.collection.mutable.ArrayBuffer[BigInt]()
      val retiredVals = scala.collection.mutable.ArrayBuffer[BigInt]()
      val trace = scala.collection.mutable.ArrayBuffer[String]()

      var cycles = 0
      while (retiredPcs.length < expectedPcs.length && cycles < 500) {
        val ret = captureRetirementEvent(dut)
        if (ret.isDefined) {
          retiredPcs += dut.io.commit.pc.peek().litValue
          retiredVals += dut.io.commit.writeData.peek().litValue
          trace += ret.get
        }
        dut.clock.step(1)
        cycles += 1
      }
      val finalRet = captureRetirementEvent(dut)
      if (finalRet.isDefined) {
        retiredPcs += dut.io.commit.pc.peek().litValue
        retiredVals += dut.io.commit.writeData.peek().litValue
        trace += finalRet.get
      }
      recordPipelineTrace("progMMIO", trace)

      retiredPcs shouldBe expectedPcs
      dut.io.processBehaviorClass.peek().litValue shouldBe 42
      dut.io.schedHint.peek().litValue shouldBe 3

      // Direct Chisel-level verification of returned register values
      retiredVals(4)  shouldBe 42 // x3 = readback PROCESS_BEHAVIOR_CLASS
      retiredVals(7)  shouldBe 3  // x4 = readback SCHED_HINT
      retiredVals(17) shouldBe 16 // x14 = RETIRED_COUNT snapshot
      retiredVals(18) shouldBe 0  // x15 = BRANCH_TAKEN_COUNT
      retiredVals(19) shouldBe 1  // x16 = LOAD_USE_STALL_COUNT
      retiredVals(20) shouldBe 33 // x17 = DIV_BUSY_CYCLES
      retiredVals(21) shouldBe 34 // x18 = PIPELINE_STALL_COUNT
      retiredVals(22) shouldBe 49 // x19 = CLA_SWITCHING
      retiredVals(23) shouldBe 5  // x21 = MUL_THERMAL
      retiredVals(24) shouldBe 59 // x22 = EDP_CURRENT
      retiredVals(25) shouldBe 1  // x23 = EDP_CONFIG
    }
  }

  // -------------------------------------------------------------
  // Test 15: Store to Read-Only MMIO Register (Effective vs Request Semantics)
  // -------------------------------------------------------------
  it should "assert memWriteReq but suppress memWrite when software stores to read-only MMIO register" in {
    val progStoreRO = Seq(
      BigInt("80002537", 16), // 0x00: lui x10, 0x80002
      BigInt("3e700293", 16), // 0x04: addi x5, x0, 999
      BigInt("00552623", 16)  // 0x08: sw x5, 12(x10) (attempt store to RETIRED_COUNT @ 0x8000200C)
    )

    test(new PipelinedCore(initialProgram = progStoreRO)) { dut =>
      var storeCommitSeen = false
      for (_ <- 0 until 12) {
        if (dut.io.commit.valid.peek().litToBoolean && dut.io.commit.pc.peek().litValue == 0x08) {
          dut.io.commit.memWriteReq.expect(true.B)
          dut.io.commit.memWrite.expect(false.B) // Store rejected!
          dut.io.commit.illegal.expect(false.B)
          storeCommitSeen = true
        }
        dut.clock.step(1)
      }
      assert(storeCommitSeen, "Store commit event at PC 0x08 must be observed")
    }
  }

  // -------------------------------------------------------------
  // Test 16: Load from Unmapped MMIO Address (Suppression & Non-Fallthrough)
  // -------------------------------------------------------------
  it should "assert memReadReq but suppress memRead and regWrite when loading from unmapped MMIO address" in {
    val progUnmappedMMIO = Seq(
      BigInt("80003537", 16), // 0x00: lui x10, 0x80003
      BigInt("00052303", 16)  // 0x04: lw x6, 0(x10) (unmapped 0x80003000)
    )

    test(new PipelinedCore(initialProgram = progUnmappedMMIO)) { dut =>
      var loadCommitSeen = false
      for (_ <- 0 until 10) {
        if (dut.io.commit.valid.peek().litToBoolean && dut.io.commit.pc.peek().litValue == 0x04) {
          dut.io.commit.memReadReq.expect(true.B)
          dut.io.commit.memRead.expect(false.B)     // Read unaccepted!
          dut.io.commit.regWrite.expect(false.B)    // Destination register writeback suppressed!
          dut.io.commit.illegal.expect(false.B)
          loadCommitSeen = true
        }
        dut.clock.step(1)
      }
      assert(loadCommitSeen, "Load commit event at PC 0x04 must be observed")
    }
  }

  // -------------------------------------------------------------
  // Test 17: Illegal Instructions & OP_SECURITY Placeholder do NOT Mutate Telemetry
  // -------------------------------------------------------------
  it should "guarantee that illegal instructions and OP_SECURITY placeholder do not mutate telemetry" in {
    val progIllegalTelem = Seq(
      BigInt("80001537", 16), // 0x00: lui x10, 0x80001
      BigInt("00a00093", 16), // 0x04: addi x1, x0, 10
      BigInt("00452583", 16), // 0x08: lw x11, 4(x10)
      BigInt("00452683", 16), // 0x0C: lw x13, 4(x10)
      BigInt("00452783", 16), // 0x10: lw x15, 4(x10) (reads baseline CLA_SWITCHING = 7)
      BigInt("00000000", 16), // 0x14: illegal instruction (all 0s)
      BigInt("0000000b", 16), // 0x18: OP_SECURITY placeholder
      BigInt("00452603", 16), // 0x1C: lw x12, 4(x10) (reads CLA_SWITCHING = 7)
      BigInt("00452703", 16)  // 0x20: lw x14, 4(x10) (reads CLA_SWITCHING = 7)
    )

    test(new PipelinedCore(initialProgram = progIllegalTelem)) { dut =>
      val retiredVals = scala.collection.mutable.Map[BigInt, BigInt]()
      for (_ <- 0 until 30) {
        if (dut.io.commit.valid.peek().litToBoolean) {
          val pc = dut.io.commit.pc.peek().litValue
          val wdata = dut.io.commit.writeData.peek().litValue
          retiredVals(pc) = wdata
        }
        dut.clock.step(1)
      }

      val claBaseline = retiredVals(0x10)
      val claAfterIllegalAndSec = retiredVals(0x1C)
      val claFinal = retiredVals(0x20)

      claBaseline shouldBe 7
      claAfterIllegalAndSec shouldBe 7
      claFinal shouldBe 7
    }
  }

  // -------------------------------------------------------------
  // Test 18: Branch Execution Telemetry & Differential Trace Export
  // -------------------------------------------------------------
  it should "correctly accumulate CLA switching activity across branch operations and export trace" in {
    val progBranchMMIO = Seq(
      BigInt("80001537", 16), // 0x00: lui x10, 0x80001
      BigInt("00f00093", 16), // 0x04: addi x1, x0, 15
      BigInt("00f00113", 16), // 0x08: addi x2, x0, 15
      BigInt("00208663", 16), // 0x0C: beq x1, x2, 12 (taken branch: SUB in ALU -> CLA active!)
      BigInt("3e700193", 16), // 0x10: addi x3, x0, 999 (killed)
      BigInt("00000013", 16), // 0x14: nop (killed)
      BigInt("00452203", 16)  // 0x18: lw x4, 4(x10) (read CLA_SWITCHING into x4)
    )

    test(new PipelinedCore(initialProgram = progBranchMMIO)) { dut =>
      val expectedPcs = Seq(BigInt(0x00), BigInt(0x04), BigInt(0x08), BigInt(0x0C), BigInt(0x18))
      val retiredPcs = scala.collection.mutable.ArrayBuffer[BigInt]()
      val retiredVals = scala.collection.mutable.ArrayBuffer[BigInt]()
      val trace = scala.collection.mutable.ArrayBuffer[String]()

      var cycles = 0
      while (retiredPcs.length < expectedPcs.length && cycles < 100) {
        val ret = captureRetirementEvent(dut)
        if (ret.isDefined) {
          retiredPcs += dut.io.commit.pc.peek().litValue
          retiredVals += dut.io.commit.writeData.peek().litValue
          trace += ret.get
        }
        dut.clock.step(1)
        cycles += 1
      }
      val finalRet = captureRetirementEvent(dut)
      if (finalRet.isDefined) {
        retiredPcs += dut.io.commit.pc.peek().litValue
        retiredVals += dut.io.commit.writeData.peek().litValue
        trace += finalRet.get
      }
      recordPipelineTrace("progBranchMMIO", trace)

      retiredPcs shouldBe expectedPcs
      // x4 readback at index 4 should be 8 (4 from addi x1 + 4 from beq SUB)
      retiredVals.last shouldBe 8
    }
  }

  // =============================================================
  // Phase 7: CapabilityLite Architectural Integration Tests (Programs A - F)
  // =============================================================

  // Capability instruction helper encoders:
  def encodeR(funct7: Int, rs2: Int, rs1: Int, funct3: Int, rd: Int, opcode: Int): BigInt = {
    val word = (BigInt(funct7 & 0x7F) << 25) | (BigInt(rs2 & 0x1F) << 20) | (BigInt(rs1 & 0x1F) << 15) | (BigInt(funct3 & 0x7) << 12) | (BigInt(rd & 0x1F) << 7) | BigInt(opcode & 0x7F)
    word & BigInt("FFFFFFFF", 16)
  }
  def encodeI(imm: Int, rs1: Int, funct3: Int, rd: Int, opcode: Int): BigInt = {
    val imm12 = imm & 0xFFF
    val word = (BigInt(imm12) << 20) | (BigInt(rs1 & 0x1F) << 15) | (BigInt(funct3 & 0x7) << 12) | (BigInt(rd & 0x1F) << 7) | BigInt(opcode & 0x7F)
    word & BigInt("FFFFFFFF", 16)
  }
  def encodeS(imm: Int, rs2: Int, rs1: Int, funct3: Int, opcode: Int): BigInt = {
    val imm12 = imm & 0xFFF
    val imm11_5 = (imm12 >> 5) & 0x7F
    val imm4_0 = imm12 & 0x1F
    val word = (BigInt(imm11_5) << 25) | (BigInt(rs2 & 0x1F) << 20) | (BigInt(rs1 & 0x1F) << 15) | (BigInt(funct3 & 0x7) << 12) | (BigInt(imm4_0) << 7) | BigInt(opcode & 0x7F)
    word & BigInt("FFFFFFFF", 16)
  }
  def encodeU(imm20: Int, rd: Int, opcode: Int): BigInt = {
    val word = (BigInt(imm20 & 0xFFFFF) << 12) | (BigInt(rd & 0x1F) << 7) | BigInt(opcode & 0x7F)
    word & BigInt("FFFFFFFF", 16)
  }

  // Capability manipulation: OP_CAP = 0x0B
  def csetbounds(cd: Int, cs1: Int, rs2: Int): BigInt = encodeR(0x00, rs2, cs1, 0x0, cd, 0x0B)
  def candperm(cd: Int, cs1: Int, rs2: Int): BigInt   = encodeR(0x00, rs2, cs1, 0x1, cd, 0x0B)
  def cincoffset(cd: Int, cs1: Int, rs2: Int): BigInt = encodeR(0x00, rs2, cs1, 0x2, cd, 0x0B)
  def cgetbase(rd: Int, cs1: Int): BigInt             = encodeR(0x00, 0, cs1, 0x3, rd, 0x0B)
  def cgetlen(rd: Int, cs1: Int): BigInt              = encodeR(0x00, 0, cs1, 0x4, rd, 0x0B)
  def cgettag(rd: Int, cs1: Int): BigInt              = encodeR(0x00, 0, cs1, 0x5, rd, 0x0B)
  def cgetperm(rd: Int, cs1: Int): BigInt             = encodeR(0x00, 0, cs1, 0x6, rd, 0x0B)
  def cgetoffset(rd: Int, cs1: Int): BigInt           = encodeR(0x00, 0, cs1, 0x7, rd, 0x0B)
  def cclear(cd: Int): BigInt                         = encodeR(0x01, 0, 0, 0x7, cd, 0x0B)

  // Capability memory: OP_CAP_MEM = 0x2B
  def clb(rd: Int, cs1: Int, offset: Int): BigInt = encodeI(offset, cs1, 0x0, rd, 0x2B)
  def clh(rd: Int, cs1: Int, offset: Int): BigInt = encodeI(offset, cs1, 0x1, rd, 0x2B)
  def clw(rd: Int, cs1: Int, offset: Int): BigInt = encodeI(offset, cs1, 0x2, rd, 0x2B)
  def csb(rs2: Int, cs1: Int, offset: Int): BigInt = encodeS(offset, rs2, cs1, 0x4, 0x2B)
  def csh(rs2: Int, cs1: Int, offset: Int): BigInt = encodeS(offset, rs2, cs1, 0x5, 0x2B)
  def csw(rs2: Int, cs1: Int, offset: Int): BigInt = encodeS(offset, rs2, cs1, 0x6, 0x2B)

  // Standard RV32I helpers
  def addi(rd: Int, rs1: Int, imm: Int): BigInt = encodeI(imm, rs1, 0x0, rd, 0x13)
  def lui(rd: Int, imm20: Int): BigInt          = encodeU(imm20, rd, 0x37)
  def lw(rd: Int, rs1: Int, offset: Int): BigInt = encodeI(offset, rs1, 0x2, rd, 0x03)
  def sw(rs2: Int, rs1: Int, offset: Int): BigInt = encodeS(offset, rs2, rs1, 0x2, 0x23)
  def div(rd: Int, rs1: Int, rs2: Int): BigInt   = encodeR(0x01, rs2, rs1, 0x4, rd, 0x33)
  def beq(rs1: Int, rs2: Int, imm: Int): BigInt = {
    val imm13 = imm & 0x1FFF
    val bit12 = (imm13 >> 12) & 1
    val bit10_5 = (imm13 >> 5) & 0x3F
    val bit4_1 = (imm13 >> 1) & 0xF
    val bit11 = (imm13 >> 11) & 1
    val immFormatted = (bit12 << 11) | (bit11 << 10) | (bit10_5 << 4) | bit4_1
    val imm11_5 = (immFormatted >> 5) & 0x7F
    val imm4_0 = immFormatted & 0x1F
    val word = (BigInt(imm11_5) << 25) | (BigInt(rs2 & 0x1F) << 20) | (BigInt(rs1 & 0x1F) << 15) | (BigInt(0) << 12) | (BigInt(imm4_0) << 7) | BigInt(0x63)
    word & BigInt("FFFFFFFF", 16)
  }
  def jal(rd: Int, imm: Int): BigInt = {
    val imm21 = imm & 0x1FFFFF
    val bit20 = (imm21 >> 20) & 1
    val bit10_1 = (imm21 >> 1) & 0x3FF
    val bit11 = (imm21 >> 11) & 1
    val bit19_12 = (imm21 >> 12) & 0xFF
    val immFormatted = (bit20 << 19) | (bit19_12 << 11) | (bit11 << 10) | bit10_1
    val word = (BigInt(immFormatted) << 12) | (BigInt(rd & 0x1F) << 7) | BigInt(0x6F)
    word & BigInt("FFFFFFFF", 16)
  }

  // -------------------------------------------------------------
  // Test 19: Program A - Buffer Overflow Containment & Bounds Enforcement
  // -------------------------------------------------------------
  it should "execute Program A: allow in-bounds CSW, deny out-of-bounds CSW, and record exact violation metadata" in {
    val progA = Seq(
      addi(5, 0, 0x100),       // 0x00: addi x5, x0, 0x100 (cursor offset = 0x100)
      cincoffset(3, 1, 5),     // 0x04: cincoffset c3, c1, x5 (c3.offset = 0x100)
      addi(6, 0, 16),          // 0x08: addi x6, x0, 16 (length = 16)
      csetbounds(3, 3, 6),     // 0x0C: csetbounds c3, c3, x6 (c3 = base:0x100, len:16, perms:RW, offset:0)
      addi(7, 0, 0x11),        // 0x10: addi x7, x0, 0x11
      csw(7, 3, 0),            // 0x14: csw x7, 0(c3) (store 0x11 to [0x100..0x103] -> allowed!)
      addi(8, 0, 0x22),        // 0x18: addi x8, x0, 0x22
      csw(8, 3, 12),           // 0x1C: csw x8, 12(c3) (store 0x22 to [0x10C..0x10F] -> allowed!)
      addi(9, 0, 0x33),        // 0x20: addi x9, x0, 0x33
      csw(9, 3, 13),           // 0x24: csw x9, 13(c3) (store to [0x10D..0x110] -> DENIED: BOUNDS!)
      clw(11, 3, 0),           // 0x28: clw x11, 0(c3) (readback offset 0 -> 0x11)
      clw(12, 3, 12),          // 0x2C: clw x12, 12(c3) (readback offset 12 -> 0x22)
      lui(10, 0x80002),        // 0x30: lui x10, 0x80002
      lw(13, 10, 0x100),       // 0x34: lw x13, 0x100(x10) (read SEC_STATUS @ 0x80002100)
      lw(14, 10, 0x10C),       // 0x38: lw x14, 0x10C(x10) (read SEC_INFO @ 0x8000210C)
      lw(15, 10, 0x108),       // 0x3C: lw x15, 0x108(x10) (read SEC_ADDR @ 0x80002108)
      lw(16, 10, 0x104)        // 0x40: lw x16, 0x104(x10) (read SEC_PC @ 0x80002104)
    )

    test(new PipelinedCore(initialProgram = progA)) { dut =>
      val expectedPcs = (0 until progA.length).map(i => BigInt(i * 4))
      val retiredPcs = scala.collection.mutable.ArrayBuffer[BigInt]()
      val retiredVals = scala.collection.mutable.ArrayBuffer[BigInt]()
      val trace = scala.collection.mutable.ArrayBuffer[String]()

      var cycles = 0
      while (retiredPcs.length < expectedPcs.length && cycles < 200) {
        val ret = captureRetirementEvent(dut)
        if (ret.isDefined) {
          retiredPcs += dut.io.commit.pc.peek().litValue
          retiredVals += dut.io.commit.writeData.peek().litValue
          trace += ret.get
        }
        dut.clock.step(1)
        cycles += 1
      }
      val finalRet = captureRetirementEvent(dut)
      if (finalRet.isDefined) {
        retiredPcs += dut.io.commit.pc.peek().litValue
        retiredVals += dut.io.commit.writeData.peek().litValue
        trace += finalRet.get
      }
      recordPipelineTrace("progA_capability_bounds", trace)

      retiredPcs shouldBe expectedPcs
      // In-bounds load results
      retiredVals(10) shouldBe 0x11 // x11 = 0x11
      retiredVals(11) shouldBe 0x22 // x12 = 0x22
      // Security Logger registers:
      retiredVals(13) shouldBe 1              // x13 = SEC_STATUS (pending = 1)
      retiredVals(14) shouldBe ((1 << 4) | 2) // x14 = SEC_INFO (accessType=WRITE=1, reason=BOUNDS=2)
      retiredVals(15) shouldBe 0x10D          // x15 = SEC_ADDR (0x10D)
      retiredVals(16) shouldBe 0x24           // x16 = SEC_PC (0x24 offending instruction PC)
    }
  }

  // -------------------------------------------------------------
  // Test 20: Program B - Permission Attenuation & Monotonicity
  // -------------------------------------------------------------
  it should "execute Program B: enforce read-only permission attenuation and prohibit privilege escalation" in {
    val progB = Seq(
      addi(5, 0, 1),           // 0x00: addi x5, x0, 1 (READ permission mask = 1)
      candperm(3, 1, 5),       // 0x04: candperm c3, c1, x5 (c3 becomes Read-Only)
      clw(6, 3, 0),            // 0x08: clw x6, 0(c3) (load allowed)
      addi(7, 0, 99),          // 0x0C: addi x7, x0, 99
      csw(7, 3, 0),            // 0x10: csw x7, 0(c3) (store DENIED: WRITE_PERMISSION = 4!)
      addi(8, 0, 3),           // 0x14: addi x8, x0, 3 (try to escalate to READ|WRITE)
      candperm(3, 3, 8),       // 0x18: candperm c3, c3, x8 (monotonic AND: 1 & 3 = 1 -> still RO!)
      cgetperm(9, 3),          // 0x1C: cgetperm x9, c3 (x9 should be 1)
      lui(10, 0x80002),        // 0x20: lui x10, 0x80002
      lw(13, 10, 0x100),       // 0x24: lw x13, 0x100(x10) (read SEC_STATUS @ 0x80002100)
      lw(14, 10, 0x10C),       // 0x28: lw x14, 0x10C(x10) (read SEC_INFO @ 0x8000210C)
      lw(15, 10, 0x108),       // 0x2C: lw x15, 0x108(x10) (read SEC_ADDR @ 0x80002108)
      lw(16, 10, 0x104)        // 0x30: lw x16, 0x104(x10) (read SEC_PC @ 0x80002104)
    )

    test(new PipelinedCore(initialProgram = progB)) { dut =>
      val expectedPcs = (0 until progB.length).map(i => BigInt(i * 4))
      val retiredPcs = scala.collection.mutable.ArrayBuffer[BigInt]()
      val retiredVals = scala.collection.mutable.ArrayBuffer[BigInt]()
      val trace = scala.collection.mutable.ArrayBuffer[String]()

      var cycles = 0
      while (retiredPcs.length < expectedPcs.length && cycles < 200) {
        val ret = captureRetirementEvent(dut)
        if (ret.isDefined) {
          retiredPcs += dut.io.commit.pc.peek().litValue
          retiredVals += dut.io.commit.writeData.peek().litValue
          trace += ret.get
        }
        dut.clock.step(1)
        cycles += 1
      }
      val finalRet = captureRetirementEvent(dut)
      if (finalRet.isDefined) {
        retiredPcs += dut.io.commit.pc.peek().litValue
        retiredVals += dut.io.commit.writeData.peek().litValue
        trace += finalRet.get
      }
      recordPipelineTrace("progB_capability_perms", trace)

      retiredPcs shouldBe expectedPcs
      retiredVals(7)  shouldBe 1              // x9 = cgetperm (perms = 1)
      retiredVals(9)  shouldBe 1              // x13 = SEC_STATUS (pending = 1)
      retiredVals(10) shouldBe ((1 << 4) | 4) // x14 = SEC_INFO (accessType=WRITE=1, reason=WRITE_PERMISSION=4)
      retiredVals(11) shouldBe 0x0            // x15 = SEC_ADDR (0x0)
      retiredVals(12) shouldBe 0x10           // x16 = SEC_PC (0x10 offending instruction PC)
    }
  }

  // -------------------------------------------------------------
  // Test 21: Program C - Invalid / NULL Capability Dereference
  // -------------------------------------------------------------
  it should "execute Program C: deny memory operations through NULL / tag=0 capability" in {
    val progC = Seq(
      addi(5, 0, 77),          // 0x00: addi x5, x0, 77
      csw(5, 0, 0),            // 0x04: csw x5, 0(c0) (store via c0 [NULL] -> DENIED: INVALID_CAPABILITY = 1)
      clw(6, 4, 0),            // 0x08: clw x6, 0(c4) (load via c4 [NULL, tag=0] -> DENIED: INVALID_CAPABILITY)
      lui(10, 0x80002),        // 0x0C: lui x10, 0x80002
      lw(13, 10, 0x100),       // 0x10: lw x13, 0x100(x10) (read SEC_STATUS @ 0x80002100)
      lw(14, 10, 0x10C),       // 0x14: lw x14, 0x10C(x10) (read SEC_INFO @ 0x8000210C)
      lw(15, 10, 0x108),       // 0x18: lw x15, 0x108(x10) (read SEC_ADDR @ 0x80002108)
      lw(16, 10, 0x104)        // 0x1C: lw x16, 0x104(x10) (read SEC_PC @ 0x80002104)
    )

    test(new PipelinedCore(initialProgram = progC)) { dut =>
      val expectedPcs = (0 until progC.length).map(i => BigInt(i * 4))
      val retiredPcs = scala.collection.mutable.ArrayBuffer[BigInt]()
      val retiredVals = scala.collection.mutable.ArrayBuffer[BigInt]()
      val trace = scala.collection.mutable.ArrayBuffer[String]()

      var cycles = 0
      while (retiredPcs.length < expectedPcs.length && cycles < 200) {
        val ret = captureRetirementEvent(dut)
        if (ret.isDefined) {
          retiredPcs += dut.io.commit.pc.peek().litValue
          retiredVals += dut.io.commit.writeData.peek().litValue
          trace += ret.get
        }
        dut.clock.step(1)
        cycles += 1
      }
      val finalRet = captureRetirementEvent(dut)
      if (finalRet.isDefined) {
        retiredPcs += dut.io.commit.pc.peek().litValue
        retiredVals += dut.io.commit.writeData.peek().litValue
        trace += finalRet.get
      }
      recordPipelineTrace("progC_capability_null", trace)

      retiredPcs shouldBe expectedPcs
      retiredVals(4) shouldBe 1              // x13 = SEC_STATUS (pending = 1)
      retiredVals(5) shouldBe ((1 << 4) | 1) // x14 = SEC_INFO (accessType=WRITE=1, reason=INVALID_CAPABILITY=1)
      retiredVals(6) shouldBe 0x0            // x15 = SEC_ADDR (0x0)
      retiredVals(7) shouldBe 0x04           // x16 = SEC_PC (0x04 offending instruction PC)
    }
  }

  // -------------------------------------------------------------
  // Test 22: Program D - Capability RAW Hazards and Zero-NOP Interlock
  // -------------------------------------------------------------
  it should "execute Program D: resolve back-to-back capability derivation RAW hazards with zero NOPs" in {
    val progD = Seq(
      addi(5, 0, 64),          // 0x00: addi x5, x0, 64
      csetbounds(3, 1, 5),     // 0x04: csetbounds c3, c1, x5 (c3 derived)
      addi(6, 0, 123),         // 0x08: addi x6, x0, 123 (0 NOPs!)
      csw(6, 3, 0),            // 0x0C: csw x6, 0(c3) (uses c3 -> pipeline stalls and forwards c3)
      clw(7, 3, 0)             // 0x10: clw x7, 0(c3) (load back into x7)
    )

    test(new PipelinedCore(initialProgram = progD)) { dut =>
      val expectedPcs = (0 until progD.length).map(i => BigInt(i * 4))
      val retiredPcs = scala.collection.mutable.ArrayBuffer[BigInt]()
      val retiredVals = scala.collection.mutable.ArrayBuffer[BigInt]()
      val trace = scala.collection.mutable.ArrayBuffer[String]()

      var cycles = 0
      while (retiredPcs.length < expectedPcs.length && cycles < 200) {
        val ret = captureRetirementEvent(dut)
        if (ret.isDefined) {
          retiredPcs += dut.io.commit.pc.peek().litValue
          retiredVals += dut.io.commit.writeData.peek().litValue
          trace += ret.get
        }
        dut.clock.step(1)
        cycles += 1
      }
      val finalRet = captureRetirementEvent(dut)
      if (finalRet.isDefined) {
        retiredPcs += dut.io.commit.pc.peek().litValue
        retiredVals += dut.io.commit.writeData.peek().litValue
        trace += finalRet.get
      }
      recordPipelineTrace("progD_capability_raw", trace)

      retiredPcs shouldBe expectedPcs
      retiredVals(4) shouldBe 123 // x7 loaded 123 from c3 with zero NOP stalls
    }
  }

  // -------------------------------------------------------------
  // Test 23: Program E - Protected MMIO Authorization & Layer Separation
  // -------------------------------------------------------------
  it should "execute Program E: permit valid capability MMIO and isolate device-level write protection" in {
    val progE = Seq(
      lui(9, 2),               // 0x00: lui x9, 2 (x9 = 0x2000)
      cincoffset(3, 2, 9),     // 0x04: cincoffset c3, c2, x9 (c3 cursor = 0x80002000)
      addi(5, 0, 42),          // 0x08: addi x5, x0, 42
      csw(5, 3, 4),            // 0x0C: csw x5, 4(c3) (write 42 to PROCESS_BEHAVIOR_CLASS @ 0x80002004 -> succeeds!)
      clw(6, 3, 4),            // 0x10: clw x6, 4(c3) (read back -> 42)
      addi(7, 0, 999),         // 0x14: addi x7, x0, 999
      csw(7, 3, 0x0C),         // 0x18: csw x7, 0x0C(c3) (store to RETIRED_COUNT: cap allows, MMIO suppresses write!)
      lui(10, 0x80002),        // 0x1C: lui x10, 0x80002
      lw(8, 10, 0x100)         // 0x20: lw x8, 0x100(x10) (read SEC_STATUS @ 0x80002100 -> 0, no capability violation!)
    )

    test(new PipelinedCore(initialProgram = progE)) { dut =>
      val expectedPcs = (0 until progE.length).map(i => BigInt(i * 4))
      val retiredPcs = scala.collection.mutable.ArrayBuffer[BigInt]()
      val retiredVals = scala.collection.mutable.ArrayBuffer[BigInt]()
      val trace = scala.collection.mutable.ArrayBuffer[String]()

      var cycles = 0
      while (retiredPcs.length < expectedPcs.length && cycles < 200) {
        val ret = captureRetirementEvent(dut)
        if (ret.isDefined) {
          retiredPcs += dut.io.commit.pc.peek().litValue
          retiredVals += dut.io.commit.writeData.peek().litValue
          trace += ret.get
        }
        dut.clock.step(1)
        cycles += 1
      }
      val finalRet = captureRetirementEvent(dut)
      if (finalRet.isDefined) {
        retiredPcs += dut.io.commit.pc.peek().litValue
        retiredVals += dut.io.commit.writeData.peek().litValue
        trace += finalRet.get
      }
      recordPipelineTrace("progE_capability_mmio", trace)

      retiredPcs shouldBe expectedPcs
      retiredVals(4) shouldBe 42 // x6 = 42
      retiredVals(8) shouldBe 0  // x8 = SEC_STATUS (0 = no capability violation)
      dut.io.processBehaviorClass.peek().litValue shouldBe 42
    }
  }

  // -------------------------------------------------------------
  // Test 24: Program F - Combined GPR and Capability Dependency Interaction
  // -------------------------------------------------------------
  it should "execute Program F: correctly handle mixed GPR forwarding and capability derivation dependencies" in {
    val progF = Seq(
      addi(5, 0, 16),          // 0x00: addi x5, x0, 16
      csetbounds(3, 1, 5),     // 0x04: csetbounds c3, c1, x5 (forwarded x5 into csetbounds)
      addi(6, 0, 99),          // 0x08: addi x6, x0, 99
      csw(6, 3, 0),            // 0x0C: csw x6, 0(c3) (forwarded c3 and forwarded x6)
      clw(7, 3, 0)             // 0x10: clw x7, 0(c3) (load back into x7)
    )

    test(new PipelinedCore(initialProgram = progF)) { dut =>
      val expectedPcs = (0 until progF.length).map(i => BigInt(i * 4))
      val retiredPcs = scala.collection.mutable.ArrayBuffer[BigInt]()
      val retiredVals = scala.collection.mutable.ArrayBuffer[BigInt]()
      val trace = scala.collection.mutable.ArrayBuffer[String]()

      var cycles = 0
      while (retiredPcs.length < expectedPcs.length && cycles < 200) {
        val ret = captureRetirementEvent(dut)
        if (ret.isDefined) {
          retiredPcs += dut.io.commit.pc.peek().litValue
          retiredVals += dut.io.commit.writeData.peek().litValue
          trace += ret.get
        }
        dut.clock.step(1)
        cycles += 1
      }
      val finalRet = captureRetirementEvent(dut)
      if (finalRet.isDefined) {
        retiredPcs += dut.io.commit.pc.peek().litValue
        retiredVals += dut.io.commit.writeData.peek().litValue
        trace += finalRet.get
      }
      recordPipelineTrace("progF_capability_gpr_forwarding", trace)

      retiredPcs shouldBe expectedPcs
      retiredVals(4) shouldBe 99 // x7 = 99
    }
  }

  // -------------------------------------------------------------
  // Test 25: Capability Memory Telemetry Invariance
  // -------------------------------------------------------------
  it should "guarantee that allowed and denied CLW/CSW operations do not alter Objective-1 CLA switching telemetry" in {
    // Hardware root capabilities:
    // c1 = DataMemory Root (base = 0x00000000, len = 0x1000, RW)
    // c2 = SystemMMIO Root (base = 0x80000000, len = 0x10000, RW)
    // c0 = NULL
    // CLA_SWITCHING counter is at 0x80001004 (c2 base + offset 0x1004)
    val progTelem = Seq(
      addi(5, 0, 100),      // 0x00: addi x5, x0, 100 (prepare payload before baseline)
      clw(11, 2, 0x1004),   // 0x04: clw x11, 0x1004(c2) -> snapshot baseline CLA counter via protected CLW
      csw(5, 1, 0),         // 0x08: csw x5, 0(c1) (allowed protected store to RAM)
      clw(6, 1, 0),         // 0x0C: clw x6, 0(c1) (allowed protected load from RAM)
      csw(5, 0, 0),         // 0x10: csw x5, 0(c0) (denied protected store through NULL)
      clw(7, 0, 0),         // 0x14: clw x7, 0(c0) (denied protected load through NULL)
      clw(12, 2, 0x1004)    // 0x18: clw x12, 0x1004(c2) -> snapshot final CLA counter via protected CLW
    )

    test(new PipelinedCore(initialProgram = progTelem)) { dut =>
      var retiredCount = 0
      val retiredVals = scala.collection.mutable.ArrayBuffer[BigInt]()
      for (_ <- 0 until 50) {
        if (dut.io.commit.valid.peek().litToBoolean) {
          retiredCount += 1
          retiredVals += dut.io.commit.writeData.peek().litValue
        }
        dut.clock.step(1)
      }
      retiredCount shouldBe progTelem.length
      val baselineCla = retiredVals(1) // x11 = baseline CLA_SWITCHING read via protected CLW
      val finalCla    = retiredVals(6) // x12 = final CLA_SWITCHING read via protected CLW
      retiredVals(3) shouldBe 100      // x6 (allowed clw readback)
      retiredVals(5) shouldBe 0        // x7 (denied clw suppressed)
      finalCla shouldBe baselineCla    // Strict telemetry invariance across all 4 capability memory ops!
    }
  }

  // -------------------------------------------------------------
  // Test 26: CINCOFFSET Signed Delta and Cursor Boundary Verification
  // -------------------------------------------------------------
  it should "execute CINCOFFSET with positive, negative, boundary, and overflow signed cursor movements" in {
    // c1 = DataMemory Root (base = 0x00000000, len = 4096 = 0x1000, RW)
    val progCinc = Seq(
      addi(5, 0, 8),          // 0x00: x5 = +8
      cincoffset(3, 1, 5),    // 0x04: c3 = offset 8 (forward)
      addi(6, 0, -4),         // 0x08: x6 = -4
      cincoffset(4, 3, 6),    // 0x0C: c4 = offset 4 (backward from c3)
      addi(7, 0, -10),        // 0x10: x7 = -10
      cincoffset(5, 4, 7),    // 0x14: c5 = offset 4 + (-10) = -6 -> BOUNDS underflow violation!
      cgetbase(8, 4),         // 0x18: x8 = c4.base (0)
      cgettag(9, 4),          // 0x1C: x9 = c4.tag (1)
      cgettag(10, 5),         // 0x20: x10 = c5.tag (0 - not updated on underflow)
      addi(14, 0, 4096),      // 0x24: x14 = +4096
      cincoffset(6, 1, 14),   // 0x28: c6 = c1 with offset 4096 (exact one-past-the-end: offset == length -> SUCCESS)
      cgettag(15, 6),         // 0x2C: x15 = c6.tag (1)
      cgetlen(16, 6),         // 0x30: x16 = c6.length (4096)
      addi(17, 0, 1),         // 0x34: x17 = +1
      cincoffset(7, 6, 17),   // 0x38: c7 = offset 4096 + 1 = 4097 > length -> BOUNDS overflow violation!
      cgettag(18, 7)          // 0x3C: x18 = c7.tag (0 - not updated on overflow)
    )

    test(new PipelinedCore(initialProgram = progCinc)) { dut =>
      val retiredVals = scala.collection.mutable.ArrayBuffer[BigInt]()
      for (_ <- 0 until 80) {
        if (dut.io.commit.valid.peek().litToBoolean) {
          retiredVals += dut.io.commit.writeData.peek().litValue
        }
        dut.clock.step(1)
      }
      retiredVals(6)  shouldBe 0    // x8 = c4.base
      retiredVals(7)  shouldBe 1    // x9 = c4.tag (forward/backward success)
      retiredVals(8)  shouldBe 0    // x10 = c5.tag (underflow failure, c5 remains NULL)
      retiredVals(11) shouldBe 1    // x15 = c6.tag (offset == length success)
      retiredVals(12) shouldBe 4096 // x16 = c6.length
      retiredVals(14) shouldBe 0    // x18 = c7.tag (offset > length overflow failure, c7 remains NULL)
    }
  }

  // =============================================================
  // Phase 8: Precise Security Traps, Handler Redirection & Context Switching (Tests 27 - 37)
  // =============================================================

  // -------------------------------------------------------------
  // Test 27: Program A - Precise OOB Store Trap & Redirection
  // -------------------------------------------------------------
  it should "execute Phase 8 Program A: trigger precise trap on out-of-bounds CSW, redirect to TRAP_VECTOR, and capture exact trap metadata" in {
    // Layout: Main code @ 0x00..0x30, Handler code @ 0x80 (word 32)
    val mainCode = Seq(
      lui(10, 0x80002),        // 0x00: lui x10, 0x80002 (MMIO base)
      addi(5, 0, 0x80),        // 0x04: addi x5, x0, 0x80
      sw(5, 10, 0x11C),        // 0x08: sw x5, 0x11C(x10) (TRAP_VECTOR := 0x80)
      addi(5, 0, 1),           // 0x0C: addi x5, x0, 1
      sw(5, 10, 0x114),        // 0x10: sw x5, 0x114(x10) (TRAP_CONTROL := 1 [enable])
      addi(5, 0, 0x200),       // 0x14: addi x5, x0, 0x200
      cincoffset(3, 1, 5),     // 0x18: cincoffset c3, c1, x5 (c3 cursor = 0x200)
      addi(6, 0, 16),          // 0x1C: addi x6, x0, 16
      csetbounds(3, 3, 6),     // 0x20: csetbounds c3, c3, x6 (c3: base 0x200, len 16, RW)
      addi(7, 0, 0x77),        // 0x24: addi x7, x0, 0x77
      csw(7, 3, 20),           // 0x28: csw x7, 20(c3) (DENIED: BOUNDS! PC=0x28 -> FAULT!)
      addi(14, 0, 999)         // 0x2C: addi x14, x0, 999 (killed by trap flush)
    )
    val nops1 = Seq.fill(32 - mainCode.length)(addi(0, 0, 0))
    val handlerCode = Seq(
      lw(11, 10, 0x118),       // 0x80: lw x11, 0x118(x10) (read TRAP_STATUS -> 1 [ACTIVE])
      lw(12, 10, 0x120),       // 0x84: lw x12, 0x120(x10) (read TRAP_EPC -> 0x28)
      lw(13, 10, 0x124),       // 0x88: lw x13, 0x124(x10) (read TRAP_CAUSE -> (1 << 4) | 2 = 0x12)
      lw(15, 10, 0x128)        // 0x8C: lw x15, 0x128(x10) (read TRAP_ADDR -> 0x214)
    )
    val progP8A = mainCode ++ nops1 ++ handlerCode

    test(new PipelinedCore(initialProgram = progP8A)) { dut =>
      val retiredVals = scala.collection.mutable.ArrayBuffer[BigInt]()
      val trace = scala.collection.mutable.ArrayBuffer[String]()
      for (_ <- 0 until 60) {
        val ret = captureRetirementEvent(dut)
        if (ret.isDefined) {
          retiredVals += dut.io.commit.writeData.peek().litValue
          trace += ret.get
        }
        dut.clock.step(1)
      }
      recordPipelineTrace("phase8_progA_precise_trap", trace)

      dut.io.trap.trapActive.peek().litToBoolean shouldBe true
      dut.io.trap.trapEpc.peek().litValue shouldBe 0x28
      dut.io.trap.trapCause.peek().litValue shouldBe ((1 << 4) | 2)
      dut.io.trap.trapAddr.peek().litValue shouldBe 0x214
    }
  }

  // -------------------------------------------------------------
  // Test 28: Program B - Read-Only Permission Trap
  // -------------------------------------------------------------
  it should "execute Phase 8 Program B: trigger precise trap on permission violation and record WRITE_PERMISSION cause" in {
    val mainCode = Seq(
      lui(10, 0x80002),        // 0x00: lui x10, 0x80002
      addi(5, 0, 0x80),        // 0x04: addi x5, x0, 0x80
      sw(5, 10, 0x11C),        // 0x08: sw x5, 0x11C(x10) (TRAP_VECTOR := 0x80)
      addi(5, 0, 1),           // 0x0C: addi x5, x0, 1
      sw(5, 10, 0x114),        // 0x10: sw x5, 0x114(x10) (TRAP_CONTROL := 1)
      addi(5, 0, 1),           // 0x14: addi x5, x0, 1 (READ permission mask)
      candperm(3, 1, 5),       // 0x18: candperm c3, c1, x5 (c3 becomes Read-Only)
      addi(7, 0, 99),          // 0x1C: addi x7, x0, 99
      csw(7, 3, 0),            // 0x20: csw x7, 0(c3) (DENIED: WRITE_PERMISSION! PC=0x20 -> FAULT!)
      addi(14, 0, 999)         // 0x24: addi x14, x0, 999 (killed)
    )
    val nops = Seq.fill(32 - mainCode.length)(addi(0, 0, 0))
    val handlerCode = Seq(
      lw(11, 10, 0x118),       // 0x80: read TRAP_STATUS
      lw(12, 10, 0x120),       // 0x84: read TRAP_EPC
      lw(13, 10, 0x124)        // 0x88: read TRAP_CAUSE
    )
    val progP8B = mainCode ++ nops ++ handlerCode

    test(new PipelinedCore(initialProgram = progP8B)) { dut =>
      for (_ <- 0 until 50) dut.clock.step(1)
      dut.io.trap.trapActive.peek().litToBoolean shouldBe true
      dut.io.trap.trapEpc.peek().litValue shouldBe 0x20
      dut.io.trap.trapCause.peek().litValue shouldBe ((1 << 4) | 4) // WRITE | WRITE_PERMISSION (4)
    }
  }

  // -------------------------------------------------------------
  // Test 29: Program C - NULL Capability Trap
  // -------------------------------------------------------------
  it should "execute Phase 8 Program C: trigger precise trap on NULL capability access" in {
    val mainCode = Seq(
      lui(10, 0x80002),        // 0x00: lui x10, 0x80002
      addi(5, 0, 0x80),        // 0x04: addi x5, x0, 0x80
      sw(5, 10, 0x11C),        // 0x08: sw x5, 0x11C(x10) (TRAP_VECTOR := 0x80)
      addi(5, 0, 1),           // 0x0C: addi x5, x0, 1
      sw(5, 10, 0x114),        // 0x10: sw x5, 0x114(x10) (TRAP_CONTROL := 1)
      addi(7, 0, 0x55),        // 0x14: addi x7, x0, 0x55
      csw(7, 0, 0),            // 0x18: csw x7, 0(c0) (DENIED: INVALID_CAPABILITY! PC=0x18 -> FAULT!)
      addi(14, 0, 999)         // 0x1C: killed
    )
    val nops = Seq.fill(32 - mainCode.length)(addi(0, 0, 0))
    val handlerCode = Seq(
      lw(11, 10, 0x118),       // 0x80: read TRAP_STATUS
      lw(12, 10, 0x120),       // 0x84: read TRAP_EPC
      lw(13, 10, 0x124)        // 0x88: read TRAP_CAUSE
    )
    val progP8C = mainCode ++ nops ++ handlerCode

    test(new PipelinedCore(initialProgram = progP8C)) { dut =>
      for (_ <- 0 until 50) dut.clock.step(1)
      dut.io.trap.trapActive.peek().litToBoolean shouldBe true
      dut.io.trap.trapEpc.peek().litValue shouldBe 0x18
      dut.io.trap.trapCause.peek().litValue shouldBe ((1 << 4) | 1) // WRITE | INVALID_CAPABILITY (1)
    }
  }

  // -------------------------------------------------------------
  // Test 30: Program D - Precise Age-Ordered Pipeline Invariance
  // -------------------------------------------------------------
  it should "execute Phase 8 Program D: commit older instruction, suppress faulting MEM instruction, and flush younger ID/EX stages" in {
    val mainCode = Seq(
      lui(10, 0x80002),        // 0x00: lui x10, 0x80002
      addi(5, 0, 0x80),        // 0x04: addi x5, x0, 0x80
      sw(5, 10, 0x11C),        // 0x08: sw x5, 0x11C(x10) (TRAP_VECTOR := 0x80)
      addi(5, 0, 1),           // 0x0C: addi x5, x0, 1
      sw(5, 10, 0x114),        // 0x10: sw x5, 0x114(x10) (TRAP_CONTROL := 1)
      addi(1, 0, 10),          // 0x14: addi x1, x0, 10 (older instruction: MUST commit!)
      csw(1, 0, 0),            // 0x18: csw x1, 0(c0) (faulting MEM instruction: MUST NOT commit!)
      addi(2, 0, 20),          // 0x1C: addi x2, x0, 20 (in EX: MUST be flushed!)
      addi(3, 0, 30)           // 0x20: addi x3, x0, 30 (in ID: MUST be flushed!)
    )
    val nops = Seq.fill(32 - mainCode.length)(addi(0, 0, 0))
    val handlerCode = Seq(
      addi(4, 0, 40)           // 0x80: addi x4, x0, 40 (handler instruction: MUST commit!)
    )
    val progP8D = mainCode ++ nops ++ handlerCode

    test(new PipelinedCore(initialProgram = progP8D)) { dut =>
      val committedPcs = scala.collection.mutable.ArrayBuffer[BigInt]()
      for (_ <- 0 until 50) {
        if (dut.io.commit.valid.peek().litToBoolean) {
          committedPcs += dut.io.commit.pc.peek().litValue
        }
        dut.clock.step(1)
      }
      committedPcs should contain (BigInt(0x14))     // Older addi x1 committed
      committedPcs should not contain (BigInt(0x18)) // Faulting csw NOT committed
      committedPcs should not contain (BigInt(0x1C)) // Younger addi x2 NOT committed
      committedPcs should not contain (BigInt(0x20)) // Youngest addi x3 NOT committed
      committedPcs should contain (BigInt(0x80))     // Handler addi x4 committed
    }
  }

  // -------------------------------------------------------------
  // Test 31: Program E1 - MEM Trap Priority over Younger Taken Branch
  // -------------------------------------------------------------
  it should "execute Phase 8 Program E1: enforce MEM trap redirect priority over younger EX taken branch" in {
    val mainCode = Seq(
      lui(10, 0x80002),        // 0x00: lui x10, 0x80002
      addi(5, 0, 0x80),        // 0x04: addi x5, x0, 0x80
      sw(5, 10, 0x11C),        // 0x08: sw x5, 0x11C(x10) (TRAP_VECTOR := 0x80)
      addi(5, 0, 1),           // 0x0C: addi x5, x0, 1
      sw(5, 10, 0x114),        // 0x10: sw x5, 0x114(x10) (TRAP_CONTROL := 1)
      csw(5, 0, 0),            // 0x14: csw x5, 0(c0) (faulting MEM instruction)
      beq(0, 0, 16),           // 0x18: beq x0, x0, 16 (younger EX taken branch -> target 0x28, MUST be overridden!)
      addi(14, 0, 111),        // 0x1C: killed
      addi(14, 0, 222),        // 0x20: killed
      addi(14, 0, 333),        // 0x24: killed
      addi(14, 0, 444)         // 0x28: branch target (MUST NOT execute!)
    )
    val nops = Seq.fill(32 - mainCode.length)(addi(0, 0, 0))
    val handlerCode = Seq(
      addi(4, 0, 77)           // 0x80: addi x4, x0, 77 (handler reached!)
    )
    val progP8E1 = mainCode ++ nops ++ handlerCode

    test(new PipelinedCore(initialProgram = progP8E1)) { dut =>
      val committedPcs = scala.collection.mutable.ArrayBuffer[BigInt]()
      for (_ <- 0 until 50) {
        if (dut.io.commit.valid.peek().litToBoolean) {
          committedPcs += dut.io.commit.pc.peek().litValue
        }
        dut.clock.step(1)
      }
      committedPcs should not contain (BigInt(0x14)) // csw denied
      committedPcs should not contain (BigInt(0x18)) // branch killed
      committedPcs should not contain (BigInt(0x28)) // branch target never reached
      committedPcs should contain (BigInt(0x80))     // trap handler reached!
    }
  }

  // -------------------------------------------------------------
  // Test 32: Program E2 - MEM Trap Priority over Younger Active Divider
  // -------------------------------------------------------------
  it should "execute Phase 8 Program E2: kill younger active divider immediately on MEM trap and eliminate pipeline deadlock" in {
    val mainCode = Seq(
      lui(10, 0x80002),        // 0x00: lui x10, 0x80002
      addi(5, 0, 0x80),        // 0x04: addi x5, x0, 0x80
      sw(5, 10, 0x11C),        // 0x08: sw x5, 0x11C(x10) (TRAP_VECTOR := 0x80)
      addi(5, 0, 1),           // 0x0C: addi x5, x0, 1
      sw(5, 10, 0x114),        // 0x10: sw x5, 0x114(x10) (TRAP_CONTROL := 1)
      addi(1, 0, 100),         // 0x14: addi x1, x0, 100
      addi(2, 0, 7),           // 0x18: addi x2, x0, 7
      csw(5, 0, 0),            // 0x1C: csw x5, 0(c0) (faulting MEM instruction)
      div(3, 1, 2)             // 0x20: div x3, x1, x2 (younger multi-cycle DIV in EX -> killed immediately!)
    )
    val nops = Seq.fill(32 - mainCode.length)(addi(0, 0, 0))
    val handlerCode = Seq(
      addi(4, 0, 99)           // 0x80: addi x4, x0, 99 (handler reached without 32-cycle deadlock!)
    )
    val progP8E2 = mainCode ++ nops ++ handlerCode

    test(new PipelinedCore(initialProgram = progP8E2)) { dut =>
      val committedPcs = scala.collection.mutable.ArrayBuffer[BigInt]()
      var handlerReachedInCycles = -1
      for (c <- 0 until 50) {
        if (dut.io.commit.valid.peek().litToBoolean) {
          val pc = dut.io.commit.pc.peek().litValue
          committedPcs += pc
          if (pc == BigInt(0x80) && handlerReachedInCycles == -1) {
            handlerReachedInCycles = c
          }
        }
        dut.clock.step(1)
      }
      committedPcs should not contain (BigInt(0x1C)) // csw denied
      committedPcs should not contain (BigInt(0x20)) // div killed
      committedPcs should contain (BigInt(0x80))     // handler reached
      handlerReachedInCycles should be < 20          // Divider was killed immediately, no 32-cycle stall!
    }
  }

  // -------------------------------------------------------------
  // Test 33: Program F - Handler Resume & Retry
  // -------------------------------------------------------------
  it should "execute Phase 8 Program F: expand capability bounds in handler, execute TRAP_RETURN, and successfully retry faulting instruction" in {
    val mainCode = Seq(
      lui(10, 0x80002),        // 0x00: lui x10, 0x80002
      addi(5, 0, 0x80),        // 0x04: addi x5, x0, 0x80
      sw(5, 10, 0x11C),        // 0x08: sw x5, 0x11C(x10) (TRAP_VECTOR := 0x80)
      addi(5, 0, 1),           // 0x0C: addi x5, x0, 1
      sw(5, 10, 0x114),        // 0x10: sw x5, 0x114(x10) (TRAP_CONTROL := 1)
      addi(6, 0, 8),           // 0x14: addi x6, x0, 8
      csetbounds(3, 1, 6),     // 0x18: csetbounds c3, c1, x6 (c3 len = 8)
      addi(7, 0, 0x42),        // 0x1C: addi x7, x0, 0x42
      csw(7, 3, 12),           // 0x20: csw x7, 12(c3) (OOB fault at len=8! Traps to 0x80!)
      addi(8, 0, 88),          // 0x24: addi x8, x0, 88 (commits after retry!)
      clw(9, 3, 12)            // 0x28: clw x9, 12(c3) (read back retried store -> 0x42)
    )
    val nops = Seq.fill(32 - mainCode.length)(addi(0, 0, 0))
    val handlerCode = Seq(
      addi(6, 0, 64),          // 0x80: addi x6, x0, 64
      csetbounds(3, 1, 6),     // 0x84: csetbounds c3, c1, x6 (expand c3 bounds to 64 so offset 12 is valid!)
      addi(5, 0, 1),           // 0x88: addi x5, x0, 1
      sw(5, 10, 0x130)         // 0x8C: sw x5, 0x130(x10) (TRAP_RETURN -> return to TRAP_EPC=0x20 and retry!)
    )
    val progP8F = mainCode ++ nops ++ handlerCode

    test(new PipelinedCore(initialProgram = progP8F)) { dut =>
      val retiredVals = scala.collection.mutable.ArrayBuffer[BigInt]()
      for (_ <- 0 until 80) {
        if (dut.io.commit.valid.peek().litToBoolean) {
          retiredVals += dut.io.commit.writeData.peek().litValue
        }
        dut.clock.step(1)
      }
      retiredVals should contain (BigInt(88))   // x8 committed after retry
      retiredVals should contain (BigInt(0x42)) // x9 readback of successful retried store!
    }
  }

  // -------------------------------------------------------------
  // Test 34: Program G - Handler Advance & Skip
  // -------------------------------------------------------------
  it should "execute Phase 8 Program G: advance TRAP_EPC in handler, execute TRAP_RETURN, and skip past faulting instruction" in {
    val mainCode = Seq(
      lui(10, 0x80002),        // 0x00: lui x10, 0x80002
      addi(5, 0, 0x80),        // 0x04: addi x5, x0, 0x80
      sw(5, 10, 0x11C),        // 0x08: sw x5, 0x11C(x10) (TRAP_VECTOR := 0x80)
      addi(5, 0, 1),           // 0x0C: addi x5, x0, 1
      sw(5, 10, 0x114),        // 0x10: sw x5, 0x114(x10) (TRAP_CONTROL := 1)
      addi(7, 0, 0x77),        // 0x14: addi x7, x0, 0x77
      csw(7, 0, 0),            // 0x18: csw x7, 0(c0) (NULL fault -> traps to 0x80)
      addi(8, 0, 99),          // 0x1C: addi x8, x0, 99 (resumes here after advance!)
      addi(9, 0, 111)          // 0x20: addi x9, x0, 111
    )
    val nops = Seq.fill(32 - mainCode.length)(addi(0, 0, 0))
    val handlerCode = Seq(
      lw(12, 10, 0x120),       // 0x80: lw x12, 0x120(x10) (read TRAP_EPC = 0x18)
      addi(12, 12, 4),         // 0x84: addi x12, x12, 4 (compute 0x1C)
      sw(12, 10, 0x120),       // 0x88: sw x12, 0x120(x10) (update TRAP_EPC := 0x1C)
      addi(5, 0, 1),           // 0x8C: addi x5, x0, 1
      sw(5, 10, 0x130)         // 0x90: sw x5, 0x130(x10) (TRAP_RETURN -> resume at 0x1C!)
    )
    val progP8G = mainCode ++ nops ++ handlerCode

    test(new PipelinedCore(initialProgram = progP8G)) { dut =>
      val committedPcs = scala.collection.mutable.ArrayBuffer[BigInt]()
      val retiredVals = scala.collection.mutable.ArrayBuffer[BigInt]()
      for (_ <- 0 until 60) {
        if (dut.io.commit.valid.peek().litToBoolean) {
          committedPcs += dut.io.commit.pc.peek().litValue
          retiredVals += dut.io.commit.writeData.peek().litValue
        }
        dut.clock.step(1)
      }
      committedPcs should not contain (BigInt(0x18)) // Faulting instruction did not commit
      committedPcs should contain (BigInt(0x1C))     // Resumed at 0x1C
      committedPcs should contain (BigInt(0x20))     // Executed 0x20
      retiredVals should contain (BigInt(99))
      retiredVals should contain (BigInt(111))
    }
  }

  // -------------------------------------------------------------
  // Test 35: Program H - OS Capability Context Switch
  // -------------------------------------------------------------
  it should "execute Phase 8 Program H: save process capability state to PCB, clear capability registers, and restore via root provenance" in {
    // 1. Process 1 derives c3 from RAM root (c1, rootSelector=0) and c4 from MMIO root (c2, rootSelector=1)
    val progH = Seq(
      // Step 1: Process 1 Capability Setup
      addi(5, 0, 0x100),       // 0x00: x5 = 0x100
      cincoffset(3, 1, 5),     // 0x04: cincoffset c3, c1, x5
      addi(6, 0, 32),          // 0x08: x6 = 32
      csetbounds(3, 3, 6),     // 0x0C: csetbounds c3, c3, x6 (c3: base 0x100, len 32, perms RW)
      addi(7, 0, 0x2000),      // 0x10: x7 = 0x2000
      cincoffset(4, 2, 7),     // 0x14: cincoffset c4, c2, x7
      addi(8, 0, 16),          // 0x18: x8 = 16
      csetbounds(4, 4, 8),     // 0x1C: csetbounds c4, c4, x8 (c4: base 0x80002000, len 16, perms RW)

      // Step 2: OS Saves c3 PCB to RAM @ 0x300 (via RAM root c1)
      cgetbase(9, 3),          // 0x20: x9 = c3.base (0x100)
      sw(9, 0, 0x300),         // 0x24: sw x9, 0x300(x0)
      cgetlen(10, 3),          // 0x28: x10 = c3.length (32)
      sw(10, 0, 0x304),        // 0x2C: sw x10, 0x304(x0)
      cgetperm(11, 3),         // 0x30: x11 = c3.perms (3)
      sw(11, 0, 0x308),        // 0x34: sw x11, 0x308(x0)
      cgetoffset(12, 3),       // 0x38: x12 = c3.offset (0)
      sw(12, 0, 0x30C),        // 0x3C: sw x12, 0x30C(x0)
      addi(13, 0, 0),          // 0x40: x13 = rootSelector 0 (RAM root c1)
      sw(13, 0, 0x310),        // 0x44: sw x13, 0x310(x0)

      // Step 3: Context Switch - Clear Capability State
      cclear(3),               // 0x48: cclear c3 -> c3 becomes NULL
      cclear(4),               // 0x4C: cclear c4 -> c4 becomes NULL
      cgettag(14, 3),          // 0x50: x14 = c3.tag (MUST be 0!)
      cgettag(15, 4),          // 0x54: x15 = c4.tag (MUST be 0!)

      // Step 4: OS Restores c3 from PCB @ 0x300
      lw(16, 0, 0x300),        // 0x58: x16 = restored base (0x100)
      lw(17, 0, 0x304),        // 0x5C: x17 = restored length (32)
      lw(18, 0, 0x308),        // 0x60: x18 = restored perms (3)
      lw(19, 0, 0x30C),        // 0x64: x19 = restored offset (0)
      lw(20, 0, 0x310),        // 0x68: x20 = restored rootSelector (0 -> c1)

      // Deterministic Re-derivation from Hardware Root c1
      cincoffset(3, 1, 16),    // 0x6C: cincoffset c3, c1, x16 (cursor = 0x100)
      csetbounds(3, 3, 17),    // 0x70: csetbounds c3, c3, x17 (base = 0x100, len = 32)
      candperm(3, 3, 18),      // 0x74: candperm c3, c3, x18
      cincoffset(3, 3, 19),    // 0x78: cincoffset c3, c3, x19

      // Verify Restored c3 Parity
      cgettag(21, 3),          // 0x7C: x21 = c3.tag (MUST be 1!)
      cgetbase(22, 3),         // 0x80: x22 = c3.base (MUST be 0x100!)
      cgetlen(23, 3)           // 0x84: x23 = c3.length (MUST be 32!)
    )

    test(new PipelinedCore(initialProgram = progH)) { dut =>
      val retiredVals = scala.collection.mutable.ArrayBuffer[BigInt]()
      for (_ <- 0 until 120) {
        if (dut.io.commit.valid.peek().litToBoolean) {
          retiredVals += dut.io.commit.writeData.peek().litValue
        }
        dut.clock.step(1)
      }
      retiredVals should contain (BigInt(0))     // x14 = c3 cleared tag
      retiredVals should contain (BigInt(0))     // x15 = c4 cleared tag
      retiredVals should contain (BigInt(1))     // x21 = restored c3.tag = 1
      retiredVals should contain (BigInt(0x100)) // x22 = restored c3.base = 0x100
      retiredVals should contain (BigInt(32))    // x23 = restored c3.length = 32
    }
  }

  // -------------------------------------------------------------
  // Test 36: Program I - Sticky Audit Logger Independence
  // -------------------------------------------------------------
  it should "execute Phase 8 Program I: preserve frozen first-event audit logger independently of subsequent precise trap captures" in {
    val mainCode = Seq(
      lui(10, 0x80002),        // 0x00: lui x10, 0x80002
      // Step 1: Pre-trap violation with TRAP_CONTROL = 0 (Phase 7 audit logger capture)
      csw(0, 0, 0),            // 0x04: csw x0, 0(c0) (DENIED NULL write @ PC=0x04 -> SEC_PC := 0x04)

      // Step 2: Enable Precise Traps
      addi(5, 0, 0x80),        // 0x08: addi x5, x0, 0x80
      sw(5, 10, 0x11C),        // 0x0C: sw x5, 0x11C(x10) (TRAP_VECTOR := 0x80)
      addi(5, 0, 1),           // 0x10: addi x5, x0, 1
      sw(5, 10, 0x114),        // 0x14: sw x5, 0x114(x10) (TRAP_CONTROL := 1)

      // Step 3: Second violation triggers precise trap @ PC=0x28
      addi(5, 0, 0x100),       // 0x18: addi x5, x0, 0x100
      cincoffset(3, 1, 5),     // 0x1C: cincoffset c3, c1, x5
      addi(6, 0, 16),          // 0x20: addi x6, x0, 16
      csetbounds(3, 3, 6),     // 0x24: csetbounds c3, c3, x6
      csw(5, 3, 20)            // 0x28: csw x5, 20(c3) (OOB fault -> TRAP_EPC := 0x28, traps to 0x80)
    )
    val nops = Seq.fill(32 - mainCode.length)(addi(0, 0, 0))
    val handlerCode = Seq(
      lw(11, 10, 0x104),       // 0x80: lw x11, 0x104(x10) (read SEC_PC -> MUST still be 0x04!)
      lw(12, 10, 0x120)        // 0x84: lw x12, 0x120(x10) (read TRAP_EPC -> MUST be 0x28!)
    )
    val progP8I = mainCode ++ nops ++ handlerCode

    test(new PipelinedCore(initialProgram = progP8I)) { dut =>
      val retiredVals = scala.collection.mutable.ArrayBuffer[BigInt]()
      for (_ <- 0 until 60) {
        if (dut.io.commit.valid.peek().litToBoolean) {
          retiredVals += dut.io.commit.writeData.peek().litValue
        }
        dut.clock.step(1)
      }
      retiredVals should contain (BigInt(0x04)) // x11 = SEC_PC (frozen first event)
      retiredVals should contain (BigInt(0x28)) // x12 = TRAP_EPC (fresh precise trap)
    }
  }

  // -------------------------------------------------------------
  // Test 37: Program J - Nested Violation & Double Fault
  // -------------------------------------------------------------
  it should "execute Phase 8 Program J: latch DOUBLE_FAULT on nested violation inside active trap handler without recursive redirect" in {
    val mainCode = Seq(
      lui(10, 0x80002),        // 0x00: lui x10, 0x80002
      addi(5, 0, 0x80),        // 0x04: addi x5, x0, 0x80
      sw(5, 10, 0x11C),        // 0x08: sw x5, 0x11C(x10) (TRAP_VECTOR := 0x80)
      addi(5, 0, 1),           // 0x0C: addi x5, x0, 1
      sw(5, 10, 0x114),        // 0x10: sw x5, 0x114(x10) (TRAP_CONTROL := 1)
      csw(5, 0, 0)             // 0x14: csw x5, 0(c0) (primary trap @ PC=0x14 -> TRAP_ACTIVE=1, jumps to 0x80)
    )
    val nops = Seq.fill(32 - mainCode.length)(addi(0, 0, 0))
    val handlerCode = Seq(
      // Step 1: Accidental nested capability violation inside active handler
      csw(5, 0, 0),            // 0x80: csw x5, 0(c0) (nested violation! DOUBLE_FAULT := 1, no recursive redirect)
      addi(6, 0, 99),          // 0x84: addi x6, x0, 99 (handler continues sequentially!)
      lw(7, 10, 0x118),        // 0x88: lw x7, 0x118(x10) (read TRAP_STATUS -> ACTIVE=1, DOUBLE_FAULT=1 -> 3)
      lw(8, 10, 0x120)         // 0x8C: lw x8, 0x120(x10) (read TRAP_EPC -> MUST remain 0x14!)
    )
    val progP8J = mainCode ++ nops ++ handlerCode

    test(new PipelinedCore(initialProgram = progP8J)) { dut =>
      val retiredVals = scala.collection.mutable.ArrayBuffer[BigInt]()
      for (_ <- 0 until 60) {
        if (dut.io.commit.valid.peek().litToBoolean) {
          retiredVals += dut.io.commit.writeData.peek().litValue
        }
        dut.clock.step(1)
      }
      retiredVals should contain (BigInt(99))   // x6 = 99 (handler continued sequentially)
      retiredVals should contain (BigInt(3))    // x7 = TRAP_STATUS (ACTIVE=1, DOUBLE_FAULT=1)
      retiredVals should contain (BigInt(0x14)) // x8 = TRAP_EPC (preserved from primary trap)
    }
  }
}


