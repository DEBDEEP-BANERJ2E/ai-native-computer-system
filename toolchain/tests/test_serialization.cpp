#include <cassert>
#include <iostream>
#include "an32/serialization.hpp"

using namespace an32;

void test_little_endian_serialization() {
    uint32_t words[] = {
        0x00000000,
        0xFFFFFFFF,
        0x12345678,
        0x003100B3,
        0xFE512C23,
        0x0050818B,
        0x0101A52B
    };

    for (uint32_t w : words) {
        auto bytes = serialize_le(w);
        assert(bytes[0] == static_cast<uint8_t>(w & 0xFF));
        assert(bytes[1] == static_cast<uint8_t>((w >> 8) & 0xFF));
        assert(bytes[2] == static_cast<uint8_t>((w >> 16) & 0xFF));
        assert(bytes[3] == static_cast<uint8_t>((w >> 24) & 0xFF));

        uint32_t reconstructed = deserialize_le(bytes);
        assert(reconstructed == w);

        uint32_t reconstructed_ptr = deserialize_le(bytes.data());
        assert(reconstructed_ptr == w);
    }
}

int main() {
    std::cout << "[RUN] test_serialization\n";
    test_little_endian_serialization();
    std::cout << "[PASS] test_serialization passed successfully!\n";
    return 0;
}
