#pragma once

#include <string>
#include <cstdint>
#include "an32/instruction.hpp"

namespace an32 {

class Disassembler {
public:
    // Disassembles a decoded instruction into canonical assembly text
    static std::string disassemble(const DecodedInstruction& inst, bool use_abi_names = true);

    // Convenience function to decode and disassemble a 32-bit machine word directly
    static std::string disassemble(uint32_t word, MachineProfile profile = MachineProfile::AN32_BARE_V1, bool use_abi_names = true);
};

} // namespace an32
