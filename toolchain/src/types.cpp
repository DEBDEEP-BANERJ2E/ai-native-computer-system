#include "an32/types.hpp"

namespace an32 {

std::string_view to_string(DecodeStatus status) noexcept {
    switch (status) {
        case DecodeStatus::CANONICAL:
            return "CANONICAL";
        case DecodeStatus::NON_CANONICAL_IGNORED_FIELDS:
            return "NON_CANONICAL_IGNORED_FIELDS";
        case DecodeStatus::ILLEGAL_OPCODE:
            return "ILLEGAL_OPCODE";
        case DecodeStatus::ILLEGAL_FUNCT:
            return "ILLEGAL_FUNCT";
        case DecodeStatus::ILLEGAL_REGISTER:
            return "ILLEGAL_REGISTER";
        case DecodeStatus::ILLEGAL_PROFILE:
            return "ILLEGAL_PROFILE";
        case DecodeStatus::UNKNOWN_INSTRUCTION:
        default:
            return "UNKNOWN_INSTRUCTION";
    }
}

std::string_view to_string(EncodeError error) noexcept {
    switch (error) {
        case EncodeError::OK:
            return "OK";
        case EncodeError::OUT_OF_RANGE_IMMEDIATE:
            return "OUT_OF_RANGE_IMMEDIATE";
        case EncodeError::MISALIGNED_BRANCH_OFFSET:
            return "MISALIGNED_BRANCH_OFFSET";
        case EncodeError::MISALIGNED_JUMP_OFFSET:
            return "MISALIGNED_JUMP_OFFSET";
        case EncodeError::INVALID_CAPABILITY_REGISTER:
            return "INVALID_CAPABILITY_REGISTER";
        case EncodeError::INVALID_INTEGER_REGISTER:
            return "INVALID_INTEGER_REGISTER";
        case EncodeError::INVALID_SHIFT_AMOUNT:
            return "INVALID_SHIFT_AMOUNT";
        case EncodeError::INVALID_OPERAND_COUNT:
            return "INVALID_OPERAND_COUNT";
        case EncodeError::UNSUPPORTED_INSTRUCTION:
        default:
            return "UNSUPPORTED_INSTRUCTION";
    }
}

} // namespace an32
