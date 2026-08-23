# Objective 2: RISC-V RV32IM Processor Core & Hardware Security

Welcome to **Objective 2** of the AI-Native Computer System project.

- **Current Implementation**:
  - Frozen SingleCycleCore reference
  - Five-stage RV32IM PipelinedCore
  - EX/MEM and MEM/WB forwarding
  - Load-use interlock
  - Branch/JAL/JALR flushing
  - Full RV32M multiplication
  - Multi-cycle iterative DIV/DIVU/REM/REMU
  - Architectural retirement interface
  - Python/SingleCycle/Pipeline differential verification
- **Target Architecture**: Hardware-enforced CHERI-lite capability security and cross-layer OS/telemetry interfaces (Phases 6–7).

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
                           │ Data Memory Access  │
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

> **Future Phase 6/7 Capabilities & Telemetry Interfaces:**
> Capability checks • MMIO • telemetry • sched_hint • security_violation

---

## Directory Structure

```
objective02-riscv-core/
├── build.sbt
├── README.md
├── reference/
│   ├── rv32i_interpreter.py       # RV32IM Python reference emulator
│   └── differential_runner.py     # Differential verification runner (Python vs Chisel commit traces)
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
│   │       ├── pipeline/
│   │       │   ├── PipelineRegisters.scala# Latch, stall, flush logic
│   │       │   ├── HazardUnit.scala       # Load-use and control hazard detection
│   │       │   ├── ForwardingUnit.scala   # EX/MEM and MEM/WB forwarding logic
│   │       │   └── PipelinedCore.scala    # 5-stage RV32IM processor
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
│           └── PipelinedCoreSpec.scala
```

---

## Hardware Contracts

- **XLEN**: 32-bit
- **Instruction Width**: 32-bit
- **Byte Order**: Little-endian
- **Execution Reuse**: Directly reuses Objective 1's `ALU(32)` arithmetic datapath (including `HierarchicalCarryLookaheadAdder` and `BoothWallaceMultiplier`). `TelemetryBlock` and performance counter integration are reserved for the Phase 6 MMIO interface.

---

## Testing & Verification

Run the test suite:
```bash
sbt test
```

Run the differential verification suite:
```bash
python3 reference/differential_runner.py
```
