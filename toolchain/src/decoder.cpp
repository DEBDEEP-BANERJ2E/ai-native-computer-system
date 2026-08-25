#include "an32/decoder.hpp"

namespace an32 {

int32_t Decoder::extract_i_imm(uint32_t word) noexcept {
    int32_t imm = static_cast<int32_t>(word) >> 20; // Sign-extend 12 bits
    return imm;
}

int32_t Decoder::extract_s_imm(uint32_t word) noexcept {
    uint32_t imm_4_0  = (word >> 7) & 0x1F;
    uint32_t imm_11_5 = (word >> 25) & 0x7F;
    uint32_t imm12    = (imm_11_5 << 5) | imm_4_0;
    int32_t sign_ext  = (static_cast<int32_t>(imm12 << 20)) >> 20;
    return sign_ext;
}

int32_t Decoder::extract_b_imm(uint32_t word) noexcept {
    uint32_t bit11    = (word >> 7) & 0x1;
    uint32_t bits4_1  = (word >> 8) & 0xF;
    uint32_t bits10_5 = (word >> 25) & 0x3F;
    uint32_t bit12    = (word >> 31) & 0x1;

    uint32_t imm13 = (bit12 << 12) | (bit11 << 11) | (bits10_5 << 5) | (bits4_1 << 1);
    int32_t sign_ext = (static_cast<int32_t>(imm13 << 19)) >> 19;
    return sign_ext;
}

int32_t Decoder::extract_u_imm(uint32_t word) noexcept {
    // U-type immediate in upper 20 bits: inst[31:12] << 12
    return static_cast<int32_t>(word & 0xFFFFF000);
}

int32_t Decoder::extract_j_imm(uint32_t word) noexcept {
    uint32_t bits19_12 = (word >> 12) & 0xFF;
    uint32_t bit11     = (word >> 20) & 0x1;
    uint32_t bits10_1  = (word >> 21) & 0x3FF;
    uint32_t bit20     = (word >> 31) & 0x1;

    uint32_t imm21 = (bit20 << 20) | (bits19_12 << 12) | (bit11 << 11) | (bits10_1 << 1);
    int32_t sign_ext = (static_cast<int32_t>(imm21 << 11)) >> 11;
    return sign_ext;
}

DecodedInstruction Decoder::decode(uint32_t word, MachineProfile profile) noexcept {
    DecodedInstruction res{};
    res.raw_word = word;

    bool bare_profile_only = (profile == MachineProfile::AN32_BARE_V1);
    const auto* desc = lookup_descriptor_by_encoding(word, bare_profile_only);

    if (!desc) {
        // Check if it's a System-v1 instruction that is illegal under Bare-v1
        const auto* sys_desc = lookup_descriptor_by_encoding(word, false);
        if (sys_desc && !sys_desc->is_bare_profile && bare_profile_only) {
            res.mnemonic = sys_desc->mnemonic;
            res.format = sys_desc->format;
            res.status = DecodeStatus::ILLEGAL_PROFILE;
            return res;
        }

        uint8_t op = static_cast<uint8_t>(word & 0x7F);
        res.mnemonic = Mnemonic::UNKNOWN_ILLEGAL;
        if (is_known_opcode(op)) {
            res.status = DecodeStatus::ILLEGAL_FUNCT;
        } else {
            res.status = DecodeStatus::ILLEGAL_OPCODE;
        }
        return res;
    }

    res.mnemonic = desc->mnemonic;
    res.format = desc->format;
    res.uses_rd_cap = desc->uses_rd_cap;
    res.uses_rs1_cap = desc->uses_rs1_cap;
    res.uses_rs2_cap = desc->uses_rs2_cap;

    res.rd_idx  = static_cast<uint8_t>((word >> 7) & 0x1F);
    res.rs1_idx = static_cast<uint8_t>((word >> 15) & 0x1F);
    res.rs2_idx = static_cast<uint8_t>((word >> 20) & 0x1F);

    // Extract immediates based on format
    switch (desc->format) {
        case InstructionFormat::R:
            res.immediate = 0;
            break;
        case InstructionFormat::I:
            res.immediate = extract_i_imm(word);
            if (desc->has_funct7) {
                res.shamt = static_cast<uint8_t>((word >> 20) & 0x1F);
            }
            break;
        case InstructionFormat::S:
            res.immediate = extract_s_imm(word);
            break;
        case InstructionFormat::B:
            res.immediate = extract_b_imm(word);
            break;
        case InstructionFormat::U:
            res.immediate = extract_u_imm(word);
            break;
        case InstructionFormat::J:
            res.immediate = extract_j_imm(word);
            break;
        case InstructionFormat::CAP_R:
            res.immediate = 0;
            break;
        case InstructionFormat::CAP_MEM_I:
            res.immediate = extract_i_imm(word);
            break;
        case InstructionFormat::CAP_MEM_S:
            res.immediate = extract_s_imm(word);
            break;
        case InstructionFormat::SYSTEM_FIXED:
        case InstructionFormat::SYSTEM_R:
            res.immediate = 0;
            break;
    }

    // Strict register legality checks
    if (desc->uses_rs1_cap && res.rs1_idx >= 8) {
        res.status = DecodeStatus::ILLEGAL_REGISTER;
        return res;
    }
    if (desc->uses_rd_cap && res.rd_idx >= 8) {
        res.status = DecodeStatus::ILLEGAL_REGISTER;
        return res;
    }

    // Check for non-canonical ignored fields
    bool is_non_canonical = false;
    if (desc->mnemonic == Mnemonic::CCLEAR) {
        if (res.rs1_idx != 0 || res.rs2_idx != 0) {
            is_non_canonical = true;
        }
    } else if (desc->format == InstructionFormat::CAP_R && !desc->uses_rd_cap) {
        // CGETBASE, CGETLEN, CGETTAG, CGETPERM, CGETOFFSET ignore rs2
        if (res.rs2_idx != 0) {
            is_non_canonical = true;
        }
    }

    res.status = is_non_canonical ? DecodeStatus::NON_CANONICAL_IGNORED_FIELDS : DecodeStatus::CANONICAL;
    return res;
}

} // namespace an32
