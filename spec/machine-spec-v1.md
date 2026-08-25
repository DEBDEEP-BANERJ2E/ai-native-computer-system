# AI-Native Machine Specification v1.0: AN32 Architecture

**Author**: Advanced Agentic Coding & Systems Engineering  
**Status**: APPROVED ARCHITECTURAL CONTRACT  
**Version**: 1.0.0  

---

## 1. Executive Summary & Machine Profiles

The **AN32 Architecture** defines the hardware-software boundary for the AI-Native Computer System. To preserve the bit-exact correctness of the frozen hardware while designing a scratch operating system, this specification establishes two distinct, versioned machine profiles:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                             AN32 MACHINE PROFILES                           │
├──────────────────────────────────────┬──────────────────────────────────────┤
│             AN32-Bare-v1             │            AN32-System-v1            │
│          (Frozen Baseline)           │       (Forward OS/Memory Target)     │
├──────────────────────────────────────┼──────────────────────────────────────┤
│ • Objective-2 PipelinedCore          │ • Extended Privileged RV32 Core      │
│ • Separate 4KB IROM + 4KB DataMemory │ • Unified System Memory Hierarchy    │
│ • Bare Physical Addressing           │ • Sv32 Virtual Memory (34-bit PA)    │
│ • 100-bit CapabilityLite Security    │ • Integrated Capability + MMU Model  │
│ • 60 Exact Machine Instructions      │ • Extended Privileged Instructions   │
│ • Synchronous Precise Traps          │ • ECALL, MRET/SRET, CSRs, Interrupts │
│ • Static Elaboration Program Bake    │ • Dynamic Runtime ELF32 Loader       │
└──────────────────────────────────────┴──────────────────────────────────────┘
```

---

## 2. Software Construction Path vs Runtime Hardware Datapath

A critical architectural distinction is maintained between the **offline compilation chain** and the **inline runtime hardware execution path**:

### A. Software Construction Pipeline (CALL Toolchain)
```
  Source (C / SysLang)
          │
          ▼
    1. COMPILER ─────► AST ─────► Semantic Analysis ─────► IR ─────► Codegen
          │
          ▼
   RV32IM+Cap ASM
          │
          ▼
    2. ASSEMBLER ────► Mnemonic Parsing ─────► Bit Packing ─────► Relocations
          │
          ▼
     ELF32 ET_REL
          │
          ▼
     3. LINKER ──────► Section Layout ─────► Symbol Resolution ─────► Reloc Apply
          │
          ▼
     ELF32 ET_EXEC
          │
          ▼
     4. LOADER ──────► Segment Parsing ─────► VA Mapping ─────► Stack/Arg Setup
          │
          ▼
      Entry PC
```

### B. Inline Runtime Hardware Datapath
```
                       CPU Core (Fetch / Decode)
                                 │
                         32-bit Virtual EA
                                 │
                   CapabilityLite Authorization Check
                   (Applies to custom CL*/CS* operations)
                                 │
                                 ▼
                             MMU / TLB
                     ┌───────────┴───────────┐
                  TLB Hit                 TLB Miss
                     │                       │
                     │                Page Table Walk
                     │                       │
                     └───────────┬───────────┘
                                 ▼
                         34-bit Physical PA
                                 │
                         ┌───────┴───────┐
                         ▼               ▼
                      L1 I/D$          L1 D$
                         │               │
                         └───────┬───────┘
                                 ▼
                             Unified L2
                                 │
                         Memory Controller
                         ┌───────┴───────┐
                         ▼               ▼
                        RAM             MMIO
                         │
                 Near / Far Tiers
                         │
                 Secondary Storage
