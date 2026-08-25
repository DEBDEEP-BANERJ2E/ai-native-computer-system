#pragma once

#include <cstdint>
#include <array>
#include <span>

namespace an32 {

// Serializes a 32-bit word into 4 bytes in little-endian order
inline constexpr std::array<uint8_t, 4> serialize_le(uint32_t word) noexcept {
    return {
        static_cast<uint8_t>(word & 0xFF),
        static_cast<uint8_t>((word >> 8) & 0xFF),
        static_cast<uint8_t>((word >> 16) & 0xFF),
        static_cast<uint8_t>((word >> 24) & 0xFF)
    };
}

// Deserializes 4 little-endian bytes into a 32-bit word
inline constexpr uint32_t deserialize_le(const std::array<uint8_t, 4>& bytes) noexcept {
    return static_cast<uint32_t>(bytes[0]) |
          (static_cast<uint32_t>(bytes[1]) << 8) |
          (static_cast<uint32_t>(bytes[2]) << 16) |
          (static_cast<uint32_t>(bytes[3]) << 24);
}

inline constexpr uint32_t deserialize_le(const uint8_t* bytes) noexcept {
    return static_cast<uint32_t>(bytes[0]) |
          (static_cast<uint32_t>(bytes[1]) << 8) |
          (static_cast<uint32_t>(bytes[2]) << 16) |
          (static_cast<uint32_t>(bytes[3]) << 24);
}

} // namespace an32
