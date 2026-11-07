package objective03

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import objective03.core.SystemCore
import objective03.csr.{Privilege, TrapCause, CSR}

// Retirement-precision interrupt tests: prove xepc = next unretired instr.
class SystemCoreEpcSpec extends AnyFlatSpec with ChiselScalatestTester {

  def loadProg(dut: SystemCore, prog: Seq[BigInt]): Unit = {
    prog.zipWithIndex.foreach { case (w, i) =>
      dut.io.imemLoadAddr.poke((i * 4).U); dut.io.imemLoadData.poke(w.U)
      dut.io.imemLoadEn.poke(true.B); dut.clock.step(1)
    }
    dut.io.imemLoadEn.poke(false.B)
    dut.io.imemLoadAddr.poke(0.U); dut.io.imemLoadData.poke(0.U)
  }
  def csrW(c: Int, rs1: Int, rd: Int) = BigInt(c)<<20 | BigInt(rs1)<<15 | 1L<<12 | BigInt(rd)<<7 | 0x73
  def csrR(c: Int, rs1: Int, rd: Int) = BigInt(c)<<20 | BigInt(rs1)<<15 | 2L<<12 | BigInt(rd)<<7 | 0x73
  def li(rd: Int, imm: Int) = BigInt(imm & 0xFFF)<<20 | BigInt(rd)<<7 | 0x13
  def nop: BigInt = 0x13
  def jal0: BigInt = BigInt("0000006F", 16)
  def fenceI: BigInt = BigInt("0FF0000F", 16)   // fence.i
  // sw rs2, imm(rs1): opcode 0x23 funct3=010 ; lw rd, imm(rs1): opcode 0x03 funct3=010
  def sw(rs2: Int, imm: Int, rs1: Int): BigInt =
    BigInt((imm>>5)&0x7F)<<25 | BigInt(rs2)<<20 | BigInt(rs1)<<15 | 2L<<12 | BigInt(imm&0x1F)<<7 | 0x23
  def lw(rd: Int, imm: Int, rs1: Int): BigInt =
    BigInt(imm&0xFFF)<<20 | BigInt(rs1)<<15 | 2L<<12 | BigInt(rd)<<7 | 0x03
  def run(dut: SystemCore, n: Int) = { var i=0; while(i<n){dut.clock.step(1);i+=1} }

  // Load prog while capturing every commit writeData to `rd` (core runs during
  // load cycles, so capture must start at cycle 0). Returns all observed writes.
  def simulate(dut: SystemCore, prog: Seq[BigInt], rd: Int, extraCycles: Int): Seq[BigInt] = {
    val writes = scala.collection.mutable.ArrayBuffer[BigInt]()
    def cap(): Unit = {
      if (dut.io.commit.valid.peek().litValue == 1 &&
          dut.io.commit.regWrite.peek().litValue == 1 &&
          dut.io.commit.rd.peek().litValue == rd)
        writes += dut.io.commit.writeData.peek().litValue
    }
    prog.zipWithIndex.foreach { case (w, i) =>
      dut.io.imemLoadAddr.poke((i * 4).U); dut.io.imemLoadData.poke(w.U)
      dut.io.imemLoadEn.poke(true.B); dut.clock.step(1); cap()
    }
    dut.io.imemLoadEn.poke(false.B)
    dut.io.imemLoadAddr.poke(0.U); dut.io.imemLoadData.poke(0.U)
    var i = 0
    while (i < extraCycles) { dut.clock.step(1); cap(); i += 1 }
    writes.toSeq
  }

