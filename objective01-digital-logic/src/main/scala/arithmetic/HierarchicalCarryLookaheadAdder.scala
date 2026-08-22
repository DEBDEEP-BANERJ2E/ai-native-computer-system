package objective01.arithmetic

import chisel3._

class CLA4 extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(4.W))
    val b = Input(UInt(4.W))
    val carryIn = Input(Bool())
    val sum = Output(UInt(4.W))
    val carryOut = Output(Bool())
    val groupPropagate = Output(Bool())
    val groupGenerate = Output(Bool())
  })

  val propagate = Wire(Vec(4, Bool()))
  val generate = Wire(Vec(4, Bool()))
  val carries = Wire(Vec(5, Bool()))
  val sums = Wire(Vec(4, Bool()))

  carries(0) := io.carryIn
  for (bit <- 0 until 4) {
    propagate(bit) := io.a(bit) ^ io.b(bit)
    generate(bit) := io.a(bit) && io.b(bit)
    val generatedTerms = (0 until bit).map { source =>
      ((source + 1) to bit).map(propagate).reduce(_ && _) && generate(source)
    }
    val inputCarryTerm = (0 to bit).map(propagate).reduce(_ && _) && carries(0)
    carries(bit + 1) := (Seq(generate(bit)) ++ generatedTerms ++ Seq(inputCarryTerm)).reduce(_ || _)
    sums(bit) := propagate(bit) ^ carries(bit)
  }

  io.sum := sums.asUInt
  io.carryOut := carries(4)
  io.groupPropagate := propagate.reduce(_ && _)
  io.groupGenerate := generate(3) ||
    (propagate(3) && generate(2)) ||
    (propagate(3) && propagate(2) && generate(1)) ||
    (propagate(3) && propagate(2) && propagate(1) && generate(0))
}

class HierarchicalCarryLookaheadAdder(val width: Int) extends Module {
  require(width > 0 && width % 4 == 0, "Hierarchical CLA width must be a positive multiple of four")

  private val blocks = width / 4
  val io = IO(new Bundle {
    val a = Input(UInt(width.W))
    val b = Input(UInt(width.W))
    val carryIn = Input(Bool())
    val sum = Output(UInt(width.W))
    val carryOut = Output(Bool())
  })

  val groupPropagate = Wire(Vec(blocks, Bool()))
  val groupGenerate = Wire(Vec(blocks, Bool()))
  val blockCarries = Wire(Vec(blocks + 1, Bool()))
  val sumBits = Wire(Vec(width, Bool()))
  blockCarries(0) := io.carryIn

  for (block <- 0 until blocks) {
    val low = block * 4
    val high = low + 3
    val cla4 = Module(new CLA4)
    cla4.io.a := io.a(high, low)
    cla4.io.b := io.b(high, low)
    cla4.io.carryIn := blockCarries(block)
    groupPropagate(block) := cla4.io.groupPropagate
    groupGenerate(block) := cla4.io.groupGenerate
    for (bit <- 0 until 4) {
      sumBits(low + bit) := cla4.io.sum(bit)
    }

    val generatedTerms = (0 until block).map { source =>
      ((source + 1) to block).map(groupPropagate).reduce(_ && _) && groupGenerate(source)
    }
    val inputCarryTerm = (0 to block).map(groupPropagate).reduce(_ && _) && blockCarries(0)
    blockCarries(block + 1) := (Seq(groupGenerate(block)) ++ generatedTerms ++ Seq(inputCarryTerm)).reduce(_ || _)
  }

  io.sum := sumBits.asUInt
  io.carryOut := blockCarries(blocks)
}