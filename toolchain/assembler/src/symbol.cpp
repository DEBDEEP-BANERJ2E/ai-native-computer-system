#include "an32asm/symbol.hpp"
#include <algorithm>

namespace an32asm {

void LocalLabelResolver::add_def(uint32_t number, uint32_t section_id, uint64_t offset, const SourcePos& pos) {
    uint32_t occ = static_cast<uint32_t>(defs_.size());
    defs_.push_back(LocalLabelDef{number, section_id, offset, occ, pos});
}

std::optional<uint64_t> LocalLabelResolver::resolve(uint32_t number, bool is_forward, uint32_t current_section, uint64_t current_offset) const {
    if (is_forward) {
        // Find first definition in current_section with offset >= current_offset (or file order after current)
        for (const auto& def : defs_) {
            if (def.number == number && def.section_id == current_section && def.offset >= current_offset) {
                return def.offset;
            }
        }
    } else {
        // Find most recent definition before current_offset
        for (auto it = defs_.rbegin(); it != defs_.rend(); ++it) {
            if (it->number == number && it->section_id == current_section && it->offset <= current_offset) {
                return it->offset;
            }
        }
    }
    return std::nullopt;
}

SymbolTable::SymbolTable() = default;

Symbol* SymbolTable::define_symbol(std::string name, std::optional<uint32_t> section_id, uint64_t value, SourceSpan span) {
    auto* sym = get_or_create(name);
    sym->section_id = section_id;
    sym->value = value;
    sym->is_defined = true;
    sym->def_span = span;
    return sym;
}

Symbol* SymbolTable::get_or_create(const std::string& name) {
    auto it = name_to_id_.find(name);
    if (it != name_to_id_.end()) {
        return &symbols_[it->second];
    }

    uint32_t id = static_cast<uint32_t>(symbols_.size());
    Symbol sym;
    sym.name = name;
    sym.id = id;
    symbols_.push_back(std::move(sym));
    name_to_id_[name] = id;
    return &symbols_.back();
}

Symbol* SymbolTable::find(const std::string& name) {
    auto it = name_to_id_.find(name);
    if (it != name_to_id_.end()) {
        return &symbols_[it->second];
    }
    return nullptr;
}

const Symbol* SymbolTable::find(const std::string& name) const {
    auto it = name_to_id_.find(name);
    if (it != name_to_id_.end()) {
        return &symbols_[it->second];
    }
    return nullptr;
}

void SymbolTable::clear() {
    symbols_.clear();
    name_to_id_.clear();
    local_labels_.clear();
}

} // namespace an32asm
