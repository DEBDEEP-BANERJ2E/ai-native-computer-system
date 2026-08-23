#!/usr/bin/env python3
"""
RV32I + MUL Reference Interpreter for Objective 2 Differential Verification.
"""

import sys
from typing import List, Dict

class RV32Interpreter:
    def __init__(self, memory_size: int = 4096):
        self.regs = [0] * 32
        self.memory = bytearray(memory_size)
        self.pc = 0
        self.halted = False

    def reset(self):
        self.regs = [0] * 32
        self.memory = bytearray(len(self.memory))
        self.pc = 0
        self.halted = False

    def load_program(self, words: List[int], start_addr: int = 0):
        for i, word in enumerate(words):
            addr = start_addr + i * 4
            self.memory[addr:addr+4] = int(word & 0xFFFFFFFF).to_bytes(4, 'little')

    def to_signed(self, val: int, bits: int = 32) -> int:
        val = val & ((1 << bits) - 1)
        if val & (1 << (bits - 1)):
            return val - (1 << bits)
        return val

    def step(self):
        if self.pc < 0 or self.pc + 4 > len(self.memory):
            self.halted = True
            return

        inst = int.from_bytes(self.memory[self.pc:self.pc+4], 'little')
        opcode = inst & 0x7F
        rd = (inst >> 7) & 0x1F
        funct3 = (inst >> 12) & 0x07
        rs1 = (inst >> 15) & 0x1F
        rs2 = (inst >> 20) & 0x1F
        funct7 = (inst >> 25) & 0x7F

        next_pc = self.pc + 4

        # I-type imm
        imm_i = self.to_signed(inst >> 20, 12)
        # S-type imm
        imm_s = self.to_signed(((inst >> 25) << 5) | ((inst >> 7) & 0x1F), 12)
        # B-type imm
        imm_b = self.to_signed(
            (((inst >> 31) & 0x1) << 12) |
            (((inst >> 7) & 0x1) << 11) |
            (((inst >> 25) & 0x3F) << 5) |
            (((inst >> 8) & 0xF) << 1), 13
        )
        # U-type imm
        imm_u = (inst & 0xFFFFF000)
        # J-type imm
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

        # Execute
        if opcode == 0x33: # R-Type & MUL
            if funct7 == 0x00:
                if funct3 == 0: res = (val1 + val2) & 0xFFFFFFFF # ADD
                elif funct3 == 1: res = (val1 << (val2 & 0x1F)) & 0xFFFFFFFF # SLL
                elif funct3 == 2: res = 1 if s_val1 < s_val2 else 0 # SLT
                elif funct3 == 3: res = 1 if val1 < val2 else 0 # SLTU
                elif funct3 == 4: res = val1 ^ val2 # XOR
                elif funct3 == 5: res = (val1 >> (val2 & 0x1F)) & 0xFFFFFFFF # SRL
                elif funct3 == 6: res = val1 | val2 # OR
                elif funct3 == 7: res = val1 & val2 # AND
                else: res = 0
            elif funct7 == 0x20:
                if funct3 == 0: res = (val1 - val2) & 0xFFFFFFFF # SUB
                elif funct3 == 5: res = (s_val1 >> (val2 & 0x1F)) & 0xFFFFFFFF # SRA
                else: res = 0
            elif funct7 == 0x01 and funct3 == 0: # MUL
                res = (s_val1 * s_val2) & 0xFFFFFFFF
            else:
                res = 0
            if rd != 0: self.regs[rd] = res

        elif opcode == 0x13: # I-Type
            if funct3 == 0: res = (val1 + imm_i) & 0xFFFFFFFF # ADDI
            elif funct3 == 2: res = 1 if s_val1 < imm_i else 0 # SLTI
            elif funct3 == 3: res = 1 if val1 < (imm_i & 0xFFFFFFFF) else 0 # SLTIU
            elif funct3 == 4: res = val1 ^ (imm_i & 0xFFFFFFFF) # XORI
            elif funct3 == 6: res = val1 | (imm_i & 0xFFFFFFFF) # ORI
            elif funct3 == 7: res = val1 & (imm_i & 0xFFFFFFFF) # ANDI
            elif funct3 == 1 and funct7 == 0x00: # SLLI
                shamt = (inst >> 20) & 0x1F
                res = (val1 << shamt) & 0xFFFFFFFF
            elif funct3 == 5 and funct7 == 0x00: # SRLI
                shamt = (inst >> 20) & 0x1F
                res = (val1 >> shamt) & 0xFFFFFFFF
            elif funct3 == 5 and funct7 == 0x20: # SRAI
                shamt = (inst >> 20) & 0x1F
                res = (s_val1 >> shamt) & 0xFFFFFFFF
            else: res = 0
            if rd != 0: self.regs[rd] = res

        elif opcode == 0x03: # Load
            addr = (val1 + imm_i) & 0xFFFFFFFF
            if addr + 4 <= len(self.memory):
                if funct3 == 0: # LB
                    res = self.to_signed(self.memory[addr], 8) & 0xFFFFFFFF
                elif funct3 == 1: # LH
                    raw = int.from_bytes(self.memory[addr:addr+2], 'little', signed=True)
                    res = raw & 0xFFFFFFFF
                elif funct3 == 2: # LW
                    res = int.from_bytes(self.memory[addr:addr+4], 'little', signed=False)
                elif funct3 == 4: # LBU
                    res = self.memory[addr]
                elif funct3 == 5: # LHU
                    res = int.from_bytes(self.memory[addr:addr+2], 'little', signed=False)
                else: res = 0
                if rd != 0: self.regs[rd] = res

        elif opcode == 0x23: # Store
            addr = (val1 + imm_s) & 0xFFFFFFFF
            if addr < len(self.memory):
                if funct3 == 0: # SB
                    self.memory[addr] = val2 & 0xFF
                elif funct3 == 1 and addr + 2 <= len(self.memory): # SH
                    self.memory[addr:addr+2] = (val2 & 0xFFFF).to_bytes(2, 'little')
                elif funct3 == 2 and addr + 4 <= len(self.memory): # SW
                    self.memory[addr:addr+4] = (val2 & 0xFFFFFFFF).to_bytes(4, 'little')

        elif opcode == 0x63: # Branch
            take = False
            if funct3 == 0: take = (val1 == val2) # BEQ
            elif funct3 == 1: take = (val1 != val2) # BNE
            elif funct3 == 4: take = (s_val1 < s_val2) # BLT
            elif funct3 == 5: take = (s_val1 >= s_val2) # BGE
            elif funct3 == 6: take = (val1 < val2) # BLTU
            elif funct3 == 7: take = (val1 >= val2) # BGEU
            if take:
                next_pc = (self.pc + imm_b) & 0xFFFFFFFF

        elif opcode == 0x6F: # JAL
            if rd != 0: self.regs[rd] = (self.pc + 4) & 0xFFFFFFFF
            next_pc = (self.pc + imm_j) & 0xFFFFFFFF

        elif opcode == 0x67: # JALR
            if rd != 0: self.regs[rd] = (self.pc + 4) & 0xFFFFFFFF
            next_pc = (val1 + imm_i) & ~1 & 0xFFFFFFFF

        elif opcode == 0x37: # LUI
            if rd != 0: self.regs[rd] = imm_u & 0xFFFFFFFF

        elif opcode == 0x17: # AUIPC
            if rd != 0: self.regs[rd] = (self.pc + imm_u) & 0xFFFFFFFF

        self.regs[0] = 0
        self.pc = next_pc

    def run(self, max_steps: int = 1000):
        steps = 0
        while not self.halted and steps < max_steps:
            self.step()
            steps += 1
        return steps

if __name__ == "__main__":
    interp = RV32Interpreter()
    prog = [0x00a00093, 0x01400113, 0x002081b3, 0x40118233] # addi x1, 10; addi x2, 20; add x3, x1, x2; sub x4, x3, x1
    interp.load_program(prog)
    interp.run(4)
    print(f"Final State: x1={interp.regs[1]}, x2={interp.regs[2]}, x3={interp.regs[3]}, x4={interp.regs[4]}")
    assert interp.regs[1] == 10
    assert interp.regs[2] == 20
    assert interp.regs[3] == 30
    assert interp.regs[4] == 20
    print("RV32I Reference Interpreter Self-Test Passed!")
