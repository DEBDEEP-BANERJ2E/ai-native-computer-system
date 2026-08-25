#include <iostream>
#include <iomanip>
#include <string>
#include <vector>
#include "an32/encoder.hpp"
#include "an32/serialization.hpp"

using namespace an32;

int main(int argc, char* argv[]) {
    if (argc < 2) {
        std::cerr << "Usage: an32-encode <mnemonic> [operands...]\n";
        std::cerr << "Example: an32-encode add x1 x2 x3\n";
        std::cerr << "Example: an32-encode csetbounds c3 c1 x5\n";
        return 1;
    }

    std::string mnem_str = argv[1];
    auto mnem_opt = lookup_mnemonic_by_name(mnem_str);
    if (!mnem_opt) {
        std::cerr << "Error: Unknown mnemonic '" << mnem_str << "'\n";
        return 1;
    }

    Mnemonic m = *mnem_opt;
    const auto& desc = get_instruction_descriptor(m);

    Result<uint32_t> res = EncodeError::UNSUPPORTED_INSTRUCTION;

    try {
        if (desc.format == InstructionFormat::R) {
            if (argc != 5) { std::cerr << "Usage: " << mnem_str << " <rd> <rs1> <rs2>\n"; return 1; }
            auto rd = lookup_xreg_by_name(argv[2]);
            auto rs1 = lookup_xreg_by_name(argv[3]);
            auto rs2 = lookup_xreg_by_name(argv[4]);
            if (!rd || !rs1 || !rs2) { std::cerr << "Error: Invalid integer register\n"; return 1; }
            res = Encoder::encode_r(m, XReg(*rd), XReg(*rs1), XReg(*rs2));
        } else if (desc.format == InstructionFormat::I) {
            if (desc.has_funct7) {
                if (argc != 5) { std::cerr << "Usage: " << mnem_str << " <rd> <rs1> <shamt>\n"; return 1; }
                auto rd = lookup_xreg_by_name(argv[2]);
                auto rs1 = lookup_xreg_by_name(argv[3]);
                uint64_t shamt = std::stoull(argv[4], nullptr, 0);
                if (!rd || !rs1) { std::cerr << "Error: Invalid integer register\n"; return 1; }
                res = Encoder::encode_shift(m, XReg(*rd), XReg(*rs1), ShiftAmount5(static_cast<uint32_t>(shamt)));
            } else {
                if (argc != 5) { std::cerr << "Usage: " << mnem_str << " <rd> <rs1> <imm>\n"; return 1; }
                auto rd = lookup_xreg_by_name(argv[2]);
                auto rs1 = lookup_xreg_by_name(argv[3]);
                int64_t imm = std::stoll(argv[4], nullptr, 0);
                if (!rd || !rs1) { std::cerr << "Error: Invalid integer register\n"; return 1; }
                res = Encoder::encode_i(m, XReg(*rd), XReg(*rs1), IImm12(imm));
            }
        } else if (desc.format == InstructionFormat::S) {
            if (argc != 5) { std::cerr << "Usage: " << mnem_str << " <rs2> <rs1> <offset>\n"; return 1; }
            auto rs2 = lookup_xreg_by_name(argv[2]);
            auto rs1 = lookup_xreg_by_name(argv[3]);
            int64_t off = std::stoll(argv[4], nullptr, 0);
            if (!rs1 || !rs2) { std::cerr << "Error: Invalid integer register\n"; return 1; }
            res = Encoder::encode_s(m, XReg(*rs2), XReg(*rs1), SImm12(off));
        } else if (desc.format == InstructionFormat::B) {
            if (argc != 5) { std::cerr << "Usage: " << mnem_str << " <rs1> <rs2> <offset>\n"; return 1; }
            auto rs1 = lookup_xreg_by_name(argv[2]);
            auto rs2 = lookup_xreg_by_name(argv[3]);
            int64_t off = std::stoll(argv[4], nullptr, 0);
            if (!rs1 || !rs2) { std::cerr << "Error: Invalid integer register\n"; return 1; }
            res = Encoder::encode_b(m, XReg(*rs1), XReg(*rs2), BranchOffset13(off));
        } else if (desc.format == InstructionFormat::U) {
            if (argc != 4) { std::cerr << "Usage: " << mnem_str << " <rd> <imm20>\n"; return 1; }
            auto rd = lookup_xreg_by_name(argv[2]);
            uint64_t imm20 = std::stoull(argv[3], nullptr, 0);
            if (!rd) { std::cerr << "Error: Invalid integer register\n"; return 1; }
            res = Encoder::encode_u(m, XReg(*rd), UImm20(imm20));
        } else if (desc.format == InstructionFormat::J) {
            if (argc != 4) { std::cerr << "Usage: " << mnem_str << " <rd> <offset>\n"; return 1; }
            auto rd = lookup_xreg_by_name(argv[2]);
            int64_t off = std::stoll(argv[3], nullptr, 0);
            if (!rd) { std::cerr << "Error: Invalid integer register\n"; return 1; }
            res = Encoder::encode_j(XReg(*rd), JumpOffset21(off));
        } else if (desc.format == InstructionFormat::CAP_R) {
            if (m == Mnemonic::CCLEAR) {
                if (argc != 3) { std::cerr << "Usage: cclear <cd>\n"; return 1; }
                auto cd = lookup_capreg_by_name(argv[2]);
                if (!cd) { std::cerr << "Error: Invalid capability register\n"; return 1; }
                res = Encoder::encode_cclear(CapReg(*cd));
            } else if (desc.uses_rd_cap) {
                if (argc != 5) { std::cerr << "Usage: " << mnem_str << " <cd> <cs1> <rs2>\n"; return 1; }
                auto cd = lookup_capreg_by_name(argv[2]);
                auto cs1 = lookup_capreg_by_name(argv[3]);
                auto rs2 = lookup_xreg_by_name(argv[4]);
                if (!cd || !cs1) { std::cerr << "Error: Invalid capability register (must be c0..c7)\n"; return 1; }
                if (!rs2) { std::cerr << "Error: Invalid integer register\n"; return 1; }
                if (m == Mnemonic::CSETBOUNDS) res = Encoder::encode_csetbounds(CapReg(*cd), CapReg(*cs1), XReg(*rs2));
                else if (m == Mnemonic::CANDPERM) res = Encoder::encode_candperm(CapReg(*cd), CapReg(*cs1), XReg(*rs2));
                else if (m == Mnemonic::CINCOFFSET) res = Encoder::encode_cincoffset(CapReg(*cd), CapReg(*cs1), XReg(*rs2));
            } else {
                if (argc != 4) { std::cerr << "Usage: " << mnem_str << " <rd> <cs1>\n"; return 1; }
                auto rd = lookup_xreg_by_name(argv[2]);
                auto cs1 = lookup_capreg_by_name(argv[3]);
                if (!rd) { std::cerr << "Error: Invalid integer register\n"; return 1; }
                if (!cs1) { std::cerr << "Error: Invalid capability register\n"; return 1; }
                if (m == Mnemonic::CGETBASE) res = Encoder::encode_cgetbase(XReg(*rd), CapReg(*cs1));
                else if (m == Mnemonic::CGETLEN) res = Encoder::encode_cgetlen(XReg(*rd), CapReg(*cs1));
                else if (m == Mnemonic::CGETTAG) res = Encoder::encode_cgettag(XReg(*rd), CapReg(*cs1));
                else if (m == Mnemonic::CGETPERM) res = Encoder::encode_cgetperm(XReg(*rd), CapReg(*cs1));
                else if (m == Mnemonic::CGETOFFSET) res = Encoder::encode_cgetoffset(XReg(*rd), CapReg(*cs1));
            }
        } else if (desc.format == InstructionFormat::CAP_MEM_I) {
            if (argc != 5) { std::cerr << "Usage: " << mnem_str << " <rd> <cs1> <offset>\n"; return 1; }
            auto rd = lookup_xreg_by_name(argv[2]);
            auto cs1 = lookup_capreg_by_name(argv[3]);
            int32_t off = std::stol(argv[4], nullptr, 0);
            if (!rd) { std::cerr << "Error: Invalid integer register\n"; return 1; }
            if (!cs1) { std::cerr << "Error: Invalid capability register\n"; return 1; }
            res = Encoder::encode_cap_load(m, XReg(*rd), CapReg(*cs1), IImm12(off));
        } else if (desc.format == InstructionFormat::CAP_MEM_S) {
            if (argc != 5) { std::cerr << "Usage: " << mnem_str << " <rs2> <cs1> <offset>\n"; return 1; }
            auto rs2 = lookup_xreg_by_name(argv[2]);
            auto cs1 = lookup_capreg_by_name(argv[3]);
            int32_t off = std::stol(argv[4], nullptr, 0);
            if (!rs2) { std::cerr << "Error: Invalid integer register\n"; return 1; }
            if (!cs1) { std::cerr << "Error: Invalid capability register\n"; return 1; }
            res = Encoder::encode_cap_store(m, XReg(*rs2), CapReg(*cs1), SImm12(off));
        }
    } catch (const std::exception& e) {
        std::cerr << "Error parsing operands: " << e.what() << "\n";
        return 1;
    }

    if (res.is_err()) {
        std::cerr << "Encoding Error: " << to_string(res.error()) << "\n";
        return 1;
    }

    uint32_t word = res.value();
    auto bytes = serialize_le(word);

    std::cout << "Word:  0x" << std::hex << std::setw(8) << std::setfill('0') << std::uppercase << word << "\n";
    std::cout << "Bytes: ";
    for (uint8_t b : bytes) {
        std::cout << "0x" << std::hex << std::setw(2) << std::setfill('0') << std::uppercase << static_cast<int>(b) << " ";
    }
    std::cout << "\n";

    return 0;
}
