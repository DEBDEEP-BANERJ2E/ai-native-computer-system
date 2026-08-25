#pragma once

#include "source_location.hpp"
#include "diagnostic.hpp"
#include <string>
#include <memory>
#include <optional>
#include <variant>

namespace an32asm {

struct Symbol;
class SymbolTable;

enum class ExprKind {
    CONSTANT,
    SYMBOL,
    DOT,
    UNARY,
    BINARY,
    RELOC_MODIFIER
};

enum class UnaryOp {
    PLUS,
    MINUS,
    BIT_NOT,
    LOGICAL_NOT
};

enum class BinaryOp {
    ADD,
    SUB,
    MUL,
    DIV,
    MOD,
    SHL,
    SHR,
    AND,
    OR,
    XOR
};

enum class RelocModifier {
    NONE,
    HI,         // %hi
    LO,         // %lo
    PCREL_HI,   // %pcrel_hi
    PCREL_LO    // %pcrel_lo
};

class Expr;
using ExprPtr = std::shared_ptr<Expr>;

class Expr {
public:
    ExprKind kind;
    SourceSpan span;

    explicit Expr(ExprKind k, SourceSpan sp) : kind(k), span(sp) {}
    virtual ~Expr() = default;
};

class ConstantExpr : public Expr {
public:
    int64_t value;
    ConstantExpr(int64_t val, SourceSpan sp)
        : Expr(ExprKind::CONSTANT, sp), value(val) {}
};

class SymbolExpr : public Expr {
public:
    std::string name;
    bool is_local_numeric = false;
    uint32_t local_label_num = 0;
    bool is_forward_ref = false;

    SymbolExpr(std::string name_, SourceSpan sp)
        : Expr(ExprKind::SYMBOL, sp), name(std::move(name_)) {}

    SymbolExpr(uint32_t num, bool forward, SourceSpan sp)
        : Expr(ExprKind::SYMBOL, sp), is_local_numeric(true), local_label_num(num), is_forward_ref(forward) {
        name = std::to_string(num) + (forward ? "f" : "b");
    }
};

class DotExpr : public Expr {
public:
    explicit DotExpr(SourceSpan sp) : Expr(ExprKind::DOT, sp) {}
};

class UnaryExpr : public Expr {
public:
    UnaryOp op;
    ExprPtr sub;

    UnaryExpr(UnaryOp op_, ExprPtr sub_, SourceSpan sp)
        : Expr(ExprKind::UNARY, sp), op(op_), sub(std::move(sub_)) {}
};

class BinaryExpr : public Expr {
public:
    BinaryOp op;
    ExprPtr lhs;
    ExprPtr rhs;

    BinaryExpr(BinaryOp op_, ExprPtr lhs_, ExprPtr rhs_, SourceSpan sp)
        : Expr(ExprKind::BINARY, sp), op(op_), lhs(std::move(lhs_)), rhs(std::move(rhs_)) {}
};

class RelocModifierExpr : public Expr {
public:
    RelocModifier modifier;
    ExprPtr sub;

    RelocModifierExpr(RelocModifier mod, ExprPtr sub_, SourceSpan sp)
        : Expr(ExprKind::RELOC_MODIFIER, sp), modifier(mod), sub(std::move(sub_)) {}
};

enum class EvalKind {
    ABSOLUTE,
    RELOCATABLE,
    SECTION_DIFF,
    RELOC_MODIFIED,
    INVALID
};

struct EvalResult {
    EvalKind kind = EvalKind::INVALID;
    int64_t value = 0;                  // Absolute value or addend
    std::string symbol_name;            // Symbol referenced
    std::string subtract_symbol_name;   // For section difference
    RelocModifier modifier = RelocModifier::NONE;
    bool is_local_numeric = false;
    uint32_t local_label_num = 0;
    bool is_forward_ref = false;

    static EvalResult make_absolute(int64_t val) {
        EvalResult res;
        res.kind = EvalKind::ABSOLUTE;
        res.value = val;
        return res;
    }

    static EvalResult make_relocatable(std::string name, int64_t addend = 0) {
        EvalResult res;
        res.kind = EvalKind::RELOCATABLE;
        res.symbol_name = std::move(name);
        res.value = addend;
        return res;
    }

    static EvalResult make_invalid() {
        EvalResult res;
        res.kind = EvalKind::INVALID;
        return res;
    }
};

class ExpressionEvaluator {
public:
    explicit ExpressionEvaluator(DiagnosticEngine& diag);

    EvalResult evaluate(const ExprPtr& expr, const SymbolTable* symtab = nullptr, uint64_t current_pc = 0);
    std::optional<int64_t> evaluate_absolute(const ExprPtr& expr, const SymbolTable* symtab = nullptr);

    // Standard RISC-V widened calculation
    static int64_t calc_hi20(int64_t val) noexcept;
    static int64_t calc_lo12(int64_t val) noexcept;

private:
    DiagnosticEngine& diag_;

    EvalResult eval_node(const ExprPtr& expr, const SymbolTable* symtab, uint64_t current_pc);
};

} // namespace an32asm
