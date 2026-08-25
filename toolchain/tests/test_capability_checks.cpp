#include <cassert>
#include <iostream>
#include "an32/encoder.hpp"
#include "an32/decoder.hpp"

using namespace an32;

void test_capability_register_bounds() {
    // Valid c0..c7
    for (uint32_t i = 0; i < 8; ++i) {
        auto enc = Encoder::encode_csetbounds(CapReg(i), CapReg(i), XReg(1));
        assert(enc.is_ok());
        auto dec = Decoder::decode(enc.value());
        assert(dec.is_legal());
        assert(dec.rd_idx == i);
        assert(dec.rs1_idx == i);
    }

    // Invalid c8..c31 in encoder
    for (uint32_t i = 8; i < 32; ++i) {
        assert(Encoder::encode_csetbounds(CapReg(i), CapReg(0), XReg(1)).is_err());
        assert(Encoder::encode_csetbounds(CapReg(0), CapReg(i), XReg(1)).is_err());
        assert(Encoder::encode_cclear(CapReg(i)).is_err());
        assert(Encoder::encode_cap_load(Mnemonic::CLW, XReg(1), CapReg(i), IImm12(0)).is_err());
        assert(Encoder::encode_cap_store(Mnemonic::CSW, XReg(1), CapReg(i), SImm12(0)).is_err());
    }

    // Raw machine words with rs1/rd >= 8 in capability instructions must be rejected by decoder
    // CSETBOUNDS with cd = 8 (bit 10:7 = 8) -> opcode 0x0B
    uint32_t raw_bad_cd = 0x0010840B; // cd=8
    auto dec_bad_cd = Decoder::decode(raw_bad_cd);
    assert(!dec_bad_cd.is_legal());
    assert(dec_bad_cd.status == DecodeStatus::ILLEGAL_REGISTER);

    // CSETBOUNDS with cs1 = 8 (bit 19:15 = 8)
    uint32_t raw_bad_cs1 = 0x0014018B; // cs1=8
    auto dec_bad_cs1 = Decoder::decode(raw_bad_cs1);
    assert(!dec_bad_cs1.is_legal());
    assert(dec_bad_cs1.status == DecodeStatus::ILLEGAL_REGISTER);
}

void test_cclear_ignored_fields() {
    // CCLEAR: cd in c0..c7, funct3=7, funct7=1, opcode=0x0B
    // rs1 and rs2 are ignored by hardware and accept 0..31
    for (uint32_t cd = 0; cd < 8; ++cd) {
        for (uint32_t rs1 = 0; rs1 < 32; ++rs1) {
            for (uint32_t rs2 = 0; rs2 < 32; ++rs2) {
                uint32_t word = 0x0B | (cd << 7) | (7 << 12) | (rs1 << 15) | (rs2 << 20) | (1 << 25);
                auto dec = Decoder::decode(word);
                assert(dec.is_legal());
                assert(dec.mnemonic == Mnemonic::CCLEAR);
                assert(dec.rd_idx == cd);
                assert(dec.rs1_idx == rs1);
                assert(dec.rs2_idx == rs2);

                if (rs1 == 0 && rs2 == 0) {
                    assert(dec.status == DecodeStatus::CANONICAL);
                } else {
                    assert(dec.status == DecodeStatus::NON_CANONICAL_IGNORED_FIELDS);
                }
            }
        }
    }

    // CCLEAR with invalid cd (c8..c31) must be rejected
    for (uint32_t cd = 8; cd < 32; ++cd) {
        uint32_t word = 0x0B | (cd << 7) | (7 << 12) | (0 << 15) | (0 << 20) | (1 << 25);
        auto dec = Decoder::decode(word);
        assert(!dec.is_legal());
        assert(dec.status == DecodeStatus::ILLEGAL_REGISTER);
    }
}

