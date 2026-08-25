"""
RV32IM + CapabilityLite Assembler & Disassembler.
Supports standard RV32I, RV32M extensions, and Objective 2 CapabilityLite custom instructions (Custom-0 & Custom-1).
"""

import re
from typing import List, Tuple, Dict, Any, Optional

# Register mapping
GPR_MAP = {
    f"x{i}": i for i in range(32)
}
ABI_MAP = {
    "zero": 0, "ra": 1, "sp": 2, "gp": 3, "tp": 4,
    "t0": 5, "t1": 6, "t2": 7, "s0": 8, "fp": 8, "s1": 9,
    "a0": 10, "a1": 11, "a2": 12, "a3": 13, "a4": 14, "a5": 15, "a6": 16, "a7": 17,
    "s2": 18, "s3": 19, "s4": 20, "s5": 21, "s6": 22, "s7": 23, "s8": 24, "s9": 25, "s10": 26, "s11": 27,
    "t3": 28, "t4": 29, "t5": 30, "t6": 31
}
GPR_MAP.update(ABI_MAP)

CAP_MAP = {
    f"c{i}": i for i in range(8)
}


def parse_reg(reg_str: str) -> int:
    reg_str = reg_str.strip().lower()
    if reg_str in GPR_MAP:
        return GPR_MAP[reg_str]
    raise ValueError(f"Unknown GPR register: {reg_str}")


def parse_cap_reg(cap_str: str) -> int:
    cap_str = cap_str.strip().lower()
    if cap_str in CAP_MAP:
        return CAP_MAP[cap_str]
    raise ValueError(f"Unknown Capability register: {cap_str} (must be c0..c7)")


def parse_imm(imm_str: str) -> int:
    imm_str = imm_str.strip()
    if imm_str.startswith("0x") or imm_str.startswith("0X"):
        return int(imm_str, 16)
    elif imm_str.startswith("0b") or imm_str.startswith("0B"):
        return int(imm_str, 2)
    else:
        return int(imm_str, 10)


def encode_r(funct7: int, rs2: int, rs1: int, funct3: int, rd: int, opcode: int) -> int:
    return (((funct7 & 0x7F) << 25) | ((rs2 & 0x1F) << 20) | ((rs1 & 0x1F) << 15) |
            ((funct3 & 0x7) << 12) | ((rd & 0x1F) << 7) | (opcode & 0x7F)) & 0xFFFFFFFF


def encode_i(imm: int, rs1: int, funct3: int, rd: int, opcode: int) -> int:
    imm12 = imm & 0xFFF
    return (((imm12 & 0xFFF) << 20) | ((rs1 & 0x1F) << 15) |
            ((funct3 & 0x7) << 12) | ((rd & 0x1F) << 7) | (opcode & 0x7F)) & 0xFFFFFFFF


def encode_s(imm: int, rs2: int, rs1: int, funct3: int, opcode: int) -> int:
    imm12 = imm & 0xFFF
    imm11_5 = (imm12 >> 5) & 0x7F
    imm4_0 = imm12 & 0x1F
    return ((imm11_5 << 25) | ((rs2 & 0x1F) << 20) | ((rs1 & 0x1F) << 15) |
            ((funct3 & 0x7) << 12) | (imm4_0 << 7) | (opcode & 0x7F)) & 0xFFFFFFFF


def encode_b(imm: int, rs2: int, rs1: int, funct3: int, opcode: int) -> int:
    imm13 = imm & 0x1FFF
    b12 = (imm13 >> 12) & 0x1
    b11 = (imm13 >> 11) & 0x1
    b10_5 = (imm13 >> 5) & 0x3F
    b4_1 = (imm13 >> 1) & 0xF
    return ((b12 << 31) | (b10_5 << 25) | ((rs2 & 0x1F) << 20) | ((rs1 & 0x1F) << 15) |
            ((funct3 & 0x7) << 12) | (b4_1 << 8) | (b11 << 7) | (opcode & 0x7F)) & 0xFFFFFFFF


def encode_u(imm20: int, rd: int, opcode: int) -> int:
    return (((imm20 & 0xFFFFF) << 12) | ((rd & 0x1F) << 7) | (opcode & 0x7F)) & 0xFFFFFFFF


