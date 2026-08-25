import React from "react";
import { GitCompare, Cpu, Gauge, Zap } from "lucide-react";
import { CoreComparisonData } from "../../types";

interface DualCoreRaceProps {
  data: CoreComparisonData | null;
}

export const DualCoreRaceVisualizer: React.FC<DualCoreRaceProps> = ({ data }) => {
  if (!data || !data.compatible || !data.single_cycle || !data.pipelined) return null;

  const scc = data.single_cycle;
  const pipe = data.pipelined;

  return (
    <div className="glass-panel" style={{ marginTop: "20px" }}>
      <div className="panel-header">
        <div className="panel-title">
          <Gauge size={18} color="var(--accent-cyan)" />
          <span>Interactive Dual-Core Execution Race & Latency Analysis</span>
        </div>
      </div>

      {/* Speedometer & Metric Cards */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "20px", marginBottom: "20px" }}>
        {/* Track 1: SingleCycleCore */}
        <div style={{ background: "rgba(0,0,0,0.4)", padding: "16px", borderRadius: "10px", border: "1px solid var(--border-subtle)" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "12px" }}>
            <span style={{ fontSize: "14px", fontWeight: 700, color: "var(--accent-amber)" }}>
              SingleCycleCore (Baseline)
            </span>
            <span className="stage-status-badge badge-bubble">1.00 CPI</span>
          </div>

          <div style={{ height: "14px", background: "#1e293b", borderRadius: "7px", overflow: "hidden", marginBottom: "12px" }}>
            <div
              style={{
                height: "100%",
                width: "100%",
                background: "var(--accent-amber)",
              }}
            />
          </div>

          <div style={{ display: "flex", justifyContent: "space-between", fontSize: "12px", fontFamily: "var(--font-mono)" }}>
            <span>Cycles: {scc.cycles}</span>
            <span>Instructions: {scc.instructions}</span>
            <span>Throughput: 1.00 IPC</span>
          </div>
        </div>

        {/* Track 2: 5-Stage PipelinedCore */}
        <div style={{ background: "rgba(0,0,0,0.4)", padding: "16px", borderRadius: "10px", border: "1px solid var(--accent-cyan)" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "12px" }}>
            <span style={{ fontSize: "14px", fontWeight: 700, color: "var(--accent-cyan)" }}>
              5-Stage PipelinedCore (Optimized)
            </span>
            <span className="stage-status-badge badge-valid">{pipe.cpi.toFixed(2)} CPI</span>
          </div>

          <div style={{ height: "14px", background: "#1e293b", borderRadius: "7px", overflow: "hidden", marginBottom: "12px" }}>
            <div
              style={{
                height: "100%",
                width: `${Math.min(100, (scc.cycles / pipe.cycles) * 100)}%`,
                background: "linear-gradient(90deg, var(--accent-cyan), var(--accent-purple))",
              }}
            />
          </div>

          <div style={{ display: "flex", justifyContent: "space-between", fontSize: "12px", fontFamily: "var(--font-mono)" }}>
            <span>Cycles: {pipe.cycles}</span>
            <span>Load Stalls: {pipe.load_use_stalls}</span>
            <span>Branch Penalties: {pipe.branch_flushes}</span>
          </div>
        </div>
      </div>
    </div>
  );
};
