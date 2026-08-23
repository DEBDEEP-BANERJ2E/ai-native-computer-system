# Objective 2: RISC-V RV32IM Processor Core & Hardware Security

Welcome to **Objective 2** of the AI-Native Computer System project.

This directory contains the Chisel hardware implementation of a **5-stage pipelined RISC-V RV32IM processor core** featuring hardware-enforced **CHERI-lite capability security** and cross-layer OS/telemetry hooks.

---

## Architectural Overview

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
├── src/
│   ├── main/scala/
│   │   └── objective02/
│   │       ├── isa/
│   │       │   ├── Opcodes.scala          # 7-bit opcodes, funct3, funct7 constants
│   │       │   └── Instructions.scala      # Bitfield extractors (rd, rs1, rs2, funct3, etc.)
│   │       ├── decode/
│   │       │   ├── ImmediateGenerator.scala# 32-bit sign-extended immediate decoder (I, S, B, U, J)
│   │       │   ├── ControlSignals.scala    # Control word bundles and ALU operation enums
│   │       │   └── Decoder.scala           # Combinational instruction decoder
│   │       └── datapath/
│   │           ├── RegisterFile.scala      # 32 × 32-bit registers (hardwired x0 = 0)
│   │           └── ProgramCounter.scala    # 32-bit PC register with stall & branch support
│   └── test/scala/
│       └── objective02/
│           ├── ImmediateGeneratorSpec.scala# Comprehensive immediate format unit tests
│           ├── DecoderSpec.scala           # Comprehensive instruction decoder test suite
│           ├── RegisterFileSpec.scala      # 32-register verification & x0 hardwiring tests
│           └── ProgramCounterSpec.scala    # PC reset, increment, stall, and jump tests
```

---

## Hardware Contracts

- **XLEN**: 32-bit
- **Instruction Width**: 32-bit
- **Byte Order**: Little-endian
- **Registers**: `x0`–`x31` (`x0` hardwired to `0x00000000`)
- **Pipeline**: 5-Stage (IF, ID, EX, MEM, WB)
- **Execution Reuse**: Directly reuses Objective 1's `ALU`, `HierarchicalCarryLookaheadAdder`, `BoothWallaceMultiplier`, and `TelemetryBlock`.

---

## Testing & Verification

Run the test suite:
```bash
sbt test
```
