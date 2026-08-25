# AI-Native Relocation Architecture & Formulas v1.0

**Author**: Advanced Agentic Coding & Systems Engineering  
**Status**: APPROVED ARCHITECTURAL CONTRACT  
**Standard Reference**: RISC-V psABI Specification  

---

## 1. Relocation Notation & Definitions

When the static Linker combines multiple relocatable object files (`.o`), it applies relocation formulas to patch unresolved symbol references.

- **$S$**: The resolved virtual address of the symbol referenced by the relocation.
- **$A$**: The explicit 32-bit signed addend from the relocation entry (`r_addend`).
- **$P$**: The link-time virtual address of the location being patched (`r_offset` within the section).

---

## 2. Standard Relocation Types & Mathematical Formulas

| Relocation Name | ID (Dec) | ID (Hex) | Formula / Calculation | Field Patched / Mask |
| :--- | :---: | :---: | :--- | :--- |
| `R_RISCV_NONE` | 0 | `0x00` | None | None |
| `R_RISCV_32` | 1 | `0x01` | $S + A$ | 32-bit absolute word in `.data` / `.rodata` |
| `R_RISCV_BRANCH` | 16 | `0x10` | $S + A - P$ | 12-bit B-Type immediate (`inst[31, 7, 30:25, 11:8]`) |
| `R_RISCV_JAL` | 17 | `0x11` | $S + A - P$ | 20-bit J-Type immediate (`inst[31, 19:12, 20, 30:21]`) |
| `R_RISCV_CALL` | 18 | `0x12` | $S + A - P$ | Pair: `AUIPC` (HI20) + `JALR` (LO12) |
| `R_RISCV_PCREL_HI20` | 23 | `0x17` | $(S + A - P + \text{0x800}) \gg 12$ | 20-bit U-Type immediate for `AUIPC` |
| `R_RISCV_PCREL_LO12_I` | 24 | `0x18` | $(S + A - P) \mathbin{\&} \text{0xFFF}$ | 12-bit I-Type immediate for `ADDI` / `LW` |
| `R_RISCV_PCREL_LO12_S` | 25 | `0x19` | $(S + A - P) \mathbin{\&} \text{0xFFF}$ | 12-bit S-Type immediate for `SW` |
| `R_RISCV_HI20` | 26 | `0x1A` | $(S + A + \text{0x800}) \gg 12$ | 20-bit U-Type immediate for `LUI` |
| `R_RISCV_LO12_I` | 27 | `0x1B` | $(S + A) \mathbin{\&} \text{0xFFF}$ | 12-bit I-Type immediate for `ADDI` / `LW` |
| `R_RISCV_LO12_S` | 28 | `0x1C` | $(S + A) \mathbin{\&} \text{0xFFF}$ | 12-bit S-Type immediate for `SW` |

---

## 3. High/Low Address Decomposition Arithmetic

Because RISC-V immediate fields in `ADDI` and `LW`/`SW` are sign-extended from 12 bits ($\text{range } -2048 \text{ to } +2047$), loading a 32-bit constant via `LUI` + `ADDI` requires adding `0x800` to the upper 20 bits if the lower 12 bits have their sign bit set ($\text{bit } 11 = 1$):

```c
uint32_t val = (uint32_t)(S + A);
int32_t  lo12 = ((int32_t)(val << 20)) >> 20; // Sign-extend 12 bits
uint32_t hi20 = (val - lo12) >> 12;

// Patch LUI / AUIPC instruction
instruction_hi = (instruction_hi & 0x00000FFF) | (hi20 << 12);

// Patch ADDI / LW instruction
instruction_lo = (instruction_lo & 0x000FFFFF) | (((uint32_t)lo12 & 0xFFF) << 20);
```

---

## 4. Range & Alignment Verification Rules

1. **Branch Range Check (`R_RISCV_BRANCH`)**:
   $$(S + A - P) \ge -4096 \quad \text{and} \quad (S + A - P) \le +4094$$
   $$(S + A - P) \pmod 2 = 0$$
   *An out-of-range branch generates a Linker relocation overflow error.*

2. **Jump Range Check (`R_RISCV_JAL`)**:
   $$(S + A - P) \ge -1048576 \quad \text{and} \quad (S + A - P) \le +1048574$$
   $$(S + A - P) \pmod 2 = 0$$
