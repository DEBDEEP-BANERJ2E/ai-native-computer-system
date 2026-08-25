# AI-Native Sv32 Virtual Memory Specification v1.0

**Author**: Advanced Agentic Coding & Systems Engineering  
**Status**: APPROVED ARCHITECTURAL CONTRACT (AN32-System-v1 Target)  
**Standard Reference**: RISC-V Privileged Architecture (Sv32 Scheme)  

---

## 1. Virtual & Physical Address Formats

The AN32-System-v1 architecture implements the **Sv32** page-based virtual memory system:

### 1.1 32-Bit Virtual Address (`VA[31:0]`)
```
 31              22 21              12 11                     0
┌──────────────────┬──────────────────┬────────────────────────┐
│      VPN[1]      │      VPN[0]      │      Page Offset       │
│     10 bits      │     10 bits      │        12 bits         │
└──────────────────┴──────────────────┴────────────────────────┘
```
- **Page Size**: 4,096 bytes ($2^{12}$ bytes).
- **Megapage Size** (Superpage): 4,194,304 bytes (4 MiB, $2^{22}$ bytes, when Level-1 PTE is a leaf).

### 1.2 34-Bit Physical Address (`PA[33:0]`)
```
 33                     22 21              12 11                     0
┌─────────────────────────┬──────────────────┬────────────────────────┐
│         PPN[1]          │      PPN[0]      │      Page Offset       │
│         12 bits         │     10 bits      │        12 bits         │
└─────────────────────────┴──────────────────┴────────────────────────┘
```
- **Physical Memory Capacity**: 16 GiB ($2^{34}$ bytes).
- Physical addresses above 4 GiB are accessed via MMU translation, while legacy bare-metal RAM/MMIO sits in the lower 4 GiB space.

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
| `2` | **W** (Write)| `1` = Page is writable. Must not be set without `R=1`. |
| `3` | **X** (Exec) | `1` = Page contains executable instructions. |
| `4` | **U** (User) | `1` = User mode accessible; `0` = Supervisor mode only. |
| `5` | **G** (Global)| `1` = Global mapping (not flushed across ASID switches). |
| `6` | **A** (Access)| `1` = Page has been accessed since bit was last cleared. |
| `7` | **D** (Dirty) | `1` = Page has been written to since bit was last cleared. |
| `9:8` | **RSW** | Reserved for operating system software use. |
| `31:10`| **PPN** | Physical Page Number (22 bits). |

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

## 4. Hardware TLB & Address Space IDs (ASID)

1. The hardware translation lookaside buffer (TLB) caches 32 recently translated page entries.
2. Each TLB entry tags translations with a 9-bit **Address Space Identifier (ASID)** to prevent stale translations across process context switches without requiring full TLB flushes.
3. The `sfence.vma` instruction invalidates matching TLB entries when the OS modifies page table entries.
