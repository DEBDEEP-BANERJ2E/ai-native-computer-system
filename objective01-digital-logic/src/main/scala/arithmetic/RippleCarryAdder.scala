package objective01.arithmetic

import chisel3._

class FullAdder extends Module {
  val io = IO(new Bundle {
    val a = Input(Bool())
    val b = Input(Bool())
    val carryIn = Input(Bool())
    val sum = Output(Bool())
    val carryOut = Output(Bool())
  })

  io.sum := io.a ^ io.b ^ io.carryIn
  io.carryOut := (io.a && io.b) || (io.carryIn && (io.a ^ io.b))
}

class RippleCarryAdder(val width: Int) extends Module {
  require(width > 0, "RippleCarryAdder width must be positive")

  val io = IO(new Bundle {
    val a = Input(UInt(width.W))
    val b = Input(UInt(width.W))
    val carryIn = Input(Bool())
    val sum = Output(UInt(width.W))
    val carryOut = Output(Bool())
  })

  val carries = Wire(Vec(width + 1, Bool()))
  val sums = Wire(Vec(width, Bool()))
  carries(0) := io.carryIn

  for (bit <- 0 until width) {
    val fullAdder = Module(new FullAdder)
    fullAdder.io.a := io.a(bit)
    fullAdder.io.b := io.b(bit)
    fullAdder.io.carryIn := carries(bit)
    sums(bit) := fullAdder.io.sum
    carries(bit + 1) := fullAdder.io.carryOut
  }

  io.sum := sums.asUInt
  io.carryOut := carries(width)
}