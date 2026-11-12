package objective03

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import objective03.csr._
import objective03.csr.CSR._
import objective03.csr.Privilege._

class CSRFileSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "CSRFile"

  it should "start in M-mode with MISA = RV32IMSU" in {
    test(new CSRFile) { dut =>
      dut.io.csrAddr.poke(0.U); dut.io.accessType.poke(0.U)
      dut.io.rs1Data.poke(0.U); dut.io.writeEn.poke(false.B)
      dut.io.probeAddr.poke(0.U); dut.io.probeIsWrite.poke(false.B)
      dut.io.trapEntry.valid.poke(false.B); dut.io.trapReturn.valid.poke(false.B)
      dut.clock.step(1)
      assert(dut.io.privLevel.peek().litValue == Privilege.M)
      // read MISA
      dut.io.csrAddr.poke(CSR.MISA.U); dut.clock.step(1)
      assert(dut.io.rdData.peek().litValue == Misa.RV32IMSU_VALUE)
    }
  }

  it should "allow M-mode CSR write then read" in {
    test(new CSRFile) { dut =>
      dut.io.trapEntry.valid.poke(false.B); dut.io.trapReturn.valid.poke(false.B)
      dut.io.csrAddr.poke(CSR.MSCRATCH.U); dut.io.accessType.poke(1.U)
      dut.io.rs1Data.poke("hdeadbeef".U); dut.io.writeEn.poke(true.B)
      dut.clock.step(1)
      dut.io.writeEn.poke(false.B); dut.io.accessType.poke(0.U)
      dut.clock.step(1)
      assert(dut.io.rdData.peek().litValue == 0xdeadbeefL)
    }
  }

  it should "reject S-mode access to machine CSRs even for reads" in {
    test(new CSRFile) { dut =>
      // enter S via trapEntry
      dut.io.trapEntry.valid.poke(true.B); dut.io.trapEntry.cause.poke(0.U)
      dut.io.trapEntry.tval.poke(0.U); dut.io.trapEntry.epc.poke(0x100.U)
      dut.io.trapEntry.targetPriv.poke(Privilege.S.U(2.W))
      dut.io.trapReturn.valid.poke(false.B)
      dut.io.csrAddr.poke(0.U); dut.io.accessType.poke(0.U)
      dut.io.rs1Data.poke(0.U); dut.io.writeEn.poke(false.B)
      dut.io.probeAddr.poke(0.U); dut.io.probeIsWrite.poke(false.B)
      dut.clock.step(1)
      assert(dut.io.privLevel.peek().litValue == Privilege.S)
      // read mtvec -> illegal
      dut.io.csrAddr.poke(CSR.MTVEC.U); dut.io.accessType.poke(0.U)
      dut.io.writeEn.poke(false.B)
      dut.clock.step(1)
      assert(dut.io.illegal.peek().asUInt.litValue == 1)
      // read mstatus -> illegal
      dut.io.csrAddr.poke(CSR.MSTATUS.U)
      dut.clock.step(1)
      assert(dut.io.illegal.peek().asUInt.litValue == 1)
      // write mepc -> illegal
      dut.io.csrAddr.poke(CSR.MEPC.U); dut.io.accessType.poke(1.U)
      dut.io.writeEn.poke(true.B); dut.io.rs1Data.poke(0x999.U)
      dut.clock.step(1)
      assert(dut.io.illegal.peek().asUInt.litValue == 1)
      // supervisor CSR ok
      dut.io.csrAddr.poke(CSR.SSCRATCH.U); dut.io.accessType.poke(1.U)
      dut.io.rs1Data.poke(0x55.U)
      dut.clock.step(1)
      assert(dut.io.illegal.peek().asUInt.litValue == 0)
    }
  }

  it should "reject writes to read-only MISA" in {
    test(new CSRFile) { dut =>
      dut.io.trapEntry.valid.poke(false.B); dut.io.trapReturn.valid.poke(false.B)
      dut.io.csrAddr.poke(CSR.MISA.U); dut.io.accessType.poke(1.U)
      dut.io.rs1Data.poke(0.U); dut.io.writeEn.poke(true.B)
      dut.clock.step(1)
      assert(dut.io.illegal.peek().asUInt.litValue == 1)
      dut.io.writeEn.poke(false.B); dut.io.accessType.poke(0.U)
      dut.clock.step(1)
      assert(dut.io.rdData.peek().litValue == Misa.RV32IMSU_VALUE)
    }
  }

  it should "alias sstatus to mstatus supervisor bits" in {
    test(new CSRFile) { dut =>
      dut.io.trapEntry.valid.poke(false.B); dut.io.trapReturn.valid.poke(false.B)
      // write mstatus.SIE (bit1)
      dut.io.csrAddr.poke(CSR.MSTATUS.U); dut.io.accessType.poke(1.U)
      dut.io.rs1Data.poke(0x2.U); dut.io.writeEn.poke(true.B)
      dut.clock.step(1)
      dut.io.writeEn.poke(false.B); dut.io.accessType.poke(0.U)
      // read sstatus view -> bit1 visible
      dut.io.csrAddr.poke(CSR.SSTATUS.U)
      dut.clock.step(1)
      assert(dut.io.rdData.peek()(1).litValue == 1)
      // write sstatus.SPP (bit8) -> mstatus bit8
      dut.io.csrAddr.poke(CSR.SSTATUS.U); dut.io.accessType.poke(1.U)
      dut.io.rs1Data.poke(0x100.U); dut.io.writeEn.poke(true.B)
      dut.clock.step(1)
      dut.io.writeEn.poke(false.B); dut.io.accessType.poke(0.U)
      dut.io.csrAddr.poke(CSR.MSTATUS.U)
      dut.clock.step(1)
      assert(dut.io.rdData.peek()(8).litValue == 1)
    }
  }

  it should "gate sie/sip views by mideleg" in {
    test(new CSRFile) { dut =>
      dut.io.trapEntry.valid.poke(false.B); dut.io.trapReturn.valid.poke(false.B)
      // mie.STIE=bit5, mip.STIP=bit5
      dut.io.csrAddr.poke(CSR.MIE.U); dut.io.accessType.poke(1.U)
      dut.io.rs1Data.poke(0x20.U); dut.io.writeEn.poke(true.B)
      dut.clock.step(1)
      dut.io.csrAddr.poke(CSR.MIP.U)
      dut.clock.step(1)
      dut.io.writeEn.poke(false.B); dut.io.accessType.poke(0.U)
      dut.io.csrAddr.poke(CSR.SIP.U)
      dut.clock.step(1)
      assert(dut.io.rdData.peek()(5).litValue == 0) // not delegated yet
      // mideleg bit5
      dut.io.csrAddr.poke(CSR.MIDELEG.U); dut.io.accessType.poke(1.U)
      dut.io.rs1Data.poke(0x20.U); dut.io.writeEn.poke(true.B)
      dut.clock.step(1)
      dut.io.writeEn.poke(false.B); dut.io.accessType.poke(0.U)
      dut.io.csrAddr.poke(CSR.SIP.U)
      dut.clock.step(1)
      assert(dut.io.rdData.peek()(5).litValue == 1)
    }
  }

  it should "trap entry to S saves epc/cause/tval, sets SPP/SPIE, clears SIE" in {
    test(new CSRFile) { dut =>
      dut.io.trapEntry.valid.poke(false.B); dut.io.trapReturn.valid.poke(false.B)
      // preset mstatus bit1 (SIE view)
      dut.io.csrAddr.poke(CSR.MSTATUS.U); dut.io.accessType.poke(1.U)
      dut.io.rs1Data.poke(0x2.U); dut.io.writeEn.poke(true.B)
      dut.clock.step(1)
      dut.io.writeEn.poke(false.B); dut.io.accessType.poke(0.U)
      dut.io.trapEntry.valid.poke(true.B); dut.io.trapEntry.cause.poke(9.U)
      dut.io.trapEntry.tval.poke(0.U); dut.io.trapEntry.epc.poke(0x200.U)
      dut.io.trapEntry.targetPriv.poke(Privilege.S.U(2.W))
      dut.clock.step(1)
      assert(dut.io.privLevel.peek().litValue == Privilege.S)
      dut.io.trapEntry.valid.poke(false.B)
      // read sepc/scause/sstatus
      dut.io.csrAddr.poke(CSR.SEPC.U)
      dut.clock.step(1)
      assert(dut.io.rdData.peek().litValue == 0x200)
      dut.io.csrAddr.poke(CSR.SCAUSE.U)
      dut.clock.step(1)
      assert(dut.io.rdData.peek().litValue == 9)
      dut.io.csrAddr.poke(CSR.SSTATUS.U)
      dut.clock.step(1)
      val s = dut.io.rdData.peek()
      assert(s(8).litValue == 0) // SPP: from M -> 0
      assert(s(5).litValue == 1) // SPIE: old SIE=1
      assert(s(1).litValue == 0) // SIE cleared
    }
  }

  it should "MRET restore privilege from MPP" in {
    test(new CSRFile) { dut =>
      dut.io.trapEntry.valid.poke(false.B); dut.io.trapReturn.valid.poke(false.B)
      dut.io.trapEntry.valid.poke(true.B); dut.io.trapEntry.cause.poke(0.U)
      dut.io.trapEntry.tval.poke(0.U); dut.io.trapEntry.epc.poke(0x300.U)
      dut.io.trapEntry.targetPriv.poke(Privilege.M.U(2.W))
      dut.clock.step(1)
      dut.io.trapEntry.valid.poke(false.B)
      // MPP should be M(3)
      dut.io.csrAddr.poke(CSR.MSTATUS.U); dut.io.accessType.poke(0.U)
      dut.io.rs1Data.poke(0.U); dut.io.writeEn.poke(false.B)
      dut.io.probeAddr.poke(0.U); dut.io.probeIsWrite.poke(false.B)
      dut.clock.step(1)
      assert(dut.io.rdData.peek()(12, 11).litValue == Privilege.M)
      dut.io.trapReturn.valid.poke(true.B); dut.io.trapReturn.toM.poke(true.B)
      dut.clock.step(1)
      assert(dut.io.privLevel.peek().litValue == Privilege.M)
    }
  }

  it should "SRET restore privilege from SPP (M->S->U)" in {
    test(new CSRFile) { dut =>
      dut.io.trapEntry.valid.poke(false.B); dut.io.trapReturn.valid.poke(false.B)
      dut.io.trapEntry.valid.poke(true.B); dut.io.trapEntry.cause.poke(0.U)
      dut.io.trapEntry.tval.poke(0.U); dut.io.trapEntry.epc.poke(0x300.U)
      dut.io.trapEntry.targetPriv.poke(Privilege.S.U(2.W))
      dut.clock.step(1)
      dut.io.trapEntry.valid.poke(false.B)
      assert(dut.io.privLevel.peek().litValue == Privilege.S)
      dut.io.trapReturn.valid.poke(true.B); dut.io.trapReturn.toM.poke(false.B)
      dut.clock.step(1)
      assert(dut.io.privLevel.peek().litValue == Privilege.U)
    }
  }

  it should "reject all CSR access in U-mode" in {
    test(new CSRFile) { dut =>
      dut.io.trapEntry.valid.poke(false.B); dut.io.trapReturn.valid.poke(false.B)
      // M->S->U
      dut.io.trapEntry.valid.poke(true.B); dut.io.trapEntry.cause.poke(0.U)
      dut.io.trapEntry.tval.poke(0.U); dut.io.trapEntry.epc.poke(0x300.U)
      dut.io.trapEntry.targetPriv.poke(Privilege.S.U(2.W))
      dut.clock.step(1)
      dut.io.trapEntry.valid.poke(false.B)
      dut.io.trapReturn.valid.poke(true.B); dut.io.trapReturn.toM.poke(false.B)
      dut.clock.step(1)
      dut.io.trapReturn.valid.poke(false.B)
      assert(dut.io.privLevel.peek().litValue == Privilege.U)
      dut.io.csrAddr.poke(CSR.SSTATUS.U); dut.io.accessType.poke(0.U)
      dut.io.rs1Data.poke(0.U); dut.io.writeEn.poke(false.B)
      dut.io.probeAddr.poke(0.U); dut.io.probeIsWrite.poke(false.B)
      dut.clock.step(1)
      assert(dut.io.illegal.peek().asUInt.litValue == 1)
      dut.io.csrAddr.poke(CSR.MSTATUS.U)
      dut.clock.step(1)
      assert(dut.io.illegal.peek().asUInt.litValue == 1)
    }
  }
}
