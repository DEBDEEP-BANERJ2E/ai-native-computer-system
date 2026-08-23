import { useState } from "react";
import type { HistoryEntry } from "../types";

interface TraceConfig {
  key: string;
  label: string;
  kind: "clk" | "bit" | "hex" | "op" | "int";
  color: string;
}

const TRACES: TraceConfig[] = [
  { key: "clk", label: "CLK", kind: "clk", color: "#8fd5b5" },
  { key: "valid", label: "VALID", kind: "bit", color: "#8fd5b5" },
  { key: "op", label: "OPCODE", kind: "op", color: "#d8a66a" },
  { key: "a", label: "A [31:0]", kind: "hex", color: "#8cb7db" },
  { key: "b", label: "B [31:0]", kind: "hex", color: "#8cb7db" },
  { key: "result", label: "RESULT [31:0]", kind: "hex", color: "#e5b286" },
  { key: "zero", label: "FLAG_Z", kind: "bit", color: "#8fd5b5" },
  { key: "negative", label: "FLAG_N", kind: "bit", color: "#8fd5b5" },
  { key: "carry", label: "FLAG_C", kind: "bit", color: "#8fd5b5" },
  { key: "overflow", label: "FLAG_V", kind: "bit", color: "#8fd5b5" },
  { key: "cla_switching", label: "CLA_SW", kind: "int", color: "#70cba5" },
  { key: "mul_thermal", label: "MUL_ACT", kind: "int", color: "#d0966a" },
];

const LABEL_W = 105;
const PAD = 16;
const ROW_H = 28;
const CYCLE_W = 90;
const SLACK_CYCLES = 1;

function hex(v: number) {
  return (v >>> 0).toString(16).padStart(8, "0").toUpperCase();
}

function buildClkPath(xStart: number, xEnd: number, yMid: number) {
  const half = CYCLE_W / 2;
  const amp = 8;
  const parts: string[] = [];
  let x = xStart;
  parts.push(`M ${x} ${yMid + amp}`);
  while (x < xEnd) {
    const rise = x;
    const fall = Math.min(x + half, xEnd);
    const nextRise = Math.min(x + CYCLE_W, xEnd);
    parts.push(`L ${rise} ${yMid - amp}`);
    parts.push(`L ${fall} ${yMid - amp}`);
    parts.push(`L ${fall} ${yMid + amp}`);
    parts.push(`L ${nextRise} ${yMid + amp}`);
    x = nextRise;
  }
  return parts.join(" ");
}

function buildBitPath(values: (boolean | number)[], xStart: number, yMid: number, amp = 8) {
  const parts: string[] = [];
  let prev = Boolean(values[0]);
  parts.push(`M ${xStart} ${yMid + (prev ? -amp : amp)}`);

  values.forEach((v, idx) => {
    const isHigh = Boolean(v);
    const stepX = xStart + idx * CYCLE_W;
    const nextX = stepX + CYCLE_W;
    if (idx > 0 && isHigh !== prev) {
      parts.push(`L ${stepX} ${yMid + (prev ? -amp : amp)}`);
      parts.push(`L ${stepX} ${yMid + (isHigh ? -amp : amp)}`);
      prev = isHigh;
    }
    parts.push(`L ${nextX} ${yMid + (isHigh ? -amp : amp)}`);
  });

  return parts.join(" ");
}

