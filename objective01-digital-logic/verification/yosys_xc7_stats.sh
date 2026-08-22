#!/usr/bin/env bash
set -euo pipefail

output="${1:-verification/yosys_xc7_stats.csv}"
printf 'design,logic_cells,LUT1,LUT2,LUT3,LUT4,LUT5,LUT6,total_luts,CARRY4,FF,DSP48,BRAM18\n' > "$output"

for rtl in generated/{RippleCarryAdder,CarryLookaheadAdder,HierarchicalCarryLookaheadAdder,RegisteredRippleCarryAdder,RegisteredFlatCarryLookaheadAdder,RegisteredHierarchicalCarryLookaheadAdder,SimpleMultiplier,BoothMultiplier,BoothWallaceMultiplier,RegisteredSimpleMultiplier,RegisteredBoothMultiplier,RegisteredBoothWallaceMultiplier,ALU,Objective1Subsystem,TelemetryBlock}.sv; do
  top="$(basename "$rtl" .sv)"
  report="$(mktemp)"
  yosys -p "read_verilog -sv $rtl; hierarchy -check -top $top; synth_xilinx -family xc7 -noiopad -top $top; flatten; opt_clean; stat -json" > "$report"
  python - "$top" "$report" "$output" <<'PY'
import json
import sys

top, report_path, output_path = sys.argv[1:]
text = open(report_path, encoding="utf-8").read()
start = text.index('{\n   "creator"')
stats, _ = json.JSONDecoder().raw_decode(text[start:])
cells = stats["design"]["num_cells_by_type"]
lut_counts = [cells.get(f"LUT{i}", 0) for i in range(1, 7)]
def count(prefixes):
    return sum(value for name, value in cells.items() if any(name.startswith(prefix) for prefix in prefixes))
row = [top, sum(cells.values()), *lut_counts, sum(lut_counts),
       count(("CARRY4",)), count(("FDRE", "FDCE", "FDPE", "FDSE")),
       count(("DSP48",)), count(("RAMB18",))]
with open(output_path, "a", encoding="ascii") as output:
    output.write(",".join(map(str, row)) + "\n")
PY
  rm -f "$report"
done

cat "$output"