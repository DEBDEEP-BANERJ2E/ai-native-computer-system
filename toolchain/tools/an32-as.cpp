#include "an32asm/source_manager.hpp"
#include "an32asm/diagnostic.hpp"
#include "an32asm/assembler.hpp"
#include "an32asm/flat_finalizer.hpp"
#include <iostream>
#include <fstream>
#include <vector>
#include <string>

void print_usage(const char* prog) {
    std::cout << "Usage: " << prog << " [options] <input.s>\n"
              << "Options:\n"
              << "  -o <file>              Specify output destination (requires --emit-bin or --emit-hex)\n"
              << "  --emit-bin [file]      Emit flat binary image of resolved section (default: .text)\n"
              << "  --emit-hex [file]      Emit Verilog @00000000 hex image of resolved section (default: .text)\n"
              << "  --section <name>       Select section to emit (default: .text)\n"
              << "  -I <dir>               Add directory to .include search paths\n"
              << "  -g                     Preserve source debug mappings\n"
              << "  --dump-object          Dump in-memory object model summary\n"
              << "  --dump-symbols         Dump symbol table\n"
              << "  --dump-fixups          Dump fixup and relocation records\n"
              << "  --dump-ast             Dump parsed AST statements\n"
              << "  -v, --verbose          Enable verbose logging\n"
              << "  -h, --help             Display this help message\n";
}

int main(int argc, char** argv) {
    if (argc < 2) {
        print_usage(argv[0]);
        return 1;
    }

    std::string input_file;
    std::string output_file;
    std::string emit_bin_file;
    std::string emit_hex_file;
    std::string section_name = ".text";
    std::vector<std::string> include_paths;

    bool opt_emit_bin = false;
    bool opt_emit_hex = false;
    bool opt_dump_object = false;
    bool opt_dump_symbols = false;
    bool opt_dump_fixups = false;
    bool opt_dump_ast = false;
    bool opt_debug = false;
    bool opt_verbose = false;

    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if (arg == "-h" || arg == "--help") {
            print_usage(argv[0]);
            return 0;
        } else if (arg == "-o") {
            if (i + 1 < argc) {
                output_file = argv[++i];
            } else {
                std::cerr << "error: missing argument after -o\n";
                return 1;
            }
        } else if (arg == "--emit-bin") {
            opt_emit_bin = true;
            if (i + 1 < argc && argv[i + 1][0] != '-') {
                emit_bin_file = argv[++i];
            }
        } else if (arg == "--emit-hex") {
            opt_emit_hex = true;
            if (i + 1 < argc && argv[i + 1][0] != '-') {
                emit_hex_file = argv[++i];
            }
        } else if (arg == "--section") {
            if (i + 1 < argc) {
                section_name = argv[++i];
            } else {
                std::cerr << "error: missing argument after --section\n";
                return 1;
            }
        } else if (arg == "-I") {
            if (i + 1 < argc) {
                include_paths.push_back(argv[++i]);
            } else {
                std::cerr << "error: missing argument after -I\n";
                return 1;
            }
        } else if (arg.rfind("-I", 0) == 0 && arg.size() > 2) {
            include_paths.push_back(arg.substr(2));
        } else if (arg == "-g") {
            opt_debug = true;
        } else if (arg == "--dump-object") {
            opt_dump_object = true;
        } else if (arg == "--dump-symbols") {
            opt_dump_symbols = true;
        } else if (arg == "--dump-fixups") {
            opt_dump_fixups = true;
        } else if (arg == "--dump-ast") {
            opt_dump_ast = true;
        } else if (arg == "-v" || arg == "--verbose") {
            opt_verbose = true;
        } else if (arg[0] == '-') {
            std::cerr << "error: unrecognized option '" << arg << "'\n";
            return 1;
        } else {
            if (input_file.empty()) {
                input_file = arg;
            } else {
                std::cerr << "error: multiple input files specified: '" << input_file << "' and '" << arg << "'\n";
                return 1;
            }
        }
    }

    if (input_file.empty()) {
        std::cerr << "error: no input file specified\n";
        return 1;
    }

    // Explicit check for -o without flat image flags
    if (!output_file.empty() && !opt_emit_bin && !opt_emit_hex) {
        std::cerr << "an32-as: error: ELF relocatable object emission is introduced in Phase 3. Use --emit-hex or --emit-bin for flat bootstrap images.\n";
        return 1;
    }

    if (opt_emit_bin && emit_bin_file.empty() && !output_file.empty()) {
        emit_bin_file = output_file;
    }
    if (opt_emit_hex && emit_hex_file.empty() && !output_file.empty()) {
        emit_hex_file = output_file;
    }

    an32asm::SourceManager sm;
    an32asm::DiagnosticEngine diag(sm);

    auto file_id = sm.load_file(input_file);
    if (!file_id.has_value()) {
        std::cerr << "an32-as: fatal error: cannot open input file '" << input_file << "'\n";
        return 1;
    }

    an32asm::AssemblerOptions opts;
    opts.include_paths = include_paths;
    opts.debug_info = opt_debug;
    opts.verbose = opt_verbose;

    an32asm::Assembler assembler(sm, diag, opts);
    auto obj = assembler.assemble_file(*file_id);

    if (diag.has_errors() || !obj) {
        diag.emit_all(std::cerr, true);
        return 1;
    }

    if (opt_dump_object) {
        obj->dump_summary(std::cout);
    }
    if (opt_dump_symbols) {
        obj->dump_symbols(std::cout);
    }
    if (opt_dump_fixups) {
        obj->dump_fixups(std::cout);
    }
    if (opt_dump_ast) {
        // AST is parsed during assembly; dump summary if requested
        obj->dump_summary(std::cout);
    }

    if (opt_emit_bin || opt_emit_hex) {
        an32asm::FlatImageFinalizer finalizer(*obj, diag);
        if (!finalizer.finalize_section(section_name, 0)) {
            diag.emit_all(std::cerr, true);
            std::cerr << "an32-as: error: failed to resolve all fixups for flat image emission\n";
            return 1;
        }

        if (opt_emit_bin) {
            if (!emit_bin_file.empty()) {
                std::ofstream ofs(emit_bin_file, std::ios::binary);
                if (!ofs.is_open()) {
                    std::cerr << "an32-as: error: cannot open output binary file '" << emit_bin_file << "'\n";
                    return 1;
                }
                finalizer.emit_binary(ofs, section_name);
            } else {
                finalizer.emit_binary(std::cout, section_name);
            }
        }

        if (opt_emit_hex) {
            if (!emit_hex_file.empty()) {
                std::ofstream ofs(emit_hex_file);
                if (!ofs.is_open()) {
                    std::cerr << "an32-as: error: cannot open output hex file '" << emit_hex_file << "'\n";
                    return 1;
                }
                finalizer.emit_hex(ofs, section_name, 0);
            } else {
                finalizer.emit_hex(std::cout, section_name, 0);
            }
        }
    }

    return 0;
}
