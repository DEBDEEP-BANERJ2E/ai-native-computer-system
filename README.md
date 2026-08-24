# AI-Native Computer System
Final-year project exploring an end-to-end computer system whose hardware,
operating system, compiler, data, network, and AI-agent layers can observe and
adapt to one another.

## Current Status

The repository currently implements and verifies two foundational hardware layers:

1. **Objective 1: Digital Logic and Arithmetic Foundation** (`objective01-digital-logic/`):
   - Reversible computing primitives (Fredkin and Toffoli gates).
   - Parameterized adders: Ripple-Carry Adder (RCA), Flat Carry-Lookahead Adder (CLA), and 4-bit block Hierarchical CLA.
   - High-throughput multipliers: Simple Shift-and-Add, Radix-4 Booth, Wallace Reduction Tree, and Booth-Wallace Multiplier.
   - Reusable 32-bit ALU with RISC-V operation set and status flags.
   - Hardware telemetry subsystem (`REV_ENERGY_ACC`, `CLA_SWITCHING`, `MUL_THERMAL`, `EDP_CURRENT`, `EDP_CONFIG`).
   - Full test suite: 24 unit and property tests with Python golden models.

2. **Objective 2: RISC-V Processor Core, System MMIO & CapabilityLite Security** (`objective02-riscv-core/`):
   - **SingleCycleCore**: Canonical baseline RV32I/M execution core.
   - **PipelinedCore**: 5-stage hazard-forwarding pipelined processor (IF, ID, EX, MEM, WB) with single-cycle EX/MEM and MEM/WB bypass paths, Load-Use stall detection, and Branch/JALR early evaluation.
   - **RV32M Full Multi-Cycle Extension**: Hardware multiplier and multi-cycle non-restoring iterative divider (`DIV`, `DIVU`, `REM`, `REMU`, `MUL`, `MULH`, `MULHSU`, `MULHU`).
   - **Hardware Telemetry & Cross-Layer System MMIO**: Telemetry block integration driven by retirement in WB, performance counters (`RETIRED_COUNT`, `BRANCH_TAKEN_COUNT`, `LOAD_USE_STALL_COUNT`, `DIV_BUSY_CYCLES`, `PIPELINE_STALL_COUNT`), and OS context classification registers (`CURRENT_CONTEXT`, `PROCESS_BEHAVIOR_CLASS`, `SCHED_HINT`).
   - **CapabilityLite Hardware Security (Phase 7)**:
     - 101-bit bounded capability registers `c0`–`c7` (`tag`, `base`, `length`, `perms`, `offset`) with hardware root initialization (`c1` = DataMemory, `c2` = SystemMMIO, `c0` = NULL).
     - Custom-0 (`0x0B`) capability manipulation instructions (`CSETBOUNDS`, `CANDPERMS`, `CINCOFFSET`, `CGETTAG`, `CGETBASE`, `CGETLEN`, `CGETOFFSET`, `CGETPERMS`).
     - Custom-1 (`0x2B`) capability-protected memory instructions (`CLW`, `CLH`, `CLHU`, `CLB`, `CLBU`, `CSW`, `CSH`, `CSB`).
     - Pipeline Capability Checker in MEM stage enforcing Tag, Bounds, and Permission checks with atomic suppression and MMIO sticky security violation logging (`SEC_STATUS`, `SEC_PC`, `SEC_ADDR`, `SEC_INFO`, `SEC_CONTEXT`).
   - **Verification**: 94 Chisel unit/integration tests and 17-benchmark cross-model differential verification suite (209 retirement events matched bit-exact across Python reference, SingleCycleCore, and PipelinedCore).

The Python AgentOS policy kernel in `objective10-agentos/agentos/` is an exploratory slice for Objective 10 and remains decoupled from the core hardware pipelines.

## Objectives

The supplied undergraduate guide defines ten connected objectives:

1. **Digital logic and arithmetic foundation**: build gates, adders, a
   multiplier, and an ALU with energy and thermal signals.
2. **Processor, ISA, and hardware security**: connect the arithmetic blocks
   into a small RISC-V processor with bounds and permission checks.
3. **Intelligent operating system and resource manager**: use OS mechanisms as
   a reliable baseline, then adapt CPU scheduling and resource management with
   telemetry and small ML models.
4. **Intelligent memory, storage, and I/O hierarchy**: create fast, medium, and
   slow tiers and move data between them using measured behavior.
5. **AI-native compiler**: use runtime observations to select and evaluate
   compiler optimizations.
6. **Database and learned data management**: benchmark learned indexes and a
   tier-aware query-plan chooser against conventional baselines.
