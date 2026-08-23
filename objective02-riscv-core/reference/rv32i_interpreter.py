#!/usr/bin/env python3
"""
RV32I + MUL Reference Interpreter for Objective 2 Differential Verification.
Mirrors hardware execution semantics including little-endian byte lanes,
misalignment rejection, and cycle-by-cycle commit event logging.
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

    def reset(self):
        self.regs = [0] * 32
        self.memory = bytearray(self.memory_size)
        self.pc = 0
        self.halted = False
        self.trace = []

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

        if opcode == 0x33: # R-Type & MUL
            if funct7 == 0x00:
                reg_write = True
                if funct3 == 0: write_data = (val1 + val2) & 0xFFFFFFFF # ADD
                elif funct3 == 1: write_data = (val1 << (val2 & 0x1F)) & 0xFFFFFFFF # SLL
                elif funct3 == 2: write_data = 1 if s_val1 < s_val2 else 0 # SLT
                elif funct3 == 3: write_data = 1 if val1 < val2 else 0 # SLTU
                elif funct3 == 4: write_data = val1 ^ val2 # XOR
                elif funct3 == 5: write_data = (val1 >> (val2 & 0x1F)) & 0xFFFFFFFF # SRL
                elif funct3 == 6: write_data = val1 | val2 # OR
                elif funct3 == 7: write_data = val1 & val2 # AND
                else: illegal = True
            elif funct7 == 0x20:
                if funct3 == 0:
                    reg_write = True
                    write_data = (val1 - val2) & 0xFFFFFFFF # SUB
                elif funct3 == 5:
                    reg_write = True
                    write_data = (s_val1 >> (val2 & 0x1F)) & 0xFFFFFFFF # SRA
                else:
                    illegal = True
            elif funct7 == 0x01 and funct3 == 0: # MUL
                reg_write = True
                write_data = (s_val1 * s_val2) & 0xFFFFFFFF
            else:
                illegal = True

        elif opcode == 0x13: # I-Type
            reg_write = True
            if funct3 == 0: write_data = (val1 + imm_i) & 0xFFFFFFFF # ADDI
            elif funct3 == 2: write_data = 1 if s_val1 < imm_i else 0 # SLTI
            elif funct3 == 3: write_data = 1 if val1 < (imm_i & 0xFFFFFFFF) else 0 # SLTIU
            elif funct3 == 4: write_data = val1 ^ (imm_i & 0xFFFFFFFF) # XORI
            elif funct3 == 6: write_data = val1 | (imm_i & 0xFFFFFFFF) # ORI
            elif funct3 == 7: write_data = val1 & (imm_i & 0xFFFFFFFF) # ANDI
            elif funct3 == 1 and funct7 == 0x00: # SLLI
                shamt = (inst >> 20) & 0x1F
                write_data = (val1 << shamt) & 0xFFFFFFFF
            elif funct3 == 5 and funct7 == 0x00: # SRLI
                shamt = (inst >> 20) & 0x1F
                write_data = (val1 >> shamt) & 0xFFFFFFFF
            elif funct3 == 5 and funct7 == 0x20: # SRAI
                shamt = (inst >> 20) & 0x1F
                write_data = (s_val1 >> shamt) & 0xFFFFFFFF
            else:
                reg_write = False
                illegal = True

        elif opcode == 0x03: # Load
            mem_read_req = True
            mem_addr = (val1 + imm_i) & 0xFFFFFFFF
            is_half = (funct3 == 1 or funct3 == 5)
            is_word = (funct3 == 2)
            misaligned = (is_half and (mem_addr & 1 != 0)) or (is_word and (mem_addr & 3 != 0))

            if mem_addr < len(self.memory) and not misaligned:
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

            if mem_addr < len(self.memory) and not misaligned:
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
            if funct3 == 0: take = (val1 == val2) # BEQ
            elif funct3 == 1: take = (val1 != val2) # BNE
            elif funct3 == 4: take = (s_val1 < s_val2) # BLT
            elif funct3 == 5: take = (s_val1 >= s_val2) # BGE
            elif funct3 == 6: take = (val1 < val2) # BLTU
            elif funct3 == 7: take = (val1 >= val2) # BGEU
            else: illegal = True
            if take:
                next_pc = (current_pc + imm_b) & 0xFFFFFFFF

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
