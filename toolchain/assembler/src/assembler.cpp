#include "an32asm/assembler.hpp"
#include "an32asm/lexer.hpp"
#include "an32asm/source_expander.hpp"
#include "an32asm/parser.hpp"
#include "an32asm/pseudo.hpp"
#include "an32asm/expression.hpp"
#include "an32/encoder.hpp"
#include "an32/types.hpp"
#include "an32/operands.hpp"
#include <algorithm>
#include <unordered_map>
#include <cassert>
#include <cstring>

namespace an32asm {

namespace {

std::optional<an32::Mnemonic> lookup_mnemonic(std::string_view name) {
    std::string lower(name);
    std::transform(lower.begin(), lower.end(), lower.begin(), [](unsigned char c) { return std::tolower(c); });

    static const std::unordered_map<std::string, an32::Mnemonic> mnem_map = {
        {"add", an32::Mnemonic::ADD},       {"sub", an32::Mnemonic::SUB},
        {"sll", an32::Mnemonic::SLL},       {"slt", an32::Mnemonic::SLT},
        {"sltu", an32::Mnemonic::SLTU},     {"xor", an32::Mnemonic::XOR},
        {"srl", an32::Mnemonic::SRL},       {"sra", an32::Mnemonic::SRA},
        {"or", an32::Mnemonic::OR},         {"and", an32::Mnemonic::AND},

        {"addi", an32::Mnemonic::ADDI},     {"slti", an32::Mnemonic::SLTI},
        {"sltiu", an32::Mnemonic::SLTIU},   {"xori", an32::Mnemonic::XORI},
        {"ori", an32::Mnemonic::ORI},       {"andi", an32::Mnemonic::ANDI},
        {"slli", an32::Mnemonic::SLLI},     {"srli", an32::Mnemonic::SRLI},
        {"srai", an32::Mnemonic::SRAI},

        {"lb", an32::Mnemonic::LB},         {"lh", an32::Mnemonic::LH},
        {"lw", an32::Mnemonic::LW},         {"lbu", an32::Mnemonic::LBU},
        {"lhu", an32::Mnemonic::LHU},       {"sb", an32::Mnemonic::SB},
        {"sh", an32::Mnemonic::SH},         {"sw", an32::Mnemonic::SW},

        {"beq", an32::Mnemonic::BEQ},       {"bne", an32::Mnemonic::BNE},
        {"blt", an32::Mnemonic::BLT},       {"bge", an32::Mnemonic::BGE},
        {"bltu", an32::Mnemonic::BLTU},     {"bgeu", an32::Mnemonic::BGEU},

        {"jal", an32::Mnemonic::JAL},       {"jalr", an32::Mnemonic::JALR},
        {"lui", an32::Mnemonic::LUI},       {"auipc", an32::Mnemonic::AUIPC},

        {"mul", an32::Mnemonic::MUL},       {"mulh", an32::Mnemonic::MULH},
        {"mulhsu", an32::Mnemonic::MULHSU}, {"mulhu", an32::Mnemonic::MULHU},
        {"div", an32::Mnemonic::DIV},       {"divu", an32::Mnemonic::DIVU},
        {"rem", an32::Mnemonic::REM},       {"remu", an32::Mnemonic::REMU},

        {"csetbounds", an32::Mnemonic::CSETBOUNDS},
        {"candperm",   an32::Mnemonic::CANDPERM},
        {"cincoffset", an32::Mnemonic::CINCOFFSET},
        {"cgetbase",   an32::Mnemonic::CGETBASE},
        {"cgetlen",    an32::Mnemonic::CGETLEN},
        {"cgettag",    an32::Mnemonic::CGETTAG},
        {"cgetperm",   an32::Mnemonic::CGETPERM},
        {"cgetoffset", an32::Mnemonic::CGETOFFSET},
        {"cclear",     an32::Mnemonic::CCLEAR},
        {"clb",        an32::Mnemonic::CLB},
        {"clh",        an32::Mnemonic::CLH},
        {"clw",        an32::Mnemonic::CLW},
        {"csb",        an32::Mnemonic::CSB},
        {"csh",        an32::Mnemonic::CSH},
        {"csw",        an32::Mnemonic::CSW}
    };

    auto it = mnem_map.find(lower);
    if (it != mnem_map.end()) {
        return it->second;
    }
    return std::nullopt;
}

void write_u32_le(uint8_t* ptr, uint32_t val) {
    ptr[0] = static_cast<uint8_t>(val & 0xFF);
    ptr[1] = static_cast<uint8_t>((val >> 8) & 0xFF);
    ptr[2] = static_cast<uint8_t>((val >> 16) & 0xFF);
    ptr[3] = static_cast<uint8_t>((val >> 24) & 0xFF);
}

} // anonymous namespace

Assembler::Assembler(SourceManager& sm, DiagnosticEngine& diag, AssemblerOptions options)
    : sm_(sm), diag_(diag), options_(std::move(options)) {}

std::unique_ptr<AssemblerObject> Assembler::assemble_file(uint32_t file_id) {
    const auto* buf = sm_.get_buffer(file_id);
    if (!buf) {
        diag_.error(SourceSpan(), "cannot find source buffer with id " + std::to_string(file_id));
        return nullptr;
    }
    return assemble_string(buf->content, buf->filename);
}

std::unique_ptr<AssemblerObject> Assembler::assemble_string(const std::string& source_code, const std::string& filename) {
    uint32_t file_id = sm_.add_buffer(filename, source_code);

    // 1. Lexing
    Lexer lexer(sm_, file_id, diag_);
    auto raw_tokens = lexer.tokenize();

    // 2. Token-Aware Macro & Source Expansion
    SourceExpander expander(sm_, diag_, options_.include_paths);
    auto expanded_tokens = expander.expand(raw_tokens);

    // 3. Parsing into AST
    Parser parser(std::move(expanded_tokens), diag_);
    auto statements = parser.parse_all();

    if (diag_.has_errors()) {
        return nullptr;
    }

    auto obj = std::make_unique<AssemblerObject>();
    obj->source_filename = filename;

    SectionTable sec_table;
    sec_table.get_or_create(".text", SectionType::PROGBITS, SectionFlags::ALLOC | SectionFlags::EXECINSTR);

    // 4. Pass 1: Process statements into section fragment streams
    process_statements(statements, sec_table, obj->symbol_table, obj->fixups);

    // 5. Pass 2: Layout fragments & compute section offsets
    perform_layout(sec_table, obj->symbol_table);

    // 6. Pass 3: Encode machine instructions & evaluate direct branches
    encode_instructions(sec_table, obj->symbol_table, obj->fixups);

    // Move sections into AssemblerObject
    obj->sections = sec_table.extract_sections();

    return obj;
}

void Assembler::process_statements(const std::vector<StatementPtr>& stmts, SectionTable& sec_table,
                                  SymbolTable& symtab, std::vector<Fixup>& fixups) {
    PseudoExpander pseudo_exp(diag_);
    ExpressionEvaluator eval(diag_);
    uint32_t group_counter = 0;

    for (const auto& stmt : stmts) {
        Section* cur_sec = sec_table.get_current_section();
        assert(cur_sec != nullptr);

        if (stmt->kind == StatementKind::LABEL) {
            auto l = std::static_pointer_cast<LabelStatement>(stmt);
            if (l->is_local_numeric) {
                symtab.local_labels().add_def(l->local_label_num, cur_sec->id, cur_sec->data.size(), l->span.start);
            } else {
                symtab.define_symbol(l->name, cur_sec->id, cur_sec->data.size(), l->span);
            }
            continue;
        }

        if (stmt->kind == StatementKind::DIRECTIVE) {
            auto d = std::static_pointer_cast<DirectiveStatement>(stmt);

            // Section switches
            if (d->directive_type == TokenType::DIR_TEXT) {
                sec_table.set_current_section(sec_table.get_or_create(".text", SectionType::PROGBITS, SectionFlags::ALLOC | SectionFlags::EXECINSTR));
                continue;
            }
            if (d->directive_type == TokenType::DIR_DATA) {
                sec_table.set_current_section(sec_table.get_or_create(".data", SectionType::PROGBITS, SectionFlags::ALLOC | SectionFlags::WRITE));
                continue;
            }
            if (d->directive_type == TokenType::DIR_RODATA) {
                sec_table.set_current_section(sec_table.get_or_create(".rodata", SectionType::PROGBITS, SectionFlags::ALLOC));
                continue;
            }
            if (d->directive_type == TokenType::DIR_BSS) {
                sec_table.set_current_section(sec_table.get_or_create(".bss", SectionType::NOBITS, SectionFlags::ALLOC | SectionFlags::WRITE));
                continue;
            }
            if (d->directive_type == TokenType::DIR_SECTION) {
                if (!d->string_args.empty() || !d->symbol_arg.empty()) {
                    std::string sec_name = !d->symbol_arg.empty() ? d->symbol_arg : d->string_args[0];
                    uint32_t flags = SectionFlags::ALLOC;
                    SectionType type = SectionType::PROGBITS;
                    if (sec_name.rfind(".bss", 0) == 0) {
                        type = SectionType::NOBITS;
                        flags |= SectionFlags::WRITE;
                    } else if (sec_name.rfind(".text", 0) == 0) {
                        flags |= SectionFlags::EXECINSTR;
                    } else if (sec_name.rfind(".data", 0) == 0) {
                        flags |= SectionFlags::WRITE;
                    }
                    sec_table.set_current_section(sec_table.get_or_create(sec_name, type, flags));
                }
                continue;
            }

            // Symbol attributes
            if (d->directive_type == TokenType::DIR_GLOBL) {
                if (!d->symbol_arg.empty()) {
                    auto* sym = symtab.get_or_create(d->symbol_arg);
                    sym->binding = SymbolBinding::GLOBAL;
                }
                continue;
            }
            if (d->directive_type == TokenType::DIR_LOCAL) {
                if (!d->symbol_arg.empty()) {
                    auto* sym = symtab.get_or_create(d->symbol_arg);
                    sym->binding = SymbolBinding::LOCAL;
                }
                continue;
            }
            if (d->directive_type == TokenType::DIR_WEAK) {
                if (!d->symbol_arg.empty()) {
                    auto* sym = symtab.get_or_create(d->symbol_arg);
                    sym->binding = SymbolBinding::WEAK;
                }
                continue;
            }
            if (d->directive_type == TokenType::DIR_TYPE) {
                if (!d->symbol_arg.empty()) {
                    auto* sym = symtab.get_or_create(d->symbol_arg);
                    if (!d->string_args.empty()) {
                        if (d->string_args[0] == "@function" || d->string_args[0] == "%function") {
                            sym->type = SymbolType::FUNC;
                        } else if (d->string_args[0] == "@object" || d->string_args[0] == "%object") {
                            sym->type = SymbolType::OBJECT;
                        }
                    }
                }
                continue;
            }
            if (d->directive_type == TokenType::DIR_SIZE) {
                if (!d->symbol_arg.empty() && !d->expr_args.empty()) {
                    auto* sym = symtab.get_or_create(d->symbol_arg);
                    auto val = eval.evaluate_absolute(d->expr_args[0], &symtab);
                    if (val.has_value()) {
                        sym->size = static_cast<uint64_t>(*val);
                    }
                }
                continue;
            }

            // Alignment directives
            if (d->directive_type == TokenType::DIR_ALIGN || d->directive_type == TokenType::DIR_P2ALIGN) {
                if (!d->expr_args.empty()) {
                    auto p2 = eval.evaluate_absolute(d->expr_args[0], &symtab);
                    if (p2.has_value()) {
                        uint64_t align_bytes = 1ULL << (*p2);
                        cur_sec->alignment = std::max(cur_sec->alignment, align_bytes);
                        cur_sec->add_fragment(std::make_unique<AlignFragment>(align_bytes, std::nullopt, 0, d->span));
                    }
                }
                continue;
            }
            if (d->directive_type == TokenType::DIR_BALIGN) {
                if (!d->expr_args.empty()) {
                    auto bytes = eval.evaluate_absolute(d->expr_args[0], &symtab);
                    if (bytes.has_value() && *bytes > 0) {
                        cur_sec->alignment = std::max(cur_sec->alignment, static_cast<uint64_t>(*bytes));
                        cur_sec->add_fragment(std::make_unique<AlignFragment>(static_cast<uint64_t>(*bytes), std::nullopt, 0, d->span));
                    }
                }
                continue;
            }

            // Space / Zero allocation
            if (d->directive_type == TokenType::DIR_ZERO || d->directive_type == TokenType::DIR_SPACE) {
                if (!d->expr_args.empty()) {
                    auto cnt = eval.evaluate_absolute(d->expr_args[0], &symtab);
                    if (cnt.has_value() && *cnt >= 0) {
                        cur_sec->add_fragment(std::make_unique<ZeroFillFragment>(static_cast<uint64_t>(*cnt), d->span));
                    }
                }
                continue;
            }

            // Data emission directives (.byte, .2byte, .4byte, .ascii, .asciz)
            if (d->directive_type == TokenType::DIR_BYTE) {
                std::vector<uint8_t> bytes;
                for (const auto& e : d->expr_args) {
                    auto val = eval.evaluate_absolute(e, &symtab);
                    if (val.has_value()) {
                        bytes.push_back(static_cast<uint8_t>(*val & 0xFF));
                    } else {
                        diag_.error(e->span, "unsupported relocatable expression in .byte directive");
                    }
                }
                cur_sec->add_fragment(std::make_unique<DataFragment>(std::move(bytes), d->span));
                continue;
            }
            if (d->directive_type == TokenType::DIR_2BYTE) {
                std::vector<uint8_t> bytes;
                for (const auto& e : d->expr_args) {
                    auto val = eval.evaluate_absolute(e, &symtab);
                    if (val.has_value()) {
                        uint16_t v16 = static_cast<uint16_t>(*val & 0xFFFF);
                        bytes.push_back(static_cast<uint8_t>(v16 & 0xFF));
                        bytes.push_back(static_cast<uint8_t>((v16 >> 8) & 0xFF));
                    } else {
                        diag_.error(e->span, "unsupported relocatable expression in .2byte / .half directive");
                    }
                }
                cur_sec->add_fragment(std::make_unique<DataFragment>(std::move(bytes), d->span));
                continue;
            }
            if (d->directive_type == TokenType::DIR_4BYTE) {
                for (const auto& e : d->expr_args) {
                    auto res = eval.evaluate(e, &symtab, cur_sec->data.size());
                    if (res.kind == EvalKind::ABSOLUTE) {
                        std::vector<uint8_t> bytes(4);
                        uint32_t v32 = static_cast<uint32_t>(res.value);
                        bytes[0] = static_cast<uint8_t>(v32 & 0xFF);
                        bytes[1] = static_cast<uint8_t>((v32 >> 8) & 0xFF);
                        bytes[2] = static_cast<uint8_t>((v32 >> 16) & 0xFF);
                        bytes[3] = static_cast<uint8_t>((v32 >> 24) & 0xFF);
                        cur_sec->add_fragment(std::make_unique<DataFragment>(std::move(bytes), d->span));
                    } else if (res.kind == EvalKind::RELOCATABLE) {
                        Fixup f;
                        f.kind = FixupKind::ABS32;
                        f.section_id = cur_sec->id;
                        f.offset = cur_sec->data.size();
                        f.symbol_name = res.symbol_name;
                        f.addend = res.value;
                        f.is_local_numeric = res.is_local_numeric;
                        f.local_label_num = res.local_label_num;
                        f.is_forward_ref = res.is_forward_ref;
                        f.span = e->span;
                        fixups.push_back(f);

                        std::vector<uint8_t> bytes(4, 0);
                        cur_sec->add_fragment(std::make_unique<DataFragment>(std::move(bytes), d->span));
                    }
                }
                continue;
            }
            if (d->directive_type == TokenType::DIR_ASCII || d->directive_type == TokenType::DIR_ASCIZ) {
                std::vector<uint8_t> bytes;
                for (const auto& s : d->string_args) {
                    bytes.insert(bytes.end(), s.begin(), s.end());
                    if (d->directive_type == TokenType::DIR_ASCIZ) {
                        bytes.push_back(0);
                    }
                }
                cur_sec->add_fragment(std::make_unique<DataFragment>(std::move(bytes), d->span));
                continue;
            }
            if (d->directive_type == TokenType::DIR_EQU || d->directive_type == TokenType::DIR_SET) {
                if (!d->symbol_arg.empty() && !d->expr_args.empty()) {
                    auto val = eval.evaluate_absolute(d->expr_args[0], &symtab);
                    if (val.has_value()) {
                        auto* sym = symtab.get_or_create(d->symbol_arg);
                        sym->is_defined = true;
                        sym->is_absolute = true;
                        sym->value = static_cast<uint64_t>(*val);
                    }
                }
                continue;
            }
            if (d->directive_type == TokenType::DIR_FILE) {
                continue;
            }
        }

        if (stmt->kind == StatementKind::INSTRUCTION) {
            auto inst = std::static_pointer_cast<InstructionStatement>(stmt);
            auto expanded_list = pseudo_exp.expand(*inst);

            uint32_t group_id = ++group_counter;
            std::optional<uint64_t> auipc_anchor_off;

            for (const auto& exp_inst : expanded_list) {
                auto mnem_opt = lookup_mnemonic(exp_inst.mnemonic);
                if (!mnem_opt.has_value()) {
                    diag_.error(exp_inst.span, "unrecognized instruction mnemonic: '" + exp_inst.mnemonic + "'");
                    continue;
                }

                if (cur_sec->is_executable() && (cur_sec->data.size() % 4) != 0) {
                    diag_.error(exp_inst.span, "instruction placed on misaligned offset (IALIGN=32 requires 4-byte alignment)");
                }

                uint64_t inst_offset = cur_sec->data.size();

                if (*mnem_opt == an32::Mnemonic::AUIPC) {
                    auipc_anchor_off = inst_offset;
                }

                for (const auto& op : exp_inst.operands) {
                    ExprPtr expr;
                    if (op->kind == OperandKind::IMMEDIATE) {
                        expr = std::static_pointer_cast<ImmediateOperand>(op)->expr;
                    } else if (op->kind == OperandKind::MEMORY) {
                        expr = std::static_pointer_cast<MemoryOperand>(op)->offset;
                    }

                    if (expr) {
                        auto res = eval.evaluate(expr, &symtab, inst_offset);
                        if (res.kind == EvalKind::RELOCATABLE || res.kind == EvalKind::RELOC_MODIFIED) {
                            Fixup f;
                            f.section_id = cur_sec->id;
                            f.offset = inst_offset;
                            f.symbol_name = res.symbol_name;
                            f.addend = res.value;
                            f.is_local_numeric = res.is_local_numeric;
                            f.local_label_num = res.local_label_num;
                            f.is_forward_ref = res.is_forward_ref;
                            f.group_id = group_id;
                            f.anchor_offset = auipc_anchor_off;
                            f.span = exp_inst.span;

                            if (res.modifier == RelocModifier::PCREL_HI) {
                                f.kind = FixupKind::PCREL_HI20;
                            } else if (res.modifier == RelocModifier::PCREL_LO) {
                                f.kind = (*mnem_opt == an32::Mnemonic::SW || *mnem_opt == an32::Mnemonic::SH || *mnem_opt == an32::Mnemonic::SB) ?
                                         FixupKind::PCREL_LO12_S : FixupKind::PCREL_LO12_I;
                            } else if (res.modifier == RelocModifier::HI) {
                                f.kind = FixupKind::HI20;
                            } else if (res.modifier == RelocModifier::LO) {
                                f.kind = (*mnem_opt == an32::Mnemonic::SW || *mnem_opt == an32::Mnemonic::SH || *mnem_opt == an32::Mnemonic::SB) ?
                                         FixupKind::LO12_S : FixupKind::LO12_I;
                            } else if (*mnem_opt == an32::Mnemonic::JAL) {
                                f.kind = FixupKind::JAL;
                            } else if (*mnem_opt == an32::Mnemonic::BEQ || *mnem_opt == an32::Mnemonic::BNE ||
                                       *mnem_opt == an32::Mnemonic::BLT || *mnem_opt == an32::Mnemonic::BGE ||
                                       *mnem_opt == an32::Mnemonic::BLTU || *mnem_opt == an32::Mnemonic::BGEU) {
                                f.kind = FixupKind::BRANCH;
                            } else {
                                f.kind = FixupKind::LO12_I;
                            }

                            if (exp_inst.is_pseudo && inst->mnemonic == "call") {
                                f.kind = FixupKind::CALL;
                            }

                            fixups.push_back(f);
                        }
                    }
                }

                cur_sec->add_fragment(std::make_unique<InstructionFragment>(exp_inst.mnemonic, exp_inst.operands, exp_inst.span));
                cur_sec->data.resize(cur_sec->data.size() + 4, 0);
            }
        }
    }
}

void Assembler::perform_layout(SectionTable& sec_table, SymbolTable& /*symtab*/) {
    for (const auto& sec_ptr : sec_table.get_sections()) {
        Section& sec = *sec_ptr;
        uint64_t cur_offset = 0;
        std::vector<uint8_t> new_data;

        for (auto& frag : sec.fragments) {
            if (frag->kind == FragmentKind::ALIGN) {
                auto* afrag = static_cast<AlignFragment*>(frag.get());
                uint64_t align = afrag->alignment_bytes;
                uint64_t pad = (align - (cur_offset % align)) % align;

                if (pad > 0) {
                    if (sec.is_executable()) {
                        if (!afrag->fill_byte.has_value() && (pad % 4) != 0) {
                            diag_.error(afrag->span, "executable section alignment requires non-multiple of 4 padding (" +
                                        std::to_string(pad) + " bytes); specify explicit fill byte or correct layout");
                        }
                        for (uint64_t p = 0; p < pad; p += 4) {
                            uint32_t nop = 0x00000013;
                            if (p + 4 <= pad) {
                                new_data.push_back(static_cast<uint8_t>(nop & 0xFF));
                                new_data.push_back(static_cast<uint8_t>((nop >> 8) & 0xFF));
                                new_data.push_back(static_cast<uint8_t>((nop >> 16) & 0xFF));
                                new_data.push_back(static_cast<uint8_t>((nop >> 24) & 0xFF));
                            } else {
                                uint8_t fill = afrag->fill_byte.value_or(0);
                                for (uint64_t rem = p; rem < pad; ++rem) {
                                    new_data.push_back(fill);
                                }
                            }
                        }
                    } else {
                        uint8_t fill = afrag->fill_byte.value_or(0);
                        new_data.insert(new_data.end(), pad, fill);
                    }
                    cur_offset += pad;
                }
                frag->offset = cur_offset;
            } else if (frag->kind == FragmentKind::INSTRUCTION) {
                frag->offset = cur_offset;
                cur_offset += 4;
                new_data.resize(cur_offset, 0);
            } else if (frag->kind == FragmentKind::DATA) {
                auto* dfrag = static_cast<DataFragment*>(frag.get());
                frag->offset = cur_offset;
                new_data.insert(new_data.end(), dfrag->data.begin(), dfrag->data.end());
                cur_offset += dfrag->data.size();
            } else if (frag->kind == FragmentKind::ZERO_FILL) {
                auto* zfrag = static_cast<ZeroFillFragment*>(frag.get());
                frag->offset = cur_offset;
                cur_offset += zfrag->count;
                if (!sec.is_nobits()) {
                    new_data.insert(new_data.end(), zfrag->count, 0);
                }
            }
        }

        sec.memory_size = cur_offset;
        sec.data = std::move(new_data);
    }
}

void Assembler::encode_instructions(SectionTable& sec_table, SymbolTable& symtab, std::vector<Fixup>& /*fixups*/) {
    ExpressionEvaluator eval(diag_);

    auto get_xreg = [](const OperandPtr& op) -> an32::XReg {
        if (op && op->kind == OperandKind::REG_X) {
            return an32::XReg(static_cast<uint8_t>(std::static_pointer_cast<XRegOperand>(op)->reg_index));
        }
        return an32::XReg(0);
    };

    auto get_capreg = [](const OperandPtr& op) -> an32::CapReg {
        if (op && op->kind == OperandKind::REG_CAP) {
            return an32::CapReg(static_cast<uint8_t>(std::static_pointer_cast<CapRegOperand>(op)->reg_index));
        }
        return an32::CapReg(0);
    };

    auto get_imm_val = [&](const OperandPtr& op, uint64_t cur_pc) -> int64_t {
        if (!op) return 0;
        ExprPtr expr;
        if (op->kind == OperandKind::IMMEDIATE) {
            expr = std::static_pointer_cast<ImmediateOperand>(op)->expr;
        } else if (op->kind == OperandKind::MEMORY) {
            expr = std::static_pointer_cast<MemoryOperand>(op)->offset;
        }
        if (expr) {
            auto res = eval.evaluate(expr, &symtab, cur_pc);
            if (res.kind == EvalKind::ABSOLUTE) {
                return res.value;
            }
        }
        return 0;
    };

    for (const auto& sec_ptr : sec_table.get_sections()) {
        Section& sec = *sec_ptr;
        if (sec.data.empty()) continue;

        for (auto& frag : sec.fragments) {
            if (frag->kind != FragmentKind::INSTRUCTION) continue;
            auto* ifrag = static_cast<InstructionFragment*>(frag.get());
            uint64_t inst_offset = ifrag->offset;

            auto mnem_opt = lookup_mnemonic(ifrag->mnemonic);
            if (!mnem_opt.has_value()) continue;
            an32::Mnemonic m = *mnem_opt;
            const auto& ops = ifrag->operands;

            std::optional<uint32_t> word;

            // R-type integer instructions
            if (m == an32::Mnemonic::ADD || m == an32::Mnemonic::SUB ||
                m == an32::Mnemonic::SLL || m == an32::Mnemonic::SLT ||
                m == an32::Mnemonic::SLTU || m == an32::Mnemonic::XOR ||
                m == an32::Mnemonic::SRL || m == an32::Mnemonic::SRA ||
                m == an32::Mnemonic::OR || m == an32::Mnemonic::AND ||
                m == an32::Mnemonic::MUL || m == an32::Mnemonic::MULH ||
                m == an32::Mnemonic::MULHSU || m == an32::Mnemonic::MULHU ||
                m == an32::Mnemonic::DIV || m == an32::Mnemonic::DIVU ||
                m == an32::Mnemonic::REM || m == an32::Mnemonic::REMU) {
                if (ops.size() >= 3) {
                    auto res = an32::Encoder::encode_r(m, get_xreg(ops[0]), get_xreg(ops[1]), get_xreg(ops[2]));
                    if (res.is_ok()) word = res.value();
                    else diag_.error(ifrag->span, std::string("instruction encoding error: ") + std::string(an32::to_string(res.error())));
                }
            }
            // Shifts (I-type)
            else if (m == an32::Mnemonic::SLLI || m == an32::Mnemonic::SRLI || m == an32::Mnemonic::SRAI) {
                if (ops.size() >= 3) {
                    int64_t shamt = get_imm_val(ops[2], inst_offset);
                    auto res = an32::Encoder::encode_shift(m, get_xreg(ops[0]), get_xreg(ops[1]), an32::ShiftAmount5(static_cast<uint8_t>(shamt & 0x1F)));
                    if (res.is_ok()) word = res.value();
                    else diag_.error(ifrag->span, std::string("instruction encoding error: ") + std::string(an32::to_string(res.error())));
                }
            }
            // I-type arithmetic instructions
            else if (m == an32::Mnemonic::ADDI || m == an32::Mnemonic::SLTI ||
                     m == an32::Mnemonic::SLTIU || m == an32::Mnemonic::XORI ||
                     m == an32::Mnemonic::ORI || m == an32::Mnemonic::ANDI) {
                if (ops.size() >= 3) {
                    int64_t imm = get_imm_val(ops[2], inst_offset);
                    auto res = an32::Encoder::encode_i(m, get_xreg(ops[0]), get_xreg(ops[1]), an32::IImm12(static_cast<int16_t>(imm)));
                    if (res.is_ok()) word = res.value();
                    else diag_.error(ifrag->span, std::string("instruction encoding error: ") + std::string(an32::to_string(res.error())));
                }
            }
            // Standard Load instructions (I-type)
            else if (m == an32::Mnemonic::LB || m == an32::Mnemonic::LH ||
                     m == an32::Mnemonic::LW || m == an32::Mnemonic::LBU ||
                     m == an32::Mnemonic::LHU) {
                if (ops.size() >= 2) {
                    auto mem_op = std::dynamic_pointer_cast<MemoryOperand>(ops[1]);
                    if (mem_op) {
                        int64_t imm = get_imm_val(mem_op, inst_offset);
                        auto res = an32::Encoder::encode_load(m, get_xreg(ops[0]), get_xreg(mem_op->base_reg), an32::IImm12(static_cast<int16_t>(imm)));
                        if (res.is_ok()) word = res.value();
                        else diag_.error(ifrag->span, std::string("instruction encoding error: ") + std::string(an32::to_string(res.error())));
                    }
                }
            }
            // Standard Store instructions (S-type)
            else if (m == an32::Mnemonic::SB || m == an32::Mnemonic::SH ||
                     m == an32::Mnemonic::SW) {
                if (ops.size() >= 2) {
                    auto mem_op = std::dynamic_pointer_cast<MemoryOperand>(ops[1]);
                    if (mem_op) {
                        int64_t imm = get_imm_val(mem_op, inst_offset);
                        auto res = an32::Encoder::encode_s(m, get_xreg(ops[0]), get_xreg(mem_op->base_reg), an32::SImm12(static_cast<int16_t>(imm)));
                        if (res.is_ok()) word = res.value();
                        else diag_.error(ifrag->span, std::string("instruction encoding error: ") + std::string(an32::to_string(res.error())));
                    }
                }
            }
            // Branch instructions (B-type)
            else if (m == an32::Mnemonic::BEQ || m == an32::Mnemonic::BNE ||
                     m == an32::Mnemonic::BLT || m == an32::Mnemonic::BGE ||
                     m == an32::Mnemonic::BLTU || m == an32::Mnemonic::BGEU) {
                if (ops.size() >= 3) {
                    int64_t offset = get_imm_val(ops[2], inst_offset);
                    auto res = an32::Encoder::encode_b(m, get_xreg(ops[0]), get_xreg(ops[1]), an32::BranchOffset13(static_cast<int16_t>(offset)));
                    if (res.is_ok()) word = res.value();
                    else diag_.error(ifrag->span, std::string("instruction encoding error: ") + std::string(an32::to_string(res.error())));
                }
            }
            // U-type instructions (LUI, AUIPC)
            else if (m == an32::Mnemonic::LUI || m == an32::Mnemonic::AUIPC) {
                if (ops.size() >= 2) {
                    int64_t imm = get_imm_val(ops[1], inst_offset);
                    auto res = an32::Encoder::encode_u(m, get_xreg(ops[0]), an32::UImm20(static_cast<uint32_t>(imm)));
                    if (res.is_ok()) word = res.value();
                    else diag_.error(ifrag->span, std::string("instruction encoding error: ") + std::string(an32::to_string(res.error())));
                }
            }
            // JAL (J-type)
            else if (m == an32::Mnemonic::JAL) {
                if (ops.size() >= 2) {
                    int64_t offset = get_imm_val(ops[1], inst_offset);
                    auto res = an32::Encoder::encode_j(get_xreg(ops[0]), an32::JumpOffset21(static_cast<int32_t>(offset)));
                    if (res.is_ok()) word = res.value();
                    else diag_.error(ifrag->span, std::string("instruction encoding error: ") + std::string(an32::to_string(res.error())));
                }
            }
            // JALR (I-type)
            else if (m == an32::Mnemonic::JALR) {
                if (ops.size() >= 2) {
                    auto mem_op = std::dynamic_pointer_cast<MemoryOperand>(ops[1]);
                    if (mem_op) {
                        int64_t imm = get_imm_val(mem_op, inst_offset);
                        auto res = an32::Encoder::encode_jalr(get_xreg(ops[0]), get_xreg(mem_op->base_reg), an32::IImm12(static_cast<int16_t>(imm)));
                        if (res.is_ok()) word = res.value();
                        else diag_.error(ifrag->span, std::string("instruction encoding error: ") + std::string(an32::to_string(res.error())));
                    }
                }
            }
            // Capability manipulation instructions (CAP_R-type)
            else if (m == an32::Mnemonic::CSETBOUNDS) {
                if (ops.size() >= 3) {
                    auto res = an32::Encoder::encode_csetbounds(get_capreg(ops[0]), get_capreg(ops[1]), get_xreg(ops[2]));
                    if (res.is_ok()) word = res.value();
                    else diag_.error(ifrag->span, std::string("instruction encoding error: ") + std::string(an32::to_string(res.error())));
                }
            }
            else if (m == an32::Mnemonic::CANDPERM) {
                if (ops.size() >= 3) {
                    auto res = an32::Encoder::encode_candperm(get_capreg(ops[0]), get_capreg(ops[1]), get_xreg(ops[2]));
                    if (res.is_ok()) word = res.value();
                    else diag_.error(ifrag->span, std::string("instruction encoding error: ") + std::string(an32::to_string(res.error())));
                }
            }
            else if (m == an32::Mnemonic::CINCOFFSET) {
                if (ops.size() >= 3) {
                    auto res = an32::Encoder::encode_cincoffset(get_capreg(ops[0]), get_capreg(ops[1]), get_xreg(ops[2]));
                    if (res.is_ok()) word = res.value();
                    else diag_.error(ifrag->span, std::string("instruction encoding error: ") + std::string(an32::to_string(res.error())));
                }
            }
            // Capability inspection (CGET-type)
            else if (m == an32::Mnemonic::CGETBASE) {
                if (ops.size() >= 2) {
                    auto res = an32::Encoder::encode_cgetbase(get_xreg(ops[0]), get_capreg(ops[1]));
                    if (res.is_ok()) word = res.value();
                    else diag_.error(ifrag->span, std::string("instruction encoding error: ") + std::string(an32::to_string(res.error())));
                }
            }
            else if (m == an32::Mnemonic::CGETLEN) {
                if (ops.size() >= 2) {
                    auto res = an32::Encoder::encode_cgetlen(get_xreg(ops[0]), get_capreg(ops[1]));
                    if (res.is_ok()) word = res.value();
                    else diag_.error(ifrag->span, std::string("instruction encoding error: ") + std::string(an32::to_string(res.error())));
                }
            }
            else if (m == an32::Mnemonic::CGETTAG) {
                if (ops.size() >= 2) {
                    auto res = an32::Encoder::encode_cgettag(get_xreg(ops[0]), get_capreg(ops[1]));
                    if (res.is_ok()) word = res.value();
                    else diag_.error(ifrag->span, std::string("instruction encoding error: ") + std::string(an32::to_string(res.error())));
                }
            }
            else if (m == an32::Mnemonic::CGETPERM) {
                if (ops.size() >= 2) {
                    auto res = an32::Encoder::encode_cgetperm(get_xreg(ops[0]), get_capreg(ops[1]));
                    if (res.is_ok()) word = res.value();
                    else diag_.error(ifrag->span, std::string("instruction encoding error: ") + std::string(an32::to_string(res.error())));
                }
            }
            else if (m == an32::Mnemonic::CGETOFFSET) {
                if (ops.size() >= 2) {
                    auto res = an32::Encoder::encode_cgetoffset(get_xreg(ops[0]), get_capreg(ops[1]));
                    if (res.is_ok()) word = res.value();
                    else diag_.error(ifrag->span, std::string("instruction encoding error: ") + std::string(an32::to_string(res.error())));
                }
            }
            // Capability clear (CCLEAR-type)
            else if (m == an32::Mnemonic::CCLEAR) {
                if (ops.size() >= 1) {
                    auto res = an32::Encoder::encode_cclear(get_capreg(ops[0]));
                    if (res.is_ok()) word = res.value();
                    else diag_.error(ifrag->span, std::string("instruction encoding error: ") + std::string(an32::to_string(res.error())));
                }
            }
            // Capability Load instructions (CAP_MEM_I-type)
            else if (m == an32::Mnemonic::CLB || m == an32::Mnemonic::CLH || m == an32::Mnemonic::CLW) {
                if (ops.size() >= 2) {
                    auto mem_op = std::dynamic_pointer_cast<MemoryOperand>(ops[1]);
                    if (mem_op) {
                        int64_t imm = get_imm_val(mem_op, inst_offset);
                        auto res = an32::Encoder::encode_cap_load(m, get_xreg(ops[0]), get_capreg(mem_op->base_reg), an32::IImm12(static_cast<int16_t>(imm)));
                        if (res.is_ok()) word = res.value();
                        else diag_.error(ifrag->span, std::string("instruction encoding error: ") + std::string(an32::to_string(res.error())));
                    }
                }
            }
            // Capability Store instructions (CAP_MEM_S-type)
            else if (m == an32::Mnemonic::CSB || m == an32::Mnemonic::CSH || m == an32::Mnemonic::CSW) {
                if (ops.size() >= 2) {
                    auto mem_op = std::dynamic_pointer_cast<MemoryOperand>(ops[1]);
                    if (mem_op) {
                        int64_t imm = get_imm_val(mem_op, inst_offset);
                        auto res = an32::Encoder::encode_cap_store(m, get_xreg(ops[0]), get_capreg(mem_op->base_reg), an32::SImm12(static_cast<int16_t>(imm)));
                        if (res.is_ok()) word = res.value();
                        else diag_.error(ifrag->span, std::string("instruction encoding error: ") + std::string(an32::to_string(res.error())));
                    }
                }
            }

            if (word.has_value()) {
                ifrag->raw_word = *word;
                if (inst_offset + 4 <= sec.data.size()) {
                    write_u32_le(sec.data.data() + inst_offset, *word);
                }
            }
        }
    }
}

} // namespace an32asm