7. **High-performance network stack**: measure QUIC, BBR, and eBPF/XDP behavior
   while separating network delay from scheduler-induced delay.
8. **Unified hardware and AI security**: combine hardware violation events,
   eBPF behavioral monitoring, and anomaly detection.
9. **Full-system integration and evaluation**: connect modules through a common
   telemetry/control plane and compare isolated baseline behavior with at least
   two cross-layer feedback loops enabled.
10. **AgentOS authorization, permissions, and scope**: treat agents like OS
    processes and mediate identity, least privilege, delegation, isolation,
    revocation, quotas, auditing, and human approval.

## Objective 1 Design

Objective 1 grows progressively from verified functional blocks to optimized
arithmetic, telemetry, and open-source FPGA-flow evaluation:

1. Reversible gates and supporting mux/register blocks
2. Ripple-carry baseline and parameterized carry-lookahead adder
3. Simple multiplier, then signed Booth and Wallace reduction
4. 32-bit ALU with RISC-V-compatible operations and status flags
5. Activity counters, switching proxies, and the frozen telemetry register map
6. Python golden models, Verilator/formal checks, and synthesis comparisons

The current Chisel API is intentionally reusable by later CPU code, for example
`Module(new CarryLookaheadAdder(32))` once the adder is implemented.

The `Objective1Subsystem` wrapper exposes one reusable ALU/telemetry boundary.
Its current combinational protocol is `busy=0`, `done=1`, and `valid` equals
the external `operationValid` input. Subtraction carry follows the common
two's-complement convention: `carry=1` means no borrow.

### Telemetry Register Map

The telemetry block uses a read-only memory-mapped interface beginning at
`0x80001000`:

| Address | Register | Meaning |
| --- | --- | --- |
| `0x80001000` | `REV_ENERGY_ACC` | Reversible-operation activity proxy |
| `0x80001004` | `CLA_SWITCHING` | Count of observed CLA result bit transitions |
| `0x80001008` | `MUL_THERMAL` | Multiplier switching/thermal activity proxy |
| `0x8000100C` | `EDP_CURRENT` | Estimated energy-delay product proxy |
| `0x80001010` | `EDP_CONFIG` | Delay/configuration scale constant |

The current implementation uses activity proxies suitable for simulation and
comparison. `MUL_THERMAL` is not a physical temperature measurement, and
`EDP_CURRENT`/`REV_ENERGY_ACC` are not calibrated power or energy units.

## Run The Tests

The Objective 1 build uses Java 17, Scala 2.12.17, sbt 1.9.7, Chisel 3.6.0,
and ChiselTest 0.6.0. Python 3.9 or newer remains available for golden models
and analysis.

The current checks are ChiselTest simulation, Python golden-model verification,
Chisel SystemVerilog generation, Verilator simulation/linting, and Yosys
structural/XC7 mapping. Generated SystemVerilog files are ignored because they
are build artifacts.

```bash
cd objective01-digital-logic
sbt test
# Generate SystemVerilog for benchmark blocks and the integrated subsystem
sbt "runMain GenerateRTL"

# Lint each generated top independently with Verilator
bash verification/verilator_lint.sh

# Structural Yosys statistics for each generated benchmark top
bash verification/yosys_stats.sh

# Flattened, no-I/O-pad Xilinx 7-series mapped resource statistics as CSV
bash verification/yosys_xc7_stats.sh

# Differentially compare Python ALU results with Verilator RTL execution
python verification/python_verilator_alu.py

# On a machine with nextpnr-Xilinx and a Project X-Ray XC7 chip database
bash verification/nextpnr_xilinx/run_nextpnr.sh /path/to/xc7a100t.chipdb

# Run the Python golden models
cd reference
python -m unittest test_models.py -v

# Build and test the persistent RTL-backed simulator
cd ..
bash simulator/build_simulator.sh
python -m unittest simulator.test_objective1_sim -v
```

### RTL-Backed Simulator

The simulator is a persistent JSON Lines process backed by the generated
`Objective1Subsystem.sv`, not a JavaScript or Python ALU reimplementation. One
`execute` request advances one clock cycle and returns the ALU result, flags,
protocol signals, the requested telemetry value, and all five telemetry
registers. A `reset` request clears the synchronous telemetry state.

Example:

```json
{"command":"execute","a":5,"b":3,"opcode":0,"operation_valid":true,"telemetry_address":2147487748}
```

The process returns one JSON object per input line. `busy` is currently `false`,
`done` is `true`, and `valid` follows `operation_valid`, matching the frozen
combinational Objective 1 protocol.

## Benchmark Plan

The generated benchmark tops support these comparisons:

