#!/usr/bin/env bash
set -euo pipefail

chipdb="${1:-${NEXT_PNR_XILINX_CHIPDB:-}}"
if [[ -z "$chipdb" ]]; then
  echo "Usage: $0 /path/to/xc7a100t.chipdb" >&2
  echo "Set NEXT_PNR_XILINX_CHIPDB to use an environment variable instead." >&2
  exit 2
fi
if [[ ! -x "$(command -v nextpnr-xilinx || true)" ]]; then
  echo "nextpnr-xilinx is not installed or not on PATH." >&2
  exit 2
fi
if [[ ! -f "$chipdb" ]]; then
  echo "Chip database not found: $chipdb" >&2
  exit 2
fi

root="$(cd "$(dirname "$0")/../.." && pwd)"
mkdir -p "$root/nextpnr-results"

for top in RegisteredRippleCarryAdder RegisteredFlatCarryLookaheadAdder RegisteredHierarchicalCarryLookaheadAdder RegisteredSimpleMultiplier RegisteredBoothMultiplier RegisteredBoothWallaceMultiplier; do
  rtl="$root/generated/$top.sv"
  json="$root/nextpnr-results/$top.json"
  report="$root/nextpnr-results/$top.report"
  yosys -q -p "read_verilog -sv $rtl; hierarchy -check -top $top; synth_xilinx -family xc7 -noiopad -top $top; write_json $json"
  nextpnr-xilinx --chipdb "$chipdb" --json "$json" --xdc "$(dirname "$0")/objective1.xdc" --freq 100 --report "$report"
done

echo "nextpnr-Xilinx reports written to $root/nextpnr-results"