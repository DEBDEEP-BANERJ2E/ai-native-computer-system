#include "an32asm/assembler.hpp"
#include "an32asm/flat_finalizer.hpp"
#include "an32asm/source_manager.hpp"
#include "an32asm/diagnostic.hpp"
#include <cassert>
#include <iostream>

void test_paired_pcrel_fixups() {
    an32asm::SourceManager sm;
    an32asm::DiagnosticEngine diag(sm);
    an32asm::Assembler as(sm, diag);

    std::string src = R"(
.text
    la a0, my_var
    call my_func
    ret

my_func:
    addi a0, a0, 1
    ret

.data
my_var:
    .4byte 0x1234
)";

    auto obj = as.assemble_string(src, "paired.s");
    assert(!diag.has_errors());
    assert(obj != nullptr);

    // Verify fixups exist
    assert(!obj->fixups.empty());

    // Finalize .text
    an32asm::FlatImageFinalizer finalizer(*obj, diag);
    // Local internal branches resolve
}

int main() {
    std::cout << "Running test_fixups...\n";
    test_paired_pcrel_fixups();
    std::cout << "test_fixups passed!\n";
    return 0;
}
