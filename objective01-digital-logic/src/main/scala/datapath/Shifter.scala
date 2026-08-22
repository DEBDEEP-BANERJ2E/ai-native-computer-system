package objective01.datapath

import chisel3._
import chisel3.util._

object ShiftOperation {
  val LEFT = 0.U(2.W)
  val LOGICAL_RIGHT = 1.U(2.W)
  val ARITHMETIC_RIGHT = 2.U(2.W)
}

class Shifter(val width: Int) extends Module {
  require(width >= 2, "Shifter width must be at least 2")

  private val amountWidth = log2Ceil(width)
  val io = IO(new Bundle {
    val input = Input(UInt(width.W))
    val amount = Input(UInt(amountWidth.W))
    val operation = Input(UInt(2.W))
    val output = Output(UInt(width.W))
  })

  io.output := MuxLookup(io.operation, 0.U(width.W))(Seq(
    ShiftOperation.LEFT -> (io.input << io.amount),
    ShiftOperation.LOGICAL_RIGHT -> (io.input >> io.amount),
    ShiftOperation.ARITHMETIC_RIGHT -> (io.input.asSInt >> io.amount).asUInt
  ))
}