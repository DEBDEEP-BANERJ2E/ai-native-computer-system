#include "an32asm/source_expander.hpp"
#include "an32asm/lexer.hpp"
#include <filesystem>
#include <sstream>

namespace an32asm {

SourceExpander::SourceExpander(SourceManager& sm, DiagnosticEngine& diag, std::vector<std::string> include_paths)
    : sm_(sm), diag_(diag), include_paths_(std::move(include_paths)) {
    // Current working directory is always an implicit search path
    include_paths_.insert(include_paths_.begin(), ".");
}

void SourceExpander::add_include_path(std::string path) {
    include_paths_.push_back(std::move(path));
}

void SourceExpander::define_constant(const std::string& name, int64_t val) {
    constants_[name] = val;
}

std::optional<int64_t> SourceExpander::get_constant(const std::string& name) const {
    auto it = constants_.find(name);
    if (it != constants_.end()) {
        return it->second;
    }
    return std::nullopt;
}

bool SourceExpander::is_defined(const std::string& name) const {
    return constants_.find(name) != constants_.end() || macros_.find(name) != macros_.end();
}

std::optional<std::string> SourceExpander::find_include_file(const std::string& filename) const {
    namespace fs = std::filesystem;
    if (fs::path(filename).is_absolute()) {
        if (fs::exists(filename)) return filename;
        return std::nullopt;
    }
    for (const auto& dir : include_paths_) {
        fs::path p = fs::path(dir) / filename;
        if (fs::exists(p)) {
            return p.string();
        }
    }
    return std::nullopt;
}

int64_t SourceExpander::evaluate_primary(const std::vector<Token>& tokens, size_t& pos) {
    if (pos >= tokens.size()) return 0;
    const auto& tok = tokens[pos];

    if (tok.type == TokenType::INTEGER) {
        pos++;
        return static_cast<int64_t>(tok.int_value);
    }
    if (tok.type == TokenType::CHAR_LITERAL) {
        pos++;
        return static_cast<int64_t>(tok.int_value);
    }
    if (tok.type == TokenType::IDENTIFIER) {
        pos++;
        auto it = constants_.find(tok.text);
        if (it != constants_.end()) {
            return it->second;
        }
        diag_.error(tok.span, "undefined constant in conditional assembly expression: '" + tok.text + "'");
        return 0;
    }
    if (tok.type == TokenType::LPAREN) {
        pos++; // '('
        int64_t val = evaluate_constant_tokens(tokens, pos);
        if (pos < tokens.size() && tokens[pos].type == TokenType::RPAREN) {
            pos++; // ')'
        } else {
            diag_.error(tok.span, "expected ')' in constant expression");
        }
        return val;
    }

    diag_.error(tok.span, "expected constant expression operand");
    pos++;
    return 0;
}

int64_t SourceExpander::evaluate_unary(const std::vector<Token>& tokens, size_t& pos) {
    if (pos >= tokens.size()) return 0;
    const auto& tok = tokens[pos];

    if (tok.type == TokenType::PLUS) {
        pos++;
        return evaluate_unary(tokens, pos);
    }
    if (tok.type == TokenType::MINUS) {
        pos++;
        return -evaluate_unary(tokens, pos);
    }
    if (tok.type == TokenType::TILDE) {
        pos++;
        return ~evaluate_unary(tokens, pos);
    }
    if (tok.type == TokenType::EXCLAIM) {
        pos++;
        return !evaluate_unary(tokens, pos);
    }

    return evaluate_primary(tokens, pos);
}

namespace {
int get_precedence(TokenType type) {
    switch (type) {
        case TokenType::PIPE:           return 1;
        case TokenType::CARET:          return 2;
        case TokenType::AMPERSAND:      return 3;
        case TokenType::EQUAL_EQUAL:
        case TokenType::EXCLAIM_EQUAL:  return 4;
        case TokenType::LESS:
        case TokenType::LESS_EQUAL:
        case TokenType::GREATER:
        case TokenType::GREATER_EQUAL:  return 5;
        case TokenType::LSHIFT:
        case TokenType::RSHIFT:         return 6;
        case TokenType::PLUS:
        case TokenType::MINUS:          return 7;
        case TokenType::STAR:
        case TokenType::SLASH:
        case TokenType::PERCENT:        return 8;
        default:                        return 0;
    }
}
}

int64_t SourceExpander::evaluate_binary(const std::vector<Token>& tokens, size_t& pos, int min_prec) {
    int64_t lhs = evaluate_unary(tokens, pos);

    while (pos < tokens.size()) {
        int prec = get_precedence(tokens[pos].type);
        if (prec < min_prec || prec == 0) break;

        TokenType op = tokens[pos].type;
        SourceSpan op_span = tokens[pos].span;
        pos++;

        int64_t rhs = evaluate_binary(tokens, pos, prec + 1);

        switch (op) {
            case TokenType::PLUS:          lhs += rhs; break;
            case TokenType::MINUS:         lhs -= rhs; break;
            case TokenType::STAR:          lhs *= rhs; break;
            case TokenType::SLASH:
                if (rhs == 0) {
                    diag_.error(op_span, "division by zero in constant expression");
                    lhs = 0;
                } else {
                    lhs /= rhs;
                }
                break;
            case TokenType::PERCENT:
                if (rhs == 0) {
                    diag_.error(op_span, "modulo by zero in constant expression");
                    lhs = 0;
                } else {
                    lhs %= rhs;
                }
                break;
            case TokenType::LSHIFT:        lhs <<= (rhs & 63); break;
            case TokenType::RSHIFT:        lhs >>= (rhs & 63); break;
            case TokenType::AMPERSAND:     lhs &= rhs; break;
            case TokenType::PIPE:          lhs |= rhs; break;
            case TokenType::CARET:         lhs ^= rhs; break;
            case TokenType::EQUAL_EQUAL:   lhs = (lhs == rhs); break;
            case TokenType::EXCLAIM_EQUAL: lhs = (lhs != rhs); break;
            case TokenType::LESS:          lhs = (lhs < rhs); break;
            case TokenType::LESS_EQUAL:    lhs = (lhs <= rhs); break;
            case TokenType::GREATER:       lhs = (lhs > rhs); break;
            case TokenType::GREATER_EQUAL: lhs = (lhs >= rhs); break;
            default: break;
        }
    }

    return lhs;
}

int64_t SourceExpander::evaluate_constant_tokens(const std::vector<Token>& tokens, size_t& pos) {
    return evaluate_binary(tokens, pos, 1);
}

std::vector<Token> SourceExpander::expand_macro(const MacroDef& macro,
                                               const std::vector<std::vector<Token>>& args,
                                               const SourcePos& call_site,
                                               std::shared_ptr<ExpansionContext> parent_exp) {
    uint32_t exp_id = ++unique_expansion_counter_;
    auto exp_ctx = std::make_shared<ExpansionContext>();
    exp_ctx->macro_or_context_name = macro.name;
    exp_ctx->call_site = call_site;
    exp_ctx->parent = parent_exp;

    // Build parameter replacement map
    std::unordered_map<std::string, std::vector<Token>> param_map;
    for (size_t i = 0; i < macro.params.size(); ++i) {
        if (i < args.size()) {
            param_map[macro.params[i]] = args[i];
        } else {
            param_map[macro.params[i]] = {}; // default empty
        }
    }

    std::vector<Token> expanded;
    for (size_t i = 0; i < macro.body.size(); ++i) {
        const auto& tok = macro.body[i];

        // 1. Check for \@ unique expansion ID
        if (tok.type == TokenType::MACRO_UNIQUE_ID) {
            Token num_tok;
            num_tok.type = TokenType::INTEGER;
            num_tok.int_value = exp_id;
            num_tok.text = std::to_string(exp_id);
            num_tok.span = tok.span;
            num_tok.span.expansion = exp_ctx;
            expanded.push_back(num_tok);
            continue;
        }

        // 2. Check for parameter substitution (\param, $param, or identifier matching param name)
        std::string param_name;
        bool is_param = false;
        if (tok.type == TokenType::MACRO_PARAM_REF) {
            param_name = tok.text;
            is_param = true;
        } else if (tok.type == TokenType::IDENTIFIER) {
            auto it = param_map.find(tok.text);
            if (it != param_map.end()) {
                param_name = tok.text;
                is_param = true;
            }
        }

        if (is_param) {
            auto it = param_map.find(param_name);
            if (it != param_map.end()) {
                for (auto arg_tok : it->second) {
                    arg_tok.span.expansion = exp_ctx;
                    expanded.push_back(arg_tok);
                }
                continue;
            }
        }

        // 3. Hygiene for numeric local labels inside macro (1: -> unique symbol)
        if (tok.type == TokenType::LOCAL_LABEL_DEF) {
            Token syn_tok;
            syn_tok.type = TokenType::IDENTIFIER;
            syn_tok.text = "__macro_" + std::to_string(exp_id) + "_lbl_" + std::to_string(tok.local_label_num) + ":";
            syn_tok.span = tok.span;
            syn_tok.span.expansion = exp_ctx;
            expanded.push_back(syn_tok);
            continue;
        }
        if (tok.type == TokenType::LOCAL_LABEL_REF) {
            Token syn_tok;
            syn_tok.type = TokenType::IDENTIFIER;
            syn_tok.text = "__macro_" + std::to_string(exp_id) + "_lbl_" + std::to_string(tok.local_label_num);
            syn_tok.span = tok.span;
            syn_tok.span.expansion = exp_ctx;
            expanded.push_back(syn_tok);
            continue;
        }

        // Standard token with expansion context attached
        Token copy_tok = tok;
        copy_tok.span.expansion = exp_ctx;
        expanded.push_back(copy_tok);
    }

    return expanded;
}

std::vector<Token> SourceExpander::expand(const std::vector<Token>& input_tokens) {
    std::vector<Token> current_tokens = input_tokens;
    bool made_progress = true;
    size_t pass_count = 0;

    while (made_progress && pass_count < 100) {
        pass_count++;
        made_progress = false;
        std::vector<Token> out;

        struct CondState {
            bool condition_met = false;
            bool branch_taken = false;
            bool is_active = true;
        };
        std::vector<CondState> cond_stack;

        auto is_currently_active = [&cond_stack]() {
            for (const auto& st : cond_stack) {
                if (!st.is_active) return false;
            }
            return true;
        };

        for (size_t i = 0; i < current_tokens.size(); ++i) {
            const auto& tok = current_tokens[i];

            // 1. Handle Conditional Assembly Directives (.if, .elseif, .else, .endif, .ifdef, .ifndef)
            if (tok.type == TokenType::DIR_IF || tok.type == TokenType::DIR_IFDEF || tok.type == TokenType::DIR_IFNDEF) {
                made_progress = true;
                bool parent_active = is_currently_active();
                bool cond = false;

                // Collect tokens until newline or semicolon
                std::vector<Token> expr_tokens;
                size_t j = i + 1;
                while (j < current_tokens.size() &&
                       current_tokens[j].type != TokenType::NEWLINE &&
                       current_tokens[j].type != TokenType::SEMICOLON &&
                       current_tokens[j].type != TokenType::END_OF_FILE) {
                    expr_tokens.push_back(current_tokens[j]);
                    j++;
                }
                i = j; // skip to end of statement

                if (parent_active) {
                    if (tok.type == TokenType::DIR_IF) {
                        size_t pos = 0;
                        int64_t val = evaluate_constant_tokens(expr_tokens, pos);
                        cond = (val != 0);
                    } else if (tok.type == TokenType::DIR_IFDEF) {
                        if (!expr_tokens.empty() && expr_tokens[0].type == TokenType::IDENTIFIER) {
                            cond = is_defined(expr_tokens[0].text);
                        }
                    } else if (tok.type == TokenType::DIR_IFNDEF) {
                        if (!expr_tokens.empty() && expr_tokens[0].type == TokenType::IDENTIFIER) {
                            cond = !is_defined(expr_tokens[0].text);
                        }
                    }
                }

                CondState st;
                st.condition_met = cond;
                st.branch_taken = (parent_active && cond);
                st.is_active = (parent_active && cond);
                cond_stack.push_back(st);
                continue;
            }

            if (tok.type == TokenType::DIR_ELSEIF) {
                made_progress = true;
                std::vector<Token> expr_tokens;
                size_t j = i + 1;
                while (j < current_tokens.size() &&
                       current_tokens[j].type != TokenType::NEWLINE &&
                       current_tokens[j].type != TokenType::SEMICOLON &&
                       current_tokens[j].type != TokenType::END_OF_FILE) {
                    expr_tokens.push_back(current_tokens[j]);
                    j++;
                }
                i = j;

                if (!cond_stack.empty()) {
                    auto& st = cond_stack.back();
                    bool parent_active = true;
                    for (size_t k = 0; k < cond_stack.size() - 1; ++k) {
                        if (!cond_stack[k].is_active) { parent_active = false; break; }
                    }

                    if (parent_active && !st.branch_taken) {
                        size_t pos = 0;
                        int64_t val = evaluate_constant_tokens(expr_tokens, pos);
                        if (val != 0) {
                            st.condition_met = true;
                            st.branch_taken = true;
                            st.is_active = true;
                        } else {
                            st.is_active = false;
                        }
                    } else {
                        st.is_active = false;
                    }
                } else {
                    diag_.error(tok.span, "unmatched .elseif directive");
                }
                continue;
            }

            if (tok.type == TokenType::DIR_ELSE) {
                made_progress = true;
                if (!cond_stack.empty()) {
                    auto& st = cond_stack.back();
                    bool parent_active = true;
                    for (size_t k = 0; k < cond_stack.size() - 1; ++k) {
                        if (!cond_stack[k].is_active) { parent_active = false; break; }
                    }
                    if (parent_active && !st.branch_taken) {
                        st.is_active = true;
                        st.branch_taken = true;
                    } else {
                        st.is_active = false;
                    }
                } else {
                    diag_.error(tok.span, "unmatched .else directive");
                }
                continue;
            }

            if (tok.type == TokenType::DIR_ENDIF) {
                made_progress = true;
                if (!cond_stack.empty()) {
                    cond_stack.pop_back();
                } else {
                    diag_.error(tok.span, "unmatched .endif directive");
                }
                continue;
            }

            // If inside inactive conditional branch, skip this token
            if (!is_currently_active()) {
                made_progress = true;
                continue;
            }

            // 2. Handle .equ and .set to update constant table
            if (tok.type == TokenType::DIR_EQU || tok.type == TokenType::DIR_SET) {
                out.push_back(tok);
                size_t j = i + 1;
                if (j < current_tokens.size() && current_tokens[j].type == TokenType::IDENTIFIER) {
                    std::string sym_name = current_tokens[j].text;
                    out.push_back(current_tokens[j]);
                    j++;
                    if (j < current_tokens.size() && current_tokens[j].type == TokenType::COMMA) {
                        out.push_back(current_tokens[j]);
                        j++;
                        // Collect expr tokens
                        std::vector<Token> expr_tokens;
                        while (j < current_tokens.size() &&
                               current_tokens[j].type != TokenType::NEWLINE &&
                               current_tokens[j].type != TokenType::SEMICOLON &&
                               current_tokens[j].type != TokenType::END_OF_FILE) {
                            expr_tokens.push_back(current_tokens[j]);
                            out.push_back(current_tokens[j]);
                            j++;
                        }
                        size_t pos = 0;
                        int64_t val = evaluate_constant_tokens(expr_tokens, pos);
                        define_constant(sym_name, val);
                    }
                }
                i = j - 1;
                continue;
            }

            // 3. Handle .include "file"
            if (tok.type == TokenType::DIR_INCLUDE) {
                made_progress = true;
                size_t j = i + 1;
                if (j < current_tokens.size() && current_tokens[j].type == TokenType::STRING) {
                    std::string inc_file = current_tokens[j].text;
                    auto found_path = find_include_file(inc_file);
                    if (found_path) {
                        if (included_files_.count(*found_path)) {
                            diag_.error(tok.span, "circular .include detected for file: '" + inc_file + "'");
                        } else {
                            included_files_.insert(*found_path);
                            auto inc_id = sm_.load_file(*found_path);
                            if (inc_id) {
                                Lexer inc_lexer(sm_, *inc_id, diag_);
                                auto inc_tokens = inc_lexer.tokenize();
                                // Remove EOF token from included stream
                                if (!inc_tokens.empty() && inc_tokens.back().type == TokenType::END_OF_FILE) {
                                    inc_tokens.pop_back();
                                }
                                out.insert(out.end(), inc_tokens.begin(), inc_tokens.end());
                            } else {
                                diag_.error(tok.span, "failed to read included file: '" + *found_path + "'");
                            }
                        }
                    } else {
                        diag_.error(tok.span, "could not find include file: '" + inc_file + "'");
                    }
                    j++; // skip filename string
                } else {
                    diag_.error(tok.span, "expected string filename after .include");
                }
                i = j - 1;
                continue;
            }

            // 4. Handle .macro <name> [params...] ... .endm
            if (tok.type == TokenType::DIR_MACRO) {
                made_progress = true;
                size_t j = i + 1;
                if (j < current_tokens.size() && current_tokens[j].type == TokenType::IDENTIFIER) {
                    MacroDef mdef;
                    mdef.name = current_tokens[j].text;
                    mdef.def_span = tok.span;
                    j++;

                    // Parse optional parameter list (comma-separated identifiers)
                    while (j < current_tokens.size() &&
                           current_tokens[j].type != TokenType::NEWLINE &&
                           current_tokens[j].type != TokenType::SEMICOLON) {
                        if (current_tokens[j].type == TokenType::IDENTIFIER) {
                            mdef.params.push_back(current_tokens[j].text);
                        }
                        j++;
                    }

                    // Collect body until .endm (handling nested .macro)
                    int macro_depth = 1;
                    while (j < current_tokens.size() && macro_depth > 0) {
                        if (current_tokens[j].type == TokenType::DIR_MACRO) {
                            macro_depth++;
                            mdef.body.push_back(current_tokens[j]);
                        } else if (current_tokens[j].type == TokenType::DIR_ENDM) {
                            macro_depth--;
                            if (macro_depth == 0) {
                                j++; // consume .endm
                                break;
                            }
                            mdef.body.push_back(current_tokens[j]);
                        } else {
                            mdef.body.push_back(current_tokens[j]);
                        }
                        j++;
                    }

                    if (macro_depth > 0) {
                        diag_.error(mdef.def_span, "unterminated .macro definition for '" + mdef.name + "'");
                    } else {
                        macros_[mdef.name] = std::move(mdef);
                    }
                    i = j - 1;
                    continue;
                } else {
                    diag_.error(tok.span, "expected macro name after .macro");
                }
            }

            // 5. Handle .rept <count> ... .endr
            if (tok.type == TokenType::DIR_REPT) {
                made_progress = true;
                size_t j = i + 1;
                std::vector<Token> count_expr;
                while (j < current_tokens.size() &&
                       current_tokens[j].type != TokenType::NEWLINE &&
                       current_tokens[j].type != TokenType::SEMICOLON) {
                    count_expr.push_back(current_tokens[j]);
                    j++;
                }
                size_t pos = 0;
                int64_t count = evaluate_constant_tokens(count_expr, pos);
                if (count < 0) {
                    diag_.error(tok.span, "repeat count cannot be negative");
                    count = 0;
                }

                std::vector<Token> body;
                int rept_depth = 1;
                while (j < current_tokens.size() && rept_depth > 0) {
                    if (current_tokens[j].type == TokenType::DIR_REPT) {
                        rept_depth++;
                        body.push_back(current_tokens[j]);
                    } else if (current_tokens[j].type == TokenType::DIR_ENDR) {
                        rept_depth--;
                        if (rept_depth == 0) {
                            j++; // consume .endr
                            break;
                        }
                        body.push_back(current_tokens[j]);
                    } else {
                        body.push_back(current_tokens[j]);
                    }
                    j++;
                }

                for (int64_t r = 0; r < count; ++r) {
                    out.insert(out.end(), body.begin(), body.end());
                }
                i = j - 1;
                continue;
            }

            // 6. Handle Macro Invocations
            if (tok.type == TokenType::IDENTIFIER) {
                auto it = macros_.find(tok.text);
                if (it != macros_.end()) {
                    made_progress = true;
                    const auto& mdef = it->second;
                    size_t j = i + 1;

                    // Collect comma-separated arguments until newline, semicolon, or EOF
                    std::vector<std::vector<Token>> args;
                    std::vector<Token> current_arg;
                    int paren_depth = 0;

                    while (j < current_tokens.size() &&
                           (paren_depth > 0 || (current_tokens[j].type != TokenType::NEWLINE &&
                                                current_tokens[j].type != TokenType::SEMICOLON &&
                                                current_tokens[j].type != TokenType::END_OF_FILE))) {
                        if (current_tokens[j].type == TokenType::LPAREN) paren_depth++;
                        if (current_tokens[j].type == TokenType::RPAREN) paren_depth--;

                        if (paren_depth == 0 && current_tokens[j].type == TokenType::COMMA) {
                            args.push_back(current_arg);
                            current_arg.clear();
                        } else {
                            current_arg.push_back(current_tokens[j]);
                        }
                        j++;
                    }
                    if (!current_arg.empty() || !args.empty()) {
                        args.push_back(current_arg);
                    }

                    auto expanded = expand_macro(mdef, args, tok.span.start, tok.span.expansion);
                    out.insert(out.end(), expanded.begin(), expanded.end());

                    i = j - 1;
                    continue;
                }
            }

            out.push_back(tok);
        }

        if (!cond_stack.empty()) {
            diag_.error(out.empty() ? SourceSpan() : out.back().span, "unterminated conditional assembly (.if without .endif)");
        }

        current_tokens = std::move(out);
    }

    return current_tokens;
}

} // namespace an32asm
