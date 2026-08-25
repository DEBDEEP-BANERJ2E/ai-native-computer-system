#include "an32asm/diagnostic.hpp"
#include <sstream>
#include <iomanip>

namespace an32asm {

namespace {
const char* severity_string(DiagnosticSeverity sev) {
    switch (sev) {
        case DiagnosticSeverity::NOTE:    return "note";
        case DiagnosticSeverity::WARNING: return "warning";
        case DiagnosticSeverity::ERROR:   return "error";
        case DiagnosticSeverity::FATAL:   return "fatal error";
    }
    return "error";
}

const char* severity_color(DiagnosticSeverity sev) {
    switch (sev) {
        case DiagnosticSeverity::NOTE:    return "\033[1;36m"; // Cyan
        case DiagnosticSeverity::WARNING: return "\033[1;35m"; // Magenta
        case DiagnosticSeverity::ERROR:   return "\033[1;31m"; // Bold Red
        case DiagnosticSeverity::FATAL:   return "\033[1;31m"; // Bold Red
    }
    return "\033[1;31m";
}

const char* COLOR_RESET = "\033[0m";
const char* COLOR_BOLD = "\033[1m";
const char* COLOR_GREEN = "\033[1;32m";
}

DiagnosticEngine::DiagnosticEngine(const SourceManager& sm) : sm_(sm) {}

void DiagnosticEngine::report(DiagnosticSeverity severity, SourceSpan span, std::string message) {
    report_with_notes(severity, span, std::move(message), {});
}

void DiagnosticEngine::report_with_notes(DiagnosticSeverity severity, SourceSpan span, std::string message, std::vector<DiagnosticNote> notes) {
    if (severity == DiagnosticSeverity::ERROR || severity == DiagnosticSeverity::FATAL) {
        error_count_++;
    } else if (severity == DiagnosticSeverity::WARNING) {
        warning_count_++;
    }
    diagnostics_.push_back(Diagnostic{severity, span, std::move(message), std::move(notes)});
}

void DiagnosticEngine::error(SourceSpan span, std::string message) {
    report(DiagnosticSeverity::ERROR, span, std::move(message));
}

void DiagnosticEngine::warning(SourceSpan span, std::string message) {
    report(DiagnosticSeverity::WARNING, span, std::move(message));
}

void DiagnosticEngine::note(SourceSpan span, std::string message) {
    report(DiagnosticSeverity::NOTE, span, std::move(message));
}

void DiagnosticEngine::clear() {
    diagnostics_.clear();
    error_count_ = 0;
    warning_count_ = 0;
}

void DiagnosticEngine::render_span(std::ostream& os, const SourceSpan& span, DiagnosticSeverity sev, const std::string& msg, bool use_color) const {
    const std::string& filename = sm_.get_filename(span.start.file_id);
    std::string fname = filename.empty() ? "<input>" : filename;

    if (use_color) {
        os << COLOR_BOLD << fname << ":" << span.start.line << ":" << span.start.column << ": "
           << severity_color(sev) << severity_string(sev) << ": " << COLOR_BOLD << msg << COLOR_RESET << "\n";
    } else {
        os << fname << ":" << span.start.line << ":" << span.start.column << ": "
           << severity_string(sev) << ": " << msg << "\n";
    }

    std::string_view line_text = sm_.get_line_text(span.start.file_id, span.start.line);
    if (!line_text.empty()) {
        os << line_text << "\n";
        
        // Render caret & underline
        uint32_t col = span.start.column;
        uint32_t len = 1;
        if (span.end.line == span.start.line && span.end.column > span.start.column) {
            len = span.end.column - span.start.column;
        }

        std::string padding(col > 0 ? col - 1 : 0, ' ');
        // Replace any tabs in line_text with spaces in padding to align carets correctly
        for (size_t i = 0; i < padding.size() && i < line_text.size(); ++i) {
            if (line_text[i] == '\t') {
                padding[i] = '\t';
            }
        }

        std::string caret_str = "^";
        if (len > 1) {
            caret_str.append(len - 1, '~');
        }

        if (use_color) {
            os << padding << COLOR_GREEN << caret_str << COLOR_RESET << "\n";
        } else {
            os << padding << caret_str << "\n";
        }
    }

    // Render expansion trace if available
    auto exp = span.expansion;
    while (exp) {
        const std::string& exp_file = sm_.get_filename(exp->call_site.file_id);
        std::string exp_fname = exp_file.empty() ? "<input>" : exp_file;
        if (use_color) {
            os << "  " << COLOR_BOLD << exp_fname << ":" << exp->call_site.line << ":" << exp->call_site.column << ": "
               << severity_color(DiagnosticSeverity::NOTE) << "note: " << COLOR_RESET
               << "expanded from macro '" << exp->macro_or_context_name << "'\n";
        } else {
            os << "  " << exp_fname << ":" << exp->call_site.line << ":" << exp->call_site.column << ": "
               << "note: expanded from macro '" << exp->macro_or_context_name << "'\n";
        }
        exp = exp->parent;
    }
}

std::string DiagnosticEngine::format(const Diagnostic& diag, bool use_color) const {
    std::ostringstream ss;
    render_span(ss, diag.span, diag.severity, diag.message, use_color);
    for (const auto& n : diag.notes) {
        render_span(ss, n.span, DiagnosticSeverity::NOTE, n.message, use_color);
    }
    return ss.str();
}

void DiagnosticEngine::emit_all(std::ostream& os, bool use_color) const {
    for (const auto& diag : diagnostics_) {
        os << format(diag, use_color);
    }
}

} // namespace an32asm
