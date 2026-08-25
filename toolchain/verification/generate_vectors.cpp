#include <iostream>
#include <fstream>
#include <vector>
#include <random>
#include <iomanip>
#include <cstdint>
#include "an32/decoder.hpp"
#include "an32/encoder.hpp"

using namespace an32;

struct ControlSignals {
    int aluOp{0};
    int aluSrcA{0};
    int aluSrcB{0};
    bool regWrite{false};
    bool memRead{false};
    bool memWrite{false};
    int memWidth{2}; // WORD
    int branchType{0};
    int jumpType{0};
    int wbSource{0};
    bool isMul{false};
    int mOp{0};
    bool isSecurityOp{false};
    bool isCapOp{false};
    int capOp{0};
    bool isCapMem{false};
    bool capRegWrite{false};
    bool usesCapRs1{false};
    bool usesIntRs1{false};
    bool usesIntRs2{false};
    bool illegalInstruction{false};
};

ControlSignals compute_expected_controls(uint32_t word) {
    ControlSignals ctrl{};
    auto dec = Decoder::decode(word, MachineProfile::AN32_BARE_V1);

    if (!dec.is_legal()) {
        ctrl.illegalInstruction = true;
        return ctrl;
    }

    uint8_t op = static_cast<uint8_t>(word & 0x7F);
    uint8_t f3 = static_cast<uint8_t>((word >> 12) & 0x7);
    uint8_t f7 = static_cast<uint8_t>((word >> 25) & 0x7F);

    switch (dec.mnemonic) {
        // R-Type Integer
        case Mnemonic::ADD:
            ctrl.aluOp = 0; ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::SUB:
            ctrl.aluOp = 1; ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::SLL:
            ctrl.aluOp = 5; ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::SLT:
            ctrl.aluOp = 8; ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::SLTU:
            ctrl.aluOp = 9; ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::XOR:
            ctrl.aluOp = 4; ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::SRL:
            ctrl.aluOp = 6; ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::SRA:
            ctrl.aluOp = 7; ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::OR:
            ctrl.aluOp = 3; ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::AND:
            ctrl.aluOp = 2; ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;

        // RV32M Extension
        case Mnemonic::MUL:
            ctrl.aluOp = 10; ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.isMul = true; ctrl.mOp = 1;
            ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::MULH:
            ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.isMul = true; ctrl.mOp = 2;
            ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::MULHSU:
            ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.isMul = true; ctrl.mOp = 3;
            ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::MULHU:
            ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.isMul = true; ctrl.mOp = 4;
            ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::DIV:
            ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.mOp = 5;
            ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::DIVU:
            ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.mOp = 6;
            ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::REM:
            ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.mOp = 7;
            ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::REMU:
            ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.mOp = 8;
            ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;

        // I-Type Arithmetic
        case Mnemonic::ADDI:
            ctrl.aluOp = 0; ctrl.aluSrcA = 0; ctrl.aluSrcB = 1; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.usesIntRs1 = true;
            break;
        case Mnemonic::SLTI:
            ctrl.aluOp = 8; ctrl.aluSrcA = 0; ctrl.aluSrcB = 1; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.usesIntRs1 = true;
            break;
        case Mnemonic::SLTIU:
            ctrl.aluOp = 9; ctrl.aluSrcA = 0; ctrl.aluSrcB = 1; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.usesIntRs1 = true;
            break;
        case Mnemonic::XORI:
            ctrl.aluOp = 4; ctrl.aluSrcA = 0; ctrl.aluSrcB = 1; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.usesIntRs1 = true;
            break;
        case Mnemonic::ORI:
            ctrl.aluOp = 3; ctrl.aluSrcA = 0; ctrl.aluSrcB = 1; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.usesIntRs1 = true;
            break;
        case Mnemonic::ANDI:
            ctrl.aluOp = 2; ctrl.aluSrcA = 0; ctrl.aluSrcB = 1; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.usesIntRs1 = true;
            break;
        case Mnemonic::SLLI:
            ctrl.aluOp = 5; ctrl.aluSrcA = 0; ctrl.aluSrcB = 1; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.usesIntRs1 = true;
            break;
        case Mnemonic::SRLI:
            ctrl.aluOp = 6; ctrl.aluSrcA = 0; ctrl.aluSrcB = 1; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.usesIntRs1 = true;
            break;
        case Mnemonic::SRAI:
            ctrl.aluOp = 7; ctrl.aluSrcA = 0; ctrl.aluSrcB = 1; ctrl.regWrite = true;
            ctrl.wbSource = 0; ctrl.usesIntRs1 = true;
            break;

        // Loads
        case Mnemonic::LB:
            ctrl.aluOp = 0; ctrl.aluSrcA = 0; ctrl.aluSrcB = 1; ctrl.regWrite = true;
            ctrl.memRead = true; ctrl.memWidth = 0; ctrl.wbSource = 1; ctrl.usesIntRs1 = true;
            break;
        case Mnemonic::LH:
            ctrl.aluOp = 0; ctrl.aluSrcA = 0; ctrl.aluSrcB = 1; ctrl.regWrite = true;
            ctrl.memRead = true; ctrl.memWidth = 1; ctrl.wbSource = 1; ctrl.usesIntRs1 = true;
            break;
        case Mnemonic::LW:
            ctrl.aluOp = 0; ctrl.aluSrcA = 0; ctrl.aluSrcB = 1; ctrl.regWrite = true;
            ctrl.memRead = true; ctrl.memWidth = 2; ctrl.wbSource = 1; ctrl.usesIntRs1 = true;
            break;
        case Mnemonic::LBU:
            ctrl.aluOp = 0; ctrl.aluSrcA = 0; ctrl.aluSrcB = 1; ctrl.regWrite = true;
            ctrl.memRead = true; ctrl.memWidth = 3; ctrl.wbSource = 1; ctrl.usesIntRs1 = true;
            break;
        case Mnemonic::LHU:
            ctrl.aluOp = 0; ctrl.aluSrcA = 0; ctrl.aluSrcB = 1; ctrl.regWrite = true;
            ctrl.memRead = true; ctrl.memWidth = 4; ctrl.wbSource = 1; ctrl.usesIntRs1 = true;
            break;

        // Stores
        case Mnemonic::SB:
            ctrl.aluOp = 0; ctrl.aluSrcA = 0; ctrl.aluSrcB = 1;
            ctrl.memWrite = true; ctrl.memWidth = 0; ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::SH:
            ctrl.aluOp = 0; ctrl.aluSrcA = 0; ctrl.aluSrcB = 1;
            ctrl.memWrite = true; ctrl.memWidth = 1; ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::SW:
            ctrl.aluOp = 0; ctrl.aluSrcA = 0; ctrl.aluSrcB = 1;
            ctrl.memWrite = true; ctrl.memWidth = 2; ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;

        // Branches
        case Mnemonic::BEQ:
            ctrl.aluOp = 1; ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.branchType = 1;
            ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::BNE:
            ctrl.aluOp = 1; ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.branchType = 2;
            ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::BLT:
            ctrl.aluOp = 8; ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.branchType = 3;
            ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::BGE:
            ctrl.aluOp = 8; ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.branchType = 4;
            ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::BLTU:
            ctrl.aluOp = 9; ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.branchType = 5;
            ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::BGEU:
            ctrl.aluOp = 9; ctrl.aluSrcA = 0; ctrl.aluSrcB = 0; ctrl.branchType = 6;
            ctrl.usesIntRs1 = true; ctrl.usesIntRs2 = true;
            break;

        // Jumps
        case Mnemonic::JAL:
            ctrl.aluOp = 0; ctrl.aluSrcA = 1; ctrl.aluSrcB = 2; ctrl.jumpType = 1;
            ctrl.regWrite = true; ctrl.wbSource = 2;
            break;
        case Mnemonic::JALR:
            ctrl.aluOp = 0; ctrl.aluSrcA = 1; ctrl.aluSrcB = 2; ctrl.jumpType = 2;
            ctrl.regWrite = true; ctrl.wbSource = 2; ctrl.usesIntRs1 = true;
            break;

        // Upper Immediates
        case Mnemonic::LUI:
            ctrl.aluOp = 0; ctrl.aluSrcA = 2; ctrl.aluSrcB = 1; ctrl.regWrite = true;
            ctrl.wbSource = 3;
            break;
        case Mnemonic::AUIPC:
            ctrl.aluOp = 0; ctrl.aluSrcA = 1; ctrl.aluSrcB = 1; ctrl.regWrite = true;
            ctrl.wbSource = 0;
            break;

        // Capability Manipulation
        case Mnemonic::CSETBOUNDS:
            ctrl.isCapOp = true; ctrl.capOp = 1; ctrl.capRegWrite = true;
            ctrl.usesCapRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::CANDPERM:
            ctrl.isCapOp = true; ctrl.capOp = 2; ctrl.capRegWrite = true;
            ctrl.usesCapRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::CINCOFFSET:
            ctrl.isCapOp = true; ctrl.capOp = 3; ctrl.capRegWrite = true;
            ctrl.usesCapRs1 = true; ctrl.usesIntRs2 = true;
            break;
        case Mnemonic::CGETBASE:
            ctrl.isCapOp = true; ctrl.capOp = 4; ctrl.regWrite = true;
            ctrl.usesCapRs1 = true; ctrl.wbSource = 0;
            break;
        case Mnemonic::CGETLEN:
            ctrl.isCapOp = true; ctrl.capOp = 5; ctrl.regWrite = true;
            ctrl.usesCapRs1 = true; ctrl.wbSource = 0;
            break;
        case Mnemonic::CGETTAG:
            ctrl.isCapOp = true; ctrl.capOp = 6; ctrl.regWrite = true;
            ctrl.usesCapRs1 = true; ctrl.wbSource = 0;
            break;
        case Mnemonic::CGETPERM:
            ctrl.isCapOp = true; ctrl.capOp = 7; ctrl.regWrite = true;
            ctrl.usesCapRs1 = true; ctrl.wbSource = 0;
            break;
        case Mnemonic::CGETOFFSET:
            ctrl.isCapOp = true; ctrl.capOp = 8; ctrl.regWrite = true;
            ctrl.usesCapRs1 = true; ctrl.wbSource = 0;
            break;
        case Mnemonic::CCLEAR:
            ctrl.isCapOp = true; ctrl.capOp = 9; ctrl.capRegWrite = true;
            break;

        // Capability Memory Loads
        case Mnemonic::CLB:
            ctrl.isCapMem = true; ctrl.usesCapRs1 = true; ctrl.regWrite = true;
            ctrl.memRead = true; ctrl.memWidth = 0; ctrl.wbSource = 1;
            break;
        case Mnemonic::CLH:
            ctrl.isCapMem = true; ctrl.usesCapRs1 = true; ctrl.regWrite = true;
            ctrl.memRead = true; ctrl.memWidth = 1; ctrl.wbSource = 1;
            break;
        case Mnemonic::CLW:
            ctrl.isCapMem = true; ctrl.usesCapRs1 = true; ctrl.regWrite = true;
            ctrl.memRead = true; ctrl.memWidth = 2; ctrl.wbSource = 1;
            break;

        // Capability Memory Stores
        case Mnemonic::CSB:
            ctrl.isCapMem = true; ctrl.usesCapRs1 = true; ctrl.usesIntRs2 = true;
            ctrl.memWrite = true; ctrl.memWidth = 0;
            break;
        case Mnemonic::CSH:
            ctrl.isCapMem = true; ctrl.usesCapRs1 = true; ctrl.usesIntRs2 = true;
            ctrl.memWrite = true; ctrl.memWidth = 1;
            break;
        case Mnemonic::CSW:
            ctrl.isCapMem = true; ctrl.usesCapRs1 = true; ctrl.usesIntRs2 = true;
            ctrl.memWrite = true; ctrl.memWidth = 2;
            break;

        default:
            ctrl.illegalInstruction = true;
            break;
    }

    return ctrl;
}

