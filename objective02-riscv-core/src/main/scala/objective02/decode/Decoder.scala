package objective02.decode

import chisel3._
import chisel3.util._
import objective02.isa.Opcodes._
import objective02.isa.Instructions._

class DecoderIO extends Bundle {
  val instruction = Input(UInt(32.W))
  val controls    = Output(new ControlSignalsBundle)
  val rd          = Output(UInt(5.W))
  val rs1         = Output(UInt(5.W))
  val rs2         = Output(UInt(5.W))
  val imm         = Output(UInt(32.W))
}

class Decoder extends Module {
  val io = IO(new DecoderIO)

  val inst = io.instruction
  val op = opcode(inst)
  val f3 = funct3(inst)
  val f7 = funct7(inst)

  io.rd  := rd(inst)
  io.rs1 := rs1(inst)
  io.rs2 := rs2(inst)

  // Instantiate Immediate Generator
  val immGen = Module(new ImmediateGenerator)
  immGen.io.instruction := inst
  io.imm := immGen.io.immOut

  // Default control signals (safe NOP defaults)
  val ctrl = Wire(new ControlSignalsBundle)
  ctrl.aluOp              := ALUOps.ADD
  ctrl.aluSrcA            := ALUSrcA.RS1
  ctrl.aluSrcB            := ALUSrcB.RS2
  ctrl.regWrite           := false.B
  ctrl.memRead            := false.B
  ctrl.memWrite           := false.B
  ctrl.memWidth           := MemWidth.WORD
  ctrl.branchType         := BranchType.NONE
  ctrl.jumpType           := JumpType.NONE
  ctrl.wbSource           := WBSource.ALU
  ctrl.isMul              := false.B
  ctrl.isSecurityOp       := false.B
  ctrl.illegalInstruction := false.B

