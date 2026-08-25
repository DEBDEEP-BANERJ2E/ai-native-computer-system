# AI-Native CapabilityLite ABI Specification v1.0

**Author**: Advanced Agentic Coding & Systems Engineering  
**Status**: APPROVED ARCHITECTURAL CONTRACT  
**Hardware Reference**: Objective 2 CapabilityRegFile & CapabilityChecker (100-bit)  

---

## 1. CapabilityLite Architecture Overview

The CapabilityLite subsystem introduces **8 bounded capability registers** (`c0`–`c7`). Each register holds a 100-bit metadata bundle:

$$\text{CapabilityLite} = \text{Tag}(1) + \text{Base}(32) + \text{Length}(32) + \text{Perms}(3) + \text{Offset}(32) = 100\text{ bits}$$

```
 99 98                          67 66                          35 34    32 31                           0
┌──┬──────────────────────────────┬──────────────────────────────┬────────┬──────────────────────────────┐
│T │         Base [31:0]          │        Length [31:0]         │Perms[2]│         Offset [31:0]        │
└──┴──────────────────────────────┴──────────────────────────────┴────────┴──────────────────────────────┘
 1b             32 bits                        32 bits              3 bits               32 bits
```

- **Tag (`T`)**: `1` = Valid hardware capability; `0` = Invalid / Unsealed / NULL.
- **Permissions (`Perms`)**: Bit 0: `Read` (`0x1`), Bit 1: `Write` (`0x2`), Bit 2: `Exec` (`0x4`, reserved).

---

## 2. Register Roles & Preservation Rules

Because there are exactly five writable capability registers (`c3`–`c7`), strict ABI conventions govern their allocation and preservation:

| Register | ABI Name | Hardware Type | Saver | Role / Calling Convention |
| :--- | :--- | :--- | :--- | :--- |
| `c0` | `cnull` | **Immutable** | None | Hardwired NULL capability (`tag = 0`). Writes discarded. |
| `c1` | `cram` | **Immutable** | None | Root Data Memory Authority (`0x00000000..0x00001000`, RW). Writes discarded. |
| `c2` | `cmmio` | **Immutable** | None | Root MMIO Interconnect Authority (`0x80000000..0x80010000`, RW). Writes discarded. |
| `c3` | `ca0` | Writable | **Caller** | Capability Argument 0 / Primary Return Capability / Active Process Data Region. |
| `c4` | `ca1` | Writable | **Caller** | Capability Argument 1 / Secondary Return Capability / Active Process Heap Region. |
| `c5` | `ct0` | Writable | **Caller** | Capability Temporary 0 / Stack Scratch Buffer Authority. |
| `c6` | `cs0` | Writable | **Callee** | Capability Callee-Saved Register 0. Preserved across function calls. |
| `c7` | `cs1` | Writable | **Callee** | Capability Callee-Saved Register 1. Preserved across function calls. |

---

## 3. Calling Conventions for Capability Operations

1. **Passing Capabilities to Functions**:
   - Up to two capability parameters are passed in `ca0` (`c3`) and `ca1` (`c4`).
   - Any additional capability parameters must be stored into a memory structure and passed via an enclosing container capability in `ca0`.
2. **Returning Capabilities from Functions**:
   - Memory allocation functions (e.g. `malloc`, `derive_buffer`) return the newly bounded capability in `ca0` (`c3`).
3. **Preserving Callee-Saved Capabilities**:
   - Functions that overwrite `cs0` (`c6`) or `cs1` (`c7`) must spill them to the stack and restore them before returning.

---

## 4. Secure Process Context Switching & PCB Serialization

> [!CRITICAL]
> **Capability Unforgeability Invariant**: CapabilityLite operates on untagged memory. Ordinary RAM words cannot hold authoritative hardware capability tags. Storing a byte `tag=1` in memory never manufactures a valid capability.

### 4.1 Memory Representation per Capability (16 Bytes in PCB)
```c
typedef struct {
    uint32_t offset;        /* Byte offset from base */
    uint32_t base;          /* Spatial base address */
    uint32_t length;        /* Bounded length in bytes */
    uint8_t  perms;         /* Read (1), Write (2), Exec (4) */
    uint8_t  tag;           /* Descriptive metadata only */
    uint8_t  root_selector; /* 0 = NULL (c0), 1 = RAM_ROOT (c1), 2 = MMIO_ROOT (c2) */
    uint8_t  reserved;      /* Padding */
} CapSlot_t;
```

$$\text{Total Capability Context per PCB} = 5 \times 16\text{ bytes} = 80\text{ bytes}$$

### 4.2 Hardware Re-Derivation Restoration Protocol

Because frozen `CSETBOUNDS` sets `new.base = old.base + old.offset` (and resets `new.offset = 0`), restoring a capability with an arbitrary base $B$ from root base $R$ requires adjusting the offset before bounding:

1. **Widened Containment Pre-Verification**:
   $$\text{pcb.base} \ge \text{root.base}$$
   $$(\text{uint64\_t})\text{pcb.base} + \text{pcb.length} \le (\text{uint64\_t})\text{root.base} + \text{root.length}$$
   $$\text{pcb.offset} \le \text{pcb.length}$$
   $$(\text{pcb.perms} \mathbin{\&} \sim\text{root.perms}) == 0$$

2. **Valid Capability Hardware Derivation Sequence (`pcb.tag == 1`)**:
   ```assembly
   # Let a1 = pcb.base, a2 = pcb.length, a3 = pcb.perms, a4 = pcb.offset
   # Let c1 be the selected RAM root (base R, offset 0)
   sub        t0, a1, zero          # delta_base = pcb.base - root.base (assuming root.base=0)
   cincoffset c3, c1, t0            # c3.base = R, c3.offset = delta_base
   csetbounds c3, c3, a2            # c3.base = R + delta_base = pcb.base, c3.length = pcb.length, c3.offset = 0
   candperm   c3, c3, a3            # c3.perms = c3.perms & pcb.perms
   cincoffset c3, c3, a4            # c3.offset = pcb.offset
   ```
   Hardware automatically verifies monotonic containment against `c1`/`c2` and sets `tag := 1`.

3. **Invalid / NULL Capability (`pcb.tag == 0`)**:
   ```assembly
   cclear     c3                    # Sets c3 tag := 0, base := 0, length := 0, perms := 0
   ```

This guarantees that a compromised process modifying its own memory cannot forge authority beyond what is rooted in `c1` or `c2`.
