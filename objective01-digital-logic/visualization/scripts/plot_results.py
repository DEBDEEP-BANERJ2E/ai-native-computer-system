#!/usr/bin/env python3
import csv
import sys
from pathlib import Path

import matplotlib.pyplot as plt


ROOT = Path(__file__).resolve().parents[2]
CSV_PATH = ROOT / "verification" / "yosys_xc7_stats.csv"
OUTPUT = ROOT / "visualization" / "charts"


def rows_by_name():
    with CSV_PATH.open(newline="", encoding="utf-8") as source:
        return {row["design"]: row for row in csv.DictReader(source)}


def bar_chart(path, title, names, values, ylabel):
    figure, axis = plt.subplots(figsize=(8, 4.5))
    bars = axis.bar(names, values, color=["#31587a", "#b56b45", "#4d8b78"])
    axis.set_title(title)
    axis.set_ylabel(ylabel)
    axis.grid(axis="y", alpha=0.25)
    axis.set_axisbelow(True)
    axis.bar_label(bars, padding=3)
    figure.tight_layout()
    figure.savefig(path, dpi=180)
    figure.savefig(path.with_suffix(".svg"))
    plt.close(figure)


def main():
    if not CSV_PATH.exists():
        raise SystemExit("Missing verification/yosys_xc7_stats.csv; run bash verification/yosys_xc7_stats.sh first")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    rows = rows_by_name()
    bar_chart(OUTPUT / "adder_lut_comparison.png", "Objective 1 adder resource comparison",
              ["Ripple carry", "Flat CLA", "Hierarchical CLA"],
              [int(rows[name]["total_luts"]) for name in
               ["RippleCarryAdder", "CarryLookaheadAdder", "HierarchicalCarryLookaheadAdder"]], "Mapped LUT primitives")
    bar_chart(OUTPUT / "multiplier_lut_comparison.png", "Objective 1 multiplier resource comparison",
              ["Simple", "Radix-4 Booth", "Booth-Wallace"],
              [int(rows[name]["total_luts"]) for name in
               ["SimpleMultiplier", "BoothMultiplier", "BoothWallaceMultiplier"]], "Mapped LUT primitives")
    alu_luts = int(rows["ALU"]["total_luts"])
    subsystem_luts = int(rows["Objective1Subsystem"]["total_luts"])
    bar_chart(OUTPUT / "telemetry_overhead.png", "ALU versus ALU with telemetry",
              ["ALU", "ALU + telemetry"], [alu_luts, subsystem_luts], "Mapped LUT primitives")
    print(f"Charts written to {OUTPUT}")


if __name__ == "__main__":
    main()