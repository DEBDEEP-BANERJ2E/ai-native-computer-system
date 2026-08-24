package objective02

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import objective02.capability.{CapabilityLite, CapabilityPerms, CapabilityRegFile}

class CapabilityRegFileSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "CapabilityRegFile"

  it should "initialize c0 to NULL and c1/c2 to hardware root capabilities" in {
    test(new CapabilityRegFile) { dut =>
      // c0 check
      dut.io.raddr1.poke(0.U)
      dut.io.rdata1.tag.expect(false.B)
      dut.io.rdata1.base.expect(0.U)
      dut.io.rdata1.length.expect(0.U)
      dut.io.rdata1.perms.expect(0.U)

      // c1 Data Memory Root check
      dut.io.raddr1.poke(1.U)
      dut.io.rdata1.tag.expect(true.B)
      dut.io.rdata1.base.expect("h00000000".U)
      dut.io.rdata1.length.expect("h00001000".U)
      dut.io.rdata1.perms.expect(CapabilityPerms.RW)

      // c2 System MMIO Root check
      dut.io.raddr1.poke(2.U)
      dut.io.rdata1.tag.expect(true.B)
      dut.io.rdata1.base.expect("h80000000".U)
      dut.io.rdata1.length.expect("h00010000".U)
      dut.io.rdata1.perms.expect(CapabilityPerms.RW)

      // c3 uninitialized check
      dut.io.raddr1.poke(3.U)
      dut.io.rdata1.tag.expect(false.B)
    }
  }

  it should "discard writes to c0 and maintain permanent NULL state" in {
    test(new CapabilityRegFile) { dut =>
      dut.io.wen.poke(true.B)
      dut.io.waddr.poke(0.U)
      dut.io.wdata.tag.poke(true.B)
      dut.io.wdata.base.poke("h12345678".U)
      dut.io.wdata.length.poke("h00000100".U)
      dut.io.wdata.perms.poke(CapabilityPerms.RW)
      dut.io.wdata.offset.poke(0.U)
      dut.clock.step(1)

      dut.io.wen.poke(false.B)
      dut.io.raddr1.poke(0.U)
      dut.io.rdata1.tag.expect(false.B)
      dut.io.rdata1.base.expect(0.U)
    }
  }

  it should "write to c3..c7 at WB and support same-cycle WB-to-ID bypass" in {
    test(new CapabilityRegFile) { dut =>
      // Same-cycle bypass check
      dut.io.wen.poke(true.B)
      dut.io.waddr.poke(3.U)
      dut.io.wdata.tag.poke(true.B)
      dut.io.wdata.base.poke("h00000200".U)
      dut.io.wdata.length.poke(32.U)
      dut.io.wdata.perms.poke(CapabilityPerms.READ)
      dut.io.wdata.offset.poke(4.U)

      dut.io.raddr1.poke(3.U)
      // Combinationally bypassed!
      dut.io.rdata1.tag.expect(true.B)
      dut.io.rdata1.base.expect("h00000200".U)
      dut.io.rdata1.length.expect(32.U)
      dut.io.rdata1.perms.expect(CapabilityPerms.READ)
      dut.io.rdata1.offset.expect(4.U)

      dut.clock.step(1)
      dut.io.wen.poke(false.B)

      // Registered readback
      dut.io.raddr1.poke(3.U)
      dut.io.rdata1.tag.expect(true.B)
      dut.io.rdata1.base.expect("h00000200".U)
    }
  }
}
