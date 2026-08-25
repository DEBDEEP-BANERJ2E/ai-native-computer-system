#!/usr/bin/env python3
"""
AN32 Deterministic ISA & ABI C++ Code Generator
===============================================
Parses:
  - spec/isa.yaml
  - spec/abi.yaml
  - spec/capability-abi.yaml

Generates:
  - toolchain/include/an32/isa_generated.hpp
  - toolchain/src/isa_generated.cpp

Supports `--check` mode to verify that generated files are in sync with specifications.
"""

import os
import sys
import yaml
import argparse
import subprocess

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(os.path.dirname(SCRIPT_DIR))
SPEC_DIR = os.path.join(PROJECT_ROOT, "spec")
INCLUDE_DIR = os.path.join(PROJECT_ROOT, "toolchain", "include", "an32")
SRC_DIR = os.path.join(PROJECT_ROOT, "toolchain", "src")

SPEC_PROVENANCE_TAG = "phase0-freeze-v1.0 (commit 43674bd)"

def compute_match_mask(inst):
    fmt = inst["format"]
    opcode = int(inst["opcode"], 16)
    funct3 = int(inst["funct3"], 16) if "funct3" in inst else 0
    funct7 = int(inst["funct7"], 16) if "funct7" in inst else 0

    match_val = opcode & 0x7F
    mask_val = 0x7F # Opcode bits [6:0]

    if fmt in ["R", "CAP_R"]:
        match_val |= (funct3 & 0x7) << 12
        match_val |= (funct7 & 0x7F) << 25
        mask_val  |= (0x7 << 12) | (0x7F << 25)
    elif fmt in ["I", "CAP_MEM_I"]:
        if "funct7" in inst: # SLLI, SRLI, SRAI
            match_val |= (funct3 & 0x7) << 12
            match_val |= (funct7 & 0x7F) << 25
            mask_val  |= (0x7 << 12) | (0x7F << 25)
        else:
            match_val |= (funct3 & 0x7) << 12
            mask_val  |= (0x7 << 12)
    elif fmt in ["S", "CAP_MEM_S", "B"]:
        match_val |= (funct3 & 0x7) << 12
        mask_val  |= (0x7 << 12)
    elif fmt in ["U", "J"]:
        pass
    elif fmt == "SYSTEM_FIXED":
        funct12 = int(inst["funct12"], 16) if "funct12" in inst else 0
        match_val |= (funct3 & 0x7) << 12
        match_val |= (funct12 & 0xFFF) << 20
        mask_val  |= (0x7 << 12) | (0xFFF << 20) | (0x1F << 7) | (0x1F << 15) # rd=0, rs1=0
    elif fmt == "SYSTEM_R":
        match_val |= (funct3 & 0x7) << 12
        match_val |= (funct7 & 0x7F) << 25
        mask_val  |= (0x7 << 12) | (0x7F << 25) | (0x1F << 7) # rd=0

    return match_val, mask_val

