package objective01.mux

import chisel3._

class Mux2(width: Int) extends Module {
  require(width > 0, "Mux2 width must be positive")

  val io = IO(new Bundle {
    val a = Input(UInt(width.W))
    val b = Input(UInt(width.W))
    val select = Input(Bool())
    val y = Output(UInt(width.W))
  })

  io.y := Mux(io.select, io.b, io.a)
}