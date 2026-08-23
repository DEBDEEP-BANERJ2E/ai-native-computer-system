#!/usr/bin/env python3
"""
Genuine Cross-Model 3-Way Differential Verification Runner.
Regenerates and compares cycle-by-cycle architectural commit/retirement traces produced by:
1. Python reference emulator (RV32Interpreter)
2. Chisel SingleCycleCore (frozen golden hardware reference)
3. Chisel PipelinedCore (5-stage pipelined processor core)
"""

import argparse
import json
import os
import subprocess
import sys
from rv32i_interpreter import RV32Interpreter, CommitEvent

# =========================================================================
# Part 1: Five Full Architectural Programs (Single-Cycle Core <-> Python)
# =========================================================================
SINGLE_CYCLE_PROGRAMS = {
    "prog1": {
        "name": "Program 1: Arithmetic & Logic Matrix",
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
        "trace_file": "test_traces/chisel_trace_prog1.json"
    },
    "prog2": {
        "name": "Program 2: Loop Accumulation (5+4+3+2+1=15)",
        "code": [
            0x00500093, # 0x00: addi x1, x0, 5
            0x00000113, # 0x04: addi x2, x0, 0
            0x00110133, # 0x08: add  x2, x2, x1
            0xfff08093, # 0x0C: addi x1, x1, -1
            0xfe009ce3, # 0x10: bne  x1, x0, -8
            0x00000013  # 0x14: nop / exit
        ],
        "cycles": 18,
        "trace_file": "test_traces/chisel_trace_prog2.json"
    },
    "prog3": {
        "name": "Program 3: Memory Operations (Little-Endian SB/SH/SW & LB/LH/LW)",
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
        "trace_file": "test_traces/chisel_trace_prog3.json"
    },
    "prog4": {
        "name": "Program 4: Function Link & Return (JAL / JALR)",
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
        "trace_file": "test_traces/chisel_trace_prog4.json"
    },
    "prog5": {
        "name": "Program 5: Objective 1 Hardware Multiplier Tree",
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
        "trace_file": "test_traces/chisel_trace_prog5.json"
    }
}