def generate_code(isa_data, abi_data, cap_data):
    bare_instructions = isa_data.get("instructions", [])
    future_instructions = isa_data.get("future_system_instructions", [])
    all_instructions = bare_instructions + future_instructions

    # Header generation
    hpp = []
    hpp.append("// ============================================================================")
    hpp.append("// GENERATED FILE - DO NOT EDIT MANUALLY")
    hpp.append(f"// Generated from Phase 0 Specifications: {SPEC_PROVENANCE_TAG}")
    hpp.append("// Generator: toolchain/tools/gen_isa_tables.py")
    hpp.append("// ============================================================================\n")
    hpp.append("#pragma once\n")
    hpp.append("#include <cstdint>")
    hpp.append("#include <string_view>")
    hpp.append("#include <array>")
    hpp.append("#include <optional>\n")
    hpp.append("namespace an32 {\n")

    # Mnemonic Enum
    hpp.append("enum class Mnemonic : uint16_t {")
    for inst in all_instructions:
        mnem = inst["mnemonic"].upper().replace(".", "_")
        hpp.append(f"    {mnem},")
    hpp.append("    UNKNOWN_ILLEGAL")
    hpp.append("};\n")

    # Instruction Format Enum (9 Bare-v1 formats + 2 System formats)
    hpp.append("enum class InstructionFormat : uint8_t {")
    hpp.append("    R,")
    hpp.append("    I,")
    hpp.append("    S,")
    hpp.append("    B,")
    hpp.append("    U,")
    hpp.append("    J,")
    hpp.append("    CAP_R,")
    hpp.append("    CAP_MEM_I,")
    hpp.append("    CAP_MEM_S,")
    hpp.append("    SYSTEM_FIXED,")
    hpp.append("    SYSTEM_R")
    hpp.append("};\n")

    # Descriptor Struct
    hpp.append("struct InstructionDescriptor {")
    hpp.append("    Mnemonic mnemonic;")
    hpp.append("    std::string_view name;")
    hpp.append("    InstructionFormat format;")
    hpp.append("    uint8_t opcode;")
    hpp.append("    uint8_t funct3;")
    hpp.append("    uint8_t funct7;")
    hpp.append("    uint16_t funct12;")
    hpp.append("    uint32_t match_val;")
    hpp.append("    uint32_t mask_val;")
    hpp.append("    bool is_bare_profile;")
    hpp.append("    bool has_funct3;")
    hpp.append("    bool has_funct7;")
    hpp.append("    bool has_funct12;")
    hpp.append("    uint8_t rd_constraint_max;  // 31 for XReg, 7 for CapReg, 0 for fixed zero")
    hpp.append("    uint8_t rs1_constraint_max; // 31 for XReg, 7 for CapReg, 0 for fixed zero")
    hpp.append("    uint8_t rs2_constraint_max; // 31 for XReg, 7 for CapReg, 0 for fixed zero")
    hpp.append("    bool uses_rd_cap;")
    hpp.append("    bool uses_rs1_cap;")
    hpp.append("    bool uses_rs2_cap;")
    hpp.append("};\n")

    hpp.append("inline constexpr size_t BARE_V1_INSTRUCTION_COUNT = 60;")
    hpp.append(f"inline constexpr size_t TOTAL_INSTRUCTION_COUNT = {len(all_instructions)};\n")
    hpp.append(f'inline constexpr std::string_view SPEC_PROVENANCE = "{SPEC_PROVENANCE_TAG}";\n')

    # Function declarations
    hpp.append("const InstructionDescriptor& get_instruction_descriptor(Mnemonic mnemonic) noexcept;")
    hpp.append("std::optional<Mnemonic> lookup_mnemonic_by_name(std::string_view name) noexcept;")
    hpp.append("const InstructionDescriptor* lookup_descriptor_by_encoding(uint32_t word, bool bare_profile_only = true) noexcept;")
    hpp.append("std::string_view get_mnemonic_name(Mnemonic mnemonic) noexcept;\n")

    # ABI register functions
    hpp.append("// Integer ABI Register Access (x0-x31)")
    hpp.append("std::string_view get_xreg_name(uint8_t reg_idx) noexcept;")
    hpp.append("std::string_view get_xreg_abi_name(uint8_t reg_idx) noexcept;")
    hpp.append("std::optional<uint8_t> lookup_xreg_by_name(std::string_view name) noexcept;\n")

    hpp.append("// Capability ABI Register Access (c0-c7)")
    hpp.append("std::string_view get_capreg_name(uint8_t reg_idx) noexcept;")
    hpp.append("std::string_view get_capreg_abi_name(uint8_t reg_idx) noexcept;")
    hpp.append("std::optional<uint8_t> lookup_capreg_by_name(std::string_view name) noexcept;\n")

    hpp.append("} // namespace an32\n")

    # Source generation
    cpp = []
    cpp.append("// ============================================================================")
    cpp.append("// GENERATED FILE - DO NOT EDIT MANUALLY")
    cpp.append(f"// Generated from Phase 0 Specifications: {SPEC_PROVENANCE_TAG}")
    cpp.append("// ============================================================================\n")
    cpp.append('#include "an32/isa_generated.hpp"\n')
    cpp.append("#include <cstring>\n")
    cpp.append("namespace an32 {\n")

    # Table of descriptors
    cpp.append("static constexpr std::array<InstructionDescriptor, TOTAL_INSTRUCTION_COUNT> INSTRUCTION_TABLE = {{")
    for inst in all_instructions:
        mnem_enum = "Mnemonic::" + inst["mnemonic"].upper().replace(".", "_")
        name = inst["mnemonic"]
        fmt = "InstructionFormat::" + inst["format"]
        opcode = int(inst["opcode"], 16)
        funct3 = int(inst["funct3"], 16) if "funct3" in inst else 0
        funct7 = int(inst["funct7"], 16) if "funct7" in inst else 0
        funct12 = int(inst["funct12"], 16) if "funct12" in inst else 0
        match_val, mask_val = compute_match_mask(inst)
        is_bare = "true" if inst in bare_instructions else "false"
        has_f3 = "true" if "funct3" in inst else "false"
        has_f7 = "true" if "funct7" in inst else "false"
        has_f12 = "true" if "funct12" in inst else "false"

        # Register constraints
        fmt_str = inst["format"]
        uses_rd_cap = "false"
        uses_rs1_cap = "false"
        uses_rs2_cap = "false"
        rd_max = 31
        rs1_max = 31
        rs2_max = 31

        if fmt_str == "CAP_R":
            uses_rs1_cap = "true"
            rs1_max = 7
            if name in ["csetbounds", "candperm", "cincoffset", "cclear"]:
                uses_rd_cap = "true"
                rd_max = 7
            else: # CGET*
                rd_max = 31
        elif fmt_str in ["CAP_MEM_I", "CAP_MEM_S"]:
            uses_rs1_cap = "true"
            rs1_max = 7

        if fmt_str in ["SYSTEM_FIXED", "SYSTEM_R"]:
            rd_max = 0
            if fmt_str == "SYSTEM_FIXED":
                rs1_max = 0
                rs2_max = 0

        cpp.append(f'    {{ {mnem_enum}, "{name}", {fmt}, 0x{opcode:02X}, 0x{funct3:X}, 0x{funct7:02X}, 0x{funct12:03X}, 0x{match_val:08X}, 0x{mask_val:08X}, {is_bare}, {has_f3}, {has_f7}, {has_f12}, {rd_max}, {rs1_max}, {rs2_max}, {uses_rd_cap}, {uses_rs1_cap}, {uses_rs2_cap} }},')
    cpp.append("}};\n")

    # get_instruction_descriptor
    cpp.append("const InstructionDescriptor& get_instruction_descriptor(Mnemonic mnemonic) noexcept {")
    cpp.append("    size_t idx = static_cast<size_t>(mnemonic);")
    cpp.append("    if (idx < TOTAL_INSTRUCTION_COUNT) {")
    cpp.append("        return INSTRUCTION_TABLE[idx];")
    cpp.append("    }")
    cpp.append("    static constexpr InstructionDescriptor UNKNOWN_DESC = {")
    cpp.append('        Mnemonic::UNKNOWN_ILLEGAL, "unknown", InstructionFormat::R, 0, 0, 0, 0, 0, 0, false, false, false, false, 0, 0, 0, false, false, false')
    cpp.append("    };")
    cpp.append("    return UNKNOWN_DESC;")
    cpp.append("}\n")

    # lookup_mnemonic_by_name
    cpp.append("std::optional<Mnemonic> lookup_mnemonic_by_name(std::string_view name) noexcept {")
    cpp.append("    for (const auto& desc : INSTRUCTION_TABLE) {")
    cpp.append("        if (desc.name == name) {")
    cpp.append("            return desc.mnemonic;")
    cpp.append("        }")
    cpp.append("    }")
    cpp.append("    return std::nullopt;")
    cpp.append("}\n")

    # lookup_descriptor_by_encoding
    cpp.append("const InstructionDescriptor* lookup_descriptor_by_encoding(uint32_t word, bool bare_profile_only) noexcept {")
    cpp.append("    for (const auto& desc : INSTRUCTION_TABLE) {")
    cpp.append("        if (bare_profile_only && !desc.is_bare_profile) {")
    cpp.append("            continue;")
    cpp.append("        }")
    cpp.append("        if ((word & desc.mask_val) == desc.match_val) {")
    cpp.append("            return &desc;")
    cpp.append("        }")
    cpp.append("    }")
    cpp.append("    return nullptr;")
    cpp.append("}\n")

    # get_mnemonic_name
    cpp.append("std::string_view get_mnemonic_name(Mnemonic mnemonic) noexcept {")
    cpp.append("    return get_instruction_descriptor(mnemonic).name;")
    cpp.append("}\n")

    # ABI registers
    xreg_names = [f"x{i}" for i in range(32)]
    xreg_abi_names = [r["abi_name"] for r in abi_data.get("registers", [])]

    cpp.append("static constexpr std::array<std::string_view, 32> XREG_NAMES = {{")
    for n in xreg_names:
        cpp.append(f'    "{n}",')
    cpp.append("}};\n")

    cpp.append("static constexpr std::array<std::string_view, 32> XREG_ABI_NAMES = {{")
    for n in xreg_abi_names:
        cpp.append(f'    "{n}",')
    cpp.append("}};\n")

    cpp.append("std::string_view get_xreg_name(uint8_t reg_idx) noexcept {")
    cpp.append("    return (reg_idx < 32) ? XREG_NAMES[reg_idx] : \"<invalid-x>\";")
    cpp.append("}\n")

    cpp.append("std::string_view get_xreg_abi_name(uint8_t reg_idx) noexcept {")
    cpp.append("    return (reg_idx < 32) ? XREG_ABI_NAMES[reg_idx] : \"<invalid-x>\";")
    cpp.append("}\n")

    cpp.append("std::optional<uint8_t> lookup_xreg_by_name(std::string_view name) noexcept {")
    cpp.append("    for (size_t i = 0; i < 32; ++i) {")
    cpp.append("        if (XREG_NAMES[i] == name || XREG_ABI_NAMES[i] == name) {")
    cpp.append("            return static_cast<uint8_t>(i);")
    cpp.append("        }")
    cpp.append("    }")
    cpp.append('    if (name == "fp") return static_cast<uint8_t>(8); // s0/fp alias')
    cpp.append("    return std::nullopt;")
    cpp.append("}\n")

    # Capability registers
    cap_names = [f"c{i}" for i in range(8)]
    cap_abi_names = [r["abi_name"] for r in cap_data.get("registers", [])]

    cpp.append("static constexpr std::array<std::string_view, 8> CAPREG_NAMES = {{")
    for n in cap_names:
        cpp.append(f'    "{n}",')
    cpp.append("}};\n")

    cpp.append("static constexpr std::array<std::string_view, 8> CAPREG_ABI_NAMES = {{")
    for n in cap_abi_names:
        cpp.append(f'    "{n}",')
    cpp.append("}};\n")

    cpp.append("std::string_view get_capreg_name(uint8_t reg_idx) noexcept {")
    cpp.append("    return (reg_idx < 8) ? CAPREG_NAMES[reg_idx] : \"<invalid-c>\";")
    cpp.append("}\n")

    cpp.append("std::string_view get_capreg_abi_name(uint8_t reg_idx) noexcept {")
    cpp.append("    return (reg_idx < 8) ? CAPREG_ABI_NAMES[reg_idx] : \"<invalid-c>\";")
    cpp.append("}\n")

    cpp.append("std::optional<uint8_t> lookup_capreg_by_name(std::string_view name) noexcept {")
    cpp.append("    for (size_t i = 0; i < 8; ++i) {")
    cpp.append("        if (CAPREG_NAMES[i] == name || CAPREG_ABI_NAMES[i] == name) {")
    cpp.append("            return static_cast<uint8_t>(i);")
    cpp.append("        }")
    cpp.append("    }")
    cpp.append("    return std::nullopt;")
    cpp.append("}\n")

    cpp.append("} // namespace an32\n")

    return "\n".join(hpp), "\n".join(cpp)

