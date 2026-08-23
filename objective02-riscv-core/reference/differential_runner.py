#!/usr/bin/env python3
"""
Genuine Cross-Model 3-Way Differential Verification Runner.
Regenerates and compares retirement-order architectural commit/retirement traces produced by:
1. Python reference emulator (RV32Interpreter)
2. Chisel SingleCycleCore (frozen golden hardware reference)
3. Chisel PipelinedCore (5-stage pipelined processor core with Data Forwarding and Load-Use Hazard Stalls)
"""

import argparse
import json
import os
import subprocess
import sys
from rv32i_interpreter import RV32Interpreter, CommitEvent

# =========================================================================
# Part 1: Five Full Original Architectural Benchmark Programs (Zero NOP Spacing)
# Verified 3-Way: Python Reference <==> SingleCycleCore <==> PipelinedCore
# =========================================================================
ORIGINAL_BENCHMARKS = {
    "prog1": {
        "name": "Program 1: Arithmetic & Logic Matrix (Back-to-Back RAW Hazards)",
        "code": [
            0x00a00093, # 0x00: addi x1, x0, 10
            0x01400113, # 0x04: addi x2, x0, 20
            0x002081b3, # 0x08: add  x3, x1, x2
            0x40118233, # 0x0C: sub  x4, x3, x1
            0x003222b3, # 0x10: slt  x5, x4, x3
            0x0020c333, # 0x14: xor  x6, x1, x2
            0x0020e3b3, # 0x18: or   x7, x1, x2
            0x0020f433  # 0x1C: and  x8, x1, x2
        ],
        "cycles": 8,
        "sc_trace": "test_traces/chisel_trace_prog1.json",
        "pipe_trace": "test_traces/pipeline_original_prog1.json"
    },
    "prog2": {
        "name": "Program 2: Loop Accumulation (Branch Hazards & RAW Accumulation)",
        "code": [
            0x00500093, # 0x00: addi x1, x0, 5
            0x00000113, # 0x04: addi x2, x0, 0
            0x00110133, # 0x08: add  x2, x2, x1
            0xfff08093, # 0x0C: addi x1, x1, -1
            0xfe009ce3, # 0x10: bne  x1, x0, -8
            0x00000013  # 0x14: nop / exit
        ],
        "cycles": 18,
        "sc_trace": "test_traces/chisel_trace_prog2.json",
        "pipe_trace": "test_traces/pipeline_original_prog2.json"
    },
    "prog3": {
        "name": "Program 3: Memory Operations (Little-Endian SB/SH/SW & Load-Use RAW Hazards)",
        "code": [
            0x02a00093, # 0x00: addi x1, x0, 42
            0x00102023, # 0x04: sw   x1, 0(x0)
            0x00002103, # 0x08: lw   x2, 0(x0)
            0xffb00193, # 0x0C: addi x3, x0, -5
            0x00300223, # 0x10: sb   x3, 4(x0)
            0x00400203, # 0x14: lb   x4, 4(x0)
            0x00404283, # 0x18: lbu  x5, 4(x0)
            0xc1800313, # 0x1C: addi x6, x0, -1000
            0x00601323, # 0x20: sh   x6, 6(x0)
            0x00601383, # 0x24: lh   x7, 6(x0)
            0x00605403  # 0x28: lhu  x8, 6(x0)
        ],
        "cycles": 11,
        "sc_trace": "test_traces/chisel_trace_prog3.json",
        "pipe_trace": "test_traces/pipeline_original_prog3.json"
    },
    "prog4": {
        "name": "Program 4: Function Link & Return (JAL / JALR Control Hazards)",
        "code": [
            0x03200513, # 0x00: addi x10, x0, 50
            0x010000ef, # 0x04: jal  x1, 16
            0x00a50613, # 0x08: addi x12, x10, 10
            0x0100006f, # 0x0C: jal  x0, 16
            0x3e700713, # 0x10: addi x14, x0, 999
            0x01950513, # 0x14: addi x10, x10, 25
            0x00008067, # 0x18: jalr x0, 0(x1)
            0x00100693  # 0x1C: addi x13, x0, 1
        ],
        "cycles": 7,
        "sc_trace": "test_traces/chisel_trace_prog4.json",
        "pipe_trace": "test_traces/pipeline_original_prog4.json"
    },
    "prog5": {
        "name": "Program 5: Objective 1 Hardware Multiplier Tree (RAW Multiplier Hazards)",
        "code": [
            0x00700093, # 0x00: addi x1, x0, 7
            0xffb00113, # 0x04: addi x2, x0, -5
            0x022081b3, # 0x08: mul  x3, x1, x2
            0x00003237, # 0x0C: lui  x4, 3
            0x03920213, # 0x10: addi x4, x4, 57
            0x7d000293, # 0x14: addi x5, x0, 2000
            0x02520333  # 0x18: mul  x6, x4, x5
        ],
        "cycles": 7,
        "sc_trace": "test_traces/chisel_trace_prog5.json",
        "pipe_trace": "test_traces/pipeline_original_prog5.json"
    }
}

