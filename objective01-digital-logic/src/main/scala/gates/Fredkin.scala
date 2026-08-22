package objective01.gates

import chisel3._

class Fredkin extends Module {
  val io = IO(new Bundle {
    val control = Input(Bool())
    val b = Input(Bool())
    val c = Input(Bool())
    val p = Output(Bool())
    val q = Output(Bool())
    val r = Output(Bool())
  })

  io.p := io.control
  io.q := Mux(io.control, io.c, io.b)
  io.r := Mux(io.control, io.b, io.c)
}