#include "an32/disasm.hpp"
#include "an32/decoder.hpp"
#include <sstream>
#include <iomanip>

namespace an32 {

std::string Disassembler::disassemble(uint32_t word, MachineProfile profile, bool use_abi_names) {
    auto decoded = Decoder::decode(word, profile);
    return disassemble(decoded, use_abi_names);
}

std::string Disassembler::disassemble(const DecodedInstruction& inst, bool use_abi_names) {
    if (!inst.is_legal()) {
        std::ostringstream ss;
        ss << ".word 0x" << std::hex << std::setw(8) << std::setfill('0') << std::uppercase << inst.raw_word;
        return ss.str();
    }

    auto xname = [use_abi_names](uint8_t idx) -> std::string_view {
        return use_abi_names ? get_xreg_abi_name(idx) : get_xreg_name(idx);
    };

    auto cname = [use_abi_names](uint8_t idx) -> std::string_view {
        return use_abi_names ? get_capreg_abi_name(idx) : get_capreg_name(idx);
    };

    std::ostringstream ss;
    ss << inst.mnemonic_name() << " ";

    switch (inst.format) {
        case InstructionFormat::R:
            ss << xname(inst.rd_idx) << ", " << xname(inst.rs1_idx) << ", " << xname(inst.rs2_idx);
            break;

        case InstructionFormat::I:
            if (inst.mnemonic == Mnemonic::SLLI || inst.mnemonic == Mnemonic::SRLI || inst.mnemonic == Mnemonic::SRAI) {
                ss << xname(inst.rd_idx) << ", " << xname(inst.rs1_idx) << ", " << static_cast<uint32_t>(inst.shamt);
            } else if (inst.mnemonic == Mnemonic::LB || inst.mnemonic == Mnemonic::LH || inst.mnemonic == Mnemonic::LW ||
                       inst.mnemonic == Mnemonic::LBU || inst.mnemonic == Mnemonic::LHU || inst.mnemonic == Mnemonic::JALR) {
                ss << xname(inst.rd_idx) << ", " << inst.immediate << "(" << xname(inst.rs1_idx) << ")";
            } else {
                ss << xname(inst.rd_idx) << ", " << xname(inst.rs1_idx) << ", " << inst.immediate;
            }
            break;

        case InstructionFormat::S:
            ss << xname(inst.rs2_idx) << ", " << inst.immediate << "(" << xname(inst.rs1_idx) << ")";
            break;

        case InstructionFormat::B:
            ss << xname(inst.rs1_idx) << ", " << xname(inst.rs2_idx) << ", " << inst.immediate;
            break;

        case InstructionFormat::U: {
            uint32_t raw_imm20 = (static_cast<uint32_t>(inst.immediate) >> 12) & 0xFFFFF;
            ss << xname(inst.rd_idx) << ", 0x" << std::hex << std::uppercase << raw_imm20;
            break;
        }

        case InstructionFormat::J:
            ss << xname(inst.rd_idx) << ", " << inst.immediate;
            break;

        case InstructionFormat::CAP_R:
            if (inst.mnemonic == Mnemonic::CCLEAR) {
                ss << cname(inst.rd_idx);
            } else if (inst.uses_rd_cap) { // csetbounds, candperm, cincoffset
                ss << cname(inst.rd_idx) << ", " << cname(inst.rs1_idx) << ", " << xname(inst.rs2_idx);
            } else { // cget*
                ss << xname(inst.rd_idx) << ", " << cname(inst.rs1_idx);
            }
            break;

        case InstructionFormat::CAP_MEM_I:
            ss << xname(inst.rd_idx) << ", " << inst.immediate << "(" << cname(inst.rs1_idx) << ")";
            break;

        case InstructionFormat::CAP_MEM_S:
            ss << xname(inst.rs2_idx) << ", " << inst.immediate << "(" << cname(inst.rs1_idx) << ")";
            break;

        case InstructionFormat::SYSTEM_FIXED:
            // ecall, ebreak, sret, mret, fence.i
            // No operands to print
            break;

        case InstructionFormat::SYSTEM_R:
            // sfence.vma rs1, rs2
            ss << xname(inst.rs1_idx) << ", " << xname(inst.rs2_idx);
            break;
    }

    return ss.str();
}

} // namespace an32
