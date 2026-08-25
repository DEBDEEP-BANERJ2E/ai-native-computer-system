#pragma once

#include "source_location.hpp"
#include <cstdint>
#include <string>
#include <optional>

namespace an32asm {

enum class FixupKind {
    BRANCH,         // 13-bit PC-relative B-format
    JAL,            // 21-bit PC-relative J-format
    CALL,           // AUIPC + JALR pair (8-byte procedure call)
    HI20,           // LUI / AUIPC upper 20 bits
    LO12_I,         // ADDI / I-format lower 12 bits
    LO12_S,         // S-format store lower 12 bits
    PCREL_HI20,     // AUIPC site PC-relative upper 20 bits
    PCREL_LO12_I,   // I-format lower 12 bits referencing paired PCREL_HI20 anchor
    PCREL_LO12_S,   // S-format lower 12 bits referencing paired PCREL_HI20 anchor
    ABS32,          // 32-bit absolute data word (.word symbol)
    ADD32,          // 32-bit additive fixup (.word end - start)
    SUB32           // 32-bit subtractive fixup
};

struct Fixup {
    FixupKind kind;
    uint32_t section_id = 0;
    uint64_t offset = 0;                  // Byte offset within section where instruction/data resides
    uint32_t symbol_id = 0;
    std::string symbol_name;
    int64_t addend = 0;

    std::optional<uint32_t> group_id;     // Paired fixup group ID (e.g. for PCREL HI/LO pairs or CALL)
    std::optional<uint64_t> anchor_offset;// Byte offset of corresponding AUIPC for %pcrel_lo

    bool is_local_numeric = false;
    uint32_t local_label_num = 0;
    bool is_forward_ref = false;

    SourceSpan span;

    std::string to_string() const;
};

const char* fixup_kind_to_string(FixupKind kind) noexcept;

} // namespace an32asm
