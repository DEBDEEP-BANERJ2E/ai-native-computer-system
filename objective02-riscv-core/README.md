# Objective 2: RISC-V RV32IM Processor Core & Hardware Security

Welcome to **Objective 2** of the AI-Native Computer System project.

- **Current Implementation**: Verified **Single-Cycle RISC-V RV32I + MUL Reference Core** ([`SingleCycleCore.scala`](file:///Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective02-riscv-core/src/main/scala/objective02/core/SingleCycleCore.scala)).
- **Target Architecture**: **5-Stage Pipelined RV32IM Processor** with hardware-enforced CHERI-lite capability security and cross-layer OS/telemetry interfaces (Phases 3–7).

---

## Target 5-Stage Pipeline Architecture

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
       ALU                  │ ALU / MUL          │
       Booth-Wallace        │ Branch Compare     │
                            └─────────┬──────────┘
                                      │ EX/MEM
                                      ▼
                           ┌─────────────────────┐
                           │ MEM — Memory        │
                           │ Capability Check    │
                           │ Bounds/Permissions  │
                           └──────────┬──────────┘
                                      │ MEM/WB
                                      ▼
                           ┌──────────────────┐
                           │ WB — Writeback   │
                           └──────────────────┘

                              +
             ┌─────────────────────────────────────┐
             │ Forwarding / Hazard Detection       │
             │ MMIO / telemetry_read               │
             │ sched_hint                          │
             │ security_violation                  │
             └─────────────────────────────────────┘
```

---

## Directory Structure

```
objective02-riscv-core/
├── build.sbt
├── README.md
├── reference/
│   ├── rv32i_interpreter.py       # Exact RV32I + MUL Python reference emulator
│   └── differential_runner.py     # Differential verification runner (Python vs Chisel commit traces)
├── src/
│   ├── main/scala/
│   │   └── objective02/
│   │       ├── isa/
│   │       │   ├── Opcodes.scala          # 7-bit opcodes, funct3, funct7 constants
│   │       │   └── Instructions.scala      # Bitfield extractors (rd, rs1, rs2, funct3, etc.)
│   │       ├── decode/
│   │       │   ├── ImmediateGenerator.scala# 32-bit sign-extended immediate decoder (I, S, B, U, J)
│   │       │   ├── ControlSignals.scala    # Control word bundles, ALUOps, and MOp enums
│   │       │   └── Decoder.scala           # Combinational instruction decoder with safety squash
│   │       ├── datapath/
│   │       │   ├── RegisterFile.scala      # 32 × 32-bit registers (hardwired x0 = 0)
│   │       │   ├── ProgramCounter.scala    # 32-bit PC register with REDIRECT > STALL priority
│   │       │   └── BranchJumpUnit.scala    # Branch evaluations and JAL/JALR target math
│   │       ├── memory/
│   │       │   ├── InstructionMemory.scala # Combinational ROM with preloading and NOP fallback
│   │       │   └── DataMemory.scala        # Byte-addressed little-endian RAM with alignment checks
│   │       └── core/
│   │           └── SingleCycleCore.scala   # Architectural reference core with commit/debug interface
│   └── test/scala/
│       └── objective02/
│           ├── ImmediateGeneratorSpec.scala# Immediate format unit tests
│           ├── DecoderSpec.scala           # Table-driven decoder and negative tests
│           ├── RegisterFileSpec.scala      # 32-register verification and x0 hardwiring tests
│           ├── ProgramCounterSpec.scala    # PC reset, increment, stall, and redirect tests
│           ├── BranchJumpUnitSpec.scala    # Branch condition logic and target address tests
│           ├── DataMemorySpec.scala        # Byte, halfword, word access, and misalignment tests
│           └── SingleCycleCoreSpec.scala   # Full core integration benchmarks & instruction coverage
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
