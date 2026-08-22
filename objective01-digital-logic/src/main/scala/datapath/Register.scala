package objective01.datapath

import chisel3._

class Register(val width: Int) extends Module {
  require(width > 0, "Register width must be positive")

  val io = IO(new Bundle {
    val enable = Input(Bool())
    val d = Input(UInt(width.W))
    val q = Output(UInt(width.W))
  })

  val value = RegInit(0.U(width.W))
  when (io.enable) {
    value := io.d
  }
  io.q := value
}