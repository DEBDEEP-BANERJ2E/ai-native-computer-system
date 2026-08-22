package objective01.arithmetic

import chisel3._

class CarryLookaheadAdder(val width: Int) extends Module {
  require(width > 0, "CarryLookaheadAdder width must be positive")

  val io = IO(new Bundle {
    val a = Input(UInt(width.W))
    val b = Input(UInt(width.W))
    val carryIn = Input(Bool())
    val sum = Output(UInt(width.W))
    val carryOut = Output(Bool())
  })

  val propagate = Wire(Vec(width, Bool()))
  val generate = Wire(Vec(width, Bool()))
  val carries = Wire(Vec(width + 1, Bool()))
  val sums = Wire(Vec(width, Bool()))

  carries(0) := io.carryIn
  for (bit <- 0 until width) {
    propagate(bit) := io.a(bit) ^ io.b(bit)
    generate(bit) := io.a(bit) && io.b(bit)

    // Expand each carry from all lower-order generate/propagate terms.
    val generatedCarryTerms = (0 until bit).map { source =>
      ((source + 1) to bit).map(propagate).reduce(_ && _) && generate(source)
    }
    val inputCarryTerm = (0 to bit).map(propagate).reduce(_ && _) && carries(0)
    val carryTerms = Seq(generate(bit)) ++ generatedCarryTerms ++ Seq(inputCarryTerm)
    carries(bit + 1) := carryTerms.reduce(_ || _)
    sums(bit) := propagate(bit) ^ carries(bit)
  }

  io.sum := sums.asUInt
  io.carryOut := carries(width)
}