uint32_t compute_reconstructed_imm(uint32_t word) {
    uint8_t op = static_cast<uint8_t>(word & 0x7F);

    if (op == 0x13 || op == 0x03 || op == 0x67) {
        // I-type
        int32_t imm = static_cast<int32_t>(word) >> 20;
        return static_cast<uint32_t>(imm);
    } else if (op == 0x23) {
        // S-type
        uint32_t imm12 = ((word >> 25) << 5) | ((word >> 7) & 0x1F);
        int32_t sign_ext = (static_cast<int32_t>(imm12 << 20)) >> 20;
        return static_cast<uint32_t>(sign_ext);
    } else if (op == 0x63) {
        // B-type
        uint32_t bit11 = (word >> 7) & 0x1;
        uint32_t bits4_1 = (word >> 8) & 0xF;
        uint32_t bits10_5 = (word >> 25) & 0x3F;
        uint32_t bit12 = (word >> 31) & 0x1;
        uint32_t imm13 = (bit12 << 12) | (bit11 << 11) | (bits10_5 << 5) | (bits4_1 << 1);
        int32_t sign_ext = (static_cast<int32_t>(imm13 << 19)) >> 19;
        return static_cast<uint32_t>(sign_ext);
    } else if (op == 0x37 || op == 0x17) {
        // U-type
        return word & 0xFFFFF000;
    } else if (op == 0x6F) {
        // J-type
        uint32_t bits19_12 = (word >> 12) & 0xFF;
        uint32_t bit11 = (word >> 20) & 0x1;
        uint32_t bits10_1 = (word >> 21) & 0x3FF;
        uint32_t bit20 = (word >> 31) & 0x1;
        uint32_t imm21 = (bit20 << 20) | (bits19_12 << 12) | (bit11 << 11) | (bits10_1 << 1);
        int32_t sign_ext = (static_cast<int32_t>(imm21 << 11)) >> 11;
        return static_cast<uint32_t>(sign_ext);
    } else if (op == 0x73) {
        // CSR format: inst[19:15] zero-extended
        return (word >> 15) & 0x1F;
    } else if (op == 0x2B) {
        // CAP_MEM: Loads (f3 bit 2 == 0) use I-type imm, Stores (f3 bit 2 == 1) use S-type imm
        if ((word & (1 << 14)) == 0) {
            int32_t imm = static_cast<int32_t>(word) >> 20;
            return static_cast<uint32_t>(imm);
        } else {
            uint32_t imm12 = ((word >> 25) << 5) | ((word >> 7) & 0x1F);
            int32_t sign_ext = (static_cast<int32_t>(imm12 << 20)) >> 20;
            return static_cast<uint32_t>(sign_ext);
        }
    }
    return 0;
}

