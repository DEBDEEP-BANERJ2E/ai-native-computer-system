package objective01.mux

import chisel3._
import chisel3.util._

class Mux4(width: Int) extends Module {
  require(width > 0, "Mux4 width must be positive")

  val io = IO(new Bundle {
    val in0 = Input(UInt(width.W))
    val in1 = Input(UInt(width.W))
    val in2 = Input(UInt(width.W))
    val in3 = Input(UInt(width.W))
    val select = Input(UInt(2.W))
    val y = Output(UInt(width.W))
  })

  io.y := MuxLookup(io.select, io.in0)(Seq(
    1.U -> io.in1,
    2.U -> io.in2,
    3.U -> io.in3
  ))
}