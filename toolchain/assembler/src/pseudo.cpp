#include "an32asm/pseudo.hpp"
#include <algorithm>
#include <unordered_set>

namespace an32asm {

PseudoExpander::PseudoExpander(DiagnosticEngine& diag) : diag_(diag) {}

std::optional<uint32_t> PseudoExpander::normalize_u32_literal(int64_t val) {
    if (val >= -2147483648LL && val <= 2147483647LL) {
        return static_cast<uint32_t>(static_cast<int32_t>(val));
    }
    if (val >= 0 && val <= 4294967295LL) {
        return static_cast<uint32_t>(val);
    }
    return std::nullopt;
}

std::pair<int64_t, int64_t> PseudoExpander::decompose_li(int64_t val) {
    uint32_t u32 = static_cast<uint32_t>(val);
    int32_t s32 = static_cast<int32_t>(u32);
    int32_t hi = static_cast<int32_t>((u32 + 0x800) >> 12);
    int32_t lo = s32 - (hi << 12);
    return {hi & 0xFFFFF, lo};
}

bool PseudoExpander::is_pseudo_instruction(const std::string& mnem) const {
    std::string lower = mnem;
    std::transform(lower.begin(), lower.end(), lower.begin(), [](unsigned char c) { return std::tolower(c); });

    static const std::unordered_set<std::string> pseudos = {
        "nop", "mv", "not", "neg", "seqz", "snez", "sltz", "sgtz",
        "beqz", "bnez", "blez", "bgez", "bltz", "bgtz",
        "bgt", "ble", "bgtu", "bleu",
        "j", "jr", "ret", "call", "tail", "li", "la"
    };

    return pseudos.count(lower) > 0;
}

std::vector<ExpandedInstruction> PseudoExpander::expand(const InstructionStatement& inst) {
    std::string mnem = inst.mnemonic;
    std::transform(mnem.begin(), mnem.end(), mnem.begin(), [](unsigned char c) { return std::tolower(c); });
    const auto& ops = inst.operands;
    SourceSpan sp = inst.span;

    std::vector<ExpandedInstruction> result;

    auto x0 = std::make_shared<XRegOperand>(0, sp);
    auto ra = std::make_shared<XRegOperand>(1, sp);
    auto t1 = std::make_shared<XRegOperand>(6, sp); // t1 is x6

    if (mnem == "nop") {
        auto zero_imm = std::make_shared<ImmediateOperand>(std::make_shared<ConstantExpr>(0, sp), sp);
        result.push_back(ExpandedInstruction{"addi", {x0, x0, zero_imm}, sp, true});
        return result;
    }

    if (mnem == "mv") {
        if (ops.size() == 2) {
            auto zero_imm = std::make_shared<ImmediateOperand>(std::make_shared<ConstantExpr>(0, sp), sp);
            result.push_back(ExpandedInstruction{"addi", {ops[0], ops[1], zero_imm}, sp, true});
            return result;
        }
    }

    if (mnem == "not") {
        if (ops.size() == 2) {
            auto minus1_imm = std::make_shared<ImmediateOperand>(std::make_shared<ConstantExpr>(-1, sp), sp);
            result.push_back(ExpandedInstruction{"xori", {ops[0], ops[1], minus1_imm}, sp, true});
            return result;
        }
    }

    if (mnem == "neg") {
        if (ops.size() == 2) {
            result.push_back(ExpandedInstruction{"sub", {ops[0], x0, ops[1]}, sp, true});
            return result;
        }
    }

    if (mnem == "seqz") {
        if (ops.size() == 2) {
            auto one_imm = std::make_shared<ImmediateOperand>(std::make_shared<ConstantExpr>(1, sp), sp);
            result.push_back(ExpandedInstruction{"sltiu", {ops[0], ops[1], one_imm}, sp, true});
            return result;
        }
    }

    if (mnem == "snez") {
        if (ops.size() == 2) {
            result.push_back(ExpandedInstruction{"sltu", {ops[0], x0, ops[1]}, sp, true});
            return result;
        }
    }

    if (mnem == "sltz") {
        if (ops.size() == 2) {
            result.push_back(ExpandedInstruction{"slt", {ops[0], ops[1], x0}, sp, true});
            return result;
        }
    }

    if (mnem == "sgtz") {
        if (ops.size() == 2) {
            result.push_back(ExpandedInstruction{"slt", {ops[0], x0, ops[1]}, sp, true});
            return result;
        }
    }

    if (mnem == "beqz") {
        if (ops.size() == 2) {
            result.push_back(ExpandedInstruction{"beq", {ops[0], x0, ops[1]}, sp, true});
            return result;
        }
    }

    if (mnem == "bnez") {
        if (ops.size() == 2) {
            result.push_back(ExpandedInstruction{"bne", {ops[0], x0, ops[1]}, sp, true});
            return result;
        }
    }

    if (mnem == "blez") {
        if (ops.size() == 2) {
            result.push_back(ExpandedInstruction{"bge", {x0, ops[0], ops[1]}, sp, true});
            return result;
        }
    }

    if (mnem == "bgez") {
        if (ops.size() == 2) {
            result.push_back(ExpandedInstruction{"bge", {ops[0], x0, ops[1]}, sp, true});
            return result;
        }
    }

    if (mnem == "bltz") {
        if (ops.size() == 2) {
            result.push_back(ExpandedInstruction{"blt", {ops[0], x0, ops[1]}, sp, true});
            return result;
        }
    }

    if (mnem == "bgtz") {
        if (ops.size() == 2) {
            result.push_back(ExpandedInstruction{"blt", {x0, ops[0], ops[1]}, sp, true});
            return result;
        }
    }

    if (mnem == "bgt") {
        if (ops.size() == 3) {
            result.push_back(ExpandedInstruction{"blt", {ops[1], ops[0], ops[2]}, sp, true});
            return result;
        }
    }

    if (mnem == "ble") {
        if (ops.size() == 3) {
            result.push_back(ExpandedInstruction{"bge", {ops[1], ops[0], ops[2]}, sp, true});
            return result;
        }
    }

    if (mnem == "bgtu") {
        if (ops.size() == 3) {
            result.push_back(ExpandedInstruction{"bltu", {ops[1], ops[0], ops[2]}, sp, true});
            return result;
        }
    }

    if (mnem == "bleu") {
        if (ops.size() == 3) {
            result.push_back(ExpandedInstruction{"bgeu", {ops[1], ops[0], ops[2]}, sp, true});
            return result;
        }
    }

    if (mnem == "j") {
        if (ops.size() == 1) {
            result.push_back(ExpandedInstruction{"jal", {x0, ops[0]}, sp, true});
            return result;
        }
    }

    if (mnem == "jr") {
        if (ops.size() == 1) {
            auto zero_imm = std::make_shared<ConstantExpr>(0, sp);
            auto mem_op = std::make_shared<MemoryOperand>(zero_imm, ops[0], sp);
            result.push_back(ExpandedInstruction{"jalr", {x0, mem_op}, sp, true});
            return result;
        }
    }

    if (mnem == "ret") {
        auto zero_imm = std::make_shared<ConstantExpr>(0, sp);
        auto mem_op = std::make_shared<MemoryOperand>(zero_imm, ra, sp);
        result.push_back(ExpandedInstruction{"jalr", {x0, mem_op}, sp, true});
        return result;
    }

    if (mnem == "call") {
        if (ops.size() == 1) {
            auto sym_imm = std::dynamic_pointer_cast<ImmediateOperand>(ops[0]);
            if (sym_imm) {
                // Canonical deterministic 8-byte expansion
                auto pcrel_hi = std::make_shared<ImmediateOperand>(
                    std::make_shared<RelocModifierExpr>(RelocModifier::PCREL_HI, sym_imm->expr, sp), sp);
                auto pcrel_lo = std::make_shared<RelocModifierExpr>(RelocModifier::PCREL_LO, sym_imm->expr, sp);
                auto mem_op = std::make_shared<MemoryOperand>(pcrel_lo, ra, sp);

                result.push_back(ExpandedInstruction{"auipc", {ra, pcrel_hi}, sp, true});
                result.push_back(ExpandedInstruction{"jalr",  {ra, mem_op}, sp, true});
                return result;
            }
        }
    }

    if (mnem == "tail") {
        if (ops.size() == 1) {
            auto sym_imm = std::dynamic_pointer_cast<ImmediateOperand>(ops[0]);
            if (sym_imm) {
                // Canonical deterministic 8-byte expansion
                auto pcrel_hi = std::make_shared<ImmediateOperand>(
                    std::make_shared<RelocModifierExpr>(RelocModifier::PCREL_HI, sym_imm->expr, sp), sp);
                auto pcrel_lo = std::make_shared<RelocModifierExpr>(RelocModifier::PCREL_LO, sym_imm->expr, sp);
                auto mem_op = std::make_shared<MemoryOperand>(pcrel_lo, t1, sp);

                result.push_back(ExpandedInstruction{"auipc", {t1, pcrel_hi}, sp, true});
                result.push_back(ExpandedInstruction{"jalr",  {x0, mem_op}, sp, true});
                return result;
            }
        }
    }

    if (mnem == "la") {
        if (ops.size() == 2) {
            auto sym_imm = std::dynamic_pointer_cast<ImmediateOperand>(ops[1]);
            if (sym_imm) {
                // Canonical deterministic 8-byte expansion: auipc rd, %pcrel_hi(sym) + addi rd, rd, %pcrel_lo(anchor)
                auto pcrel_hi = std::make_shared<ImmediateOperand>(
                    std::make_shared<RelocModifierExpr>(RelocModifier::PCREL_HI, sym_imm->expr, sp), sp);
                auto pcrel_lo = std::make_shared<ImmediateOperand>(
                    std::make_shared<RelocModifierExpr>(RelocModifier::PCREL_LO, sym_imm->expr, sp), sp);

                result.push_back(ExpandedInstruction{"auipc", {ops[0], pcrel_hi}, sp, true});
                result.push_back(ExpandedInstruction{"addi",  {ops[0], ops[0], pcrel_lo}, sp, true});
                return result;
            }
        }
    }

    if (mnem == "li") {
        if (ops.size() == 2) {
            auto imm_op = std::dynamic_pointer_cast<ImmediateOperand>(ops[1]);
            if (imm_op && imm_op->expr->kind == ExprKind::CONSTANT) {
                int64_t val = std::static_pointer_cast<ConstantExpr>(imm_op->expr)->value;
                auto norm = normalize_u32_literal(val);
                if (!norm.has_value()) {
                    diag_.error(imm_op->span, "literal value out of 32-bit range for 'li': " + std::to_string(val));
                    return result;
                }

                uint32_t u32 = *norm;
                int32_t s32 = static_cast<int32_t>(u32);

                // Small 12-bit signed immediate
                if (s32 >= -2048 && s32 <= 2047) {
                    auto const_expr = std::make_shared<ConstantExpr>(s32, sp);
                    result.push_back(ExpandedInstruction{"addi", {ops[0], x0, std::make_shared<ImmediateOperand>(const_expr, sp)}, sp, true});
                    return result;
                }

                // Lower 12 bits zero -> single LUI
                if ((s32 & 0xFFF) == 0) {
                    auto hi_expr = std::make_shared<ConstantExpr>((s32 >> 12) & 0xFFFFF, sp);
                    result.push_back(ExpandedInstruction{"lui", {ops[0], std::make_shared<ImmediateOperand>(hi_expr, sp)}, sp, true});
                    return result;
                }

                // General 32-bit decomposition
                auto [hi_val, lo_val] = decompose_li(s32);
                auto hi_expr = std::make_shared<ConstantExpr>(hi_val, sp);
                auto lo_expr = std::make_shared<ConstantExpr>(lo_val, sp);

                result.push_back(ExpandedInstruction{"lui",  {ops[0], std::make_shared<ImmediateOperand>(hi_expr, sp)}, sp, true});
                result.push_back(ExpandedInstruction{"addi", {ops[0], ops[0], std::make_shared<ImmediateOperand>(lo_expr, sp)}, sp, true});
                return result;
            } else {
                // If it's a symbolic expression in 'li', expand to lui + addi
                auto hi_expr = std::make_shared<RelocModifierExpr>(RelocModifier::HI, imm_op->expr, sp);
                auto lo_expr = std::make_shared<RelocModifierExpr>(RelocModifier::LO, imm_op->expr, sp);
                result.push_back(ExpandedInstruction{"lui",  {ops[0], std::make_shared<ImmediateOperand>(hi_expr, sp)}, sp, true});
                result.push_back(ExpandedInstruction{"addi", {ops[0], ops[0], std::make_shared<ImmediateOperand>(lo_expr, sp)}, sp, true});
                return result;
            }
        }
    }

    // Default: non-pseudo concrete instruction
    result.push_back(ExpandedInstruction{inst.mnemonic, inst.operands, inst.span, false});
    return result;
}

} // namespace an32asm
