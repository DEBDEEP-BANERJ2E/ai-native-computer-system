# Objective 2: RISC-V RV32IM Processor Core & CapabilityLite Hardware Security

Welcome to **Objective 2** of the AI-Native Computer System project.

- **Current Implementation (Phase 8: Precise Security Traps, OS Capability Context Switching & Objective 2 Freeze)**:
  - Frozen SingleCycleCore reference architecture
  - Five-stage RV32IM PipelinedCore with CapabilityLite security extensions and precise security trapping
  - Full integer EX/MEM and MEM/WB forwarding + Load-Use hazard interlock
  - Capability RAW hazard interlock (stalling ID/EX & EX/MEM producers) + same-cycle WB $\rightarrow$ ID capability bypass
  - Clean integer/capability operand separation (`usesIntRs1`, `usesIntRs2`, `usesCapRs1`) with GPR forwarding into capability operations (`rs2` for `CSETBOUNDS`, `CANDPERM`, `CINCOFFSET`, `CSW`)
  - Branch/JAL/JALR flushing & Branch/JALR target forwarding
  - Full RV32M multiplication (Objective 1 Booth-Wallace tree datapath reuse)
  - Multi-cycle iterative non-restoring DIV/DIVU/REM/REMU with immediate kill port on traps
  - Architectural retirement commit interface
  - SystemMMIO MEM-stage interception & RAM window isolation
  - Objective 1 hardware telemetry bridge (`0x80001000`–`0x80001010`) with verified CLA/MUL switching isolation
  - OS/scheduler cross-layer registers (`PROCESS_BEHAVIOR_CLASS`, `SCHED_HINT`, `CURRENT_CONTEXT` at `0x80002004`–`0x80002024`)
  - Performance & execution event counters (`0x8000200C`–`0x80002020`)
  - Hardware Capability Register File (`c0`–`c7`):
    - `c0`: Hardware-immutable NULL capability (write attempts discarded)
    - `c1`: Hardware-immutable RAM Root capability (parameterized by RAM size, write attempts discarded)
    - `c2`: Hardware-immutable MMIO Root capability (covers `0x80000000`–`0x8000FFFF`, write attempts discarded)
    - `c3`–`c7`: General-purpose process capability registers (writable)
  - Custom-0 (`0x0B`) Capability Manipulation:
    - `CSETBOUNDS`, `CANDPERM`, `CINCOFFSET`, `CGETBASE`, `CGETLEN`, `CGETTAG`, `CGETPERM`
    - `CGETOFFSET rd, cs1` (`funct3 = 7`, `funct7 = 0x00`): Extracts capability cursor offset into GPR
    - `CCLEAR cd` (`funct3 = 7`, `funct7 = 0x01`): Clears process capability register `cd` to uninitialized NULL
  - Custom-1 (`0x2B`) Capability Protected Memory: `CLB`, `CLH`, `CLW`, `CSB`, `CSH`, `CSW`
  - Overflow-safe 33-bit / 34-bit bounds checking with strict `Tag -> Bounds -> Permission` precedence
  - Pipelined EX $\rightarrow$ MEM derivation violation metadata and unified single-source MEM security event logging (`SEC_PC = exMemReg.pc`)
  - Sticky first-event security audit logger with W1C clear + simultaneous-event priority (`0x80002100`–`0x80002110`)
  - Dedicated Architectural Precise Trap Engine (`0x80002114`–`0x80002130`):
    - Combinational MEM-stage trap redirect (`takePreciseTrap`) in exact cycle of fault
    - Architectural state suppression: faulting MEM instruction does not commit (`commit.valid = 0`), register file and memory writeback suppressed
    - Pipeline flush: younger IF, ID, and EX stages flushed immediately; active iterative divider killed (`io.kill = 1`) to eliminate deadlock
    - Synchronous capture on clock edge: `TRAP_ACTIVE := 1`, `TRAP_EPC := exMemReg.pc`, `TRAP_CAUSE := (accessType << 4) | reason`, `TRAP_ADDR := address`, `TRAP_CONTEXT := context`
    - Alignment enforcement: `TRAP_VECTOR` and handler-written `TRAP_EPC` masked with `& 0xFFFFFFFC`
    - Double fault detection: nested violation with `TRAP_ACTIVE = 1` latches `DOUBLE_FAULT = 1` (set-over-W1C priority), preserves existing `TRAP_*` state, suppresses side effects, and continues without recursive redirect
    - Normal `TRAP_RETURN` command store retirement (`commit.valid = 1`), resetting `TRAP_ACTIVE` and redirecting PC to `TRAP_EPC`
  - Comprehensive Integration Programs A through J verifying precise traps, bounds expansion/retry, EPC skipping, OS PCB capability context switching, audit logger independence, and double fault handling
  - 6-Section cross-model differential verification across Python reference emulator, SingleCycleCore, and PipelinedCore (18 benchmark programs, 223 retirement events bit-exact)

