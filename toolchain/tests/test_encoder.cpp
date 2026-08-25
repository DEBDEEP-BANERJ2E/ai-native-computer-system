#include <cassert>
#include <iostream>
#include "an32/encoder.hpp"
#include "an32/decoder.hpp"

using namespace an32;

void test_r_type_encoding() {
    // ADD x1, x2, x3 -> opcode 0x33, rd=1, funct3=0, rs1=2, rs2=3, funct7=0x00
    // 0000000 00011 00010 000 00001 0110011 -> 0x003100B3
    auto res = Encoder::encode_r(Mnemonic::ADD, XReg(1), XReg(2), XReg(3));
    assert(res.is_ok());
    assert(res.value() == 0x003100B3);

    // SUB x4, x5, x6 -> funct7 = 0x20
    // 0100000 00110 00101 000 00100 0110011 -> 0x40628233
    auto res_sub = Encoder::encode_r(Mnemonic::SUB, XReg(4), XReg(5), XReg(6));
    assert(res_sub.is_ok());
    assert(res_sub.value() == 0x40628233);

    // MUL x10, x11, x12 -> funct7 = 0x01
    // 0000001 01100 01011 000 01010 0110011 -> 0x02C58533
    auto res_mul = Encoder::encode_r(Mnemonic::MUL, XReg(10), XReg(11), XReg(12));
    assert(res_mul.is_ok());
    assert(res_mul.value() == 0x02C58533);
}

void test_i_type_encoding() {
    // ADDI x10, x0, 42 -> 0x02A00513
    auto res = Encoder::encode_i(Mnemonic::ADDI, XReg(10), XReg(0), IImm12(42));
    assert(res.is_ok());
    assert(res.value() == 0x02A00513);

    // SLLI x1, x2, 5 -> 0x00511093
    auto res_slli = Encoder::encode_shift(Mnemonic::SLLI, XReg(1), XReg(2), ShiftAmount5(5));
    assert(res_slli.is_ok());
    assert(res_slli.value() == 0x00511093);

    // LW x5, -8(x2) -> imm = -8 = 0xFF8 -> 0xFF812283
    auto res_lw = Encoder::encode_load(Mnemonic::LW, XReg(5), XReg(2), IImm12(-8));
    assert(res_lw.is_ok());
    assert(res_lw.value() == 0xFF812283);
}

void test_s_type_encoding() {
    // SW x5, -8(x2) -> imm12 = 0xFF8, imm_11_5 = 0x7F, imm_4_0 = 0x18
    // 1111111 00101 00010 010 11000 0100011 -> 0xFE512C23
    auto res = Encoder::encode_s(Mnemonic::SW, XReg(5), XReg(2), SImm12(-8));
    assert(res.is_ok());
    assert(res.value() == 0xFE512C23);
}

void test_b_type_encoding() {
    // BEQ x1, x2, 16 -> 0x00208863
    auto res = Encoder::encode_b(Mnemonic::BEQ, XReg(1), XReg(2), BranchOffset13(16));
    assert(res.is_ok());
    assert(res.value() == 0x00208863);
}

void test_u_type_encoding() {
    // LUI x10, 0x12345 -> 0x12345537
    auto res = Encoder::encode_u(Mnemonic::LUI, XReg(10), UImm20(0x12345));
    assert(res.is_ok());
    assert(res.value() == 0x12345537);
}

void test_j_type_encoding() {
    // JAL x1, 100 -> 0x064000EF
    auto res = Encoder::encode_j(XReg(1), JumpOffset21(100));
    assert(res.is_ok());
    assert(res.value() == 0x064000EF);
}

void test_cap_manipulation_encoding() {
    // CSETBOUNDS c3, c1, x5 -> opcode 0x0B, cd=3, funct3=0, cs1=1, rs2=5, funct7=0x00
    // 0000000 00101 00001 000 00011 0001011 -> 0x0050818B
    auto res = Encoder::encode_csetbounds(CapReg(3), CapReg(1), XReg(5));
    assert(res.is_ok());
    assert(res.value() == 0x0050818B);

    // CCLEAR c3 -> opcode 0x0B, cd=3, funct3=7, cs1=0, rs2=0, funct7=0x01
    // 0000001 00000 00000 111 00011 0001011 -> 0x0200718B
    auto res_cclear = Encoder::encode_cclear(CapReg(3));
    assert(res_cclear.is_ok());
    assert(res_cclear.value() == 0x0200718B);
}

void test_cap_memory_encoding() {
    // CLW x10, 16(c3) -> opcode 0x2B, rd=10, funct3=2, cs1=3, imm=16
    // 000000010000 00011 010 01010 0101011 -> 0x0101A52B
    auto res_clw = Encoder::encode_cap_load(Mnemonic::CLW, XReg(10), CapReg(3), IImm12(16));
    assert(res_clw.is_ok());
    assert(res_clw.value() == 0x0101A52B);

    // CSW x10, 16(c3) -> opcode 0x2B, rs2=10, funct3=6, cs1=3, imm=16 (imm_11_5=0, imm_4_0=16)
    // 0000000 01010 00011 110 10000 0101011 -> 0x00A1E82B
    auto res_csw = Encoder::encode_cap_store(Mnemonic::CSW, XReg(10), CapReg(3), SImm12(16));
    assert(res_csw.is_ok());
    assert(res_csw.value() == 0x00A1E82B);
}

int main() {
    std::cout << "[RUN] test_encoder\n";
    test_r_type_encoding();
    test_i_type_encoding();
    test_s_type_encoding();
    test_b_type_encoding();
    test_u_type_encoding();
    test_j_type_encoding();
    test_cap_manipulation_encoding();
    test_cap_memory_encoding();
    std::cout << "[PASS] test_encoder passed successfully!\n";
    return 0;
}
