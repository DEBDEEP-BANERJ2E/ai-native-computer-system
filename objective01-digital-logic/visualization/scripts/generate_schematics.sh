#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
schematics="$root/visualization/schematics"
mkdir -p "$schematics"

for top in Fredkin Toffoli CLA4 HierarchicalCarryLookaheadAdder BoothWallaceMultiplier Objective1Subsystem; do
  rtl="$root/generated/$top.sv"
  if [[ ! -f "$rtl" ]]; then
    echo "Missing $rtl; run sbt 'runMain GenerateRTL' first." >&2
    exit 2
  fi
  yosys -q -p "read_verilog -sv $rtl; hierarchy -top $top; proc; opt; show -format dot -prefix $schematics/$top"
  dot -Tsvg "$schematics/$top.dot" -o "$schematics/$top.svg"
done

echo "Schematics written to $schematics"