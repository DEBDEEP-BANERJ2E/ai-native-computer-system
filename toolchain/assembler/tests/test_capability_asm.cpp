#include "an32asm/assembler.hpp"
#include "an32asm/flat_finalizer.hpp"
#include "an32asm/source_manager.hpp"
#include "an32asm/diagnostic.hpp"
#include <cassert>
#include <iostream>

void test_capability_instructions() {
    an32asm::SourceManager sm;
    an32asm::DiagnosticEngine diag(sm);
    an32asm::Assembler as(sm, diag);

    std::string src = R"(
.text
    csetbounds ca0, cram, a0
    candperm   ca1, ca0, a1
    cincoffset ct0, ca0, a2
    cgetbase   t0, ca0
    cgetlen    t1, ca0
    cgettag    t2, ca0
    cgetperm   t3, ca0
    cgetoffset t4, ca0
    cclear     cs0
    clw        a0, 0(ca0)
    csw        a0, 4(ca1)
)";

    auto obj = as.assemble_string(src, "cap_test.s");
    assert(!diag.has_errors());
    assert(obj != nullptr);

    auto* text = obj->find_section(".text");
    assert(text != nullptr);
    assert(text->data.size() == 11 * 4); // 11 instructions = 44 bytes
}

int main() {
    std::cout << "Running test_capability_asm...\n";
    test_capability_instructions();
    std::cout << "test_capability_asm passed!\n";
    return 0;
}
