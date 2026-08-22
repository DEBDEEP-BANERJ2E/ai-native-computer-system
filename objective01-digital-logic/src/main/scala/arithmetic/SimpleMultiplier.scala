package objective01.arithmetic

import chisel3._

class SimpleMultiplier(val width: Int) extends Module {
  require(width > 0, "SimpleMultiplier width must be positive")

  private val productWidth = 2 * width
  val io = IO(new Bundle {
    val a = Input(UInt(width.W))
    val b = Input(UInt(width.W))
    val product = Output(UInt(productWidth.W))
  })

  val partialProducts = (0 until width).map { bit =>
    val shifted = (io.a << bit).pad(productWidth)
    Mux(io.b(bit), shifted, 0.U(productWidth.W))
  }
  val accumulated = partialProducts.foldLeft(0.U(productWidth.W)) {
    (sum, partial) => (sum +& partial)(productWidth - 1, 0)
  }

  io.product := accumulated
}