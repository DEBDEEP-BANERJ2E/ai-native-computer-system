#pragma once

#include "ast.hpp"
#include "diagnostic.hpp"
#include <vector>
#include <string>
#include <optional>

namespace an32asm {

struct ExpandedInstruction {
    std::string mnemonic;
    std::vector<OperandPtr> operands;
    SourceSpan span;
    bool is_pseudo = false;
};

class PseudoExpander {
public:
    explicit PseudoExpander(DiagnosticEngine& diag);

    bool is_pseudo_instruction(const std::string& mnem) const;

    // Expands an instruction statement into 1 or more concrete instructions
    std::vector<ExpandedInstruction> expand(const InstructionStatement& inst);

    // Load immediate decomposition
    static std::optional<uint32_t> normalize_u32_literal(int64_t val);
    static std::pair<int64_t, int64_t> decompose_li(int64_t val);

private:
    DiagnosticEngine& diag_;
};

} // namespace an32asm
