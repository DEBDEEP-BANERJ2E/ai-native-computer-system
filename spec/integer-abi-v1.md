# AI-Native Integer ABI Specification v1.0 (ILP32)

**Author**: Advanced Agentic Coding & Systems Engineering  
**Status**: APPROVED ARCHITECTURAL CONTRACT  
**Standard Reference**: RISC-V psABI ILP32 Standard  

---

## 1. Data Types & Representation

The AN32 Architecture adheres to the **ILP32** data model:

| C Data Type | Size (Bytes) | Alignment (Bytes) | Range / Format |
| :--- | :---: | :---: | :--- |
| `bool` / `_Bool` | 1 | 1 | `0` (false), `1` (true) |
| `char` / `int8_t` | 1 | 1 | `-128` to `127` (signed by default) |
| `unsigned char` / `uint8_t` | 1 | 1 | `0` to `255` |
| `short` / `int16_t` | 2 | 2 | `-32,768` to `32,767` |
| `int` / `int32_t` | 4 | 4 | `-2,147,483,648` to `2,147,483,647` |
| `long` | 4 | 4 | 32-bit signed integer |
| `long long` / `int64_t` | 8 | 8 | 64-bit signed two's complement |
| `pointer` (`void*`) | 4 | 4 | 32-bit address word |
| `float` | 4 | 4 | IEEE 754 single-precision |
| `double` | 8 | 8 | IEEE 754 double-precision |

---

## 2. Register Roles & Preservation Rules

The 32 integer registers (`x0`–`x31`) are assigned standard ABI names and preservation obligations:

| Register | ABI Name | Role | Saver | Description |
| :--- | :--- | :--- | :--- | :--- |
| `x0` | `zero` | Zero constant | — | Hardwired zero; writes discarded |
| `x1` | `ra` | Return address | Caller | Set by `JAL` / `JALR` |
| `x2` | `sp` | Stack pointer | **Callee** | Top of stack (16-byte aligned) |
| `x3` | `gp` | Global pointer | — | Points to small-data area (`.sdata`) |
| `x4` | `tp` | Thread pointer | — | Points to thread-local storage (TLS) |
| `x5`–`x7` | `t0`–`t2` | Temporaries 0–2 | Caller | Scratch registers; destroyed across calls |
| `x8` | `s0` / `fp` | Saved 0 / Frame pointer | **Callee** | Frame base pointer or saved variable |
| `x9` | `s1` | Saved register 1 | **Callee** | Preserved across function calls |
| `x10`–`x11` | `a0`–`a1` | Arguments 0–1 / Return | Caller | First 2 args; primary & secondary return values |
| `x12`–`x17` | `a2`–`a7` | Arguments 2–7 | Caller | Remaining register arguments / syscall ID (`a7`) |
| `x18`–`x27` | `s2`–`s11` | Saved registers 2–11 | **Callee** | Preserved across function calls |
| `x28`–`x31` | `t3`–`t6` | Temporaries 3–6 | Caller | Scratch registers |

> [!CAUTION]
> The register allocator must never allocate `x3` (`gp`) or `x4` (`tp`) as ordinary temporaries. `x2` (`sp`) must remain 16-byte aligned at all times.

---

## 3. Function Calling Sequence & Stack Frame

### 3.1 Stack Alignment & Growth
1. The stack grows **downward** (from high addresses toward low addresses).
2. The stack pointer (`sp`) **must be 16-byte aligned** upon entry to any function and when executing any `CALL` or `JALR` instruction:
   $$\text{sp} \pmod{16} = 0$$

### 3.2 Standard Function Prologue & Epilogue
```assembly
# Standard Leaf Function (no stack allocation required)
leaf_function:
    add   a0, a0, a1        # a0 = a0 + a1
    ret                     # jalr x0, 0(ra)

# Standard Non-Leaf Function Frame (allocating 32 bytes)
complex_function:
    addi  sp, sp, -32       # Allocate frame (multiple of 16)
    sw    ra, 28(sp)        # Save return address
    sw    s0, 24(sp)        # Save frame pointer
    sw    s1, 20(sp)        # Save callee-saved register s1
    addi  s0, sp, 32        # Establish frame pointer

    # Function body ...
    call  other_function

    # Function Epilogue
    lw    s1, 20(sp)        # Restore s1
    lw    s0, 24(sp)        # Restore frame pointer
    lw    ra, 28(sp)        # Restore return address
    addi  sp, sp, 32        # Deallocate frame
    ret                     # Return to caller
```

---

## 4. Argument Passing & Return Conventions

1. **Scalar Arguments**:
   - The first 8 scalar arguments (up to 32 bits each) are passed in `a0`–`a7`.
   - 64-bit arguments (e.g. `long long`) are passed in an **aligned even-odd register pair** (e.g. `a0:a1` or `a2:a3`). If only one argument register remains, the 64-bit argument is passed on the stack.
   - Any arguments beyond what fits in `a0`–`a7` are pushed onto the stack in right-to-left order.
2. **Scalar Return Values**:
   - 32-bit values are returned in `a0`.
   - 64-bit values are returned in `a0` (lower 32 bits) and `a1` (upper 32 bits).
3. **Structure Passing & Return**:
   - Aggregates $\le 8$ bytes are passed in up to two argument registers (`a0`, `a1`).
   - Aggregates $> 8$ bytes are passed by invisible reference or copied to a caller-allocated buffer whose address is passed in `a0`.
