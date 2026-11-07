#include "an32asm/assembler.hpp"
#include "an32asm/flat_finalizer.hpp"
#include "an32asm/source_manager.hpp"
#include "an32asm/diagnostic.hpp"
#include "an32/decoder.hpp"
#include "an32/disasm.hpp"
#include <cassert>
#include <iostream>
#include <sstream>

void test_fibonacci_assembly() {
    an32asm::SourceManager sm;
    an32asm::DiagnosticEngine diag(sm);
    an32asm::Assembler as(sm, diag);

    std::string src = R"(
.text
.globl fib
fib:
    addi a1, zero, 0      # a1 = prev = 0
    addi a2, zero, 1      # a2 = curr = 1
    addi a3, zero, 0      # a3 = i = 0

1:
    bge  a3, a0, 2f       # if (i >= n) goto end
    add  t0, a1, a2       # t0 = prev + curr
    addi a1, a2, 0        # prev = curr
    addi a2, t0, 0        # curr = t0
    addi a3, a3, 1        # i++
    j    1b

2:
    addi a0, a1, 0        # return prev
    ret
)";

    auto obj = as.assemble_string(src, "fib.s");
    assert(!diag.has_errors());
    assert(obj != nullptr);

    an32asm::FlatImageFinalizer finalizer(*obj, diag);
    bool ok = finalizer.finalize_section(".text", 0);
    assert(ok && !diag.has_errors());

    auto words = finalizer.get_machine_words(".text");
    assert(!words.empty());

    // Verify all decoded instructions using frozen decoder
    an32::Disassembler disasm;

    for (uint32_t w : words) {
        auto dec = an32::Decoder::decode(w);
        assert(dec.is_canonical());
        std::string dis = disasm.disassemble(w);
        assert(!dis.empty());
    }

    std::ostringstream hex_os;
    finalizer.emit_hex(hex_os, ".text", 0);
    assert(!hex_os.str().empty());
}

int main() {
    std::cout << "Running test_end_to_end...\n";
    test_fibonacci_assembly();
    std::cout << "test_end_to_end passed!\n";
    return 0;
}
