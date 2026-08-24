# Objective 2: RISC-V RV32IM Processor Core & CapabilityLite Hardware Security

Welcome to **Objective 2** of the AI-Native Computer System project.

- **Current Implementation (Phase 7: CapabilityLite Hardware Security)**:
  - Frozen SingleCycleCore reference architecture
  - Five-stage RV32IM PipelinedCore with CapabilityLite security extensions
  - Full integer EX/MEM and MEM/WB forwarding + Load-Use hazard interlock
  - Capability RAW hazard interlock (stalling ID/EX & EX/MEM producers) + same-cycle WB $\rightarrow$ ID capability bypass
  - Clean integer/capability operand separation (`usesIntRs1`, `usesIntRs2`, `usesCapRs1`) with GPR forwarding into capability operations (`rs2` for `CSETBOUNDS`, `CANDPERM`, `CINCOFFSET`, `CSW`)
  - Branch/JAL/JALR flushing & Branch/JALR target forwarding
  - Full RV32M multiplication (Objective 1 Booth-Wallace tree datapath reuse)
  - Multi-cycle iterative non-restoring DIV/DIVU/REM/REMU
  - Architectural retirement commit interface
  - SystemMMIO MEM-stage interception & RAM window isolation
  - Objective 1 hardware telemetry bridge (`0x80001000`–`0x80001010`) with verified CLA/MUL switching isolation
  - OS/scheduler cross-layer registers (`PROCESS_BEHAVIOR_CLASS`, `SCHED_HINT`, `CURRENT_CONTEXT` at `0x80002004`–`0x80002024`)
  - Performance & execution event counters (`0x8000200C`–`0x80002020`)
  - Hardware Capability Register File (`c0`–`c7`): `c0` permanently NULL, `c1` Data Memory Root, `c2` System MMIO Root, `c3`–`c7` general-purpose capability registers
  - Custom-0 (`0x0B`) Capability Manipulation: `CSETBOUNDS`, `CANDPERM`, `CINCOFFSET`, `CGETBASE`, `CGETLEN`, `CGETTAG`, `CGETPERM`
  - Custom-1 (`0x2B`) Capability Protected Memory: `CLB`, `CLH`, `CLW`, `CSB`, `CSH`, `CSW`
  - Overflow-safe 33-bit / 34-bit bounds checking with strict `Tag -> Bounds -> Permission` precedence
  - Pipelined EX $\rightarrow$ MEM derivation violation metadata and unified single-source MEM security event logging (`SEC_PC = exMemReg.pc`)
  - Sticky first-event security logger with W1C clear + simultaneous-event priority (`0x80002100`–`0x80002110`)
  - Comprehensive integration programs A through F verifying spatial safety, attenuation, NULL dereference, RAW interlock, MMIO policy separation, and mixed forwarding
  - Complete 5-Section cross-model differential verification across Python reference emulator, SingleCycleCore, and PipelinedCore (17 benchmark programs, 209 retirement events bit-exact)

- **Target Architecture (Future Phase 8)**:
  - Hardware security fault trapping / exception vectoring
  - OS context switching / capability spill-fill abstractions
  - Final Objective 2 integration freeze

---

## 5-Stage Capability-Hardened Pipeline Architecture

```
                         ┌────────────┐
                         │   PC       │
                         └─────┬──────┘
                               │
                               ▼
                     ┌───────────────────┐
                     │ IF — Instruction  │
                     │      Fetch        │
                     └────────┬──────────┘
                              │ IF/ID
                              ▼
                     ┌───────────────────┐
                     │ ID — Decode       │
                     │ Control Unit      │
                     │ Integer RF (x0-31)│
                     │ Capability RF(c0-7│ ◄─── Same-cycle WB->ID Bypass
                     └────────┬──────────┘
                              │ ID/EX
                              ▼
       Objective 1 ───────► ┌────────────────────┐
       HCLA                 │ EX — Execute       │
       ALU                  │ ALU / MUL / DIV    │
       Booth-Wallace        │ Branch Compare     │
                            │ Cap Derivation     │ (CSETBOUNDS, CANDPERM, CINCOFFSET, CGET*)
                            │ Violation Detect   │
                            └─────────┬──────────┘
                                      │ EX/MEM (Carries Cap, EffAddr & Violation Metadata)
                                      ▼
                           ┌─────────────────────┐
                           │ MEM — Memory & Sec  │
                           │ ├── CapabilityCheck │ (Tag -> Bounds -> Permission Checker)
                           │ ├── Single SecEvent │ ──► SystemMMIO Logger (SEC_PC = exMemReg.pc)
                           │ ├── DataMemory      │ (Protected RAM accesses)
                           │ └── SystemMMIO      │ (Protected MMIO & Telemetry)
                           └──────────┬──────────┘
                                      │ MEM/WB
                                      ▼
                           ┌──────────────────┐
                           │ WB — Writeback   │
                           │ ├── Integer RF   │
                           │ └── Cap RF (c0-7)│
                           └──────────────────┘

                              +
             ┌─────────────────────────────────────┐
             │ Hazard Unit: Load-Use & Cap RAW     │
             │ Forwarding Unit: EX/MEM & MEM/WB    │
             └─────────────────────────────────────┘
```

