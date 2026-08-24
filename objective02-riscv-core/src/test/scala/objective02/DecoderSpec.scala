package objective02

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import objective02.decode._

class DecoderSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Decoder"

  // Helper to test an illegal instruction and verify zero architectural side effects
  def verifyIllegal(dut: Decoder, instHex: String, description: String): Unit = {
    dut.io.instruction.poke(instHex.U)
    dut.io.controls.illegalInstruction.expect(true.B, s"$description: illegalInstruction must be true")
    dut.io.controls.regWrite.expect(false.B, s"$description: regWrite must be suppressed")
    dut.io.controls.memRead.expect(false.B, s"$description: memRead must be suppressed")
    dut.io.controls.memWrite.expect(false.B, s"$description: memWrite must be suppressed")
    dut.io.controls.branchType.expect(BranchType.NONE, s"$description: branchType must be NONE")
    dut.io.controls.jumpType.expect(JumpType.NONE, s"$description: jumpType must be NONE")
    dut.io.controls.isMul.expect(false.B, s"$description: isMul must be false")
    dut.io.controls.mOp.expect(MOp.NONE, s"$description: mOp must be NONE")
    dut.io.controls.isSecurityOp.expect(false.B, s"$description: isSecurityOp must be false")
  }

  it should "correctly decode all 10 RV32I R-type arithmetic and logic operations" in {
    test(new Decoder) { dut =>
      // Table: (instHex, aluOp, name)
      val rTests = Seq(
        ("h003100B3", ALUOps.ADD,  "ADD"),  // ADD  x1, x2, x3 (f7=0x00, f3=0)
        ("h403100B3", ALUOps.SUB,  "SUB"),  // SUB  x1, x2, x3 (f7=0x20, f3=0)
        ("h003110B3", ALUOps.SLL,  "SLL"),  // SLL  x1, x2, x3 (f7=0x00, f3=1)
        ("h003120B3", ALUOps.SLT,  "SLT"),  // SLT  x1, x2, x3 (f7=0x00, f3=2)
        ("h003130B3", ALUOps.SLTU, "SLTU"), // SLTU x1, x2, x3 (f7=0x00, f3=3)
        ("h003140B3", ALUOps.XOR,  "XOR"),  // XOR  x1, x2, x3 (f7=0x00, f3=4)
        ("h003150B3", ALUOps.SRL,  "SRL"),  // SRL  x1, x2, x3 (f7=0x00, f3=5)
        ("h403150B3", ALUOps.SRA,  "SRA"),  // SRA  x1, x2, x3 (f7=0x20, f3=5)
        ("h003160B3", ALUOps.OR,   "OR"),   // OR   x1, x2, x3 (f7=0x00, f3=6)
        ("h003170B3", ALUOps.AND,  "AND")   // AND  x1, x2, x3 (f7=0x00, f3=7)
      )

      for ((hex, op, name) <- rTests) {
        dut.io.instruction.poke(hex.U)
        dut.io.rd.expect(1.U)
        dut.io.rs1.expect(2.U)
        dut.io.rs2.expect(3.U)
        dut.io.controls.regWrite.expect(true.B, s"$name: regWrite should be true")
        dut.io.controls.aluOp.expect(op, s"$name: ALU op mismatch")
        dut.io.controls.aluSrcA.expect(ALUSrcA.RS1)
        dut.io.controls.aluSrcB.expect(ALUSrcB.RS2)
        dut.io.controls.wbSource.expect(WBSource.ALU)
        dut.io.controls.illegalInstruction.expect(false.B)
      }
    }
  }

  it should "correctly decode all 9 RV32I I-type ALU instructions and immediate shifts" in {
    test(new Decoder) { dut =>
      val iTests = Seq(
        ("h00A10093", ALUOps.ADD,  "ADDI"),  // ADDI  x1, x2, 10
        ("h00A12093", ALUOps.SLT,  "SLTI"),  // SLTI  x1, x2, 10
        ("h00A13093", ALUOps.SLTU, "SLTIU"), // SLTIU x1, x2, 10
        ("h00A14093", ALUOps.XOR,  "XORI"),  // XORI  x1, x2, 10
        ("h00A16093", ALUOps.OR,   "ORI"),   // ORI   x1, x2, 10
        ("h00A17093", ALUOps.AND,  "ANDI"),  // ANDI  x1, x2, 10
        ("h00411093", ALUOps.SLL,  "SLLI"),  // SLLI  x1, x2, 4  (f7=0x00, f3=1)
        ("h00415093", ALUOps.SRL,  "SRLI"),  // SRLI  x1, x2, 4  (f7=0x00, f3=5)
        ("h40415093", ALUOps.SRA,  "SRAI")   // SRAI  x1, x2, 4  (f7=0x20, f3=5)
      )

      for ((hex, op, name) <- iTests) {
        dut.io.instruction.poke(hex.U)
        dut.io.rd.expect(1.U)
        dut.io.rs1.expect(2.U)
        dut.io.controls.regWrite.expect(true.B, s"$name: regWrite should be true")
        dut.io.controls.aluOp.expect(op, s"$name: ALU op mismatch")
        dut.io.controls.aluSrcA.expect(ALUSrcA.RS1)
        dut.io.controls.aluSrcB.expect(ALUSrcB.IMM)
        dut.io.controls.wbSource.expect(WBSource.ALU)
        dut.io.controls.illegalInstruction.expect(false.B)
      }
    }
  }

  it should "correctly decode all 5 Load instructions (LB, LH, LW, LBU, LHU)" in {
    test(new Decoder) { dut =>
      val loadTests = Seq(
        ("h00410083", MemWidth.BYTE,   "LB"),
        ("h00411083", MemWidth.HALF,   "LH"),
        ("h00412083", MemWidth.WORD,   "LW"),
        ("h00414083", MemWidth.BYTE_U, "LBU"),
        ("h00415083", MemWidth.HALF_U, "LHU")
      )

      for ((hex, width, name) <- loadTests) {
        dut.io.instruction.poke(hex.U)
        dut.io.rd.expect(1.U)
        dut.io.rs1.expect(2.U)
        dut.io.controls.regWrite.expect(true.B, s"$name: regWrite")
        dut.io.controls.memRead.expect(true.B, s"$name: memRead")
        dut.io.controls.memWrite.expect(false.B)
        dut.io.controls.memWidth.expect(width, s"$name: memWidth")
        dut.io.controls.wbSource.expect(WBSource.MEM)
        dut.io.controls.aluOp.expect(ALUOps.ADD)
        dut.io.controls.illegalInstruction.expect(false.B)
      }
    }
  }

  it should "correctly decode all 3 Store instructions (SB, SH, SW)" in {
    test(new Decoder) { dut =>
      val storeTests = Seq(
        ("h00310423", MemWidth.BYTE, "SB"), // SB x3, 8(x2)
        ("h00311423", MemWidth.HALF, "SH"), // SH x3, 8(x2)
        ("h00312423", MemWidth.WORD, "SW")  // SW x3, 8(x2)
      )

      for ((hex, width, name) <- storeTests) {
        dut.io.instruction.poke(hex.U)
        dut.io.rs1.expect(2.U)
        dut.io.rs2.expect(3.U)
        dut.io.controls.regWrite.expect(false.B)
        dut.io.controls.memRead.expect(false.B)
        dut.io.controls.memWrite.expect(true.B, s"$name: memWrite")
        dut.io.controls.memWidth.expect(width, s"$name: memWidth")
        dut.io.controls.aluOp.expect(ALUOps.ADD)
        dut.io.controls.illegalInstruction.expect(false.B)
      }
    }
  }

  it should "correctly decode all 6 Conditional Branches (BEQ, BNE, BLT, BGE, BLTU, BGEU)" in {
    test(new Decoder) { dut =>
      val branchTests = Seq(
        ("h00208863", BranchType.BEQ,  "BEQ"),
        ("h00209863", BranchType.BNE,  "BNE"),
        ("h0020C863", BranchType.BLT,  "BLT"),
        ("h0020D863", BranchType.BGE,  "BGE"),
        ("h0020E863", BranchType.BLTU, "BLTU"),
        ("h0020F863", BranchType.BGEU, "BGEU")
      )

      for ((hex, brType, name) <- branchTests) {
        dut.io.instruction.poke(hex.U)
        dut.io.controls.branchType.expect(brType, s"$name: branchType")
        dut.io.controls.regWrite.expect(false.B)
        dut.io.controls.memRead.expect(false.B)
        dut.io.controls.memWrite.expect(false.B)
        dut.io.controls.illegalInstruction.expect(false.B)
      }
    }
  }

  it should "correctly decode JAL, JALR, LUI, AUIPC, and MUL" in {
    test(new Decoder) { dut =>
      // JAL x1, 100
      dut.io.instruction.poke("h064000EF".U)
      dut.io.rd.expect(1.U)
      dut.io.controls.jumpType.expect(JumpType.JAL)
      dut.io.controls.regWrite.expect(true.B)
      dut.io.controls.wbSource.expect(WBSource.PC_PLUS_4)
      dut.io.controls.illegalInstruction.expect(false.B)

      // JALR x1, 0(x2)
      dut.io.instruction.poke("h000100E7".U)
      dut.io.rd.expect(1.U)
      dut.io.rs1.expect(2.U)
      dut.io.controls.jumpType.expect(JumpType.JALR)
      dut.io.controls.regWrite.expect(true.B)
      dut.io.controls.wbSource.expect(WBSource.PC_PLUS_4)
      dut.io.controls.illegalInstruction.expect(false.B)

      // LUI x1, 0x12345
      dut.io.instruction.poke("h123450B7".U)
      dut.io.rd.expect(1.U)
      dut.io.controls.regWrite.expect(true.B)
      dut.io.controls.wbSource.expect(WBSource.IMM)
      dut.io.controls.aluOp.expect(ALUOps.ADD)
      dut.io.controls.illegalInstruction.expect(false.B)

      // AUIPC x2, 0x80000
      dut.io.instruction.poke("h80000117".U)
      dut.io.rd.expect(2.U)
      dut.io.controls.aluSrcA.expect(ALUSrcA.PC)
      dut.io.controls.aluSrcB.expect(ALUSrcB.IMM)
      dut.io.controls.wbSource.expect(WBSource.ALU)
      dut.io.controls.illegalInstruction.expect(false.B)

      // MUL x10, x11, x12 (RV32M lower-word multiply)
      dut.io.instruction.poke("h02C58533".U)
      dut.io.controls.aluOp.expect(ALUOps.MUL)
      dut.io.controls.isMul.expect(true.B)
      dut.io.controls.mOp.expect(MOp.MUL)
      dut.io.controls.regWrite.expect(true.B)
      dut.io.controls.illegalInstruction.expect(false.B)

      // OP_SECURITY reservation: isSecurityOp=1 but regWrite/memWrite=0
      dut.io.instruction.poke("h0000000B".U)
      dut.io.controls.isSecurityOp.expect(true.B)
      dut.io.controls.regWrite.expect(false.B)
      dut.io.controls.memRead.expect(false.B)
      dut.io.controls.memWrite.expect(false.B)
    }
  }

  it should "decode all 8 RV32M instructions when enableFullM = true" in {
    test(new Decoder(enableFullM = true)) { dut =>
      val mTests = Seq(
        ("h02C58533", MOp.MUL,    "MUL"),
        ("h02C59533", MOp.MULH,   "MULH"),
        ("h02C5A533", MOp.MULHSU, "MULHSU"),
        ("h02C5B533", MOp.MULHU,  "MULHU"),
        ("h02C5C533", MOp.DIV,    "DIV"),
        ("h02C5D533", MOp.DIVU,   "DIVU"),
        ("h02C5E533", MOp.REM,    "REM"),
        ("h02C5F533", MOp.REMU,   "REMU")
      )

      for ((hex, mOp, name) <- mTests) {
        dut.io.instruction.poke(hex.U)
        dut.io.controls.mOp.expect(mOp, s"$name: mOp mismatch")
        dut.io.controls.regWrite.expect(true.B, s"$name: regWrite should be true")
        dut.io.controls.illegalInstruction.expect(false.B, s"$name: should not be illegal")

        if (name.startsWith("MUL")) {
          dut.io.controls.isMul.expect(true.B, s"$name: isMul should be true")
        } else {
          dut.io.controls.isMul.expect(false.B, s"$name: isMul should be false")
        }
      }
    }
  }

  it should "decode CapabilityLite instructions including CGETOFFSET and CCLEAR" in {
    test(new Decoder(enableCapabilities = true)) { dut =>
      // CGETOFFSET x1, c2 -> opcode 0x0B, rd=1, funct3=7, rs1=2, funct7=0x00
      // 0000000 00010 00001 111 00001 0001011 = 0x0001708B
      dut.io.instruction.poke("h0001708B".U)
      dut.io.rd.expect(1.U)
      dut.io.rs1.expect(2.U)
      dut.io.controls.isCapOp.expect(true.B)
      dut.io.controls.capOp.expect(CapOp.CGETOFFSET)
      dut.io.controls.regWrite.expect(true.B)
      dut.io.controls.usesCapRs1.expect(true.B)
      dut.io.controls.illegalInstruction.expect(false.B)

      // CCLEAR c4 -> opcode 0x0B, rd=4, funct3=7, rs1=0, funct7=0x01
      // 0000001 00000 00000 111 00100 0001011 = 0x0200720B
      dut.io.instruction.poke("h0200720B".U)
      dut.io.rd.expect(4.U)
      dut.io.controls.isCapOp.expect(true.B)
      dut.io.controls.capOp.expect(CapOp.CCLEAR)
      dut.io.controls.capRegWrite.expect(true.B)
      dut.io.controls.illegalInstruction.expect(false.B)

      // Invalid CGETOFFSET with cs1 = 8 (> 7)
      // 0000000 00000 01000 111 00001 0001011 = 0x0004708B
      dut.io.instruction.poke("h0004708B".U)
      dut.io.controls.illegalInstruction.expect(true.B)

      // Invalid CCLEAR with cd = 8 (> 7)
      // 0000001 00000 00000 111 01000 0001011 = 0x0200740B
      dut.io.instruction.poke("h0200740B".U)
      dut.io.controls.illegalInstruction.expect(true.B)

      // Invalid funct7 = 0x02 on funct3 = 7
      // 0000010 00000 00001 111 00001 0001011 = 0x0400F08B
      dut.io.instruction.poke("h0400F08B".U)
      dut.io.controls.illegalInstruction.expect(true.B)
    }
  }

  it should "comprehensively reject illegal instructions with zero side effects" in {
    test(new Decoder) { dut =>
      // 1. Invalid R-type funct7 (0x05) on ADD
      verifyIllegal(dut, "h0A3100B3", "Invalid R-type funct7 0x05")

      // 2. Invalid R-type funct3 with funct7=0x20 (e.g. XOR with funct7=0x20)
      verifyIllegal(dut, "h403140B3", "Invalid XOR with funct7=0x20")

      // 3. Invalid SLLI funct7 (0x20 on SLLI)
      verifyIllegal(dut, "h40411093", "Invalid SLLI with funct7=0x20")

      // 4. Invalid SRLI/SRAI funct7 (0x10)
      verifyIllegal(dut, "h20415093", "Invalid SRLI with funct7=0x10")

      // 5. Invalid Load funct3 (funct3 = 3)
      verifyIllegal(dut, "h00413083", "Invalid Load funct3=3")

      // 6. Invalid Load funct3 (funct3 = 6)
      verifyIllegal(dut, "h00416083", "Invalid Load funct3=6")

      // 7. Invalid Store funct3 (funct3 = 3)
      verifyIllegal(dut, "h00313423", "Invalid Store funct3=3")

      // 8. Invalid Branch funct3 (funct3 = 2)
      verifyIllegal(dut, "h0020A863", "Invalid Branch funct3=2")

      // 9. JALR with funct3 != 0 (funct3 = 1)
      verifyIllegal(dut, "h000110E7", "Invalid JALR with funct3=1")

      // 10. Unsupported M-extension DIV (funct7=0x01, funct3=4)
      verifyIllegal(dut, "h02C5C533", "Unsupported M-extension DIV")

      // 11. Unsupported M-extension MULH (funct7=0x01, funct3=1)
      verifyIllegal(dut, "h02C59533", "Unsupported M-extension MULH")

      // 12. Unsupported SYSTEM instruction (ECALL / CSR: 0x73)
      verifyIllegal(dut, "h00000073", "Unsupported SYSTEM ECALL opcode 0x73")

      // 13. All-zeros opcode
      verifyIllegal(dut, "h00000000", "All-zeros instruction word")

      // 14. All-ones opcode
      verifyIllegal(dut, "hFFFFFFFF", "All-ones instruction word")
    }
  }
}
