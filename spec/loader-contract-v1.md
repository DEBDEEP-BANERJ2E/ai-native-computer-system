# AI-Native Executable Loader Contract v1.0

**Author**: Advanced Agentic Coding & Systems Engineering  
**Status**: APPROVED ARCHITECTURAL CONTRACT  

---

## 1. Loader Responsibilities & Execution Flow

The **ELF32 Loader** is the operating system component responsible for taking an on-disk executable file (`ET_EXEC`) and constructing an active, runnable process image in virtual memory:

```
                      1. Parse & Validate ELF32 Header
                                     │
                                     ▼
                      2. Iterate Over Program Headers (Phdr)
                                     │
                        ┌────────────┴────────────┐
                        ▼                         ▼
               p_type == PT_LOAD        Other Header Types
                        │               (PT_NOTE, PT_DYNAMIC)
                        │                         │
                        ▼                         ▼
             3. Map Virtual Memory Segment     (Process/Ignore)
             - Allocate frames at p_vaddr
             - Copy p_filesz bytes from disk
             - Zero-fill (p_memsz - p_filesz)
             - Set permissions (R/W/X)
                        │
                        ▼
             4. Construct Process Stack & Auxiliary Vectors
             - Push string data (args & env strings)
             - Push envp[] pointer table
             - Push argv[] pointer table
             - Push argc
             - Align sp to 16-byte boundary
                        │
                        ▼
             5. Initialize Process Registers
             - a0 = argc, a1 = argv, a2 = envp
             - sp = stack_top (16-byte aligned)
             - gp = global pointer
             - c3 = process data capability
                        │
                        ▼
             6. Transfer Execution: Jump to Entry PC (e_entry)
```

---

## 2. Segment Mapping & `.bss` Zeroing Contract

For every `Elf32_Phdr` with `p_type == PT_LOAD`:
1. The loader allocates memory in the process virtual address space spanning `[p_vaddr, p_vaddr + p_memsz)`.
2. It copies `p_filesz` bytes from file offset `p_offset` into `p_vaddr`.
3. If `p_memsz > p_filesz`, the loader guarantees that all bytes in `[p_vaddr + p_filesz, p_vaddr + p_memsz)` are **initialized to zero** before user code begins execution.
4. Segment protection is enforced according to `p_flags`:
   - `PF_R`: Read permission (`0x4`)
   - `PF_W`: Write permission (`0x2`)
   - `PF_X`: Execute permission (`0x1`)

---

## 3. Initial Process Stack Layout

```
High Address
┌─────────────────────────────────────────────────────────┐
│ Argument Strings (e.g. "program_name\0", "arg1\0")      │
├─────────────────────────────────────────────────────────┤
│ Environment Strings (e.g. "PATH=/bin\0")                │
├─────────────────────────────────────────────────────────┤
│ NULL word (end of envp table)                           │
├─────────────────────────────────────────────────────────┤
│ envp[n-1], ..., envp[0] pointers                        │
├─────────────────────────────────────────────────────────┤
│ NULL word (end of argv table)                           │
├─────────────────────────────────────────────────────────┤
│ argv[argc-1], ..., argv[0] pointers                     │
├─────────────────────────────────────────────────────────┤
│ argc (uint32_t)                                         │
└─────────────────────────────────────────────────────────┘ ◄── sp (16-byte aligned)
Low Address
```

---

## 4. Architectural Profile Differences

- **AN32-Bare-v1 (Frozen Hardware)**:
  - Instruction Memory is a synchronous Chisel `VecInit` ROM populated at Chisel elaboration time.
  - The offline toolchain converts the compiled `.elf` into a binary memory array baked into the Verilator/SystemVerilog build.
- **AN32-System-v1 (Forward System)**:
  - Memory is a unified, writable DRAM hierarchy.
  - The OS Kernel ELF loader reads executables from secondary storage, allocates physical frames, maps them into Sv32 two-level page tables, and vectors execution dynamically.
