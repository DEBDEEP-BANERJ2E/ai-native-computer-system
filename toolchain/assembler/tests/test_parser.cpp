#include "an32asm/parser.hpp"
#include "an32asm/lexer.hpp"
#include "an32asm/source_manager.hpp"
#include "an32asm/diagnostic.hpp"
#include <cassert>
#include <iostream>

void test_valid_parsing() {
    an32asm::SourceManager sm;
    an32asm::DiagnosticEngine diag(sm);

    std::string src = R"(
.text
.globl main
main:
    addi a0, zero, 42
    clw  t0, 0(ca0)
    sw   t0, 4(sp)
    ret
)";

    uint32_t fid = sm.add_buffer("test.s", src);
    an32asm::Lexer lexer(sm, fid, diag);
    auto tokens = lexer.tokenize();

    an32asm::Parser parser(std::move(tokens), diag);
    auto stmts = parser.parse_all();

    if (diag.has_errors()) {
        diag.emit_all(std::cerr, false);
    }
    assert(!diag.has_errors());
    assert(stmts.size() == 7);
}

void test_register_type_rejection() {
    an32asm::SourceManager sm;
    an32asm::DiagnosticEngine diag(sm);

    // clw with integer base register must fail
    std::string src_bad1 = "clw t0, 0(a0)\n";
    uint32_t fid1 = sm.add_buffer("bad1.s", src_bad1);
    an32asm::Lexer lexer1(sm, fid1, diag);
    an32asm::Parser parser1(lexer1.tokenize(), diag);
    parser1.parse_all();
    assert(diag.has_errors());
    diag.clear();

    // lw with capability base register must fail
    std::string src_bad2 = "lw t0, 0(ca0)\n";
    uint32_t fid2 = sm.add_buffer("bad2.s", src_bad2);
    an32asm::Lexer lexer2(sm, fid2, diag);
    an32asm::Parser parser2(lexer2.tokenize(), diag);
    parser2.parse_all();
    assert(diag.has_errors());
}

int main() {
    std::cout << "Running test_parser...\n";
    test_valid_parsing();
    test_register_type_rejection();
    std::cout << "test_parser passed!\n";
    return 0;
}