---

## Directory Structure

```
objective02-riscv-core/
├── build.sbt
├── README.md
├── reference/
│   ├── rv32i_interpreter.py       # RV32IM + CapabilityLite Python reference emulator & telemetry oracle
│   └── differential_runner.py     # 5-Section differential verification runner (17 suites, 209 events)
├── test_traces/                   # Architectural retirement trace fixtures
│   ├── prog1_alu_logic.json
│   ├── prog2_loop_accum.json
│   ├── prog3_mem_ops.json
│   ├── prog4_link_return.json
│   ├── prog5_multiplier_tree.json
│   ├── prog_rv32m.json
│   ├── pipe_bench1.json
│   ├── pipe_bench2.json
│   ├── pipe_bench3.json
│   ├── progMMIO.json
│   ├── progBranchTelemetery.json
│   ├── progA_capability_bounds.json
│   ├── progB_capability_perms.json
│   ├── progC_capability_null.json
│   ├── progD_capability_raw.json
│   ├── progE_capability_mmio.json
│   └── progF_capability_gpr_forwarding.json
├── src/
│   ├── main/scala/
│   │   └── objective02/
│   │       ├── capability/
│   │       │   ├── CapabilityLite.scala   # Bounded capability Bundle & root definitions
│   │       │   ├── CapabilityRegFile.scala# 8-register file (c0 NULL, c1 RAM, c2 MMIO, c3-c7 GP)
│   │       │   └── CapabilityChecker.scala# Combinational bounds/perm/tag checker
│   │       ├── isa/
│   │       │   ├── Opcodes.scala          # 7-bit opcodes (including OP_CAP 0x0B, OP_CAP_MEM 0x2B)
│   │       │   └── Instructions.scala     # Bitfield extractors (rd, rs1, rs2, funct3, funct7)
│   │       ├── decode/
│   │       │   ├── ImmediateGenerator.scala
│   │       │   ├── ControlSignals.scala   # Operand tags (usesIntRs1, usesIntRs2, usesCapRs1)
│   │       │   └── Decoder.scala          # Full-M + CapabilityLite decoder
│   │       ├── datapath/
│   │       │   ├── RegisterFile.scala     # 32 × 32-bit GPRs (hardwired x0 = 0)
│   │       │   ├── ProgramCounter.scala
│   │       │   └── BranchJumpUnit.scala
│   │       ├── memory/
│   │       │   ├── InstructionMemory.scala
│   │       │   └── DataMemory.scala
│   │       ├── execute/
│   │       │   ├── RV32MMultiplier.scala  # Reuses Objective 1 Booth-Wallace tree
│   │       │   └── IterativeDivider.scala # Multi-cycle division/remainder
│   │       ├── system/
│   │       │   ├── MMIOAddress.scala      # System & telemetry address definitions
│   │       │   ├── SecurityEvent.scala    # Security violation contract, reasons, and types
│   │       │   └── SystemMMIO.scala       # Cross-layer MMIO, performance counters, sticky logger
│   │       ├── pipeline/
│   │       │   ├── PipelineRegisters.scala# Latch, stall, flush logic
│   │       │   ├── HazardUnit.scala       # Load-use and capability RAW hazard detection
│   │       │   ├── ForwardingUnit.scala   # EX/MEM and MEM/WB integer GPR forwarding
│   │       │   └── PipelinedCore.scala    # 5-stage RV32IM + CapabilityLite processor
│   │       └── core/
│   │           └── SingleCycleCore.scala  # Frozen reference core
│   └── test/scala/
│       └── objective02/
│           ├── ImmediateGeneratorSpec.scala
│           ├── DecoderSpec.scala
│           ├── RegisterFileSpec.scala
│           ├── ProgramCounterSpec.scala
│           ├── BranchJumpUnitSpec.scala
│           ├── DataMemorySpec.scala
│           ├── RV32MMultiplierSpec.scala
│           ├── IterativeDividerSpec.scala
│           ├── HazardUnitSpec.scala
│           ├── ForwardingUnitSpec.scala
│           ├── PipelineRegistersSpec.scala
│           ├── CapabilityRegFileSpec.scala
│           ├── CapabilityCheckerSpec.scala
│           ├── SingleCycleCoreSpec.scala
│           ├── SystemMMIOSpec.scala
│           └── PipelinedCoreSpec.scala
```

