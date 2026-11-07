#include "an32asm/fixup.hpp"
#include <sstream>

namespace an32asm {

const char* fixup_kind_to_string(FixupKind kind) noexcept {
    switch (kind) {
        case FixupKind::BRANCH:       return "R_AN32_BRANCH";
        case FixupKind::JAL:          return "R_AN32_JAL";
        case FixupKind::CALL:         return "R_AN32_CALL";
        case FixupKind::HI20:         return "R_AN32_HI20";
        case FixupKind::LO12_I:       return "R_AN32_LO12_I";
        case FixupKind::LO12_S:       return "R_AN32_LO12_S";
        case FixupKind::PCREL_HI20:   return "R_AN32_PCREL_HI20";
        case FixupKind::PCREL_LO12_I: return "R_AN32_PCREL_LO12_I";
        case FixupKind::PCREL_LO12_S: return "R_AN32_PCREL_LO12_S";
        case FixupKind::ABS32:        return "R_AN32_32";
        case FixupKind::ADD32:        return "R_AN32_ADD32";
        case FixupKind::SUB32:        return "R_AN32_SUB32";
    }
    return "R_AN32_UNKNOWN";
}

std::string Fixup::to_string() const {
    std::ostringstream ss;
    ss << "Fixup(kind=" << fixup_kind_to_string(kind)
       << ", sec=" << section_id
       << ", off=0x" << std::hex << offset << std::dec
       << ", sym='" << symbol_name << "'";
    if (addend != 0) {
        ss << ", addend=" << addend;
    }
    if (group_id.has_value()) {
        ss << ", group=" << *group_id;
    }
    if (anchor_offset.has_value()) {
        ss << ", anchor=0x" << std::hex << *anchor_offset << std::dec;
    }
    ss << ")";
    return ss.str();
}

} // namespace an32asm