# =========================================================================
# Part 1.5: RV32M Benchmarks (2-Way Differential: Python <==> PipelinedCore)
# =========================================================================
RV32M_BENCHMARKS = {
    "prog_div": {
        "name": "Phase 5C RV32M Full Multi-cycle Integration",
        "code": [
            0xffe00093, # 0x00: addi x1, x0, -2
            0x00300113, # 0x04: addi x2, x0, 3
            0x022091b3, # 0x08: mulh x3, x1, x2
            0xffe00093, # 0x0C: addi x1, x0, -2
            0xfff00113, # 0x10: addi x2, x0, -1
            0x0220a233, # 0x14: mulhsu x4, x1, x2
            0xfff00093, # 0x18: addi x1, x0, -1
            0xfff00113, # 0x1C: addi x2, x0, -1
            0x0220b2b3, # 0x20: mulhu x5, x1, x2
            0x00a00093, # 0x24: addi x1, x0, 10
            0x00300113, # 0x28: addi x2, x0, 3
            0x0220c333, # 0x2C: div x6, x1, x2
            0x002303b3, # 0x30: add x7, x6, x2
            0xfff00093, # 0x34: addi x1, x0, -1
            0x00300113, # 0x38: addi x2, x0, 3
            0x0220d433, # 0x3C: divu x8, x1, x2
            0x00a00093, # 0x40: addi x1, x0, 10
            0x00300113, # 0x44: addi x2, x0, 3
            0x0220e4b3, # 0x48: rem x9, x1, x2
            0xfff00093, # 0x4C: addi x1, x0, -1
            0x00300113, # 0x50: addi x2, x0, 3
            0x0220f533, # 0x54: remu x10, x1, x2
            0x022085b3, # 0x58: mul x11, x1, x2
            0x00500093, # 0x5C: addi x1, x0, 5
            0x0200c633, # 0x60: div x12, x1, x0
            0x0200e6b3, # 0x64: rem x13, x1, x0
            0x0200d733, # 0x68: divu x14, x1, x0
            0x0200f7b3, # 0x6C: remu x15, x1, x0
            0x800000b7, # 0x70: lui x1, 0x80000
            0xfff00113, # 0x74: addi x2, x0, -1
            0x0220c833, # 0x78: div x16, x1, x2
            0x0220e8b3  # 0x7C: rem x17, x1, x2
        ],
        "cycles": 32, # retirement events count = 32
        "pipe_trace": "test_traces/progDiv.json"
    }
}

