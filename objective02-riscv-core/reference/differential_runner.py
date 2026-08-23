#!/usr/bin/env python3
"""
Genuine Cross-Model Differential Verification Runner.
Regenerates and compares cycle-by-cycle architectural commit traces produced by
the Chisel RTL simulation (SingleCycleCore) against the Python reference emulator (RV32Interpreter).
"""

import argparse
import json
import os
import subprocess
import sys
from rv32i_interpreter import RV32Interpreter, CommitEvent

PROGRAMS = {
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

def generate_chisel_traces():
    print("[Differential Runner] Executing sbt test to generate fresh Chisel hardware commit traces...")
    cmd = ["sbt", "--batch", "testOnly objective02.SingleCycleCoreSpec"]
    res = subprocess.run(cmd, check=True)
    if res.returncode != 0:
        print("[Differential Runner] Error: sbt test execution failed.")
        sys.exit(1)

def run_differential_comparison(use_existing_traces: bool = False):
    if not use_existing_traces:
        generate_chisel_traces()
    else:
        missing = [p["trace_file"] for p in PROGRAMS.values() if not os.path.exists(p["trace_file"])]
        if missing:
            print(f"[Differential Runner] Trace files missing: {missing}. Generating them...")
            generate_chisel_traces()

    print("\n" + "=" * 80)
    print("GENUINE DIFFERENTIAL VERIFICATION: CHISEL RTL <-> PYTHON REFERENCE EMULATOR")
    print("=" * 80)

    total_events = 0
    passed_programs = 0

    for prog_key, prog_info in PROGRAMS.items():
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
            total_events += 1
            # Field-by-field architectural comparison
            assert py_ev.pc == ch_ev["pc"], f"Cycle {i} PC mismatch: Py={py_ev.pc}, Ch={ch_ev['pc']}"
            assert py_ev.instruction == ch_ev["instruction"], f"Cycle {i} Inst mismatch: Py={hex(py_ev.instruction)}, Ch={hex(ch_ev['instruction'])}"
            assert py_ev.rd == ch_ev["rd"], f"Cycle {i} Rd mismatch: Py={py_ev.rd}, Ch={ch_ev['rd']}"
            assert py_ev.regWrite == ch_ev["regWrite"], f"Cycle {i} RegWrite mismatch: Py={py_ev.regWrite}, Ch={ch_ev['regWrite']}"
            if py_ev.regWrite:
                assert py_ev.writeData == ch_ev["writeData"], (
                    f"Cycle {i} WriteData mismatch at PC 0x{py_ev.pc:02x}: Python={hex(py_ev.writeData)}, Chisel={hex(ch_ev['writeData'])}"
                )
            assert py_ev.memRead == ch_ev["memRead"], f"Cycle {i} MemRead mismatch"
            assert py_ev.memReadReq == ch_ev["memReadReq"], f"Cycle {i} MemReadReq mismatch"
            assert py_ev.memWrite == ch_ev["memWrite"], f"Cycle {i} MemWrite mismatch"
            assert py_ev.memWriteReq == ch_ev["memWriteReq"], f"Cycle {i} MemWriteReq mismatch"
            if py_ev.memRead or py_ev.memWrite or py_ev.memReadReq or py_ev.memWriteReq:
                assert py_ev.memAddress == ch_ev["memAddress"], (
                    f"Cycle {i} MemAddress mismatch: Py={py_ev.memAddress}, Ch={ch_ev['memAddress']}"
                )
            if py_ev.memWrite:
                assert (py_ev.memWriteData & 0xFFFFFFFF) == (ch_ev["memWriteData"] & 0xFFFFFFFF), f"Cycle {i} MemWriteData mismatch"
            assert py_ev.illegal == ch_ev["illegal"], f"Cycle {i} Illegal status mismatch"

        print(f"  [PASS] All {len(py_trace)} commit events matched 1:1 with bit-exact parity!")
        passed_programs += 1

    print("\n" + "=" * 80)
    print(f"DIFFERENTIAL RESULT: {passed_programs}/{len(PROGRAMS)} Programs Passed ({total_events} total commit events verified bit-for-bit)")
    print("=" * 80)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="RV32I Differential Verification Runner")
    parser.add_argument("--use-existing-traces", action="store_true", help="Skip running sbt test and use existing JSON trace files")
    args = parser.parse_args()
    run_differential_comparison(use_existing_traces=args.use_existing_traces)
