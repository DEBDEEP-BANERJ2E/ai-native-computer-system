#!/usr/bin/env python3
"""
RV32IM Reference Interpreter for Objective 2 Differential Verification.
Mirrors hardware execution semantics including little-endian byte lanes,
misalignment rejection, full RV32M extension, Phase 6 System MMIO & Telemetry,
and cycle-by-cycle commit event logging.
"""

from typing import List, Dict, Optional, Tuple, NamedTuple

class CapabilityLite(NamedTuple):
    tag: bool
    base: int
    length: int
    perms: int
    offset: int

class CommitEvent(NamedTuple):
    pc: int
    instruction: int
    rd: int
    regWrite: bool
    writeData: int
    memRead: bool
    memReadReq: bool
    memWrite: bool
    memWriteReq: bool
    memAddress: int
    memWriteData: int
    illegal: bool

class RV32Interpreter:
    def __init__(self, memory_size: int = 4096):
        self.memory_size = memory_size
        self.regs = [0] * 32
        self.cap_regs: List[CapabilityLite] = [CapabilityLite(False, 0, 0, 0, 0) for _ in range(8)]
        self.imem = bytearray(memory_size)
        self.memory = bytearray(memory_size)
        self.pc = 0
        self.halted = False
        self.trace: List[CommitEvent] = []

        # Phase 6 & 7: System MMIO, Telemetry & Performance Counter State
        self.mmio_regs: Dict[int, int] = {
            0x80002004: 0, # PROCESS_BEHAVIOR_CLASS (RW)
            0x80002008: 0, # SCHED_HINT (RW)
            0x80002024: 0, # CURRENT_CONTEXT (RW)
            0x80001010: 1, # EDP_CONFIG (RO, 1)
        }
        self.telemetry_prev_result = 0
        self.cla_switching = 0
        self.mul_thermal = 0
        self.rev_energy = 0

        self.retired_count = 0
        self.branch_taken_count = 0
        self.load_use_stall_count = 0
        self.div_busy_cycles = 0
        self.pipeline_stall_count = 0
        self.last_commit_pc = 0

        # Snapshot of committed state visible to the MEM stage (prior instruction commit)
        self.mem_vis_cla_switching = 0
        self.mem_vis_mul_thermal = 0
        self.mem_vis_retired_count = 0
        self.mem_vis_last_commit_pc = 0

        self.sec_status = 0
        self.sec_pc = 0
        self.sec_addr = 0
        self.sec_info = 0
        self.sec_context = 0

        # State tracking for stall emulation
        self.last_was_load = False
        self.last_load_rd = 0
        self.reset()

    def reset(self):
        self.regs = [0] * 32
        self.cap_regs = [
            CapabilityLite(False, 0, 0, 0, 0),                       # c0: NULL
            CapabilityLite(True, 0x00000000, 0x00001000, 3, 0),      # c1: RAM Root (0..0x1000, RW)
            CapabilityLite(True, 0x80000000, 0x00010000, 3, 0),      # c2: MMIO Root (0x80000000..0x80010000, RW)
            CapabilityLite(False, 0, 0, 0, 0),                       # c3: NULL
            CapabilityLite(False, 0, 0, 0, 0),                       # c4: NULL
            CapabilityLite(False, 0, 0, 0, 0),                       # c5: NULL
            CapabilityLite(False, 0, 0, 0, 0),                       # c6: NULL
            CapabilityLite(False, 0, 0, 0, 0),                       # c7: NULL
        ]
        self.memory = bytearray(self.memory_size)
        self.pc = 0
        self.halted = False
        self.trace = []
        self.mmio_regs = {
            0x80002004: 0,
            0x80002008: 0,
            0x80002024: 0,
            0x80001010: 1,
        }
        self.telemetry_prev_result = 0
        self.cla_switching = 0
        self.mul_thermal = 0
        self.rev_energy = 0
        self.retired_count = 0
        self.branch_taken_count = 0
        self.load_use_stall_count = 0
        self.div_busy_cycles = 0
        self.pipeline_stall_count = 0
        self.last_commit_pc = 0
        self.mem_vis_cla_switching = 0
        self.mem_vis_mul_thermal = 0
        self.mem_vis_retired_count = 0
        self.mem_vis_last_commit_pc = 0
        self.sec_status = 0
        self.sec_pc = 0
        self.sec_addr = 0
        self.sec_info = 0
        self.sec_context = 0
        self.last_was_load = False
        self.last_load_rd = 0

    def trigger_security_violation(self, access_type: int, reason: int, addr: int, pc: int):
        if self.sec_status == 0:
            self.sec_status = 1
            self.sec_pc = pc & 0xFFFFFFFF
            self.sec_addr = addr & 0xFFFFFFFF
            self.sec_info = ((access_type & 0x3) << 4) | (reason & 0xF)
            self.sec_context = self.mmio_regs.get(0x80002024, 0)

    def load_program(self, words: List[int], start_addr: int = 0):
        self.imem = bytearray(self.memory_size)
        for i, word in enumerate(words):
            addr = start_addr + i * 4
            if addr + 4 <= len(self.imem):
                self.imem[addr:addr+4] = int(word & 0xFFFFFFFF).to_bytes(4, 'little')

    def to_signed(self, val: int, bits: int = 32) -> int:
        val = val & ((1 << bits) - 1)
        if val & (1 << (bits - 1)):
            return val - (1 << bits)
        return val

    def step(self) -> Optional[CommitEvent]:
        if self.pc < 0 or self.pc + 4 > len(self.imem):
            self.halted = True
            return None

        current_pc = self.pc
        inst = int.from_bytes(self.imem[current_pc:current_pc+4], 'little')
        opcode = inst & 0x7F
        rd = (inst >> 7) & 0x1F
        funct3 = (inst >> 12) & 0x07
        rs1 = (inst >> 15) & 0x1F
        rs2 = (inst >> 20) & 0x1F
        funct7 = (inst >> 25) & 0x7F

        next_pc = (current_pc + 4) & 0xFFFFFFFF
        reg_write = False
        write_data = 0
        mem_read = False
        mem_read_req = False
        mem_write = False
        mem_write_req = False
        mem_addr = 0
        mem_write_data = 0
        illegal = False

        # Immediate decoding
        imm_i = self.to_signed(inst >> 20, 12)
        imm_s = self.to_signed(((inst >> 25) << 5) | ((inst >> 7) & 0x1F), 12)
        imm_b = self.to_signed(
            (((inst >> 31) & 0x1) << 12) |
            (((inst >> 7) & 0x1) << 11) |
            (((inst >> 25) & 0x3F) << 5) |
            (((inst >> 8) & 0xF) << 1), 13
        )
        imm_u = (inst & 0xFFFFF000)
        imm_j = self.to_signed(
            (((inst >> 31) & 0x1) << 20) |
            (((inst >> 12) & 0xFF) << 12) |
            (((inst >> 20) & 0x1) << 11) |
            (((inst >> 21) & 0x3FF) << 1), 21
        )

        val1 = self.regs[rs1]
        val2 = self.regs[rs2]
        s_val1 = self.to_signed(val1)
        s_val2 = self.to_signed(val2)

        # Load-use hazard detection for performance counter tracking
        if self.last_was_load and self.last_load_rd != 0:
            uses_rs1 = (opcode in (0x33, 0x13, 0x03, 0x23, 0x63, 0x67))
            uses_rs2 = (opcode in (0x33, 0x23, 0x63))
            if (uses_rs1 and rs1 == self.last_load_rd) or (uses_rs2 and rs2 == self.last_load_rd):
                self.load_use_stall_count += 1
                self.pipeline_stall_count += 1

        is_current_load = False

        # Telemetry tracking metadata
        telem_valid = False
        telem_cla = False
        telem_mul = False
        telem_res = 0

        if opcode == 0x33: # R-Type & RV32M
            if funct7 == 0x00:
                reg_write = True
                if funct3 == 0:
                    write_data = (val1 + val2) & 0xFFFFFFFF # ADD
                    telem_valid = True; telem_cla = True; telem_res = write_data
                elif funct3 == 1:
                    write_data = (val1 << (val2 & 0x1F)) & 0xFFFFFFFF # SLL
                    telem_valid = True; telem_res = write_data
                elif funct3 == 2:
                    write_data = 1 if s_val1 < s_val2 else 0 # SLT
                    telem_valid = True; telem_res = write_data
                elif funct3 == 3:
                    write_data = 1 if val1 < val2 else 0 # SLTU
                    telem_valid = True; telem_res = write_data
                elif funct3 == 4:
                    write_data = val1 ^ val2 # XOR
                    telem_valid = True; telem_res = write_data
                elif funct3 == 5:
                    write_data = (val1 >> (val2 & 0x1F)) & 0xFFFFFFFF # SRL
                    telem_valid = True; telem_res = write_data
                elif funct3 == 6:
                    write_data = val1 | val2 # OR
                    telem_valid = True; telem_res = write_data
                elif funct3 == 7:
                    write_data = val1 & val2 # AND
                    telem_valid = True; telem_res = write_data
                else: illegal = True
            elif funct7 == 0x20:
                if funct3 == 0:
                    reg_write = True
                    write_data = (val1 - val2) & 0xFFFFFFFF # SUB
                    telem_valid = True; telem_cla = True; telem_res = write_data
                elif funct3 == 5:
                    reg_write = True
                    write_data = (s_val1 >> (val2 & 0x1F)) & 0xFFFFFFFF # SRA
                    telem_valid = True; telem_res = write_data
                else:
                    illegal = True
            elif funct7 == 0x01: # Full RV32M
                reg_write = True
                if funct3 == 0: # MUL
                    write_data = (s_val1 * s_val2) & 0xFFFFFFFF
                    telem_valid = True; telem_mul = True; telem_res = write_data
                elif funct3 == 1: # MULH (signed x signed high)
                    prod = s_val1 * s_val2
                    write_data = (prod >> 32) & 0xFFFFFFFF
                    telem_valid = True; telem_mul = True; telem_res = write_data
                elif funct3 == 2: # MULHSU (signed x unsigned high)
                    prod = s_val1 * val2
                    write_data = (prod >> 32) & 0xFFFFFFFF
                    telem_valid = True; telem_mul = True; telem_res = write_data
                elif funct3 == 3: # MULHU (unsigned x unsigned high)
                    prod = val1 * val2
                    write_data = (prod >> 32) & 0xFFFFFFFF
                    telem_valid = True; telem_mul = True; telem_res = write_data
                elif funct3 == 4: # DIV (signed)
                    self.div_busy_cycles += 33
                    self.pipeline_stall_count += 33
                    if val2 == 0:
                        write_data = 0xFFFFFFFF
                    elif s_val1 == -0x80000000 and s_val2 == -1:
                        write_data = 0x80000000
                    else:
                        q = abs(s_val1) // abs(s_val2)
                        if (s_val1 < 0) ^ (s_val2 < 0):
                            q = -q
                        write_data = q & 0xFFFFFFFF
                elif funct3 == 5: # DIVU (unsigned)
                    self.div_busy_cycles += 33
                    self.pipeline_stall_count += 33
                    if val2 == 0:
                        write_data = 0xFFFFFFFF
                    else:
                        write_data = (val1 // val2) & 0xFFFFFFFF
                elif funct3 == 6: # REM (signed)
                    self.div_busy_cycles += 33
                    self.pipeline_stall_count += 33
                    if val2 == 0:
                        write_data = val1 & 0xFFFFFFFF
                    elif s_val1 == -0x80000000 and s_val2 == -1:
                        write_data = 0
                    else:
                        r = abs(s_val1) % abs(s_val2)
                        if s_val1 < 0:
                            r = -r
                        write_data = r & 0xFFFFFFFF
                elif funct3 == 7: # REMU (unsigned)
                    self.div_busy_cycles += 33
                    self.pipeline_stall_count += 33
                    if val2 == 0:
                        write_data = val1 & 0xFFFFFFFF
                    else:
                        write_data = (val1 % val2) & 0xFFFFFFFF
                else:
                    illegal = True
                    reg_write = False
            else:
                illegal = True

        elif opcode == 0x13: # I-Type
            reg_write = True
            if funct3 == 0:
                write_data = (val1 + imm_i) & 0xFFFFFFFF # ADDI
                telem_valid = True; telem_cla = True; telem_res = write_data
            elif funct3 == 2:
                write_data = 1 if s_val1 < imm_i else 0 # SLTI
                telem_valid = True; telem_res = write_data
            elif funct3 == 3:
                write_data = 1 if val1 < (imm_i & 0xFFFFFFFF) else 0 # SLTIU
                telem_valid = True; telem_res = write_data
            elif funct3 == 4:
                write_data = val1 ^ (imm_i & 0xFFFFFFFF) # XORI
                telem_valid = True; telem_res = write_data
            elif funct3 == 6:
                write_data = val1 | (imm_i & 0xFFFFFFFF) # ORI
                telem_valid = True; telem_res = write_data
            elif funct3 == 7:
                write_data = val1 & (imm_i & 0xFFFFFFFF) # ANDI
                telem_valid = True; telem_res = write_data
            elif funct3 == 1 and funct7 == 0x00: # SLLI
                shamt = (inst >> 20) & 0x1F
                write_data = (val1 << shamt) & 0xFFFFFFFF
                telem_valid = True; telem_res = write_data
            elif funct3 == 5 and funct7 == 0x00: # SRLI
                shamt = (inst >> 20) & 0x1F
                write_data = (val1 >> shamt) & 0xFFFFFFFF
                telem_valid = True; telem_res = write_data
            elif funct3 == 5 and funct7 == 0x20: # SRAI
                shamt = (inst >> 20) & 0x1F
                write_data = (s_val1 >> shamt) & 0xFFFFFFFF
                telem_valid = True; telem_res = write_data
            else:
                reg_write = False
                illegal = True

        elif opcode == 0x03: # Load
            mem_read_req = True
            mem_addr = (val1 + imm_i) & 0xFFFFFFFF
            is_half = (funct3 == 1 or funct3 == 5)
            is_word = (funct3 == 2)
            misaligned = (is_half and (mem_addr & 1 != 0)) or (is_word and (mem_addr & 3 != 0))
            is_current_load = True
            telem_valid = True; telem_cla = True; telem_res = mem_addr

            if (mem_addr >> 16) == 0x8000: # MMIO Space
                if is_word and not misaligned:
                    mem_read = True
                    reg_write = True
                    if mem_addr == 0x80001000: write_data = 0
                    elif mem_addr == 0x80001004: write_data = self.mem_vis_cla_switching & 0xFFFFFFFF
                    elif mem_addr == 0x80001008: write_data = self.mem_vis_mul_thermal & 0xFFFFFFFF
                    elif mem_addr == 0x8000100C: write_data = ((self.rev_energy + self.mem_vis_cla_switching + self.mem_vis_mul_thermal) * 1) & 0xFFFFFFFF
                    elif mem_addr == 0x80001010: write_data = 1
                    elif mem_addr == 0x80002000: write_data = 0 # BRANCH_CONFIDENCE
                    elif mem_addr == 0x80002004: write_data = self.mmio_regs.get(0x80002004, 0)
                    elif mem_addr == 0x80002008: write_data = self.mmio_regs.get(0x80002008, 0)
                    elif mem_addr == 0x8000200C: write_data = self.mem_vis_retired_count & 0xFFFFFFFF
                    elif mem_addr == 0x80002010: write_data = self.branch_taken_count & 0xFFFFFFFF
                    elif mem_addr == 0x80002014: write_data = self.load_use_stall_count & 0xFFFFFFFF
                    elif mem_addr == 0x80002018: write_data = self.div_busy_cycles & 0xFFFFFFFF
                    elif mem_addr == 0x8000201C: write_data = self.pipeline_stall_count & 0xFFFFFFFF
                    elif mem_addr == 0x80002020: write_data = self.mem_vis_last_commit_pc & 0xFFFFFFFF
                    elif mem_addr == 0x80002024: write_data = self.mmio_regs.get(0x80002024, 0)
                    elif mem_addr == 0x80002100: write_data = self.sec_status
                    elif mem_addr == 0x80002104: write_data = self.sec_pc
                    elif mem_addr == 0x80002108: write_data = self.sec_addr
                    elif mem_addr == 0x8000210C: write_data = self.sec_info
                    elif mem_addr == 0x80002110: write_data = self.sec_context
                    else:
                        mem_read = False
                        reg_write = False
                else:
                    mem_read = False
                    reg_write = False
            elif mem_addr < len(self.memory) and not misaligned: # RAM Space
                mem_read = True
                reg_write = True
                if funct3 == 0: # LB
                    write_data = self.to_signed(self.memory[mem_addr], 8) & 0xFFFFFFFF
                elif funct3 == 1: # LH
                    raw = int.from_bytes(self.memory[mem_addr:mem_addr+2], 'little', signed=True)
                    write_data = raw & 0xFFFFFFFF
                elif funct3 == 2: # LW
                    write_data = int.from_bytes(self.memory[mem_addr:mem_addr+4], 'little', signed=False)
                elif funct3 == 4: # LBU
                    write_data = self.memory[mem_addr]
                elif funct3 == 5: # LHU
                    write_data = int.from_bytes(self.memory[mem_addr:mem_addr+2], 'little', signed=False)
                else:
                    mem_read = False
                    reg_write = False
                    illegal = True
            else:
                mem_read = False
                reg_write = False

        elif opcode == 0x23: # Store
            mem_write_req = True
            mem_addr = (val1 + imm_s) & 0xFFFFFFFF
            mem_write_data = val2
            is_half = (funct3 == 1)
            is_word = (funct3 == 2)
            misaligned = (is_half and (mem_addr & 1 != 0)) or (is_word and (mem_addr & 3 != 0))
            telem_valid = True; telem_cla = True; telem_res = mem_addr

            if (mem_addr >> 16) == 0x8000: # MMIO Space
                if is_word and not misaligned:
                    if mem_addr == 0x80002004:
                        self.mmio_regs[0x80002004] = val2 & 0xFFFFFFFF
                        mem_write = True
                    elif mem_addr == 0x80002008:
                        self.mmio_regs[0x80002008] = val2 & 0xFFFFFFFF
                        mem_write = True
                    elif mem_addr == 0x80002024:
                        self.mmio_regs[0x80002024] = val2 & 0xFFFFFFFF
                        mem_write = True
                    elif mem_addr == 0x80002100:
                        if val2 & 1:
                            self.sec_status = 0
                        mem_write = True
                    else:
                        mem_write = False
                else:
                    mem_write = False
            elif mem_addr < len(self.memory) and not misaligned: # RAM Space
                mem_write = True
                if funct3 == 0: # SB
                    self.memory[mem_addr] = val2 & 0xFF
                elif funct3 == 1 and mem_addr + 2 <= len(self.memory): # SH
                    self.memory[mem_addr:mem_addr+2] = (val2 & 0xFFFF).to_bytes(2, 'little')
                elif funct3 == 2 and mem_addr + 4 <= len(self.memory): # SW
                    self.memory[mem_addr:mem_addr+4] = (val2 & 0xFFFFFFFF).to_bytes(4, 'little')
                else:
                    mem_write = False
                    illegal = True
            else:
                mem_write = False

        elif opcode == 0x63: # Branch
            take = False
            telem_valid = True
            if funct3 in (0, 1): # BEQ, BNE use SUB in ALU
                telem_cla = True
                telem_res = (val1 - val2) & 0xFFFFFFFF
                take = (val1 == val2) if funct3 == 0 else (val1 != val2)
            elif funct3 in (4, 5): # BLT, BGE use SLT in ALU (signed)
                telem_cla = False
                telem_res = 1 if s_val1 < s_val2 else 0
                take = (s_val1 < s_val2) if funct3 == 4 else (s_val1 >= s_val2)
            elif funct3 in (6, 7): # BLTU, BGEU use SLTU in ALU (unsigned)
                telem_cla = False
                telem_res = 1 if val1 < val2 else 0
                take = (val1 < val2) if funct3 == 6 else (val1 >= val2)
            else:
                illegal = True
                telem_valid = False
            if take:
                next_pc = (current_pc + imm_b) & 0xFFFFFFFF
                self.branch_taken_count += 1

        elif opcode == 0x6F: # JAL
            reg_write = True
            write_data = (current_pc + 4) & 0xFFFFFFFF
            next_pc = (current_pc + imm_j) & 0xFFFFFFFF

        elif opcode == 0x67: # JALR
            if funct3 == 0:
                reg_write = True
                write_data = (current_pc + 4) & 0xFFFFFFFF
                next_pc = (val1 + imm_i) & ~1 & 0xFFFFFFFF
            else:
                illegal = True

        elif opcode == 0x37: # LUI
            reg_write = True
            write_data = imm_u & 0xFFFFFFFF

        elif opcode == 0x17: # AUIPC
            reg_write = True
            write_data = (current_pc + imm_u) & 0xFFFFFFFF
            telem_valid = True; telem_cla = True; telem_res = write_data

        elif opcode == 0x0B: # OP_CAP: Capability Manipulation
            cs1_idx = rs1
            cd_idx = rd
            if cs1_idx > 7 or funct7 != 0:
                illegal = True
            elif funct3 <= 2 and cd_idx > 7:
                illegal = True
            else:
                src_cap = self.cap_regs[cs1_idx]
                if funct3 == 0: # CSETBOUNDS cd, cs1, rs2
                    req_base = (src_cap.base + src_cap.offset) & 0xFFFFFFFF
                    req_len = val2 & 0xFFFFFFFF
                    req_top = req_base + req_len
                    parent_top = src_cap.base + src_cap.length
                    if not src_cap.tag:
                        self.trigger_security_violation(3, 1, req_base, current_pc) # INVALID_CAPABILITY
                    elif req_top > parent_top:
                        self.trigger_security_violation(3, 6, req_base, current_pc) # MONOTONICITY
                    else:
                        if cd_idx != 0:
                            self.cap_regs[cd_idx] = CapabilityLite(True, req_base, req_len, src_cap.perms, 0)
                elif funct3 == 1: # CANDPERM cd, cs1, rs2
                    if not src_cap.tag:
                        self.trigger_security_violation(3, 1, src_cap.base, current_pc) # INVALID_CAPABILITY
                    else:
                        new_perms = src_cap.perms & (val2 & 0x7)
                        if cd_idx != 0:
                            self.cap_regs[cd_idx] = CapabilityLite(True, src_cap.base, src_cap.length, new_perms, src_cap.offset)
                elif funct3 == 2: # CINCOFFSET cd, cs1, rs2
                    u_offset = src_cap.offset & 0xFFFFFFFF
                    s_delta = self.to_signed(val2, 32)
                    new_offset_s = u_offset + s_delta
                    cursor_addr = (src_cap.base + src_cap.offset) & 0xFFFFFFFF
                    if not src_cap.tag:
                        self.trigger_security_violation(3, 1, cursor_addr, current_pc) # INVALID_CAPABILITY
                    elif new_offset_s < 0 or new_offset_s > src_cap.length:
                        self.trigger_security_violation(3, 2, cursor_addr, current_pc) # BOUNDS
                    else:
                        if cd_idx != 0:
                            self.cap_regs[cd_idx] = CapabilityLite(True, src_cap.base, src_cap.length, src_cap.perms, new_offset_s & 0xFFFFFFFF)
                elif funct3 == 3: # CGETBASE rd, cs1
                    reg_write = True
                    write_data = src_cap.base & 0xFFFFFFFF
                elif funct3 == 4: # CGETLEN rd, cs1
                    reg_write = True
                    write_data = src_cap.length & 0xFFFFFFFF
                elif funct3 == 5: # CGETTAG rd, cs1
                    reg_write = True
                    write_data = 1 if src_cap.tag else 0
                elif funct3 == 6: # CGETPERM rd, cs1
                    reg_write = True
                    write_data = src_cap.perms & 0x7
                else:
                    illegal = True

        elif opcode == 0x2B: # OP_CAP_MEM: Capability Protected Memory
            cs1_idx = rs1
            if cs1_idx > 7:
                illegal = True
            else:
                src_cap = self.cap_regs[cs1_idx]
                is_store = (funct3 & 0x4) != 0
                f3 = funct3 & 0x3
                access_size = 1 if f3 == 0 else (2 if f3 == 1 else 4)
                imm = imm_s if is_store else imm_i
                eff_addr = (src_cap.base + src_cap.offset + imm) & 0xFFFFFFFF
                access_end = eff_addr + access_size
                cap_top = src_cap.base + src_cap.length

                # Precedence: INVALID_CAPABILITY -> BOUNDS -> PERMISSION
                cap_allowed = True
                if not src_cap.tag:
                    cap_allowed = False
                    self.trigger_security_violation(1 if is_store else 0, 1, eff_addr, current_pc) # INVALID_CAPABILITY
                elif not (eff_addr >= src_cap.base and access_end <= cap_top):
                    cap_allowed = False
                    self.trigger_security_violation(1 if is_store else 0, 2, eff_addr, current_pc) # BOUNDS
                elif is_store and not (src_cap.perms & 2):
                    cap_allowed = False
                    self.trigger_security_violation(1, 4, eff_addr, current_pc) # WRITE_PERMISSION
                elif (not is_store) and not (src_cap.perms & 1):
                    cap_allowed = False
                    self.trigger_security_violation(0, 3, eff_addr, current_pc) # READ_PERMISSION

                if not is_store: # Protected Load (CLB, CLH, CLW)
                    mem_read_req = True
                    mem_addr = eff_addr
                    is_half = (f3 == 1)
                    is_word = (f3 == 2)
                    misaligned = (is_half and (mem_addr & 1 != 0)) or (is_word and (mem_addr & 3 != 0))
                    is_current_load = True
                    if cap_allowed:
                        if (mem_addr >> 16) == 0x8000: # MMIO
                            if is_word and not misaligned:
                                mem_read = True; reg_write = True
                                if mem_addr == 0x80001000: write_data = 0
                                elif mem_addr == 0x80001004: write_data = self.mem_vis_cla_switching & 0xFFFFFFFF
                                elif mem_addr == 0x80001008: write_data = self.mem_vis_mul_thermal & 0xFFFFFFFF
                                elif mem_addr == 0x8000100C: write_data = ((self.rev_energy + self.mem_vis_cla_switching + self.mem_vis_mul_thermal) * 1) & 0xFFFFFFFF
                                elif mem_addr == 0x80001010: write_data = 1
                                elif mem_addr == 0x80002000: write_data = 0
                                elif mem_addr == 0x80002004: write_data = self.mmio_regs.get(0x80002004, 0)
                                elif mem_addr == 0x80002008: write_data = self.mmio_regs.get(0x80002008, 0)
                                elif mem_addr == 0x8000200C: write_data = self.mem_vis_retired_count & 0xFFFFFFFF
                                elif mem_addr == 0x80002010: write_data = self.branch_taken_count & 0xFFFFFFFF
                                elif mem_addr == 0x80002014: write_data = self.load_use_stall_count & 0xFFFFFFFF
                                elif mem_addr == 0x80002018: write_data = self.div_busy_cycles & 0xFFFFFFFF
                                elif mem_addr == 0x8000201C: write_data = self.pipeline_stall_count & 0xFFFFFFFF
                                elif mem_addr == 0x80002020: write_data = self.mem_vis_last_commit_pc & 0xFFFFFFFF
                                elif mem_addr == 0x80002024: write_data = self.mmio_regs.get(0x80002024, 0)
                                elif mem_addr == 0x80002100: write_data = self.sec_status
                                elif mem_addr == 0x80002104: write_data = self.sec_pc
                                elif mem_addr == 0x80002108: write_data = self.sec_addr
                                elif mem_addr == 0x8000210C: write_data = self.sec_info
                                elif mem_addr == 0x80002110: write_data = self.sec_context
                                else:
                                    mem_read = False; reg_write = False
                            else:
                                mem_read = False; reg_write = False
                        elif mem_addr < len(self.memory) and not misaligned: # RAM
                            mem_read = True; reg_write = True
                            if f3 == 0: write_data = self.to_signed(self.memory[mem_addr], 8) & 0xFFFFFFFF
                            elif f3 == 1: write_data = int.from_bytes(self.memory[mem_addr:mem_addr+2], 'little', signed=True) & 0xFFFFFFFF
                            elif f3 == 2: write_data = int.from_bytes(self.memory[mem_addr:mem_addr+4], 'little', signed=False)
                        else:
                            mem_read = False; reg_write = False
                    else:
                        mem_read = False; reg_write = False

                else: # Protected Store (CSB, CSH, CSW)
                    mem_write_req = True
                    mem_addr = eff_addr
                    mem_write_data = val2
                    is_half = (f3 == 1)
                    is_word = (f3 == 2)
                    misaligned = (is_half and (mem_addr & 1 != 0)) or (is_word and (mem_addr & 3 != 0))
                    if cap_allowed:
                        if (mem_addr >> 16) == 0x8000: # MMIO
                            if is_word and not misaligned:
                                if mem_addr == 0x80002004:
                                    self.mmio_regs[0x80002004] = val2 & 0xFFFFFFFF
                                    mem_write = True
                                elif mem_addr == 0x80002008:
                                    self.mmio_regs[0x80002008] = val2 & 0xFFFFFFFF
                                    mem_write = True
                                elif mem_addr == 0x80002024:
                                    self.mmio_regs[0x80002024] = val2 & 0xFFFFFFFF
                                    mem_write = True
                                elif mem_addr == 0x80002100:
                                    if val2 & 1:
                                        self.sec_status = 0
                                    mem_write = True
                                else:
                                    mem_write = False
                            else:
                                mem_write = False
                        elif mem_addr < len(self.memory) and not misaligned: # RAM
                            mem_write = True
                            if f3 == 0: self.memory[mem_addr] = val2 & 0xFF
                            elif f3 == 1 and mem_addr + 2 <= len(self.memory): self.memory[mem_addr:mem_addr+2] = (val2 & 0xFFFF).to_bytes(2, 'little')
                            elif f3 == 2 and mem_addr + 4 <= len(self.memory): self.memory[mem_addr:mem_addr+4] = (val2 & 0xFFFFFFFF).to_bytes(4, 'little')
                            else: mem_write = False
                        else:
                            mem_write = False
                    else:
                        mem_write = False

        else:
            illegal = True

        # Safety squash on illegal
        if illegal:
            reg_write = False
            mem_read = False
            mem_read_req = False
            mem_write = False
            mem_write_req = False
            telem_valid = False

        if reg_write and rd != 0:
            self.regs[rd] = write_data
        self.regs[0] = 0

        self.pc = next_pc
        self.last_was_load = is_current_load
        self.last_load_rd = rd if is_current_load else 0

        cla_before = self.cla_switching
        mul_before = self.mul_thermal
        ret_before = self.retired_count
        last_pc_before = self.last_commit_pc

        # Update telemetry on architectural retirement
        if telem_valid:
            changed_bits = bin((telem_res ^ self.telemetry_prev_result) & 0xFFFFFFFF).count('1')
            if telem_cla:
                self.cla_switching += changed_bits
            if telem_mul:
                self.mul_thermal += changed_bits
            self.telemetry_prev_result = telem_res & 0xFFFFFFFF

        self.retired_count += 1
        self.last_commit_pc = current_pc

        # Snapshot for MEM stage visibility of next instruction:
        # If the pipeline flushes (taken branch, jumps),
        # this instruction commits in WB before the target/next instruction reaches MEM.
        # In straight-line back-to-back flow, the next instruction in MEM sees the state before this retirement.
        is_flush_or_long_stall = (opcode == 0x63 and take) or (opcode in (0x6F, 0x67))
        if is_flush_or_long_stall:
            self.mem_vis_cla_switching = self.cla_switching
            self.mem_vis_mul_thermal = self.mul_thermal
            self.mem_vis_retired_count = self.retired_count
            self.mem_vis_last_commit_pc = self.last_commit_pc
        else:
            self.mem_vis_cla_switching = cla_before
            self.mem_vis_mul_thermal = mul_before
            self.mem_vis_retired_count = ret_before
            self.mem_vis_last_commit_pc = last_pc_before

        event = CommitEvent(
            pc=current_pc,
            instruction=inst,
            rd=rd,
            regWrite=reg_write and rd != 0,
            writeData=write_data,
            memRead=mem_read,
            memReadReq=mem_read_req,
            memWrite=mem_write,
            memWriteReq=mem_write_req,
            memAddress=mem_addr,
            memWriteData=mem_write_data,
            illegal=illegal
        )
        self.trace.append(event)
        return event

    def run(self, max_steps: int = 1000) -> List[CommitEvent]:
        steps = 0
        while not self.halted and steps < max_steps:
            ev = self.step()
            if ev is None:
                break
            steps += 1
        return self.trace