---

## Hardware Contracts & Memory Map

- **XLEN**: 32-bit
- **Instruction Width**: 32-bit
- **Byte Order**: Little-endian
- **Execution Datapath**: Directly integrates Objective 1's `ALU(32)` arithmetic datapath (including `HierarchicalCarryLookaheadAdder`, `BoothWallaceMultiplier`, and `TelemetryBlock`).

### CapabilityLite Instruction Format (Custom-0 & Custom-1)

1. **Capability Manipulation (`opcode = 0x0B`)**:
   - `CSETBOUNDS cd, cs1, rs2` (`funct3 = 0`): Derives bounded child capability `{tag = cs1.tag, base = cs1.base + cs1.offset, length = rs2, perms = cs1.perms, offset = 0}`.
   - `CANDPERM cd, cs1, rs2` (`funct3 = 1`): Monotonically attenuates permissions `{perms = cs1.perms & rs2[2:0]}`.
   - `CINCOFFSET cd, cs1, rs2` (`funct3 = 2`): Adjusts cursor by signed 32-bit delta with widened signed arithmetic `$0 \le (\text{offset} + \text{delta}) \le \text{length}$`.
   - `CGETBASE rd, cs1` (`funct3 = 3`): Reads 32-bit base address of `cs1` into GPR `rd`.
   - `CGETLEN rd, cs1` (`funct3 = 4`): Reads 32-bit length of `cs1` into GPR `rd`.
   - `CGETTAG rd, cs1` (`funct3 = 5`): Reads 1-bit validity tag of `cs1` into GPR `rd`.
   - `CGETPERM rd, cs1` (`funct3 = 6`): Reads 3-bit permissions of `cs1` into GPR `rd`.

2. **Capability Protected Memory (`opcode = 0x2B`)**:
   - `CLB / CLH / CLW rd, imm(cs1)` (`funct3 = 0, 1, 2`): Protected load requiring `tag == 1`, valid bounds, and `READ` permission bit (`bit 0`).
   - `CSB / CSH / CSW rs2, imm(cs1)` (`funct3 = 4, 5, 6`): Protected store requiring `tag == 1`, valid bounds, and `WRITE` permission bit (`bit 1`).

### System MMIO Address Map (`0x80000000`–`0x8000FFFF`)