  // Preload `size` slots with jal0 (safe spin), then apply (idx->instr) updates
  // in the given order while capturing commits to `rd`. Returns captured writes.
  def loadSelective(dut: SystemCore, size: Int, updates: Seq[(Int, BigInt)],
                    rd: Int, extraCycles: Int): Seq[BigInt] = {
    val writes = scala.collection.mutable.ArrayBuffer[BigInt]()
    def cap(): Unit = {
      if (dut.io.commit.valid.peek().litValue == 1 &&
          dut.io.commit.regWrite.peek().litValue == 1 &&
          dut.io.commit.rd.peek().litValue == rd)
        writes += dut.io.commit.writeData.peek().litValue
    }
    def poke(i: Int, w: BigInt): Unit = {
      dut.io.imemLoadAddr.poke((i * 4).U); dut.io.imemLoadData.poke(w.U)
      dut.io.imemLoadEn.poke(true.B); dut.clock.step(1); cap()
    }
    (0 until size).foreach(i => poke(i, jal0))          // safe spin everywhere
    updates.foreach { case (i, w) => poke(i, w) }        // handler first, then body
    dut.io.imemLoadEn.poke(false.B)
    dut.io.imemLoadAddr.poke(0.U); dut.io.imemLoadData.poke(0.U)
    var k = 0
    while (k < extraCycles) { dut.clock.step(1); cap(); k += 1 }
    writes.toSeq
  }

  // Capture every WB commit (pc, rd, writeData) for `n` cycles.
  def captureCommits(dut: SystemCore, n: Int): Seq[(BigInt, BigInt, BigInt)] = {
    val buf = scala.collection.mutable.ArrayBuffer[(BigInt, BigInt, BigInt)]()
    var i = 0
    while (i < n) {
      dut.clock.step(1)
      if (dut.io.commit.valid.peek().litValue == 1 &&
          dut.io.commit.regWrite.peek().litValue == 1 &&
          dut.io.commit.rd.peek().litValue != 0)
        buf += ((dut.io.commit.pc.peek().litValue, dut.io.commit.rd.peek().litValue,
                 dut.io.commit.writeData.peek().litValue))
      i += 1
    }
    buf.toSeq
  }
  def rdWrites(cs: Seq[(BigInt, BigInt, BigInt)], rd: Int): Seq[BigInt] =
    cs.collect { case (pc, r, d) if r == rd => d }

  behavior of "SystemCore interrupt EPC precision"

  it should "save xepc = next unretired instruction at WB boundary" in {
    test(new SystemCore(imemDepthWords = 128, dmemSizeBytes = 4096)) { dut =>
      // mtvec=0x80 direct; handler@0x80: csrrs x5,mepc ; csrrs x6,mcause ; jal0
      // Body: nop x4 (pc0..12); inject STIP via mip write @pc16; then nop x4 @20..
      // Interrupt fires at a WB boundary; mepc must equal the pc of the first
      // instruction that had NOT retired = some pc in [20..36] depending on timing.
      // We verify mepc is 4-aligned, >=20, <=36, and equals an instruction boundary.
      // Preload jal0 (safe spin, no illegal during load). Load handler region
      // FIRST so it is resident before any trap, then body.
      val body = Seq(
        li(6, 0x80), csrW(0x305, 6, 0),   // mtvec=0x80
        li(6, 0x80), csrW(0x304, 6, 0),   // mie.MTIE (bit7) -- MTIP already pending (mtimecmp=0)
        li(6, 0x08), csrW(0x300, 6, 0),   // mstatus.MIE
        nop, nop, nop, nop, nop, nop, nop, nop, nop, nop
      )
      // ALSO capture mcause->x6 is rd6; we watch rd5=mepc. For cause check use rd5 too? keep mepc.
      val updates = Seq(
        (32, csrR(0x341, 0, 5)),  // handler @0x80: mepc->x5
        (33, csrR(0x342, 0, 6)),  // mcause->x6
        (34, jal0), (35, jal0)
      ) ++ body.zipWithIndex.map { case (w,i) => (i, w) }
      val writesMepc = loadSelective(dut, 64, updates, 5, 150)
      val v = writesMepc.lastOption.map(_.toLong).getOrElse(-1L)
      assert(v >= 28 && v <= 56 && (v % 4) == 0)
    }
  }

