package objective02.capability

import chisel3._
import chisel3.util._

object CapabilityPerms {
  val NONE  = 0.U(3.W)
  val READ  = 1.U(3.W) // Bit 0
  val WRITE = 2.U(3.W) // Bit 1
  val EXEC  = 4.U(3.W) // Bit 2 (reserved)
  val RW    = 3.U(3.W)
  val RWX   = 7.U(3.W)
}

class CapabilityLite extends Bundle {
  val tag    = Bool()
  val base   = UInt(32.W)
  val length = UInt(32.W)
  val perms  = UInt(3.W)
  val offset = UInt(32.W)
}

object CapabilityLite {
  def nullCapability(): CapabilityLite = {
    val cap = Wire(new CapabilityLite)
    cap.tag    := false.B
    cap.base   := 0.U(32.W)
    cap.length := 0.U(32.W)
    cap.perms  := 0.U(3.W)
    cap.offset := 0.U(32.W)
    cap
  }
}
