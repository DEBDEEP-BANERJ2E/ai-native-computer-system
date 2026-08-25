#include <cassert>
#include <iostream>
#include "an32/decoder.hpp"
#include "an32/encoder.hpp"

using namespace an32;

void test_all_bare_instructions_decode() {
    for (size_t i = 0; i < BARE_V1_INSTRUCTION_COUNT; ++i) {
        Mnemonic m = static_cast<Mnemonic>(i);
        const auto& desc = get_instruction_descriptor(m);
        assert(desc.is_bare_profile);

        // Build a canonical word with rd=1, rs1=2, rs2=3, imm=4
        uint32_t word = desc.opcode & 0x7F;
        uint8_t rd = (desc.uses_rd_cap) ? 1 : 1;
        uint8_t rs1 = (desc.uses_rs1_cap) ? 2 : 2;
        uint8_t rs2 = (desc.uses_rs2_cap) ? 3 : 3;

        if (desc.format == InstructionFormat::R || desc.format == InstructionFormat::CAP_R) {
            if (desc.mnemonic == Mnemonic::CCLEAR) {
                word |= (rd << 7) | ((desc.funct3 & 0x7) << 12) | ((desc.funct7 & 0x7F) << 25);
            } else if (desc.format == InstructionFormat::CAP_R && !desc.uses_rd_cap) { // CGET*
                word |= (rd << 7) | ((desc.funct3 & 0x7) << 12) | (rs1 << 15) | ((desc.funct7 & 0x7F) << 25);
            } else {
                word |= (rd << 7) | ((desc.funct3 & 0x7) << 12) | (rs1 << 15) | (rs2 << 20) | ((desc.funct7 & 0x7F) << 25);
            }
        } else if (desc.format == InstructionFormat::I || desc.format == InstructionFormat::CAP_MEM_I) {
            word |= (rd << 7) | ((desc.funct3 & 0x7) << 12) | (rs1 << 15) | (4 << 20);
            if (desc.has_funct7) word |= ((desc.funct7 & 0x7F) << 25);
        } else if (desc.format == InstructionFormat::S || desc.format == InstructionFormat::CAP_MEM_S) {
            word |= (4 << 7) | ((desc.funct3 & 0x7) << 12) | (rs1 << 15) | (rs2 << 20);
        } else if (desc.format == InstructionFormat::B) {
            // Branch offset 4 -> bit11=0, bits4_1=2, bits10_5=0, bit12=0
            word |= (2 << 8) | ((desc.funct3 & 0x7) << 12) | (rs1 << 15) | (rs2 << 20);
        } else if (desc.format == InstructionFormat::U) {
            word |= (rd << 7) | (0x12345 << 12);
        } else if (desc.format == InstructionFormat::J) {
            // JAL offset 4 -> bits10_1=2
            word |= (rd << 7) | (2 << 21);
        }

        auto decoded = Decoder::decode(word, MachineProfile::AN32_BARE_V1);
        assert(decoded.is_legal());
        assert(decoded.is_canonical());
        assert(decoded.mnemonic == m);
    }
}

void test_non_canonical_ignored_fields() {
    // CCLEAR c3 is 0x0200718B (canonical: rs1=0, rs2=0)
    // Non-canonical CCLEAR with rs1=5 (0x0202F18B) should decode as legal but NON_CANONICAL
    uint32_t non_canon_cclear = 0x0202F18B;
    auto dec1 = Decoder::decode(non_canon_cclear, MachineProfile::AN32_BARE_V1);
    assert(dec1.is_legal());
    assert(!dec1.is_canonical());
    assert(dec1.status == DecodeStatus::NON_CANONICAL_IGNORED_FIELDS);
    assert(dec1.mnemonic == Mnemonic::CCLEAR);

    // CGETBASE x10, c3 with non-zero rs2 field (rs2=7)
    // Canonical: 0x0001B50B. Non-canonical with rs2=7: 0x0071B50B
    uint32_t non_canon_cgetbase = 0x0071B50B;
    auto dec2 = Decoder::decode(non_canon_cgetbase, MachineProfile::AN32_BARE_V1);
    assert(dec2.is_legal());
    assert(!dec2.is_canonical());
    assert(dec2.status == DecodeStatus::NON_CANONICAL_IGNORED_FIELDS);
    assert(dec2.mnemonic == Mnemonic::CGETBASE);
}

void test_illegal_profile_system_instructions() {
    // ECALL is 0x00000073
    auto dec_bare = Decoder::decode(0x00000073, MachineProfile::AN32_BARE_V1);
    assert(!dec_bare.is_legal());
    assert(dec_bare.status == DecodeStatus::ILLEGAL_PROFILE);

    auto dec_sys = Decoder::decode(0x00000073, MachineProfile::AN32_SYSTEM_V1);
    assert(dec_sys.is_legal());
    assert(dec_sys.mnemonic == Mnemonic::ECALL);
}

int main() {
    std::cout << "[RUN] test_decoder\n";
    test_all_bare_instructions_decode();
    test_non_canonical_ignored_fields();
    test_illegal_profile_system_instructions();
    std::cout << "[PASS] test_decoder passed successfully!\n";
    return 0;
}
