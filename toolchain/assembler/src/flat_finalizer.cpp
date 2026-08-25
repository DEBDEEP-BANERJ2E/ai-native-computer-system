#include "an32asm/flat_finalizer.hpp"
#include "an32asm/expression.hpp"
#include <iomanip>
#include <cstring>

namespace an32asm {

FlatImageFinalizer::FlatImageFinalizer(AssemblerObject& obj, DiagnosticEngine& diag)
    : obj_(obj), diag_(diag) {}

namespace {
uint32_t read_u32_le(const uint8_t* ptr) {
    return static_cast<uint32_t>(ptr[0]) |
          (static_cast<uint32_t>(ptr[1]) << 8) |
          (static_cast<uint32_t>(ptr[2]) << 16) |
          (static_cast<uint32_t>(ptr[3]) << 24);
}

void write_u32_le(uint8_t* ptr, uint32_t val) {
    ptr[0] = static_cast<uint8_t>(val & 0xFF);
    ptr[1] = static_cast<uint8_t>((val >> 8) & 0xFF);
    ptr[2] = static_cast<uint8_t>((val >> 16) & 0xFF);
    ptr[3] = static_cast<uint8_t>((val >> 24) & 0xFF);
}

uint32_t patch_b_imm(uint32_t inst, int32_t imm) {
    uint32_t uimm = static_cast<uint32_t>(imm);
    uint32_t b12 = (uimm >> 12) & 0x1;
    uint32_t b10_5 = (uimm >> 5) & 0x3F;
    uint32_t b4_1 = (uimm >> 1) & 0xF;
    uint32_t b11 = (uimm >> 11) & 0x1;

    inst &= 0x01FFF07F; // Clear [31:25] and [11:7]
    inst |= (b12 << 31) | (b10_5 << 25) | (b4_1 << 8) | (b11 << 7);
    return inst;
}

uint32_t patch_j_imm(uint32_t inst, int32_t imm) {
    uint32_t uimm = static_cast<uint32_t>(imm);
    uint32_t b20 = (uimm >> 20) & 0x1;
    uint32_t b10_1 = (uimm >> 1) & 0x3FF;
    uint32_t b11 = (uimm >> 11) & 0x1;
    uint32_t b19_12 = (uimm >> 12) & 0xFF;

    inst &= 0x00000FFF; // Clear [31:12]
    inst |= (b20 << 31) | (b10_1 << 21) | (b11 << 20) | (b19_12 << 12);
    return inst;
}

uint32_t patch_u_imm(uint32_t inst, uint32_t hi20) {
    inst &= 0x00000FFF; // Clear [31:12]
    inst |= (hi20 & 0xFFFFF) << 12;
    return inst;
}

uint32_t patch_i_imm(uint32_t inst, int32_t lo12) {
    inst &= 0x000FFFFF; // Clear [31:20]
    inst |= (static_cast<uint32_t>(lo12) & 0xFFF) << 20;
    return inst;
}

uint32_t patch_s_imm(uint32_t inst, int32_t lo12) {
    uint32_t uimm = static_cast<uint32_t>(lo12);
    uint32_t b11_5 = (uimm >> 5) & 0x7F;
    uint32_t b4_0 = uimm & 0x1F;

    inst &= 0x01FFF07F; // Clear [31:25] and [11:7]
    inst |= (b11_5 << 25) | (b4_0 << 7);
    return inst;
}
}

bool FlatImageFinalizer::apply_fixup(const Fixup& fixup, Section& sec, uint64_t base_address) {
    // 1. Resolve target symbol offset
    uint64_t target_offset = 0;
    if (fixup.is_local_numeric) {
        auto resolved = obj_.symbol_table.local_labels().resolve(
            fixup.local_label_num, fixup.is_forward_ref, fixup.section_id, fixup.offset);
        if (!resolved.has_value()) {
            diag_.error(fixup.span, "unresolved local numeric label reference: " +
                        std::to_string(fixup.local_label_num) + (fixup.is_forward_ref ? "f" : "b"));
            return false;
        }
        target_offset = *resolved;
    } else {
        const auto* sym = obj_.symbol_table.find(fixup.symbol_name);
        if (!sym || !sym->is_defined) {
            diag_.error(fixup.span, "unresolved external symbol in flat binary image: '" + fixup.symbol_name + "'");
            return false;
        }
        if (sym->is_absolute) {
            target_offset = sym->value;
        } else {
            if (!sym->section_id.has_value() || *sym->section_id != sec.id) {
                diag_.error(fixup.span, "cross-section symbol reference to '" + fixup.symbol_name + "' cannot be resolved in flat image");
                return false;
            }
            target_offset = sym->value;
        }
    }

    if (fixup.offset + 4 > sec.data.size() && fixup.kind != FixupKind::ABS32) {
        diag_.error(fixup.span, "fixup offset out of section bounds");
        return false;
    }

    uint8_t* ptr = sec.data.data() + fixup.offset;
    uint32_t word = read_u32_le(ptr);

    int64_t inst_pc = static_cast<int64_t>(base_address + fixup.offset);
    int64_t target_pc = static_cast<int64_t>(base_address + target_offset + fixup.addend);

    switch (fixup.kind) {
        case FixupKind::BRANCH: {
            int64_t delta = target_pc - inst_pc;
            if (delta < -4096 || delta > 4094 || (delta & 1) != 0) {
                diag_.error(fixup.span, "branch offset out of range: " + std::to_string(delta));
                return false;
            }
            write_u32_le(ptr, patch_b_imm(word, static_cast<int32_t>(delta)));
            return true;
        }

        case FixupKind::JAL: {
            int64_t delta = target_pc - inst_pc;
            if (delta < -1048576 || delta > 1048574 || (delta & 1) != 0) {
                diag_.error(fixup.span, "jump offset out of range: " + std::to_string(delta));
                return false;
            }
            write_u32_le(ptr, patch_j_imm(word, static_cast<int32_t>(delta)));
            return true;
        }

        case FixupKind::CALL: {
            // AUIPC at fixup.offset, JALR at fixup.offset + 4
            int64_t delta = target_pc - inst_pc;
            int64_t hi = ExpressionEvaluator::calc_hi20(delta);
            int64_t lo = ExpressionEvaluator::calc_lo12(delta);

            write_u32_le(ptr, patch_u_imm(word, static_cast<uint32_t>(hi)));

            if (fixup.offset + 8 <= sec.data.size()) {
                uint8_t* jalr_ptr = sec.data.data() + fixup.offset + 4;
                uint32_t jalr_word = read_u32_le(jalr_ptr);
                write_u32_le(jalr_ptr, patch_i_imm(jalr_word, static_cast<int32_t>(lo)));
            }
            return true;
        }

        case FixupKind::PCREL_HI20: {
            int64_t delta = target_pc - inst_pc;
            int64_t hi = ExpressionEvaluator::calc_hi20(delta);
            write_u32_le(ptr, patch_u_imm(word, static_cast<uint32_t>(hi)));
            return true;
        }

        case FixupKind::PCREL_LO12_I: {
            uint64_t anchor_off = fixup.anchor_offset.value_or(fixup.offset);
            int64_t anchor_pc = static_cast<int64_t>(base_address + anchor_off);
            int64_t delta = target_pc - anchor_pc;
            int64_t lo = ExpressionEvaluator::calc_lo12(delta);
            write_u32_le(ptr, patch_i_imm(word, static_cast<int32_t>(lo)));
            return true;
        }

        case FixupKind::PCREL_LO12_S: {
            uint64_t anchor_off = fixup.anchor_offset.value_or(fixup.offset);
            int64_t anchor_pc = static_cast<int64_t>(base_address + anchor_off);
            int64_t delta = target_pc - anchor_pc;
            int64_t lo = ExpressionEvaluator::calc_lo12(delta);
            write_u32_le(ptr, patch_s_imm(word, static_cast<int32_t>(lo)));
            return true;
        }

        case FixupKind::HI20: {
            int64_t hi = ExpressionEvaluator::calc_hi20(target_pc);
            write_u32_le(ptr, patch_u_imm(word, static_cast<uint32_t>(hi)));
            return true;
        }

        case FixupKind::LO12_I: {
            int64_t lo = ExpressionEvaluator::calc_lo12(target_pc);
            write_u32_le(ptr, patch_i_imm(word, static_cast<int32_t>(lo)));
            return true;
        }

        case FixupKind::LO12_S: {
            int64_t lo = ExpressionEvaluator::calc_lo12(target_pc);
            write_u32_le(ptr, patch_s_imm(word, static_cast<int32_t>(lo)));
            return true;
        }

        case FixupKind::ABS32: {
            write_u32_le(ptr, static_cast<uint32_t>(target_pc));
            return true;
        }

        case FixupKind::ADD32:
        case FixupKind::SUB32:
            return true;
    }

    return false;
}

bool FlatImageFinalizer::finalize_section(const std::string& section_name, uint64_t base_address) {
    auto* sec = obj_.find_section(section_name);
    if (!sec) {
        diag_.error(SourceSpan(), "section '" + section_name + "' not found in assembler object");
        return false;
    }

    bool all_ok = true;
    for (const auto& fixup : obj_.fixups) {
        if (fixup.section_id == sec->id) {
            if (!apply_fixup(fixup, *sec, base_address)) {
                all_ok = false;
            }
        }
    }
    return all_ok;
}

bool FlatImageFinalizer::emit_binary(std::ostream& os, const std::string& section_name) const {
    const auto* sec = obj_.find_section(section_name);
    if (!sec) return false;
    os.write(reinterpret_cast<const char*>(sec->data.data()), sec->data.size());
    return true;
}

bool FlatImageFinalizer::emit_hex(std::ostream& os, const std::string& section_name, uint64_t base_address) const {
    const auto* sec = obj_.find_section(section_name);
    if (!sec) return false;

    // Verilog hex format
    os << "@" << std::hex << std::setw(8) << std::setfill('0') << (base_address / 4) << "\n";
    for (size_t i = 0; i < sec->data.size(); i += 4) {
        uint32_t word = 0;
        if (i + 4 <= sec->data.size()) {
            word = read_u32_le(sec->data.data() + i);
        } else {
            // Partial word
            for (size_t b = 0; b < sec->data.size() - i; ++b) {
                word |= static_cast<uint32_t>(sec->data[i + b]) << (b * 8);
            }
        }
        os << std::hex << std::setw(8) << std::setfill('0') << word << "\n";
    }
    return true;
}

std::vector<uint32_t> FlatImageFinalizer::get_machine_words(const std::string& section_name) const {
    std::vector<uint32_t> words;
    const auto* sec = obj_.find_section(section_name);
    if (!sec) return words;

    for (size_t i = 0; i < sec->data.size(); i += 4) {
        if (i + 4 <= sec->data.size()) {
            words.push_back(read_u32_le(sec->data.data() + i));
        }
    }
    return words;
}

} // namespace an32asm
