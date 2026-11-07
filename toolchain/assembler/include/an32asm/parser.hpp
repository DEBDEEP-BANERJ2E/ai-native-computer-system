#pragma once

#include "token.hpp"
#include "ast.hpp"
#include "diagnostic.hpp"
#include <vector>
#include <memory>

namespace an32asm {

class Parser {
public:
    Parser(std::vector<Token> tokens, DiagnosticEngine& diag);

    std::vector<StatementPtr> parse_all();

private:
    std::vector<Token> tokens_;
    size_t pos_ = 0;
    DiagnosticEngine& diag_;

    const Token& peek(size_t ahead = 0) const;
    const Token& current() const;
    Token advance();
    bool match(TokenType type);
    bool check(TokenType type) const;
    void skip_to_statement_end();

    StatementPtr parse_statement();
    StatementPtr parse_directive();
    StatementPtr parse_instruction();

    OperandPtr parse_operand();
    ExprPtr parse_expression();
    ExprPtr parse_primary_expr();
    ExprPtr parse_unary_expr();
    ExprPtr parse_binary_expr(int min_prec);

    void validate_instruction_operands(const std::string& mnem, const std::vector<OperandPtr>& ops, SourceSpan span);
};

} // namespace an32asm
