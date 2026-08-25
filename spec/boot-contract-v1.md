# AI-Native Boot, Reset & Runtime Initialization Contract v1.0

**Author**: Advanced Agentic Coding & Systems Engineering  
**Status**: APPROVED ARCHITECTURAL CONTRACT  

---

## 1. Reset Architecture & Boot Chain

Upon hardware power-on or deassertion of the synchronous reset line:

```
Hardware Reset Line Deasserted
            │
            ▼
PC := 0x00000000 (Hardware Reset Vector)
            │
            ▼
_start Entry Routine (crt0.s)
  1. Initialize Global Pointer (gp)
  2. Initialize Stack Pointer (sp = _stack_top, 16-byte aligned)
  3. Zero .bss section (__bss_start to __bss_end)
  4. Copy .data from LMA to VMA (if ROM/Flash boot)
  5. Configure Precise Trap Vector (TRAP_VECTOR = 0x8000211C)
            │
            ▼
Transfer Control: call kernel_main(argc, argv)
            │
            ▼
Kernel / Application Execution
            │
            ▼
System Shutdown / Exit: wfi loop or trap trigger
```

---

## 2. Canonical `_start` Assembly Routine (`crt0.s`)

```assembly
.section .text.init
.globl _start
.type _start, @function

_start:
    # 1. Disable interrupts / clear status registers
    # (In AN32-Bare-v1, hardware resets with clean state)

    # 2. Initialize global pointer for small data access
.option push
.option norelax
    la    gp, __global_pointer$
.option pop

    # 3. Initialize 16-byte aligned stack pointer from linker symbol
    la    sp, _stack_top
    andi  sp, sp, -16           # Force 16-byte alignment

    # 4. Zero the .bss segment
    la    t0, __bss_start
    la    t1, __bss_end
.Lzero_bss_loop:
    bgeu  t0, t1, .Lzero_bss_done
    sw    zero, 0(t0)
    addi  t0, t0, 4
    j     .Lzero_bss_loop
.Lzero_bss_done:

    # 5. Configure Precise Trap Vector Register
    la    t0, _trap_entry
    li    t1, 0x8000211C        # MMIO Address of TRAP_VECTOR
    sw    t0, 0(t1)

    # 6. Call kernel / program main
    li    a0, 0                 # argc = 0
    li    a1, 0                 # argv = NULL
    call  kernel_main

    # 7. Hang or spin if main returns
.Lhalt_loop:
    j     .Lhalt_loop

.size _start, .-_start
```

---

## 3. Linker Memory Layout Symbols

The boot sequence requires the Linker Script to export the following standard boundary symbols:

- `_stack_top`: Highest address of the stack allocation.
- `__global_pointer$`: Small data anchor for `%gp_rel` offsets.
- `__bss_start` & `__bss_end`: Byte bounds of uninitialized `.bss`.
- `__data_start` & `__data_end`: Byte bounds of initialized `.data`.
- `__text_start` & `__text_end`: Byte bounds of executable code.
