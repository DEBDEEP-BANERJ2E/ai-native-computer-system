# AI-Native Sv32 Virtual Memory Specification v1.0

**Author**: Advanced Agentic Coding & Systems Engineering  
**Status**: APPROVED ARCHITECTURAL CONTRACT (AN32-System-v1 Target)  
**Standard Reference**: RISC-V Privileged Architecture (Sv32 Scheme)  

---

## 1. Virtual & Physical Address Formats

The AN32-System-v1 architecture implements the canonical **Sv32** page-based virtual memory system:

### 1.1 32-Bit Virtual Address (`VA[31:0]`)
```
 31              22 21              12 11                     0
┌──────────────────┬──────────────────┬────────────────────────┐
│      VPN[1]      │      VPN[0]      │      Page Offset       │
│     10 bits      │     10 bits      │        12 bits         │
└──────────────────┴──────────────────┴────────────────────────┘
```
- **Page Size**: 4,096 bytes ($2^{12}$ bytes).
- **Megapage Size (Superpage)**: 4,194,304 bytes (4 MiB, $2^{22}$ bytes), when Level-1 PTE is a leaf (`R=1` or `X=1`).

### 1.2 34-Bit Physical Address (`PA[33:0]`)
```
 33                     22 21              12 11                     0
┌─────────────────────────┬──────────────────┬────────────────────────┐
│         PPN[1]          │      PPN[0]      │      Page Offset       │
│         12 bits         │     10 bits      │        12 bits         │
└─────────────────────────┴──────────────────┴────────────────────────┘
```
- **Physical Address Width**: 34 bits (supporting up to 16 GiB of physical space).
- Memory below 4 GiB houses physical RAM and the legacy SystemMMIO window (`0x80000000`–`0x8000FFFF`).

---

## 2. Page Table Entry (PTE) Bitfields (32 Bits)

```
 31                                  10 9  8 7 6 5 4 3 2 1 0
┌──────────────────────────────────────┬────┬─┬─┬─┬─┬─┬─┬─┬─┐
│              PPN[21:0]               │RSW │D│A│G│U│X│W│R│V│
└──────────────────────────────────────┴────┴─┬─┬─┬─┬─┬─┬─┬─┘
                 22 bits                2b   1 1 1 1 1 1 1 1
```

| Bit(s) | Name | Description |
| :---: | :--- | :--- |
| `0` | **V** (Valid) | `1` = PTE is valid; `0` = PTE is invalid (causes Page Fault). |
| `1` | **R** (Read) | `1` = Page is readable. |
| `2` | **W** (Write)| `1` = Page is writable. Must not be set without `R=1` (`W=1, R=0` is reserved and raises Page Fault). |
| `3` | **X** (Exec) | `1` = Page contains executable instructions. |
| `4` | **U** (User) | `1` = User mode accessible; `0` = Supervisor mode only. |
| `5` | **G** (Global)| `1` = Global mapping across all ASIDs (not flushed on context switch). |
| `6` | **A** (Access)| `1` = Page has been accessed since bit was last cleared. |
| `7` | **D** (Dirty) | `1` = Page has been written to since bit was last cleared. |
| `9:8` | **RSW** | Reserved for operating system software use. |
| `31:10`| **PPN** | Physical Page Number (22 bits: `PPN[1]` = 12 bits, `PPN[0]` = 10 bits). |

### 2.1 Leaf vs Non-Leaf PTE Encoding
- **Non-Leaf Pointer PTE** (`R=0, W=0, X=0, V=1`): Points to next-level page table. `PPN` field holds the physical frame number of the Level-0 page table.
- **Leaf Data/Code PTE** (`R=1` or `X=1`, `V=1`): Directly maps a 4-KiB frame (at Level 0) or a 4-MiB superpage (at Level 1).
- **Reserved / Illegal Encodings**: `W=1, R=0` is reserved and immediately raises a Page Fault.

---

## 3. Two-Level Page Table Translation Walk

```
                   Virtual Address (VA)
                            │
               ┌────────────┴────────────┐
               ▼                         ▼
            VPN[1]                    VPN[0]
               │                         │
               ▼                         ▼
   satp.PPN ──► Level-1 Page Table       │
                     │ (PTE1)            │
                     ▼                   ▼
                Is Leaf PTE? ──No──► Level-0 Page Table
                     │                     │ (PTE0)
                    Yes                    ▼
                     │                 Check Permissions (R/W/X/U/V)
                     │                     │
                     ▼                     ▼
                4-MiB Superpage      4-KiB Physical Frame
                     │                     │
                     └──────────┬──────────┘
                                ▼
                      + VA Page Offset (12 bits)
                                ▼
                       34-bit Physical Address (PA)
```

---

## 4. Hardware TLB, ASIDs & Page Faults

1. **TLB Miss vs Page Fault**:
   - A **TLB Miss** is not an exception: it invokes the hardware page-table walker to fetch the PTE from RAM.
   - If the walker finds `PTE.V == 0`, privilege mismatch (`PTE.U == 0` when in U-mode), permission failure (`PTE.W == 0` on store, `PTE.X == 0` on fetch), or misaligned superpage, a **Page Fault** is raised to the OS kernel.
2. **Page Fault Exception Codes (`scause` / `mcause`)**:
   - `12`: Instruction Page Fault
   - `13`: Load Page Fault
   - `15`: Store / AMO Page Fault
3. **Address Space Identifiers (ASID)**:
   - The TLB tags cached translations with a 9-bit ASID from `satp.ASID`.
   - The OS executes `sfence.vma` when updating page tables to selectively flush matching TLB entries.
