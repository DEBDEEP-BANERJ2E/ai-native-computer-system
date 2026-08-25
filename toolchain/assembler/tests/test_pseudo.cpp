#include "an32asm/pseudo.hpp"
#include "an32asm/assembler.hpp"
#include "an32asm/source_manager.hpp"
#include "an32asm/diagnostic.hpp"
#include <cassert>
#include <iostream>

void test_li_decomposition() {
    // 0xFFFFFFFF (-1) -> addi rd, x0, -1
    auto [hi1, lo1] = an32asm::PseudoExpander::decompose_li(-1);
    assert(hi1 == 0 && lo1 == -1);

    // 0x80000000 -> lui rd, 0x80000 + addi rd, rd, 0
    auto [hi2, lo2] = an32asm::PseudoExpander::decompose_li(static_cast<int32_t>(0x80000000));
    assert(hi2 == 0x80000 && lo2 == 0);

    // 0x7FFFFFFF -> lui rd, 0x80000 + addi rd, rd, -1
    auto [hi3, lo3] = an32asm::PseudoExpander::decompose_li(0x7FFFFFFF);
    assert(hi3 == 0x80000 && lo3 == -1);

    // 0x00000800 -> lui rd, 1 + addi rd, rd, -2048
    auto [hi4, lo4] = an32asm::PseudoExpander::decompose_li(0x800);
    assert(hi4 == 1 && lo4 == -2048);
}

void test_pseudo_assembly() {
    an32asm::SourceManager sm;
    an32asm::DiagnosticEngine diag(sm);
    an32asm::Assembler as(sm, diag);

    std::string src = R"(
.text
    nop
    mv a0, a1
    not a0, a1
    neg a0, a1
    seqz a0, a1
    snez a0, a1
    ret
    j 1f
1:
    call target
target:
    ret
)";

    auto obj = as.assemble_string(src, "pseudo.s");
    assert(!diag.has_errors());
    assert(obj != nullptr);
}

int main() {
    std::cout << "Running test_pseudo...\n";
    test_li_decomposition();
    test_pseudo_assembly();
    std::cout << "test_pseudo passed!\n";
    return 0;
}
