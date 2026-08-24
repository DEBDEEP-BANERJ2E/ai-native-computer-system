package objective02.system

import chisel3._

object AccessType {
  val READ                 = 0.U(2.W)
  val WRITE                = 1.U(2.W)
  val EXECUTE              = 2.U(2.W)
  val CAPABILITY_OPERATION = 3.U(2.W)
}

object SecurityReason {
  val NONE               = 0.U(4.W)
  val INVALID_CAPABILITY = 1.U(4.W)
  val BOUNDS             = 2.U(4.W)
  val READ_PERMISSION    = 3.U(4.W)
  val WRITE_PERMISSION   = 4.U(4.W)
  val EXECUTE_PERMISSION = 5.U(4.W)
  val MONOTONICITY       = 6.U(4.W)
}

class SecurityViolationEvent extends Bundle {
  val valid      = Bool()
  val pc         = UInt(32.W)
  val address    = UInt(32.W)
  val accessType = UInt(2.W)
  val reason     = UInt(4.W)
  val context    = UInt(32.W)
}
