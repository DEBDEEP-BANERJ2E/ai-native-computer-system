#pragma once

#include <cstdint>
#include <string_view>
#include "an32/types.hpp"
#include "an32/operands.hpp"

namespace an32 {

struct DecodedInstruction {
    uint32_t raw_word{0};
    Mnemonic mnemonic{Mnemonic::UNKNOWN_ILLEGAL};
    InstructionFormat format{InstructionFormat::R};
    DecodeStatus status{DecodeStatus::UNKNOWN_INSTRUCTION};

    uint8_t rd_idx{0};
    uint8_t rs1_idx{0};
    uint8_t rs2_idx{0};

    int32_t immediate{0};
    uint8_t shamt{0};

    bool uses_rd_cap{false};
    bool uses_rs1_cap{false};
    bool uses_rs2_cap{false};

    constexpr bool is_legal() const noexcept {
        return is_hardware_legal(status);
    }

    constexpr bool is_canonical() const noexcept {
        return status == DecodeStatus::CANONICAL;
    }

    std::string_view mnemonic_name() const noexcept {
        return get_mnemonic_name(mnemonic);
    }
};

} // namespace an32