export function WaveformView({ history }: { history: HistoryEntry[] }) {
  const [hoverCycle, setHoverCycle] = useState<number | null>(null);

  const cycles = history.slice(-20);
  const totalCycles = Math.max(cycles.length + SLACK_CYCLES, 8);
  const innerW = totalCycles * CYCLE_W;
  const width = LABEL_W + innerW + PAD;
  const rows = TRACES.length;
  const header = 32;
  const height = header + rows * ROW_H + PAD;
  const cycleStart = LABEL_W + 6;

  function rowY(idx: number) {
    return header + idx * ROW_H + ROW_H / 2;
  }

  const clkEnd = cycleStart + totalCycles * CYCLE_W;
  const hoveredEntry = hoverCycle !== null ? cycles.find((c) => c.cycle === hoverCycle) : null;

  return (
    <div className="waveform-svg-wrap">
      {hoveredEntry && (
        <div className="waveform-tooltip">
          <strong>Cycle #{hoveredEntry.cycle}</strong>
          <span>Op: {hoveredEntry.operation}</span>
          <span>A: 0x{hex(hoveredEntry.a)}</span>
          <span>B: 0x{hex(hoveredEntry.b)}</span>
          <span>Res: 0x{hex(hoveredEntry.result)}</span>
          <span>Flags: Z={hoveredEntry.zero ? 1 : 0} N={hoveredEntry.negative ? 1 : 0} C={hoveredEntry.carry ? 1 : 0} V={hoveredEntry.overflow ? 1 : 0}</span>
          <span>CLA_SW: {hoveredEntry.telemetry.cla_switching} | MUL_TH: {hoveredEntry.telemetry.mul_thermal}</span>
        </div>
      )}

      <svg
        viewBox={`0 0 ${width} ${height}`}
        className="waveform-svg"
        xmlns="http://www.w3.org/2000/svg"
        preserveAspectRatio="xMinYMid meet"
      >
        <defs>
          <pattern id="wGrid" width={CYCLE_W} height={ROW_H} patternUnits="userSpaceOnUse">
            <path d={`M ${CYCLE_W} 0 L 0 0 0 ${ROW_H}`} fill="none" stroke="#1f2d2c" strokeWidth="0.8" />
          </pattern>
        </defs>

        {/* Canvas Background */}
        <rect x="0" y="0" width={width} height={height} fill="#0d1616" />
        <rect x="0" y="0" width={LABEL_W} height={height} fill="#111c1b" stroke="#253534" strokeWidth="1" />
        <rect x={LABEL_W} y="0" width={innerW} height={header} fill="#111c1b" stroke="#253534" strokeWidth="1" />
        <rect x={LABEL_W} y={header} width={innerW} height={rows * ROW_H} fill="url(#wGrid)" />

        {/* Cycle header labels */}
        {Array.from({ length: totalCycles }).map((_, i) => {
          const cx = cycleStart + i * CYCLE_W + CYCLE_W / 2;
          const entry = cycles[i];
          const isHovered = entry && entry.cycle === hoverCycle;
          return (
            <g
              key={`c-${entry ? entry.cycle : `pad-${i}`}`}
              onMouseEnter={() => entry && setHoverCycle(entry.cycle)}
              onMouseLeave={() => setHoverCycle(null)}
              className="cycle-col-target"
            >
              {entry && (
                <rect
                  x={cycleStart + i * CYCLE_W}
                  y={0}
                  width={CYCLE_W}
                  height={height}
                  fill={isHovered ? "#223b36" : "transparent"}
                  opacity={isHovered ? 0.35 : 0}
                />
              )}
              <text
                x={cx}
                y={21}
                fontSize="10"
                fontFamily="DM Mono"
                fontWeight={entry ? "600" : "400"}
                fill={entry ? (isHovered ? "#9ee1c0" : "#d39e72") : "#475c5b"}
                textAnchor="middle"
              >
                {entry ? `C${entry.cycle}` : `C${i + 1}`}
              </text>
            </g>
          );
        })}

        {/* Signal Labels Column */}
        {TRACES.map((t, idx) => {
          const y = rowY(idx);
          const yTop = header + idx * ROW_H;
          return (
            <g key={t.key}>
              <rect x="0" y={yTop} width={LABEL_W} height={ROW_H} stroke="#1f2d2c" strokeWidth="0.5" fill="none" />
              <text x={12} y={y + 4} fontSize="9.5" fontFamily="DM Mono" fill="#8cb7a7" fontWeight="500" letterSpacing="0.5">
                {t.label}
              </text>
              <line x1={LABEL_W} x2={clkEnd} y1={yTop} y2={yTop} stroke="#1f2d2c" strokeWidth="0.8" />
            </g>
          );
        })}

        {/* Signal Waveform Traces */}
        {TRACES.map((t, idx) => {
          const y = rowY(idx);

          // 1. Clock
          if (t.kind === "clk") {
            return (
              <path
                key={t.key}
                d={buildClkPath(cycleStart, clkEnd, y)}
                fill="none"
                stroke={t.color}
                strokeWidth="1.5"
              />
            );
          }

          // 2. Single-bit digital lines
          if (t.kind === "bit") {
            const values: (0 | 1)[] = Array.from({ length: totalCycles }).map((_, i) => {
              const e = cycles[i];
              if (!e) return 0;
              if (t.key === "valid") return e.valid ? 1 : 0;
              if (t.key === "zero") return e.zero ? 1 : 0;
              if (t.key === "negative") return e.negative ? 1 : 0;
              if (t.key === "carry") return e.carry ? 1 : 0;
              if (t.key === "overflow") return e.overflow ? 1 : 0;
              return 0;
            });
            return (
              <g key={t.key}>
                <path
                  d={buildBitPath(values, cycleStart, y)}
                  fill="none"
                  stroke={t.color}
                  strokeWidth="1.6"
                />
                {values.map((v, i) =>
                  cycles[i] ? (
                    <text
                      key={`bv-${cycles[i].cycle}`}
                      x={cycleStart + i * CYCLE_W + CYCLE_W / 2}
                      y={y - 10}
                      fontSize="8"
                      fontFamily="DM Mono"
                      fill={t.color}
                      textAnchor="middle"
                    >
                      {v}
                    </text>
                  ) : null
                )}
              </g>
            );
          }

          // 3. Multi-bit Hex Bus Packets
          if (t.kind === "hex") {
            return (
              <g key={t.key}>
                {Array.from({ length: totalCycles }).map((_, i) => {
                  const e = cycles[i];
                  const x = cycleStart + i * CYCLE_W;
                  if (!e) return null;
                  const v = t.key === "a" ? e.a : t.key === "b" ? e.b : e.result;
                  const hexStr = hex(v);
                  const w = CYCLE_W - 6;
                  const top = y - 10;
                  const h = 20;

                  // Hexagonal bus packet polygon path
                  const busPath = `M ${x + 6} ${top + h / 2} L ${x + 10} ${top} L ${x + w - 4} ${top} L ${x + w} ${top + h / 2} L ${x + w - 4} ${top + h} L ${x + 10} ${top + h} Z`;

                  return (
                    <g key={`h-${e.cycle}`}>
                      <path d={busPath} fill="#142120" stroke="#334d49" strokeWidth="1" />
                      <text
                        x={x + CYCLE_W / 2}
                        y={y + 3.5}
                        fontSize="8.5"
                        fontFamily="DM Mono"
                        fill={t.color}
                        fontWeight="600"
                        textAnchor="middle"
                      >
                        {hexStr}
                      </text>
                    </g>
                  );
                })}
              </g>
            );
          }

          // 4. Opcode name tags
          if (t.kind === "op") {
            return (
              <g key={t.key}>
                {Array.from({ length: totalCycles }).map((_, i) => {
                  const e = cycles[i];
                  const x = cycleStart + i * CYCLE_W;
                  if (!e) return null;
                  return (
                    <g key={`op-${e.cycle}`}>
                      <rect
                        x={x + 5}
                        y={y - 9}
                        width={CYCLE_W - 10}
                        height={18}
                        rx={2}
                        fill="#281f14"
                        stroke="#735431"
                        strokeWidth="0.8"
                      />
                      <text
                        x={x + CYCLE_W / 2}
                        y={y + 3.5}
                        fontSize="9"
                        fontFamily="DM Mono"
                        fill={t.color}
                        fontWeight="600"
                        textAnchor="middle"
                      >
                        {e.operation}
                      </text>
                    </g>
                  );
                })}
              </g>
            );
          }

          // 5. Stepped Integer / Telemetry Metrics
          if (t.kind === "int") {
            return (
              <g key={t.key}>
                {Array.from({ length: totalCycles }).map((_, i) => {
                  const e = cycles[i];
                  const x = cycleStart + i * CYCLE_W;
                  if (!e) return null;
                  const v = t.key === "cla_switching" ? e.telemetry.cla_switching : e.telemetry.mul_thermal;
                  const top = y - 10;
                  const max = 48;
                  const pct = Math.max(0.15, Math.min(1, v / max));
                  const barH = 18 * pct;
                  return (
                    <g key={`i-${e.cycle}`}>
                      <rect
                        x={x + 6}
                        y={top + (18 - barH)}
                        width={CYCLE_W - 12}
                        height={barH}
                        fill={t.color}
                        opacity="0.35"
                        rx={1}
                      />
                      <text
                        x={x + CYCLE_W / 2}
                        y={y + 3.5}
                        fontSize="8.5"
                        fontFamily="DM Mono"
                        fill={t.color}
                        fontWeight="600"
                        textAnchor="middle"
                      >
                        {v}
                      </text>
                    </g>
                  );
                })}
              </g>
            );
          }

          return null;
        })}

        {/* Empty history banner */}
        {cycles.length === 0 ? (
          <g>
            <rect
              x={LABEL_W + 12}
              y={header + 12}
              width={totalCycles * CYCLE_W - 24}
              height={rows * ROW_H - 24}
              fill="#0d1616"
              stroke="#223332"
              strokeDasharray="4 3"
            />
            <text
              x={LABEL_W + (totalCycles * CYCLE_W) / 2}
              y={header + (rows * ROW_H) / 2 + 4}
              textAnchor="middle"
              fontSize="12"
              fontFamily="DM Mono"
              fill="#627876"
            >
              Execute ALU operations above to record RTL transaction timing traces (sampled once per executed hardware cycle).
            </text>
          </g>
        ) : null}
      </svg>

      <div className="waveform-foot">
        <span>
          <b>RTL Transaction Timing View:</b> Sampled once per executed hardware transaction from persistent Verilator simulation.
        </span>
        <span className="trace-count-mono">
          Displaying last {cycles.length.toString().padStart(2, "0")} cycles (hover to inspect)
        </span>
      </div>
    </div>
  );
}
