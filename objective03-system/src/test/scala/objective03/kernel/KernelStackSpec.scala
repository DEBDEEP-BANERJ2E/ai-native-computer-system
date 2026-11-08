package objective03.kernel

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import objective03.core.SystemCore
import objective03.csr.Privilege
import scala.sys.process._
import scala.collection.mutable

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

  // Boot-loader model: park the core on jal x0,0 in EVERY slot, install the
  // whole image except the reset vector, then release boot by writing word 0
  // last. This prevents the core running off into half-installed code.
  def loadImage(dut: SystemCore, words: Seq[BigInt]): Unit = {
    val jal0 = BigInt("0000006F", 16)
    def write(i: Int, w: BigInt): Unit = {
      dut.io.imemLoadAddr.poke((i * 4).U); dut.io.imemLoadData.poke(w.U)
      dut.io.imemLoadEn.poke(true.B);  dut.clock.step(1)
    }
    for (i <- words.indices) write(i, jal0)          // safe-fill
    for (i <- 1 until words.length) write(i, words(i)) // image minus reset vector
    write(0, words(0))                                // release boot LAST
    dut.io.imemLoadEn.poke(false.B)
    dut.io.imemLoadAddr.poke(0.U); dut.io.imemLoadData.poke(0.U)
  }

  // One traced commit record for diagnostics.
  case class Rec(pc: Long, insn: Long, mode: Long, rd: Int, wdata: Long,
                 trap: Boolean, vec: Long, epc: Long, cause: Long)

  // Run until `done` or `maxCycles`; keep a rolling window of ALL valid
  // commits (incl. non-writing ones like j/jal spin loops) plus trap state.
  // Returns (completed, regs, trace).
  def runUntil(dut: SystemCore, maxCycles: Int)(
      done: mutable.Map[Int, Long] => Boolean): (Boolean, Map[Int, Long], Seq[Rec]) = {
    val regs  = mutable.Map[Int, Long]()
    val trace = mutable.ArrayBuffer[Rec]()
    var cyc = 0
    var finished = false
    while (cyc < maxCycles && !finished) {
      dut.clock.step(1); cyc += 1
      val c = dut.io.commit
      if (c.valid.peek().litValue == 1) {
        val pc   = c.pc.peek().litValue.toLong
        val insn = c.instruction.peek().litValue.toLong
        val rd   = c.rd.peek().litValue.toInt
        val wd   = c.writeData.peek().litValue.toLong
        val rw   = c.regWrite.peek().litValue == 1
        val mode = dut.io.privLevel.peek().litValue.toLong
        val tr   = dut.io.trapTakenSys.peek().litValue == 1
        val vec  = dut.io.sysTrapVector.peek().litValue.toLong
        val epc  = dut.io.sysTrapEpc.peek().litValue.toLong
        val cau  = dut.io.sysTrapCause.peek().litValue.toLong
        if (rw && rd != 0) regs(rd) = wd
        trace += Rec(pc, insn, mode, rd, wd, tr, vec, epc, cau)
        if (trace.size > 4096) trace.remove(0, trace.size - 4096)
      }
      finished = done(regs)
    }
    (finished, regs.toMap, trace.toSeq)
  }

  def reg(regs: Map[Int, Long], x: Int) = regs.getOrElse(x, -1L)

  it should "boot assembled kernel: M->S->U stacks then U->S->U on ecall" in {
    val words = assembleKernel()
    assert(words.nonEmpty, "kernel.S produced no instructions")
    test(new SystemCore(imemDepthWords = 256, dmemSizeBytes = 65536)) { dut =>
      loadImage(dut, words)

      // Completion: U wrote a0=0xBB after ecall AND is in U-mode on U stack.
      val (ok, regs, trace) = runUntil(dut, 800) { r =>
        r.getOrElse(10, -1L) == 0xBB &&        // a0 = post-ecall marker
        dut.io.privLevel.peek().litValue == Privilege.U &&
        r.getOrElse(2, -1L) == 0x0E000L        // sp = U_STACK_TOP
      }

      if (!ok) {
        def fmt(r: Rec) =
          f"pc=${r.pc}%04x mode=${r.mode} rd=${r.rd}%2d wd=${r.wdata}%08x " +
          f"trap=${r.trap} vec=${r.vec}%08x epc=${r.epc}%08x cause=${r.cause}%02x insn=${r.insn}%08x"
        val traps = trace.filter(_.trap)
        val head  = trace.take(70).map(fmt)
        val tail  = trace.takeRight(16).map(fmt)
        fail(s"B2 did not complete in 800 cycles.\n" +
             s"== TRAP EVENTS (${traps.size}) ==\n" + traps.map(fmt).mkString("\n") +
             s"\n== FIRST 70 COMMITS (boot path) ==\n" + head.mkString("\n") +
             s"\n== LAST 16 ==\n" + tail.mkString("\n"))
      }

      // Stack regions actually used (dmem tops): M 0x10000, S 0xF000, U 0xE000.
      // Re-run a short window already covered; instead assert via the trace's
      // sp(x2) write history captured in regs is insufficient - use dedicated
      // sp-write scan below.
      val spWrites = trace.collect { case r if r.rd == 2 && r.wdata != 0 => r.wdata }.toSet
      // sp top values that MUST have been written during boot/trap:
      //   0x10000 (M), 0xF000 (S kernel), 0xE000 (U)
      // (frame addi/sub values also appear; tops are the invariant anchors)
      assert(spWrites.contains(0x10000L), s"M stack top not written; spWrites=$spWrites")
      assert(spWrites.contains(0x0F000L), s"S kernel stack top not written; spWrites=$spWrites")
      assert(spWrites.contains(0x0E000L), s"U stack top not written; spWrites=$spWrites")

      // Final state: still in U on the user stack.
      assert(reg(regs, 10) == 0xBB, "post-ecall U marker missing")
      assert(reg(regs, 2)  == 0x0E000L, "sp not on U stack at end")
    }
  }
}
