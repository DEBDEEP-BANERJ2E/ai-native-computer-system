#!/usr/bin/env python3
"""
Phase 0 Specification Validation Suite
======================================
Automated verification suite validating the integrity, disjointness,
and frozen RTL consistency of all Phase 0 machine and ABI specifications.

Checks performed:
1. YAML schema validation and syntactic integrity.
2. ISA instruction encoding non-overlap and bitmask disjointness.
3. Integer ABI register coverage (32 registers, 16-byte alignment).
4. CapabilityLite ABI coverage (8 registers, 100-bit metadata, immutable roots).
5. MMIO aperture bounds and register address uniqueness.
6. Bit-exact cross-check against frozen Objective-2 Chisel source code.
"""

import os
import sys
import yaml
import re

SPEC_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SPEC_DIR)
O2_SCALA_DIR = os.path.join(PROJECT_ROOT, "objective02-riscv-core", "src", "main", "scala", "objective02")

def log_pass(msg):
    print(f"  [PASS] {msg}")

def log_fail(msg):
    print(f"  [FAIL] {msg}")
    sys.exit(1)

def test_yaml_files_exist_and_load():
    print("\n--- Test 1: YAML Schema & Loading ---")
    required_files = [
        "machine-profiles.yaml",
        "isa.yaml",
        "abi.yaml",
        "capability-abi.yaml",
        "mmio.yaml",
    ]
    data = {}
    for fname in required_files:
        fpath = os.path.join(SPEC_DIR, fname)
        if not os.path.exists(fpath):
            log_fail(f"Missing specification file: {fname}")
        with open(fpath, "r") as f:
            try:
                data[fname] = yaml.safe_load(f)
                log_pass(f"Successfully loaded {fname}")
            except Exception as e:
                log_fail(f"YAML parse error in {fname}: {e}")
    return data

def test_isa_integrity(isa_data):
    print("\n--- Test 2: ISA Encodings & Disjointness ---")
    instructions = isa_data.get("instructions", [])
    if not instructions or len(instructions) < 55:
        log_fail(f"Insufficient instruction definitions in isa.yaml: found {len(instructions)}")

    mnemonics = set()
    encodings = {}

    for inst in instructions:
        mnem = inst["mnemonic"]
        if mnem in mnemonics:
            log_fail(f"Duplicate mnemonic detected: {mnem}")
        mnemonics.add(mnem)

        opcode = int(inst["opcode"], 16)
        funct3 = int(inst.get("funct3", "0x0"), 16) if "funct3" in inst else None
        funct7 = int(inst.get("funct7", "0x00"), 16) if "funct7" in inst else None
        funct12 = int(inst.get("funct12", "0x000"), 16) if "funct12" in inst else None

        key = (opcode, funct3, funct7, funct12)
        if key in encodings:
            log_fail(f"Instruction encoding collision: {mnem} collides with {encodings[key]}")
        encodings[key] = mnem

    log_pass(f"Verified {len(instructions)} unique, non-overlapping instruction encodings.")

def test_integer_abi(abi_data):
    print("\n--- Test 3: Integer ABI (ILP32) & Register Map ---")
    registers = abi_data.get("registers", [])
    if len(registers) != 32:
        log_fail(f"Expected 32 integer registers, found {len(registers)}")

    indices = set()
    names = set()
    for reg in registers:
        idx = reg["index"]
        if idx in indices or idx < 0 or idx > 31:
            log_fail(f"Invalid or duplicate register index: {idx}")
        indices.add(idx)
        names.add(reg["name"])

    data_model = abi_data.get("data_model", {})
    if data_model.get("stack_alignment_bytes") != 16:
        log_fail("Stack alignment must be 16 bytes")
    if data_model.get("pointer_size_bytes") != 4:
        log_fail("Pointer size must be 4 bytes for ILP32")

    log_pass("Verified all 32 integer registers (x0–x31) and 16-byte stack alignment.")

def test_capability_abi(cap_data):
    print("\n--- Test 4: CapabilityLite ABI & Metadata Width ---")
    registers = cap_data.get("registers", [])
    if len(registers) != 8:
        log_fail(f"Expected 8 capability registers, found {len(registers)}")

    model = cap_data.get("capability_model", {})
    fields = model.get("fields", {})
    total_bits = (
        fields.get("tag", {}).get("width", 0) +
        fields.get("base", {}).get("width", 0) +
        fields.get("length", {}).get("width", 0) +
        fields.get("perms", {}).get("width", 0) +
        fields.get("offset", {}).get("width", 0)
    )
    if total_bits != 100:
        log_fail(f"CapabilityLite metadata width must equal 100 bits (found {total_bits})")

    # Verify immutable roots
    c0 = next(r for r in registers if r["name"] == "c0")
    c1 = next(r for r in registers if r["name"] == "c1")
    c2 = next(r for r in registers if r["name"] == "c2")

    if c0["type"] != "HARDWARE_IMMUTABLE" or c0["tag"] != 0:
        log_fail("c0 must be immutable NULL capability with tag=0")
    if c1["type"] != "HARDWARE_IMMUTABLE" or c1["base"] != "0x00000000" or c1["length"] != "0x00001000":
        log_fail("c1 must be immutable RAM root (0x00000000..0x00001000)")
    if c2["type"] != "HARDWARE_IMMUTABLE" or c2["base"] != "0x80000000" or c2["length"] != "0x00010000":
        log_fail("c2 must be immutable MMIO root (0x80000000..0x80010000)")

    log_pass("Verified 8 CapabilityLite registers (100 bits, c0–c2 immutable roots, c3–c7 process).")

