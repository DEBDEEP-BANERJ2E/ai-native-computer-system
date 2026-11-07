#include "an32asm/expression.hpp"
#include "an32asm/symbol.hpp"
#include "an32asm/source_manager.hpp"
#include "an32asm/diagnostic.hpp"
#include <cassert>
#include <iostream>

void test_hi_lo_calculations() {
    // Tests for %hi and %lo boundary values
    int64_t v1 = 0x12345678;
    int64_t hi1 = an32asm::ExpressionEvaluator::calc_hi20(v1);
    int64_t lo1 = an32asm::ExpressionEvaluator::calc_lo12(v1);
    assert(hi1 == 0x12345);
    assert(lo1 == 0x678);

    // Negative sign boundary e.g. 0x00000800
    int64_t v2 = 0x00000800;
    int64_t hi2 = an32asm::ExpressionEvaluator::calc_hi20(v2);
    int64_t lo2 = an32asm::ExpressionEvaluator::calc_lo12(v2);
    assert(hi2 == 1);
    assert(lo2 == -2048);

    // 0xFFFFFFFF (-1)
    int64_t v3 = -1;
    int64_t hi3 = an32asm::ExpressionEvaluator::calc_hi20(v3);
    int64_t lo3 = an32asm::ExpressionEvaluator::calc_lo12(v3);
    assert(hi3 == 0);
    assert(lo3 == -1);

    // 0x7FFFFFFF
    int64_t v4 = 0x7FFFFFFF;
    int64_t hi4 = an32asm::ExpressionEvaluator::calc_hi20(v4);
    int64_t lo4 = an32asm::ExpressionEvaluator::calc_lo12(v4);
    int64_t hi_sext4 = ((hi4 & 0xFFFFF) ^ 0x80000) - 0x80000;
    assert((hi_sext4 << 12) + lo4 == v4);
}

void test_evaluator_ast() {
    an32asm::SourceManager sm;
    an32asm::DiagnosticEngine diag(sm);
    an32asm::ExpressionEvaluator eval(diag);
    an32asm::SymbolTable symtab;

    // Define symbols
    symtab.define_symbol("start", 0, 0x100, an32asm::SourceSpan());
    symtab.define_symbol("end", 0, 0x140, an32asm::SourceSpan());

    // Expression: end - start
    auto expr_start = std::make_shared<an32asm::SymbolExpr>("start", an32asm::SourceSpan());
    auto expr_end = std::make_shared<an32asm::SymbolExpr>("end", an32asm::SourceSpan());
    auto sub_expr = std::make_shared<an32asm::BinaryExpr>(an32asm::BinaryOp::SUB, expr_end, expr_start, an32asm::SourceSpan());

    auto res = eval.evaluate(sub_expr, &symtab, 0);
    assert(!diag.has_errors());
    assert(res.kind == an32asm::EvalKind::ABSOLUTE);
    assert(res.value == 0x40);
}

int main() {
    std::cout << "Running test_expression...\n";
    test_hi_lo_calculations();
    test_evaluator_ast();
    std::cout << "test_expression passed!\n";
    return 0;
}
