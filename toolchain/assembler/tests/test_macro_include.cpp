#include "an32asm/source_manager.hpp"
#include "an32asm/diagnostic.hpp"
#include "an32asm/lexer.hpp"
#include "an32asm/source_expander.hpp"
#include <cassert>
#include <iostream>

void test_macro_expansion() {
    an32asm::SourceManager sm;
    an32asm::DiagnosticEngine diag(sm);

    std::string src = R"(
.macro push_reg reg
    addi sp, sp, -4
    sw \reg, 0(sp)
.endm

push_reg a0
push_reg a1
)";

    uint32_t fid = sm.add_buffer("test_macro.s", src);
    an32asm::Lexer lexer(sm, fid, diag);
    auto raw_tokens = lexer.tokenize();

    an32asm::SourceExpander expander(sm, diag);
    auto expanded = expander.expand(raw_tokens);

    assert(!diag.has_errors());
    // Should have expanded sw a0, 0(sp) and sw a1, 0(sp)
    bool found_a0 = false;
    bool found_a1 = false;
    for (const auto& t : expanded) {
        if (t.type == an32asm::TokenType::REG_X && t.reg_index == 10) found_a0 = true; // a0 = x10
        if (t.type == an32asm::TokenType::REG_X && t.reg_index == 11) found_a1 = true; // a1 = x11
    }
    assert(found_a0 && found_a1);
}

void test_conditional_assembly() {
    an32asm::SourceManager sm;
    an32asm::DiagnosticEngine diag(sm);

    std::string src = R"(
.equ FEATURE_ENABLED, 1
.equ DEBUG_LEVEL, 2

.if FEATURE_ENABLED == 1
    addi a0, zero, 10
.else
    addi a0, zero, 20
.endif

.if DEBUG_LEVEL == 0
    nop
.elseif DEBUG_LEVEL == 2
    addi a1, zero, 42
.else
    nop
.endif
)";

    uint32_t fid = sm.add_buffer("test_cond.s", src);
    an32asm::Lexer lexer(sm, fid, diag);
    auto raw_tokens = lexer.tokenize();

    an32asm::SourceExpander expander(sm, diag);
    auto expanded = expander.expand(raw_tokens);

    assert(!diag.has_errors());
    bool found_10 = false;
    bool found_20 = false;
    bool found_42 = false;
    for (const auto& t : expanded) {
        if (t.type == an32asm::TokenType::INTEGER && t.int_value == 10) found_10 = true;
        if (t.type == an32asm::TokenType::INTEGER && t.int_value == 20) found_20 = true;
        if (t.type == an32asm::TokenType::INTEGER && t.int_value == 42) found_42 = true;
    }
    assert(found_10 && !found_20 && found_42);
}

void test_rept() {
    an32asm::SourceManager sm;
    an32asm::DiagnosticEngine diag(sm);

    std::string src = R"(
.rept 3
    nop
.endr
)";

    uint32_t fid = sm.add_buffer("test_rept.s", src);
    an32asm::Lexer lexer(sm, fid, diag);
    auto raw_tokens = lexer.tokenize();

    an32asm::SourceExpander expander(sm, diag);
    auto expanded = expander.expand(raw_tokens);

    assert(!diag.has_errors());
    size_t nop_count = 0;
    for (const auto& t : expanded) {
        if (t.type == an32asm::TokenType::IDENTIFIER && t.text == "nop") nop_count++;
    }
    assert(nop_count == 3);
}

int main() {
    std::cout << "Running test_macro_include...\n";
    test_macro_expansion();
    test_conditional_assembly();
    test_rept();
    std::cout << "test_macro_include passed!\n";
    return 0;
}
