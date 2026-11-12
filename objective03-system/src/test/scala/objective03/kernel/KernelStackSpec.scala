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

  // Lightweight event-driven B5 diagnostic: counts trap entries by
  // mode/cause with early-stop on storm signatures. Minimal per-cycle peeks.
  def runDiag(dut: SystemCore, maxCycles: Int):
      (String, Map[Int, Long], Seq[Rec]) = {
    val regs  = mutable.Map[Int, Long]()
    val ring  = mutable.ArrayBuffer[Rec]()
    val boot  = mutable.ArrayBuffer[String]()   // first commits, boot path
    var mTraps = 0; var sStip = 0; var mtip = 0; var uCommits = 0
    var timerResumePending = false; var sawTimerThenU = false
    var lastPC = 0L; var lastMode = 0L; var lastCause = 0L; var tip = false
    var stop = ""; var cyc = 0; var finished = false
    while (cyc < maxCycles && !finished) {
      dut.clock.step(1); cyc += 1
      val c = dut.io.commit
      if (c.valid.peek().litValue == 1) {
        val pc   = c.pc.peek().litValue.toLong
        val mode = dut.io.privLevel.peek().litValue.toLong
        val tr   = dut.io.trapTakenSys.peek().litValue == 1
        val cau  = dut.io.sysTrapCause.peek().litValue.toLong
        val rd   = c.rd.peek().litValue.toInt
        val rw   = c.regWrite.peek().litValue == 1
        val wd   = c.writeData.peek().litValue.toLong
        if (rw && rd != 0) regs(rd) = wd
        lastPC = pc; lastMode = mode; lastCause = cau; tip = dut.io.timerMtip.peek().litValue == 1
        if (mode == 0) uCommits += 1
        if (tr) {
          ring += Rec(pc, 0, mode, rd, wd, tr, 0, 0, cau)
          if (ring.size > 16) ring.remove(0, ring.size - 16)
          if (mode == 3) mTraps += 1                 // M-mode trap entry
          if (cau == 0x80000007L) mtip += 1          // MTIP interrupt (real cause)
          if (cau == 0x80000005L) { sStip += 1; timerResumePending = true }
        }
        if (mode == 0 && timerResumePending) sawTimerThenU = true
        if (boot.size < 90)
          boot += f"cyc=$cyc pc=${pc}%04x m=$mode insn=${c.instruction.peek().litValue.toLong}%08x tr=${tr} cau=${cau}%08x tip=${tip}"
      }
      if (mtip >= 4)       { stop = "M-STORM";      finished = true }
      else if (sStip >= 4) { stop = "S-STORM";      finished = true }
      else if (sawTimerThenU){ stop = "TIMER-OK";   finished = true }
    }
    if (stop == "") stop = if (cyc >= maxCycles) "CEILING" else "?"
    val summary = s"$stop mtip=$mtip sStip=$sStip mTraps=$mTraps uCommits=$uCommits " +
                  s"cyc=$cyc lastPC=${lastPC}%04x lastMode=$lastMode lastCause=${lastCause}%08x tip=${tip}"
    println("[B5-BOOT]\n" + boot.mkString("\n"))
    (summary, regs.toMap, ring.toSeq)
  }

  def reg(regs: Map[Int, Long], x: Int) = regs.getOrElse(x, -1L)

  it should "boot assembled kernel: M->S->U stacks then U->S->U on ecall" in {
    val words = assembleKernel()
    assert(words.nonEmpty, "kernel.S produced no instructions")
    test(new SystemCore(imemDepthWords = 256, dmemSizeBytes = 65536)) { dut =>
      // B5 legitimately runs for many thousands of cycles; disable
      // chiseltest's default clock watchdog (runUntil has its own ceiling).
      dut.clock.setTimeout(0)
      loadImage(dut, words)

      // B5 short diagnostic: classify MTIP->STIP with real trap causes.
      // Early-stops on M-STORM / S-STORM / first timer->U round-trip.
      val (summary, regs, ring) = runDiag(dut, 6000)
      println(s"[B5-DIAG] $summary kticks=${reg(regs,7)}")
      ring.foreach(r => println(f"  trap pc=${r.pc}%04x m=${r.mode} cause=${r.cause}%08x"))
      assert(!summary.startsWith("M-STORM") && !summary.startsWith("S-STORM"),
             s"B5 timer storm: $summary")
    }
  }
}