def encode_j(imm: int, rd: int, opcode: int) -> int:
    imm21 = imm & 0x1FFFFF
    j20 = (imm21 >> 20) & 0x1
    j19_12 = (imm21 >> 12) & 0xFF
    j11 = (imm21 >> 11) & 0x1
    j10_1 = (imm21 >> 1) & 0x3FF
    return ((j20 << 31) | (j10_1 << 21) | (j11 << 20) | (j19_12 << 12) |
            ((rd & 0x1F) << 7) | (opcode & 0x7F)) & 0xFFFFFFFF


def parse_offset_reg(op_str: str) -> Tuple[int, str]:
    """Parse 'offset(reg)' or 'reg, offset' format."""
    m = re.match(r"^([+-]?\d+|0x[0-9a-fA-F]+)\s*\(\s*([a-zA-Z0-9]+)\s*\)$", op_str.strip())
    if m:
        return parse_imm(m.group(1)), m.group(2)
    parts = [p.strip() for p in op_str.split(",")]
    if len(parts) == 2:
        return parse_imm(parts[1]), parts[0]
    raise ValueError(f"Invalid memory offset syntax: {op_str}")


def assemble_line(line: str, current_pc: int = 0, labels: Optional[Dict[str, int]] = None) -> int:
    """Assembles a single line of assembly into a 32-bit machine word."""
    labels = labels or {}
    clean_line = line.split("#")[0].split("//")[0].strip()
    if not clean_line:
        return 0

    tokens = [t for t in re.split(r"[\s,]+", clean_line) if t]
    if not tokens:
        return 0

    mnemonic = tokens[0].lower()
    args = tokens[1:]

    # RV32I Arithmetic & Logic R-Type
    if mnemonic == "add":   return encode_r(0x00, parse_reg(args[2]), parse_reg(args[1]), 0x0, parse_reg(args[0]), 0x33)
    if mnemonic == "sub":   return encode_r(0x20, parse_reg(args[2]), parse_reg(args[1]), 0x0, parse_reg(args[0]), 0x33)
    if mnemonic == "sll":   return encode_r(0x00, parse_reg(args[2]), parse_reg(args[1]), 0x1, parse_reg(args[0]), 0x33)
    if mnemonic == "slt":   return encode_r(0x00, parse_reg(args[2]), parse_reg(args[1]), 0x2, parse_reg(args[0]), 0x33)
    if mnemonic == "sltu":  return encode_r(0x00, parse_reg(args[2]), parse_reg(args[1]), 0x3, parse_reg(args[0]), 0x33)
    if mnemonic == "xor":   return encode_r(0x00, parse_reg(args[2]), parse_reg(args[1]), 0x4, parse_reg(args[0]), 0x33)
    if mnemonic == "srl":   return encode_r(0x00, parse_reg(args[2]), parse_reg(args[1]), 0x5, parse_reg(args[0]), 0x33)
    if mnemonic == "sra":   return encode_r(0x20, parse_reg(args[2]), parse_reg(args[1]), 0x5, parse_reg(args[0]), 0x33)
    if mnemonic == "or":    return encode_r(0x00, parse_reg(args[2]), parse_reg(args[1]), 0x6, parse_reg(args[0]), 0x33)
    if mnemonic == "and":   return encode_r(0x00, parse_reg(args[2]), parse_reg(args[1]), 0x7, parse_reg(args[0]), 0x33)

    # RV32M Extension R-Type
    if mnemonic == "mul":    return encode_r(0x01, parse_reg(args[2]), parse_reg(args[1]), 0x0, parse_reg(args[0]), 0x33)
    if mnemonic == "mulh":   return encode_r(0x01, parse_reg(args[2]), parse_reg(args[1]), 0x1, parse_reg(args[0]), 0x33)
    if mnemonic == "mulhsu": return encode_r(0x01, parse_reg(args[2]), parse_reg(args[1]), 0x2, parse_reg(args[0]), 0x33)
    if mnemonic == "mulhu":  return encode_r(0x01, parse_reg(args[2]), parse_reg(args[1]), 0x3, parse_reg(args[0]), 0x33)
    if mnemonic == "div":    return encode_r(0x01, parse_reg(args[2]), parse_reg(args[1]), 0x4, parse_reg(args[0]), 0x33)
    if mnemonic == "divu":   return encode_r(0x01, parse_reg(args[2]), parse_reg(args[1]), 0x5, parse_reg(args[0]), 0x33)
    if mnemonic == "rem":    return encode_r(0x01, parse_reg(args[2]), parse_reg(args[1]), 0x6, parse_reg(args[0]), 0x33)
    if mnemonic == "remu":   return encode_r(0x01, parse_reg(args[2]), parse_reg(args[1]), 0x7, parse_reg(args[0]), 0x33)

    # I-Type Arithmetic
    if mnemonic == "addi":  return encode_i(parse_imm(args[2]), parse_reg(args[1]), 0x0, parse_reg(args[0]), 0x13)
    if mnemonic == "slli":  return encode_i(parse_imm(args[2]) & 0x1F, parse_reg(args[1]), 0x1, parse_reg(args[0]), 0x13)
    if mnemonic == "slti":  return encode_i(parse_imm(args[2]), parse_reg(args[1]), 0x2, parse_reg(args[0]), 0x13)
    if mnemonic == "sltiu": return encode_i(parse_imm(args[2]), parse_reg(args[1]), 0x3, parse_reg(args[0]), 0x13)
    if mnemonic == "xori":  return encode_i(parse_imm(args[2]), parse_reg(args[1]), 0x4, parse_reg(args[0]), 0x13)
    if mnemonic == "srli":  return encode_i(parse_imm(args[2]) & 0x1F, parse_reg(args[1]), 0x5, parse_reg(args[0]), 0x13)
    if mnemonic == "srai":  return encode_i((parse_imm(args[2]) & 0x1F) | 0x400, parse_reg(args[1]), 0x5, parse_reg(args[0]), 0x13)
    if mnemonic == "ori":   return encode_i(parse_imm(args[2]), parse_reg(args[1]), 0x6, parse_reg(args[0]), 0x13)
    if mnemonic == "andi":  return encode_i(parse_imm(args[2]), parse_reg(args[1]), 0x7, parse_reg(args[0]), 0x13)
    if mnemonic == "nop":   return encode_i(0, 0, 0, 0, 0x13)

    # Loads (I-Type)
    if mnemonic in ("lb", "lh", "lw", "lbu", "lhu"):
        rd = parse_reg(args[0])
        if len(args) == 2:
            imm, rs1_str = parse_offset_reg(args[1])
            rs1 = parse_reg(rs1_str)
        else:
            rs1 = parse_reg(args[1])
            imm = parse_imm(args[2])
        f3_map = {"lb": 0x0, "lh": 0x1, "lw": 0x2, "lbu": 0x4, "lhu": 0x5}
        return encode_i(imm, rs1, f3_map[mnemonic], rd, 0x03)

    # Stores (S-Type)
    if mnemonic in ("sb", "sh", "sw"):
        rs2 = parse_reg(args[0])
        if len(args) == 2:
            imm, rs1_str = parse_offset_reg(args[1])
            rs1 = parse_reg(rs1_str)
        else:
            rs1 = parse_reg(args[1])
            imm = parse_imm(args[2])
        f3_map = {"sb": 0x0, "sh": 0x1, "sw": 0x2}
        return encode_s(imm, rs2, rs1, f3_map[mnemonic], 0x23)

    # Branches (B-Type)
    if mnemonic in ("beq", "bne", "blt", "bge", "bltu", "bgeu"):
        rs1 = parse_reg(args[0])
        rs2 = parse_reg(args[1])
        target_str = args[2]
        if target_str in labels:
            offset = labels[target_str] - current_pc
        else:
            offset = parse_imm(target_str)
        f3_map = {"beq": 0x0, "bne": 0x1, "blt": 0x4, "bge": 0x5, "bltu": 0x6, "bgeu": 0x7}
        return encode_b(offset, rs2, rs1, f3_map[mnemonic], 0x63)

    # Jumps & U-Types
    if mnemonic == "jal":
        if len(args) == 1:
            rd = 1 # ra
            target_str = args[0]
        else:
            rd = parse_reg(args[0])
            target_str = args[1]
        offset = (labels[target_str] - current_pc) if target_str in labels else parse_imm(target_str)
        return encode_j(offset, rd, 0x6F)

    if mnemonic == "jalr":
        if len(args) == 1:
            rd = 1; rs1 = parse_reg(args[0]); imm = 0
        elif len(args) == 2:
            rd = parse_reg(args[0])
            imm, rs1_str = parse_offset_reg(args[1])
            rs1 = parse_reg(rs1_str)
        else:
            rd = parse_reg(args[0]); rs1 = parse_reg(args[1]); imm = parse_imm(args[2])
        return encode_i(imm, rs1, 0x0, rd, 0x67)

    if mnemonic == "lui":
        return encode_u(parse_imm(args[1]), parse_reg(args[0]), 0x37)
    if mnemonic == "auipc":
        return encode_u(parse_imm(args[1]), parse_reg(args[0]), 0x17)

    # Custom-0 CapabilityLite Manipulation (0x0B)
    if mnemonic == "csetbounds":
        return encode_r(0x00, parse_reg(args[2]), parse_cap_reg(args[1]), 0x0, parse_cap_reg(args[0]), 0x0B)
    if mnemonic == "candperm":
        return encode_r(0x00, parse_reg(args[2]), parse_cap_reg(args[1]), 0x1, parse_cap_reg(args[0]), 0x0B)
    if mnemonic == "cincoffset":
        return encode_r(0x00, parse_reg(args[2]), parse_cap_reg(args[1]), 0x2, parse_cap_reg(args[0]), 0x0B)
    if mnemonic == "cgetbase":
        return encode_r(0x00, 0, parse_cap_reg(args[1]), 0x3, parse_reg(args[0]), 0x0B)
    if mnemonic == "cgetlen":
        return encode_r(0x00, 0, parse_cap_reg(args[1]), 0x4, parse_reg(args[0]), 0x0B)
    if mnemonic == "cgettag":
        return encode_r(0x00, 0, parse_cap_reg(args[1]), 0x5, parse_reg(args[0]), 0x0B)
    if mnemonic == "cgetperm":
        return encode_r(0x00, 0, parse_cap_reg(args[1]), 0x6, parse_reg(args[0]), 0x0B)
    if mnemonic == "cgetoffset":
        return encode_r(0x00, 0, parse_cap_reg(args[1]), 0x7, parse_reg(args[0]), 0x0B)
    if mnemonic == "cclear":
        return encode_r(0x01, 0, 0, 0x7, parse_cap_reg(args[0]), 0x0B)

    # Custom-1 Capability Protected Memory (0x2B)
    if mnemonic in ("clb", "clh", "clw"):
        rd = parse_reg(args[0])
        if len(args) == 2:
            imm, cs1_str = parse_offset_reg(args[1])
            cs1 = parse_cap_reg(cs1_str)
        else:
            cs1 = parse_cap_reg(args[1])
            imm = parse_imm(args[2])
        f3_map = {"clb": 0x0, "clh": 0x1, "clw": 0x2}
        return encode_i(imm, cs1, f3_map[mnemonic], rd, 0x2B)

    if mnemonic in ("csb", "csh", "csw"):
        rs2 = parse_reg(args[0])
        if len(args) == 2:
            imm, cs1_str = parse_offset_reg(args[1])
            cs1 = parse_cap_reg(cs1_str)
        else:
            cs1 = parse_cap_reg(args[1])
            imm = parse_imm(args[2])
        f3_map = {"csb": 0x4, "csh": 0x5, "csw": 0x6}
        return encode_s(imm, rs2, cs1, f3_map[mnemonic], 0x2B)

    raise ValueError(f"Unsupported instruction or mnemonic: '{mnemonic}'")


