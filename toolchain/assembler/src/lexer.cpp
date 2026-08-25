#include "an32asm/lexer.hpp"
#include <cctype>
#include <unordered_map>
#include <algorithm>

namespace an32asm {

Lexer::Lexer(const SourceManager& sm, uint32_t file_id, DiagnosticEngine& diag)
    : sm_(sm), file_id_(file_id), diag_(diag) {
    const auto* buf = sm_.get_buffer(file_id);
    if (buf) {
        content_ = buf->content;
    }
}

char Lexer::peek(size_t ahead) const {
    if (offset_ + ahead < content_.size()) {
        return content_[offset_ + ahead];
    }
    return '\0';
}

char Lexer::advance() {
    if (offset_ < content_.size()) {
        char c = content_[offset_++];
        if (c == '\n') {
            line_++;
            col_ = 1;
        } else {
            col_++;
        }
        return c;
    }
    return '\0';
}

bool Lexer::match(char expected) {
    if (peek() == expected) {
        advance();
        return true;
    }
    return false;
}

SourcePos Lexer::current_pos() const {
    return SourcePos{file_id_, line_, col_, offset_};
}

void Lexer::skip_whitespace_and_comments() {
    while (offset_ < content_.size()) {
        char c = peek();
        if (c == ' ' || c == '\t' || c == '\r') {
            advance();
        } else if (c == '#' || (c == '/' && peek(1) == '/')) {
            // Line comment: skip until newline or EOF
            while (offset_ < content_.size() && peek() != '\n') {
                advance();
            }
        } else if (c == '/' && peek(1) == '*') {
            // Block comment: skip until */
            SourcePos start_pos = current_pos();
            advance(); // '/'
            advance(); // '*'
            bool closed = false;
            while (offset_ < content_.size()) {
                if (peek() == '*' && peek(1) == '/') {
                    advance();
                    advance();
                    closed = true;
                    break;
                }
                advance();
            }
            if (!closed) {
                diag_.error(SourceSpan(start_pos, current_pos()), "unterminated block comment");
            }
        } else if (c == '\\' && peek(1) == '\n') {
            // Line continuation
            advance(); // '\'
            advance(); // '\n'
        } else {
            break;
        }
    }
}

Token Lexer::lex_number() {
    SourcePos start = current_pos();
    uint32_t start_off = offset_;

    int base = 10;
    if (peek() == '0' && (peek(1) == 'x' || peek(1) == 'X')) {
        base = 16;
        advance(); // '0'
        advance(); // 'x'
    } else if (peek() == '0' && (peek(1) == 'b' || peek(1) == 'B')) {
        base = 2;
        advance(); // '0'
        advance(); // 'b'
    }

    uint64_t val = 0;
    bool overflow = false;
    size_t digits_read = 0;

    auto is_digit_for_base = [base](char c) {
        if (base == 2) return c == '0' || c == '1';
        if (base == 10) return c >= '0' && c <= '9';
        if (base == 16) return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
        return false;
    };

    auto digit_val = [](char c) -> uint64_t {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return 0;
    };

    while (is_digit_for_base(peek())) {
        char c = advance();
        digits_read++;
        uint64_t d = digit_val(c);
        if (__builtin_mul_overflow(val, base, &val) || __builtin_add_overflow(val, d, &val)) {
            overflow = true;
        }
    }

    // Check for local numeric label definition (e.g. 1:) or reference (e.g. 1b, 1f)
    if (base == 10 && digits_read > 0) {
        if (peek() == ':') {
            advance(); // ':'
            Token tok;
            tok.type = TokenType::LOCAL_LABEL_DEF;
            tok.span = SourceSpan(start, current_pos());
            tok.text = std::string(content_.substr(start_off, offset_ - start_off));
            tok.local_label_num = static_cast<uint32_t>(val);
            return tok;
        } else if (peek() == 'b' || peek() == 'B') {
            advance(); // 'b'
            Token tok;
            tok.type = TokenType::LOCAL_LABEL_REF;
            tok.span = SourceSpan(start, current_pos());
            tok.text = std::string(content_.substr(start_off, offset_ - start_off));
            tok.local_label_num = static_cast<uint32_t>(val);
            tok.is_forward_ref = false;
            return tok;
        } else if (peek() == 'f' || peek() == 'F') {
            advance(); // 'f'
            Token tok;
            tok.type = TokenType::LOCAL_LABEL_REF;
            tok.span = SourceSpan(start, current_pos());
            tok.text = std::string(content_.substr(start_off, offset_ - start_off));
            tok.local_label_num = static_cast<uint32_t>(val);
            tok.is_forward_ref = true;
            return tok;
        }
    }

    if (digits_read == 0) {
        diag_.error(SourceSpan(start, current_pos()), "expected digits after number prefix");
    }
    if (overflow) {
        diag_.error(SourceSpan(start, current_pos()), "integer literal overflow");
    }

    Token tok;
    tok.type = TokenType::INTEGER;
    tok.span = SourceSpan(start, current_pos());
    tok.text = std::string(content_.substr(start_off, offset_ - start_off));
    tok.int_value = val;
    return tok;
}

Token Lexer::lex_string() {
    SourcePos start = current_pos();
    advance(); // opening quote '"'

    std::string s;
    bool closed = false;

    while (offset_ < content_.size()) {
        char c = advance();
        if (c == '"') {
            closed = true;
            break;
        }
        if (c == '\n') {
            diag_.error(SourceSpan(start, current_pos()), "newline in string literal");
            break;
        }
        if (c == '\\') {
            if (offset_ >= content_.size()) break;
            char esc = advance();
            switch (esc) {
                case 'n': s.push_back('\n'); break;
                case 't': s.push_back('\t'); break;
                case 'r': s.push_back('\r'); break;
                case '0': s.push_back('\0'); break;
                case '\\': s.push_back('\\'); break;
                case '"': s.push_back('"'); break;
                case '\'': s.push_back('\''); break;
                case 'x': {
                    // Hex escape \xHH
                    char h1 = advance();
                    char h2 = advance();
                    if (std::isxdigit(h1) && std::isxdigit(h2)) {
                        auto hex_d = [](char ch) {
                            return std::isdigit(ch) ? (ch - '0') : (std::tolower(ch) - 'a' + 10);
                        };
                        s.push_back(static_cast<char>((hex_d(h1) << 4) | hex_d(h2)));
                    } else {
                        diag_.error(SourceSpan(start, current_pos()), "invalid \\x escape in string literal");
                    }
                    break;
                }
                default:
                    s.push_back(esc);
                    break;
            }
        } else {
            s.push_back(c);
        }
    }

    if (!closed) {
        diag_.error(SourceSpan(start, current_pos()), "unterminated string literal");
    }

    Token tok;
    tok.type = TokenType::STRING;
    tok.span = SourceSpan(start, current_pos());
    tok.text = std::move(s);
    return tok;
}

Token Lexer::lex_char() {
    SourcePos start = current_pos();
    advance(); // opening quote '\''

    char val = '\0';
    if (offset_ < content_.size()) {
        char c = advance();
        if (c == '\\') {
            char esc = advance();
            switch (esc) {
                case 'n': val = '\n'; break;
                case 't': val = '\t'; break;
                case 'r': val = '\r'; break;
                case '0': val = '\0'; break;
                case '\\': val = '\\'; break;
                case '\'': val = '\''; break;
                default: val = esc; break;
            }
        } else {
            val = c;
        }
    }

    if (!match('\'')) {
        diag_.error(SourceSpan(start, current_pos()), "unterminated character literal");
    }

    Token tok;
    tok.type = TokenType::CHAR_LITERAL;
    tok.span = SourceSpan(start, current_pos());
    tok.text = std::string(1, val);
    tok.int_value = static_cast<uint8_t>(val);
    return tok;
}

Token Lexer::lex_directive() {
    SourcePos start = current_pos();
    advance(); // '.'

    uint32_t start_off = offset_;
    while (std::isalnum(peek()) || peek() == '_' || peek() == '.') {
        advance();
    }

    std::string name(content_.substr(start_off, offset_ - start_off));
    std::string full_name = "." + name;

    static const std::unordered_map<std::string, TokenType> dir_map = {
        {".text",     TokenType::DIR_TEXT},
        {".data",     TokenType::DIR_DATA},
        {".rodata",   TokenType::DIR_RODATA},
        {".bss",      TokenType::DIR_BSS},
        {".section",  TokenType::DIR_SECTION},
        {".globl",    TokenType::DIR_GLOBL},
        {".global",   TokenType::DIR_GLOBL},
        {".local",    TokenType::DIR_LOCAL},
        {".weak",     TokenType::DIR_WEAK},
        {".type",     TokenType::DIR_TYPE},
        {".size",     TokenType::DIR_SIZE},
        {".align",    TokenType::DIR_ALIGN},
        {".balign",   TokenType::DIR_BALIGN},
        {".p2align",  TokenType::DIR_P2ALIGN},
        {".byte",     TokenType::DIR_BYTE},
        {".2byte",    TokenType::DIR_2BYTE},
        {".half",     TokenType::DIR_2BYTE},
        {".4byte",    TokenType::DIR_4BYTE},
        {".word",     TokenType::DIR_4BYTE},
        {".zero",     TokenType::DIR_ZERO},
        {".space",    TokenType::DIR_SPACE},
        {".skip",     TokenType::DIR_SPACE},
        {".ascii",    TokenType::DIR_ASCII},
        {".asciz",    TokenType::DIR_ASCIZ},
        {".string",   TokenType::DIR_ASCIZ},
        {".equ",      TokenType::DIR_EQU},
        {".set",      TokenType::DIR_SET},
        {".file",     TokenType::DIR_FILE},
        {".include",  TokenType::DIR_INCLUDE},
        {".macro",    TokenType::DIR_MACRO},
        {".endm",     TokenType::DIR_ENDM},
        {".if",       TokenType::DIR_IF},
        {".elseif",   TokenType::DIR_ELSEIF},
        {".else",     TokenType::DIR_ELSE},
        {".endif",    TokenType::DIR_ENDIF},
        {".ifdef",    TokenType::DIR_IFDEF},
        {".ifndef",   TokenType::DIR_IFNDEF},
        {".rept",     TokenType::DIR_REPT},
        {".endr",     TokenType::DIR_ENDR}
    };

    auto it = dir_map.find(full_name);
    Token tok;
    tok.span = SourceSpan(start, current_pos());
    tok.text = full_name;
    if (it != dir_map.end()) {
        tok.type = it->second;
    } else {
        // If it's just a dot '.', emit DOT
        if (name.empty()) {
            tok.type = TokenType::DOT;
        } else {
            // General identifier starting with dot (e.g. .Llocal_label)
            tok.type = TokenType::IDENTIFIER;
        }
    }
    return tok;
}

Token Lexer::lex_percent_modifier() {
    SourcePos start = current_pos();
    advance(); // '%'

    uint32_t start_off = offset_;
    while (std::isalnum(peek()) || peek() == '_') {
        advance();
    }

    std::string mod(content_.substr(start_off, offset_ - start_off));
    std::string full_mod = "%" + mod;

    Token tok;
    tok.span = SourceSpan(start, current_pos());
    tok.text = full_mod;

    if (full_mod == "%hi") {
        tok.type = TokenType::MOD_HI;
    } else if (full_mod == "%lo") {
        tok.type = TokenType::MOD_LO;
    } else if (full_mod == "%pcrel_hi") {
        tok.type = TokenType::MOD_PCREL_HI;
    } else if (full_mod == "%pcrel_lo") {
        tok.type = TokenType::MOD_PCREL_LO;
    } else {
        // Standalone percent operator
        offset_ = start_off; // rewind identifier part
        col_ = start.column + 1;
        tok.type = TokenType::PERCENT;
        tok.text = "%";
        tok.span = SourceSpan(start, current_pos());
    }
    return tok;
}

Token Lexer::lex_macro_escape() {
    SourcePos start = current_pos();
    advance(); // '\\'

    if (peek() == '@') {
        advance(); // '@'
        Token tok;
        tok.type = TokenType::MACRO_UNIQUE_ID;
        tok.span = SourceSpan(start, current_pos());
        tok.text = "\\@";
        return tok;
    }

    uint32_t start_off = offset_;
    while (std::isalnum(peek()) || peek() == '_') {
        advance();
    }

    Token tok;
    tok.type = TokenType::MACRO_PARAM_REF;
    tok.span = SourceSpan(start, current_pos());
    tok.text = std::string(content_.substr(start_off, offset_ - start_off));
    return tok;
}

Token Lexer::lex_identifier_or_keyword() {
    SourcePos start = current_pos();
    uint32_t start_off = offset_;

    while (std::isalnum(peek()) || peek() == '_' || peek() == '$') {
        advance();
    }

    std::string id(content_.substr(start_off, offset_ - start_off));

    Token tok;
    tok.span = SourceSpan(start, current_pos());
    tok.text = id;

    // Check if it's a register
    auto xreg = parse_xreg_name(id);
    if (xreg.has_value()) {
        tok.type = TokenType::REG_X;
        tok.reg_index = *xreg;
        return tok;
    }

    auto capreg = parse_capreg_name(id);
    if (capreg.has_value()) {
        tok.type = TokenType::REG_CAP;
        tok.reg_index = *capreg;
        return tok;
    }

    tok.type = TokenType::IDENTIFIER;
    return tok;
}

std::vector<Token> Lexer::tokenize() {
    std::vector<Token> tokens;

    while (offset_ < content_.size()) {
        skip_whitespace_and_comments();
        if (offset_ >= content_.size()) break;

        char c = peek();
        SourcePos start = current_pos();

        if (c == '\n') {
            advance();
            Token tok;
            tok.type = TokenType::NEWLINE;
            tok.span = SourceSpan(start, current_pos());
            tok.text = "\n";
            tokens.push_back(tok);
            continue;
        }

        if (c == ';') {
            advance();
            Token tok;
            tok.type = TokenType::SEMICOLON;
            tok.span = SourceSpan(start, current_pos());
            tok.text = ";";
            tokens.push_back(tok);
            continue;
        }

        if (c == ':') {
            advance();
            Token tok;
            tok.type = TokenType::COLON;
            tok.span = SourceSpan(start, current_pos());
            tok.text = ":";
            tokens.push_back(tok);
            continue;
        }

        if (c == ',') {
            advance();
            Token tok;
            tok.type = TokenType::COMMA;
            tok.span = SourceSpan(start, current_pos());
            tok.text = ",";
            tokens.push_back(tok);
            continue;
        }

        if (c == '(') {
            advance();
            Token tok;
            tok.type = TokenType::LPAREN;
            tok.span = SourceSpan(start, current_pos());
            tok.text = "(";
            tokens.push_back(tok);
            continue;
        }

        if (c == ')') {
            advance();
            Token tok;
            tok.type = TokenType::RPAREN;
            tok.span = SourceSpan(start, current_pos());
            tok.text = ")";
            tokens.push_back(tok);
            continue;
        }

        if (c == '+') {
            advance();
            Token tok;
            tok.type = TokenType::PLUS;
            tok.span = SourceSpan(start, current_pos());
            tok.text = "+";
            tokens.push_back(tok);
            continue;
        }

        if (c == '-') {
            advance();
            Token tok;
            tok.type = TokenType::MINUS;
            tok.span = SourceSpan(start, current_pos());
            tok.text = "-";
            tokens.push_back(tok);
            continue;
        }

        if (c == '*') {
            advance();
            Token tok;
            tok.type = TokenType::STAR;
            tok.span = SourceSpan(start, current_pos());
            tok.text = "*";
            tokens.push_back(tok);
            continue;
        }

        if (c == '/') {
            advance();
            Token tok;
            tok.type = TokenType::SLASH;
            tok.span = SourceSpan(start, current_pos());
            tok.text = "/";
            tokens.push_back(tok);
            continue;
        }

        if (c == '<') {
            if (peek(1) == '<') {
                advance(); advance();
                Token tok;
                tok.type = TokenType::LSHIFT;
                tok.span = SourceSpan(start, current_pos());
                tok.text = "<<";
                tokens.push_back(tok);
                continue;
            }
            if (peek(1) == '=') {
                advance(); advance();
                Token tok;
                tok.type = TokenType::LESS_EQUAL;
                tok.span = SourceSpan(start, current_pos());
                tok.text = "<=";
                tokens.push_back(tok);
                continue;
            }
            advance();
            Token tok;
            tok.type = TokenType::LESS;
            tok.span = SourceSpan(start, current_pos());
            tok.text = "<";
            tokens.push_back(tok);
            continue;
        }

        if (c == '>') {
            if (peek(1) == '>') {
                advance(); advance();
                Token tok;
                tok.type = TokenType::RSHIFT;
                tok.span = SourceSpan(start, current_pos());
                tok.text = ">>";
                tokens.push_back(tok);
                continue;
            }
            if (peek(1) == '=') {
                advance(); advance();
                Token tok;
                tok.type = TokenType::GREATER_EQUAL;
                tok.span = SourceSpan(start, current_pos());
                tok.text = ">=";
                tokens.push_back(tok);
                continue;
            }
            advance();
            Token tok;
            tok.type = TokenType::GREATER;
            tok.span = SourceSpan(start, current_pos());
            tok.text = ">";
            tokens.push_back(tok);
            continue;
        }

        if (c == '=') {
            if (peek(1) == '=') {
                advance(); advance();
                Token tok;
                tok.type = TokenType::EQUAL_EQUAL;
                tok.span = SourceSpan(start, current_pos());
                tok.text = "==";
                tokens.push_back(tok);
                continue;
            }
            advance();
            Token tok;
            tok.type = TokenType::EQUAL;
            tok.span = SourceSpan(start, current_pos());
            tok.text = "=";
            tokens.push_back(tok);
            continue;
        }

        if (c == '!') {
            if (peek(1) == '=') {
                advance(); advance();
                Token tok;
                tok.type = TokenType::EXCLAIM_EQUAL;
                tok.span = SourceSpan(start, current_pos());
                tok.text = "!=";
                tokens.push_back(tok);
                continue;
            }
            advance();
            Token tok;
            tok.type = TokenType::EXCLAIM;
            tok.span = SourceSpan(start, current_pos());
            tok.text = "!";
            tokens.push_back(tok);
            continue;
        }

        if (c == '%') {
            tokens.push_back(lex_percent_modifier());
            continue;
        }

        if (c == '.') {
            tokens.push_back(lex_directive());
            continue;
        }

        if (c == '\\') {
            tokens.push_back(lex_macro_escape());
            continue;
        }

        if (c == '"') {
            tokens.push_back(lex_string());
            continue;
        }

        if (c == '\'') {
            tokens.push_back(lex_char());
            continue;
        }

        if (std::isdigit(c)) {
            tokens.push_back(lex_number());
            continue;
        }

        if (std::isalpha(c) || c == '_' || c == '$') {
            tokens.push_back(lex_identifier_or_keyword());
            continue;
        }

        // Unknown character
        advance();
        diag_.error(SourceSpan(start, current_pos()), std::string("unexpected character '") + c + "'");
    }

    Token eof;
    eof.type = TokenType::END_OF_FILE;
    eof.span = SourceSpan(current_pos(), current_pos());
    eof.text = "";
    tokens.push_back(eof);

    return tokens;
}

} // namespace an32asm
