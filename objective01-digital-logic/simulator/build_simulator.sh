#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
rtl="$root/generated/Objective1Subsystem.sv"
source="$root/simulator/objective1_sim.cpp"
binary="$root/simulator/objective1_sim"

if [[ ! -f "$rtl" ]]; then
  echo "Missing $rtl; run sbt 'runMain GenerateRTL' first." >&2
  exit 2
fi

verilator --cc --exe --build --timing --Wno-fatal \
  --top-module Objective1Subsystem \
  --Mdir "$root/obj_dir/objective1_sim" \
  -o "$binary" "$rtl" "$source"
echo "Built $binary"