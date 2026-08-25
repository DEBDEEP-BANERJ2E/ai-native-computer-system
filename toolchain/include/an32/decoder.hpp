#pragma once

#include <cstdint>
#include "an32/types.hpp"
#include "an32/instruction.hpp"

namespace an32 {

class Decoder {
public:
    // Decodes an arbitrary 32-bit machine word under the specified machine profile
    static DecodedInstruction decode(uint32_t word, MachineProfile profile = MachineProfile::AN32_BARE_V1) noexcept;

private:
    static int32_t extract_i_imm(uint32_t word) noexcept;
    static int32_t extract_s_imm(uint32_t word) noexcept;
    static int32_t extract_b_imm(uint32_t word) noexcept;
    static int32_t extract_u_imm(uint32_t word) noexcept;
    static int32_t extract_j_imm(uint32_t word) noexcept;
};

} // namespace an32
