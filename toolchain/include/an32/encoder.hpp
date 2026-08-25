#pragma once

#include <cstdint>
#include "an32/types.hpp"
#include "an32/operands.hpp"

namespace an32 {

class Encoder {
public:
    // R-Type Encoder (Arithmetic/Logic/M-extension)
    static Result<uint32_t> encode_r(Mnemonic m, XReg rd, XReg rs1, XReg rs2) noexcept;

    // I-Type Encoders
    static Result<uint32_t> encode_i(Mnemonic m, XReg rd, XReg rs1, IImm12 imm) noexcept;
    static Result<uint32_t> encode_shift(Mnemonic m, XReg rd, XReg rs1, ShiftAmount5 shamt) noexcept;
    static Result<uint32_t> encode_load(Mnemonic m, XReg rd, XReg rs1, IImm12 offset) noexcept;
    static Result<uint32_t> encode_jalr(XReg rd, XReg rs1, IImm12 offset) noexcept;

    // S-Type Store Encoder
    static Result<uint32_t> encode_s(Mnemonic m, XReg rs2, XReg rs1, SImm12 offset) noexcept;

    // B-Type Conditional Branch Encoder
    static Result<uint32_t> encode_b(Mnemonic m, XReg rs1, XReg rs2, BranchOffset13 offset) noexcept;

    // U-Type Upper Immediate Encoders (LUI, AUIPC) - imm20 is bits [31:12]
    static Result<uint32_t> encode_u(Mnemonic m, XReg rd, UImm20 imm20) noexcept;

    // J-Type Jump and Link Encoder (JAL)
    static Result<uint32_t> encode_j(XReg rd, JumpOffset21 offset) noexcept;

    // CapabilityLite Custom-0 Manipulation Encoders (OP_CAP = 0x0B)
    static Result<uint32_t> encode_csetbounds(CapReg cd, CapReg cs1, XReg rs2) noexcept;
    static Result<uint32_t> encode_candperm(CapReg cd, CapReg cs1, XReg rs2) noexcept;
    static Result<uint32_t> encode_cincoffset(CapReg cd, CapReg cs1, XReg rs2) noexcept;
    static Result<uint32_t> encode_cgetbase(XReg rd, CapReg cs1) noexcept;
    static Result<uint32_t> encode_cgetlen(XReg rd, CapReg cs1) noexcept;
    static Result<uint32_t> encode_cgettag(XReg rd, CapReg cs1) noexcept;
    static Result<uint32_t> encode_cgetperm(XReg rd, CapReg cs1) noexcept;
    static Result<uint32_t> encode_cgetoffset(XReg rd, CapReg cs1) noexcept;
    static Result<uint32_t> encode_cclear(CapReg cd) noexcept;

    // CapabilityLite Custom-1 Memory Encoders (OP_CAP_MEM = 0x2B)
    static Result<uint32_t> encode_cap_load(Mnemonic m, XReg rd, CapReg cs1, IImm12 offset) noexcept;
    static Result<uint32_t> encode_cap_store(Mnemonic m, XReg rs2, CapReg cs1, SImm12 offset) noexcept;
};

} // namespace an32
