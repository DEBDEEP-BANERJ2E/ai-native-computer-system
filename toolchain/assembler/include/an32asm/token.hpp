#pragma once

#include "source_location.hpp"
#include <string>
#include <string_view>
#include <cstdint>
#include <optional>

namespace an32asm {

enum class TokenType {
    END_OF_FILE,
    NEWLINE,
    SEMICOLON,

    // Punctuation & Operators
    COLON,              // :
    COMMA,              // ,
    LPAREN,             // (
    RPAREN,             // )
    PLUS,               // +
    MINUS,              // -
    STAR,               // *
    SLASH,              // /
    PERCENT,            // %
    LSHIFT,             // <<
    RSHIFT,             // >>
    AMPERSAND,          // &
    PIPE,               // |
    CARET,              // ^
    TILDE,              // ~
    EXCLAIM,            // !
    EQUAL,              // =
    EQUAL_EQUAL,        // ==
    EXCLAIM_EQUAL,      // !=
    LESS,               // <
    LESS_EQUAL,         // <=
    GREATER,            // >
    GREATER_EQUAL,      // >=
    DOT,                // . (when standalone or current-pc)

    // Literals
    INTEGER,            // Unsigned 64-bit parsed value
    STRING,             // Parsed string content without quotes
    CHAR_LITERAL,       // Character value

    // Identifiers & Symbols
    IDENTIFIER,         // Mnemonics, label names, symbol names
    LOCAL_LABEL_DEF,    // e.g. 1:, 2:
    LOCAL_LABEL_REF,    // e.g. 1b, 1f

    // Directives
    DIR_TEXT,           // .text
    DIR_DATA,           // .data
    DIR_RODATA,         // .rodata
    DIR_BSS,            // .bss
    DIR_SECTION,        // .section
    DIR_GLOBL,          // .globl / .global
    DIR_LOCAL,          // .local
    DIR_WEAK,           // .weak
    DIR_TYPE,           // .type
    DIR_SIZE,           // .size
    DIR_ALIGN,          // .align
    DIR_BALIGN,         // .balign
    DIR_P2ALIGN,        // .p2align
    DIR_BYTE,           // .byte
    DIR_2BYTE,          // .2byte / .half
    DIR_4BYTE,          // .4byte / .word
    DIR_ZERO,           // .zero
    DIR_SPACE,          // .space / .skip
    DIR_ASCII,          // .ascii
    DIR_ASCIZ,          // .asciz / .string
    DIR_EQU,            // .equ
    DIR_SET,            // .set
    DIR_FILE,           // .file
    DIR_INCLUDE,        // .include
    DIR_MACRO,          // .macro
    DIR_ENDM,           // .endm
    DIR_IF,             // .if
    DIR_ELSEIF,         // .elseif
    DIR_ELSE,           // .else
    DIR_ENDIF,          // .endif
    DIR_IFDEF,          // .ifdef
    DIR_IFNDEF,         // .ifndef
    DIR_REPT,           // .rept
    DIR_ENDR,           // .endr

    // Relocation Modifiers
    MOD_HI,             // %hi
    MOD_LO,             // %lo
    MOD_PCREL_HI,       // %pcrel_hi
    MOD_PCREL_LO,       // %pcrel_lo

    // Register Tokens (Typed)
    REG_X,              // x0..x31, zero, ra, sp, etc.
    REG_CAP,            // c0..c7, cnull, cram, cmmio, ca0, ca1, ct0, cs0, cs1

    // Macro Special
    MACRO_UNIQUE_ID,    // \@
    MACRO_PARAM_REF     // \param or $param
};

struct Token {
    TokenType type = TokenType::END_OF_FILE;
    SourceSpan span;
    std::string text;
    uint64_t int_value = 0;
    uint32_t reg_index = 0;     // For REG_X (0..31) and REG_CAP (0..7)
    uint32_t local_label_num = 0; // For LOCAL_LABEL_DEF / LOCAL_LABEL_REF (e.g. 1)
    bool is_forward_ref = false;  // For LOCAL_LABEL_REF (true for 'f', false for 'b')

    std::string to_string() const;
};

// Register Name Resolution Functions
std::optional<uint32_t> parse_xreg_name(std::string_view name);
std::optional<uint32_t> parse_capreg_name(std::string_view name);

} // namespace an32asm
