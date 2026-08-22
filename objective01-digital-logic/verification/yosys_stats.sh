#!/usr/bin/env bash
set -euo pipefail

for rtl in generated/*.sv; do
  top="$(basename "$rtl" .sv)"
  echo "=== $top ==="
  yosys -p "read_verilog -sv $rtl; hierarchy -top $top; proc; opt; stat" \
    | grep -E '^[[:space:]]+[0-9]+ (wires|wire bits|public wires|public wire bits|cells|[A-Za-z_]|\$)' \
    | tail -20
done