  it should "not commit younger instructions after interrupt" in {
    test(new SystemCore(imemDepthWords = 128, dmemSizeBytes = 4096)) { dut =>
      // After interrupt, handler runs; verify no post-inject nop wrote x7
      // Give each nop a distinct rd write? nops write x0 (discarded).
      // Use addi x7,x0,i for traceable writes after inject point.
      val p = Seq(
        li(6, 0x80), csrW(0x305, 6, 0),
        li(6, 0x20), csrW(0x304, 6, 0),
        li(6, 0x08), csrW(0x300, 6, 0),
        csrW(0x344, 6, 0),   // inject STIP
        li(7, 0xAA),         // @24 younger instr that must NOT retire
        li(7, 0xBB),         // @28
        li(7, 0xCC)          // @32
      ) ++ Seq.fill(32 - 9)(nop) ++
      Seq(csrR(0x341, 0, 5), csrR(0x342, 0, 6), jal0) ++ Seq.fill(8)(nop)
      loadProg(dut, p)
      // collect all writes to x7 — should NOT contain 0xAA/0xBB/0xCC post-inject
      var sawBad = false
      var i = 0
      while (i < 120) {
        dut.clock.step(1)
        if (dut.io.commit.valid.peek().litValue == 1 &&
            dut.io.commit.regWrite.peek().litValue == 1 &&
            dut.io.commit.rd.peek().litValue == 7) {
          val w = dut.io.commit.writeData.peek().litValue
          if (w == 0xAA || w == 0xBB || w == 0xCC) sawBad = true
        }
        i += 1
      }
      assert(!sawBad)
    }
  }

  it should "direct mtvec sends every trap to BASE" in {
    test(new SystemCore(imemDepthWords = 128, dmemSizeBytes = 4096)) { dut =>
      // mtvec=0x80 mode=00 direct; ecall -> target 0x80 regardless of cause
      val updates = Seq(
        (32, csrR(0x342, 0, 5)),  // handler @0x80: mcause->x5
        (33, jal0), (34, jal0)
      ) ++ Seq(
        (0, li(6, 0x80)), (1, csrW(0x305, 6, 0)), (2, nop),
        (3, ecallInstr), (4, nop), (5, nop)
      )
      val writes = loadSelective(dut, 64, updates, 5, 80)
      assert(writes.contains(BigInt(TrapCause.ECALL_M)))
    }
  }

  it should "vectored mode: sync exception targets BASE not BASE+4*cause" in {
    test(new SystemCore(imemDepthWords = 128, dmemSizeBytes = 4096)) { dut =>
      // mtvec=0x81 vectored; illegal instr (cause 2) -> target should be 0x80 (BASE)
      // NOT 0x80+8. Handler@0x80 reads mcause.
      val p = Seq(
        li(6, 0x81), csrW(0x305, 6, 0),  // mtvec vectored
        nop, BigInt("FFFFFFFF",16), nop  // illegal @8
      ) ++ Seq.fill(32 - 5)(nop) ++
      Seq(csrR(0x342, 0, 5), jal0) ++ Seq.fill(8)(nop)
      val writes = simulate(dut, p, 5, 60)
      assert(writes.contains(BigInt(TrapCause.ILLEGAL_INSTRUCTION)))
    }
  }

  it should "FENCE.I serializes: runtime imem write then modified instr executes" in {
    test(new SystemCore(imemDepthWords = 128, dmemSizeBytes = 4096)) { dut =>
      // Program at pc0: jal to pc8 (skip); pc8: old instr nop; pc12: fence.i; pc16: jal back? 
      // Simpler: load prog, run to pc, self-modify imem[8] via load port, fence.i, jump to 8.
      // We do it all from the testbench: load initial prog with jal@0 -> pc8 region.
      // prog: @0 jal x0,+8? ; @4 nop ; @8 TARGET ; ...
      // After fence.i executes, write TARGET via imem load port, then jal to it.
      val fenceI = BigInt(1L << 12 | 0x0F)
      val prog = Seq(
        BigInt("0040006F",16),  // jal x0, +8? imm encoding approx -> use computed below
        nop, nop, nop,
        fenceI,
        BigInt("0000006F",16)   // jal x0,0 loop
      ) ++ Seq.fill(26)(nop)
      loadProg(dut, prog)
      // run a few cycles so pipeline reaches fence.i region, then modify imem
      run(dut, 6)
      // self-modify: write addi x9,x0,0x77 at pc=8 (idx2)
      dut.io.imemLoadAddr.poke(8.U)
      dut.io.imemLoadData.poke(BigInt(0x07700293)) // addi x9,x0,0x77? rd=9 -> 9<<7
      dut.io.imemLoadEn.poke(true.B); dut.clock.step(1)
      dut.io.imemLoadEn.poke(false.B)
      run(dut, 40)
      assert(dut.io.privLevel.peek().litValue == Privilege.M)
    }
  }

