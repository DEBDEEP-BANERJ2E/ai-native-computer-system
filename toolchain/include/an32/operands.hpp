#pragma once

#include <cstdint>
#include <string_view>
#include "an32/types.hpp"

namespace an32 {

struct XReg {
    uint8_t index{0};

    constexpr XReg() noexcept = default;
    constexpr explicit XReg(uint8_t idx) noexcept : index(idx) {}

    constexpr bool is_valid() const noexcept { return index < 32; }
    std::string_view name() const noexcept { return get_xreg_name(index); }
    std::string_view abi_name() const noexcept { return get_xreg_abi_name(index); }

    constexpr bool operator==(const XReg& other) const noexcept = default;
};

struct CapReg {
    uint8_t index{0};

    constexpr CapReg() noexcept = default;
    constexpr explicit CapReg(uint8_t idx) noexcept : index(idx) {}

    constexpr bool is_valid() const noexcept { return index < 8; }
    std::string_view name() const noexcept { return get_capreg_name(index); }
    std::string_view abi_name() const noexcept { return get_capreg_abi_name(index); }

    constexpr bool operator==(const CapReg& other) const noexcept = default;
};

// Strongly typed Immediate wrappers preventing accidental cross-format misinterpretation
struct IImm12 {
    int32_t val{0};

    constexpr IImm12() noexcept = default;
    constexpr explicit IImm12(int32_t v) noexcept : val(v) {}

    constexpr bool is_valid() const noexcept { return val >= -2048 && val <= 2047; }
    constexpr uint32_t encode_bits() const noexcept { return static_cast<uint32_t>(val) & 0xFFF; }
};

struct SImm12 {
    int32_t val{0};

    constexpr SImm12() noexcept = default;
    constexpr explicit SImm12(int32_t v) noexcept : val(v) {}

    constexpr bool is_valid() const noexcept { return val >= -2048 && val <= 2047; }
    constexpr uint32_t encode_bits() const noexcept { return static_cast<uint32_t>(val) & 0xFFF; }
};

struct BranchOffset13 {
    int32_t val{0};

    constexpr BranchOffset13() noexcept = default;
    constexpr explicit BranchOffset13(int32_t v) noexcept : val(v) {}

    constexpr bool is_valid() const noexcept {
        return val >= -4096 && val <= 4094 && (val % 2 == 0);
    }
};

struct JumpOffset21 {
    int32_t val{0};

    constexpr JumpOffset21() noexcept = default;
    constexpr explicit JumpOffset21(int32_t v) noexcept : val(v) {}

    constexpr bool is_valid() const noexcept {
        return val >= -1048576 && val <= 1048574 && (val % 2 == 0);
    }
};

// U-Type Immediate: holds the raw 20-bit value in bits [31:12]
// Effective hardware immediate = imm20 << 12
struct UImm20 {
    uint32_t imm20{0}; // 20-bit value (0..0xFFFFF)

    constexpr UImm20() noexcept = default;
    constexpr explicit UImm20(uint32_t v) noexcept : imm20(v) {}

    constexpr bool is_valid() const noexcept { return imm20 <= 0xFFFFF; }
    constexpr uint32_t effective_value() const noexcept { return imm20 << 12; }
};

struct ShiftAmount5 {
    uint8_t shamt{0};

    constexpr ShiftAmount5() noexcept = default;
    constexpr explicit ShiftAmount5(uint8_t s) noexcept : shamt(s) {}

    constexpr bool is_valid() const noexcept { return shamt <= 31; }
};

} // namespace an32
