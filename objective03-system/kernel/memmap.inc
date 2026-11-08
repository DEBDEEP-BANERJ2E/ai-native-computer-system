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
