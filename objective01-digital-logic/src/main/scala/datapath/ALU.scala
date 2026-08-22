package objective01.datapath

import chisel3._
import chisel3.util._
import objective01.arithmetic.{BoothWallaceMultiplier, HierarchicalCarryLookaheadAdder}

object ALUOpcode {
  val ADD = 0.U(4.W)
  val SUB = 1.U(4.W)
  val AND = 2.U(4.W)
  val OR = 3.U(4.W)
  val XOR = 4.U(4.W)
  val SLL = 5.U(4.W)
  val SRL = 6.U(4.W)
  val SRA = 7.U(4.W)
  val SLT = 8.U(4.W)
  val SLTU = 9.U(4.W)
  val MUL = 10.U(4.W)
}

class ALU(val width: Int) extends Module {
  require(width >= 2, "ALU width must be at least 2")

  val io = IO(new Bundle {
    val a = Input(UInt(width.W))
    val b = Input(UInt(width.W))
    val opcode = Input(UInt(4.W))
    val result = Output(UInt(width.W))
    val zero = Output(Bool())
    val negative = Output(Bool())
    val carry = Output(Bool())
    val overflow = Output(Bool())
    val busy = Output(Bool())
    val done = Output(Bool())
    val valid = Output(Bool())
  })

  private val shiftWidth = log2Ceil(width)
  val adder = Module(new HierarchicalCarryLookaheadAdder(width))
  val subtractor = Module(new HierarchicalCarryLookaheadAdder(width))
  val multiplier = Module(new BoothWallaceMultiplier(width + (width % 2)))

  adder.io.a := io.a
  adder.io.b := io.b
  adder.io.carryIn := false.B

  subtractor.io.a := io.a
  subtractor.io.b := (~io.b).asUInt
  subtractor.io.carryIn := true.B

  multiplier.io.a := io.a
  multiplier.io.b := io.b

  val addResult = adder.io.sum
  val subtractResult = subtractor.io.sum
  val shiftAmount = io.b(shiftWidth - 1, 0)
  val logicalRight = io.a >> shiftAmount
  val arithmeticRight = (io.a.asSInt >> shiftAmount).asUInt
  val signedLess = io.a.asSInt < io.b.asSInt
  val unsignedLess = io.a < io.b

  io.result := MuxLookup(io.opcode, 0.U(width.W))(Seq(
    ALUOpcode.ADD -> addResult,
    ALUOpcode.SUB -> subtractResult,
    ALUOpcode.AND -> (io.a & io.b),
    ALUOpcode.OR -> (io.a | io.b),
    ALUOpcode.XOR -> (io.a ^ io.b),
    ALUOpcode.SLL -> (io.a << shiftAmount),
    ALUOpcode.SRL -> logicalRight,
    ALUOpcode.SRA -> arithmeticRight,
    ALUOpcode.SLT -> signedLess,
    ALUOpcode.SLTU -> unsignedLess,
    ALUOpcode.MUL -> multiplier.io.product(width - 1, 0)
  ))

  val selectedAdd = io.opcode === ALUOpcode.ADD
  val selectedSub = io.opcode === ALUOpcode.SUB
  val resultSign = io.result(width - 1)
  val aSign = io.a(width - 1)
  val bSign = io.b(width - 1)
  io.carry := Mux(selectedAdd, adder.io.carryOut,
    Mux(selectedSub, subtractor.io.carryOut, false.B))
  io.overflow := Mux(selectedAdd,
    (aSign === bSign) && (resultSign =/= aSign),
    Mux(selectedSub, (aSign =/= bSign) && (resultSign =/= aSign), false.B))
  io.zero := io.result === 0.U
  io.negative := resultSign
  io.busy := false.B
  io.done := true.B
  io.valid := true.B
}