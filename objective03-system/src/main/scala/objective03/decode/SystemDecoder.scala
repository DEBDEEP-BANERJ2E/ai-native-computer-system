package objective03.decode

import chisel3._
import chisel3.util._
import objective02.decode._
import objective02.isa.Opcodes._

// =========================================================================
// SystemDecoder — AN32-System-v1 privileged instruction decode.
//
// Wraps the frozen objective02 Decoder for all Bare-v1 opcodes and adds
// OP_SYSTEM (0x73) decode: CSRRW/CSRRS/CSRRC + immediate forms, ECALL,
// EBREAK, MRET, SRET, SFENCE.VMA. FENCE.I is decoded under MISC-MEM
// (opcode 0x0F, funct3=001) — NOT under OP_SYSTEM.
//
// SystemControlSignals nests the frozen base bundle (never modified).
// =========================================================================

object SysOp {
  val NONE      = 0.U(4.W)
  val CSRRW     = 1.U(4.W)
  val CSRRS     = 2.U(4.W)
  val CSRRC     = 3.U(4.W)
  val CSRRWI    = 4.U(4.W)
  val CSRRSI    = 5.U(4.W)
  val CSRRCI    = 6.U(4.W)
}

// CSR access type passed to CSRFile (0=read,1=write,2=set,3=clear)
object CsrAccess {
  val READ  = 0.U(2.W)
  val WRITE = 1.U(2.W)
  val SET   = 2.U(2.W)
  val CLEAR = 3.U(2.W)
}

class SystemControlSignals extends Bundle {
  val base        = new ControlSignalsBundle
  val sysOp       = UInt(4.W)   // SysOp.*
  val csrAddr     = UInt(12.W)  // inst[31:20]
  val csrWrite    = Bool()      // instruction actually writes the CSR
  val csrRs1Data  = UInt(32.W)  // write data (rs1 value or zimm), driven at EX
  val isEcall     = Bool()
  val isEbreak    = Bool()
  val isMret      = Bool()
  val isSret      = Bool()
  val isSfenceVma = Bool()
  val isFenceI    = Bool()
  val isFence     = Bool()      // any fence variant (incl. FENCE.I)
  val sfenceRs1   = UInt(5.W)   // VA select for SFENCE.VMA
  val sfenceRs2   = UInt(5.W)   // ASID select for SFENCE.VMA
}

class SystemDecoderIO extends Bundle {
  val instruction = Input(UInt(32.W))
  val controls    = Output(new SystemControlSignals)
  val rd          = Output(UInt(5.W))
  val rs1         = Output(UInt(5.W))
  val rs2         = Output(UInt(5.W))
  val imm         = Output(UInt(32.W))
  // Register values needed by CSR ops (wired by core at ID)
  val rs1Data     = Input(UInt(32.W))
  val rs2Data     = Input(UInt(32.W))
}

