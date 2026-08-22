#!/usr/bin/env python3
import random
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from reference.models import alu


RTL = ROOT / "generated" / "ALU.sv"


def make_vectors(count=128):
    random.seed(6)
    values = [(0, 0), (0x7FFFFFFF, 1), (0x80000000, 1), (0xFFFFFFFF, 1)]
    values.extend((random.getrandbits(32), random.getrandbits(32)) for _ in range(count))
    return [(a, b, opcode, alu(32, a, b, opcode))
            for a, b in values for opcode in range(11)]


def make_testbench(vectors):
    lines = [
        "module PythonVerilatorALUTb;",
        "  logic clock = 0; logic reset = 0;",
        "  logic [31:0] io_a; logic [31:0] io_b; logic [3:0] io_opcode;",
        "  wire [31:0] io_result; wire io_zero, io_negative, io_carry, io_overflow;",
        "  wire io_busy, io_done, io_valid;",
        "  ALU dut (.clock(clock), .reset(reset), .io_a(io_a), .io_b(io_b),",
        "    .io_opcode(io_opcode), .io_result(io_result), .io_zero(io_zero),",
        "    .io_negative(io_negative), .io_carry(io_carry), .io_overflow(io_overflow),",
        "    .io_busy(io_busy), .io_done(io_done), .io_valid(io_valid));",
        "  always #1 clock = ~clock;",
        "  initial begin",
    ]
    for index, (a, b, opcode, expected) in enumerate(vectors):
        lines.extend([
            f"    io_a = 32'h{a:08x}; io_b = 32'h{b:08x}; io_opcode = 4'd{opcode}; #1;",
            f"    if (io_result !== 32'h{expected['result']:08x}) $fatal(1, \"vector {index} result\");",
            f"    if (io_zero !== 1'b{expected['zero']}) $fatal(1, \"vector {index} zero\");",
            f"    if (io_negative !== 1'b{expected['negative']}) $fatal(1, \"vector {index} negative\");",
            f"    if (io_carry !== 1'b{expected['carry']}) $fatal(1, \"vector {index} carry\");",
            f"    if (io_overflow !== 1'b{expected['overflow']}) $fatal(1, \"vector {index} overflow\");",
        ])
    lines.extend([
        f'    $display("Python/Verilator ALU differential test passed: {len(vectors)} vectors");',
        "    $finish;",
        "  end",
        "endmodule",
    ])
    return "\n".join(lines) + "\n"


def main():
    if not RTL.exists():
        raise SystemExit("generated/ALU.sv is missing; run sbt 'runMain GenerateRTL' first")
    vectors = make_vectors()
    with tempfile.TemporaryDirectory(prefix="objective1-diff-") as directory:
        testbench = Path(directory) / "PythonVerilatorALUTb.sv"
        testbench.write_text(make_testbench(vectors), encoding="ascii")
        binary = Path(directory) / "VPythonVerilatorALUTb"
        subprocess.run([
            "verilator", "--binary", "--timing", "--Wno-fatal",
            "--top-module", "PythonVerilatorALUTb", "-o", str(binary),
            str(RTL), str(testbench),
        ], cwd=ROOT, check=True)
        subprocess.run([str(binary)], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()