#!/usr/bin/env bash
set -euo pipefail

for rtl in generated/*.sv; do
  top="$(basename "$rtl" .sv)"
  echo "=== $top ==="
  verilator --lint-only --Wno-fatal --top-module "$top" --sv "$rtl"
done