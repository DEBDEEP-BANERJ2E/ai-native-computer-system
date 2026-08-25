#pragma once

#include <cstdint>
#include <string_view>
#include <optional>
#include "an32/types.hpp"

namespace an32 {

struct XReg {
    uint32_t index{0};

    constexpr XReg() noexcept = default;
    constexpr explicit XReg(uint32_t idx) noexcept : index(idx) {}

    constexpr bool is_valid() const noexcept { return index < 32; }
    constexpr uint8_t raw_index() const noexcept { return static_cast<uint8_t>(index); }

    std::string_view name() const noexcept {
        return (index < 32) ? get_xreg_name(static_cast<uint8_t>(index)) : "<invalid-x>";
    }
    std::string_view abi_name() const noexcept {
        return (index < 32) ? get_xreg_abi_name(static_cast<uint8_t>(index)) : "<invalid-x>";
    }

    static constexpr std::optional<XReg> from_index(uint32_t idx) noexcept {
        if (idx < 32) return XReg(idx);
        return std::nullopt;
    }

    constexpr bool operator==(const XReg& other) const noexcept = default;
};

struct CapReg {
    uint32_t index{0};

    constexpr CapReg() noexcept = default;
    constexpr explicit CapReg(uint32_t idx) noexcept : index(idx) {}

    constexpr bool is_valid() const noexcept { return index < 8; }
    constexpr uint8_t raw_index() const noexcept { return static_cast<uint8_t>(index); }

    std::string_view name() const noexcept {
        return (index < 8) ? get_capreg_name(static_cast<uint8_t>(index)) : "<invalid-c>";
    }
    std::string_view abi_name() const noexcept {
        return (index < 8) ? get_capreg_abi_name(static_cast<uint8_t>(index)) : "<invalid-c>";
    }

    static constexpr std::optional<CapReg> from_index(uint32_t idx) noexcept {
        if (idx < 8) return CapReg(idx);
        return std::nullopt;
    }

    constexpr bool operator==(const CapReg& other) const noexcept = default;
};

// Strongly typed Immediate wrappers preventing accidental cross-format misinterpretation
struct IImm12 {
    int64_t val{0};

    constexpr IImm12() noexcept = default;
    constexpr explicit IImm12(int64_t v) noexcept : val(v) {}

    constexpr bool is_valid() const noexcept { return val >= -2048 && val <= 2047; }
    constexpr uint32_t encode_bits() const noexcept { return static_cast<uint32_t>(val) & 0xFFF; }

    static constexpr std::optional<IImm12> from_value(int64_t v) noexcept {
        if (v >= -2048 && v <= 2047) return IImm12(v);
        return std::nullopt;
    }
};

struct SImm12 {
    int64_t val{0};

    constexpr SImm12() noexcept = default;
    constexpr explicit SImm12(int64_t v) noexcept : val(v) {}

    constexpr bool is_valid() const noexcept { return val >= -2048 && val <= 2047; }
    constexpr uint32_t encode_bits() const noexcept { return static_cast<uint32_t>(val) & 0xFFF; }

    static constexpr std::optional<SImm12> from_value(int64_t v) noexcept {
        if (v >= -2048 && v <= 2047) return SImm12(v);
        return std::nullopt;
    }
};

struct BranchOffset13 {
    int64_t val{0};

    constexpr BranchOffset13() noexcept = default;
    constexpr explicit BranchOffset13(int64_t v) noexcept : val(v) {}

    constexpr bool is_valid() const noexcept {
        return val >= -4096 && val <= 4094 && (val % 2 == 0);
    }

    static constexpr std::optional<BranchOffset13> from_value(int64_t v) noexcept {
        if (v >= -4096 && v <= 4094 && (v % 2 == 0)) return BranchOffset13(v);
        return std::nullopt;
    }
};

struct JumpOffset21 {
    int64_t val{0};

    constexpr JumpOffset21() noexcept = default;
    constexpr explicit JumpOffset21(int64_t v) noexcept : val(v) {}

    constexpr bool is_valid() const noexcept {
        return val >= -1048576 && val <= 1048574 && (val % 2 == 0);
    }

    static constexpr std::optional<JumpOffset21> from_value(int64_t v) noexcept {
        if (v >= -1048576 && v <= 1048574 && (v % 2 == 0)) return JumpOffset21(v);
        return std::nullopt;
    }
};

// U-Type Immediate: holds the raw 20-bit value in bits [31:12]
// Effective hardware immediate = imm20 << 12
struct UImm20 {
    uint64_t imm20{0}; // 20-bit value (0..0xFFFFF)

    constexpr UImm20() noexcept = default;
    constexpr explicit UImm20(uint64_t v) noexcept : imm20(v) {}

    constexpr bool is_valid() const noexcept { return imm20 <= 0xFFFFF; }
    constexpr uint32_t effective_value() const noexcept { return static_cast<uint32_t>(imm20 << 12); }

    static constexpr std::optional<UImm20> from_value(uint64_t v) noexcept {
        if (v <= 0xFFFFF) return UImm20(v);
        return std::nullopt;
    }
};

struct ShiftAmount5 {
    uint32_t value{0};

    constexpr ShiftAmount5() noexcept = default;
    constexpr explicit ShiftAmount5(uint32_t s) noexcept : value(s) {}

    constexpr bool is_valid() const noexcept { return value <= 31; }
    constexpr uint8_t raw_value() const noexcept { return static_cast<uint8_t>(value); }

    static constexpr std::optional<ShiftAmount5> from_value(uint32_t v) noexcept {
        if (v <= 31) return ShiftAmount5(v);
        return std::nullopt;
    }
};

} // namespace an32