class SystemDecoder(enableFullM: Boolean = true, enableCapabilities: Boolean = true)
    extends Module {
  val io = IO(new SystemDecoderIO)

  val inst = io.instruction
  val op   = inst(6, 0)
  val f3   = inst(14, 12)
  val f7   = inst(31, 25)

  // ---- Base decoder for all non-SYSTEM opcodes ----
  val baseDec = Module(new Decoder(enableFullM, enableCapabilities))
  baseDec.io.instruction := inst

  io.rd  := baseDec.io.rd
  io.rs1 := baseDec.io.rs1
  io.rs2 := baseDec.io.rs2
  io.imm := baseDec.io.imm

  val ctrl = Wire(new SystemControlSignals)
  ctrl.base := baseDec.io.controls

  // Defaults
  ctrl.sysOp       := SysOp.NONE
  ctrl.csrAddr     := inst(31, 20)
  ctrl.csrWrite    := false.B
  ctrl.csrRs1Data  := 0.U
  ctrl.isEcall     := false.B
  ctrl.isEbreak    := false.B
  ctrl.isMret      := false.B
  ctrl.isSret      := false.B
  ctrl.isSfenceVma := false.B
  ctrl.isFenceI    := false.B
  ctrl.isFence     := false.B
  ctrl.sfenceRs1   := inst(19, 15)
  ctrl.sfenceRs2   := inst(24, 20)

  // When base decoder marks illegal on OP_SYSTEM we override below.
  val isSystemOp = op === OP_SYSTEM
  val isMiscMem  = op === "b0001111".U // 0x0F

  // Suppress base "illegal" for opcodes we handle ourselves
  when(isSystemOp || (isMiscMem && f3 =/= "b000".U)) {
    ctrl.base.illegalInstruction := false.B
  }

  // ---- OP_SYSTEM (0x73) decode ----
  when(isSystemOp) {
    ctrl.base.illegalInstruction := true.B // default: illegal until proven
    ctrl.base.regWrite           := false.B
    ctrl.base.memRead            := false.B
    ctrl.base.memWrite           := false.B
    ctrl.base.branchType         := BranchType.NONE
    ctrl.base.jumpType           := JumpType.NONE

    switch(f3) {
      // ---- SYSTEM group (funct3=000): ECALL/EBREAK/MRET/SRET/SFENCE.VMA ----
      is("b000".U) {
        // Decode on inst[31:20] pattern (csr field repurposed as funct12)
        val f12 = inst(31, 20)
        when(inst === "h00000073".U) {           // ECALL
          ctrl.isEcall := true.B
          ctrl.base.illegalInstruction := false.B
        }.elsewhen(inst === "h00100073".U) {     // EBREAK
          ctrl.isEbreak := true.B
          ctrl.base.illegalInstruction := false.B
        }.elsewhen(inst === "h30200073".U) {     // MRET
          ctrl.isMret := true.B
          ctrl.base.illegalInstruction := false.B
        }.elsewhen(inst === "h10200073".U) {     // SRET
          ctrl.isSret := true.B
          ctrl.base.illegalInstruction := false.B
        }.elsewhen(f7 === "b0001001".U && inst(11, 7) === 0.U &&
                    inst(19, 15) =/= 0.U || f7 === "b0001001".U) {
          // SFENCE.VMA: funct7=0001001, funct3=000, rd field=00000
          when(inst(11, 7) === 0.U) {
            ctrl.isSfenceVma := true.B
            ctrl.isFence    := true.B
            ctrl.base.illegalInstruction := false.B
          }
        }
      }
      // ---- CSR instructions ----
      is("b001".U) { ctrl.sysOp := SysOp.CSRRW;  decodeCsr() }
      is("b010".U) { ctrl.sysOp := SysOp.CSRRS;  decodeCsr() }
      is("b011".U) { ctrl.sysOp := SysOp.CSRRC;  decodeCsr() }
      is("b100".U) { /* reserved */ }
      is("b101".U) { ctrl.sysOp := SysOp.CSRRWI; decodeCsr() }
      is("b110".U) { ctrl.sysOp := SysOp.CSRRSI; decodeCsr() }
      is("b111".U) { ctrl.sysOp := SysOp.CSRRCI; decodeCsr() }
    }
  }

  // ---- MISC-MEM (0x0F): FENCE.I at funct3=001 ----
  when(isMiscMem) {
    ctrl.base.illegalInstruction := true.B
    ctrl.base.regWrite           := false.B
    ctrl.base.memRead            := false.B
    ctrl.base.memWrite           := false.B
    when(f3 === "b001".U) { // FENCE.I
      ctrl.isFenceI   := true.B
      ctrl.isFence    := true.B
      ctrl.base.illegalInstruction := false.B
    }.elsewhen(f3 === "b000".U) { // FENCE (treated as pipeline fence nop)
      ctrl.isFence    := true.B
      ctrl.base.illegalInstruction := false.B
    }
    // other funct3 remain illegal
  }

  // Helper: CSR decode legality + write-intent rules
  def decodeCsr(): Unit = {
    val isImmForm = f3 >= "b101".U
    // rs1 field for reg form, uimm[4:0] for imm form
    val rs1IsZero = inst(19, 15) === 0.U
    val uimmZero  = inst(19, 15) === 0.U // zimm is inst[19:15]

    ctrl.base.illegalInstruction := false.B
    // csrWrite intent:
    //   CSRRW/CSRRWI: always write
    //   CSRRS/CSRRC:  write iff rs1 != x0
    //   CSRRSI/CSRRCI:write iff zimm != 0
    ctrl.csrWrite := Mux(f3 === "b001".U || f3 === "b101".U, true.B,
                    Mux(isImmForm, inst(19, 15) =/= 0.U, inst(19, 15) =/= 0.U))
    // rd=x0 suppresses the read return for CSRRW-family; all forms still
    // "read" for RS/RC semantics — modeled as accessType below.
    // accessType for CSRFile: 0=read,1=write,2=set,3=clear
    // We encode intent in sysOp; CSRFile writeEn gated by csrWrite.
    // For RS/RC with zero source the effective access is read-only.
    val effWrite = ctrl.csrWrite
    ctrl.base.regWrite := inst(11, 7) =/= 0.U // rd != x0 -> write rd
    ctrl.base.wbSource := WBSource.ALU // CSR read data flows via csrRs1Data path
    ctrl.base.usesIntRs1 := !isImmForm
    ctrl.base.usesIntRs2 := false.B
  }

  // CSR data operand: rs1 value (reg form) or zimm (imm form, zero-extended)
  ctrl.csrRs1Data := Mux(f3 >= "b101".U,
    Cat(0.U(27.W), inst(19, 15)), io.rs1Data)

  io.controls := ctrl
}
