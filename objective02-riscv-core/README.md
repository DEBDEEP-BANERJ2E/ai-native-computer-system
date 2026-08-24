# Objective 2: RISC-V RV32IM Processor Core & Hardware Security

Welcome to **Objective 2** of the AI-Native Computer System project.

- **Current Implementation (Phase 6)**:
  - Frozen SingleCycleCore reference
  - Five-stage RV32IM PipelinedCore
  - EX/MEM and MEM/WB forwarding + Load-Use hazard interlock
  - Branch/JAL/JALR flushing & Branch/JALR target forwarding
  - Full RV32M multiplication (Booth-Wallace tree reuse)
  - Multi-cycle iterative non-restoring DIV/DIVU/REM/REMU
  - Architectural retirement commit interface
  - SystemMMIO MEM-stage interception & RAM window isolation
  - Objective 1 hardware telemetry bridge (`0x80001000`–`0x80001010`)
  - OS/scheduler cross-layer registers (`PROCESS_BEHAVIOR_CLASS`, `SCHED_HINT` at `0x80002004`–`0x80002008`)
  - Performance & execution event counters (`0x8000200C`–`0x80002020`)
  - Reserved `BRANCH_CONFIDENCE` register (`0x80002000`)
  - Frozen `SecurityViolationEvent` hardware contract (`0x80002100`–`0x80002110`)
  - Sticky first-event security logger with W1C clear + simultaneous-event priority
  - 4-way differential verification across Python oracle, SingleCycleCore, and PipelinedCore (11 benchmark programs, 152 retirement events)

- **Target Architecture (Future Phase 7 / 8)**:
  - **Phase 7**: Capability-Lite Hardware Security (CHERI-inspired bounded tagged capabilities, `CSETBOUNDS`, `CANDPERM`, `CINCOFFSET`, MEM/IF permission & bounds enforcement).
  - **Phase 8**: Hardware security fault trapping, violation dispatch to Phase 6 security logger, attack mitigation suites, and final Objective 2 freeze.

---

## 5-Stage Pipeline Architecture

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
                     │ Register File     │
                     └────────┬──────────┘
                              │ ID/EX
                              ▼
       Objective 1 ───────► ┌────────────────────┐
       HCLA                 │ EX — Execute       │
       ALU                  │ ALU / MUL / DIV    │
       Booth-Wallace        │ Branch Compare     │
                            └─────────┬──────────┘
                                      │ EX/MEM
                                      ▼
                           ┌─────────────────────┐
                           │ MEM — Memory        │
                           │ ├── DataMemory      │
                           │ └── SystemMMIO      │
                           │     ├── Telemetry   │
                           │     ├── OS / Hints  │
                           │     └── Sec Logger  │
                           └──────────┬──────────┘
                                      │ MEM/WB
                                      ▼
                           ┌──────────────────┐
                           │ WB — Writeback   │
                           └──────────────────┘

                              +
             ┌─────────────────────────────────────┐
             │ Forwarding / Hazard Detection       │
             └─────────────────────────────────────┘
```

> **Future Phase 7 Capability Enforcement:**
> Bounded tagged capabilities • Base/Top checks • Permissions (R/W/X) • Active violation producer

---

## Directory Structure

```
objective02-riscv-core/
├── build.sbt
├── README.md
├── reference/
│   ├── rv32i_interpreter.py       # RV32IM Python reference emulator & telemetry oracle
│   └── differential_runner.py     # 4-Section differential verification runner
├── src/
│   ├── main/scala/
│   │   └── objective02/
│   │       ├── isa/
│   │       │   ├── Opcodes.scala          # 7-bit opcodes, funct3, funct7 constants
│   │       │   └── Instructions.scala     # Bitfield extractors (rd, rs1, rs2, funct3, etc.)
│   │       ├── decode/
│   │       │   ├── ImmediateGenerator.scala
│   │       │   ├── ControlSignals.scala
│   │       │   └── Decoder.scala          # Full-M decoder
│   │       ├── datapath/
│   │       │   ├── RegisterFile.scala     # 32 × 32-bit registers (hardwired x0 = 0)
│   │       │   ├── ProgramCounter.scala
│   │       │   └── BranchJumpUnit.scala
│   │       ├── memory/
│   │       │   ├── InstructionMemory.scala
│   │       │   └── DataMemory.scala
│   │       ├── execute/
│   │       │   ├── RV32MMultiplier.scala  # Reuses Booth-Wallace multiplier
│   │       │   └── IterativeDivider.scala # Multi-cycle division/remainder
│   │       ├── system/
│   │       │   ├── MMIOAddress.scala      # System & telemetry address definitions
│   │       │   ├── SecurityEvent.scala    # Security violation contract & reasons
│   │       │   └── SystemMMIO.scala       # Cross-layer MMIO, counters, telemetry, sec logger
│   │       ├── pipeline/
│   │       │   ├── PipelineRegisters.scala# Latch, stall, flush logic
│   │       │   ├── HazardUnit.scala       # Load-use and control hazard detection
│   │       │   ├── ForwardingUnit.scala   # EX/MEM and MEM/WB forwarding logic
│   │       │   └── PipelinedCore.scala    # 5-stage RV32IM processor with MMIO & telemetry
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
| `0x80002100` | `SEC_STATUS` | RW | Bit 0: Security Violation Pending (Write-1-to-Clear) |
| `0x80002104` | `SEC_PC` | RO | Offending Program Counter on Violation |
| `0x80002108` | `SEC_ADDR` | RO | Offending Target Address on Violation |
| `0x8000210C` | `SEC_INFO` | RO | `[5:4]` Access Type (R/W/X), `[3:0]` Violation Reason |
| `0x80002110` | `SEC_CONTEXT` | RO | Thread / Domain Context ID at Violation |

---

## Testing & Verification

Run the complete Objective 2 test suite:
```bash
cd objective02-riscv-core
sbt --batch test
```

Run the differential verification suite (Python reference vs Chisel pipeline traces):
```bash
python3 reference/differential_runner.py
```

Run the Objective 1 regression suite:
```bash
cd ../objective01-digital-logic
sbt --batch test
```

