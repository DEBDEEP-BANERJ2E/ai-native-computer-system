package objective01.arithmetic

import chisel3._
import chisel3.util._

class BoothWallaceMultiplier(val width: Int) extends Module {
  require(width > 0 && width % 2 == 0, "BoothWallaceMultiplier width must be positive and even")

  private val productWidth = 2 * width
  private val accumulatorWidth = productWidth + 2
  private val groups = width / 2

  val io = IO(new Bundle {
    val a = Input(UInt(width.W))
    val b = Input(UInt(width.W))
    val product = Output(UInt(productWidth.W))
  })

  val signedA = Cat(Fill(accumulatorWidth - width, io.a(width - 1)), io.a).asSInt
  val extendedB = Cat(Fill(2, io.b(width - 1)), io.b, 0.U(1.W))
  val wallace = Module(new WallaceTree(accumulatorWidth, groups))

  for (group <- 0 until groups) {
    val code = extendedB(2 * group + 2, 2 * group)
    val multiple = Wire(SInt(accumulatorWidth.W))
    multiple := MuxLookup(code, 0.S(accumulatorWidth.W))(Seq(
      1.U -> signedA,
      2.U -> signedA,
      3.U -> (signedA << 1).asSInt,
      4.U -> ((-signedA) << 1).asSInt,
      5.U -> (-signedA).asSInt,
      6.U -> (-signedA).asSInt
    ))
    val shifted = Wire(UInt(accumulatorWidth.W))
    shifted := (multiple << (2 * group)).asUInt
    wallace.io.inputs(group) := shifted
  }

  io.product := (wallace.io.result)(productWidth - 1, 0)
}