| Address Range | Register Name | Access | Description |
| :--- | :--- | :--- | :--- |
| `0x80001000` | `REV_ENERGY_ACC` | RO | Objective 1 Reversible Energy Accumulator |
| `0x80001004` | `CLA_SWITCHING` | RO | Objective 1 CLA Switching Activity (Hamming distance) |
| `0x80001008` | `MUL_THERMAL` | RO | Objective 1 Multiplier Switching / Thermal Proxy |
| `0x8000100C` | `EDP_CURRENT` | RO | Objective 1 Energy-Delay Product Metric |
| `0x80001010` | `EDP_CONFIG` | RO | Objective 1 EDP Configuration Weight (Default 1) |
| `0x80002000` | `BRANCH_CONFIDENCE` | RO | Reserved Branch Confidence Metric (Reset 0) |
| `0x80002004` | `PROCESS_BEHAVIOR_CLASS` | RW | OS Process Behavior Classifier Register (Reset 0) |
| `0x80002008` | `SCHED_HINT` | RW | OS Scheduler Hint Register (Reset 0) |
| `0x8000200C` | `RETIRED_COUNT` | RO | Instructions Committed / Retired Counter |
| `0x80002010` | `BRANCH_TAKEN_COUNT` | RO | Conditional Taken Branch Counter |
| `0x80002014` | `LOAD_USE_STALL_COUNT` | RO | Cycles Stalled on Load-Use RAW Hazard |
| `0x80002018` | `DIV_BUSY_CYCLES` | RO | Divider Multi-Cycle Execution Busy Cycles |
| `0x8000201C` | `PIPELINE_STALL_COUNT` | RO | Total Pipeline Frontend Stall Cycles |
| `0x80002020` | `LAST_COMMIT_PC` | RO | Architectural PC of Last Retired Instruction |
| `0x80002024` | `CURRENT_CONTEXT` | RW | Thread / Domain Context Identifier (Reset 0) |
| `0x80002100` | `SEC_STATUS` | RW | Bit 0: Security Violation Pending (Write-1-to-Clear) |
| `0x80002104` | `SEC_PC` | RO | Offending Program Counter on Violation (`exMemReg.pc`) |
| `0x80002108` | `SEC_ADDR` | RO | Offending Target Address on Violation |
| `0x8000210C` | `SEC_INFO` | RO | `[5:4]` Access Type (R/W/X/CapOp), `[3:0]` Violation Reason |
| `0x80002110` | `SEC_CONTEXT` | RO | Thread Context ID at Violation |

---

## Phase 7 Integration Verification Programs

- **Program A (Buffer Overflow Containment)**: Proves in-bounds capability store succeeds while out-of-bounds store is suppressed and captured in `SEC_*` registers with `BOUNDS` reason.
- **Program B (Permission Attenuation & Escalation Prevention)**: Demonstrates monotonic attenuation via `CANDPERM` (`RW -> RO`), confirms read success, and denies write escalation with `WRITE_PERMISSION` reason.
- **Program C (NULL / Uninitialized Capability Access Denials)**: Demonstrates immediate denial on `c0` or uninitialized capability loads with `INVALID_CAPABILITY` reason.
- **Program D (Zero-NOP Capability RAW Interlock)**: Proves back-to-back capability derivation (`CSETBOUNDS -> CANDPERM -> CINCOFFSET -> CLW`) executes with zero software NOPs via hardware RAW interlocking.
- **Program E (Protected MMIO Authorization & Layer Separation)**: Confirms valid capability access to `0x80002004` succeeds, while unauthorized write to read-only MMIO is rejected by the peripheral without generating a false capability violation.
- **Program F (Mixed GPR & Capability Forwarding Dependencies)**: Proves integer GPR forwarding into `CSETBOUNDS` and protected store payload forwarding into `CSW` with zero NOPs.

---

## Architectural Limitations & Scope

CapabilityLite is a **CHERI-inspired capability security model**, not a full CHERI-compliant architecture:
1. **Unprotected Baseline Access**: Ordinary RV32I `LW`/`SW` instructions remain baseline memory operations and do not require capabilities.
2. **Register-Only Capabilities**: Capabilities reside exclusively within the 8 capability registers `c0`–`c7`. There is no tagged DRAM / capability memory representation.
3. **No PCC Enforcement**: Instruction fetch uses standard PC addresses; capability checking is applied to data accesses.
4. **Reserved Permissions**: `EXEC` permission is reserved for future hardware extensions.
5. **No Sealing**: Object sealing and unsealing mechanisms are omitted in CapabilityLite.
6. **Non-Trapping Violations**: Security violations record evidence in the MMIO sticky logger and allow the pipeline to continue rather than raising a hardware trap.

---

## Testing & Verification

Run the complete Objective 2 test suite (94 tests):
```bash
cd objective02-riscv-core
sbt --batch test
```

Run the complete differential verification runner across all 17 benchmark suites (209 retirement events bit-exact):
```bash
python3 reference/differential_runner.py
```

Run the Objective 1 digital logic regression suite (24 tests):
```bash
cd ../objective01-digital-logic
sbt --batch test
```
