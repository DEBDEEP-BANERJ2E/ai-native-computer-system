#include "an32asm/parser.hpp"
#include <algorithm>
#include <unordered_set>

namespace an32asm {

Parser::Parser(std::vector<Token> tokens, DiagnosticEngine& diag)
    : tokens_(std::move(tokens)), diag_(diag) {}

const Token& Parser::peek(size_t ahead) const {
    if (pos_ + ahead < tokens_.size()) {
        return tokens_[pos_ + ahead];
    }
    static const Token eof_tok{TokenType::END_OF_FILE, SourceSpan(), "", 0, 0, 0, false};
    return eof_tok;
}

const Token& Parser::current() const {
    return peek(0);
}

Token Parser::advance() {
    if (pos_ < tokens_.size()) {
        return tokens_[pos_++];
    }
    return peek(0);
}

bool Parser::match(TokenType type) {
    if (check(type)) {
        advance();
        return true;
    }
    return false;
}

bool Parser::check(TokenType type) const {
    return current().type == type;
}

void Parser::skip_to_statement_end() {
    while (!check(TokenType::NEWLINE) && !check(TokenType::SEMICOLON) && !check(TokenType::END_OF_FILE)) {
        advance();
    }
    if (check(TokenType::NEWLINE) || check(TokenType::SEMICOLON)) {
        advance();
    }
}

ExprPtr Parser::parse_primary_expr() {
    SourcePos start_pos = current().span.start;

    if (check(TokenType::INTEGER)) {
        auto tok = advance();
        return std::make_shared<ConstantExpr>(static_cast<int64_t>(tok.int_value), tok.span);
    }
    if (check(TokenType::CHAR_LITERAL)) {
        auto tok = advance();
        return std::make_shared<ConstantExpr>(static_cast<int64_t>(tok.int_value), tok.span);
    }
    if (check(TokenType::DOT)) {
        auto tok = advance();
        return std::make_shared<DotExpr>(tok.span);
    }
    if (check(TokenType::IDENTIFIER)) {
        auto tok = advance();
        return std::make_shared<SymbolExpr>(tok.text, tok.span);
    }
    if (check(TokenType::LOCAL_LABEL_REF)) {
        auto tok = advance();
        return std::make_shared<SymbolExpr>(tok.local_label_num, tok.is_forward_ref, tok.span);
    }
    if (check(TokenType::MOD_HI) || check(TokenType::MOD_LO) ||
        check(TokenType::MOD_PCREL_HI) || check(TokenType::MOD_PCREL_LO)) {
        auto mod_tok = advance();
        RelocModifier mod = RelocModifier::NONE;
        if (mod_tok.type == TokenType::MOD_HI) mod = RelocModifier::HI;
        else if (mod_tok.type == TokenType::MOD_LO) mod = RelocModifier::LO;
        else if (mod_tok.type == TokenType::MOD_PCREL_HI) mod = RelocModifier::PCREL_HI;
        else if (mod_tok.type == TokenType::MOD_PCREL_LO) mod = RelocModifier::PCREL_LO;

        if (!match(TokenType::LPAREN)) {
            diag_.error(mod_tok.span, "expected '(' after relocation modifier");
            return nullptr;
        }
        auto sub = parse_expression();
        if (!match(TokenType::RPAREN)) {
            diag_.error(current().span, "expected ')' after relocation modifier argument");
        }
        return std::make_shared<RelocModifierExpr>(mod, sub, SourceSpan(start_pos, current().span.end));
    }
    if (match(TokenType::LPAREN)) {
        auto sub = parse_expression();
        if (!match(TokenType::RPAREN)) {
            diag_.error(current().span, "expected ')'");
        }
        return sub;
    }

    diag_.error(current().span, "expected expression");
    advance();
    return nullptr;
}

ExprPtr Parser::parse_unary_expr() {
    SourcePos start_pos = current().span.start;

    if (check(TokenType::PLUS)) {
        advance();
        auto sub = parse_unary_expr();
        return std::make_shared<UnaryExpr>(UnaryOp::PLUS, sub, SourceSpan(start_pos, sub ? sub->span.end : current().span.end));
    }
    if (check(TokenType::MINUS)) {
        advance();
        auto sub = parse_unary_expr();
        return std::make_shared<UnaryExpr>(UnaryOp::MINUS, sub, SourceSpan(start_pos, sub ? sub->span.end : current().span.end));
    }
    if (check(TokenType::TILDE)) {
        advance();
        auto sub = parse_unary_expr();
        return std::make_shared<UnaryExpr>(UnaryOp::BIT_NOT, sub, SourceSpan(start_pos, sub ? sub->span.end : current().span.end));
    }
    if (check(TokenType::EXCLAIM)) {
        advance();
        auto sub = parse_unary_expr();
        return std::make_shared<UnaryExpr>(UnaryOp::LOGICAL_NOT, sub, SourceSpan(start_pos, sub ? sub->span.end : current().span.end));
    }

    return parse_primary_expr();
}

namespace {
int get_binop_precedence(TokenType type) {
    switch (type) {
        case TokenType::PIPE:       return 1;
        case TokenType::CARET:      return 2;
        case TokenType::AMPERSAND:  return 3;
        case TokenType::LSHIFT:
        case TokenType::RSHIFT:     return 4;
        case TokenType::PLUS:
        case TokenType::MINUS:      return 5;
        case TokenType::STAR:
        case TokenType::SLASH:
        case TokenType::PERCENT:    return 6;
        default:                    return 0;
    }
}

BinaryOp token_to_binop(TokenType type) {
    switch (type) {
        case TokenType::PLUS:      return BinaryOp::ADD;
        case TokenType::MINUS:     return BinaryOp::SUB;
        case TokenType::STAR:      return BinaryOp::MUL;
        case TokenType::SLASH:     return BinaryOp::DIV;
        case TokenType::PERCENT:   return BinaryOp::MOD;
        case TokenType::LSHIFT:    return BinaryOp::SHL;
        case TokenType::RSHIFT:    return BinaryOp::SHR;
        case TokenType::AMPERSAND: return BinaryOp::AND;
        case TokenType::PIPE:      return BinaryOp::OR;
        case TokenType::CARET:     return BinaryOp::XOR;
        default:                   return BinaryOp::ADD;
    }
}
}

ExprPtr Parser::parse_binary_expr(int min_prec) {
    auto lhs = parse_unary_expr();
    if (!lhs) return nullptr;

    while (true) {
        int prec = get_binop_precedence(current().type);
        if (prec < min_prec || prec == 0) break;

        TokenType op_tok = current().type;
        advance();

        auto rhs = parse_binary_expr(prec + 1);
        if (!rhs) break;

        lhs = std::make_shared<BinaryExpr>(token_to_binop(op_tok), lhs, rhs,
                                           SourceSpan(lhs->span.start, rhs->span.end));
    }

    return lhs;
}

ExprPtr Parser::parse_expression() {
    return parse_binary_expr(1);
}

OperandPtr Parser::parse_operand() {
    SourcePos start_pos = current().span.start;

    // Check for register operand
    if (check(TokenType::REG_X)) {
        auto tok = advance();
        return std::make_shared<XRegOperand>(tok.reg_index, tok.span);
    }
    if (check(TokenType::REG_CAP)) {
        auto tok = advance();
        return std::make_shared<CapRegOperand>(tok.reg_index, tok.span);
    }

    // Check for memory operand without leading offset e.g. (sp) or (c1)
    if (check(TokenType::LPAREN)) {
        if (peek(1).type == TokenType::REG_X || peek(1).type == TokenType::REG_CAP) {
            advance(); // '('
            auto base_tok = advance();
            OperandPtr base;
            if (base_tok.type == TokenType::REG_X) {
                base = std::make_shared<XRegOperand>(base_tok.reg_index, base_tok.span);
            } else {
                base = std::make_shared<CapRegOperand>(base_tok.reg_index, base_tok.span);
            }
            if (!match(TokenType::RPAREN)) {
                diag_.error(current().span, "expected ')' in memory operand");
            }
            auto zero_expr = std::make_shared<ConstantExpr>(0, SourceSpan(start_pos, start_pos));
            return std::make_shared<MemoryOperand>(zero_expr, base, SourceSpan(start_pos, current().span.end));
        }
    }

    // Parse expression (immediate or memory displacement)
    auto expr = parse_expression();
    if (!expr) return nullptr;

    // Check if this expression is followed by (reg) -> MemoryOperand
    if (check(TokenType::LPAREN)) {
        if (peek(1).type == TokenType::REG_X || peek(1).type == TokenType::REG_CAP) {
            advance(); // '('
            auto base_tok = advance();
            OperandPtr base;
            if (base_tok.type == TokenType::REG_X) {
                base = std::make_shared<XRegOperand>(base_tok.reg_index, base_tok.span);
            } else {
                base = std::make_shared<CapRegOperand>(base_tok.reg_index, base_tok.span);
            }
            if (!match(TokenType::RPAREN)) {
                diag_.error(current().span, "expected ')' in memory operand");
            }
            return std::make_shared<MemoryOperand>(expr, base, SourceSpan(start_pos, current().span.end));
        }
    }

    return std::make_shared<ImmediateOperand>(expr, SourceSpan(start_pos, expr->span.end));
}

void Parser::validate_instruction_operands(const std::string& mnem, const std::vector<OperandPtr>& ops, SourceSpan span) {
    (void)span;
    std::string lower_mnem = mnem;
    std::transform(lower_mnem.begin(), lower_mnem.end(), lower_mnem.begin(), [](unsigned char c) { return std::tolower(c); });

    static const std::unordered_set<std::string> cap_mem_ops = {
        "clb", "clh", "clw", "csb", "csh", "csw"
    };

    static const std::unordered_set<std::string> standard_mem_ops = {
        "lb", "lh", "lw", "lbu", "lhu", "sb", "sh", "sw"
    };

    if (cap_mem_ops.count(lower_mnem)) {
        // Must have format: clw rd, offset(cs1) or csw rs2, offset(cs1)
        if (ops.size() >= 2) {
            auto mem_op = std::dynamic_pointer_cast<MemoryOperand>(ops[1]);
            if (!mem_op) {
                diag_.error(ops[1]->span, "instruction '" + mnem + "' requires memory operand offset(cs1)");
            } else if (mem_op->base_reg->kind != OperandKind::REG_CAP) {
                diag_.error(mem_op->base_reg->span, "instruction '" + mnem + "' requires capability base register (c0..c7); got integer register");
            }
        }
    } else if (standard_mem_ops.count(lower_mnem)) {
        if (ops.size() >= 2) {
            auto mem_op = std::dynamic_pointer_cast<MemoryOperand>(ops[1]);
            if (mem_op && mem_op->base_reg->kind == OperandKind::REG_CAP) {
                diag_.error(mem_op->base_reg->span, "standard memory instruction '" + mnem + "' requires integer base register (x0..x31); got capability register");
            }
        }
    } else if (lower_mnem == "csetbounds" || lower_mnem == "candperm" || lower_mnem == "cincoffset") {
        // Format: cd, cs1, rs2
        if (ops.size() >= 1 && ops[0]->kind != OperandKind::REG_CAP) {
            diag_.error(ops[0]->span, "instruction '" + mnem + "' destination must be capability register (c0..c7)");
        }
        if (ops.size() >= 2 && ops[1]->kind != OperandKind::REG_CAP) {
            diag_.error(ops[1]->span, "instruction '" + mnem + "' source cs1 must be capability register (c0..c7)");
        }
        if (ops.size() >= 3 && ops[2]->kind != OperandKind::REG_X) {
            diag_.error(ops[2]->span, "instruction '" + mnem + "' operand rs2 must be integer register (x0..x31)");
        }
    } else if (lower_mnem == "cgetbase" || lower_mnem == "cgetlen" || lower_mnem == "cgettag" ||
               lower_mnem == "cgetperm" || lower_mnem == "cgetoffset") {
        // Format: rd, cs1
        if (ops.size() >= 1 && ops[0]->kind != OperandKind::REG_X) {
            diag_.error(ops[0]->span, "instruction '" + mnem + "' destination must be integer register (x0..x31)");
        }
        if (ops.size() >= 2 && ops[1]->kind != OperandKind::REG_CAP) {
            diag_.error(ops[1]->span, "instruction '" + mnem + "' source cs1 must be capability register (c0..c7)");
        }
    } else if (lower_mnem == "cclear") {
        // Format: cd
        if (ops.size() >= 1 && ops[0]->kind != OperandKind::REG_CAP) {
            diag_.error(ops[0]->span, "instruction 'cclear' operand must be capability register (c0..c7)");
        }
    }
}

StatementPtr Parser::parse_directive() {
    Token dir_tok = advance();
    auto stmt = std::make_shared<DirectiveStatement>(dir_tok.type, dir_tok.text, dir_tok.span);

    // Parse directive arguments
    while (!check(TokenType::NEWLINE) && !check(TokenType::SEMICOLON) && !check(TokenType::END_OF_FILE)) {
        if (check(TokenType::STRING)) {
            stmt->string_args.push_back(advance().text);
        } else if (check(TokenType::IDENTIFIER) && stmt->directive_type == TokenType::DIR_TYPE) {
            stmt->symbol_arg = advance().text;
        } else if (check(TokenType::IDENTIFIER) && stmt->directive_type == TokenType::DIR_SIZE) {
            stmt->symbol_arg = advance().text;
        } else if (check(TokenType::IDENTIFIER) && (stmt->directive_type == TokenType::DIR_GLOBL ||
                                                    stmt->directive_type == TokenType::DIR_LOCAL ||
                                                    stmt->directive_type == TokenType::DIR_WEAK)) {
            stmt->symbol_arg = advance().text;
        } else {
            auto expr = parse_expression();
            if (expr) {
                stmt->expr_args.push_back(expr);
            }
        }

        if (check(TokenType::COMMA)) {
            advance();
        } else {
            break;
        }
    }

    return stmt;
}

StatementPtr Parser::parse_instruction() {
    Token mnem_tok = advance();
    std::vector<OperandPtr> operands;

    while (!check(TokenType::NEWLINE) && !check(TokenType::SEMICOLON) && !check(TokenType::END_OF_FILE)) {
        auto op = parse_operand();
        if (op) {
            operands.push_back(op);
        }

        if (check(TokenType::COMMA)) {
            advance();
        } else {
            break;
        }
    }

    SourceSpan inst_span(mnem_tok.span.start, operands.empty() ? mnem_tok.span.end : operands.back()->span.end);
    validate_instruction_operands(mnem_tok.text, operands, inst_span);

    return std::make_shared<InstructionStatement>(mnem_tok.text, std::move(operands), inst_span);
}

StatementPtr Parser::parse_statement() {
    // Skip empty lines and standalone semicolons
    while (match(TokenType::NEWLINE) || match(TokenType::SEMICOLON)) {}

    if (check(TokenType::END_OF_FILE)) {
        return nullptr;
    }

    // 1. Label definition (named or local numeric)
    if (check(TokenType::LOCAL_LABEL_DEF)) {
        Token tok = advance();
        return std::make_shared<LabelStatement>(tok.local_label_num, tok.span);
    }
    if (check(TokenType::IDENTIFIER) && peek(1).type == TokenType::COLON) {
        Token id_tok = advance();
        advance(); // consume ':'
        return std::make_shared<LabelStatement>(id_tok.text, SourceSpan(id_tok.span.start, current().span.end));
    }

    // 2. Directives
    if (current().type >= TokenType::DIR_TEXT && current().type <= TokenType::DIR_ENDR) {
        auto dir = parse_directive();
        // Consume statement end
        if (check(TokenType::NEWLINE) || check(TokenType::SEMICOLON)) {
            advance();
        }
        return dir;
    }

    // 3. Instruction
    if (check(TokenType::IDENTIFIER)) {
        auto inst = parse_instruction();
        if (check(TokenType::NEWLINE) || check(TokenType::SEMICOLON)) {
            advance();
        }
        return inst;
    }

    diag_.error(current().span, "unexpected token in assembly statement: '" + current().to_string() + "'");
    skip_to_statement_end();
    return nullptr;
}

std::vector<StatementPtr> Parser::parse_all() {
    std::vector<StatementPtr> statements;
    while (!check(TokenType::END_OF_FILE)) {
        auto stmt = parse_statement();
        if (stmt) {
            statements.push_back(stmt);
        }
    }
    return statements;
}

} // namespace an32asm
