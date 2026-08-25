#include "an32asm/assembler.hpp"
#include "an32asm/source_manager.hpp"
#include "an32asm/diagnostic.hpp"
#include <cassert>
#include <iostream>

void test_section_model() {
    an32asm::SourceManager sm;
    an32asm::DiagnosticEngine diag(sm);
    an32asm::Assembler as(sm, diag);

    std::string src = R"(
.text
    addi a0, zero, 1
    .p2align 3 # Align to 8 bytes
    addi a0, zero, 2

.data
    .4byte 0x12345678
    .asciz "hello"

.bss
    .zero 64
)";

    auto obj = as.assemble_string(src, "sections.s");
    assert(!diag.has_errors());
    assert(obj != nullptr);

    auto* text = obj->find_section(".text");
    auto* data = obj->find_section(".data");
    auto* bss = obj->find_section(".bss");

    assert(text != nullptr && text->is_executable() && !text->is_nobits());
    assert(data != nullptr && !data->is_nobits() && data->data.size() > 0);
    assert(bss != nullptr && bss->is_nobits() && bss->data.empty() && bss->memory_size == 64);

    // Verify .text alignment padding contains canonical NOP (0x00000013)
    assert(text->data.size() == 12);
    // At offset 4, it should be NOP (0x13, 0x00, 0x00, 0x00)
    assert(text->data[4] == 0x13 && text->data[5] == 0x00 && text->data[6] == 0x00 && text->data[7] == 0x00);
}

int main() {
    std::cout << "Running test_sections...\n";
    test_section_model();
    std::cout << "test_sections passed!\n";
    return 0;
}
