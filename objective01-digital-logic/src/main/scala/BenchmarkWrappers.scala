import chisel3._
import objective01.arithmetic.{BoothMultiplier, BoothWallaceMultiplier, CarryLookaheadAdder, HierarchicalCarryLookaheadAdder, RippleCarryAdder, SimpleMultiplier}

class RegisteredRippleCarryAdder extends Module {
  val io = IO(new Bundle { val a = Input(UInt(32.W)); val b = Input(UInt(32.W)); val carryIn = Input(Bool()); val sum = Output(UInt(32.W)); val carryOut = Output(Bool()) })
  val adder = Module(new RippleCarryAdder(32)); adder.io.a := RegNext(io.a); adder.io.b := RegNext(io.b); adder.io.carryIn := RegNext(io.carryIn)
  io.sum := RegNext(adder.io.sum, 0.U); io.carryOut := RegNext(adder.io.carryOut, false.B)
}

class RegisteredFlatCarryLookaheadAdder extends Module {
  val io = IO(new Bundle { val a = Input(UInt(32.W)); val b = Input(UInt(32.W)); val carryIn = Input(Bool()); val sum = Output(UInt(32.W)); val carryOut = Output(Bool()) })
  val adder = Module(new CarryLookaheadAdder(32)); adder.io.a := RegNext(io.a); adder.io.b := RegNext(io.b); adder.io.carryIn := RegNext(io.carryIn)
  io.sum := RegNext(adder.io.sum, 0.U); io.carryOut := RegNext(adder.io.carryOut, false.B)
}

class RegisteredHierarchicalCarryLookaheadAdder extends Module {
  val io = IO(new Bundle { val a = Input(UInt(32.W)); val b = Input(UInt(32.W)); val carryIn = Input(Bool()); val sum = Output(UInt(32.W)); val carryOut = Output(Bool()) })
  val adder = Module(new HierarchicalCarryLookaheadAdder(32)); adder.io.a := RegNext(io.a); adder.io.b := RegNext(io.b); adder.io.carryIn := RegNext(io.carryIn)
  io.sum := RegNext(adder.io.sum, 0.U); io.carryOut := RegNext(adder.io.carryOut, false.B)
}

class RegisteredSimpleMultiplier extends Module {
  val io = IO(new Bundle { val a = Input(UInt(16.W)); val b = Input(UInt(16.W)); val product = Output(UInt(32.W)) })
  val multiplier = Module(new SimpleMultiplier(16)); multiplier.io.a := RegNext(io.a); multiplier.io.b := RegNext(io.b)
  io.product := RegNext(multiplier.io.product, 0.U)
}

class RegisteredBoothMultiplier extends Module {
  val io = IO(new Bundle { val a = Input(UInt(16.W)); val b = Input(UInt(16.W)); val product = Output(UInt(32.W)) })
  val multiplier = Module(new BoothMultiplier(16)); multiplier.io.a := RegNext(io.a); multiplier.io.b := RegNext(io.b)
  io.product := RegNext(multiplier.io.product, 0.U)
}

class RegisteredBoothWallaceMultiplier extends Module {
  val io = IO(new Bundle { val a = Input(UInt(16.W)); val b = Input(UInt(16.W)); val product = Output(UInt(32.W)) })
  val multiplier = Module(new BoothWallaceMultiplier(16)); multiplier.io.a := RegNext(io.a); multiplier.io.b := RegNext(io.b)
  io.product := RegNext(multiplier.io.product, 0.U)
}