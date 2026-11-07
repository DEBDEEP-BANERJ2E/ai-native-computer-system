#pragma once

#include "object.hpp"
#include "diagnostic.hpp"
#include <string>
#include <vector>
#include <iostream>

namespace an32asm {

class FlatImageFinalizer {
public:
    FlatImageFinalizer(AssemblerObject& obj, DiagnosticEngine& diag);

    // Finalizes and resolves fixups for the specified section (default ".text")
    bool finalize_section(const std::string& section_name = ".text", uint64_t base_address = 0);

    // Emits raw binary
    bool emit_binary(std::ostream& os, const std::string& section_name = ".text") const;

    // Emits Verilog/Chisel @00000000 32-bit hex words
    bool emit_hex(std::ostream& os, const std::string& section_name = ".text", uint64_t base_address = 0) const;

    // Returns machine instruction words as 32-bit integers
    std::vector<uint32_t> get_machine_words(const std::string& section_name = ".text") const;

private:
    AssemblerObject& obj_;
    DiagnosticEngine& diag_;

    bool apply_fixup(const Fixup& fixup, Section& sec, uint64_t base_address);
};

} // namespace an32asm
