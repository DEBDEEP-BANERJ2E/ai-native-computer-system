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
    }
  }
}
