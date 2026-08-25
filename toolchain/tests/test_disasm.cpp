#include <cassert>
#include <iostream>
#include "an32/disasm.hpp"
#include "an32/encoder.hpp"

using namespace an32;

void test_disasm_formatting() {
    // ADDI x0, x0, 0 (not pseudo 'nop')
    auto addi_zero = Encoder::encode_i(Mnemonic::ADDI, XReg(0), XReg(0), IImm12(0)).value();
    std::string text_abi = Disassembler::disassemble(addi_zero, MachineProfile::AN32_BARE_V1, true);
    std::string text_num = Disassembler::disassemble(addi_zero, MachineProfile::AN32_BARE_V1, false);
    assert(text_abi == "addi zero, zero, 0");
    assert(text_num == "addi x0, x0, 0");

    // JALR x0, 0(x1) (not pseudo 'ret')
    auto jalr_ret = Encoder::encode_jalr(XReg(0), XReg(1), IImm12(0)).value();
    assert(Disassembler::disassemble(jalr_ret, MachineProfile::AN32_BARE_V1, true) == "jalr zero, 0(ra)");
    assert(Disassembler::disassemble(jalr_ret, MachineProfile::AN32_BARE_V1, false) == "jalr x0, 0(x1)");

    // Capability manipulation
    auto cset = Encoder::encode_csetbounds(CapReg(3), CapReg(1), XReg(10)).value();
    assert(Disassembler::disassemble(cset, MachineProfile::AN32_BARE_V1, true) == "csetbounds ca0, cram, a0");
    assert(Disassembler::disassemble(cset, MachineProfile::AN32_BARE_V1, false) == "csetbounds c3, c1, x10");

    // Capability load
    auto clw = Encoder::encode_cap_load(Mnemonic::CLW, XReg(10), CapReg(3), IImm12(16)).value();
    assert(Disassembler::disassemble(clw, MachineProfile::AN32_BARE_V1, true) == "clw a0, 16(ca0)");
    assert(Disassembler::disassemble(clw, MachineProfile::AN32_BARE_V1, false) == "clw x10, 16(c3)");

    // Illegal instruction representation
    uint32_t illegal_word = 0xFFFFFFFF;
    std::string text_illegal = Disassembler::disassemble(illegal_word, MachineProfile::AN32_BARE_V1, true);
    assert(text_illegal == ".word 0xFFFFFFFF");
}

int main() {
    std::cout << "[RUN] test_disasm\n";
    test_disasm_formatting();
    std::cout << "[PASS] test_disasm passed successfully!\n";
    return 0;
}
