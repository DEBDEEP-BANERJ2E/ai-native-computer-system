#pragma once

#include "source_location.hpp"
#include "token.hpp"
#include "expression.hpp"
#include <string>
#include <vector>
#include <memory>

namespace an32asm {

enum class OperandKind {
    REG_X,
    REG_CAP,
    MEMORY,
    IMMEDIATE
};

class Operand {
public:
    OperandKind kind;
    SourceSpan span;

    Operand(OperandKind k, SourceSpan sp) : kind(k), span(sp) {}
    virtual ~Operand() = default;
};

using OperandPtr = std::shared_ptr<Operand>;

class XRegOperand : public Operand {
public:
    uint32_t reg_index; // 0..31
    XRegOperand(uint32_t reg, SourceSpan sp)
        : Operand(OperandKind::REG_X, sp), reg_index(reg) {}
};

class CapRegOperand : public Operand {
public:
    uint32_t reg_index; // 0..7
    CapRegOperand(uint32_t reg, SourceSpan sp)
        : Operand(OperandKind::REG_CAP, sp), reg_index(reg) {}
};

class MemoryOperand : public Operand {
public:
    ExprPtr offset;
    OperandPtr base_reg; // XRegOperand or CapRegOperand

    MemoryOperand(ExprPtr off, OperandPtr base, SourceSpan sp)
        : Operand(OperandKind::MEMORY, sp), offset(std::move(off)), base_reg(std::move(base)) {}
};

class ImmediateOperand : public Operand {
public:
    ExprPtr expr;

    ImmediateOperand(ExprPtr e, SourceSpan sp)
        : Operand(OperandKind::IMMEDIATE, sp), expr(std::move(e)) {}
};

enum class StatementKind {
    LABEL,
    DIRECTIVE,
    INSTRUCTION
};

class Statement {
public:
    StatementKind kind;
    SourceSpan span;

    Statement(StatementKind k, SourceSpan sp) : kind(k), span(sp) {}
    virtual ~Statement() = default;
};

using StatementPtr = std::shared_ptr<Statement>;

class LabelStatement : public Statement {
public:
    std::string name;
    bool is_local_numeric = false;
    uint32_t local_label_num = 0;

    LabelStatement(std::string name_, SourceSpan sp)
        : Statement(StatementKind::LABEL, sp), name(std::move(name_)) {}

    LabelStatement(uint32_t num, SourceSpan sp)
        : Statement(StatementKind::LABEL, sp), is_local_numeric(true), local_label_num(num) {
        name = std::to_string(num);
    }
};

class DirectiveStatement : public Statement {
public:
    TokenType directive_type;
    std::string directive_name;
    std::vector<ExprPtr> expr_args;
    std::vector<std::string> string_args;
    std::string symbol_arg;

    DirectiveStatement(TokenType dir, std::string name, SourceSpan sp)
        : Statement(StatementKind::DIRECTIVE, sp), directive_type(dir), directive_name(std::move(name)) {}
};

class InstructionStatement : public Statement {
public:
    std::string mnemonic;
    std::vector<OperandPtr> operands;

    InstructionStatement(std::string mnem, std::vector<OperandPtr> ops, SourceSpan sp)
        : Statement(StatementKind::INSTRUCTION, sp), mnemonic(std::move(mnem)), operands(std::move(ops)) {}
};

} // namespace an32asm
