#pragma once

#include <cstdint>
#include <string_view>
#include <string>
#include <variant>
#include <optional>
#include <system_error>
#include "an32/isa_generated.hpp"

namespace an32 {

enum class MachineProfile : uint8_t {
    AN32_BARE_V1,   // Frozen Objective-2 Harvard core (60 instructions)
    AN32_SYSTEM_V1  // Forward privileged system target
};

enum class DecodeStatus : uint8_t {
    CANONICAL,                     // Valid and canonically encoded
    NON_CANONICAL_IGNORED_FIELDS,  // Hardware-accepted, but contains non-zero ignored bits
    ILLEGAL_OPCODE,                // Unknown / unsupported major opcode
    ILLEGAL_FUNCT,                 // Reserved funct3 / funct7 / funct12
    ILLEGAL_REGISTER,              // Capability register out of bounds (c8..c31) or fixed register violation
    ILLEGAL_PROFILE,               // Valid in future profile, but illegal in current profile
    UNKNOWN_INSTRUCTION            // Unrecognized bit pattern
};

inline constexpr bool is_hardware_legal(DecodeStatus status) noexcept {
    return status == DecodeStatus::CANONICAL || status == DecodeStatus::NON_CANONICAL_IGNORED_FIELDS;
}

enum class EncodeError : uint8_t {
    OK = 0,
    OUT_OF_RANGE_IMMEDIATE,
    MISALIGNED_BRANCH_OFFSET,
    MISALIGNED_JUMP_OFFSET,
    INVALID_CAPABILITY_REGISTER, // c8..c31 or non-capability where required
    INVALID_INTEGER_REGISTER,    // x32+
    INVALID_SHIFT_AMOUNT,        // shamt > 31
    INVALID_OPERAND_COUNT,
    UNSUPPORTED_INSTRUCTION
};

std::string_view to_string(DecodeStatus status) noexcept;
std::string_view to_string(EncodeError error) noexcept;

template <typename T>
class Result {
public:
    Result(T val) : data_(std::move(val)), error_(EncodeError::OK) {}
    Result(EncodeError err) : data_(std::nullopt), error_(err) {}

    bool is_ok() const noexcept { return error_ == EncodeError::OK; }
    bool is_err() const noexcept { return error_ != EncodeError::OK; }

    const T& value() const { return *data_; }
    T& value() { return *data_; }
    EncodeError error() const noexcept { return error_; }

    const T& operator*() const { return value(); }
    T* operator->() { return &value(); }

private:
    std::optional<T> data_;
    EncodeError error_;
};

} // namespace an32
