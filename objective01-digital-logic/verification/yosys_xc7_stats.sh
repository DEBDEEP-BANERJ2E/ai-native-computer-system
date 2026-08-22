#!/usr/bin/env bash
set -euo pipefail

output="${1:-verification/yosys_xc7_stats.csv}"
printf 'design,logic_cells,luts,carry4,flip_flops,dsp48,bram18\n' > "$output"

for rtl in generated/{RippleCarryAdder,CarryLookaheadAdder,HierarchicalCarryLookaheadAdder,SimpleMultiplier,BoothMultiplier,BoothWallaceMultiplier,ALU,Objective1Subsystem,TelemetryBlock}.sv; do
  top="$(basename "$rtl" .sv)"
  report="$(mktemp)"
  yosys -p "read_verilog -sv $rtl; hierarchy -check -top $top; synth_xilinx -family xc7 -top $top; stat" > "$report"
  cells="$(awk '/^=== design hierarchy ===/{in_hierarchy=1} in_hierarchy && $2 == "cells" {total=$1} END {print total + 0}' "$report")"
  luts="$(awk '/^=== design hierarchy ===/{in_hierarchy=1} in_hierarchy && $2 ~ /^LUT[1-6]$/ {sum += $1} END {print sum + 0}' "$report")"
  carry4="$(awk '/^=== design hierarchy ===/{in_hierarchy=1} in_hierarchy && $2 == "CARRY4" {sum += $1} END {print sum + 0}' "$report")"
  flip_flops="$(awk '/^=== design hierarchy ===/{in_hierarchy=1} in_hierarchy && $2 ~ /^(FDRE|FDCE|FDPE|FDSE)$/ {sum += $1} END {print sum + 0}' "$report")"
  dsp48="$(awk '/^=== design hierarchy ===/{in_hierarchy=1} in_hierarchy && $2 ~ /^DSP48/ {sum += $1} END {print sum + 0}' "$report")"
  bram18="$(awk '/^=== design hierarchy ===/{in_hierarchy=1} in_hierarchy && $2 ~ /^RAMB18/ {sum += $1} END {print sum + 0}' "$report")"
  printf '%s,%s,%s,%s,%s,%s,%s\n' "$top" "${cells:-0}" "$luts" "$carry4" "$flip_flops" "$dsp48" "$bram18" >> "$output"
  rm -f "$report"
done

cat "$output"