def assemble_program(source_text: str) -> List[Tuple[int, int, str]]:
    """
    Two-pass assembler.
    Returns a list of (pc, machine_code, raw_source_line).
    """
    raw_lines = source_text.splitlines()
    labels: Dict[str, int] = {}
    cleaned_items: List[Tuple[int, str, str]] = [] # pc, line, orig

    current_pc = 0
    # Pass 1: Gather labels and line PCs
    for raw in raw_lines:
        orig = raw
        line = raw.split("#")[0].split("//")[0].strip()
        if not line:
            continue
        if ":" in line:
            parts = line.split(":")
            lbl = parts[0].strip()
            labels[lbl] = current_pc
            line = parts[1].strip()
            if not line:
                continue
        cleaned_items.append((current_pc, line, orig))
        current_pc += 4

    # Pass 2: Assemble machine instructions
    assembled: List[Tuple[int, int, str]] = []
    for pc, line, orig in cleaned_items:
        word = assemble_line(line, pc, labels)
        assembled.append((pc, word, orig))

    return assembled


def disassemble_inst(inst: int) -> str:
    """Disassembles a 32-bit machine word into human-readable assembly mnemonic."""
    opcode = inst & 0x7F
    rd = (inst >> 7) & 0x1F
    funct3 = (inst >> 12) & 0x7
    rs1 = (inst >> 15) & 0x1F
    rs2 = (inst >> 20) & 0x1F
    funct7 = (inst >> 25) & 0x7F

    imm_i = (inst >> 20) if (inst >> 31) == 0 else ((inst >> 20) - (1 << 12))
    imm_s = (((inst >> 25) << 5) | ((inst >> 7) & 0x1F))
    if (inst >> 31) != 0: imm_s -= (1 << 12)

    if opcode == 0x33:
        if funct7 == 0x00:
            names = {0: "add", 1: "sll", 2: "slt", 3: "sltu", 4: "xor", 5: "srl", 6: "or", 7: "and"}
            return f"{names.get(funct3, 'unknown')} x{rd}, x{rs1}, x{rs2}"
        elif funct7 == 0x20:
            names = {0: "sub", 5: "sra"}
            return f"{names.get(funct3, 'unknown')} x{rd}, x{rs1}, x{rs2}"
        elif funct7 == 0x01: # RV32M
            names = {0: "mul", 1: "mulh", 2: "mulhsu", 3: "mulhu", 4: "div", 5: "divu", 6: "rem", 7: "remu"}
            return f"{names.get(funct3, 'unknown')} x{rd}, x{rs1}, x{rs2}"

    elif opcode == 0x13:
        if funct3 == 0 and rs1 == 0 and rd == 0 and imm_i == 0:
            return "nop"
        names = {0: "addi", 1: "slli", 2: "slti", 3: "sltiu", 4: "xori", 5: "srli/srai", 6: "ori", 7: "andi"}
        if funct3 == 5:
            return f"{'srai' if (funct7 == 0x20) else 'srli'} x{rd}, x{rs1}, {imm_i & 0x1F}"
        return f"{names.get(funct3, 'unknown')} x{rd}, x{rs1}, {imm_i}"

    elif opcode == 0x03:
        names = {0: "lb", 1: "lh", 2: "lw", 4: "lbu", 5: "lhu"}
        return f"{names.get(funct3, 'unknown')} x{rd}, {imm_i}(x{rs1})"

    elif opcode == 0x23:
        names = {0: "sb", 1: "sh", 2: "sw"}
        return f"{names.get(funct3, 'unknown')} x{rs2}, {imm_s}(x{rs1})"

    elif opcode == 0x63:
        names = {0: "beq", 1: "bne", 4: "blt", 5: "bge", 6: "bltu", 7: "bgeu"}
        return f"{names.get(funct3, 'unknown')} x{rs1}, x{rs2}, offset"

    elif opcode == 0x6F:
        return f"jal x{rd}, target"

    elif opcode == 0x67:
        return f"jalr x{rd}, {imm_i}(x{rs1})"

    elif opcode == 0x37:
        return f"lui x{rd}, 0x{(inst >> 12) & 0xFFFFF:X}"

    elif opcode == 0x17:
        return f"auipc x{rd}, 0x{(inst >> 12) & 0xFFFFF:X}"

    # Custom-0: CapabilityLite
    elif opcode == 0x0B:
        if funct3 == 0: return f"csetbounds c{rd}, c{rs1}, x{rs2}"
        if funct3 == 1: return f"candperm c{rd}, c{rs1}, x{rs2}"
        if funct3 == 2: return f"cincoffset c{rd}, c{rs1}, x{rs2}"
        if funct3 == 3: return f"cgetbase x{rd}, c{rs1}"
        if funct3 == 4: return f"cgetlen x{rd}, c{rs1}"
        if funct3 == 5: return f"cgettag x{rd}, c{rs1}"
        if funct3 == 6: return f"cgetperm x{rd}, c{rs1}"
        if funct3 == 7:
            if funct7 == 0x00: return f"cgetoffset x{rd}, c{rs1}"
            if funct7 == 0x01: return f"cclear c{rd}"

    # Custom-1: Capability Memory
    elif opcode == 0x2B:
        if funct3 == 0: return f"clb x{rd}, {imm_i}(c{rs1})"
        if funct3 == 1: return f"clh x{rd}, {imm_i}(c{rs1})"
        if funct3 == 2: return f"clw x{rd}, {imm_i}(c{rs1})"
        if funct3 == 4: return f"csb x{rs2}, {imm_s}(c{rs1})"
        if funct3 == 5: return f"csh x{rs2}, {imm_s}(c{rs1})"
        if funct3 == 6: return f"csw x{rs2}, {imm_s}(c{rs1})"

    return f".word 0x{inst:08X}"
