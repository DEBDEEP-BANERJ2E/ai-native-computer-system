package objective01.arithmetic

import chisel3._
import chisel3.util.log2Ceil

class CarrySaveCompressor(val width: Int) extends Module {
  require(width > 0, "CarrySaveCompressor width must be positive")

  val io = IO(new Bundle {
    val a = Input(UInt(width.W))
    val b = Input(UInt(width.W))
    val c = Input(UInt(width.W))
    val sum = Output(UInt(width.W))
    val carry = Output(UInt(width.W))
  })

  io.sum := io.a ^ io.b ^ io.c
  io.carry := ((io.a & io.b) | (io.a & io.c) | (io.b & io.c)) << 1
}

class WallaceTree(val width: Int, val rows: Int) extends Module {
  require(width > 0, "WallaceTree width must be positive")
  require(rows >= 3, "WallaceTree requires at least three input rows")

  private val resultWidth = width + log2Ceil(rows)
  val io = IO(new Bundle {
    val inputs = Input(Vec(rows, UInt(width.W)))
    val result = Output(UInt(resultWidth.W))
  })

  var currentRows: Seq[UInt] = io.inputs.map(_.pad(resultWidth))
  while (currentRows.length > 2) {
    val nextRows = scala.collection.mutable.ArrayBuffer[UInt]()
    var index = 0
    while (index + 2 < currentRows.length) {
      val compressor = Module(new CarrySaveCompressor(width))
      compressor.io.a := currentRows(index)
      compressor.io.b := currentRows(index + 1)
      compressor.io.c := currentRows(index + 2)
      nextRows += compressor.io.sum
      nextRows += compressor.io.carry
      index += 3
    }
    while (index < currentRows.length) {
      nextRows += currentRows(index)
      index += 1
    }
    currentRows = nextRows.toSeq
  }

  io.result := currentRows(0) +& currentRows(1)
}