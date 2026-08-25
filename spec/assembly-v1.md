# AI-Native Assembly Language Specification v1.0

**Author**: Advanced Agentic Coding & Systems Engineering  
**Status**: APPROVED ARCHITECTURAL CONTRACT  

---

## 1. Syntax & Lexical Structure

The AN32 Assembler parses assembly text files (`.s` or `.S`) with standard formatting:

```assembly
# Single-line comment begins with '#' or '//'
.section .text
.globl kernel_main
.type kernel_main, @function

kernel_main:
    addi  sp, sp, -16       # Allocate 16-byte stack frame
    sw    ra, 12(sp)        # Save return address
    li    a0, 42            # Pseudo-instruction: Load immediate 42
    call  process_init      # Pseudo-instruction: PC-relative call
    lw    ra, 12(sp)        # Restore return address
    addi  sp, sp, 16        # Deallocate stack frame
    ret                     # Pseudo-instruction: Return to caller
```

---

## 2. Register Aliases

The assembler recognizes canonical numeric names and standard ABI aliases for both integer and capability registers:

### 2.1 Integer Registers (`x0`–`x31`)
`zero` (`x0`), `ra` (`x1`), `sp` (`x2`), `gp` (`x3`), `tp` (`x4`), `t0` (`x5`), `t1` (`x6`), `t2` (`x7`), `s0` / `fp` (`x8`), `s1` (`x9`), `a0` (`x10`), `a1` (`x11`), `a2` (`x12`), `a3` (`x13`), `a4` (`x14`), `a5` (`x15`), `a6` (`x16`), `a7` (`x17`), `s2` (`x18`), `s3` (`x19`), `s4` (`x20`), `s5` (`x21`), `s6` (`x22`), `s7` (`x23`), `s8` (`x24`), `s9` (`x25`), `s10` (`x26`), `s11` (`x27`), `t3` (`x28`), `t4` (`x29`), `t5` (`x30`), `t6` (`x31`).

### 2.2 Capability Registers (`c0`–`c7`)
`cnull` (`c0`), `cram` (`c1`), `cmmio` (`c2`), `ca0` (`c3`), `ca1` (`c4`), `ct0` (`c5`), `cs0` (`c6`), `cs1` (`c7`).

---

## 3. Assembler Directives

| Directive | Description | Example |
| :--- | :--- | :--- |
| `.text` | Emits code into the `.text` section | `.text` |
| `.rodata` | Emits read-only constants into `.rodata` | `.rodata` |
| `.data` | Emits initialized global data into `.data` | `.data` |
| `.bss` | Reserves uninitialized zeroed space in `.bss` | `.bss` |
| `.section name, "flags"` | Emits into a named section | `.section .init, "ax"` |
| `.globl symbol` | Exports a symbol with global binding | `.globl _start` |
| `.local symbol` | Declares a symbol with local file binding | `.local helper` |
| `.type symbol, @type` | Declares symbol type (`@function` or `@object`) | `.type main, @function` |
| `.size symbol, size` | Declares symbol size in bytes | `.size main, .-main` |
| `.align n` / `.balign n`| Aligns current location to $2^n$ or $n$ bytes | `.balign 4` |
| `.byte b1, b2, ...` | Emits 8-bit integer bytes | `.byte 0x10, 0x20` |
| `.half h1, h2, ...` | Emits 16-bit halfwords (little-endian) | `.half 0x1234` |
| `.word w1, w2, ...` | Emits 32-bit words (little-endian) | `.word 0x80001000` |
| `.string "str"` / `.asciz`| Emits null-terminated ASCII string | `.string "AN32 Kernel"` |
| `.zero n` / `.space n` | Reserves $n$ zero-initialized bytes | `.zero 256` |

---

## 4. Pseudo-Instruction Expansions

The assembler automatically expands pseudo-instructions into exact canonical hardware instructions:

| Pseudo-Instruction | Expanded Hardware Instruction(s) | Description |
| :--- | :--- | :--- |
| `nop` | `addi x0, x0, 0` | No-operation |
| `li rd, imm` ($\pm 2047$) | `addi rd, x0, imm` | Load small immediate |
| `li rd, imm32` (large) | `lui rd, %hi(imm32)`<br>`addi rd, rd, %lo(imm32)` | Load full 32-bit immediate |
| `la rd, symbol` | `auipc rd, %pcrel_hi(symbol)`<br>`addi rd, rd, %pcrel_lo(symbol)` | Load symbol address (PC-relative) |
| `mv rd, rs` | `addi rd, rs, 0` | Register copy / move |
| `not rd, rs` | `xori rd, rs, -1` | Bitwise inversion |
| `neg rd, rs` | `sub rd, x0, rs` | Two's complement negation |
| `j label` | `jal x0, label` | Unconditional jump |
| `jr rs` | `jalr x0, 0(rs)` | Jump to address in register |
| `ret` | `jalr x0, 0(ra)` | Return from subroutine |
| `call symbol` | `auipc x1, %pcrel_hi(symbol)`<br>`jalr x1, %pcrel_lo(symbol)(x1)` | Function call |
| `tail symbol` | `auipc x6, %pcrel_hi(symbol)`<br>`jalr x0, %pcrel_lo(symbol)(x6)` | Tail call |
| `beqz rs, label` | `beq rs, x0, label` | Branch if equal to zero |
| `bnez rs, label` | `bne rs, x0, label` | Branch if not equal to zero |
| `blez rs, label` | `bge x0, rs, label` | Branch if less than or equal to zero |
| `bgez rs, label` | `bge rs, x0, label` | Branch if greater than or equal to zero |
| `bltz rs, label` | `blt rs, x0, label` | Branch if less than zero |
| `bgtz rs, label` | `blt x0, rs, label` | Branch if greater than zero |
| `bgt rs, rt, label` | `blt rt, rs, label` | Branch if greater than |
| `ble rs, rt, label` | `bge rt, rs, label` | Branch if less than or equal |
| `bgtu rs, rt, label`| `bltu rt, rs, label` | Branch if greater than unsigned |
| `bleu rs, rt, label`| `bgeu rt, rs, label` | Branch if less than or equal unsigned |
