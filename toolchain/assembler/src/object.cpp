#include "an32asm/object.hpp"
#include <iomanip>

namespace an32asm {

Section* AssemblerObject::find_section(const std::string& name) {
    for (auto& sec : sections) {
        if (sec->name == name) return sec.get();
    }
    return nullptr;
}

const Section* AssemblerObject::find_section(const std::string& name) const {
    for (const auto& sec : sections) {
        if (sec->name == name) return sec.get();
    }
    return nullptr;
}

Section* AssemblerObject::find_section_by_id(uint32_t id) {
    if (id < sections.size()) return sections[id].get();
    return nullptr;
}

const Section* AssemblerObject::find_section_by_id(uint32_t id) const {
    if (id < sections.size()) return sections[id].get();
    return nullptr;
}

void AssemblerObject::dump_sections(std::ostream& os) const {
    os << "=== Sections (" << sections.size() << ") ===\n";
    os << std::left << std::setw(6) << "ID"
       << std::setw(20) << "Name"
       << std::setw(12) << "Type"
       << std::setw(12) << "MemSize"
       << std::setw(12) << "FileSize"
       << std::setw(8)  << "Align" << "\n";
    os << std::string(70, '-') << "\n";

    for (const auto& sec : sections) {
        os << std::left << std::setw(6) << sec->id
           << std::setw(20) << sec->name
           << std::setw(12) << (sec->is_nobits() ? "NOBITS" : "PROGBITS")
           << "0x" << std::hex << std::setw(10) << sec->memory_size << std::dec
           << "0x" << std::hex << std::setw(10) << sec->data.size() << std::dec
           << std::setw(8) << sec->alignment << "\n";
    }
}

void AssemblerObject::dump_symbols(std::ostream& os) const {
    const auto& syms = symbol_table.get_symbols();
    os << "=== Symbols (" << syms.size() << ") ===\n";
    os << std::left << std::setw(6) << "ID"
       << std::setw(25) << "Name"
       << std::setw(12) << "Section"
       << std::setw(14) << "Value"
       << std::setw(10) << "Size"
       << std::setw(10) << "Binding"
       << std::setw(10) << "Type" << "\n";
    os << std::string(87, '-') << "\n";

    for (const auto& sym : syms) {
        std::string sec_str = sym.section_id.has_value() ? std::to_string(*sym.section_id) : "UND";
        const char* bind_str = (sym.binding == SymbolBinding::GLOBAL) ? "GLOBAL" :
                               (sym.binding == SymbolBinding::WEAK)   ? "WEAK"   : "LOCAL";
        const char* type_str = (sym.type == SymbolType::FUNC)   ? "FUNC"   :
                               (sym.type == SymbolType::OBJECT) ? "OBJECT" : "NOTYPE";

        os << std::left << std::setw(6) << sym.id
           << std::setw(25) << sym.name
           << std::setw(12) << sec_str
           << "0x" << std::hex << std::setw(12) << sym.value << std::dec
           << std::setw(10) << sym.size
           << std::setw(10) << bind_str
           << std::setw(10) << type_str << "\n";
    }
}

void AssemblerObject::dump_fixups(std::ostream& os) const {
    os << "=== Fixups (" << fixups.size() << ") ===\n";
    for (size_t i = 0; i < fixups.size(); ++i) {
        os << "[" << std::setw(3) << i << "] " << fixups[i].to_string() << "\n";
    }
}

void AssemblerObject::dump_summary(std::ostream& os) const {
    os << "=== AssemblerObject: " << source_filename << " ===\n";
    dump_sections(os);
    os << "\n";
    dump_symbols(os);
    os << "\n";
    dump_fixups(os);
}

void AssemblerObject::dump_ast(const std::vector<StatementPtr>& stmts, std::ostream& os) {
    os << "=== AST Statements (" << stmts.size() << ") ===\n";
    for (size_t i = 0; i < stmts.size(); ++i) {
        const auto& s = stmts[i];
        if (s->kind == StatementKind::LABEL) {
            auto l = std::static_pointer_cast<LabelStatement>(s);
            os << "[" << i << "] Label: " << l->name << ":\n";
        } else if (s->kind == StatementKind::DIRECTIVE) {
            auto d = std::static_pointer_cast<DirectiveStatement>(s);
            os << "[" << i << "] Directive: " << d->directive_name << "\n";
        } else if (s->kind == StatementKind::INSTRUCTION) {
            auto inst = std::static_pointer_cast<InstructionStatement>(s);
            os << "[" << i << "] Instruction: " << inst->mnemonic << " (" << inst->operands.size() << " operands)\n";
        }
    }
}

} // namespace an32asm
