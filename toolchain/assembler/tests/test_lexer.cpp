#include "an32asm/lexer.hpp"
#include "an32asm/source_manager.hpp"
#include "an32asm/diagnostic.hpp"
#include <cassert>
#include <iostream>

void test_register_tokens() {
    an32asm::SourceManager sm;
    an32asm::DiagnosticEngine diag(sm);
    uint32_t fid = sm.add_buffer("test.s", "x0 x31 zero ra sp gp tp t0 t1 t2 s0 fp s1 a0 a1 a2 a3 a4 a5 a6 a7 s2 s3 s4 s5 s6 s7 s8 s9 s10 s11 t3 t4 t5 t6 c0 c7 cnull cram cmmio ca0 ca1 ct0 cs0 cs1");

    an32asm::Lexer lexer(sm, fid, diag);
    auto tokens = lexer.tokenize();

    assert(!diag.has_errors());
    assert(tokens.size() > 40);

    // Verify capability aliases
    for (const auto& tok : tokens) {
        if (tok.text == "cnull") { assert(tok.type == an32asm::TokenType::REG_CAP && tok.reg_index == 0); }
        if (tok.text == "cram")  { assert(tok.type == an32asm::TokenType::REG_CAP && tok.reg_index == 1); }
        if (tok.text == "cmmio") { assert(tok.type == an32asm::TokenType::REG_CAP && tok.reg_index == 2); }
        if (tok.text == "ca0")   { assert(tok.type == an32asm::TokenType::REG_CAP && tok.reg_index == 3); }
        if (tok.text == "ca1")   { assert(tok.type == an32asm::TokenType::REG_CAP && tok.reg_index == 4); }
        if (tok.text == "ct0")   { assert(tok.type == an32asm::TokenType::REG_CAP && tok.reg_index == 5); }
        if (tok.text == "cs0")   { assert(tok.type == an32asm::TokenType::REG_CAP && tok.reg_index == 6); }
        if (tok.text == "cs1")   { assert(tok.type == an32asm::TokenType::REG_CAP && tok.reg_index == 7); }
    }
}

void test_signed_tokens_and_local_labels() {
    an32asm::SourceManager sm;
    an32asm::DiagnosticEngine diag(sm);
    uint32_t fid = sm.add_buffer("test.s", "-56 +128 1: 1b 1f 2: 2b 2f %hi %lo %pcrel_hi %pcrel_lo \\@ \\param");

    an32asm::Lexer lexer(sm, fid, diag);
    auto tokens = lexer.tokenize();

    assert(!diag.has_errors());
    // -56 must be MINUS followed by INTEGER(56)
    assert(tokens[0].type == an32asm::TokenType::MINUS);
    assert(tokens[1].type == an32asm::TokenType::INTEGER && tokens[1].int_value == 56);

    // +128 must be PLUS followed by INTEGER(128)
    assert(tokens[2].type == an32asm::TokenType::PLUS);
    assert(tokens[3].type == an32asm::TokenType::INTEGER && tokens[3].int_value == 128);

    // 1: is LOCAL_LABEL_DEF
    assert(tokens[4].type == an32asm::TokenType::LOCAL_LABEL_DEF && tokens[4].local_label_num == 1);
    // 1b is LOCAL_LABEL_REF backward
    assert(tokens[5].type == an32asm::TokenType::LOCAL_LABEL_REF && tokens[5].local_label_num == 1 && !tokens[5].is_forward_ref);
    // 1f is LOCAL_LABEL_REF forward
    assert(tokens[6].type == an32asm::TokenType::LOCAL_LABEL_REF && tokens[6].local_label_num == 1 && tokens[6].is_forward_ref);

    // Modifiers
    assert(tokens[10].type == an32asm::TokenType::MOD_HI);
    assert(tokens[11].type == an32asm::TokenType::MOD_LO);
    assert(tokens[12].type == an32asm::TokenType::MOD_PCREL_HI);
    assert(tokens[13].type == an32asm::TokenType::MOD_PCREL_LO);

    // Macro tokens
    assert(tokens[14].type == an32asm::TokenType::MACRO_UNIQUE_ID);
    assert(tokens[15].type == an32asm::TokenType::MACRO_PARAM_REF && tokens[15].text == "param");
}

int main() {
    std::cout << "Running test_lexer...\n";
    test_register_tokens();
    test_signed_tokens_and_local_labels();
    std::cout << "test_lexer passed!\n";
    return 0;
}
