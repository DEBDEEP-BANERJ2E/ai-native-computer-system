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
| `c3` | `ca0` | Writable | **Caller** | Capability Argument 0 / Primary Return Capability / Active Process Data Authority. |
| `c4` | `ca1` | Writable | **Caller** | Capability Argument 1 / Secondary Return Capability / Active Process Heap Authority. |
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
   - Functions that overwrite `cs0` (`c6`) or `cs1` (`c7`) must spill them to the stack using `CSW` instructions and restore them before returning.

---

## 4. Process Context Switching & PCB Layout

In an operating system context switch, the OS kernel saves the 5 writable process capability registers (`c3`–`c7`).

### Memory Representation per Capability (16 Bytes)
Each 100-bit capability is serialized into a 16-byte aligned slot in the Process Control Block (PCB):
```
Offset +0x00:  uint32_t  offset;
Offset +0x04:  uint32_t  base;
Offset +0x08:  uint32_t  length;
Offset +0x0C:  uint8_t   perms;
Offset +0x0D:  uint8_t   tag;
Offset +0x0E:  uint16_t  reserved_padding;
```

$$\text{Total Capability Context per PCB} = 5 \times 16\text{ bytes} = 80\text{ bytes}$$

---

## 5. Interaction with Traps & Exceptions

1. On a precise capability security violation (`takePreciseTrap`), the hardware combinationally suppresses the faulting instruction's writeback and saves fault metadata into `TRAP_*` registers.
2. The OS trap handler reads `TRAP_EPC` and `TRAP_CAUSE` without mutating `c3`–`c7` until the process context is securely checkpointed.
3. Returning from a trap via `TRAP_RETURN` resumes execution at `TRAP_EPC` with the restored capability register file.