# =========================================================================
# Part 2: Three Canonical Hazard-Free Programs (Preserved from Phase 3.1)
# =========================================================================
PIPELINE_3WAY_PROGRAMS = {
    "pipe_prog1": {
        "name": "Pipeline Benchmark 1: Arithmetic & Hardware MUL",
        "code": [
            0x00a00093, 0x01400113, 0x00000013, 0x00000013, 0x00000013,
            0x002081b3, 0x00000013, 0x00000013, 0x00000013, 0x40118233,
            0x00000013, 0x00000013, 0x00000013, 0x021202b3
        ],
        "cycles": 14,
        "sc_trace": "test_traces/single_cycle_pipe_prog1.json",
        "pipe_trace": "test_traces/pipelined_core_prog1.json"
    },
    "pipe_prog2": {
        "name": "Pipeline Benchmark 2: Memory Operations (SW, LW, SB, LB)",
        "code": [
            0x02a00093, 0xffb00113, 0x00000013, 0x00000013, 0x00000013,
            0x00102023, 0x00200223, 0x00000013, 0x00000013, 0x00000013,
            0x00002183, 0x00400203
        ],
        "cycles": 12,
        "sc_trace": "test_traces/single_cycle_pipe_prog2.json",
        "pipe_trace": "test_traces/pipelined_core_prog2.json"
    },
    "pipe_prog3": {
        "name": "Pipeline Benchmark 3: Control Flow (BEQ taken/flush, JALR LSB=0 & flush)",
        "code": [
            0x00a00093, 0x00a00113, 0x00000013, 0x00000013, 0x00000013,
            0x00208863, 0x3e700713, 0x37800713, 0x00000013, 0x04900293,
            0x00000013, 0x00000013, 0x00000013, 0x00028367, 0x3e700713,
            0x37800713, 0x00000013, 0x00000013, 0x06400393
        ],
        "cycles": 12,
        "sc_trace": "test_traces/single_cycle_pipe_prog3.json",
        "pipe_trace": "test_traces/pipelined_core_prog3.json"
    }
}

def generate_hardware_traces():
    print("[Differential Runner] Executing sbt test to freshly regenerate all Chisel hardware commit traces...")
    cmd = ["sbt", "--batch", "testOnly objective02.SingleCycleCoreSpec objective02.PipelinedCoreSpec"]
    res = subprocess.run(cmd, check=True)
    if res.returncode != 0:
        print("[Differential Runner] Error: sbt test execution failed.")
        sys.exit(1)

def compare_event(model_a_name: str, ev_a, model_b_name: str, ev_b, idx: int):
    """Bit-exact field comparison between two commit/retirement events."""
    a_pc = ev_a.pc if isinstance(ev_a, CommitEvent) else ev_a["pc"]
    b_pc = ev_b.pc if isinstance(ev_b, CommitEvent) else ev_b["pc"]
    assert a_pc == b_pc, f"Event {idx} PC mismatch: {model_a_name}={hex(a_pc)}, {model_b_name}={hex(b_pc)}"

    a_inst = ev_a.instruction if isinstance(ev_a, CommitEvent) else ev_a["instruction"]
    b_inst = ev_b.instruction if isinstance(ev_b, CommitEvent) else ev_b["instruction"]
    assert a_inst == b_inst, f"Event {idx} Inst mismatch at PC {hex(a_pc)}: {model_a_name}={hex(a_inst)}, {model_b_name}={hex(b_inst)}"

    a_rd = ev_a.rd if isinstance(ev_a, CommitEvent) else ev_a["rd"]
    b_rd = ev_b.rd if isinstance(ev_b, CommitEvent) else ev_b["rd"]
    assert a_rd == b_rd, f"Event {idx} Rd mismatch at PC {hex(a_pc)}: {model_a_name}={a_rd}, {model_b_name}={b_rd}"

    a_regWrite = ev_a.regWrite if isinstance(ev_a, CommitEvent) else ev_a["regWrite"]
    b_regWrite = ev_b.regWrite if isinstance(ev_b, CommitEvent) else ev_b["regWrite"]
    assert a_regWrite == b_regWrite, f"Event {idx} RegWrite mismatch at PC {hex(a_pc)}"

    if a_regWrite:
        a_data = ev_a.writeData if isinstance(ev_a, CommitEvent) else ev_a["writeData"]
        b_data = ev_b.writeData if isinstance(ev_b, CommitEvent) else ev_b["writeData"]
        assert a_data == b_data, f"Event {idx} WriteData mismatch at PC {hex(a_pc)}: {model_a_name}={hex(a_data)}, {model_b_name}={hex(b_data)}"

    a_memRead = ev_a.memRead if isinstance(ev_a, CommitEvent) else ev_a["memRead"]
    b_memRead = ev_b.memRead if isinstance(ev_b, CommitEvent) else ev_b["memRead"]
    assert a_memRead == b_memRead, f"Event {idx} MemRead mismatch at PC {hex(a_pc)}"

    a_memWrite = ev_a.memWrite if isinstance(ev_a, CommitEvent) else ev_a["memWrite"]
    b_memWrite = ev_b.memWrite if isinstance(ev_b, CommitEvent) else ev_b["memWrite"]
    assert a_memWrite == b_memWrite, f"Event {idx} MemWrite mismatch at PC {hex(a_pc)}"

    a_memReadReq = ev_a.memReadReq if isinstance(ev_a, CommitEvent) else ev_a["memReadReq"]
    b_memReadReq = ev_b.memReadReq if isinstance(ev_b, CommitEvent) else ev_b["memReadReq"]
    assert a_memReadReq == b_memReadReq, f"Event {idx} MemReadReq mismatch at PC {hex(a_pc)}"

    a_memWriteReq = ev_a.memWriteReq if isinstance(ev_a, CommitEvent) else ev_a["memWriteReq"]
    b_memWriteReq = ev_b.memWriteReq if isinstance(ev_b, CommitEvent) else ev_b["memWriteReq"]
    assert a_memWriteReq == b_memWriteReq, f"Event {idx} MemWriteReq mismatch at PC {hex(a_pc)}"

    if a_memReadReq or a_memWriteReq:
        a_addr = ev_a.memAddress if isinstance(ev_a, CommitEvent) else ev_a["memAddress"]
        b_addr = ev_b.memAddress if isinstance(ev_b, CommitEvent) else ev_b["memAddress"]
        assert a_addr == b_addr, f"Event {idx} MemAddress mismatch at PC {hex(a_pc)}: {model_a_name}={hex(a_addr)}, {model_b_name}={hex(b_addr)}"

    if a_memWriteReq:
        a_wdata = ev_a.memWriteData if isinstance(ev_a, CommitEvent) else ev_a["memWriteData"]
        b_wdata = ev_b.memWriteData if isinstance(ev_b, CommitEvent) else ev_b["memWriteData"]
        assert (a_wdata & 0xFFFFFFFF) == (b_wdata & 0xFFFFFFFF), f"Event {idx} MemWriteData mismatch at PC {hex(a_pc)}"

    a_illegal = ev_a.illegal if isinstance(ev_a, CommitEvent) else ev_a["illegal"]
    b_illegal = ev_b.illegal if isinstance(ev_b, CommitEvent) else ev_b["illegal"]
    assert a_illegal == b_illegal, f"Event {idx} Illegal status mismatch at PC {hex(a_pc)}"

