#pragma once

#include "token.hpp"
#include "source_manager.hpp"
#include "diagnostic.hpp"
#include <vector>
#include <string_view>

namespace an32asm {

class Lexer {
public:
    Lexer(const SourceManager& sm, uint32_t file_id, DiagnosticEngine& diag);

    std::vector<Token> tokenize();

private:
    const SourceManager& sm_;
    uint32_t file_id_;
    DiagnosticEngine& diag_;
    std::string_view content_;
    uint32_t offset_ = 0;
    uint32_t line_ = 1;
    uint32_t col_ = 1;

    char peek(size_t ahead = 0) const;
    char advance();
    bool match(char expected);
    void skip_whitespace_and_comments();
    SourcePos current_pos() const;

    Token lex_number();
    Token lex_string();
    Token lex_char();
    Token lex_identifier_or_keyword();
    Token lex_directive();
    Token lex_percent_modifier();
    Token lex_macro_escape();
};

} // namespace an32asm
