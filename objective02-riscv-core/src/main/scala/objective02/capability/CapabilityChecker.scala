package objective02.capability

import chisel3._
import chisel3.util._
import objective02.system.SecurityReason

class CapabilityCheckerIO extends Bundle {
  val cap              = Input(new CapabilityLite)
  val effectiveAddress = Input(UInt(32.W))
  val accessSize       = Input(UInt(3.W)) // 1 (byte), 2 (halfword), 4 (word)
  val isRead           = Input(Bool())
  val isWrite          = Input(Bool())

  val allow            = Output(Bool())
  val violation        = Output(Bool())
  val reason           = Output(UInt(4.W))
}

class CapabilityChecker extends Module {
  val io = IO(new CapabilityCheckerIO)

  // 33-bit widened unsigned bounds calculation to prevent wraparound
  val base33      = Cat(0.U(1.W), io.cap.base)
  val top33       = base33 +& Cat(0.U(1.W), io.cap.length)
  val addr33      = Cat(0.U(1.W), io.effectiveAddress)
  val accessEnd33 = addr33 +& Cat(0.U(30.W), io.accessSize)

  val tagValid = io.cap.tag
  val boundsOk = (addr33 >= base33) && (accessEnd33 <= top33)
  val permOk   = Mux(io.isWrite, io.cap.perms(1), Mux(io.isRead, io.cap.perms(0), true.B))

  val allow = tagValid && boundsOk && permOk
  io.allow     := allow
  io.violation := !allow

  // Reason precedence: Tag -> Bounds -> Permissions
  io.reason := Mux(!tagValid, SecurityReason.INVALID_CAPABILITY,
               Mux(!boundsOk, SecurityReason.BOUNDS,
               Mux(!permOk,   Mux(io.isWrite, SecurityReason.WRITE_PERMISSION, SecurityReason.READ_PERMISSION),
                              SecurityReason.NONE)))
}
