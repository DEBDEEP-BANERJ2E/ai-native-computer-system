#pragma once

#include "source_location.hpp"
#include <string>
#include <vector>
#include <unordered_map>
#include <optional>
#include <cstdint>

namespace an32asm {

enum class SymbolBinding {
    LOCAL,
    GLOBAL,
    WEAK
};

enum class SymbolType {
    NOTYPE,
    OBJECT,
    FUNC
};

struct Symbol {
    std::string name;
    uint32_t id = 0;
    std::optional<uint32_t> section_id;
    uint64_t value = 0;                  // Offset within section or absolute value
    uint64_t size = 0;
    SymbolBinding binding = SymbolBinding::LOCAL;
    SymbolType type = SymbolType::NOTYPE;
    bool is_defined = false;
    bool is_absolute = false;            // True if defined via .equ/.set
    SourceSpan def_span;
};

struct LocalLabelDef {
    uint32_t number;
    uint32_t section_id;
    uint64_t offset;
    uint32_t occurrence_index;
    SourcePos pos;
};

class LocalLabelResolver {
public:
    void add_def(uint32_t number, uint32_t section_id, uint64_t offset, const SourcePos& pos);

    // Resolves 1b (backward) or 1f (forward) relative to current section and offset
    std::optional<uint64_t> resolve(uint32_t number, bool is_forward, uint32_t current_section, uint64_t current_offset) const;

    const std::vector<LocalLabelDef>& get_defs() const noexcept { return defs_; }
    void clear() { defs_.clear(); }

private:
    std::vector<LocalLabelDef> defs_;
};

class SymbolTable {
public:
    SymbolTable();

    Symbol* define_symbol(std::string name, std::optional<uint32_t> section_id, uint64_t value, SourceSpan span);
    Symbol* get_or_create(const std::string& name);

    Symbol* find(const std::string& name);
    const Symbol* find(const std::string& name) const;

    const std::vector<Symbol>& get_symbols() const noexcept { return symbols_; }
    LocalLabelResolver& local_labels() noexcept { return local_labels_; }
    const LocalLabelResolver& local_labels() const noexcept { return local_labels_; }

    void clear();

private:
    std::vector<Symbol> symbols_;
    std::unordered_map<std::string, uint32_t> name_to_id_;
    LocalLabelResolver local_labels_;
};

} // namespace an32asm
