#pragma once

#include <cstdint>
#include <string>
#include <string_view>
#include <optional>
#include <memory>

namespace an32asm {

/// Represents a single character coordinate within a source buffer
struct SourcePos {
    uint32_t file_id = 0;
    uint32_t line = 1;      // 1-indexed
    uint32_t column = 1;    // 1-indexed
    uint32_t offset = 0;    // 0-indexed byte offset in buffer

    bool is_valid() const noexcept { return line > 0 && column > 0; }
};

/// Represents an expansion origin (e.g. from macro or include)
struct ExpansionContext {
    std::string macro_or_context_name;
    SourcePos call_site;
    std::shared_ptr<ExpansionContext> parent;
};

/// Represents a source span [start, end)
struct SourceSpan {
    SourcePos start;
    SourcePos end;
    std::shared_ptr<ExpansionContext> expansion;

    SourceSpan() = default;
    SourceSpan(SourcePos s, SourcePos e, std::shared_ptr<ExpansionContext> exp = nullptr)
        : start(s), end(e), expansion(std::move(exp)) {}

    static SourceSpan single_pos(SourcePos p, std::shared_ptr<ExpansionContext> exp = nullptr) {
        SourcePos end_pos = p;
        end_pos.column += 1;
        end_pos.offset += 1;
        return SourceSpan(p, end_pos, std::move(exp));
    }
};

} // namespace an32asm
