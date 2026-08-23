def emit_i(imm, rs1, f3, rd, opcode):
    imm = imm & 0xFFF
    inst = (imm << 20) | (rs1 << 15) | (f3 << 12) | (rd << 7) | opcode
    return f"{inst:08x}"

def emit_r(f7, rs2, rs1, f3, rd, opcode):
    inst = (f7 << 25) | (rs2 << 20) | (rs1 << 15) | (f3 << 12) | (rd << 7) | opcode
    return f"{inst:08x}"

OP_IMM = 0x13
OP_R = 0x33

print("addi x1, x0, -2 :", emit_i(-2, 0, 0, 1, OP_IMM))
print("addi x2, x0, 3 :", emit_i(3, 0, 0, 2, OP_IMM))
print("mulh x3, x1, x2 :", emit_r(1, 2, 1, 1, 3, OP_R))

print("addi x1, x0, -2 :", emit_i(-2, 0, 0, 1, OP_IMM))
print("addi x2, x0, -1 :", emit_i(-1, 0, 0, 2, OP_IMM))
print("mulhsu x4, x1, x2 :", emit_r(1, 2, 1, 2, 4, OP_R))

print("addi x1, x0, -1 :", emit_i(-1, 0, 0, 1, OP_IMM))
print("addi x2, x0, -1 :", emit_i(-1, 0, 0, 2, OP_IMM))
print("mulhu x5, x1, x2 :", emit_r(1, 2, 1, 3, 5, OP_R))

print("addi x1, x0, 10 :", emit_i(10, 0, 0, 1, OP_IMM))
print("addi x2, x0, 3 :", emit_i(3, 0, 0, 2, OP_IMM))
print("div x6, x1, x2 :", emit_r(1, 2, 1, 4, 6, OP_R))

print("add x7, x6, x2 :", emit_r(0, 2, 6, 0, 7, OP_R))

print("addi x1, x0, -1 :", emit_i(-1, 0, 0, 1, OP_IMM))
print("addi x2, x0, 3 :", emit_i(3, 0, 0, 2, OP_IMM))
print("divu x8, x1, x2 :", emit_r(1, 2, 1, 5, 8, OP_R))

print("addi x1, x0, 10 :", emit_i(10, 0, 0, 1, OP_IMM))
print("addi x2, x0, 3 :", emit_i(3, 0, 0, 2, OP_IMM))
print("rem x9, x1, x2 :", emit_r(1, 2, 1, 6, 9, OP_R))

print("addi x1, x0, -1 :", emit_i(-1, 0, 0, 1, OP_IMM))
print("addi x2, x0, 3 :", emit_i(3, 0, 0, 2, OP_IMM))
print("remu x10, x1, x2 :", emit_r(1, 2, 1, 7, 10, OP_R))
