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
│ 2. System Control Registers  │ satp, sstatus, stvec, sepc, scause, stval... │
├──────────────────────────────┼──────────────────────────────────────────────┤
│ 3. Traps & Syscalls          │ ECALL (Syscall entry), SRET/MRET (Return)    │
├──────────────────────────────┼──────────────────────────────────────────────┤
│ 4. Preemptive Scheduling     │ 64-bit Real-Time MTIME / MTIMECMP Subsystem  │
├──────────────────────────────┼──────────────────────────────────────────────┤
│ 5. Memory Management         │ Sv32 Page Table Walker, TLB & SFENCE.VMA     │
├──────────────────────────────┼──────────────────────────────────────────────┤
│ 6. I-Cache Synchronization   │ FENCE.I (Instruction-Fetch Cache Sync)       │
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
| `0x100` | `sstatus` | S-Mode | Supervisor Status Register (`UIE, SIE, UPIE, SPIE, SPP, SUM, MXR`). |
| `0x105` | `stvec` | S-Mode | Supervisor Trap Vector Base Address. |
| `0x141` | `sepc` | S-Mode | Supervisor Exception Program Counter. |
| `0x142` | `scause` | S-Mode | Supervisor Trap Cause Code. |
| `0x143` | `stval` | S-Mode | Supervisor Trap Value (faulting virtual address / instruction). |
| `0x104` | `sie` | S-Mode | Supervisor Interrupt Enable Register (`USIE, SSIE, UTIE, STIE, UEIE, SEIE`). |
| `0x144` | `sip` | S-Mode | Supervisor Interrupt Pending Register. |
| `0x300` | `mstatus` | M-Mode | Machine Status Register (`MIE, MPIE, MPP[1:0], MPRV, TW, TSR`). |
| `0x304` | `mie` | M-Mode | Machine Interrupt Enable Register (`MSIE, MTIE, MEIE`). |
| `0x344` | `mip` | M-Mode | Machine Interrupt Pending Register (`MSIP, MTIP, MEIP`). |
| `0x305` | `mtvec` | M-Mode | Machine Trap Vector Base Address. |
| `0x341` | `mepc` | M-Mode | Machine Exception Program Counter. |
| `0x342` | `mcause` | M-Mode | Machine Trap Cause Code. |
| `0x343` | `mtval` | M-Mode | Machine Trap Value. |
| `0x302` | `medeleg` | M-Mode | Machine Exception Delegation Register. |
| `0x303` | `mideleg` | M-Mode | Machine Interrupt Delegation Register. |

---

## 4. System Call, Trap Routing & Synchronization Instructions

### 4.1 `ecall` Routing & Delegation Semantics
- Executing `ecall` produces an exception corresponding to the current execution privilege:
  - From U-mode: Exception Cause `8` (`Environment Call from U-mode`)
  - From S-mode: Exception Cause `9` (`Environment Call from S-mode`)
  - From M-mode: Exception Cause `11` (`Environment Call from M-mode`)
- **Trap Destination & Delegation**:
  - By default, all traps vector to `mtvec` (M-mode) and write `mepc`, `mcause`, and `mtval`.
  - When `medeleg[8] == 1`, U-mode system calls are delegated to S-mode: the core vectors to `stvec`, saves return PC in `sepc`, writes `scause = 8`, sets `sstatus.SPP = 0`, and clears `sstatus.SIE`.
  - S-mode system calls are not delegated to S-mode by default (`medeleg[9] == 0`) and vector to M-mode firmware.

### 4.2 Trap Return & Synchronization Instructions
- **`sret` (`0x10200073`)**: Returns from S-mode trap. Restores PC from `sepc`, privilege from `sstatus.SPP`, and interrupt enable from `sstatus.SPIE`.
- **`mret` (`0x30200073`)**: Returns from M-mode trap. Restores PC from `mepc`, privilege from `mstatus.MPP`, and interrupt enable from `mstatus.MPIE`.
- **`sfence.vma` (`0x00000073` base with funct7=0x09, rd=x0)**: Invalidates TLB entries for virtual address `rs1` and ASID `rs2`.
- **`fence.i` (`0x0000100F`)**: Synchronizes instruction fetch with data stores (flushes L1 instruction cache lines after loading code).

---

## 5. Preemptive Real-Time Timer Subsystem

Because AN32 is an RV32 architecture with a 32-bit MMIO datapath, the 64-bit real-time counter and comparator are mapped into four 32-bit registers:

| Address | Register Name | Access | Description |
| :---: | :--- | :---: | :--- |
| `0x80003000` | `MTIME_LO` | RO | Lower 32 bits of 64-bit real-time clock counter |
| `0x80003004` | `MTIME_HI` | RO | Upper 32 bits of 64-bit real-time clock counter |
| `0x80003008` | `MTIMECMP_LO` | RW | Lower 32 bits of 64-bit timer comparator |
| `0x8000300C` | `MTIMECMP_HI` | RW | Upper 32 bits of 64-bit timer comparator |

### 5.1 Atomic Read/Write Protocols
- **Reading 64-bit `mtime`**:
  ```c
  uint64_t get_mtime(void) {
      uint32_t hi, lo, hi2;
      do {
          hi  = *(volatile uint32_t*)0x80003004;
          lo  = *(volatile uint32_t*)0x80003000;
          hi2 = *(volatile uint32_t*)0x80003004;
      } while (hi != hi2);
      return (((uint64_t)hi) << 32) | lo;
  }
  ```
- **Writing 64-bit `mtimecmp`**:
  ```c
  void set_mtimecmp(uint64_t val) {
      *(volatile uint32_t*)0x80003008 = 0xFFFFFFFF; // Prevent spurious match
      *(volatile uint32_t*)0x8000300C = (uint32_t)(val >> 32);
      *(volatile uint32_t*)0x80003008 = (uint32_t)(val & 0xFFFFFFFF);
  }
  ```

### 5.2 Timer Interrupt Semantics
- When `mtime >= mtimecmp`, the hardware asserts the timer interrupt bit `mip.MTIP = 1`.
- In standard RISC-V operation, this vectors to M-mode timer handler; M-mode firmware delegates to S-mode or asserts `sip.STIP` to signal the OS scheduler quantum.
- *AN32 Platform Extension*: The platform allows configuring direct `mtimecmp` comparator assertion into `sip.STIP` (Sstc-style) for low-overhead scratch OS scheduling.

---

## 6. Custom Capability Exception Codes in `scause` / `mcause`

In **AN32-System-v1**, capability exceptions map to the RISC-V designated custom synchronous exception range **24–31**:

| Exception Code | Exception Name | Description |
| :---: | :--- | :--- |
| `24` | `EXC_CAP_TAG_INVALID` | Capability tag bit is zero (unsealed / NULL / invalid pointer) |
| `25` | `EXC_CAP_BOUNDS` | Target address outside `[base, base + length)` |
| `26` | `EXC_CAP_READ_PERM` | Read attempt without Read permission (`R=0`) |
| `27` | `EXC_CAP_WRITE_PERM` | Write attempt without Write permission (`W=0`) |
| `28` | `EXC_CAP_EXEC_PERM` | Instruction fetch attempt without Execute permission (`X=0`) |
| `29` | `EXC_CAP_MONOTONICITY`| Illegal bounds expansion or permission elevation attempt |
