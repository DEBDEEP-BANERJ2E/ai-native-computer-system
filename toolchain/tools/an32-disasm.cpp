#include <iostream>
#include <iomanip>
#include <fstream>
#include <string>
#include <vector>
#include "an32/decoder.hpp"
#include "an32/disasm.hpp"
#include "an32/serialization.hpp"

using namespace an32;

int main(int argc, char* argv[]) {
    if (argc < 2) {
        std::cerr << "Usage: an32-disasm [--numeric] <hex-word | binary-file>\n";
        std::cerr << "Example: an32-disasm 0x003100B3 0x00000013\n";
        std::cerr << "Example: an32-disasm binary.bin\n";
        return 1;
    }

    bool use_abi_names = true;
    std::vector<std::string> inputs;

    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if (arg == "--numeric") {
            use_abi_names = false;
        } else {
            inputs.push_back(arg);
        }
    }

    if (inputs.empty()) {
        std::cerr << "Error: No inputs provided\n";
        return 1;
    }

    // Check if single input is a binary file
    if (inputs.size() == 1 && inputs[0].rfind("0x", 0) != 0 && inputs[0].rfind("0X", 0) != 0) {
        std::ifstream file(inputs[0], std::ios::binary);
        if (file.is_open()) {
            std::vector<uint8_t> buffer((std::istreambuf_iterator<char>(file)), std::istreambuf_iterator<char>());
            uint32_t pc = 0;
            for (size_t i = 0; i + 4 <= buffer.size(); i += 4, pc += 4) {
                uint32_t word = deserialize_le(&buffer[i]);
                auto decoded = Decoder::decode(word);
                std::string asm_text = Disassembler::disassemble(decoded, use_abi_names);

                std::cout << std::hex << std::setw(8) << std::setfill('0') << std::uppercase << pc << ":\t"
                          << std::setw(8) << std::setfill('0') << word << "\t"
                          << asm_text << "\n";
            }
            return 0;
        }
    }

    // Process list of hex words
    uint32_t pc = 0;
    for (const auto& hex_str : inputs) {
        try {
            uint32_t word = std::stoul(hex_str, nullptr, 0);
            auto decoded = Decoder::decode(word);
            std::string asm_text = Disassembler::disassemble(decoded, use_abi_names);

            std::cout << std::hex << std::setw(8) << std::setfill('0') << std::uppercase << pc << ":\t"
                      << std::setw(8) << std::setfill('0') << word << "\t"
                      << asm_text << "\n";
            pc += 4;
        } catch (const std::exception& e) {
            std::cerr << "Error decoding '" << hex_str << "': " << e.what() << "\n";
        }
    }

    return 0;
}