void test_typed_wrappers_no_narrowing() {
    // XReg hardening
    assert(XReg(0).is_valid());
    assert(XReg(31).is_valid());
    assert(!XReg(32).is_valid());
    assert(!XReg(255).is_valid());
    assert(!XReg(256).is_valid());
    assert(!XReg(257).is_valid());
    assert(!XReg(100000).is_valid());
    assert(XReg::from_index(31).has_value());
    assert(!XReg::from_index(32).has_value());
    assert(!XReg::from_index(256).has_value());

    // CapReg hardening
    assert(CapReg(0).is_valid());
    assert(CapReg(7).is_valid());
    assert(!CapReg(8).is_valid());
    assert(!CapReg(255).is_valid());
    assert(!CapReg(256).is_valid());
    assert(!CapReg(257).is_valid());
    assert(!CapReg(100000).is_valid());
    assert(CapReg::from_index(7).has_value());
    assert(!CapReg::from_index(8).has_value());
    assert(!CapReg::from_index(256).has_value());

    // ShiftAmount5 hardening
    assert(ShiftAmount5(0).is_valid());
    assert(ShiftAmount5(31).is_valid());
    assert(!ShiftAmount5(32).is_valid());
    assert(!ShiftAmount5(255).is_valid());
    assert(!ShiftAmount5(256).is_valid());
    assert(!ShiftAmount5(257).is_valid());
    assert(!ShiftAmount5(100000).is_valid());
    assert(ShiftAmount5::from_value(31).has_value());
    assert(!ShiftAmount5::from_value(32).has_value());
    assert(!ShiftAmount5::from_value(256).has_value());

    // Immediate wrappers without 32-bit/64-bit overflow narrowing
    assert(IImm12(-2048).is_valid());
    assert(IImm12(2047).is_valid());
    assert(!IImm12(-2049).is_valid());
    assert(!IImm12(2048).is_valid());
    assert(!IImm12(1LL << 33).is_valid());
    assert(!IImm12(-(1LL << 33)).is_valid());

    assert(SImm12(-2048).is_valid());
    assert(SImm12(2047).is_valid());
    assert(!SImm12(-2049).is_valid());
    assert(!SImm12(2048).is_valid());
    assert(!SImm12(1LL << 33).is_valid());

    assert(BranchOffset13(-4096).is_valid());
    assert(BranchOffset13(4094).is_valid());
    assert(!BranchOffset13(-4098).is_valid());
    assert(!BranchOffset13(4096).is_valid());
    assert(!BranchOffset13(3).is_valid()); // Odd
    assert(!BranchOffset13(1LL << 33).is_valid());

    assert(JumpOffset21(-1048576).is_valid());
    assert(JumpOffset21(1048574).is_valid());
    assert(!JumpOffset21(-1048578).is_valid());
    assert(!JumpOffset21(1048576).is_valid());
    assert(!JumpOffset21(3).is_valid()); // Odd
    assert(!JumpOffset21(1LL << 33).is_valid());

    assert(UImm20(0).is_valid());
    assert(UImm20(0xFFFFF).is_valid());
    assert(!UImm20(0x100000).is_valid());
    assert(!UImm20(1ULL << 33).is_valid());

    // Verify encoder rejects overflow operands
    assert(Encoder::encode_shift(Mnemonic::SLLI, XReg(1), XReg(2), ShiftAmount5(256)).is_err());
    assert(Encoder::encode_r(Mnemonic::ADD, XReg(256), XReg(1), XReg(2)).is_err());
    assert(Encoder::encode_csetbounds(CapReg(256), CapReg(0), XReg(1)).is_err());
}

void test_cap_mem_i_vs_s_distinction() {
    // CLW uses I-type layout: imm[11:0] in [31:20]
    // CSW uses S-type layout: imm[11:5] in [31:25], imm[4:0] in [11:7]
    int32_t test_imm = 0x123; // 291 -> imm_11_5 = 0x09, imm_4_0 = 0x03

    auto clw_enc = Encoder::encode_cap_load(Mnemonic::CLW, XReg(5), CapReg(2), IImm12(test_imm));
    assert(clw_enc.is_ok());
    auto clw_dec = Decoder::decode(clw_enc.value());
    assert(clw_dec.is_legal());
    assert(clw_dec.mnemonic == Mnemonic::CLW);
    assert(clw_dec.format == InstructionFormat::CAP_MEM_I);
    assert(clw_dec.immediate == test_imm);
    assert(clw_dec.rd_idx == 5);
    assert(clw_dec.rs1_idx == 2);

    auto csw_enc = Encoder::encode_cap_store(Mnemonic::CSW, XReg(5), CapReg(2), SImm12(test_imm));
    assert(csw_enc.is_ok());
    auto csw_dec = Decoder::decode(csw_enc.value());
    assert(csw_dec.is_legal());
    assert(csw_dec.mnemonic == Mnemonic::CSW);
    assert(csw_dec.format == InstructionFormat::CAP_MEM_S);
    assert(csw_dec.immediate == test_imm);
    assert(csw_dec.rs2_idx == 5);
    assert(csw_dec.rs1_idx == 2);

    // Verify raw bit difference between load and store with same operands
    assert(clw_enc.value() != csw_enc.value());
}

int main() {
    std::cout << "[RUN] test_capability_checks\n";
    test_capability_register_bounds();
    test_cclear_ignored_fields();
    test_typed_wrappers_no_narrowing();
    test_cap_mem_i_vs_s_distinction();
    std::cout << "[PASS] test_capability_checks passed successfully!\n";
    return 0;
}
