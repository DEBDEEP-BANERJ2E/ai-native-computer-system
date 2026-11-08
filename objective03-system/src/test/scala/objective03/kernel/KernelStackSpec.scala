package objective03.kernel

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import objective03.core.SystemCore
import scala.sys.process._
import scala.collection.mutable.ArrayBuffer

// Stage B2: prove the ASSEMBLED kernel boots through M->S->U with distinct
// physical stacks and returns U->S->U on ecall. kernel.S is built by the real
// Phase-2 assembler (an32-as) at test time - no hand-encoded BigInt kernel.
class KernelStackSpec extends AnyFlatSpec with ChiselScalatestTester {
  val kernelDir = "/Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective03-system/kernel"
  val assembler = "/Users/debdeepbanerjee/Desktop/ai-native-computer-system/toolchain/bin/an32-as"
  val kernelHex = "/tmp/b2_kernel.hex"

  // Assemble kernel.S -> Verilog hex image -> Seq[BigInt] instruction words.
  def assembleKernel(): Seq[BigInt] = {
    val cmd = s"$assembler $kernelDir/kernel.S --emit-hex $kernelHex"
    val rc  = Process(cmd).!
    assert(rc == 0, s"an32-as failed on kernel.S (exit $rc)")
    scala.io.Source.fromFile(kernelHex).getLines().toSeq
      .filter(l => l.nonEmpty && l(0) != '@')
      .map(l => BigInt(l.trim, 16)).toSeq
  }

  def loadImage(dut: SystemCore, words: Seq[BigInt]): Unit = {
    val jal0 = BigInt("0000006F", 16)
    for (i <- words.indices) {
      dut.io.imemLoadAddr.poke((i * 4).U); dut.io.imemLoadData.poke(jal0.U)
      dut.io.imemLoadEn.poke(true.B); dut.clock.step(1)
    }
    words.zipWithIndex.foreach { case (w, i) =>
      dut.io.imemLoadAddr.poke((i * 4).U); dut.io.imemLoadData.poke(w.U)
      dut.io.imemLoadEn.poke(true.B); dut.clock.step(1)
    }
    dut.io.imemLoadEn.poke(false.B)
    dut.io.imemLoadAddr.poke(0.U); dut.io.imemLoadData.poke(0.U)
  }

  // capture (pc, rd, writeData) for every WB commit
  def captureCommits(dut: SystemCore, n: Int): Seq[(BigInt, BigInt, BigInt)] = {
    val buf = ArrayBuffer[(BigInt, BigInt, BigInt)]()
    for (_ <- 0 until n) {
      dut.clock.step(1)
      if (dut.io.commit.valid.peek().litValue == 1 &&
          dut.io.commit.regWrite.peek().litValue == 1 &&
          dut.io.commit.rd.peek().litValue != 0) {
        buf += ((dut.io.commit.pc.peek().litValue,
                 dut.io.commit.rd.peek().litValue,
                 dut.io.commit.writeData.peek().litValue))
      }
    }
    buf.toSeq
  }
  def rdWrites(cs: Seq[(BigInt, BigInt, BigInt)], rd: Int) =
    cs.collect { case (pc, r, d) if r == rd => d }

  it should "boot assembled kernel: M->S->U stacks then U->S->U on ecall" in {
    val words = assembleKernel()
    assert(words.nonEmpty, "kernel.S produced no instructions")
    test(new SystemCore(imemDepthWords = 256, dmemSizeBytes = 65536)) { dut =>
      loadImage(dut, words)
      val cs = captureCommits(dut, 3000)

      val spWrites  = rdWrites(cs, 2)   // x2 = sp
      val a0Writes  = rdWrites(cs, 10)  // x10 = a0
      val spVals    = spWrites.map(_.toLong & 0xffffffffL).toSet

      // Stack regions actually used (dmem tops): M 0x10000, S 0xF000, U 0xE000
      assert(spVals.contains(0x10000L), s"M stack top not used; sp vals=$spVals")
      assert(spVals.contains(0x0F000L), s"S kernel stack top not used; sp vals=$spVals")
      assert(spVals.contains(0x0E000L), s"U stack top not used; sp vals=$spVals")

      // After the trap round-trip, sp must return to the USER stack top
      // (sscratch swap restored it), proving U->S->U stack discipline.
      val lastSp = spWrites.lastOption.map(_.toLong & 0xffffffffL).getOrElse(-1L)
      assert(lastSp == 0x0E000L, s"final sp not restored to U stack; last=$lastSp hex=${java.lang.Long.toHexString(lastSp)}")

      // U resumed after ecall and wrote the marker in U-mode.
      assert(a0Writes.contains(BigInt(0xBB)), s"post-ecall U marker 0xBB missing; a0=$a0Writes")
    }
  }
}
