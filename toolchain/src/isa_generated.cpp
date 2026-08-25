// ============================================================================
// GENERATED FILE - DO NOT EDIT MANUALLY
// Generated from Phase 0 Specifications: phase0-freeze-v1.0 (commit 43674bd)
// ============================================================================

#include "an32/isa_generated.hpp"

#include <cstring>

namespace an32 {

static constexpr std::array<InstructionDescriptor, TOTAL_INSTRUCTION_COUNT> INSTRUCTION_TABLE = {{
    { Mnemonic::ADD, "add", InstructionFormat::R, 0x33, 0x0, 0x00, 0x000, 0x00000033, 0xFE00707F, true, true, true, false, 31, 31, 31, false, false, false },
    { Mnemonic::SUB, "sub", InstructionFormat::R, 0x33, 0x0, 0x20, 0x000, 0x40000033, 0xFE00707F, true, true, true, false, 31, 31, 31, false, false, false },
    { Mnemonic::SLL, "sll", InstructionFormat::R, 0x33, 0x1, 0x00, 0x000, 0x00001033, 0xFE00707F, true, true, true, false, 31, 31, 31, false, false, false },
    { Mnemonic::SLT, "slt", InstructionFormat::R, 0x33, 0x2, 0x00, 0x000, 0x00002033, 0xFE00707F, true, true, true, false, 31, 31, 31, false, false, false },
    { Mnemonic::SLTU, "sltu", InstructionFormat::R, 0x33, 0x3, 0x00, 0x000, 0x00003033, 0xFE00707F, true, true, true, false, 31, 31, 31, false, false, false },
    { Mnemonic::XOR, "xor", InstructionFormat::R, 0x33, 0x4, 0x00, 0x000, 0x00004033, 0xFE00707F, true, true, true, false, 31, 31, 31, false, false, false },
    { Mnemonic::SRL, "srl", InstructionFormat::R, 0x33, 0x5, 0x00, 0x000, 0x00005033, 0xFE00707F, true, true, true, false, 31, 31, 31, false, false, false },
    { Mnemonic::SRA, "sra", InstructionFormat::R, 0x33, 0x5, 0x20, 0x000, 0x40005033, 0xFE00707F, true, true, true, false, 31, 31, 31, false, false, false },
    { Mnemonic::OR, "or", InstructionFormat::R, 0x33, 0x6, 0x00, 0x000, 0x00006033, 0xFE00707F, true, true, true, false, 31, 31, 31, false, false, false },
    { Mnemonic::AND, "and", InstructionFormat::R, 0x33, 0x7, 0x00, 0x000, 0x00007033, 0xFE00707F, true, true, true, false, 31, 31, 31, false, false, false },
    { Mnemonic::ADDI, "addi", InstructionFormat::I, 0x13, 0x0, 0x00, 0x000, 0x00000013, 0x0000707F, true, true, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::SLTI, "slti", InstructionFormat::I, 0x13, 0x2, 0x00, 0x000, 0x00002013, 0x0000707F, true, true, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::SLTIU, "sltiu", InstructionFormat::I, 0x13, 0x3, 0x00, 0x000, 0x00003013, 0x0000707F, true, true, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::XORI, "xori", InstructionFormat::I, 0x13, 0x4, 0x00, 0x000, 0x00004013, 0x0000707F, true, true, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::ORI, "ori", InstructionFormat::I, 0x13, 0x6, 0x00, 0x000, 0x00006013, 0x0000707F, true, true, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::ANDI, "andi", InstructionFormat::I, 0x13, 0x7, 0x00, 0x000, 0x00007013, 0x0000707F, true, true, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::SLLI, "slli", InstructionFormat::I, 0x13, 0x1, 0x00, 0x000, 0x00001013, 0xFE00707F, true, true, true, false, 31, 31, 31, false, false, false },
    { Mnemonic::SRLI, "srli", InstructionFormat::I, 0x13, 0x5, 0x00, 0x000, 0x00005013, 0xFE00707F, true, true, true, false, 31, 31, 31, false, false, false },
    { Mnemonic::SRAI, "srai", InstructionFormat::I, 0x13, 0x5, 0x20, 0x000, 0x40005013, 0xFE00707F, true, true, true, false, 31, 31, 31, false, false, false },
    { Mnemonic::LB, "lb", InstructionFormat::I, 0x03, 0x0, 0x00, 0x000, 0x00000003, 0x0000707F, true, true, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::LH, "lh", InstructionFormat::I, 0x03, 0x1, 0x00, 0x000, 0x00001003, 0x0000707F, true, true, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::LW, "lw", InstructionFormat::I, 0x03, 0x2, 0x00, 0x000, 0x00002003, 0x0000707F, true, true, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::LBU, "lbu", InstructionFormat::I, 0x03, 0x4, 0x00, 0x000, 0x00004003, 0x0000707F, true, true, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::LHU, "lhu", InstructionFormat::I, 0x03, 0x5, 0x00, 0x000, 0x00005003, 0x0000707F, true, true, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::SB, "sb", InstructionFormat::S, 0x23, 0x0, 0x00, 0x000, 0x00000023, 0x0000707F, true, true, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::SH, "sh", InstructionFormat::S, 0x23, 0x1, 0x00, 0x000, 0x00001023, 0x0000707F, true, true, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::SW, "sw", InstructionFormat::S, 0x23, 0x2, 0x00, 0x000, 0x00002023, 0x0000707F, true, true, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::BEQ, "beq", InstructionFormat::B, 0x63, 0x0, 0x00, 0x000, 0x00000063, 0x0000707F, true, true, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::BNE, "bne", InstructionFormat::B, 0x63, 0x1, 0x00, 0x000, 0x00001063, 0x0000707F, true, true, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::BLT, "blt", InstructionFormat::B, 0x63, 0x4, 0x00, 0x000, 0x00004063, 0x0000707F, true, true, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::BGE, "bge", InstructionFormat::B, 0x63, 0x5, 0x00, 0x000, 0x00005063, 0x0000707F, true, true, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::BLTU, "bltu", InstructionFormat::B, 0x63, 0x6, 0x00, 0x000, 0x00006063, 0x0000707F, true, true, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::BGEU, "bgeu", InstructionFormat::B, 0x63, 0x7, 0x00, 0x000, 0x00007063, 0x0000707F, true, true, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::JAL, "jal", InstructionFormat::J, 0x6F, 0x0, 0x00, 0x000, 0x0000006F, 0x0000007F, true, false, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::JALR, "jalr", InstructionFormat::I, 0x67, 0x0, 0x00, 0x000, 0x00000067, 0x0000707F, true, true, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::LUI, "lui", InstructionFormat::U, 0x37, 0x0, 0x00, 0x000, 0x00000037, 0x0000007F, true, false, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::AUIPC, "auipc", InstructionFormat::U, 0x17, 0x0, 0x00, 0x000, 0x00000017, 0x0000007F, true, false, false, false, 31, 31, 31, false, false, false },
    { Mnemonic::MUL, "mul", InstructionFormat::R, 0x33, 0x0, 0x01, 0x000, 0x02000033, 0xFE00707F, true, true, true, false, 31, 31, 31, false, false, false },
    { Mnemonic::MULH, "mulh", InstructionFormat::R, 0x33, 0x1, 0x01, 0x000, 0x02001033, 0xFE00707F, true, true, true, false, 31, 31, 31, false, false, false },
    { Mnemonic::MULHSU, "mulhsu", InstructionFormat::R, 0x33, 0x2, 0x01, 0x000, 0x02002033, 0xFE00707F, true, true, true, false, 31, 31, 31, false, false, false },
    { Mnemonic::MULHU, "mulhu", InstructionFormat::R, 0x33, 0x3, 0x01, 0x000, 0x02003033, 0xFE00707F, true, true, true, false, 31, 31, 31, false, false, false },
    { Mnemonic::DIV, "div", InstructionFormat::R, 0x33, 0x4, 0x01, 0x000, 0x02004033, 0xFE00707F, true, true, true, false, 31, 31, 31, false, false, false },
    { Mnemonic::DIVU, "divu", InstructionFormat::R, 0x33, 0x5, 0x01, 0x000, 0x02005033, 0xFE00707F, true, true, true, false, 31, 31, 31, false, false, false },
    { Mnemonic::REM, "rem", InstructionFormat::R, 0x33, 0x6, 0x01, 0x000, 0x02006033, 0xFE00707F, true, true, true, false, 31, 31, 31, false, false, false },
    { Mnemonic::REMU, "remu", InstructionFormat::R, 0x33, 0x7, 0x01, 0x000, 0x02007033, 0xFE00707F, true, true, true, false, 31, 31, 31, false, false, false },
    { Mnemonic::CSETBOUNDS, "csetbounds", InstructionFormat::CAP_R, 0x0B, 0x0, 0x00, 0x000, 0x0000000B, 0xFE00707F, true, true, true, false, 7, 7, 31, true, true, false },
    { Mnemonic::CANDPERM, "candperm", InstructionFormat::CAP_R, 0x0B, 0x1, 0x00, 0x000, 0x0000100B, 0xFE00707F, true, true, true, false, 7, 7, 31, true, true, false },
    { Mnemonic::CINCOFFSET, "cincoffset", InstructionFormat::CAP_R, 0x0B, 0x2, 0x00, 0x000, 0x0000200B, 0xFE00707F, true, true, true, false, 7, 7, 31, true, true, false },
    { Mnemonic::CGETBASE, "cgetbase", InstructionFormat::CAP_R, 0x0B, 0x3, 0x00, 0x000, 0x0000300B, 0xFE00707F, true, true, true, false, 31, 7, 31, false, true, false },
    { Mnemonic::CGETLEN, "cgetlen", InstructionFormat::CAP_R, 0x0B, 0x4, 0x00, 0x000, 0x0000400B, 0xFE00707F, true, true, true, false, 31, 7, 31, false, true, false },
    { Mnemonic::CGETTAG, "cgettag", InstructionFormat::CAP_R, 0x0B, 0x5, 0x00, 0x000, 0x0000500B, 0xFE00707F, true, true, true, false, 31, 7, 31, false, true, false },
    { Mnemonic::CGETPERM, "cgetperm", InstructionFormat::CAP_R, 0x0B, 0x6, 0x00, 0x000, 0x0000600B, 0xFE00707F, true, true, true, false, 31, 7, 31, false, true, false },
    { Mnemonic::CGETOFFSET, "cgetoffset", InstructionFormat::CAP_R, 0x0B, 0x7, 0x00, 0x000, 0x0000700B, 0xFE00707F, true, true, true, false, 31, 7, 31, false, true, false },
    { Mnemonic::CCLEAR, "cclear", InstructionFormat::CAP_R, 0x0B, 0x7, 0x01, 0x000, 0x0200700B, 0xFE00707F, true, true, true, false, 7, 31, 31, true, false, false },
    { Mnemonic::CLB, "clb", InstructionFormat::CAP_MEM_I, 0x2B, 0x0, 0x00, 0x000, 0x0000002B, 0x0000707F, true, true, false, false, 31, 7, 31, false, true, false },
    { Mnemonic::CLH, "clh", InstructionFormat::CAP_MEM_I, 0x2B, 0x1, 0x00, 0x000, 0x0000102B, 0x0000707F, true, true, false, false, 31, 7, 31, false, true, false },
    { Mnemonic::CLW, "clw", InstructionFormat::CAP_MEM_I, 0x2B, 0x2, 0x00, 0x000, 0x0000202B, 0x0000707F, true, true, false, false, 31, 7, 31, false, true, false },
    { Mnemonic::CSB, "csb", InstructionFormat::CAP_MEM_S, 0x2B, 0x4, 0x00, 0x000, 0x0000402B, 0x0000707F, true, true, false, false, 31, 7, 31, false, true, false },
    { Mnemonic::CSH, "csh", InstructionFormat::CAP_MEM_S, 0x2B, 0x5, 0x00, 0x000, 0x0000502B, 0x0000707F, true, true, false, false, 31, 7, 31, false, true, false },
    { Mnemonic::CSW, "csw", InstructionFormat::CAP_MEM_S, 0x2B, 0x6, 0x00, 0x000, 0x0000602B, 0x0000707F, true, true, false, false, 31, 7, 31, false, true, false },
    { Mnemonic::ECALL, "ecall", InstructionFormat::SYSTEM_FIXED, 0x73, 0x0, 0x00, 0x000, 0x00000073, 0xFFFFFFFF, false, true, false, true, 0, 0, 0, false, false, false },
    { Mnemonic::EBREAK, "ebreak", InstructionFormat::SYSTEM_FIXED, 0x73, 0x0, 0x00, 0x001, 0x00100073, 0xFFFFFFFF, false, true, false, true, 0, 0, 0, false, false, false },
    { Mnemonic::SRET, "sret", InstructionFormat::SYSTEM_FIXED, 0x73, 0x0, 0x00, 0x102, 0x10200073, 0xFFFFFFFF, false, true, false, true, 0, 0, 0, false, false, false },
    { Mnemonic::MRET, "mret", InstructionFormat::SYSTEM_FIXED, 0x73, 0x0, 0x00, 0x302, 0x30200073, 0xFFFFFFFF, false, true, false, true, 0, 0, 0, false, false, false },
    { Mnemonic::SFENCE_VMA, "sfence.vma", InstructionFormat::SYSTEM_R, 0x73, 0x0, 0x09, 0x000, 0x12000073, 0xFE007FFF, false, true, true, false, 0, 31, 31, false, false, false },
    { Mnemonic::FENCE_I, "fence.i", InstructionFormat::SYSTEM_FIXED, 0x0F, 0x1, 0x00, 0x000, 0x0000100F, 0xFFFFFFFF, false, true, false, false, 0, 0, 0, false, false, false },
}};

const InstructionDescriptor& get_instruction_descriptor(Mnemonic mnemonic) noexcept {
    size_t idx = static_cast<size_t>(mnemonic);
    if (idx < TOTAL_INSTRUCTION_COUNT) {
        return INSTRUCTION_TABLE[idx];
    }
    static constexpr InstructionDescriptor UNKNOWN_DESC = {
        Mnemonic::UNKNOWN_ILLEGAL, "unknown", InstructionFormat::R, 0, 0, 0, 0, 0, 0, false, false, false, false, 0, 0, 0, false, false, false
    };
    return UNKNOWN_DESC;
}

std::optional<Mnemonic> lookup_mnemonic_by_name(std::string_view name) noexcept {
    for (const auto& desc : INSTRUCTION_TABLE) {
        if (desc.name == name) {
            return desc.mnemonic;
        }
    }
    return std::nullopt;
}

const InstructionDescriptor* lookup_descriptor_by_encoding(uint32_t word, bool bare_profile_only) noexcept {
    for (const auto& desc : INSTRUCTION_TABLE) {
        if (bare_profile_only && !desc.is_bare_profile) {
            continue;
        }
        if ((word & desc.mask_val) == desc.match_val) {
            return &desc;
        }
    }
    return nullptr;
}

bool is_known_opcode(uint8_t opcode) noexcept {
    for (const auto& desc : INSTRUCTION_TABLE) {
        if (desc.opcode == opcode) {
            return true;
        }
    }
    return false;
}

std::string_view get_mnemonic_name(Mnemonic mnemonic) noexcept {
    return get_instruction_descriptor(mnemonic).name;
}

static constexpr std::array<std::string_view, 32> XREG_NAMES = {{
    "x0",
    "x1",
    "x2",
    "x3",
    "x4",
    "x5",
    "x6",
    "x7",
    "x8",
    "x9",
    "x10",
    "x11",
    "x12",
    "x13",
    "x14",
    "x15",
    "x16",
    "x17",
    "x18",
    "x19",
    "x20",
    "x21",
    "x22",
    "x23",
    "x24",
    "x25",
    "x26",
    "x27",
    "x28",
    "x29",
    "x30",
    "x31",
}};

static constexpr std::array<std::string_view, 32> XREG_ABI_NAMES = {{
    "zero",
    "ra",
    "sp",
    "gp",
    "tp",
    "t0",
    "t1",
    "t2",
    "s0",
    "s1",
    "a0",
    "a1",
    "a2",
    "a3",
    "a4",
    "a5",
    "a6",
    "a7",
    "s2",
    "s3",
    "s4",
    "s5",
    "s6",
    "s7",
    "s8",
    "s9",
    "s10",
    "s11",
    "t3",
    "t4",
    "t5",
    "t6",
}};

std::string_view get_xreg_name(uint8_t reg_idx) noexcept {
    return (reg_idx < 32) ? XREG_NAMES[reg_idx] : "<invalid-x>";
}

std::string_view get_xreg_abi_name(uint8_t reg_idx) noexcept {
    return (reg_idx < 32) ? XREG_ABI_NAMES[reg_idx] : "<invalid-x>";
}

std::optional<uint8_t> lookup_xreg_by_name(std::string_view name) noexcept {
    for (size_t i = 0; i < 32; ++i) {
        if (XREG_NAMES[i] == name || XREG_ABI_NAMES[i] == name) {
            return static_cast<uint8_t>(i);
        }
    }
    if (name == "fp") return static_cast<uint8_t>(8); // s0/fp alias
    return std::nullopt;
}

static constexpr std::array<std::string_view, 8> CAPREG_NAMES = {{
    "c0",
    "c1",
    "c2",
    "c3",
    "c4",
    "c5",
    "c6",
    "c7",
}};

static constexpr std::array<std::string_view, 8> CAPREG_ABI_NAMES = {{
    "cnull",
    "cram",
    "cmmio",
    "ca0",
    "ca1",
    "ct0",
    "cs0",
    "cs1",
}};

std::string_view get_capreg_name(uint8_t reg_idx) noexcept {
    return (reg_idx < 8) ? CAPREG_NAMES[reg_idx] : "<invalid-c>";
}

std::string_view get_capreg_abi_name(uint8_t reg_idx) noexcept {
    return (reg_idx < 8) ? CAPREG_ABI_NAMES[reg_idx] : "<invalid-c>";
}

std::optional<uint8_t> lookup_capreg_by_name(std::string_view name) noexcept {
    for (size_t i = 0; i < 8; ++i) {
        if (CAPREG_NAMES[i] == name || CAPREG_ABI_NAMES[i] == name) {
            return static_cast<uint8_t>(i);
        }
    }
    return std::nullopt;
}

} // namespace an32