# =========================================================================
# Part 2: Three Canonical Hazard-Free Programs for 3-Way Differential Flow
# (Python Reference <-> SingleCycleCore <-> PipelinedCore)
# =========================================================================
PIPELINE_3WAY_PROGRAMS = {
    "pipe_prog1": {
        "name": "Pipeline 3-Way Benchmark 1: Arithmetic & Hardware MUL",
        "code": [
            0x00a00093, # 0x00: addi x1, x0, 10
            0x01400113, # 0x04: addi x2, x0, 20
            0x00000013, # 0x08: nop
            0x00000013, # 0x0C: nop
            0x00000013, # 0x10: nop
            0x002081b3, # 0x14: add  x3, x1, x2
            0x00000013, # 0x18: nop
            0x00000013, # 0x1C: nop
            0x00000013, # 0x20: nop
            0x40118233, # 0x24: sub  x4, x3, x1
            0x00000013, # 0x28: nop
            0x00000013, # 0x2C: nop
            0x00000013, # 0x30: nop
            0x021202b3  # 0x34: mul  x5, x4, x1  (20 * 10 = 200 = 0xC8)
        ],
        "cycles": 14,
        "sc_trace": "test_traces/single_cycle_pipe_prog1.json",
        "pipe_trace": "test_traces/pipelined_core_prog1.json"
    },
    "pipe_prog2": {
        "name": "Pipeline 3-Way Benchmark 2: Memory Operations (SW, LW, SB, LB)",
        "code": [
            0x02a00093, # 0x00: addi x1, x0, 42
            0xffb00113, # 0x04: addi x2, x0, -5
            0x00000013, # 0x08: nop
            0x00000013, # 0x0C: nop
            0x00000013, # 0x10: nop
            0x00102023, # 0x14: sw   x1, 0(x0)
            0x00200223, # 0x18: sb   x2, 4(x0)
            0x00000013, # 0x1C: nop
            0x00000013, # 0x20: nop
            0x00000013, # 0x24: nop
            0x00002183, # 0x28: lw   x3, 0(x0)
            0x00400203  # 0x2C: lb   x4, 4(x0)
        ],
        "cycles": 12,
        "sc_trace": "test_traces/single_cycle_pipe_prog2.json",
        "pipe_trace": "test_traces/pipelined_core_prog2.json"
    },
    "pipe_prog3": {
        "name": "Pipeline 3-Way Benchmark 3: Control Flow (BEQ taken/flush, JALR LSB=0 & flush)",
        "code": [
            0x00a00093, # 0x00: addi x1, x0, 10
            0x00a00113, # 0x04: addi x2, x0, 10
            0x00000013, # 0x08: nop
            0x00000013, # 0x0C: nop
            0x00000013, # 0x10: nop
            0x00208863, # 0x14: beq  x1, x2, 16    (taken -> jumps to 0x24)
            0x3e700713, # 0x18: addi x14, x0, 999 (killed wrong path)
            0x37800713, # 0x1C: addi x14, x0, 888 (killed wrong path)
            0x00000013, # 0x20: nop
            0x04900293, # 0x24: addi x5, x0, 0x49 (target 0x49, bit 0 cleared to 0x48)
            0x00000013, # 0x28: nop
            0x00000013, # 0x2C: nop
            0x00000013, # 0x30: nop
            0x00028367, # 0x34: jalr x6, 0(x5)     (link x6 = 0x38, jumps to 0x48)
            0x3e700713, # 0x38: addi x14, x0, 777 (killed wrong path)
            0x37800713, # 0x3C: addi x14, x0, 666 (killed wrong path)
            0x00000013, # 0x40: nop
            0x00000013, # 0x44: nop
            0x06400393  # 0x48: addi x7, x0, 100
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

    if a_memRead or a_memWrite:
        a_addr = ev_a.memAddress if isinstance(ev_a, CommitEvent) else ev_a["memAddress"]
        b_addr = ev_b.memAddress if isinstance(ev_b, CommitEvent) else ev_b["memAddress"]
        assert a_addr == b_addr, f"Event {idx} MemAddress mismatch at PC {hex(a_pc)}: {model_a_name}={hex(a_addr)}, {model_b_name}={hex(b_addr)}"

    if a_memWrite:
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
    print("SECTION 1: DIFFERENTIAL VERIFICATION (SINGLE-CYCLE CORE <-> PYTHON REFERENCE)")
    print("=" * 80)

    total_sc_events = 0
    passed_sc_programs = 0

    for prog_key, prog_info in SINGLE_CYCLE_PROGRAMS.items():
        print(f"\nVerifying {prog_info['name']}...")
        trace_path = prog_info["trace_file"]
        with open(trace_path, "r") as f:
            chisel_events = json.load(f)

        interp = RV32Interpreter()
        interp.load_program(prog_info["code"])
        py_trace = interp.run(prog_info["cycles"])

        assert len(py_trace) == len(chisel_events), (
            f"Trace length mismatch: Python had {len(py_trace)} events, Chisel had {len(chisel_events)}"
        )

        for i, (py_ev, ch_ev) in enumerate(zip(py_trace, chisel_events)):
            total_sc_events += 1
            compare_event("Python", py_ev, "SingleCycleCore", ch_ev, i)

        print(f"  [PASS] All {len(py_trace)} commit events matched 1:1 with bit-exact parity!")
        passed_sc_programs += 1

    print("\n" + "=" * 80)
    print("SECTION 2: GENUINE 3-WAY DIFFERENTIAL VERIFICATION")
    print("           (PYTHON REFERENCE <==> SINGLE-CYCLE CORE <==> PIPELINED CORE)")
    print("=" * 80)

    total_3way_events = 0
    passed_3way_programs = 0

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
            total_3way_events += 1
            py_ev = py_trace[i]
            sc_ev = sc_events[i]
            pipe_ev = pipe_events[i]

            # 1. Compare Python <-> SingleCycleCore
            compare_event("Python", py_ev, "SingleCycleCore", sc_ev, i)
            # 2. Compare SingleCycleCore <-> PipelinedCore
            compare_event("SingleCycleCore", sc_ev, "PipelinedCore", pipe_ev, i)
            # 3. Compare Python <-> PipelinedCore
            compare_event("Python", py_ev, "PipelinedCore", pipe_ev, i)

        print(f"  [PASS] All {len(py_trace)} retirement events matched 1:1:1 across all three models!")
        passed_3way_programs += 1

    print("\n" + "=" * 80)
    print("DIFFERENTIAL VERIFICATION SUMMARY:")
    print(f"  1. Single-Cycle Core <-> Python: {passed_sc_programs}/{len(SINGLE_CYCLE_PROGRAMS)} Programs ({total_sc_events} events bit-exact)")
    print(f"  2. Genuine 3-Way Pipeline:      {passed_3way_programs}/{len(PIPELINE_3WAY_PROGRAMS)} Programs ({total_3way_events} events bit-exact across Python, SingleCycleCore, and PipelinedCore)")
    print("=" * 80)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="RV32I 3-Way Differential Verification Runner")
    parser.add_argument("--use-existing-traces", action="store_true", help="Skip running sbt test and use existing JSON trace files")
    args = parser.parse_args()
    run_differential_comparison(use_existing_traces=args.use_existing_traces)