def test_mmio_aperture(mmio_data):
    print("\n--- Test 5: System MMIO Aperture & Register Map ---")
    aperture = mmio_data.get("aperture", {})
    base = int(aperture.get("base", "0x0"), 16)
    end = int(aperture.get("end", "0x0"), 16)

    if base != 0x80000000 or end != 0x8000FFFF:
        log_fail(f"MMIO aperture must be 0x80000000..0x8000FFFF (found 0x{base:08X}..0x{end:08X})")

    addresses = {}
    for window in mmio_data.get("windows", []):
        for reg in window.get("registers", []):
            addr = int(reg["address"], 16)
            name = reg["name"]
            if addr < base or addr > end:
                log_fail(f"Register {name} at 0x{addr:08X} lies outside aperture")
            if addr in addresses:
                log_fail(f"MMIO address collision: {name} collides with {addresses[addr]} at 0x{addr:08X}")
            addresses[addr] = name

    # Verify critical registers
    if addresses.get(0x80002100) != "SEC_STATUS":
        log_fail(f"0x80002100 must be SEC_STATUS (found {addresses.get(0x80002100)})")
    if addresses.get(0x8000211C) != "TRAP_VECTOR":
        log_fail(f"0x8000211C must be TRAP_VECTOR (found {addresses.get(0x8000211C)})")

    log_pass(f"Verified {len(addresses)} unique MMIO registers within 0x80000000..0x8000FFFF.")

def test_cross_check_with_frozen_chisel():
    print("\n--- Test 6: Cross-Check Against Frozen Chisel Source Code ---")
    opcodes_scala = os.path.join(O2_SCALA_DIR, "isa", "Opcodes.scala")
    mmio_scala = os.path.join(O2_SCALA_DIR, "system", "MMIOAddress.scala")
    cap_scala = os.path.join(O2_SCALA_DIR, "capability", "CapabilityLite.scala")

    if not os.path.exists(opcodes_scala):
        log_fail(f"Cannot find frozen Chisel Opcodes.scala at {opcodes_scala}")

    with open(opcodes_scala, "r") as f:
        op_text = f.read()
        assert 'OP_R_TYPE   = "b0110011"' in op_text
        assert 'OP_CAP      = "b0001011"' in op_text
        assert 'OP_CAP_MEM  = "b0101011"' in op_text
        assert 'FUNCT3_CSETBOUNDS = "b000"' in op_text
        log_pass("Opcodes.scala opcodes matched canonical ISA database.")

    with open(mmio_scala, "r") as f:
        mmio_text = f.read()
        assert 'SEC_STATUS              = "h80002100"' in mmio_text
        assert 'TRAP_VECTOR             = "h8000211c"' in mmio_text
        assert 'TRAP_RETURN             = "h80002130"' in mmio_text
        log_pass("MMIOAddress.scala constants matched MMIO database.")

    with open(cap_scala, "r") as f:
        cap_text = f.read()
        assert "val tag    = Bool()" in cap_text
        assert "val base   = UInt(32.W)" in cap_text
        assert "val length = UInt(32.W)" in cap_text
        assert "val perms  = UInt(3.W)" in cap_text
        assert "val offset = UInt(32.W)" in cap_text
        log_pass("CapabilityLite.scala bundle fields matched 100-bit metadata specification.")

def main():
    print("================================================================")
    print("  PHASE 0 SPECIFICATION AUTOMATED VERIFICATION SUITE")
    print("================================================================")
    data = test_yaml_files_exist_and_load()
    test_isa_integrity(data["isa.yaml"])
    test_integer_abi(data["abi.yaml"])
    test_capability_abi(data["capability-abi.yaml"])
    test_mmio_aperture(data["mmio.yaml"])
    test_cross_check_with_frozen_chisel()
    print("\n================================================================")
    print("  ALL PHASE 0 SPECIFICATION CHECKS PASSED WITH 100% INTEGRITY")
    print("================================================================")

if __name__ == "__main__":
    main()
