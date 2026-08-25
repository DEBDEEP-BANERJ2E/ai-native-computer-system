#include <iostream>
#include <iomanip>
#include <string>
#include "an32/decoder.hpp"
#include "an32/disasm.hpp"

using namespace an32;

int main(int argc, char* argv[]) {
    if (argc < 2) {
        std::cerr << "Usage: an32-decode <32-bit-hex-word>\n";
        std::cerr << "Example: an32-decode 0x003100B3\n";
        return 1;
    }

    uint32_t word = 0;
    try {
        word = std::stoul(argv[1], nullptr, 0);
    } catch (const std::exception& e) {
        std::cerr << "Error parsing hex word: " << e.what() << "\n";
        return 1;
    }

    auto decoded = Decoder::decode(word);

    std::cout << "Word:        0x" << std::hex << std::setw(8) << std::setfill('0') << std::uppercase << word << "\n";
    std::cout << "Mnemonic:    " << decoded.mnemonic_name() << "\n";
    std::cout << "Status:      " << to_string(decoded.status) << "\n";
    std::cout << "Legal:       " << (decoded.is_legal() ? "YES" : "NO") << "\n";
    std::cout << "Canonical:   " << (decoded.is_canonical() ? "YES" : "NO") << "\n";
    std::cout << "Disassembly: " << Disassembler::disassemble(decoded, true) << "\n";

    std::cout << "Fields:\n";
    std::cout << "  rd:        " << (decoded.uses_rd_cap ? get_capreg_abi_name(decoded.rd_idx) : get_xreg_abi_name(decoded.rd_idx))
              << " (" << static_cast<int>(decoded.rd_idx) << ")\n";
    std::cout << "  rs1:       " << (decoded.uses_rs1_cap ? get_capreg_abi_name(decoded.rs1_idx) : get_xreg_abi_name(decoded.rs1_idx))
              << " (" << static_cast<int>(decoded.rs1_idx) << ")\n";
    std::cout << "  rs2:       " << get_xreg_abi_name(decoded.rs2_idx)
              << " (" << static_cast<int>(decoded.rs2_idx) << ")\n";
    std::cout << "  immediate: " << std::dec << decoded.immediate << " (0x" << std::hex << decoded.immediate << ")\n";

    return 0;
}
