# AI-Native Privileged System Requirements v1.0 (AN32-System-v1)

**Author**: Advanced Agentic Coding & Systems Engineering  
**Status**: APPROVED ARCHITECTURAL CONTRACT  
**Target Subsystems**: Objective 3 (Operating System) & Objective 4 (Memory Hierarchy)  

---

## 1. Motivation & Scope

The frozen **Objective-2 PipelinedCore** (`AN32-Bare-v1`) provides verified RV32IM execution, 100-bit CapabilityLite security, SystemMMIO, and precise security traps. However, hosting a fully protected, multi-process, virtual-memory operating system requires additional privileged architectural capabilities.

To preserve the frozen Objective-2 RTL, all OS-level hardware additions are specified as the **AN32-System-v1 Forward Extension**.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           AN32-System-v1 EXTENSIONS                         │
├──────────────────────────────┬──────────────────────────────────────────────┤
│ 1. Privileged Modes          │ Machine (M), Supervisor (S), User (U) Modes  │
├──────────────────────────────┼──────────────────────────────────────────────┤
│ 2. System Control Registers  │ satp, sstatus, stvec, sepc, scause, stval    │
├──────────────────────────────┼──────────────────────────────────────────────┤
│ 3. Traps & Syscalls          │ ECALL (Syscall entry), SRET (Atomic return)  │
├──────────────────────────────┼──────────────────────────────────────────────┤
│ 4. Preemptive Scheduling     │ 64-bit mtime / mtimecmp Timer Interrupts     │
├──────────────────────────────┼──────────────────────────────────────────────┤
│ 5. Memory Management         │ Sv32 Page Table Walker, TLB & SFENCE.VMA     │
└──────────────────────────────┴──────────────────────────────────────────────┘
```

---

## 2. Privileged Execution Modes

1. **User Mode (U-Mode, `priv = 00`)**:
   - Executes unprivileged user processes.
   - Access to MMIO registers, CSRs, and supervisor page tables (`U=0`) is strictly forbidden and hardware-trapped.
2. **Supervisor Mode (S-Mode, `priv = 01`)**:
   - Executes the operating system kernel.
   - Manages page tables, process scheduling, physical frame allocation, and device drivers.
3. **Machine Mode (M-Mode, `priv = 11`)**:
   - Highest privilege execution mode for boot firmware and hardware exception delegation.

---

## 3. Required Control & Status Registers (CSRs)

| CSR Address | Register Name | Privilege | Description |
| :---: | :--- | :---: | :--- |
| `0x180` | `satp` | S-Mode | Supervisor Address Translation and Protection (`[MODE:1][ASID:9][PPN:22]`). |
| `0x100` | `sstatus` | S-Mode | Supervisor Status Register (Interrupt enables, previous privilege). |
| `0x105` | `stvec` | S-Mode | Supervisor Trap Vector Base Address. |
| `0x141` | `sepc` | S-Mode | Supervisor Exception Program Counter. |
| `0x142` | `scause` | S-Mode | Supervisor Trap Cause Code. |
| `0x143` | `stval` | S-Mode | Supervisor Trap Value (faulting virtual address). |
| `0x104` | `sie` | S-Mode | Supervisor Interrupt Enable Register. |
| `0x144` | `sip` | S-Mode | Supervisor Interrupt Pending Register. |

---

## 4. System Call & Trap Instructions

- **`ecall` (0x73, funct3=0, funct12=0x000)**:
  - Generates an Environment Call exception from U-mode (`scause = 8`).
  - Atomically saves current PC into `sepc`, sets `sstatus.SPP = 0`, switches privilege to S-mode, and vectors PC to `stvec`.
- **`sret` (0x73, funct3=0, funct12=0x102)**:
  - Returns from supervisor trap handler.
  - Atomically restores PC from `sepc`, sets privilege mode from `sstatus.SPP`, and restores interrupt enables.
- **`sfence.vma` (0x73, funct3=0, funct7=0x09)**:
  - Invalidates cached translations in the MMU TLB for a specified virtual address and ASID.

---

## 5. Preemptive Timer Interrupts

The system implements a 64-bit memory-mapped real-time timer:
- **`mtime` (`0x80003000`)**: Free-running 64-bit hardware clock cycle counter.
- **`mtimecmp` (`0x80003008`)**: Programmable timer comparator register.
- When `mtime >= mtimecmp`, a timer interrupt (`scause = 0x80000005`) is asserted, prompting the OS scheduler to preemptively switch tasks.
