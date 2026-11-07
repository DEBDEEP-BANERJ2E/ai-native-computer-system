package objective03.kernel

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import objective03.core.SystemCore
import objective03.csr.{Privilege, TrapCause}

// Stage B B1: privilege bootstrap proof.
//   M boot -> mtvec/mepc/MPP=S -> MRET -> S -> ECALL -> (medeleg[8]=0) -> M handler
//   handler reads mcause=9 (ECALL_S), mepc=addr of the S ecall.
class KernelBootSpec extends AnyFlatSpec with ChiselScalatestTester {

  // CSR addrs: mtvec=0x305 mepc=0x341 mstatus=0x300 mcause=0x342 medeleg=0x302
  //            stvec=0x105 sepc=0x141 sstatus=0x100 scause=0x142 mideleg=0x303
  def csrW(c:Int,r:Int,d:Int)=BigInt(c)<<20|BigInt(r)<<15|1L<<12|BigInt(d)<<7|0x73
  def csrR(c:Int,r:Int,d:Int)=BigInt(c)<<20|BigInt(r)<<15|2L<<12|BigInt(d)<<7|0x73
  def csrI(c:Int,imm:Int,d:Int)=BigInt(c)<<20|BigInt(imm&0x1F)<<15|6L<<12|BigInt(d)<<7|0x73
  def li(rd:Int,imm:Int)=BigInt(imm&0xFFF)<<20|BigInt(rd)<<7|0x13
  def lui(rd:Int,imm20:Int)=BigInt(imm20&0xFFFFF)<<12|BigInt(rd)<<7|0x37
  def addi(rd:Int,rs1:Int,imm:Int)=BigInt(imm&0xFFF)<<20|BigInt(rs1)<<15|BigInt(rd)<<7|0x13
  def nop:BigInt=0x13
  def jal0:BigInt=BigInt("0000006F",16)
  def mret:BigInt=BigInt("30200073",16)
  def sret:BigInt=BigInt("10200073",16)
  def ecall:BigInt=0x73

  // 32-bit constant materialization (handles values li() can't, e.g. 0x800).
  // Returns 1 or 2 instructions writing rd. Caller splices into program.
  def li32(rd:Int, v:Int): Seq[BigInt] = {
    val hi=(v+0x800)>>12; val lo=v-(hi<<12)
    if(lo>=0x800||lo< -0x800) Seq(lui(rd,hi+1), addi(rd,rd,lo-0x1000))
    else Seq(lui(rd,hi), addi(rd,rd,lo))
  }

  // Safe ordered load: jal0-preload all slots, then apply (idx->instr) updates
  // IN THE GIVEN ORDER so handlers/entries are resident before boot runs.
  def loadOrdered(dut:SystemCore, size:Int, updates:Seq[(Int,BigInt)]):Unit={
    (0 until size).foreach{i=>
      dut.io.imemLoadAddr.poke((i*4).U); dut.io.imemLoadData.poke(jal0.U)
      dut.io.imemLoadEn.poke(true.B); dut.clock.step(1)
    }
    updates.foreach{case(i,w)=>
      dut.io.imemLoadAddr.poke((i*4).U); dut.io.imemLoadData.poke(w.U)
      dut.io.imemLoadEn.poke(true.B); dut.clock.step(1)
    }
    dut.io.imemLoadEn.poke(false.B)
    dut.io.imemLoadAddr.poke(0.U); dut.io.imemLoadData.poke(0.U)
  }

  def captureCommits(dut:SystemCore,n:Int):Seq[(BigInt,BigInt,BigInt)]={
    val buf=scala.collection.mutable.ArrayBuffer[(BigInt,BigInt,BigInt)]()
    var i=0
    while(i<n){dut.clock.step(1)
      if(dut.io.commit.valid.peek().litValue==1&&dut.io.commit.regWrite.peek().litValue==1&&dut.io.commit.rd.peek().litValue!=0)
        buf+=((dut.io.commit.pc.peek().litValue,dut.io.commit.rd.peek().litValue,dut.io.commit.writeData.peek().litValue))
      i+=1}
    buf.toSeq
  }
  def rdWrites(cs:Seq[(BigInt,BigInt,BigInt)],rd:Int)=cs.collect{case(pc,r,d)if r==rd=>d}

  behavior of "Stage B B1 privilege bootstrap"

  it should "M->S->ECALL->M: mcause=9 mepc=S ecall pc" in {
    test(new SystemCore(imemDepthWords=128,dmemSizeBytes=4096)){dut=>
      // Layout (idx = pc/4):
      //   boot @0.. : mtvec=0x100, mepc=0x40, mstatus.MPP=S, mret
      //   S entry @16 (0x40): ecall  -> nondelegated -> M
      //   M handler @64 (0x100): csrrs x5,mcause ; csrrs x6,mepc ; jal0
      // Load order: handler, then S entry, then boot LAST.
      val handler = Seq((64,csrR(0x342,0,5)),(65,csrR(0x341,0,6)),(66,jal0),(67,jal0))
      val sentry  = Seq((16,ecall))
      // boot: li x6,0x100; csrw mtvec ; li x6,0x40; csrw mepc ;
      //       li32 x6,0x800 (MPP=S); csrw mstatus ; mret
      val boot = Seq(
        li(6,0x100), csrW(0x305,6,0),
        li(6,0x40),  csrW(0x341,6,0),
      ) ++ li32(6,0x800) ++ Seq(csrW(0x300,6,0), mret)
      val updates = handler ++ sentry ++ boot.zipWithIndex.map{case(w,i)=>(i,w)}
      loadOrdered(dut,96,updates)
      val cs=captureCommits(dut,240)
      // mcause = ECALL_S = 9 ; mepc = 0x40 (the S ecall)
      assert(rdWrites(cs,5).contains(BigInt(9)))
      val mepc=rdWrites(cs,6).lastOption.map(_.toLong&0xffffffffL).getOrElse(-1L)
      assert(mepc==0x40)
      assert(dut.io.privLevel.peek().litValue==Privilege.M)
    }
  }
}
