#include "an32/encoder.hpp"

namespace an32 {

Result<uint32_t> Encoder::encode_r(Mnemonic m, XReg rd, XReg rs1, XReg rs2) noexcept {
    if (!rd.is_valid() || !rs1.is_valid() || !rs2.is_valid()) {
        return EncodeError::INVALID_INTEGER_REGISTER;
    }
    const auto& desc = get_instruction_descriptor(m);
    if (desc.format != InstructionFormat::R) {
        return EncodeError::UNSUPPORTED_INSTRUCTION;
    }
    uint32_t word = (desc.opcode & 0x7F) |
                    ((rd.index & 0x1F) << 7) |
                    ((desc.funct3 & 0x7) << 12) |
                    ((rs1.index & 0x1F) << 15) |
                    ((rs2.index & 0x1F) << 20) |
                    ((desc.funct7 & 0x7F) << 25);
    return word;
}

Result<uint32_t> Encoder::encode_i(Mnemonic m, XReg rd, XReg rs1, IImm12 imm) noexcept {
    if (!rd.is_valid() || !rs1.is_valid()) {
        return EncodeError::INVALID_INTEGER_REGISTER;
    }
    if (!imm.is_valid()) {
        return EncodeError::OUT_OF_RANGE_IMMEDIATE;
    }
    const auto& desc = get_instruction_descriptor(m);
    if (desc.format != InstructionFormat::I || desc.has_funct7) {
        return EncodeError::UNSUPPORTED_INSTRUCTION;
    }
    uint32_t word = (desc.opcode & 0x7F) |
                    ((rd.index & 0x1F) << 7) |
                    ((desc.funct3 & 0x7) << 12) |
                    ((rs1.index & 0x1F) << 15) |
                    ((imm.encode_bits() & 0xFFF) << 20);
    return word;
}

Result<uint32_t> Encoder::encode_shift(Mnemonic m, XReg rd, XReg rs1, ShiftAmount5 shamt) noexcept {
    if (!rd.is_valid() || !rs1.is_valid()) {
        return EncodeError::INVALID_INTEGER_REGISTER;
    }
    if (!shamt.is_valid()) {
        return EncodeError::INVALID_SHIFT_AMOUNT;
    }
    const auto& desc = get_instruction_descriptor(m);
    if (desc.format != InstructionFormat::I || !desc.has_funct7) {
        return EncodeError::UNSUPPORTED_INSTRUCTION;
    }
    uint32_t word = (desc.opcode & 0x7F) |
                    ((rd.index & 0x1F) << 7) |
                    ((desc.funct3 & 0x7) << 12) |
                    ((rs1.index & 0x1F) << 15) |
                    ((shamt.value & 0x1F) << 20) |
                    ((desc.funct7 & 0x7F) << 25);
    return word;
}

Result<uint32_t> Encoder::encode_load(Mnemonic m, XReg rd, XReg rs1, IImm12 offset) noexcept {
    return encode_i(m, rd, rs1, offset);
}

Result<uint32_t> Encoder::encode_jalr(XReg rd, XReg rs1, IImm12 offset) noexcept {
    return encode_i(Mnemonic::JALR, rd, rs1, offset);
}

Result<uint32_t> Encoder::encode_s(Mnemonic m, XReg rs2, XReg rs1, SImm12 offset) noexcept {
    if (!rs1.is_valid() || !rs2.is_valid()) {
        return EncodeError::INVALID_INTEGER_REGISTER;
    }
    if (!offset.is_valid()) {
        return EncodeError::OUT_OF_RANGE_IMMEDIATE;
    }
    const auto& desc = get_instruction_descriptor(m);
    if (desc.format != InstructionFormat::S) {
        return EncodeError::UNSUPPORTED_INSTRUCTION;
    }
    uint32_t imm12 = offset.encode_bits();
    uint32_t imm_4_0 = imm12 & 0x1F;
    uint32_t imm_11_5 = (imm12 >> 5) & 0x7F;

    uint32_t word = (desc.opcode & 0x7F) |
                    (imm_4_0 << 7) |
                    ((desc.funct3 & 0x7) << 12) |
                    ((rs1.index & 0x1F) << 15) |
                    ((rs2.index & 0x1F) << 20) |
                    (imm_11_5 << 25);
    return word;
}

Result<uint32_t> Encoder::encode_b(Mnemonic m, XReg rs1, XReg rs2, BranchOffset13 offset) noexcept {
    if (!rs1.is_valid() || !rs2.is_valid()) {
        return EncodeError::INVALID_INTEGER_REGISTER;
    }
    if (offset.val < -4096 || offset.val > 4094) {
        return EncodeError::OUT_OF_RANGE_IMMEDIATE;
    }
    if ((offset.val % 2) != 0) {
        return EncodeError::MISALIGNED_BRANCH_OFFSET;
    }
    const auto& desc = get_instruction_descriptor(m);
    if (desc.format != InstructionFormat::B) {
        return EncodeError::UNSUPPORTED_INSTRUCTION;
    }
    uint32_t uoff = static_cast<uint32_t>(offset.val);
    uint32_t bit11    = (uoff >> 11) & 0x1;
    uint32_t bits4_1  = (uoff >> 1)  & 0xF;
    uint32_t bits10_5 = (uoff >> 5)  & 0x3F;
    uint32_t bit12    = (uoff >> 12) & 0x1;

    uint32_t word = (desc.opcode & 0x7F) |
                    (bit11 << 7) |
                    (bits4_1 << 8) |
                    ((desc.funct3 & 0x7) << 12) |
                    ((rs1.index & 0x1F) << 15) |
                    ((rs2.index & 0x1F) << 20) |
                    (bits10_5 << 25) |
                    (bit12 << 31);
    return word;
}

Result<uint32_t> Encoder::encode_u(Mnemonic m, XReg rd, UImm20 imm20) noexcept {
    if (!rd.is_valid()) {
        return EncodeError::INVALID_INTEGER_REGISTER;
    }
    if (!imm20.is_valid()) {
        return EncodeError::OUT_OF_RANGE_IMMEDIATE;
    }
    const auto& desc = get_instruction_descriptor(m);
    if (desc.format != InstructionFormat::U) {
        return EncodeError::UNSUPPORTED_INSTRUCTION;
    }
    uint32_t word = (desc.opcode & 0x7F) |
                    ((rd.index & 0x1F) << 7) |
                    ((imm20.imm20 & 0xFFFFF) << 12);
    return word;
}

Result<uint32_t> Encoder::encode_j(XReg rd, JumpOffset21 offset) noexcept {
    if (!rd.is_valid()) {
        return EncodeError::INVALID_INTEGER_REGISTER;
    }
    if (offset.val < -1048576 || offset.val > 1048574) {
        return EncodeError::OUT_OF_RANGE_IMMEDIATE;
    }
    if ((offset.val % 2) != 0) {
        return EncodeError::MISALIGNED_JUMP_OFFSET;
    }
    const auto& desc = get_instruction_descriptor(Mnemonic::JAL);
    uint32_t uoff = static_cast<uint32_t>(offset.val);
    uint32_t bits19_12 = (uoff >> 12) & 0xFF;
    uint32_t bit11     = (uoff >> 11) & 0x1;
    uint32_t bits10_1  = (uoff >> 1)  & 0x3FF;
    uint32_t bit20     = (uoff >> 20) & 0x1;

    uint32_t word = (desc.opcode & 0x7F) |
                    ((rd.index & 0x1F) << 7) |
                    (bits19_12 << 12) |
                    (bit11 << 20) |
                    (bits10_1 << 21) |
                    (bit20 << 31);
    return word;
}

// CapabilityLite Custom-0 Manipulation
Result<uint32_t> Encoder::encode_csetbounds(CapReg cd, CapReg cs1, XReg rs2) noexcept {
    if (!cd.is_valid() || !cs1.is_valid()) {
        return EncodeError::INVALID_CAPABILITY_REGISTER;
    }
    if (!rs2.is_valid()) {
        return EncodeError::INVALID_INTEGER_REGISTER;
    }
    const auto& desc = get_instruction_descriptor(Mnemonic::CSETBOUNDS);
    uint32_t word = (desc.opcode & 0x7F) |
                    ((cd.index & 0x7) << 7) |
                    ((desc.funct3 & 0x7) << 12) |
                    ((cs1.index & 0x7) << 15) |
                    ((rs2.index & 0x1F) << 20) |
                    ((desc.funct7 & 0x7F) << 25);
    return word;
}

Result<uint32_t> Encoder::encode_candperm(CapReg cd, CapReg cs1, XReg rs2) noexcept {
    if (!cd.is_valid() || !cs1.is_valid()) {
        return EncodeError::INVALID_CAPABILITY_REGISTER;
    }
    if (!rs2.is_valid()) {
        return EncodeError::INVALID_INTEGER_REGISTER;
    }
    const auto& desc = get_instruction_descriptor(Mnemonic::CANDPERM);
    uint32_t word = (desc.opcode & 0x7F) |
                    ((cd.index & 0x7) << 7) |
                    ((desc.funct3 & 0x7) << 12) |
                    ((cs1.index & 0x7) << 15) |
                    ((rs2.index & 0x1F) << 20) |
                    ((desc.funct7 & 0x7F) << 25);
    return word;
}

Result<uint32_t> Encoder::encode_cincoffset(CapReg cd, CapReg cs1, XReg rs2) noexcept {
    if (!cd.is_valid() || !cs1.is_valid()) {
        return EncodeError::INVALID_CAPABILITY_REGISTER;
    }
    if (!rs2.is_valid()) {
        return EncodeError::INVALID_INTEGER_REGISTER;
    }
    const auto& desc = get_instruction_descriptor(Mnemonic::CINCOFFSET);
    uint32_t word = (desc.opcode & 0x7F) |
                    ((cd.index & 0x7) << 7) |
                    ((desc.funct3 & 0x7) << 12) |
                    ((cs1.index & 0x7) << 15) |
                    ((rs2.index & 0x1F) << 20) |
                    ((desc.funct7 & 0x7F) << 25);
    return word;
}

Result<uint32_t> Encoder::encode_cgetbase(XReg rd, CapReg cs1) noexcept {
    if (!rd.is_valid()) return EncodeError::INVALID_INTEGER_REGISTER;
    if (!cs1.is_valid()) return EncodeError::INVALID_CAPABILITY_REGISTER;
    const auto& desc = get_instruction_descriptor(Mnemonic::CGETBASE);
    uint32_t word = (desc.opcode & 0x7F) |
                    ((rd.index & 0x1F) << 7) |
                    ((desc.funct3 & 0x7) << 12) |
                    ((cs1.index & 0x7) << 15) |
                    (0 << 20) | // canonical rs2 = 0
                    ((desc.funct7 & 0x7F) << 25);
    return word;
}

Result<uint32_t> Encoder::encode_cgetlen(XReg rd, CapReg cs1) noexcept {
    if (!rd.is_valid()) return EncodeError::INVALID_INTEGER_REGISTER;
    if (!cs1.is_valid()) return EncodeError::INVALID_CAPABILITY_REGISTER;
    const auto& desc = get_instruction_descriptor(Mnemonic::CGETLEN);
    uint32_t word = (desc.opcode & 0x7F) |
                    ((rd.index & 0x1F) << 7) |
                    ((desc.funct3 & 0x7) << 12) |
                    ((cs1.index & 0x7) << 15) |
                    (0 << 20) |
                    ((desc.funct7 & 0x7F) << 25);
    return word;
}

Result<uint32_t> Encoder::encode_cgettag(XReg rd, CapReg cs1) noexcept {
    if (!rd.is_valid()) return EncodeError::INVALID_INTEGER_REGISTER;
    if (!cs1.is_valid()) return EncodeError::INVALID_CAPABILITY_REGISTER;
    const auto& desc = get_instruction_descriptor(Mnemonic::CGETTAG);
    uint32_t word = (desc.opcode & 0x7F) |
                    ((rd.index & 0x1F) << 7) |
                    ((desc.funct3 & 0x7) << 12) |
                    ((cs1.index & 0x7) << 15) |
                    (0 << 20) |
                    ((desc.funct7 & 0x7F) << 25);
    return word;
}

Result<uint32_t> Encoder::encode_cgetperm(XReg rd, CapReg cs1) noexcept {
    if (!rd.is_valid()) return EncodeError::INVALID_INTEGER_REGISTER;
    if (!cs1.is_valid()) return EncodeError::INVALID_CAPABILITY_REGISTER;
    const auto& desc = get_instruction_descriptor(Mnemonic::CGETPERM);
    uint32_t word = (desc.opcode & 0x7F) |
                    ((rd.index & 0x1F) << 7) |
                    ((desc.funct3 & 0x7) << 12) |
                    ((cs1.index & 0x7) << 15) |
                    (0 << 20) |
                    ((desc.funct7 & 0x7F) << 25);
    return word;
}

Result<uint32_t> Encoder::encode_cgetoffset(XReg rd, CapReg cs1) noexcept {
    if (!rd.is_valid()) return EncodeError::INVALID_INTEGER_REGISTER;
    if (!cs1.is_valid()) return EncodeError::INVALID_CAPABILITY_REGISTER;
    const auto& desc = get_instruction_descriptor(Mnemonic::CGETOFFSET);
    uint32_t word = (desc.opcode & 0x7F) |
                    ((rd.index & 0x1F) << 7) |
                    ((desc.funct3 & 0x7) << 12) |
                    ((cs1.index & 0x7) << 15) |
                    (0 << 20) |
                    ((desc.funct7 & 0x7F) << 25);
    return word;
}

Result<uint32_t> Encoder::encode_cclear(CapReg cd) noexcept {
    if (!cd.is_valid()) return EncodeError::INVALID_CAPABILITY_REGISTER;
    const auto& desc = get_instruction_descriptor(Mnemonic::CCLEAR);
    uint32_t word = (desc.opcode & 0x7F) |
                    ((cd.index & 0x7) << 7) |
                    ((desc.funct3 & 0x7) << 12) |
                    (0 << 15) | // canonical cs1 = 0
                    (0 << 20) | // canonical rs2 = 0
                    ((desc.funct7 & 0x7F) << 25);
    return word;
}

// CapabilityLite Custom-1 Memory (Loads use CAP_MEM_I, Stores use CAP_MEM_S)
Result<uint32_t> Encoder::encode_cap_load(Mnemonic m, XReg rd, CapReg cs1, IImm12 offset) noexcept {
    if (!rd.is_valid()) return EncodeError::INVALID_INTEGER_REGISTER;
    if (!cs1.is_valid()) return EncodeError::INVALID_CAPABILITY_REGISTER;
    if (!offset.is_valid()) return EncodeError::OUT_OF_RANGE_IMMEDIATE;

    const auto& desc = get_instruction_descriptor(m);
    if (desc.format != InstructionFormat::CAP_MEM_I) {
        return EncodeError::UNSUPPORTED_INSTRUCTION;
    }
    uint32_t word = (desc.opcode & 0x7F) |
                    ((rd.index & 0x1F) << 7) |
                    ((desc.funct3 & 0x7) << 12) |
                    ((cs1.index & 0x7) << 15) |
                    ((offset.encode_bits() & 0xFFF) << 20);
    return word;
}

Result<uint32_t> Encoder::encode_cap_store(Mnemonic m, XReg rs2, CapReg cs1, SImm12 offset) noexcept {
    if (!rs2.is_valid()) return EncodeError::INVALID_INTEGER_REGISTER;
    if (!cs1.is_valid()) return EncodeError::INVALID_CAPABILITY_REGISTER;
    if (!offset.is_valid()) return EncodeError::OUT_OF_RANGE_IMMEDIATE;

    const auto& desc = get_instruction_descriptor(m);
    if (desc.format != InstructionFormat::CAP_MEM_S) {
        return EncodeError::UNSUPPORTED_INSTRUCTION;
    }
    uint32_t imm12 = offset.encode_bits();
    uint32_t imm_4_0 = imm12 & 0x1F;
    uint32_t imm_11_5 = (imm12 >> 5) & 0x7F;

    uint32_t word = (desc.opcode & 0x7F) |
                    (imm_4_0 << 7) |
                    ((desc.funct3 & 0x7) << 12) |
                    ((cs1.index & 0x7) << 15) |
                    ((rs2.index & 0x1F) << 20) |
                    (imm_11_5 << 25);
    return word;
}

} // namespace an32