- Ripple-carry adder versus carry-lookahead adder
- Simple multiplier versus Booth versus Booth-Wallace
- ALU without telemetry versus the integrated ALU/telemetry subsystem

Yosys provides flattened XC7 resource estimates, while nextpnr-Xilinx provides
the open-source placement, routing, and timing stage when a matching Project
X-Ray chip database is available.

The nextpnr flow targets the Artix-7 100T class with a 100 MHz constraint. A
matching Project X-Ray chip database is required. No physical Nexys A7 demo is
planned; board deployment is explicitly outside this project's scope.

The current flattened `synth_xilinx -family xc7 -noiopad` run produces these
mapped-resource baselines for the generated widths. The JSON-based script
counts the final design once and reports primitive LUT types separately:

| Design | LUTs | CARRY4 | FF | Mapped cells |
| --- | ---: | ---: | ---: | ---: |
| Ripple Carry Adder | 64 | 0 | 0 | 96 |
| Flat Carry Lookahead Adder | 318 | 0 | 0 | 368 |
| Hierarchical Carry Lookahead Adder | 98 | 0 | 0 | 116 |
| Simple Multiplier | 638 | 8 | 0 | 880 |
| Booth Multiplier | 566 | 16 | 0 | 725 |
| Booth-Wallace Multiplier | 715 | 16 | 0 | 859 |
| Registered Ripple Carry Adder | 64 | 0 | 98 | 196 |
| Registered Flat Carry Lookahead Adder | 318 | 0 | 98 | 481 |
| Registered Hierarchical Carry Lookahead Adder | 98 | 0 | 98 | 216 |
| Registered Simple Multiplier | 638 | 8 | 64 | 946 |
| Registered Booth Multiplier | 566 | 16 | 64 | 791 |
| Registered Booth-Wallace Multiplier | 715 | 16 | 64 | 925 |

These are flattened Yosys XC7-mapped resource counts with top-level I/O pads
excluded, not nextpnr timing, Fmax, power, or a claim that one architecture is
faster. The result shows the hierarchical CLA using substantially fewer mapped
LUTs than the flat CLA, while the Booth-Wallace path uses more mapped resources
than Booth alone. Registered wrappers add storage for fair timing experiments;
they are not intended as the final CPU interface.

## Roadmap

- **Objective 2 Phase 8 (Upcoming Security Trapping & Freeze)**:
  - Synchronous hardware security exception vectoring and trap handler redirection.
  - OS capability context save/restore abstraction (`CURRENT_CONTEXT` domain isolation).
  - Security attack & exploit mitigation evaluation benchmark suite.
  - Hardware/OS co-design interface freeze for Objective 3 (Adaptive OS Scheduler) and Objective 8 (Unified Security).
- **Downstream Cross-Layer Integration (Objectives 3–10)**:
  - OS kernel adaptation driven by hardware telemetry and MMIO counters.
  - Hierarchical tiered memory management and compiler feedback loops.

## Visualization

The `objective01-digital-logic/visualization/` directory is documentation-only;
it does not alter the frozen RTL or existing tests. It contains schematics
generated from Chisel-produced SystemVerilog, Verilator VCD waveform benches,
an overview architecture diagram, and charts generated from the corrected Yosys
XC7 CSV.

```bash
cd objective01-digital-logic
sbt "runMain GenerateRTL"
bash visualization/scripts/generate_schematics.sh
bash visualization/scripts/generate_architecture.sh
bash verification/yosys_xc7_stats.sh
python visualization/scripts/plot_results.py
bash visualization/scripts/generate_waveforms.sh
```

The VCD files can be opened with GTKWave, Surfer, or another VCD viewer. DOT,
SVG, PNG, and VCD artifacts are kept separate from the source design.

## Source Documents

- `Computer_System_Project_10_Objectives_Undergraduate_Guide.docx` contains the
  ten objectives, minimum viable versions, suggested tools, and evaluation
  guidance.
- `project_idea.pdf` contains the broader project proposal and research basis.

## Interactive Workbench

The RTL-backed workbench runs as two local processes. Start the backend first,
then the Vite frontend in a second terminal:

```bash
cd objective01-digital-logic
pip install -r simulator/backend/requirements.txt
bash simulator/build_simulator.sh
python -m uvicorn simulator.backend.app:app --host 127.0.0.1 --port 8000
```

```bash
npm --prefix objective01-digital-logic/simulator/frontend install
npm --prefix objective01-digital-logic/simulator/frontend run dev -- --host 127.0.0.1
```

Open `http://127.0.0.1:5173/`. The browser sends operands and opcodes to
FastAPI; FastAPI forwards them to the persistent `objective1_sim` process.
Results are never recomputed in the browser.

