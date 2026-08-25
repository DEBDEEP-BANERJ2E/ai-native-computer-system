#include "an32asm/token.hpp"
#include <unordered_map>
#include <string>
#include <algorithm>

namespace an32asm {

namespace {
std::string to_lower_str(std::string_view sv) {
    std::string s(sv);
    std::transform(s.begin(), s.end(), s.begin(), [](unsigned char c) { return std::tolower(c); });
    return s;
}
}

std::optional<uint32_t> parse_xreg_name(std::string_view name) {
    std::string lower = to_lower_str(name);

    // Direct numeric forms x0..x31
    if (lower.size() >= 2 && lower[0] == 'x') {
        try {
            size_t pos = 0;
            unsigned long val = std::stoul(lower.substr(1), &pos);
            if (pos == lower.size() - 1 && val <= 31) {
                return static_cast<uint32_t>(val);
            }
        } catch (...) {}
    }

    static const std::unordered_map<std::string, uint32_t> x_aliases = {
        {"zero", 0},  {"ra", 1},   {"sp", 2},   {"gp", 3},   {"tp", 4},
        {"t0", 5},    {"t1", 6},   {"t2", 7},   {"s0", 8},   {"fp", 8},
        {"s1", 9},    {"a0", 10},  {"a1", 11},  {"a2", 12},  {"a3", 13},
        {"a4", 14},   {"a5", 15},  {"a6", 16},  {"a7", 17},  {"s2", 18},
        {"s3", 19},   {"s4", 20},  {"s5", 21},  {"s6", 22},  {"s7", 23},
        {"s8", 24},   {"s9", 25},  {"s10", 26}, {"s11", 27}, {"t3", 28},
        {"t4", 29},   {"t5", 30},  {"t6", 31}
    };

    auto it = x_aliases.find(lower);
    if (it != x_aliases.end()) {
        return it->second;
    }
    return std::nullopt;
}

std::optional<uint32_t> parse_capreg_name(std::string_view name) {
    std::string lower = to_lower_str(name);

    // Direct numeric forms c0..c7
    if (lower.size() == 2 && lower[0] == 'c' && lower[1] >= '0' && lower[1] <= '7') {
        return static_cast<uint32_t>(lower[1] - '0');
    }

    // Frozen AN32-Bare-v1 Capability ABI Aliases
    static const std::unordered_map<std::string, uint32_t> cap_aliases = {
        {"cnull", 0}, // Hardware Root NULL (tag=0)
        {"cram",  1}, // Hardware Root RAM (0x00000000..0x00000FFF RW)
        {"cmmio", 2}, // Hardware Root MMIO (0x80000000..0x8000FFFF RW)
        {"ca0",   3}, // Process Argument / Return Capability
        {"ca1",   4}, // Process Argument Capability
        {"ct0",   5}, // Process Temporary Capability
        {"cs0",   6}, // Process Saved Capability 0
        {"cs1",   7}  // Process Saved Capability 1
    };

    auto it = cap_aliases.find(lower);
    if (it != cap_aliases.end()) {
        return it->second;
    }
    return std::nullopt;
}

std::string Token::to_string() const {
    switch (type) {
        case TokenType::END_OF_FILE:    return "<EOF>";
        case TokenType::NEWLINE:        return "<NEWLINE>";
        case TokenType::SEMICOLON:      return ";";
        case TokenType::COLON:          return ":";
        case TokenType::COMMA:          return ",";
        case TokenType::LPAREN:         return "(";
        case TokenType::RPAREN:         return ")";
        case TokenType::PLUS:           return "+";
        case TokenType::MINUS:          return "-";
        case TokenType::STAR:           return "*";
        case TokenType::SLASH:          return "/";
        case TokenType::PERCENT:        return "%";
        case TokenType::LSHIFT:         return "<<";
        case TokenType::RSHIFT:         return ">>";
        case TokenType::AMPERSAND:      return "&";
        case TokenType::PIPE:           return "|";
        case TokenType::CARET:          return "^";
        case TokenType::TILDE:          return "~";
        case TokenType::EXCLAIM:        return "!";
        case TokenType::EQUAL:          return "=";
        case TokenType::EQUAL_EQUAL:    return "==";
        case TokenType::EXCLAIM_EQUAL:  return "!=";
        case TokenType::LESS:           return "<";
        case TokenType::LESS_EQUAL:     return "<=";
        case TokenType::GREATER:        return ">";
        case TokenType::GREATER_EQUAL:  return ">=";
        case TokenType::DOT:            return ".";
        case TokenType::INTEGER:        return "INT(" + std::to_string(int_value) + ")";
        case TokenType::STRING:         return "STR(\"" + text + "\")";
        case TokenType::CHAR_LITERAL:   return "CHAR('" + text + "')";
        case TokenType::IDENTIFIER:     return "ID(" + text + ")";
        case TokenType::LOCAL_LABEL_DEF:return "LOCAL_DEF(" + std::to_string(local_label_num) + ":)";
        case TokenType::LOCAL_LABEL_REF:return "LOCAL_REF(" + std::to_string(local_label_num) + (is_forward_ref ? "f" : "b") + ")";
        case TokenType::REG_X:          return "REG_X(x" + std::to_string(reg_index) + ")";
        case TokenType::REG_CAP:        return "REG_CAP(c" + std::to_string(reg_index) + ")";
        case TokenType::MOD_HI:         return "%hi";
        case TokenType::MOD_LO:         return "%lo";
        case TokenType::MOD_PCREL_HI:   return "%pcrel_hi";
        case TokenType::MOD_PCREL_LO:   return "%pcrel_lo";
        case TokenType::MACRO_UNIQUE_ID:return "\\@";
        case TokenType::MACRO_PARAM_REF:return "MACRO_PARAM(" + text + ")";
        default:                        return text.empty() ? "<TOKEN>" : text;
    }
}

} // namespace an32asm
