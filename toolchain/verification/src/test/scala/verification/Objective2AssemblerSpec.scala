package verification

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import objective02.pipeline.PipelinedCore
import java.io.{File, PrintWriter}
import scala.sys.process._
import scala.io.Source

class Objective2AssemblerSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Objective-2 PipelinedCore Hardware with an32-as Assembly Injection"

  // Helper method to assemble an assembly source string using an32-as and return Seq[BigInt]
  def assembleSource(sourceCode: String): Seq[BigInt] = {
    val tempSrc = File.createTempFile("an32_test_", ".s")
    val tempHex = File.createTempFile("an32_test_", ".hex")
    tempSrc.deleteOnExit()
    tempHex.deleteOnExit()

    val writer = new PrintWriter(tempSrc)
    try {
      writer.write(sourceCode)
    } finally {
      writer.close()
    }

    val asBinary = "../bin/an32-as"
    val cmd = Seq(asBinary, "--emit-hex", tempHex.getAbsolutePath, tempSrc.getAbsolutePath)
    val exitCode = cmd.!
    if (exitCode != 0) {
      throw new RuntimeException(s"Assembler an32-as failed on source:\n$sourceCode")
    }

    val lines = Source.fromFile(tempHex).getLines().toList
    lines.filterNot(line => line.startsWith("@") || line.trim.isEmpty).map { line =>
      BigInt(line.trim, 16)
    }
  }

  // -------------------------------------------------------------
  // Test 1: Assembled Integer ALU and Forwarding
  // -------------------------------------------------------------
  it should "execute assembled integer instructions with hazard forwarding" in {
    val asm =
      """
      .text
      .globl main
      main:
          addi x1, zero, 10
          addi x2, zero, 20
          add  x3, x1, x2
          sub  x4, x3, x1
          slt  x5, x4, x3
      """

    val words = assembleSource(asm)
    words.length should be (5)

    test(new PipelinedCore(initialProgram = words)) { dut =>
      var retiredCount = 0
      var lastX5: BigInt = 0

      for (_ <- 0 until 40) {
        dut.clock.step(1)
        if (dut.io.commit.valid.peek().litToBoolean) {
          retiredCount += 1
          if (dut.io.commit.rd.peek().litValue == 5 && dut.io.commit.regWrite.peek().litToBoolean) {
            lastX5 = dut.io.commit.writeData.peek().litValue
          }
        }
      }

      retiredCount should be >= 5
      lastX5 should be (BigInt(1)) // slt x5, 20, 30 -> 1
    }
  }

  // -------------------------------------------------------------
  // Test 2: Assembled Branch Loops with Numeric Local Labels
  // -------------------------------------------------------------
  it should "execute assembled loop with local numeric labels and backward branch" in {
    val asm =
      """
      .text
      .globl _start
      _start:
          addi x1, zero, 0
          addi x2, zero, 5
      1:
          addi x1, x1, 2
          addi x2, x2, -1
          bne  x2, zero, 1b
          addi x3, x1, 10
      """

    val words = assembleSource(asm)

    test(new PipelinedCore(initialProgram = words)) { dut =>
      var lastX3: BigInt = 0

      for (_ <- 0 until 100) {
        dut.clock.step(1)
        if (dut.io.commit.valid.peek().litToBoolean) {
          if (dut.io.commit.rd.peek().litValue == 3 && dut.io.commit.regWrite.peek().litToBoolean) {
            lastX3 = dut.io.commit.writeData.peek().litValue
          }
        }
      }

      // x1 = 5 * 2 = 10, x3 = 10 + 10 = 20
      lastX3 should be (BigInt(20))
    }
  }

  // -------------------------------------------------------------
  // Test 3: Macro Expansion & Repetition
  // -------------------------------------------------------------
  it should "execute assembled code generated from macros and repetition blocks" in {
    val asm =
      """
      .text
      .equ FACTOR, 3
      .macro TRIPLE rd, rs
          addi \rd, zero, 0
          .rept FACTOR
              add \rd, \rd, \rs
          .endr
      .endm

      _start:
          addi x10, zero, 7
          TRIPLE x11, x10
      """

    val words = assembleSource(asm)
    words.length should be (5) // addi x10 + addi x11 + 3 * add x11

    test(new PipelinedCore(initialProgram = words)) { dut =>
      var lastX11: BigInt = 0

      for (_ <- 0 until 50) {
        dut.clock.step(1)
        if (dut.io.commit.valid.peek().litToBoolean) {
          if (dut.io.commit.rd.peek().litValue == 11 && dut.io.commit.regWrite.peek().litToBoolean) {
            lastX11 = dut.io.commit.writeData.peek().litValue
          }
        }
      }

      lastX11 should be (BigInt(21)) // 3 * 7 = 21
    }
  }

  // -------------------------------------------------------------
  // Test 4: Capability Derivation and Memory Access
  // -------------------------------------------------------------
  it should "execute capability derivation and capability load/store instructions" in {
    val asm =
      """
      .text
      _start:
          addi a0, zero, 64
          csetbounds ca0, cram, a0
          addi t0, zero, 0x5A
          csw  t0, 0(ca0)
          clw  t1, 0(ca0)
      """

    val words = assembleSource(asm)

    test(new PipelinedCore(initialProgram = words)) { dut =>
      var lastT1: BigInt = 0

      for (_ <- 0 until 60) {
        dut.clock.step(1)
        if (dut.io.commit.valid.peek().litToBoolean) {
          // t1 is x6
          if (dut.io.commit.rd.peek().litValue == 6 && dut.io.commit.regWrite.peek().litToBoolean) {
            lastT1 = dut.io.commit.writeData.peek().litValue
          }
        }
      }

      lastT1 should be (BigInt(0x5A))
    }
  }

  // -------------------------------------------------------------
  // Test 5: Deterministic PC-Relative Address Loading (la pseudo)
  // -------------------------------------------------------------
  it should "execute deterministic la pseudo-instruction with paired fixups" in {
    val asm =
      """
      .text
      _start:
          la   a0, data_target
          addi t0, zero, 0x77
          sw   t0, 0(a0)
          lw   a1, 0(a0)
          j    done
          .p2align 2
      data_target:
          .4byte 0
      done:
          addi a2, zero, 1
      """

    val words = assembleSource(asm)

    test(new PipelinedCore(initialProgram = words)) { dut =>
      var lastA0: BigInt = 0
      var lastA1: BigInt = 0

      for (_ <- 0 until 80) {
        dut.clock.step(1)
        if (dut.io.commit.valid.peek().litToBoolean) {
          // a0 is x10
          if (dut.io.commit.rd.peek().litValue == 10 && dut.io.commit.regWrite.peek().litToBoolean) {
            lastA0 = dut.io.commit.writeData.peek().litValue
          }
          // a1 is x11
          if (dut.io.commit.rd.peek().litValue == 11 && dut.io.commit.regWrite.peek().litToBoolean) {
            lastA1 = dut.io.commit.writeData.peek().litValue
          }
        }
      }

      // la a0, data_target calculates exact target address
      lastA0 should be (BigInt(24)) // offset 0x18 = 24
      lastA1 should be (BigInt(0x77))
    }
  }
}