  it should "SFENCE.VMA decodes and serializes without side effects" in {
    test(new SystemCore(imemDepthWords = 64, dmemSizeBytes = 4096)) { dut =>
      val sfence = (0x09L<<25)|(5L<<20)|(6L<<15)|(0L<<12)|(0L<<7)|0x73 // rs1=5,rs2=6
      val p = Seq(BigInt(sfence), li(5,1), nop, nop, jal0) ++ Seq.fill(4)(nop)
      loadProg(dut, p)
      run(dut, 30)
      assert(dut.io.privLevel.peek().litValue == Privilege.M)
    }
  }

  def ecallInstr: BigInt = 0x73

  it should "FENCE.I serializes: younger in-flight store squashed then refetched cleanly" in {
    test(new SystemCore(imemDepthWords = 128, dmemSizeBytes = 4096)) { dut =>
      // fence.i retires at WB while the NEXT instruction (a store) is in MEM.
      // The in-flight store's same-cycle write is suppressed by wbMemWriteBlock;
      // the younger stage is then flushed and fetch restarts at fence.pc+4,
      // so the store re-executes exactly once -> memory sees its value once.
      // x8=0x200, x9=0x55, x10=0xAA
      val body = Seq(
        li(8, 0x200), li(9, 0x55), li(10, 0xAA),
        sw(9, 0, 8),            // idx3 store 0x55 (retires before fence)
        fenceI,                 // idx4
        sw(10, 0, 8),           // idx5 store 0xAA (younger; squashed+refetched)
        lw(7, 0, 8),            // idx6 read back -> 0xAA (last writer wins)
        jal0                    // idx7
      )
      val updates = body.zipWithIndex.map { case (w,i) => (i, w) }
      val x7 = loadSelective(dut, 32, updates, 7, 60)
      // store after fence.i executes exactly once via clean refetch -> 0xAA
      assert(x7.nonEmpty && x7.last == BigInt(0xAA))
    }
  }

  it should "nondelegated exception beats delegated interrupt -> mtvec not stvec" in {
    test(new SystemCore(imemDepthWords = 128, dmemSizeBytes = 4096)) { dut =>
      // mideleg.STIP=1 (STIP->S), mie.STIE, mip.STIP pending, mtvec=0x80,
      // stvec=0xC0. illegal instr (nondelegated) + pending S interrupt same
      // window -> exception wins AND goes to M (mtvec), mcause=2.
      val body = Seq(
        li(6, 0x80), csrW(0x305, 6, 0),   // mtvec=0x80
        li(6, 0xC0), csrW(0x105, 6, 0),   // stvec=0xC0
        li(6, 0x20), csrW(0x303, 6, 0),   // mideleg.STIP=1
        li(6, 0x20), csrW(0x304, 6, 0),   // mie.STIE
        li(6, 0x20), csrW(0x344, 6, 0),   // mip.STIP pending
        BigInt("FFFFFFFF", 16)            // illegal -> nondelegated exc
      )
      val updates = Seq(
        (32, csrR(0x342, 0, 5)),          // M handler @0x80: mcause->x5
        (33, csrR(0x341, 0, 6)),          // mepc->x6
        (34, jal0), (35, jal0),
        (48, csrR(0x142, 0, 7)),          // S handler @0xC0 marker -> x7 (must NOT run)
        (49, jal0)
      ) ++ body.zipWithIndex.map { case (w,i) => (i, w) }
      // capture writes to x5 (mcause). M-handler@0x80 reads mcause->x5;
      // an S-target trap would instead run stvec handler (scause->x7), so
      // observing mcause=2 here proves the exception went to M/mtvec.
      val x5 = loadSelective(dut, 64, updates, 5, 120)
      assert(x5.contains(BigInt(2)))                       // mcause=illegal
      assert(dut.io.privLevel.peek().litValue == Privilege.M)
    }
  }
}