int main(int argc, char* argv[]) {
    std::string out_path = (argc > 1) ? argv[1] : "test_vectors.csv";
    std::ofstream out(out_path);
    if (!out.is_open()) {
        std::cerr << "Failed to open " << out_path << " for writing\n";
        return 1;
    }

    std::vector<uint32_t> test_words;

    // 1. Structured Enumeration
    // 1a. All 60 instructions with legal operands
    for (size_t i = 0; i < BARE_V1_INSTRUCTION_COUNT; ++i) {
        Mnemonic m = static_cast<Mnemonic>(i);
        const auto& desc = get_instruction_descriptor(m);
        for (uint8_t rd = 0; rd <= desc.rd_constraint_max; rd += 3) {
            for (uint8_t rs1 = 0; rs1 <= desc.rs1_constraint_max; rs1 += 3) {
                for (uint8_t rs2 = 0; rs2 <= desc.rs2_constraint_max; rs2 += 7) {
                    uint32_t w = desc.opcode & 0x7F;
                    w |= (rd & 0x1F) << 7;
                    w |= (desc.funct3 & 0x7) << 12;
                    w |= (rs1 & 0x1F) << 15;
                    w |= (rs2 & 0x1F) << 20;
                    w |= (desc.funct7 & 0x7F) << 25;
                    test_words.push_back(w);
                }
            }
        }
    }

    // 1b. Capability indices c0..c31 on custom opcodes
    for (uint32_t cs1 = 0; cs1 < 32; ++cs1) {
        for (uint32_t cd = 0; cd < 32; ++cd) {
            // CSETBOUNDS
            test_words.push_back(0x0B | (cd << 7) | (0 << 12) | (cs1 << 15) | (1 << 20) | (0 << 25));
            // CLW
            test_words.push_back(0x2B | (1 << 7) | (2 << 12) | (cs1 << 15) | (16 << 20));
            // CSW
            test_words.push_back(0x2B | (0 << 7) | (6 << 12) | (cs1 << 15) | (1 << 20) | (0 << 25));
        }
    }

    // 1b-ii. Exhaustive CCLEAR sweep across all cd (0..31), rs1 (0..31), and rs2 (0..31)
    // For cd=0..7: all 8*32*32 = 8,192 cases are hardware legal.
    // For cd=8..31: all 24*32*32 = 24,576 cases are hardware illegal.
    for (uint32_t cd = 0; cd < 32; ++cd) {
        for (uint32_t rs1 = 0; rs1 < 32; ++rs1) {
            for (uint32_t rs2 = 0; rs2 < 32; ++rs2) {
                uint32_t w = 0x0B | (cd << 7) | (7 << 12) | (rs1 << 15) | (rs2 << 20) | (1 << 25);
                test_words.push_back(w);
            }
        }
    }

    // 1b-iii. Exhaustive CGET* ignored rs2 field sweep
    uint32_t cget_f3[] = {3, 4, 5, 6, 7}; // cgetbase, cgetlen, cgettag, cgetperm, cgetoffset
    for (uint32_t f3 : cget_f3) {
        for (uint32_t cs1 = 0; cs1 < 32; ++cs1) {
            for (uint32_t rs2 = 0; rs2 < 32; ++rs2) {
                uint32_t w = 0x0B | (1 << 7) | (f3 << 12) | (cs1 << 15) | (rs2 << 20) | (0 << 25);
                test_words.push_back(w);
            }
        }
    }

    // 1c. All 128 major opcodes
    for (uint32_t op = 0; op < 128; ++op) {
        test_words.push_back(op | (1 << 7) | (0 << 12) | (2 << 15) | (3 << 20));
    }

    // 1d. All funct3 values (0..7) for R-type, I-type, Loads, Stores, Branches, CAP_MEM
    uint32_t test_opcodes[] = {0x33, 0x13, 0x03, 0x23, 0x63, 0x0B, 0x2B, 0x73};
    for (uint32_t op : test_opcodes) {
        for (uint32_t f3 = 0; f3 < 8; ++f3) {
            test_words.push_back(op | (1 << 7) | (f3 << 12) | (2 << 15) | (3 << 20));
            test_words.push_back(op | (1 << 7) | (f3 << 12) | (2 << 15) | (3 << 20) | (0x20 << 25));
            test_words.push_back(op | (1 << 7) | (f3 << 12) | (2 << 15) | (3 << 20) | (0x01 << 25));
        }
    }

    // 1e. Immediate boundary extrema
    int32_t imm_extrema[] = {-2048, 2047, 0, -1, 1, -4096, 4094, -1048576, 1048574};
    for (int32_t imm : imm_extrema) {
        if (imm >= -2048 && imm <= 2047) {
            test_words.push_back(Encoder::encode_i(Mnemonic::ADDI, XReg(1), XReg(2), IImm12(imm)).value());
            test_words.push_back(Encoder::encode_s(Mnemonic::SW, XReg(1), XReg(2), SImm12(imm)).value());
        }
        if (imm >= -4096 && imm <= 4094 && imm % 2 == 0) {
            test_words.push_back(Encoder::encode_b(Mnemonic::BEQ, XReg(1), XReg(2), BranchOffset13(imm)).value());
        }
        if (imm >= -1048576 && imm <= 1048574 && imm % 2 == 0) {
            test_words.push_back(Encoder::encode_j(XReg(1), JumpOffset21(imm)).value());
        }
    }

    // 2. Randomized Fuzzing Corpus (15,000+ words)
    std::mt19937 rng(1337);
    std::uniform_int_distribution<uint32_t> dist(0, 0xFFFFFFFF);
    for (size_t i = 0; i < 15000; ++i) {
        test_words.push_back(dist(rng));
    }

    // Write all vectors
    for (uint32_t w : test_words) {
        uint8_t rd = static_cast<uint8_t>((w >> 7) & 0x1F);
        uint8_t rs1 = static_cast<uint8_t>((w >> 15) & 0x1F);
        uint8_t rs2 = static_cast<uint8_t>((w >> 20) & 0x1F);
        uint32_t imm = compute_reconstructed_imm(w);
        ControlSignals ctrl = compute_expected_controls(w);

        out << "0x" << std::hex << std::setw(8) << std::setfill('0') << std::uppercase << w << ","
            << std::dec << static_cast<int>(rd) << ","
            << static_cast<int>(rs1) << ","
            << static_cast<int>(rs2) << ","
            << "0x" << std::hex << std::setw(8) << std::setfill('0') << std::uppercase << imm << ","
            << std::dec << ctrl.aluOp << ","
            << ctrl.aluSrcA << ","
            << ctrl.aluSrcB << ","
            << (ctrl.regWrite ? "true" : "false") << ","
            << (ctrl.memRead ? "true" : "false") << ","
            << (ctrl.memWrite ? "true" : "false") << ","
            << ctrl.memWidth << ","
            << ctrl.branchType << ","
            << ctrl.jumpType << ","
            << ctrl.wbSource << ","
            << (ctrl.isMul ? "true" : "false") << ","
            << ctrl.mOp << ","
            << (ctrl.isSecurityOp ? "true" : "false") << ","
            << (ctrl.isCapOp ? "true" : "false") << ","
            << ctrl.capOp << ","
            << (ctrl.isCapMem ? "true" : "false") << ","
            << (ctrl.capRegWrite ? "true" : "false") << ","
            << (ctrl.usesCapRs1 ? "true" : "false") << ","
            << (ctrl.usesIntRs1 ? "true" : "false") << ","
            << (ctrl.usesIntRs2 ? "true" : "false") << ","
            << (ctrl.illegalInstruction ? "true" : "false") << "\n";
    }

    out.close();
    std::cout << "Generated " << test_words.size() << " differential test vectors to " << out_path << "\n";
    return 0;
}