def main():
    parser = argparse.ArgumentParser(description="AN32 C++ ISA Table Generator")
    parser.add_argument("--check", action="store_true", help="Check if generated files are up to date without modifying")
    args = parser.parse_args()

    with open(os.path.join(SPEC_DIR, "isa.yaml"), "r") as f:
        isa_data = yaml.safe_load(f)
    with open(os.path.join(SPEC_DIR, "abi.yaml"), "r") as f:
        abi_data = yaml.safe_load(f)
    with open(os.path.join(SPEC_DIR, "capability-abi.yaml"), "r") as f:
        cap_data = yaml.safe_load(f)

    hpp_content, cpp_content = generate_code(isa_data, abi_data, cap_data)

    hpp_path = os.path.join(INCLUDE_DIR, "isa_generated.hpp")
    cpp_path = os.path.join(SRC_DIR, "isa_generated.cpp")

    if args.check:
        if not os.path.exists(hpp_path) or not os.path.exists(cpp_path):
            print("Generated files missing!")
            sys.exit(1)
        with open(hpp_path, "r") as f:
            if f.read() != hpp_content:
                print("isa_generated.hpp is out of date!")
                sys.exit(1)
        with open(cpp_path, "r") as f:
            if f.read() != cpp_content:
                print("isa_generated.cpp is out of date!")
                sys.exit(1)
        print("Generated files are in sync with specifications.")
        sys.exit(0)

    os.makedirs(INCLUDE_DIR, exist_ok=True)
    os.makedirs(SRC_DIR, exist_ok=True)

    with open(hpp_path, "w") as f:
        f.write(hpp_content)
    with open(cpp_path, "w") as f:
        f.write(cpp_content)

    print(f"Generated {hpp_path} and {cpp_path} from {SPEC_PROVENANCE_TAG}.")

if __name__ == "__main__":
    main()
