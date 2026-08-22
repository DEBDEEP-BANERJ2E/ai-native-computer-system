# AI-Native Computer System
Final-year project exploring an end-to-end computer system whose hardware,
operating system, compiler, data, network, and AI-agent layers can observe and
adapt to one another.

## Current Status

The active implementation slice is **Objective 1: Digital Logic and Arithmetic
Foundation**. It is a Chisel 3.6.0 hardware library, with the first reusable
building blocks implemented under `objective01-digital-logic/`:

- Fredkin reversible gate
- Toffoli reversible gate
- Parameterized 2:1 and 4:1 muxes
- Parameterized register and logical/arithmetic shifter
- Parameterized ripple-carry adder with carry-in and carry-out
- Parameterized carry-lookahead adder, including a 32-bit configuration
- Flat and hierarchical (4-bit block) carry-lookahead adders
- Parameterized unsigned shift-and-add multiplier baseline
- Signed Radix-4 Booth multiplier
- Wallace-style carry-save reduction tree
- Connected Booth-Wallace multiplier using Booth rows and Wallace reduction
- Parameterized ALU with RISC-V-oriented operations and status flags
- Integrated `Objective1Subsystem` ALU/telemetry wrapper
- Memory-mapped telemetry block with switching and EDP proxy counters
- Python golden models for arithmetic, ALU, and telemetry behavior
- Chisel SystemVerilog generation for individual benchmark targets and the integrated subsystem
- Artix-7-oriented Yosys mapping statistics workflow
- Registered timing wrappers for fair adder and multiplier comparisons
- Exhaustive truth-table tests for both reversible gates
- ChiselTest coverage for both muxes
- Exhaustive 8-bit addition coverage for the ripple-carry baseline
- Exhaustive 8-bit and randomized 32-bit carry-lookahead coverage
- Exhaustive 8-bit and randomized 16-bit multiplier coverage
- Exhaustive signed 8-bit Booth multiplication coverage
- Carry-save compressor and multi-row Wallace reduction coverage
- Exhaustive signed 8-bit Booth-Wallace multiplier coverage
- Reusable register/shifter and ALU/telemetry integration coverage
- Randomized 32-bit ALU operation and overflow coverage
- Telemetry counter, switching-activity, and EDP register coverage
- Python exhaustive/randomized golden-model coverage

The Python AgentOS policy kernel in `objective10-agentos/agentos/` is an earlier exploratory slice
for Objective 10. It remains separate so that the hardware library can become
the reusable foundation for Objective 2's processor.

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
```

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

- Increase differential vector coverage and extend the comparison to telemetry.
- Run nextpnr-Xilinx placement, routing, and timing using a matching Project
   X-Ray database if that open-source database environment is available.
- Treat physical Nexys A7 deployment as out of scope.
- Reuse the ALU in Objective 2's RISC-V processor and control path.
- Connect hardware telemetry to the shared cross-layer control plane.
- Extend AgentOS with tool mediation, signed identities, quotas, and sandboxing.
- Implement and evaluate the remaining OS, compiler, memory, database, network,
  and security objectives incrementally.

## Source Documents

- `Computer_System_Project_10_Objectives_Undergraduate_Guide.docx` contains the
  ten objectives, minimum viable versions, suggested tools, and evaluation
  guidance.
- `project_idea.pdf` contains the broader project proposal and research basis.
