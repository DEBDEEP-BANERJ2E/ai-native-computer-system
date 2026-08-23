#!/usr/bin/env python3
"""
Differential Verification Test: Checks all 5 benchmark programs in the Python reference interpreter.
"""

from rv32i_interpreter import RV32Interpreter

def test_program1():
    print("Running Program 1: Arithmetic & Logical Matrix...")
    interp = RV32Interpreter()
    prog = [
        0x00a00093, # addi x1, x0, 10
        0x01400113, # addi x2, x0, 20
        0x002081b3, # add  x3, x1, x2  (30)
        0x40118233, # sub  x4, x3, x1  (20)
        0x003222b3, # slt  x5, x4, x3  (1)
        0x0020c333, # xor  x6, x1, x2  (30)
        0x0020e3b3, # or   x7, x1, x2  (30)
        0x0020f433  # and  x8, x1, x2  (0)
    ]
    interp.load_program(prog)
    interp.run(8)
    assert interp.regs[1] == 10
    assert interp.regs[2] == 20
    assert interp.regs[3] == 30
    assert interp.regs[4] == 20
    assert interp.regs[5] == 1
    assert interp.regs[6] == 30
    assert interp.regs[7] == 30
    assert interp.regs[8] == 0
    print("  -> Program 1 Passed! [x1=10, x2=20, x3=30, x4=20, x5=1, x6=30, x7=30, x8=0]")

def test_program2():
    print("Running Program 2: Loop Accumulation (5+4+3+2+1=15)...")
    interp = RV32Interpreter()
    prog = [
        0x00500093, # addi x1, x0, 5
        0x00000113, # addi x2, x0, 0
        0x00110133, # add  x2, x2, x1 (loop @ 0x08)
        0xfff08093, # addi x1, x1, -1
        0xfe009ce3, # bne  x1, x0, -8 (target 0x08)
        0x00000013  # nop
    ]
    interp.load_program(prog)
    for _ in range(18):
        interp.step()
    assert interp.regs[1] == 0
    assert interp.regs[2] == 15
    assert interp.pc == 0x18
    print("  -> Program 2 Passed! [x1=0, x2=15, final PC=0x18]")

def test_program3():
    print("Running Program 3: Memory Loads & Stores...")
    interp = RV32Interpreter()
    prog = [
        0x02a00093, # addi x1, x0, 42
        0x00102023, # sw   x1, 0(x0)
        0x00002103, # lw   x2, 0(x0)
        0xffb00193, # addi x3, x0, -5
        0x00300223, # sb   x3, 4(x0)
        0x00400203, # lb   x4, 4(x0)
        0x00404283, # lbu  x5, 4(x0)
        0xc1800313, # addi x6, x0, -1000
        0x00601323, # sh   x6, 6(x0)
        0x00601383, # lh   x7, 6(x0)
        0x00605403  # lhu  x8, 6(x0)
    ]
    interp.load_program(prog)
    interp.run(11)
    assert interp.regs[2] == 42
    assert interp.to_signed(interp.regs[4]) == -5
    assert interp.regs[5] == 251
    assert interp.to_signed(interp.regs[7]) == -1000
    assert interp.regs[8] == 0xFC18
    print("  -> Program 3 Passed! [LW=42, LB=-5, LBU=251, LH=-1000, LHU=64536]")

def test_program4():
    print("Running Program 4: JAL / JALR Subroutine Call...")
    interp = RV32Interpreter()
    prog = [
        0x03200513, # addi x10, x0, 50
        0x010000ef, # jal  x1, 16      (func @ 0x14, link x1 = 0x08)
        0x00a50613, # addi x12, x10, 10
        0x0100006f, # jal  x0, 16      (done @ 0x1C)
        0x3e700713, # addi x14, x0, 999
        0x01950513, # addi x10, x10, 25 (func: x10 = 75)
        0x00008067, # jalr x0, 0(x1)   (return)
        0x00100693  # addi x13, x0, 1   (done: x13 = 1)
    ]
    interp.load_program(prog)
    for _ in range(7):
        interp.step()
    assert interp.regs[10] == 75
    assert interp.regs[12] == 85
    assert interp.regs[13] == 1
    assert interp.regs[14] == 0
    print("  -> Program 4 Passed! [x10=75, x12=85, x13=1, x14=0]")

def test_program5():
    print("Running Program 5: Hardware Multiplication...")
    interp = RV32Interpreter()
    prog = [
        0x00700093, # addi x1, x0, 7
        0xffb00113, # addi x2, x0, -5
        0x022081b3, # mul  x3, x1, x2
        0x00003237, # lui  x4, 3
        0x03920213, # addi x4, x4, 57
        0x7d000293, # addi x5, x0, 2000
        0x02520333  # mul  x6, x4, x5
    ]
    interp.load_program(prog)
    interp.run(7)
    assert interp.to_signed(interp.regs[3]) == -35
    assert interp.regs[6] == 24690000
    print("  -> Program 5 Passed! [7 * -5 = -35, 12345 * 2000 = 24690000]")

if __name__ == "__main__":
    test_program1()
    test_program2()
    test_program3()
    test_program4()
    test_program5()
    print("\nALL 5 DIFFERENTIAL BENCHMARK TESTS PASSED 100%!")
