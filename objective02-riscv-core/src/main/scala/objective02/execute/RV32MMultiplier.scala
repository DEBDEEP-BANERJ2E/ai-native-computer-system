package objective02.execute

import chisel3._
import chisel3.util._
import objective02.decode.MOp
import objective01.arithmetic.BoothWallaceMultiplier

class RV32MMultiplierIO extends Bundle {
  val rs1    = Input(UInt(32.W))
  val rs2    = Input(UInt(32.W))
  val mOp    = Input(UInt(4.W))
  val result = Output(UInt(32.W))
}

class RV32MMultiplier extends Module {
  val io = IO(new RV32MMultiplierIO)

  // We instantiate a 34-bit signed Booth-Wallace Multiplier
  // 34 bits is the minimum even width to represent a zero-extended 32-bit unsigned value
  // as a positive signed number in two's complement without overflowing the sign bit.
  val multiplier = Module(new BoothWallaceMultiplier(34))

  // Determine signedness based on mOp
  // MULH   : signed x signed
  // MULHSU : signed x unsigned
  // MULHU  : unsigned x unsigned
  // MUL    : signed x signed (or unsigned x unsigned, both work for low 32 bits)
  
  val isRs1Signed = (io.mOp === MOp.MULH) || (io.mOp === MOp.MULHSU) || (io.mOp === MOp.MUL)
  val isRs2Signed = (io.mOp === MOp.MULH) || (io.mOp === MOp.MUL)

  val rs1Ext = Wire(UInt(34.W))
  val rs2Ext = Wire(UInt(34.W))

  // Sign-extend or zero-extend to 34 bits
  when(isRs1Signed) {
    rs1Ext := Cat(Fill(2, io.rs1(31)), io.rs1)
  }.otherwise {
    rs1Ext := Cat(0.U(2.W), io.rs1)
  }

  when(isRs2Signed) {
    rs2Ext := Cat(Fill(2, io.rs2(31)), io.rs2)
  }.otherwise {
    rs2Ext := Cat(0.U(2.W), io.rs2)
  }

  multiplier.io.a := rs1Ext
  multiplier.io.b := rs2Ext

  // The 34x34 multiplier returns a 68-bit product
  val fullProduct = multiplier.io.product(63, 0)

  // Select output based on mOp
  when(io.mOp === MOp.MUL) {
    io.result := fullProduct(31, 0)
  }.otherwise {
    io.result := fullProduct(63, 32)
  }
}
