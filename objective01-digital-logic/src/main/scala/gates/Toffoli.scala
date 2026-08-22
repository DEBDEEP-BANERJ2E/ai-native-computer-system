package objective01.gates

import chisel3._

class Toffoli extends Module {
  val io = IO(new Bundle {
    val a = Input(Bool())
    val b = Input(Bool())
    val c = Input(Bool())
    val p = Output(Bool())
    val q = Output(Bool())
    val r = Output(Bool())
  })

  io.p := io.a
  io.q := io.b
  io.r := io.c ^ (io.a && io.b)
}