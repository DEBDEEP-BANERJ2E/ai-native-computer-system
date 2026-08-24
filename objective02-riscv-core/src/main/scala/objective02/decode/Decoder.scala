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

class Decoder(val enableFullM: Boolean = false, val enableCapabilities: Boolean = false) extends Module {
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
  ctrl.mOp                := MOp.NONE
  ctrl.isSecurityOp       := false.B
  ctrl.isCapOp            := false.B
  ctrl.capOp              := CapOp.NONE
  ctrl.isCapMem           := false.B
  ctrl.capRegWrite        := false.B
  ctrl.usesCapRs1         := false.B
  ctrl.usesIntRs1         := false.B
  ctrl.usesIntRs2         := false.B
  ctrl.illegalInstruction := false.B

  switch(op) {
    // -------------------------------------------------------------
    // R-Type Instructions (Register-Register ALU & RV32M)
    // -------------------------------------------------------------
    is(OP_R_TYPE) {
      ctrl.aluSrcA    := ALUSrcA.RS1
      ctrl.aluSrcB    := ALUSrcB.RS2
      ctrl.regWrite   := true.B
      ctrl.wbSource   := WBSource.ALU
      ctrl.usesIntRs1 := true.B
      ctrl.usesIntRs2 := true.B

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
        }
        when(f3 =/= FUNCT3_ADD_SUB && f3 =/= FUNCT3_SRL_SRA) {
          ctrl.illegalInstruction := true.B
        }
      }.elsewhen(f7 === FUNCT7_MULDIV) {
        // RV32M Extension
        when(f3 === FUNCT3_MUL) {
          ctrl.aluOp := ALUOps.MUL
          ctrl.isMul := true.B
          ctrl.mOp   := MOp.MUL
        }.elsewhen(enableFullM.B) {
          switch(f3) {
            is(FUNCT3_MULH)   { ctrl.mOp := MOp.MULH; ctrl.isMul := true.B }
            is(FUNCT3_MULHSU) { ctrl.mOp := MOp.MULHSU; ctrl.isMul := true.B }
            is(FUNCT3_MULHU)  { ctrl.mOp := MOp.MULHU; ctrl.isMul := true.B }
            is(FUNCT3_DIV)    { ctrl.mOp := MOp.DIV }
            is(FUNCT3_DIVU)   { ctrl.mOp := MOp.DIVU }
            is(FUNCT3_REM)    { ctrl.mOp := MOp.REM }
            is(FUNCT3_REMU)   { ctrl.mOp := MOp.REMU }
          }
        }.otherwise {
          // If not enableFullM, MUL is legal but other M-extension ops are illegal
          ctrl.illegalInstruction := true.B
        }
      }.otherwise {
        ctrl.illegalInstruction := true.B
      }
    }

    // -------------------------------------------------------------
    // I-Type Arithmetic & Logical Instructions (Register-Immediate)
    // -------------------------------------------------------------
    is(OP_I_TYPE) {
      ctrl.aluSrcA    := ALUSrcA.RS1
      ctrl.aluSrcB    := ALUSrcB.IMM
      ctrl.regWrite   := true.B
      ctrl.wbSource   := WBSource.ALU
      ctrl.usesIntRs1 := true.B

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
      ctrl.aluSrcA    := ALUSrcA.RS1
      ctrl.aluSrcB    := ALUSrcB.IMM
      ctrl.aluOp      := ALUOps.ADD // Address calculation: rs1 + offset
      ctrl.regWrite   := true.B
      ctrl.memRead    := true.B
      ctrl.wbSource   := WBSource.MEM
      ctrl.usesIntRs1 := true.B

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
      ctrl.aluSrcA    := ALUSrcA.RS1
      ctrl.aluSrcB    := ALUSrcB.IMM
      ctrl.aluOp      := ALUOps.ADD // Address calculation (rs1 + imm)
      ctrl.memWrite   := true.B
      ctrl.regWrite   := false.B
      ctrl.usesIntRs1 := true.B
      ctrl.usesIntRs2 := true.B

      switch(f3) {
        is(FUNCT3_BYTE) { ctrl.memWidth := MemWidth.BYTE }
        is(FUNCT3_HALF) { ctrl.memWidth := MemWidth.HALF }
        is(FUNCT3_WORD) { ctrl.memWidth := MemWidth.WORD }
        is(3.U)         { ctrl.illegalInstruction := true.B }
        is(4.U)         { ctrl.illegalInstruction := true.B }
        is(5.U)         { ctrl.illegalInstruction := true.B }
        is(6.U)         { ctrl.illegalInstruction := true.B }
        is(7.U)         { ctrl.illegalInstruction := true.B }
      }
    }

    // -------------------------------------------------------------
    // Conditional Branch Instructions
    // -------------------------------------------------------------
    is(OP_BRANCH) {
      ctrl.aluSrcA    := ALUSrcA.RS1
      ctrl.aluSrcB    := ALUSrcB.RS2
      ctrl.regWrite   := false.B
      ctrl.usesIntRs1 := true.B
      ctrl.usesIntRs2 := true.B

      switch(f3) {
        is(FUNCT3_BEQ)  { ctrl.branchType := BranchType.BEQ;  ctrl.aluOp := ALUOps.SUB }
        is(FUNCT3_BNE)  { ctrl.branchType := BranchType.BNE;  ctrl.aluOp := ALUOps.SUB }
        is(FUNCT3_BLT)  { ctrl.branchType := BranchType.BLT;  ctrl.aluOp := ALUOps.SLT }
        is(FUNCT3_BGE)  { ctrl.branchType := BranchType.BGE;  ctrl.aluOp := ALUOps.SLT }
        is(FUNCT3_BLTU) { ctrl.branchType := BranchType.BLTU; ctrl.aluOp := ALUOps.SLTU }
        is(FUNCT3_BGEU) { ctrl.branchType := BranchType.BGEU; ctrl.aluOp := ALUOps.SLTU }
        is(2.U)         { ctrl.illegalInstruction := true.B }
        is(3.U)         { ctrl.illegalInstruction := true.B }
      }
    }

    // -------------------------------------------------------------
    // Unconditional Jump Instructions (JAL, JALR)
    // -------------------------------------------------------------
    is(OP_JAL) {
      ctrl.aluSrcA   := ALUSrcA.PC
      ctrl.aluSrcB   := ALUSrcB.FOUR
      ctrl.aluOp     := ALUOps.ADD // PC + 4
      ctrl.jumpType  := JumpType.JAL
      ctrl.regWrite  := true.B
      ctrl.wbSource  := WBSource.PC_PLUS_4
    }

    is(OP_JALR) {
      when(f3 === "b000".U) {
        ctrl.aluSrcA    := ALUSrcA.PC
        ctrl.aluSrcB    := ALUSrcB.FOUR
        ctrl.aluOp      := ALUOps.ADD // PC + 4
        ctrl.jumpType   := JumpType.JALR
        ctrl.regWrite   := true.B
        ctrl.wbSource   := WBSource.PC_PLUS_4
        ctrl.usesIntRs1 := true.B
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
      ctrl.aluOp     := ALUOps.ADD
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
    // Custom-0: Capability Manipulation Instructions (OP_CAP = 0x0B)
    // -------------------------------------------------------------
    is(OP_CAP) {
      when(enableCapabilities.B) {
        val cs1Index = io.rs1
        val cdIndex  = io.rd
        // Validate capability register indices (must be in range 0..7)
        val cs1Valid = (cs1Index <= 7.U)
        val cdValid  = (cdIndex <= 7.U)

        when(f7 === 0.U && cs1Valid) {
          ctrl.isCapOp    := true.B
          ctrl.usesCapRs1 := true.B

          switch(f3) {
            is(FUNCT3_CSETBOUNDS) {
              when(cdValid) {
                ctrl.capOp       := CapOp.CSETBOUNDS
                ctrl.capRegWrite := true.B
                ctrl.usesIntRs2  := true.B // rs2 length in integer GPR
              }.otherwise {
                ctrl.illegalInstruction := true.B
              }
            }
            is(FUNCT3_CANDPERM) {
              when(cdValid) {
                ctrl.capOp       := CapOp.CANDPERM
                ctrl.capRegWrite := true.B
                ctrl.usesIntRs2  := true.B // rs2 mask in integer GPR
              }.otherwise {
                ctrl.illegalInstruction := true.B
              }
            }
            is(FUNCT3_CINCOFFSET) {
              when(cdValid) {
                ctrl.capOp       := CapOp.CINCOFFSET
                ctrl.capRegWrite := true.B
                ctrl.usesIntRs2  := true.B // rs2 signed delta in integer GPR
              }.otherwise {
                ctrl.illegalInstruction := true.B
              }
            }
            is(FUNCT3_CGETBASE) {
              ctrl.capOp    := CapOp.CGETBASE
              ctrl.regWrite := true.B
              ctrl.wbSource := WBSource.ALU
            }
            is(FUNCT3_CGETLEN) {
              ctrl.capOp    := CapOp.CGETLEN
              ctrl.regWrite := true.B
              ctrl.wbSource := WBSource.ALU
            }
            is(FUNCT3_CGETTAG) {
              ctrl.capOp    := CapOp.CGETTAG
              ctrl.regWrite := true.B
              ctrl.wbSource := WBSource.ALU
            }
            is(FUNCT3_CGETPERM) {
              ctrl.capOp    := CapOp.CGETPERM
              ctrl.regWrite := true.B
              ctrl.wbSource := WBSource.ALU
            }
            is(7.U) {
              ctrl.illegalInstruction := true.B
            }
          }
        }.otherwise {
          ctrl.illegalInstruction := true.B
        }
      }.otherwise {
        // When capabilities are disabled, maintain safe placeholder semantics
        ctrl.isSecurityOp := true.B
      }
    }

    // -------------------------------------------------------------
    // Custom-1: Capability Protected Memory Instructions (OP_CAP_MEM = 0x2B)
    // -------------------------------------------------------------
    is(OP_CAP_MEM) {
      when(enableCapabilities.B) {
        val cs1Index = io.rs1
        val cs1Valid = (cs1Index <= 7.U)

        when(cs1Valid) {
          ctrl.isCapMem   := true.B
          ctrl.usesCapRs1 := true.B

          switch(f3) {
            is(FUNCT3_CLB) {
              ctrl.memRead  := true.B
              ctrl.regWrite := true.B
              ctrl.memWidth := MemWidth.BYTE
              ctrl.wbSource := WBSource.MEM
            }
            is(FUNCT3_CLH) {
              ctrl.memRead  := true.B
              ctrl.regWrite := true.B
              ctrl.memWidth := MemWidth.HALF
              ctrl.wbSource := WBSource.MEM
            }
            is(FUNCT3_CLW) {
              ctrl.memRead  := true.B
              ctrl.regWrite := true.B
              ctrl.memWidth := MemWidth.WORD
              ctrl.wbSource := WBSource.MEM
            }
            is(FUNCT3_CSB) {
              ctrl.memWrite   := true.B
              ctrl.usesIntRs2 := true.B // store payload in integer rs2
              ctrl.memWidth   := MemWidth.BYTE
            }
            is(FUNCT3_CSH) {
              ctrl.memWrite   := true.B
              ctrl.usesIntRs2 := true.B // store payload in integer rs2
              ctrl.memWidth   := MemWidth.HALF
            }
            is(FUNCT3_CSW) {
              ctrl.memWrite   := true.B
              ctrl.usesIntRs2 := true.B // store payload in integer rs2
              ctrl.memWidth   := MemWidth.WORD
            }
            is(3.U) { ctrl.illegalInstruction := true.B }
            is(7.U) { ctrl.illegalInstruction := true.B }
          }
        }.otherwise {
          ctrl.illegalInstruction := true.B
        }
      }.otherwise {
        ctrl.illegalInstruction := true.B
      }
    }

    // -------------------------------------------------------------
    // System Instructions (CSR / ECALL / EBREAK: Marked illegal until traps/CSRs implemented)
    // -------------------------------------------------------------
    is(OP_SYSTEM) {
      ctrl.illegalInstruction := true.B
    }
  }

  // Handle unmapped opcodes
  when(op =/= OP_R_TYPE && op =/= OP_I_TYPE && op =/= OP_LOAD && op =/= OP_STORE &&
       op =/= OP_BRANCH && op =/= OP_JAL && op =/= OP_JALR && op =/= OP_LUI &&
       op =/= OP_AUIPC && op =/= OP_CAP && op =/= OP_CAP_MEM) {
    ctrl.illegalInstruction := true.B
  }

  // -------------------------------------------------------------
  // Final Safety Squash: Illegal instruction => zero architectural side effects
  // -------------------------------------------------------------
  when(ctrl.illegalInstruction) {
    ctrl.regWrite     := false.B
    ctrl.memRead      := false.B
    ctrl.memWrite     := false.B
    ctrl.branchType   := BranchType.NONE
    ctrl.jumpType     := JumpType.NONE
    ctrl.isMul        := false.B
    ctrl.mOp          := MOp.NONE
    ctrl.isSecurityOp := false.B
    ctrl.isCapOp      := false.B
    ctrl.capOp        := CapOp.NONE
    ctrl.isCapMem     := false.B
    ctrl.capRegWrite  := false.B
    ctrl.usesCapRs1   := false.B
    ctrl.usesIntRs1   := false.B
    ctrl.usesIntRs2   := false.B
  }

  io.controls := ctrl
}
