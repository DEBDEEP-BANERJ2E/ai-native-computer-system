// AN32 System-v1 physical memory layout — SINGLE SOURCE OF TRUTH.
// This file is the origin for the linker script, loader, kernel memory
// manager, and (Stage C) Sv32 page tables. Do not hard-code these
// addresses in assembly; .include this file.
//
// Physical data memory (dmem): 64 KiB, byte addresses 0x0000_0000-0x0000_FFFF.
// Instruction memory (imem) is a separate physical region; kernel code
// addresses below are IMEM offsets, not dmem.

#ifndef AN32_MEMMAP_H
#define AN32_MEMMAP_H

// ---- Physical data-memory (stack/data) regions ----
#define DMEM_BASE          0x00000000u
#define DMEM_SIZE          0x00010000u          // 64 KiB
#define DMEM_TOP           0x00010000u          // one past end

// Stack regions grow DOWNWARD; keep sp 16-byte aligned.
#define M_STACK_TOP        0x00010000u          // machine boot stack top
#define M_STACK_SIZE       0x00001000u          // 4 KiB
#define M_STACK_BASE       (M_STACK_TOP - M_STACK_SIZE)   // 0xF000

#define S_STACK_TOP        0x0000F000u          // supervisor kernel stack
#define S_STACK_SIZE       0x00001000u          // 4 KiB
#define S_STACK_BASE       (S_STACK_TOP - S_STACK_SIZE)   // 0xE000

#define U_STACK_TOP        0x0000E000u          // user stack
#define U_STACK_SIZE       0x00001000u          // 4 KiB
#define U_STACK_BASE       (U_STACK_TOP - U_STACK_SIZE)   // 0xD000

// Early kernel/trap scratch data (trap frames, PCB staging) — reserved.
#define KDATA_BASE         0x0000C000u
#define KDATA_SIZE         0x00001000u          // 4 KiB

// MMIO windows (from SystemMMIO / TimerBlock) — kept distinct from RAM.
// (filled in as the platform map solidifies)

#endif // AN32_MEMMAP_H
