#pragma once

#include "source_manager.hpp"
#include "diagnostic.hpp"
#include "source_expander.hpp"
#include "parser.hpp"
#include "pseudo.hpp"
#include "expression.hpp"
#include "symbol.hpp"
#include "section.hpp"
#include "object.hpp"
#include "flat_finalizer.hpp"
#include <memory>
#include <vector>
#include <string>

namespace an32asm {

struct AssemblerOptions {
    std::vector<std::string> include_paths;
    bool debug_info = false;
    bool verbose = false;
};

class Assembler {
public:
    Assembler(SourceManager& sm, DiagnosticEngine& diag, AssemblerOptions options = {});

    std::unique_ptr<AssemblerObject> assemble_file(uint32_t file_id);
    std::unique_ptr<AssemblerObject> assemble_string(const std::string& source_code, const std::string& filename = "<input>");

private:
    SourceManager& sm_;
    DiagnosticEngine& diag_;
    AssemblerOptions options_;

    void process_statements(const std::vector<StatementPtr>& stmts, SectionTable& sec_table,
                            SymbolTable& symtab, std::vector<Fixup>& fixups);
    void perform_layout(SectionTable& sec_table, SymbolTable& symtab);
    void encode_instructions(SectionTable& sec_table, SymbolTable& symtab, std::vector<Fixup>& fixups);
};

} // namespace an32asm