  switch(op) {
    // -------------------------------------------------------------
    // R-Type Instructions (Register-Register ALU & RV32M)
    // -------------------------------------------------------------
    is(OP_R_TYPE) {
      ctrl.aluSrcA  := ALUSrcA.RS1
      ctrl.aluSrcB  := ALUSrcB.RS2
      ctrl.regWrite := true.B
      ctrl.wbSource := WBSource.ALU

      when(f7 === FUNCT7_STANDARD) {
        switch(f3) {
          is(FUNCT3_ADD_SUB) { ctrl.aluOp := ALUOps.ADD }
          is(FUNCT3_SLL)     { ctrl.aluOp := ALUOps.SLL }
          is(FUNCT3_SLT)     { ctrl.aluOp := ALUOps.SLT }
          is(FUNCT3_SLTU)    { ctrl.aluOp := ALUOps.SLTU }
          is(FUNCT3_XOR)     { ctrl.aluOp := ALUOps.XOR }
          is(FUNCT3_SRL_SRA) { ctrl.aluOp := ALUOps.SRL }
          is(FUNCT3_OR)      { ctrl.aluOp := ALUOps.OR }
          is(FUNCT3_AND)     { ctrl.aluOp := ALUOps.AND }
        }
      }.elsewhen(f7 === FUNCT7_ALT) {
        switch(f3) {
          is(FUNCT3_ADD_SUB) { ctrl.aluOp := ALUOps.SUB }
          is(FUNCT3_SRL_SRA) { ctrl.aluOp := ALUOps.SRA }
          // Any other funct3 with funct7=0x20 is illegal
        }
        when(f3 =/= FUNCT3_ADD_SUB && f3 =/= FUNCT3_SRL_SRA) {
          ctrl.illegalInstruction := true.B
        }
      }.elsewhen(f7 === FUNCT7_MULDIV) {
        // RV32M Extension
        when(f3 === FUNCT3_MUL) {
          ctrl.aluOp := ALUOps.MUL
          ctrl.isMul := true.B
        }.otherwise {
          // Other M-extension ops handled by future divider
          ctrl.aluOp := ALUOps.MUL
          ctrl.isMul := true.B
        }
      }.otherwise {
        ctrl.illegalInstruction := true.B
      }
    }

    // -------------------------------------------------------------
    // I-Type Arithmetic & Logical Instructions (Register-Immediate)
    // -------------------------------------------------------------
    is(OP_I_TYPE) {
      ctrl.aluSrcA  := ALUSrcA.RS1
      ctrl.aluSrcB  := ALUSrcB.IMM
      ctrl.regWrite := true.B
      ctrl.wbSource := WBSource.ALU

      switch(f3) {
        is(FUNCT3_ADD_SUB) { ctrl.aluOp := ALUOps.ADD } // ADDI
        is(FUNCT3_SLT)     { ctrl.aluOp := ALUOps.SLT } // SLTI
        is(FUNCT3_SLTU)    { ctrl.aluOp := ALUOps.SLTU } // SLTIU
        is(FUNCT3_XOR)     { ctrl.aluOp := ALUOps.XOR } // XORI
        is(FUNCT3_OR)      { ctrl.aluOp := ALUOps.OR } // ORI
        is(FUNCT3_AND)     { ctrl.aluOp := ALUOps.AND } // ANDI
        is(FUNCT3_SLL) {
          // SLLI requires upper funct7 bits to be 0x00
          when(f7 === FUNCT7_STANDARD) {
            ctrl.aluOp := ALUOps.SLL
          }.otherwise {
            ctrl.illegalInstruction := true.B
          }
        }
        is(FUNCT3_SRL_SRA) {
          when(f7 === FUNCT7_STANDARD) {
            ctrl.aluOp := ALUOps.SRL // SRLI
          }.elsewhen(f7 === FUNCT7_ALT) {
            ctrl.aluOp := ALUOps.SRA // SRAI
          }.otherwise {
            ctrl.illegalInstruction := true.B
          }
        }
      }
    }

    // -------------------------------------------------------------
    // Load Instructions (LB, LH, LW, LBU, LHU)
    // -------------------------------------------------------------
    is(OP_LOAD) {
      ctrl.aluSrcA  := ALUSrcA.RS1
      ctrl.aluSrcB  := ALUSrcB.IMM
      ctrl.aluOp    := ALUOps.ADD // Address calculation: rs1 + offset
      ctrl.regWrite := true.B
      ctrl.memRead  := true.B
      ctrl.wbSource := WBSource.MEM

      switch(f3) {
        is(FUNCT3_BYTE)   { ctrl.memWidth := MemWidth.BYTE }
        is(FUNCT3_HALF)   { ctrl.memWidth := MemWidth.HALF }
        is(FUNCT3_WORD)   { ctrl.memWidth := MemWidth.WORD }
        is(FUNCT3_BYTE_U) { ctrl.memWidth := MemWidth.BYTE_U }
        is(FUNCT3_HALF_U) { ctrl.memWidth := MemWidth.HALF_U }
        is(3.U, 6.U, 7.U) { ctrl.illegalInstruction := true.B }
      }
    }

    // -------------------------------------------------------------
    // Store Instructions (SB, SH, SW)
    // -------------------------------------------------------------
    is(OP_STORE) {
      ctrl.aluSrcA   := ALUSrcA.RS1
      ctrl.aluSrcB   := ALUSrcB.IMM
      ctrl.aluOp     := ALUOps.ADD // Address calculation: rs1 + offset
      ctrl.memWrite  := true.B
      ctrl.regWrite  := false.B

      switch(f3) {
        is(FUNCT3_BYTE) { ctrl.memWidth := MemWidth.BYTE }
        is(FUNCT3_HALF) { ctrl.memWidth := MemWidth.HALF }
        is(FUNCT3_WORD) { ctrl.memWidth := MemWidth.WORD }
        is(3.U, 4.U, 5.U, 6.U, 7.U) { ctrl.illegalInstruction := true.B }
      }
    }

    // -------------------------------------------------------------
    // Conditional Branch Instructions (BEQ, BNE, BLT, BGE, BLTU, BGEU)
    // -------------------------------------------------------------
    is(OP_BRANCH) {
      ctrl.aluSrcA   := ALUSrcA.RS1
      ctrl.aluSrcB   := ALUSrcB.RS2
      ctrl.regWrite  := false.B

      switch(f3) {
        is(FUNCT3_BEQ)  { ctrl.branchType := BranchType.BEQ;  ctrl.aluOp := ALUOps.SUB }
        is(FUNCT3_BNE)  { ctrl.branchType := BranchType.BNE;  ctrl.aluOp := ALUOps.SUB }
        is(FUNCT3_BLT)  { ctrl.branchType := BranchType.BLT;  ctrl.aluOp := ALUOps.SLT }
        is(FUNCT3_BGE)  { ctrl.branchType := BranchType.BGE;  ctrl.aluOp := ALUOps.SLT }
        is(FUNCT3_BLTU) { ctrl.branchType := BranchType.BLTU; ctrl.aluOp := ALUOps.SLTU }
        is(FUNCT3_BGEU) { ctrl.branchType := BranchType.BGEU; ctrl.aluOp := ALUOps.SLTU }
        is(2.U, 3.U)    { ctrl.illegalInstruction := true.B }
      }
    }

    // -------------------------------------------------------------
    // Unconditional Jumps (JAL, JALR)
    // -------------------------------------------------------------
    is(OP_JAL) {
      ctrl.aluSrcA   := ALUSrcA.PC
      ctrl.aluSrcB   := ALUSrcB.FOUR
      ctrl.aluOp     := ALUOps.ADD
      ctrl.regWrite  := true.B
      ctrl.jumpType  := JumpType.JAL
      ctrl.wbSource  := WBSource.PC_PLUS_4
    }

    is(OP_JALR) {
      when(f3 === 0.U) {
        ctrl.aluSrcA   := ALUSrcA.PC
        ctrl.aluSrcB   := ALUSrcB.FOUR
        ctrl.aluOp     := ALUOps.ADD
        ctrl.regWrite  := true.B
        ctrl.jumpType  := JumpType.JALR
        ctrl.wbSource  := WBSource.PC_PLUS_4
      }.otherwise {
        ctrl.illegalInstruction := true.B
      }
    }

    // -------------------------------------------------------------
    // Upper Immediate Instructions (LUI, AUIPC)
    // -------------------------------------------------------------
    is(OP_LUI) {
      ctrl.aluSrcA   := ALUSrcA.ZERO
      ctrl.aluSrcB   := ALUSrcB.IMM
      ctrl.aluOp     := ALUOps.PASS_B
      ctrl.regWrite  := true.B
      ctrl.wbSource  := WBSource.IMM
    }

    is(OP_AUIPC) {
      ctrl.aluSrcA   := ALUSrcA.PC
      ctrl.aluSrcB   := ALUSrcB.IMM
      ctrl.aluOp     := ALUOps.ADD // PC + (imm << 12)
      ctrl.regWrite  := true.B
      ctrl.wbSource  := WBSource.ALU
    }

    // -------------------------------------------------------------
    // Security / Capability Extension Instructions
    // -------------------------------------------------------------
    is(OP_SECURITY) {
      ctrl.isSecurityOp := true.B
      ctrl.regWrite     := true.B
      ctrl.wbSource     := WBSource.ALU
    }
  }

  // Handle default / unmapped opcode
  when(op =/= OP_R_TYPE && op =/= OP_I_TYPE && op =/= OP_LOAD && op =/= OP_STORE &&
       op =/= OP_BRANCH && op =/= OP_JAL && op =/= OP_JALR && op =/= OP_LUI &&
       op =/= OP_AUIPC && op =/= OP_SECURITY && op =/= OP_SYSTEM) {
    ctrl.illegalInstruction := true.B
  }

  io.controls := ctrl
}