---

## 5-Stage Capability-Hardened Pipeline Architecture

```
                         ┌────────────┐
                         │   PC       │ ◄─── Precise Trap Redirect (TRAP_VECTOR / TRAP_EPC)
                         └─────┬──────┘
                               │
                               ▼
                     ┌───────────────────┐
                     │ IF — Instruction  │
                     │      Fetch        │
                     └────────┬──────────┘
                              │ IF/ID (Flush on Trap / Return)
                              ▼
                     ┌───────────────────┐
                     │ ID — Decode       │
                     │ Control Unit      │
                     │ Integer RF (x0-31)│
                     │ Capability RF(c0-7│ ◄─── Same-cycle WB->ID Bypass (c0-c2 immutable)
                     └────────┬──────────┘
                              │ ID/EX (Flush on Trap / Return)
                              ▼
       Objective 1 ───────► ┌────────────────────┐
       HCLA                 │ EX — Execute       │
       ALU                  │ ALU / MUL / DIV    │ ◄─── Divider Kill on Trap/Return
       Booth-Wallace        │ Branch Compare     │
                            │ Cap Derivation     │ (CSETBOUNDS, CANDPERM, CINCOFFSET, CGET*, CCLEAR)
                            │ Violation Detect   │
                            └─────────┬──────────┘
                                      │ EX/MEM (Carries Cap, EffAddr & Violation Metadata)
                                      ▼
                           ┌─────────────────────┐
                           │ MEM — Memory & Sec  │
                           │ ├── CapabilityCheck │ (Tag -> Bounds -> Permission Checker)
                           │ ├── Combinational   │ ──► takePreciseTrap ──► Flush Pipeline & Set PC
                           │ │   Trap Engine     │ ──► SystemMMIO Trap Registers (0x80002114-30)
                           │ ├── Single SecEvent │ ──► SystemMMIO Sticky Logger (0x80002100-10)
                           │ ├── DataMemory      │ (Protected RAM accesses)
                           │ └── SystemMMIO      │ (Protected MMIO & Telemetry)
                           └──────────┬──────────┘
                                      │ MEM/WB (memEnterWbValid suppressed on Trap)
                                      ▼
                           ┌──────────────────┐
                           │ WB — Writeback   │
                           │ ├── Integer RF   │
                           │ └── Cap RF (c3-7)│
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
├── generated/                     # Emitted SystemVerilog RTL
│   ├── SingleCycleCore.sv
│   ├── PipelinedCore.sv
│   ├── IterativeDivider.sv
│   ├── CapabilityRegFile.sv
│   └── SystemMMIO.sv
├── reference/
│   ├── rv32i_interpreter.py       # RV32IM + CapabilityLite Python reference emulator & trap oracle
│   └── differential_runner.py     # 6-Section differential verification runner (18 suites, 223 events)
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
│   ├── progF_capability_gpr_forwarding.json
│   └── phase8_progA_precise_trap.json
├── src/
│   ├── main/scala/
│   │   └── objective02/
│   │       ├── GenerateRTL.scala      # SystemVerilog generator entrypoint
│   │       ├── capability/
│   │       │   ├── CapabilityLite.scala   # Bounded capability Bundle & root definitions
│   │       │   ├── CapabilityRegFile.scala# 8-register file (c0 NULL, c1 RAM, c2 MMIO immutable; c3-c7 GP)
│   │       │   └── CapabilityChecker.scala# Combinational bounds/perm/tag checker
│   │       ├── isa/
│   │       │   ├── Opcodes.scala          # 7-bit opcodes (including OP_CAP 0x0B, OP_CAP_MEM 0x2B)
│   │       │   └── Instructions.scala     # Bitfield extractors (rd, rs1, rs2, funct3, funct7)
│   │       ├── decode/
│   │       │   ├── ImmediateGenerator.scala
│   │       │   ├── ControlSignals.scala   # Operand tags (usesIntRs1, usesIntRs2, usesCapRs1)
│   │       │   └── Decoder.scala          # Full-M + CapabilityLite + Phase 8 decoder
│   │       ├── datapath/
│   │       │   ├── RegisterFile.scala     # 32 × 32-bit GPRs (hardwired x0 = 0)
│   │       │   ├── ProgramCounter.scala
│   │       │   └── BranchJumpUnit.scala
│   │       ├── memory/
│   │       │   ├── InstructionMemory.scala
│   │       │   └── DataMemory.scala
│   │       ├── execute/
│   │       │   ├── RV32MMultiplier.scala  # Reuses Objective 1 Booth-Wallace tree
│   │       │   └── IterativeDivider.scala # Multi-cycle division/remainder with kill port
│   │       ├── system/
│   │       │   ├── MMIOAddress.scala      # System, telemetry, logger & trap address definitions
│   │       │   ├── SecurityEvent.scala    # Security violation contract, reasons, and types
│   │       │   └── SystemMMIO.scala       # Cross-layer MMIO, performance counters, trap engine
│   │       ├── pipeline/
│   │       │   ├── PipelineRegisters.scala# Latch, stall, flush logic
│   │       │   ├── HazardUnit.scala       # Load-use, capability RAW hazard & trap flush detection
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

### CapabilityLite Instruction Set Architecture

1. **Capability Manipulation (`opcode = 0x0B`)**:
   - `CSETBOUNDS cd, cs1, rs2` (`funct3 = 0`): Derives bounded child capability `{tag = cs1.tag, base = cs1.base + cs1.offset, length = rs2, perms = cs1.perms, offset = 0}`.
   - `CANDPERM cd, cs1, rs2` (`funct3 = 1`): Monotonically attenuates permissions `{perms = cs1.perms & rs2[2:0]}`.
   - `CINCOFFSET cd, cs1, rs2` (`funct3 = 2`): Adjusts cursor by signed 32-bit delta with widened signed arithmetic `$0 \le (\text{offset} + \text{delta}) \le \text{length}$`.
   - `CGETBASE rd, cs1` (`funct3 = 3`): Reads 32-bit base address of `cs1` into GPR `rd`.
   - `CGETLEN rd, cs1` (`funct3 = 4`): Reads 32-bit length of `cs1` into GPR `rd`.
   - `CGETTAG rd, cs1` (`funct3 = 5`): Reads 1-bit validity tag of `cs1` into GPR `rd`.
   - `CGETPERM rd, cs1` (`funct3 = 6`): Reads 3-bit permissions of `cs1` into GPR `rd`.
   - `CGETOFFSET rd, cs1` (`funct3 = 7`, `funct7 = 0x00`): Reads 32-bit cursor offset of `cs1` into GPR `rd`.
   - `CCLEAR cd` (`funct3 = 7`, `funct7 = 0x01`): Clears process capability register `cd` (`cd >= 3`) to NULL.

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
| `0x80002024` | `CURRENT_CONTEXT` | RW | Active Thread / Domain Context Identifier (Reset 0) |
| `0x80002100` | `SEC_STATUS` | RW | Bit 0: Security Violation Pending (Write-1-to-Clear) |
| `0x80002104` | `SEC_PC` | RO | Offending Program Counter on Violation (`exMemReg.pc`) |
| `0x80002108` | `SEC_ADDR` | RO | Offending Target Address on Violation |
| `0x8000210C` | `SEC_INFO` | RO | `[5:4]` Access Type (R/W/X/CapOp), `[3:0]` Violation Reason |
| `0x80002110` | `SEC_CONTEXT` | RO | Thread Context ID at Violation |
| `0x80002114` | `TRAP_CONTROL` | RW | Bit 0: `TRAP_ENABLE` (Reset 0) |
| `0x80002118` | `TRAP_STATUS` | RW | Bit 0: `ACTIVE` (RO), Bit 1: `DOUBLE_FAULT` (W1C) |
| `0x8000211C` | `TRAP_VECTOR` | RW | 4-byte aligned base PC of security trap handler |
| `0x80002120` | `TRAP_EPC` | RW | Offending Instruction PC (Writable by handler when ACTIVE) |
| `0x80002124` | `TRAP_CAUSE` | RO | `[5:4]` Access Type (R/W/X/CapOp), `[3:0]` Violation Reason |
| `0x80002128` | `TRAP_ADDR` | RO | Offending Memory Address |
| `0x8000212C` | `TRAP_CONTEXT` | RO | Process / Scheduler Context at Violation |
| `0x80002130` | `TRAP_RETURN` | WO | Write 1 to clear ACTIVE, flush pipeline, and redirect PC to `TRAP_EPC` |

---

## Phase 8 Integration Verification Programs (A – J)

- **Program A (Precise OOB Store Trap & Redirection)**: Proves out-of-bounds `CSW` triggers `takePreciseTrap`, suppresses writeback/store, captures exact fault metadata (`TRAP_EPC = 0x28`, `TRAP_CAUSE = 0x12`, `TRAP_ADDR = 0x214`), flushes younger `addi x14`, and jumps to handler at `0x80`.
- **Program B (Permission Violation Trap)**: Proves unauthorized store against read-only capability immediately traps with `WRITE_PERMISSION` cause (`0x14`).
- **Program C (NULL Capability Access Trap)**: Proves store against uninitialized `c4` traps immediately with `INVALID_CAPABILITY` cause (`0x13`).
- **Program D (Precise Trap Age Ordering & Pipeline Flush)**: Proves older instruction commits normally in WB, while faulting instruction in MEM and younger instructions in ID/EX are suppressed and flushed.
- **Program E1 (MEM Trap vs Younger EX Taken Branch)**: Proves MEM trap takes absolute redirect priority over younger branch redirect in EX stage.
- **Program E2 (MEM Trap vs Younger Active Divider)**: Proves MEM trap immediately kills multi-cycle division in EX stage via `io.kill`, eliminating pipeline deadlock.
- **Program F (Capability Bounds Expansion & Fault Retry)**: Demonstrates OS trap handler inspecting `TRAP_ADDR`, re-deriving expanded capability bounds into `c3`, and executing `TRAP_RETURN` to retry the faulting instruction successfully.
- **Program G (EPC Skip Handler)**: Demonstrates OS trap handler advancing `TRAP_EPC := TRAP_EPC + 4`, executing `TRAP_RETURN`, and resuming execution past the faulting instruction.
- **Program H (OS Capability Context Switching & PCB Provenance)**: Demonstrates saving process capability registers `c3`–`c7` to memory PCB, clearing capability registers via `CCLEAR`, and restoring process capability state deterministically via root capability provenance (`rootSelector = 0` $\rightarrow$ `c1`).
- **Program I (Audit Logger Independence)**: Proves frozen first-event audit logger (`SEC_*`) remains immutable and independent across subsequent precise trap triggers.
- **Program J (Double Fault Latching & Set-over-W1C Priority)**: Proves nested violation while `TRAP_ACTIVE = 1` latches `DOUBLE_FAULT = 1`, preserves original `TRAP_*` metadata, suppresses memory side effects, and allows handler to continue sequentially without recursive redirection.

---

## Architectural Scope & Layer Boundaries

The Phase 8 trap subsystem and CapabilityLite architecture define clear contract boundaries:
1. **Trusted Software Interface**: Phase-8 trap register configuration (`TRAP_CONTROL`, `TRAP_VECTOR`), trap handler routines, and PCB context management are trusted-software interfaces.
2. **Out-of-Scope Hardware Extensions**: Hardware privilege modes (RISC-V Machine/Supervisor/User modes), CSR registers, physical memory protection (PMP/ePMP), capability-tagged DRAM, Program Counter Capabilities (PCC), and decoupled access-execute (DAE) are outside Objective 2 and deferred to Objectives 3 and 8.

---

## Testing & Verification

Run the complete Objective 2 test suite (108 tests in 16 suites):
```bash
cd objective02-riscv-core
sbt --batch test
```

Run the complete 6-Section differential verification runner across all 18 benchmark suites (223 retirement events bit-exact):
```bash
python3 reference/differential_runner.py
```

Run the Objective 1 digital logic regression suite (24 tests in 13 suites):
```bash
cd ../objective01-digital-logic
sbt --batch test
```

Generate SystemVerilog RTL for all Objective 2 processor components:
```bash
cd ../objective02-riscv-core
sbt --batch "runMain objective02.GenerateRTL"
```
