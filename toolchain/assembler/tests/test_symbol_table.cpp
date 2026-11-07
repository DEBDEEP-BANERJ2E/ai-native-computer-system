#include "an32asm/symbol.hpp"
#include <cassert>
#include <iostream>

void test_local_labels() {
    an32asm::LocalLabelResolver res;

    // Define 1: at offset 0x10, 1: at offset 0x30, 2: at offset 0x20
    res.add_def(1, 0, 0x10, an32asm::SourcePos());
    res.add_def(2, 0, 0x20, an32asm::SourcePos());
    res.add_def(1, 0, 0x30, an32asm::SourcePos());

    // At offset 0x18, 1b should be 0x10, 1f should be 0x30, 2f should be 0x20
    auto b1 = res.resolve(1, false, 0, 0x18);
    auto f1 = res.resolve(1, true, 0, 0x18);
    auto f2 = res.resolve(2, true, 0, 0x18);

    assert(b1.has_value() && *b1 == 0x10);
    assert(f1.has_value() && *f1 == 0x30);
    assert(f2.has_value() && *f2 == 0x20);

    // At offset 0x38, 1b should be 0x30
    auto b1_later = res.resolve(1, false, 0, 0x38);
    assert(b1_later.has_value() && *b1_later == 0x30);
}

void test_symbols() {
    an32asm::SymbolTable symtab;
    auto* s = symtab.define_symbol("main", 0, 0x0, an32asm::SourceSpan());
    s->binding = an32asm::SymbolBinding::GLOBAL;
    s->type = an32asm::SymbolType::FUNC;

    assert(symtab.find("main") == s);
    assert(s->is_defined);
    assert(s->binding == an32asm::SymbolBinding::GLOBAL);
    assert(s->type == an32asm::SymbolType::FUNC);
}

int main() {
    std::cout << "Running test_symbol_table...\n";
    test_local_labels();
    test_symbols();
    std::cout << "test_symbol_table passed!\n";
    return 0;
}
