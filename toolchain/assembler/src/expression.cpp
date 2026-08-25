#include "an32asm/expression.hpp"
#include "an32asm/symbol.hpp"

namespace an32asm {

int64_t ExpressionEvaluator::calc_hi20(int64_t val) noexcept {
    int64_t hi = (val + 0x800) >> 12;
    return hi & 0xFFFFF;
}

int64_t ExpressionEvaluator::calc_lo12(int64_t val) noexcept {
    int64_t hi = (val + 0x800) >> 12;
    // Sign-extend 20-bit hi if bit 19 is set
    int64_t hi_sext = ((hi & 0xFFFFF) ^ 0x80000) - 0x80000;
    return val - (hi_sext << 12);
}

ExpressionEvaluator::ExpressionEvaluator(DiagnosticEngine& diag) : diag_(diag) {}

EvalResult ExpressionEvaluator::eval_node(const ExprPtr& expr, const SymbolTable* symtab, uint64_t current_pc) {
    if (!expr) return EvalResult::make_invalid();

    switch (expr->kind) {
        case ExprKind::CONSTANT: {
            auto c = std::static_pointer_cast<ConstantExpr>(expr);
            return EvalResult::make_absolute(c->value);
        }

        case ExprKind::SYMBOL: {
            auto s = std::static_pointer_cast<SymbolExpr>(expr);
            if (s->is_local_numeric) {
                EvalResult res;
                res.kind = EvalKind::RELOCATABLE;
                res.is_local_numeric = true;
                res.local_label_num = s->local_label_num;
                res.is_forward_ref = s->is_forward_ref;
                res.symbol_name = s->name;
                res.value = 0;
                return res;
            }

            // Check if symbol is defined as an absolute equate/constant in symtab
            if (symtab) {
                const auto* sym = symtab->find(s->name);
                if (sym && sym->is_defined && sym->is_absolute) {
                    return EvalResult::make_absolute(sym->value);
                }
            }

            return EvalResult::make_relocatable(s->name, 0);
        }

        case ExprKind::DOT: {
            return EvalResult::make_absolute(static_cast<int64_t>(current_pc));
        }

        case ExprKind::UNARY: {
            auto u = std::static_pointer_cast<UnaryExpr>(expr);
            auto sub_res = eval_node(u->sub, symtab, current_pc);
            if (sub_res.kind != EvalKind::ABSOLUTE) {
                diag_.error(u->span, "unary operator requires absolute expression");
                return EvalResult::make_invalid();
            }

            switch (u->op) {
                case UnaryOp::PLUS:        return EvalResult::make_absolute(+sub_res.value);
                case UnaryOp::MINUS:       return EvalResult::make_absolute(-sub_res.value);
                case UnaryOp::BIT_NOT:     return EvalResult::make_absolute(~sub_res.value);
                case UnaryOp::LOGICAL_NOT: return EvalResult::make_absolute(!sub_res.value);
            }
            return EvalResult::make_invalid();
        }

        case ExprKind::BINARY: {
            auto b = std::static_pointer_cast<BinaryExpr>(expr);
            auto lhs_res = eval_node(b->lhs, symtab, current_pc);
            auto rhs_res = eval_node(b->rhs, symtab, current_pc);

            // Both absolute: compute directly
            if (lhs_res.kind == EvalKind::ABSOLUTE && rhs_res.kind == EvalKind::ABSOLUTE) {
                int64_t res_val = 0;
                switch (b->op) {
                    case BinaryOp::ADD: res_val = lhs_res.value + rhs_res.value; break;
                    case BinaryOp::SUB: res_val = lhs_res.value - rhs_res.value; break;
                    case BinaryOp::MUL: res_val = lhs_res.value * rhs_res.value; break;
                    case BinaryOp::DIV:
                        if (rhs_res.value == 0) {
                            diag_.error(b->span, "division by zero in expression");
                            return EvalResult::make_invalid();
                        }
                        res_val = lhs_res.value / rhs_res.value;
                        break;
                    case BinaryOp::MOD:
                        if (rhs_res.value == 0) {
                            diag_.error(b->span, "modulo by zero in expression");
                            return EvalResult::make_invalid();
                        }
                        res_val = lhs_res.value % rhs_res.value;
                        break;
                    case BinaryOp::SHL: res_val = lhs_res.value << (rhs_res.value & 63); break;
                    case BinaryOp::SHR: res_val = lhs_res.value >> (rhs_res.value & 63); break;
                    case BinaryOp::AND: res_val = lhs_res.value & rhs_res.value; break;
                    case BinaryOp::OR:  res_val = lhs_res.value | rhs_res.value; break;
                    case BinaryOp::XOR: res_val = lhs_res.value ^ rhs_res.value; break;
                }
                return EvalResult::make_absolute(res_val);
            }

            // Relocatable + Absolute (e.g. symbol + 4)
            if (lhs_res.kind == EvalKind::RELOCATABLE && rhs_res.kind == EvalKind::ABSOLUTE) {
                if (b->op == BinaryOp::ADD) {
                    lhs_res.value += rhs_res.value;
                    return lhs_res;
                }
                if (b->op == BinaryOp::SUB) {
                    lhs_res.value -= rhs_res.value;
                    return lhs_res;
                }
                diag_.error(b->span, "unsupported arithmetic on relocatable symbol");
                return EvalResult::make_invalid();
            }

            // Absolute + Relocatable (e.g. 4 + symbol)
            if (lhs_res.kind == EvalKind::ABSOLUTE && rhs_res.kind == EvalKind::RELOCATABLE) {
                if (b->op == BinaryOp::ADD) {
                    rhs_res.value += lhs_res.value;
                    return rhs_res;
                }
                diag_.error(b->span, "unsupported arithmetic on relocatable symbol");
                return EvalResult::make_invalid();
            }

            // Relocatable - Relocatable (symbolA - symbolB)
            if (lhs_res.kind == EvalKind::RELOCATABLE && rhs_res.kind == EvalKind::RELOCATABLE) {
                if (b->op == BinaryOp::SUB) {
                    // Check if both symbols are defined in the same section
                    if (symtab) {
                        const auto* symA = symtab->find(lhs_res.symbol_name);
                        const auto* symB = symtab->find(rhs_res.symbol_name);
                        if (symA && symB && symA->is_defined && symB->is_defined &&
                            symA->section_id.has_value() && symB->section_id.has_value() &&
                            *symA->section_id == *symB->section_id) {
                            int64_t diff = static_cast<int64_t>(symA->value) + lhs_res.value -
                                          (static_cast<int64_t>(symB->value) + rhs_res.value);
                            return EvalResult::make_absolute(diff);
                        }
                    }

                    // Otherwise create a SectionDifference record for the assembler/linker
                    EvalResult res;
                    res.kind = EvalKind::SECTION_DIFF;
                    res.symbol_name = lhs_res.symbol_name;
                    res.subtract_symbol_name = rhs_res.symbol_name;
                    res.value = lhs_res.value - rhs_res.value;
                    return res;
                }
                diag_.error(b->span, "cannot multiply/divide/operate on two relocatable symbols");
                return EvalResult::make_invalid();
            }

            diag_.error(b->span, "invalid operands in expression");
            return EvalResult::make_invalid();
        }

        case ExprKind::RELOC_MODIFIER: {
            auto r = std::static_pointer_cast<RelocModifierExpr>(expr);
            auto sub_res = eval_node(r->sub, symtab, current_pc);
            if (sub_res.kind == EvalKind::INVALID) {
                return EvalResult::make_invalid();
            }

            // If sub-expression is already absolute, compute immediately
            if (sub_res.kind == EvalKind::ABSOLUTE) {
                int64_t val = sub_res.value;
                if (r->modifier == RelocModifier::HI) {
                    return EvalResult::make_absolute(calc_hi20(val));
                }
                if (r->modifier == RelocModifier::LO) {
                    return EvalResult::make_absolute(calc_lo12(val));
                }
            }

            sub_res.kind = EvalKind::RELOC_MODIFIED;
            sub_res.modifier = r->modifier;
            return sub_res;
        }
    }

    return EvalResult::make_invalid();
}

EvalResult ExpressionEvaluator::evaluate(const ExprPtr& expr, const SymbolTable* symtab, uint64_t current_pc) {
    return eval_node(expr, symtab, current_pc);
}

std::optional<int64_t> ExpressionEvaluator::evaluate_absolute(const ExprPtr& expr, const SymbolTable* symtab) {
    auto res = eval_node(expr, symtab, 0);
    if (res.kind == EvalKind::ABSOLUTE) {
        return res.value;
    }
    return std::nullopt;
}

} // namespace an32asm
