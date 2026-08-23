#!/usr/bin/env python3
"""
RV32IM Reference Interpreter for Objective 2 Differential Verification.
Mirrors hardware execution semantics including little-endian byte lanes,
misalignment rejection, full RV32M extension, Phase 6 System MMIO & Telemetry,
and cycle-by-cycle commit event logging.
"""

from typing import List, Dict, Optional, Tuple, NamedTuple

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
        self.memory = bytearray(memory_size)
        self.pc = 0
        self.halted = False
        self.trace: List[CommitEvent] = []

        # Phase 6: System MMIO, Telemetry & Performance Counter State
        self.mmio_regs: Dict[int, int] = {
            0x80002004: 0, # PROCESS_BEHAVIOR_CLASS (RW)
            0x80002008: 0, # SCHED_HINT (RW)
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

    def reset(self):
        self.regs = [0] * 32
        self.memory = bytearray(self.memory_size)
        self.pc = 0
        self.halted = False
        self.trace = []
        self.mmio_regs = {
            0x80002004: 0,
            0x80002008: 0,
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

    def load_program(self, words: List[int], start_addr: int = 0):
        for i, word in enumerate(words):
            addr = start_addr + i * 4
            if addr + 4 <= len(self.memory):
                self.memory[addr:addr+4] = int(word & 0xFFFFFFFF).to_bytes(4, 'little')

    def to_signed(self, val: int, bits: int = 32) -> int:
        val = val & ((1 << bits) - 1)
        if val & (1 << (bits - 1)):
            return val - (1 << bits)
        return val

    def step(self) -> Optional[CommitEvent]:
        if self.pc < 0 or self.pc + 4 > len(self.memory):
            self.halted = True
            return None

        current_pc = self.pc
        inst = int.from_bytes(self.memory[current_pc:current_pc+4], 'little')
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
            telem_valid = True; telem_res = (val1 ^ val2) & 0xFFFFFFFF
            if funct3 == 0: take = (val1 == val2) # BEQ
            elif funct3 == 1: take = (val1 != val2) # BNE
            elif funct3 == 4: take = (s_val1 < s_val2) # BLT
            elif funct3 == 5: take = (s_val1 >= s_val2) # BGE
            elif funct3 == 6: take = (val1 < val2) # BLTU
            elif funct3 == 7: take = (val1 >= val2) # BGEU
            else: illegal = True
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

        else:
            illegal = True

        # Safety squash on illegal
        if illegal:
            reg_write = False
            mem_read = False
            mem_read_req = False
            mem_write = False
            mem_write_req = False

        if reg_write and rd != 0:
            self.regs[rd] = write_data
        self.regs[0] = 0

        self.pc = next_pc
        self.last_was_load = is_current_load
        self.last_load_rd = rd if is_current_load else 0

        # Snapshot for MEM stage visibility of next instruction
        self.mem_vis_cla_switching = self.cla_switching
        self.mem_vis_mul_thermal = self.mul_thermal
        self.mem_vis_retired_count = self.retired_count
        self.mem_vis_last_commit_pc = self.last_commit_pc

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
