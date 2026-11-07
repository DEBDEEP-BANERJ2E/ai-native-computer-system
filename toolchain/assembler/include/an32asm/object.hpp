#pragma once

#include "section.hpp"
#include "symbol.hpp"
#include "fixup.hpp"
#include "ast.hpp"
#include <vector>
#include <string>
#include <iostream>

namespace an32asm {

class AssemblerObject {
public:
    std::string source_filename;
    std::vector<std::unique_ptr<Section>> sections;
    SymbolTable symbol_table;
    std::vector<Fixup> fixups;

    Section* find_section(const std::string& name);
    const Section* find_section(const std::string& name) const;
    Section* find_section_by_id(uint32_t id);
    const Section* find_section_by_id(uint32_t id) const;

    void dump_summary(std::ostream& os = std::cout) const;
    void dump_symbols(std::ostream& os = std::cout) const;
    void dump_fixups(std::ostream& os = std::cout) const;
    void dump_sections(std::ostream& os = std::cout) const;
    static void dump_ast(const std::vector<StatementPtr>& stmts, std::ostream& os = std::cout);
};

} // namespace an32asm
