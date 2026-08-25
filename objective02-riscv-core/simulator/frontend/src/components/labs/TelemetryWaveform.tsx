import React, { useState, useEffect } from "react";
import { Activity, TrendingUp, Zap, Radio, Sliders, Play, RotateCcw, Cpu, ShieldAlert, Sparkles } from "lucide-react";
import { MMIORegisters } from "../../types";

interface TelemetryWaveformProps {
  mmio: MMIORegisters;
}

interface DataPoint {
  cycle: number;
  cla: number;
  mul: number;
  edp: number;
  retired: number;
}

export const TelemetryWaveform: React.FC<TelemetryWaveformProps> = ({ mmio }) => {
  const [history, setHistory] = useState<DataPoint[]>([]);
  const [timebase, setTimebase] = useState<number>(1); // ns/div
  const [ch1Visible, setCh1Visible] = useState<boolean>(true); // CLA
  const [ch2Visible, setCh2Visible] = useState<boolean>(true); // MUL Thermal
  const [ch3Visible, setCh3Visible] = useState<boolean>(true); // EDP
  const [ch4Visible, setCh4Visible] = useState<boolean>(true); // Retired

  const claVal = mmio.CLA_SWITCHING ?? 0;
  const mulVal = mmio.MUL_THERMAL ?? 0;
  const edpVal = mmio.EDP_CURRENT ?? 0;
  const retiredVal = mmio.RETIRED_COUNT ?? 0;

  // Track telemetry history as new state updates arrive
  useEffect(() => {
    setHistory((prev) => {
      const nextCycle = prev.length > 0 ? prev[prev.length - 1].cycle + 1 : 1;
      const nextPoint: DataPoint = {
        cycle: nextCycle,
        cla: claVal,
        mul: mulVal,
        edp: edpVal,
        retired: retiredVal,
      };
      // Keep last 24 sample points for smooth oscilloscope waveform
      const updated = [...prev, nextPoint];
      return updated.slice(-24);
    });
  }, [claVal, mulVal, edpVal, retiredVal]);

  const maxVal = Math.max(20, ...history.map((d) => Math.max(d.cla, d.mul, d.edp, d.retired)), 1);

  // SVG Chart Dimensions
  const svgWidth = 850;
  const svgHeight = 220;
  const padding = { top: 20, right: 30, bottom: 30, left: 50 };
  const graphWidth = svgWidth - padding.left - padding.right;
  const graphHeight = svgHeight - padding.top - padding.bottom;

  // Generate SVG path for a metric
  const generatePath = (key: keyof DataPoint) => {
    if (history.length === 0) return "";
    const points = history.map((d, idx) => {
      const x = padding.left + (idx / Math.max(1, history.length - 1)) * graphWidth;
      const rawY = Number(d[key]);
      const y = padding.top + graphHeight - (rawY / maxVal) * graphHeight;
      return `${x},${Math.max(padding.top, Math.min(padding.top + graphHeight, y))}`;
    });
    return `M ${points.join(" L ")}`;
  };

  return (
    <div className="glass-panel" style={{ marginTop: "20px", background: "rgba(10, 14, 23, 0.95)", border: "1px solid var(--border-subtle)" }}>
      {/* Oscilloscope Header & Controls */}
      <div className="panel-header" style={{ marginBottom: "12px" }}>
        <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
          <Radio size={18} color="var(--accent-cyan)" className="engine-dot" />
          <span style={{ fontSize: "15px", fontWeight: 700, color: "#fff", letterSpacing: "0.02em" }}>
            DIGITAL TELEMETRY OSCILLOSCOPE & ENERGY-DELAY MONITOR
          </span>
        </div>

        {/* Channel Toggles & Timebase */}
        <div style={{ display: "flex", alignItems: "center", gap: "10px", flexWrap: "wrap" }}>
          <button
            className={`btn ${ch1Visible ? "btn-primary" : "btn-secondary"}`}
            style={{ padding: "4px 10px", fontSize: "11px" }}
            onClick={() => setCh1Visible(!ch1Visible)}
          >
            CH1: CLA ({claVal})
          </button>
          <button
            className={`btn ${ch2Visible ? "btn-purple" : "btn-secondary"}`}
            style={{ padding: "4px 10px", fontSize: "11px" }}
            onClick={() => setCh2Visible(!ch2Visible)}
          >
            CH2: MUL Thermal ({mulVal})
          </button>
          <button
            className={`btn ${ch3Visible ? "btn-primary" : "btn-secondary"}`}
            style={{
              padding: "4px 10px",
              fontSize: "11px",
              background: ch3Visible ? "var(--accent-amber)" : "#1e293b",
              color: ch3Visible ? "#000" : "#fff",
            }}
            onClick={() => setCh3Visible(!ch3Visible)}
          >
            CH3: EDP ({edpVal})
          </button>
          <button
            className={`btn ${ch4Visible ? "btn-primary" : "btn-secondary"}`}
            style={{
              padding: "4px 10px",
              fontSize: "11px",
              background: ch4Visible ? "var(--accent-emerald)" : "#1e293b",
              color: ch4Visible ? "#000" : "#fff",
            }}
            onClick={() => setCh4Visible(!ch4Visible)}
          >
            CH4: Retired ({retiredVal})
          </button>
        </div>
      </div>

      {/* Oscilloscope Screen with Graticule Grid */}
      <div style={{ position: "relative", width: "100%", background: "#06090e", borderRadius: "10px", padding: "12px", border: "1px solid rgba(0, 245, 212, 0.2)", boxShadow: "inset 0 0 40px rgba(0,0,0,0.8)" }}>
        <svg viewBox={`0 0 ${svgWidth} ${svgHeight}`} style={{ width: "100%", height: "auto", display: "block" }}>
          {/* Graticule Grid Lines */}
          <g stroke="rgba(255, 255, 255, 0.06)" strokeWidth="1">
            {/* Horizontal Grid */}
            {[0, 0.25, 0.5, 0.75, 1].map((pct, i) => {
              const y = padding.top + pct * graphHeight;
              return <line key={`h-${i}`} x1={padding.left} y1={y} x2={svgWidth - padding.right} y2={y} strokeDasharray={i === 2 ? "none" : "2,4"} stroke={i === 2 ? "rgba(255,255,255,0.15)" : "rgba(255,255,255,0.06)"} />;
            })}
            {/* Vertical Grid */}
            {[0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1].map((pct, i) => {
              const x = padding.left + pct * graphWidth;
              return <line key={`v-${i}`} x1={x} y1={padding.top} x2={x} y2={svgHeight - padding.bottom} strokeDasharray={i === 4 ? "none" : "2,4"} stroke={i === 4 ? "rgba(255,255,255,0.15)" : "rgba(255,255,255,0.06)"} />;
            })}
          </g>

          {/* Y-Axis Labels */}
          <text x={padding.left - 8} y={padding.top + 4} fill="var(--text-muted)" fontSize="9" textAnchor="end" fontFamily="var(--font-mono)">
            {maxVal}
          </text>
          <text x={padding.left - 8} y={padding.top + graphHeight / 2 + 4} fill="var(--text-muted)" fontSize="9" textAnchor="end" fontFamily="var(--font-mono)">
            {Math.round(maxVal / 2)}
          </text>
          <text x={padding.left - 8} y={padding.top + graphHeight + 4} fill="var(--text-muted)" fontSize="9" textAnchor="end" fontFamily="var(--font-mono)">
            0
          </text>

          {/* X-Axis Labels (Timebase) */}
          <text x={padding.left} y={svgHeight - 10} fill="var(--text-muted)" fontSize="9" fontFamily="var(--font-mono)">
            T-24 Cycles
          </text>
          <text x={svgWidth / 2} y={svgHeight - 10} fill="var(--text-muted)" fontSize="9" textAnchor="middle" fontFamily="var(--font-mono)">
            T-12 Cycles
          </text>
          <text x={svgWidth - padding.right} y={svgHeight - 10} fill="var(--text-muted)" fontSize="9" textAnchor="end" fontFamily="var(--font-mono)">
            Current (T=0)
          </text>

          {/* Channel 1 Waveform (Cyan: CLA Switching) */}
          {ch1Visible && (
            <path
              d={generatePath("cla")}
              fill="none"
              stroke="#00f5d4"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
              filter="url(#glow-cyan)"
            />
          )}

          {/* Channel 2 Waveform (Purple: MUL Thermal) */}
          {ch2Visible && (
            <path
              d={generatePath("mul")}
              fill="none"
              stroke="#a855f7"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
              filter="url(#glow-purple)"
            />
          )}

          {/* Channel 3 Waveform (Amber: EDP Proxy) */}
          {ch3Visible && (
            <path
              d={generatePath("edp")}
              fill="none"
              stroke="#f59e0b"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
              filter="url(#glow-amber)"
            />
          )}

          {/* Channel 4 Waveform (Emerald: Retired Instructions) */}
          {ch4Visible && (
            <path
              d={generatePath("retired")}
              fill="none"
              stroke="#10b981"
              strokeWidth="2"
              strokeDasharray="4,4"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          )}
        </svg>

        {/* Live Scope Measurements Readout Bar */}
        <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "10px", marginTop: "12px", background: "rgba(0,0,0,0.6)", padding: "10px", borderRadius: "6px", border: "1px solid var(--border-subtle)", fontFamily: "var(--font-mono)", fontSize: "11px" }}>
          <div style={{ display: "flex", flexDirection: "column" }}>
            <span style={{ color: "var(--accent-cyan)" }}>● CH1 CLA SWITCHING:</span>
            <span style={{ fontWeight: 700, fontSize: "13px", color: "#fff" }}>{claVal} trans/cycle</span>
          </div>
          <div style={{ display: "flex", flexDirection: "column" }}>
            <span style={{ color: "var(--accent-purple)" }}>● CH2 MUL THERMAL:</span>
            <span style={{ fontWeight: 700, fontSize: "13px", color: "#fff" }}>{mulVal} J/cycle proxy</span>
          </div>
          <div style={{ display: "flex", flexDirection: "column" }}>
            <span style={{ color: "var(--accent-amber)" }}>● CH3 EDP SCORE:</span>
            <span style={{ fontWeight: 700, fontSize: "13px", color: "#fff" }}>{edpVal} (Delay × Energy)</span>
          </div>
          <div style={{ display: "flex", flexDirection: "column" }}>
            <span style={{ color: "var(--accent-emerald)" }}>● CH4 RETIRED TOTAL:</span>
            <span style={{ fontWeight: 700, fontSize: "13px", color: "#fff" }}>{retiredVal} instructions</span>
          </div>
        </div>
      </div>
    </div>
  );
};
