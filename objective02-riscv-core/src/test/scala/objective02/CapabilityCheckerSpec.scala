package objective02

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import objective02.capability.{CapabilityChecker, CapabilityLite, CapabilityPerms}
import objective02.system.SecurityReason

class CapabilityCheckerSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "CapabilityChecker"

  it should "allow accesses strictly within end-exclusive bounds" in {
    test(new CapabilityChecker) { dut =>
      // Base = 100, Length = 16 (range 100 to 115 inclusive, top = 116)
      dut.io.cap.tag.poke(true.B)
      dut.io.cap.base.poke(100.U)
      dut.io.cap.length.poke(16.U)
      dut.io.cap.perms.poke(CapabilityPerms.RW)
      dut.io.cap.offset.poke(0.U)

      // Exact first byte (100, size 1)
      dut.io.effectiveAddress.poke(100.U)
      dut.io.accessSize.poke(1.U)
      dut.io.isRead.poke(true.B)
      dut.io.isWrite.poke(false.B)
      dut.io.allow.expect(true.B)
      dut.io.violation.expect(false.B)

      // Exact last valid byte (115, size 1)
      dut.io.effectiveAddress.poke(115.U)
      dut.io.accessSize.poke(1.U)
      dut.io.allow.expect(true.B)

      // Word ending exactly at top (112, size 4: bytes 112, 113, 114, 115)
      dut.io.effectiveAddress.poke(112.U)
      dut.io.accessSize.poke(4.U)
      dut.io.allow.expect(true.B)

      // Word crossing top by one byte (113, size 4: bytes 113..116 -> 116 is out of bounds!)
      dut.io.effectiveAddress.poke(113.U)
      dut.io.accessSize.poke(4.U)
      dut.io.allow.expect(false.B)
      dut.io.violation.expect(true.B)
      dut.io.reason.expect(SecurityReason.BOUNDS)
    }
  }

  it should "handle 33-bit boundary wrap and overflow edge cases safely" in {
    test(new CapabilityChecker) { dut =>
      // base = 0xFFFFFFFC, length = 4 -> top = 0x1_00000000 in 33-bit arithmetic!
      dut.io.cap.tag.poke(true.B)
      dut.io.cap.base.poke("hFFFFFFFC".U)
      dut.io.cap.length.poke(4.U)
      dut.io.cap.perms.poke(CapabilityPerms.RW)
      dut.io.cap.offset.poke(0.U)

      // Word at 0xFFFFFFFC (size 4: FFFFFFFC, FFFFFFFD, FFFFFFFE, FFFFFFFF)
      dut.io.effectiveAddress.poke("hFFFFFFFC".U)
      dut.io.accessSize.poke(4.U)
      dut.io.isRead.poke(true.B)
      dut.io.isWrite.poke(false.B)
      dut.io.allow.expect(true.B)

      // Address below base (0xFFFFFFFB)
      dut.io.effectiveAddress.poke("hFFFFFFFB".U)
      dut.io.accessSize.poke(1.U)
      dut.io.allow.expect(false.B)
      dut.io.reason.expect(SecurityReason.BOUNDS)

      // Address at 0xFFFFFFFF with size 4 -> accessEnd = 0x1_00000003 > top -> denied!
      dut.io.effectiveAddress.poke("hFFFFFFFF".U)
      dut.io.accessSize.poke(4.U)
      dut.io.allow.expect(false.B)
      dut.io.reason.expect(SecurityReason.BOUNDS)
    }
  }

  it should "strictly enforce reason precedence: Tag -> Bounds -> Permissions" in {
    test(new CapabilityChecker) { dut =>
      // Tag invalid + Bounds invalid + Perm missing -> reason MUST be INVALID_CAPABILITY
      dut.io.cap.tag.poke(false.B)
      dut.io.cap.base.poke(100.U)
      dut.io.cap.length.poke(10.U)
      dut.io.cap.perms.poke(CapabilityPerms.NONE)
      dut.io.effectiveAddress.poke(500.U)
      dut.io.accessSize.poke(4.U)
      dut.io.isRead.poke(false.B)
      dut.io.isWrite.poke(true.B)
      dut.io.allow.expect(false.B)
      dut.io.reason.expect(SecurityReason.INVALID_CAPABILITY)

      // Tag valid + Bounds invalid + Perm missing -> reason MUST be BOUNDS
      dut.io.cap.tag.poke(true.B)
      dut.io.reason.expect(SecurityReason.BOUNDS)

      // Tag valid + Bounds valid + Perm missing -> reason MUST be WRITE_PERMISSION
      dut.io.effectiveAddress.poke(100.U)
      dut.io.accessSize.poke(4.U)
      dut.io.reason.expect(SecurityReason.WRITE_PERMISSION)

      // Tag valid + Bounds valid + Read missing on read request -> READ_PERMISSION
      dut.io.isWrite.poke(false.B)
      dut.io.isRead.poke(true.B)
      dut.io.reason.expect(SecurityReason.READ_PERMISSION)
    }
  }
}
