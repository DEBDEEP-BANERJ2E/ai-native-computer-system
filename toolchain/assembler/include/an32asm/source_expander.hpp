#pragma once

#include "token.hpp"
#include "source_manager.hpp"
#include "diagnostic.hpp"
#include <vector>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <optional>
#include <memory>

namespace an32asm {

struct MacroDef {
    std::string name;
    std::vector<std::string> params;
    std::vector<Token> body;
    SourceSpan def_span;
};

class SourceExpander {
public:
    SourceExpander(SourceManager& sm, DiagnosticEngine& diag, std::vector<std::string> include_paths = {});

    std::vector<Token> expand(const std::vector<Token>& input_tokens);

    void add_include_path(std::string path);
    void define_constant(const std::string& name, int64_t val);
    std::optional<int64_t> get_constant(const std::string& name) const;
    bool is_defined(const std::string& name) const;

private:
    SourceManager& sm_;
    DiagnosticEngine& diag_;
    std::vector<std::string> include_paths_;
    std::unordered_map<std::string, MacroDef> macros_;
    std::unordered_map<std::string, int64_t> constants_;
    std::unordered_set<std::string> included_files_;
    uint32_t unique_expansion_counter_ = 0;

    std::optional<std::string> find_include_file(const std::string& filename) const;
    int64_t evaluate_constant_tokens(const std::vector<Token>& tokens, size_t& pos);
    int64_t evaluate_primary(const std::vector<Token>& tokens, size_t& pos);
    int64_t evaluate_unary(const std::vector<Token>& tokens, size_t& pos);
    int64_t evaluate_binary(const std::vector<Token>& tokens, size_t& pos, int min_prec);

    std::vector<Token> expand_macro(const MacroDef& macro, const std::vector<std::vector<Token>>& args,
                                   const SourcePos& call_site, std::shared_ptr<ExpansionContext> parent_exp);
};

} // namespace an32asm
