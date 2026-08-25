#include "an32asm/diagnostic.hpp"
#include "an32asm/source_manager.hpp"
#include "an32asm/assembler.hpp"
#include <cassert>
#include <iostream>

void test_diagnostic_formatting() {
    an32asm::SourceManager sm;
    an32asm::DiagnosticEngine diag(sm);

    uint32_t fid = sm.add_buffer("bad.s", "addi x99, zero, 0\n");
    an32asm::SourcePos p1{fid, 1, 6, 5};
    an32asm::SourcePos p2{fid, 1, 9, 8};
    diag.error(an32asm::SourceSpan(p1, p2), "unknown register 'x99'");

    assert(diag.has_errors());
    assert(diag.error_count() == 1);

    std::string formatted = diag.format(diag.get_diagnostics()[0], false);
    assert(formatted.find("bad.s:1:6: error: unknown register 'x99'") != std::string::npos);
    assert(formatted.find("^^^") != std::string::npos || formatted.find("^~~") != std::string::npos);
}

int main() {
    std::cout << "Running test_diagnostics...\n";
    test_diagnostic_formatting();
    std::cout << "test_diagnostics passed!\n";
    return 0;
}
