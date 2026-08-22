#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
mkdir -p "$root/visualization/waveforms"

verilator --binary --timing --trace --Wno-fatal --top-module ALUWaveform_tb \
  "$root/generated/ALU.sv" "$root/visualization/waveforms/ALUWaveform_tb.sv" \
  -o "$root/visualization/waveforms/ALUWaveform_tb"
"$root/visualization/waveforms/ALUWaveform_tb"

verilator --binary --timing --trace --Wno-fatal --top-module TelemetryWaveform_tb \
  "$root/generated/TelemetryBlock.sv" "$root/visualization/waveforms/TelemetryWaveform_tb.sv" \
  -o "$root/visualization/waveforms/TelemetryWaveform_tb"
"$root/visualization/waveforms/TelemetryWaveform_tb"

echo "Waveforms written to $root/visualization/waveforms"