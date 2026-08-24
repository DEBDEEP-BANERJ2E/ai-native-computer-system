package objective02.execute

import chisel3._
import chisel3.util._

class IterativeDividerIO extends Bundle {
  val start       = Input(Bool())
  val kill        = Input(Bool())
  val dividend    = Input(UInt(32.W))
  val divisor     = Input(UInt(32.W))
  val isSigned    = Input(Bool())
  
  val busy        = Output(Bool())
  val done        = Output(Bool())
  
  val quotient    = Output(UInt(32.W))
  val remainder   = Output(UInt(32.W))
  val iteration   = Output(UInt(6.W))
}

class IterativeDivider extends Module {
  val io = IO(new IterativeDividerIO)

  // State machine
  val sIdle :: sCompute :: sDone :: Nil = Enum(3)
  val state = RegInit(sIdle)
  val count = RegInit(0.U(6.W))

  // Data registers
  // To handle 32-bit division, we need a 64-bit remainder register (shifted left)
  // or a 32-bit quotient and 32-bit remainder. 
  // Standard restoring divider algorithm:
  // Initialize: AQ = {32'b0, dividend}, M = divisor
  // Loop 32 times:
  //   Shift AQ left by 1
  //   A = A - M
  //   If A < 0 (i.e. MSB is 1): A = A + M, Q[0] = 0
  //   Else: Q[0] = 1
  val aReg = RegInit(0.U(33.W)) // 33 bits to hold sign bit during subtraction
  val qReg = RegInit(0.U(32.W))
  val mReg = RegInit(0.U(33.W)) // 33 bits to match A

  // Sign tracking registers
  val qNeg = RegInit(false.B)
  val rNeg = RegInit(false.B)

  // Output registers for corner cases and final results
  val finalQuotient = RegInit(0.U(32.W))
  val finalRemainder = RegInit(0.U(32.W))

  // Outputs
  io.busy := state =/= sIdle
  io.done := state === sDone
  io.quotient := finalQuotient
  io.remainder := finalRemainder
  io.iteration := count

  // Pre-processing Combinational Logic
  val dividendSign = io.isSigned && io.dividend(31)
  val divisorSign = io.isSigned && io.divisor(31)
  
  val absDividend = Wire(UInt(32.W))
  absDividend := Mux(dividendSign, (~io.dividend) + 1.U, io.dividend)
  
  val absDivisor = Wire(UInt(32.W))
  absDivisor := Mux(divisorSign, (~io.divisor) + 1.U, io.divisor)

  val isDivByZero = (io.divisor === 0.U)
  val isOverflow = io.isSigned && (io.dividend === "h80000000".U) && (io.divisor === "hFFFFFFFF".U)

  when(io.kill) {
    state := sIdle
    count := 0.U
  }.otherwise {
    switch(state) {
      is(sIdle) {
        when(io.start) {
          qNeg := dividendSign ^ divisorSign
          rNeg := dividendSign

          when(isDivByZero) {
            finalQuotient := "hFFFFFFFF".U
            finalRemainder := io.dividend
            state := sDone
          }.elsewhen(isOverflow) {
            finalQuotient := "h80000000".U
            finalRemainder := 0.U
            state := sDone
          }.otherwise {
            aReg := 0.U
            qReg := absDividend
            mReg := Cat(0.U(1.W), absDivisor)
            count := 32.U
            state := sCompute
          }
        }
      }
      
      is(sCompute) {
        val shiftedA = Cat(aReg(31, 0), qReg(31))
        val shiftedQ = Cat(qReg(30, 0), 0.U(1.W))
        
        val subA = shiftedA - mReg
        val isNeg = subA(32)

        when(isNeg) {
          // Restore
          aReg := shiftedA
          qReg := shiftedQ // Q[0] is already 0
        }.otherwise {
          // Don't restore
          aReg := subA
          qReg := shiftedQ | 1.U
        }
        
        count := count - 1.U
        when(count === 1.U) {
          state := sDone
          // Post-processing logic on transition to sDone
          val qResult = Mux(isNeg, shiftedQ, shiftedQ | 1.U)
          val rResult = Mux(isNeg, shiftedA, subA)(31, 0)
          
          finalQuotient := Mux(qNeg, (~qResult) + 1.U, qResult)
          finalRemainder := Mux(rNeg, (~rResult) + 1.U, rResult)
        }
      }

      is(sDone) {
        state := sIdle
      }
    }
  }
}
