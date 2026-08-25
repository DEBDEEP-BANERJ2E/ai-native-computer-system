// ============================================================================
// GENERATED FILE - DO NOT EDIT MANUALLY
// Generated from Phase 0 Specifications: phase0-freeze-v1.0 (commit 43674bd)
// Generator: toolchain/tools/gen_isa_tables.py
// ============================================================================

#pragma once

#include <cstdint>
#include <string_view>
#include <array>
#include <optional>

namespace an32 {

enum class Mnemonic : uint16_t {
    ADD,
    SUB,
    SLL,
    SLT,
    SLTU,
    XOR,
    SRL,
    SRA,
    OR,
    AND,
    ADDI,
    SLTI,
    SLTIU,
    XORI,
    ORI,
    ANDI,
    SLLI,
    SRLI,
    SRAI,
    LB,
    LH,
    LW,
    LBU,
    LHU,
    SB,
    SH,
    SW,
    BEQ,
    BNE,
    BLT,
    BGE,
    BLTU,
    BGEU,
    JAL,
    JALR,
    LUI,
    AUIPC,
    MUL,
    MULH,
    MULHSU,
    MULHU,
    DIV,
    DIVU,
    REM,
    REMU,
    CSETBOUNDS,
    CANDPERM,
    CINCOFFSET,
    CGETBASE,
    CGETLEN,
    CGETTAG,
    CGETPERM,
    CGETOFFSET,
    CCLEAR,
    CLB,
    CLH,
    CLW,
    CSB,
    CSH,
    CSW,
    ECALL,
    EBREAK,
    SRET,
    MRET,
    SFENCE_VMA,
    FENCE_I,
    UNKNOWN_ILLEGAL
};

enum class InstructionFormat : uint8_t {
    R,
    I,
    S,
    B,
    U,
    J,
    CAP_R,
    CAP_MEM_I,
    CAP_MEM_S,
    SYSTEM_FIXED,
    SYSTEM_R
};

struct InstructionDescriptor {
    Mnemonic mnemonic;
    std::string_view name;
    InstructionFormat format;
    uint8_t opcode;
    uint8_t funct3;
    uint8_t funct7;
    uint16_t funct12;
    uint32_t match_val;
    uint32_t mask_val;
    bool is_bare_profile;
    bool has_funct3;
    bool has_funct7;
    bool has_funct12;
    uint8_t rd_constraint_max;  // 31 for XReg, 7 for CapReg, 0 for fixed zero
    uint8_t rs1_constraint_max; // 31 for XReg, 7 for CapReg, 0 for fixed zero
    uint8_t rs2_constraint_max; // 31 for XReg, 7 for CapReg, 0 for fixed zero
    bool uses_rd_cap;
    bool uses_rs1_cap;
    bool uses_rs2_cap;
};

inline constexpr size_t BARE_V1_INSTRUCTION_COUNT = 60;
inline constexpr size_t TOTAL_INSTRUCTION_COUNT = 66;

inline constexpr std::string_view SPEC_PROVENANCE = "phase0-freeze-v1.0 (commit 43674bd)";

const InstructionDescriptor& get_instruction_descriptor(Mnemonic mnemonic) noexcept;
std::optional<Mnemonic> lookup_mnemonic_by_name(std::string_view name) noexcept;
const InstructionDescriptor* lookup_descriptor_by_encoding(uint32_t word, bool bare_profile_only = true) noexcept;
std::string_view get_mnemonic_name(Mnemonic mnemonic) noexcept;

// Integer ABI Register Access (x0-x31)
std::string_view get_xreg_name(uint8_t reg_idx) noexcept;
std::string_view get_xreg_abi_name(uint8_t reg_idx) noexcept;
std::optional<uint8_t> lookup_xreg_by_name(std::string_view name) noexcept;

// Capability ABI Register Access (c0-c7)
std::string_view get_capreg_name(uint8_t reg_idx) noexcept;
std::string_view get_capreg_abi_name(uint8_t reg_idx) noexcept;
std::optional<uint8_t> lookup_capreg_by_name(std::string_view name) noexcept;

} // namespace an32