def run_differential_comparison(use_existing_traces: bool = False):
    if not use_existing_traces:
        generate_hardware_traces()

    print("\n" + "=" * 80)
    print("SECTION 1: 3-WAY DIFFERENTIAL VERIFICATION ON ORIGINAL BENCHMARKS (NO NOPS)")
    print("           (PYTHON REFERENCE <==> SINGLE-CYCLE CORE <==> PIPELINED CORE)")
    print("=" * 80)

    total_orig_events = 0
    passed_orig_programs = 0

    for prog_key, prog_info in ORIGINAL_BENCHMARKS.items():
        print(f"\nVerifying 3-Way Parity on {prog_info['name']}...")
        with open(prog_info["sc_trace"], "r") as f:
            sc_events = json.load(f)
        with open(prog_info["pipe_trace"], "r") as f:
            pipe_events = json.load(f)

        interp = RV32Interpreter()
        interp.load_program(prog_info["code"])
        py_trace = interp.run(prog_info["cycles"])

        assert len(py_trace) == len(sc_events), (
            f"Python ({len(py_trace)}) vs SingleCycle ({len(sc_events)}) event count mismatch"
        )
        assert len(sc_events) == len(pipe_events), (
            f"SingleCycle ({len(sc_events)}) vs PipelinedCore ({len(pipe_events)}) retirement count mismatch"
        )

        for i in range(len(py_trace)):
            total_orig_events += 1
            py_ev = py_trace[i]
            sc_ev = sc_events[i]
            pipe_ev = pipe_events[i]

            # 1. Compare Python <-> SingleCycleCore
            compare_event("Python", py_ev, "SingleCycleCore", sc_ev, i)
            # 2. Compare SingleCycleCore <-> PipelinedCore
            compare_event("SingleCycleCore", sc_ev, "PipelinedCore", pipe_ev, i)
            # 3. Compare Python <-> PipelinedCore
            compare_event("Python", py_ev, "PipelinedCore", pipe_ev, i)

        print(f"  [PASS] All {len(py_trace)} retirement events matched 1:1:1 across Python, SingleCycleCore, and PipelinedCore!")
        passed_orig_programs += 1

    print("\n" + "=" * 80)
    print("SECTION 2: 2-WAY DIFFERENTIAL VERIFICATION ON RV32M MULTI-CYCLE")
    print("           (PYTHON REFERENCE <==> PIPELINED CORE)")
    print("=" * 80)

    total_rv32m_events = 0
    passed_rv32m_programs = 0

    for prog_key, prog_info in RV32M_BENCHMARKS.items():
        print(f"\nVerifying 2-Way Parity on {prog_info['name']}...")
        with open(prog_info["pipe_trace"], "r") as f:
            pipe_events = json.load(f)

        interp = RV32Interpreter()
        interp.load_program(prog_info["code"])
        py_trace = interp.run(prog_info["cycles"])

        assert len(py_trace) == len(pipe_events), (
            f"Python ({len(py_trace)}) vs PipelinedCore ({len(pipe_events)}) retirement count mismatch"
        )

        for i in range(len(py_trace)):
            total_rv32m_events += 1
            py_ev = py_trace[i]
            pipe_ev = pipe_events[i]
            compare_event("Python", py_ev, "PipelinedCore", pipe_ev, i)

        print(f"  [PASS] All {len(py_trace)} retirement events matched 1:1 across Python and PipelinedCore!")
        passed_rv32m_programs += 1

    print("\n" + "=" * 80)
    print("SECTION 3: 3-WAY DIFFERENTIAL VERIFICATION ON CANONICAL BENCHMARKS")
    print("=" * 80)

    total_canon_events = 0
    passed_canon_programs = 0

    for prog_key, prog_info in PIPELINE_3WAY_PROGRAMS.items():
        print(f"\nVerifying 3-Way Parity on {prog_info['name']}...")
        with open(prog_info["sc_trace"], "r") as f:
            sc_events = json.load(f)
        with open(prog_info["pipe_trace"], "r") as f:
            pipe_events = json.load(f)

        interp = RV32Interpreter()
        interp.load_program(prog_info["code"])
        py_trace = interp.run(prog_info["cycles"])

        assert len(py_trace) == len(sc_events), (
            f"Python ({len(py_trace)}) vs SingleCycle ({len(sc_events)}) event count mismatch"
        )
        assert len(sc_events) == len(pipe_events), (
            f"SingleCycle ({len(sc_events)}) vs PipelinedCore ({len(pipe_events)}) retirement count mismatch"
        )

        for i in range(len(py_trace)):
            total_canon_events += 1
            py_ev = py_trace[i]
            sc_ev = sc_events[i]
            pipe_ev = pipe_events[i]

            compare_event("Python", py_ev, "SingleCycleCore", sc_ev, i)
            compare_event("SingleCycleCore", sc_ev, "PipelinedCore", pipe_ev, i)
            compare_event("Python", py_ev, "PipelinedCore", pipe_ev, i)

        print(f"  [PASS] All {len(py_trace)} retirement events matched 1:1:1 across all three models!")
        passed_canon_programs += 1

    print("\n" + "=" * 80)
    print("DIFFERENTIAL VERIFICATION SUMMARY:")
    print(f"  1. Original 5 Benchmarks (3-Way Bit-Exact Parity): {passed_orig_programs}/{len(ORIGINAL_BENCHMARKS)} Programs ({total_orig_events} events bit-exact across Python, SingleCycleCore, and PipelinedCore)")
    print(f"  2. RV32M Benchmarks      (2-Way Bit-Exact Parity): {passed_rv32m_programs}/{len(RV32M_BENCHMARKS)} Programs ({total_rv32m_events} events bit-exact across Python and PipelinedCore)")
    print(f"  3. Canonical Benchmarks  (3-Way Bit-Exact Parity): {passed_canon_programs}/{len(PIPELINE_3WAY_PROGRAMS)} Programs ({total_canon_events} events bit-exact across Python, SingleCycleCore, and PipelinedCore)")
    print("=" * 80)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="RV32I 3-Way Differential Verification Runner")
    parser.add_argument("--use-existing-traces", action="store_true", help="Skip running sbt test and use existing JSON trace files")
    args = parser.parse_args()
    run_differential_comparison(use_existing_traces=args.use_existing_traces)
