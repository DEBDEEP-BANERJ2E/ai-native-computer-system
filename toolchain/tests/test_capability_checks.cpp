#include <cassert>
#include <iostream>
#include "an32/encoder.hpp"
#include "an32/decoder.hpp"

using namespace an32;

void test_capability_register_bounds() {
    // Valid c0..c7
    for (uint8_t i = 0; i < 8; ++i) {
        auto enc = Encoder::encode_csetbounds(CapReg(i), CapReg(i), XReg(1));
        assert(enc.is_ok());
        auto dec = Decoder::decode(enc.value());
        assert(dec.is_legal());
        assert(dec.rd_idx == i);
        assert(dec.rs1_idx == i);
    }

    // Invalid c8..c31 in encoder
    for (uint8_t i = 8; i < 32; ++i) {
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
    test_cap_mem_i_vs_s_distinction();
    std::cout << "[PASS] test_capability_checks passed successfully!\n";
    return 0;
}