```

> [!IMPORTANT]
> The Operating System does **not** sit inline between every CPU memory access and physical RAM. Instead, the hardware MMU and CapabilityChecker execute translation and checks combinationally. The OS acts as a control and fault-handling layer, responding asynchronously to page faults, interrupts, and security traps.

---

## 3. Instruction Set Architecture (ISA) Definition

The **AN32-Bare-v1** ISA consists of exactly **60 implemented instructions** matching the frozen Chisel decoder:

| Category | Count | Instructions |
| :--- | :---: | :--- |
| **RV32I Arithmetic & Logic** | 10 | `ADD`, `SUB`, `SLL`, `SLT`, `SLTU`, `XOR`, `SRL`, `SRA`, `OR`, `AND` |
| **RV32I Immediate Arithmetic** | 9 | `ADDI`, `SLTI`, `SLTIU`, `XORI`, `ORI`, `ANDI`, `SLLI`, `SRLI`, `SRAI` |
| **RV32I Memory Loads** | 5 | `LB`, `LH`, `LW`, `LBU`, `LHU` |
| **RV32I Memory Stores** | 3 | `SB`, `SH`, `SW` |
| **RV32I Control Flow** | 8 | `BEQ`, `BNE`, `BLT`, `BGE`, `BLTU`, `BGEU`, `JAL`, `JALR` |
| **RV32I Upper Immediates** | 2 | `LUI`, `AUIPC` |
| **RV32M Multiplier & Divider** | 8 | `MUL`, `MULH`, `MULHSU`, `MULHU`, `DIV`, `DIVU`, `REM`, `REMU` |
| **CapabilityLite Manipulation (0x0B)** | 9 | `CSETBOUNDS`, `CANDPERM`, `CINCOFFSET`, `CGETBASE`, `CGETLEN`, `CGETTAG`, `CGETPERM`, `CGETOFFSET`, `CCLEAR` |
| **CapabilityLite Memory (0x2B)** | 6 | `CLB`, `CLH`, `CLW`, `CSB`, `CSH`, `CSW` |
| **Total Implemented Instructions** | **60** | Exact match with frozen `Decoder.scala` and `Opcodes.scala`. |

> [!NOTE]
> `CLBU` and `CLHU` are not present in the frozen hardware decoder. `OP_SYSTEM` (`0x73`, ECALL/CSR) currently triggers `illegalInstruction := true.B` in Bare-v1.

---

## 4. CapabilityLite Hardware Security Specification

The CapabilityLite architecture implements 100 bits of hardware metadata per register across 8 registers (`c0`–`c7`):

$$\text{CapabilityLite} = \text{Tag}(1) + \text{Base}(32) + \text{Length}(32) + \text{Perms}(3) + \text{Offset}(32) = 100\text{ bits}$$

### 4.1 Register Layout & Root Immutability
- **`c0` (NULL)**: `tag = 0`, `base = 0x0`, `length = 0x0`, `perms = 0`. Hardwired immutable.
- **`c1` (RAM Root)**: `tag = 1`, `base = 0x00000000`, `length = 0x00001000`, `perms = 3 (RW)`. Hardwired immutable.
- **`c2` (MMIO Root)**: `tag = 1`, `base = 0x80000000`, `length = 0x00010000`, `perms = 3 (RW)`. Hardwired immutable.
- **`c3`–`c7` (Process Capabilities)**: General-purpose process registers managed under the CapabilityLite ABI.

### 4.2 Protection Scope
In **AN32-Bare-v1**, capability bounds and permission checks apply strictly to the custom `CL*` (load) and `CS*` (store) instructions, and capability derivation rules apply to `CSETBOUNDS`/`CANDPERM`. Ordinary `LW`/`SW`/`LB`/`SB` instructions execute as canonical un-bounded RISC-V memory accesses.

---

## 5. System MMIO & Precise Exception Model

The MMIO aperture occupies `0x80000000`–`0x8000FFFF` (64 KiB window, checks `address[31:16] == 0x8000`):
- **Objective 1 Telemetry (`0x80001000`–`0x80001010`)**: `REV_ENERGY_ACC`, `CLA_SWITCHING`, `MUL_THERMAL`, `EDP_CURRENT`, `EDP_CONFIG`.
- **Objective 2 Performance Counters (`0x80002000`–`0x80002024`)**: `BRANCH_CONFIDENCE`, `PROCESS_BEHAVIOR_CLASS`, `SCHED_HINT`, `RETIRED_COUNT`, `BRANCH_TAKEN_COUNT`, `LOAD_USE_STALL_COUNT`, `DIV_BUSY_CYCLES`, `PIPELINE_STALL_COUNT`, `LAST_COMMIT_PC`, `CURRENT_CONTEXT`.
- **Security Event Sticky Logger (`0x80002100`–`0x80002110`)**: `SEC_STATUS` (0x80002100, W1C), `SEC_PC`, `SEC_ADDR`, `SEC_INFO`, `SEC_CONTEXT`.
- **Precise Trap Subsystem (`0x80002114`–`0x80002130`)**: `TRAP_CONTROL`, `TRAP_STATUS`, `TRAP_VECTOR` (0x8000211C, holds handler PC, resets to 0x00000800), `TRAP_EPC`, `TRAP_CAUSE`, `TRAP_ADDR`, `TRAP_CONTEXT`, `TRAP_RETURN`.

### 5.1 Trap Cause Encodings
- **AN32-Bare-v1**: `TRAP_CAUSE` is constructed by hardware as:
  $$\text{TRAP\_CAUSE} = (\text{accessType} \ll 4) \mid \text{reason}$$
  where `accessType`: 0=READ, 1=WRITE, 2=EXECUTE; `reason`: 1=INVALID_CAP, 2=BOUNDS, 3=READ_PERM, 4=WRITE_PERM, 5=EXEC_PERM, 6=MONOTONICITY.
- **AN32-System-v1**: Capability exceptions map to the RISC-V designated custom synchronous exception range **24–31** (`24`=Tag, `25`=Bounds, `26`=ReadPerm, `27`=WritePerm, `28`=ExecPerm, `29`=Monotonicity), keeping standard page faults on codes `12`, `13`, `15`.
