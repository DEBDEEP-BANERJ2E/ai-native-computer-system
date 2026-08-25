"""
Catalog of pre-packaged, validated demonstration scenarios for the Objective 2 Workbench.
Covers Canonical Benchmarks, Hazard Visualizer, RV32M Multi-Cycle, System MMIO Telemetry,
CapabilityLite Security Derivations, and Attacks A–J with Precise Trapping.
"""

from typing import Dict, Any, List

SCENARIO_CATALOG: Dict[str, Dict[str, Any]] = {
    # =========================================================================
    # Lab 2: Live RV32IM Execution / Canonical Benchmarks
    # =========================================================================
    "canon_prog1_alu": {
        "id": "canon_prog1_alu",
        "title": "Program 1: Arithmetic & RAW Forwarding Matrix",
        "lab": "lab2",
        "category": "Canonical RV32I",
        "description": "Comprehensive evaluation of basic ALU instructions with consecutive back-to-back RAW dependencies resolved by EX/MEM forwarding without NOPs.",
        "single_cycle_compatible": True,
        "max_cycles": 20,
        "assembly": """# Program 1: Arithmetic & RAW Matrix
addi x1, x0, 10
addi x2, x0, 20
add  x3, x1, x2
sub  x4, x3, x1
and  x5, x4, x2
or   x6, x5, x1
xor  x7, x6, x3
sll  x8, x7, x1
"""
    },
    "canon_prog2_loop": {
        "id": "canon_prog2_loop",
        "title": "Program 2: Loop Accumulation & Branch Hazard",
        "lab": "lab2",
        "category": "Canonical RV32I",
        "description": "Iterative loop that counts down from 3 to 0 while accumulating sum in x2. Tests conditional branch evaluation, loop convergence, and EX branch resolution.",
        "single_cycle_compatible": True,
        "max_cycles": 30,
        "assembly": """# Program 2: Loop Accumulation
addi x1, x0, 3
addi x2, x0, 0
addi x3, x0, 1
loop:
add  x2, x2, x1
sub  x1, x1, x3
bne  x1, x0, loop
addi x4, x2, 100
"""
    },
    "canon_prog3_mem": {
        "id": "canon_prog3_mem",
        "title": "Program 3: Memory Operations (Little-Endian SB/SH/SW)",
        "lab": "lab2",
        "category": "Canonical RV32I",
        "description": "Stores and loads 8-bit, 16-bit, and 32-bit values at memory offset 0x100 to verify little-endian byte ordering, sign-extension, and Load-Use interlock.",
        "single_cycle_compatible": True,
        "max_cycles": 25,
        "assembly": """# Program 3: Memory Operations Matrix
addi x1, x0, 0x100
addi x2, x0, 0x12
sb   x2, 0(x1)
addi x3, x0, 0x3456
sh   x3, 2(x1)
addi x4, x0, 0x78
sb   x4, 1(x1)
lw   x5, 0(x1)
lb   x6, 1(x1)
lh   x7, 2(x1)
lbu  x8, 1(x1)
"""
    },
    "canon_prog4_link": {
        "id": "canon_prog4_link",
        "title": "Program 4: Function Call Link & Return (JAL / JALR)",
        "lab": "lab2",
        "category": "Canonical RV32I",
        "description": "Demonstrates subroutine linkage via JAL (saving return PC in ra=x1) and return via JALR with LSB masking, verifying 2-cycle control hazard pipeline flushing.",
        "single_cycle_compatible": True,
        "max_cycles": 20,
        "assembly": """# Program 4: Function Link & Return
addi x2, x0, 15
jal  x1, double_val
addi x4, x3, 5
jal  x0, end_prog
double_val:
add  x3, x2, x2
jalr x0, 0(x1)
end_prog:
addi x5, x4, 1
"""
    },

    # =========================================================================
    # Lab 3: Pipeline Hazard & Forwarding Visualizer
    # =========================================================================
    "hazard_raw_exmem": {
        "id": "hazard_raw_exmem",
        "title": "RAW Data Hazard: EX/MEM & MEM/WB Forwarding",
        "lab": "lab3",
        "category": "Data Hazard",
        "description": "Instruction produces a result used immediately by the very next instruction. ForwardingUnit activates forwardA=2 (EX/MEM) and forwardB=1 (MEM/WB) to eliminate stalls.",
        "single_cycle_compatible": True,
        "max_cycles": 15,
        "assembly": """# RAW Hazard & Forwarding Demonstration
addi x1, x0, 42
add  x2, x1, x1
sub  x3, x2, x1
or   x4, x3, x2
and  x5, x4, x3
"""
    },
    "hazard_load_use": {
        "id": "hazard_load_use",
        "title": "Load-Use Hazard: 1-Cycle Stall & Bubble Insertion",
        "lab": "lab3",
        "category": "Data Hazard",
        "description": "An instruction immediately consumes data from a preceding LW. Since data is only available after MEM, HazardUnit freezes IF/ID for 1 cycle and inserts an ID/EX bubble.",
        "single_cycle_compatible": True,
        "max_cycles": 15,
        "assembly": """# Load-Use Interlock Demonstration
addi x1, x0, 0x100
addi x2, x0, 99
sw   x2, 0(x1)
lw   x3, 0(x1)
add  x4, x3, x2
sub  x5, x4, x3
"""
    },
    "hazard_branch_flush": {
        "id": "hazard_branch_flush",
        "title": "Branch/Jump Control Hazard: 2-Cycle Pipeline Flush",
        "lab": "lab3",
        "category": "Control Hazard",
        "description": "Sequential fetch encounters a taken BEQ resolved in EX stage. Pipeline squashes the 2 younger instructions in IF/ID and ID/EX, asserting flushIFID and flushIDEX.",
        "single_cycle_compatible": True,
        "max_cycles": 15,
        "assembly": """# Branch Control Hazard & Flush
addi x1, x0, 10
addi x2, x0, 10
beq  x1, x2, target
addi x3, x0, 999
addi x4, x0, 888
target:
addi x5, x0, 42
add  x6, x5, x1
"""
    },

    # =========================================================================
    # Lab 4: RV32M Arithmetic Lab (Booth-Wallace MUL & 33-Cycle DIV)
    # =========================================================================
    "rv32m_full_matrix": {
        "id": "rv32m_full_matrix",
        "title": "RV32M Arithmetic Matrix: 17-Group Booth MUL & 33-Cycle DIV",
        "lab": "lab4",
        "category": "Hardware Arithmetic",
        "description": "Exercises all 8 RV32M operations: signed/unsigned 34-bit Booth-Wallace multiplication (MUL, MULH, MULHSU, MULHU) and 33-cycle iterative division (DIV, DIVU, REM, REMU).",
        "single_cycle_compatible": False,
        "max_cycles": 80,
        "assembly": """# Full RV32M Multiplier & Iterative Divider
addi x1, x0, -17
addi x2, x0, 25
mul  x3, x1, x2
mulh x4, x1, x2
addi x5, x0, 100
addi x6, x0, 7
div  x7, x5, x6
rem  x8, x5, x6
divu x9, x5, x6
remu x10, x5, x6
"""
    },
    "rv32m_div_corners": {
        "id": "rv32m_div_corners",
        "title": "Divider Corner Cases: Divide-by-Zero & Overflow",
        "lab": "lab4",
        "category": "Hardware Arithmetic",
        "description": "Tests RISC-V specification corner cases: division by zero (returns -1 / dividend remainder) and signed overflow (0x80000000 / -1 returns 0x80000000 / 0).",
        "single_cycle_compatible": False,
        "max_cycles": 40,
        "assembly": """# Divider Hardware Corner Cases
addi x1, x0, 42
addi x2, x0, 0
div  x3, x1, x2
rem  x4, x1, x2
lui  x5, 0x80000
addi x6, x0, -1
div  x7, x5, x6
rem  x8, x5, x6
"""
    },

    # =========================================================================
    # Lab 5: MMIO & Cross-Layer Telemetry Dashboard
    # =========================================================================
    "mmio_cross_layer": {
        "id": "mmio_cross_layer",
        "title": "Cross-Layer System MMIO, Telemetry & Performance Counters",
        "lab": "lab5",
        "category": "System MMIO",
        "description": "Architectural writes to PROCESS_BEHAVIOR_CLASS (0x80002004), SCHED_HINT (0x80002008), and CURRENT_CONTEXT (0x80002024), followed by reading hardware event counters.",
        "single_cycle_compatible": False,
        "max_cycles": 40,
        "assembly": """# Cross-Layer Telemetry & System MMIO
lui  x10, 0x80002
lui  x20, 0x80001
addi x1, x0, 42
sw   x1, 4(x10)
lw   x3, 4(x10)
addi x2, x0, 3
sw   x2, 8(x10)
lw   x4, 8(x10)
addi x5, x0, 10
addi x6, x0, 20
add  x7, x5, x6
mul  x8, x5, x6
lw   x14, 12(x10)
lw   x15, 16(x10)
lw   x16, 20(x10)
lw   x17, 24(x10)
lw   x18, 28(x10)
lw   x19, 4(x20)
"""
    },

    # =========================================================================
    # Lab 6: CapabilityLite Security Playground
    # =========================================================================
    "cap_derivation_chain": {
        "id": "cap_derivation_chain",
        "title": "Monotonic Capability Derivation & Bounded Authority",
        "lab": "lab6",
        "category": "CapabilityLite",
        "description": "Derives bounded buffer from RAM root c1 (CSETBOUNDS base 0x200, length 16), attenuates permissions to Read-Only (CANDPERM 0x1), advances cursor (CINCOFFSET), and inspects fields.",
        "single_cycle_compatible": False,
        "max_cycles": 30,
        "assembly": """# Monotonic Authority Reduction Chain
addi x5, x0, 0x200
cincoffset c3, c1, x5
addi x6, x0, 16
csetbounds c3, c3, x6
addi x7, x0, 0x55
csw  x7, 0(c3)
addi x8, x0, 1
candperm   c4, c3, x8
clw  x9, 0(c4)
cgetbase   x10, c4
cgetlen    x11, c4
cgetperm   x12, c4
cgetoffset x13, c4
cclear     c5
"""
    },

    # =========================================================================
    # Lab 7: Attack & Precise Trap Demonstrator
    # =========================================================================
    "attack_buffer_overflow": {
        "id": "attack_buffer_overflow",
        "title": "Attack 1: Spatial Buffer Overflow Containment",
        "lab": "lab7",
        "category": "Security Attack",
        "description": "Process allocates a 16-byte buffer (c3) and attempts an out-of-bounds write at offset 20. CapabilityChecker denies access, takePreciseTrap fires, suppresses RAM write, flushes younger ADD, and vectors to trap handler.",
        "single_cycle_compatible": False,
        "max_cycles": 60,
        "assembly": """# Attack 1: Out-of-Bounds Buffer Overflow
lui  x10, 0x80002
addi x5, x0, 0x80
sw   x5, 0x11C(x10)
addi x5, x0, 1
sw   x5, 0x114(x10)
addi x5, x0, 0x200
cincoffset c3, c1, x5
addi x6, x0, 16
csetbounds c3, c3, x6
addi x7, x0, 0x77
csw  x7, 20(c3)
addi x14, x0, 999
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
lw   x11, 0x118(x10)
lw   x12, 0x120(x10)
lw   x13, 0x124(x10)
lw   x15, 0x128(x10)
"""
    },
    "attack_readonly_violation": {
        "id": "attack_readonly_violation",
        "title": "Attack 2: Privilege Escalation & Read-Only Violation",
        "lab": "lab7",
        "category": "Security Attack",
        "description": "Process receives a Read-Only capability (perms = 1) and attempts a CSW write. Hardware blocks writeback, captures TRAP_CAUSE = WRITE_PERMISSION (0x14), and enters trap handler.",
        "single_cycle_compatible": False,
        "max_cycles": 60,
        "assembly": """# Attack 2: Read-Only Violation
lui  x10, 0x80002
addi x5, x0, 0x80
sw   x5, 0x11C(x10)
addi x5, x0, 1
sw   x5, 0x114(x10)
addi x5, x0, 1
candperm c3, c1, x5
addi x7, x0, 0x99
csw  x7, 0(c3)
addi x14, x0, 888
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
lw   x11, 0x118(x10)
lw   x12, 0x120(x10)
lw   x13, 0x124(x10)
lw   x15, 0x128(x10)
"""
    },
    "attack_null_deref": {
        "id": "attack_null_deref",
        "title": "Attack 3: NULL / Uninitialized Capability Dereference",
        "lab": "lab7",
        "category": "Security Attack",
        "description": "Attempting memory store through uninitialized register c4 (tag = 0). Immediate denial with INVALID_CAPABILITY cause (0x13), zero memory corruption.",
        "single_cycle_compatible": False,
        "max_cycles": 60,
        "assembly": """# Attack 3: NULL Capability Access
lui  x10, 0x80002
addi x5, x0, 0x80
sw   x5, 0x11C(x10)
addi x5, x0, 1
sw   x5, 0x114(x10)
addi x7, x0, 0x33
csw  x7, 0(c4)
addi x14, x0, 777
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
lw   x11, 0x118(x10)
lw   x12, 0x120(x10)
lw   x13, 0x124(x10)
lw   x15, 0x128(x10)
"""
    },
    "attack_trap_vs_div": {
        "id": "attack_trap_vs_div",
        "title": "Attack 4: MEM Trap over Active Divider Kill (No Deadlock)",
        "lab": "lab7",
        "category": "Security Attack",
        "description": "A faulting memory access occurs while a multi-cycle DIV is running in EX stage. MEM trap fires takePreciseTrap, asserts divRem.io.kill, cleanly aborts the divider, and avoids pipeline deadlock.",
        "single_cycle_compatible": False,
        "max_cycles": 60,
        "assembly": """# Attack 4: MEM Trap vs Active Divider Kill
lui  x10, 0x80002
addi x5, x0, 0x80
sw   x5, 0x11C(x10)
addi x5, x0, 1
sw   x5, 0x114(x10)
addi x6, x0, 100
addi x7, x0, 10
div  x8, x6, x7
csw  x6, 0(c4)
addi x14, x0, 666
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
lw   x11, 0x118(x10)
lw   x12, 0x120(x10)
lw   x13, 0x124(x10)
lw   x15, 0x128(x10)
"""
    },
    "attack_bounds_retry": {
        "id": "attack_bounds_retry",
        "title": "Attack 5: Bounds Expansion & Fault Retry via TRAP_RETURN",
        "lab": "lab7",
        "category": "Security Attack",
        "description": "Faulting access triggers trap; OS trap handler expands capability bounds in c3 and executes TRAP_RETURN (0x80002130), returning to faulting PC and successfully executing the retry.",
        "single_cycle_compatible": False,
        "max_cycles": 70,
        "assembly": """# Attack 5: Bounds Expansion & Retry
lui  x10, 0x80002
addi x5, x0, 0x80
sw   x5, 0x11C(x10)
addi x5, x0, 1
sw   x5, 0x114(x10)
addi x5, x0, 0x200
cincoffset c3, c1, x5
addi x6, x0, 16
csetbounds c3, c3, x6
addi x7, x0, 0x77
csw  x7, 20(c3)
addi x14, x0, 555
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
nop
addi x6, x0, 64
csetbounds c3, c1, x6
addi x8, x0, 1
sw   x8, 0x130(x10)
"""
    }
}
