#pragma once

#include "source_location.hpp"
#include "source_manager.hpp"
#include <string>
#include <vector>
#include <iostream>

namespace an32asm {

enum class DiagnosticSeverity {
    NOTE,
    WARNING,
    ERROR,
    FATAL
};

struct DiagnosticNote {
    SourceSpan span;
    std::string message;
};

struct Diagnostic {
    DiagnosticSeverity severity;
    SourceSpan span;
    std::string message;
    std::vector<DiagnosticNote> notes;
};

class DiagnosticEngine {
public:
    explicit DiagnosticEngine(const SourceManager& sm);

    void report(DiagnosticSeverity severity, SourceSpan span, std::string message);
    void report_with_notes(DiagnosticSeverity severity, SourceSpan span, std::string message, std::vector<DiagnosticNote> notes);

    void error(SourceSpan span, std::string message);
    void warning(SourceSpan span, std::string message);
    void note(SourceSpan span, std::string message);

    bool has_errors() const noexcept { return error_count_ > 0; }
    size_t error_count() const noexcept { return error_count_; }
    size_t warning_count() const noexcept { return warning_count_; }

    const std::vector<Diagnostic>& get_diagnostics() const noexcept { return diagnostics_; }
    void clear();

    void emit_all(std::ostream& os = std::cerr, bool use_color = true) const;
    std::string format(const Diagnostic& diag, bool use_color = false) const;

private:
    const SourceManager& sm_;
    std::vector<Diagnostic> diagnostics_;
    size_t error_count_ = 0;
    size_t warning_count_ = 0;

    void render_span(std::ostream& os, const SourceSpan& span, DiagnosticSeverity sev, const std::string& msg, bool use_color) const;
};

} // namespace an32asm
