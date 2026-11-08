# AN32 System-v1 physical memory layout - ASSEMBLY source of truth.
# Mirrors kernel/memmap.h (C/tooling side). Keep the two in sync; a future
# linker script will consume the same numbers. Stacks grow DOWNWARD,
# sp stays 16-byte aligned. Addresses are DMEM (data memory) offsets.

.equ DMEM_BASE,      0x00000000
.equ DMEM_SIZE,      0x00010000
.equ DMEM_TOP,       0x00010000

# Stack tops (first push lands at TOP-16 after alignment).
.equ M_STACK_TOP,    0x00010000     # machine boot stack
.equ M_STACK_SIZE,   0x00001000
.equ M_STACK_BASE,   0x0000F000

.equ S_STACK_TOP,    0x0000F000     # supervisor kernel stack
.equ S_STACK_SIZE,   0x00001000
.equ S_STACK_BASE,   0x0000E000

.equ U_STACK_TOP,    0x0000E000     # user stack
.equ U_STACK_SIZE,   0x00001000
.equ U_STACK_BASE,   0x0000D000

.equ KDATA_BASE,     0x0000C000     # early kernel/trap scratch data
.equ KDATA_SIZE,     0x00001000

# ---- B3 trap-frame ABI (144 bytes, 16B-aligned) ----
# Layout: x1..x31 at (N-1)*4, then sepc/sstatus/scause/stval.
# TF_SP slot holds the TRAPPED user sp (from sscratch), not kernel sp.
.equ TF_SIZE,    144
.equ TF_SEPC,    124
.equ TF_SSTATUS, 128
.equ TF_SCAUSE,  132
.equ TF_STVAL,   136
# KDATA observation area: kernel copies select frame fields here for the test.
.equ KOBS_BASE,  0x0000C000
.equ KOBS_SP,    0x0000C000   # trapped user sp
.equ KOBS_RA,    0x0000C004   # x1
.equ KOBS_S0,    0x0000C008   # x8 saved reg
.equ KOBS_T0,    0x0000C00C   # x5 temp
.equ KOBS_SEPC,  0x0000C010
.equ KOBS_SCAUSE,0x0000C014
.equ KOBS_SSTATUS,0x0000C018
