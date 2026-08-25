#include <cassert>
#include <iostream>
#include "an32/encoder.hpp"
#include "an32/decoder.hpp"

using namespace an32;

void test_i_type_immediates() {
    int32_t values[] = {-2048, 2047, 0, -1, 1, -100, 100};
    for (int32_t val : values) {
        auto enc = Encoder::encode_i(Mnemonic::ADDI, XReg(1), XReg(2), IImm12(val));
        assert(enc.is_ok());
        auto dec = Decoder::decode(enc.value());
        assert(dec.is_legal());
        assert(dec.immediate == val);
    }

    // Out of range checks
    assert(Encoder::encode_i(Mnemonic::ADDI, XReg(1), XReg(2), IImm12(2048)).is_err());
    assert(Encoder::encode_i(Mnemonic::ADDI, XReg(1), XReg(2), IImm12(-2049)).is_err());
}

void test_s_type_immediates() {
    int32_t values[] = {-2048, 2047, 0, -1, 1, -100, 100};
    for (int32_t val : values) {
        auto enc = Encoder::encode_s(Mnemonic::SW, XReg(1), XReg(2), SImm12(val));
        assert(enc.is_ok());
        auto dec = Decoder::decode(enc.value());
        assert(dec.is_legal());
        assert(dec.immediate == val);
    }

    assert(Encoder::encode_s(Mnemonic::SW, XReg(1), XReg(2), SImm12(2048)).is_err());
    assert(Encoder::encode_s(Mnemonic::SW, XReg(1), XReg(2), SImm12(-2049)).is_err());
}

void test_b_type_immediates() {
    int32_t values[] = {-4096, 4094, 0, -2, 2, -100, 100};
    for (int32_t val : values) {
        auto enc = Encoder::encode_b(Mnemonic::BEQ, XReg(1), XReg(2), BranchOffset13(val));
        assert(enc.is_ok());
        auto dec = Decoder::decode(enc.value());
        assert(dec.is_legal());
        assert(dec.immediate == val);
    }

    // Out of range and misalignment
    assert(Encoder::encode_b(Mnemonic::BEQ, XReg(1), XReg(2), BranchOffset13(4096)).is_err());
    assert(Encoder::encode_b(Mnemonic::BEQ, XReg(1), XReg(2), BranchOffset13(-4098)).is_err());
    assert(Encoder::encode_b(Mnemonic::BEQ, XReg(1), XReg(2), BranchOffset13(3)).is_err());
    assert(Encoder::encode_b(Mnemonic::BEQ, XReg(1), XReg(2), BranchOffset13(-3)).is_err());
}

void test_u_type_immediates() {
    uint32_t imm20_values[] = {0, 0xFFFFF, 0x12345, 0x80000, 1};
    for (uint32_t imm20 : imm20_values) {
        auto enc = Encoder::encode_u(Mnemonic::LUI, XReg(1), UImm20(imm20));
        assert(enc.is_ok());
        auto dec = Decoder::decode(enc.value());
        assert(dec.is_legal());
        assert(dec.immediate == static_cast<int32_t>(imm20 << 12));
    }

    assert(Encoder::encode_u(Mnemonic::LUI, XReg(1), UImm20(0x100000)).is_err());
}

void test_j_type_immediates() {
    int32_t values[] = {-1048576, 1048574, 0, -2, 2, -100, 100};
    for (int32_t val : values) {
        auto enc = Encoder::encode_j(XReg(1), JumpOffset21(val));
        assert(enc.is_ok());
        auto dec = Decoder::decode(enc.value());
        assert(dec.is_legal());
        assert(dec.immediate == val);
    }

    assert(Encoder::encode_j(XReg(1), JumpOffset21(1048576)).is_err());
    assert(Encoder::encode_j(XReg(1), JumpOffset21(-1048578)).is_err());
    assert(Encoder::encode_j(XReg(1), JumpOffset21(5)).is_err());
}

void test_shift_amounts() {
    for (uint8_t shamt = 0; shamt <= 31; ++shamt) {
        auto enc = Encoder::encode_shift(Mnemonic::SLLI, XReg(1), XReg(2), ShiftAmount5(shamt));
        assert(enc.is_ok());
        auto dec = Decoder::decode(enc.value());
        assert(dec.is_legal());
        assert(dec.shamt == shamt);
    }

    assert(Encoder::encode_shift(Mnemonic::SLLI, XReg(1), XReg(2), ShiftAmount5(32)).is_err());
}

int main() {
    std::cout << "[RUN] test_immediates\n";
    test_i_type_immediates();
    test_s_type_immediates();
    test_b_type_immediates();
    test_u_type_immediates();
    test_j_type_immediates();
    test_shift_amounts();
    std::cout << "[PASS] test_immediates passed successfully!\n";
    return 0;
}
