import React, { useState } from "react";
import { GitCompare, Cpu, Play, CheckCircle2, AlertTriangle } from "lucide-react";
import { compareCores } from "../../api";
import { CoreComparisonData, ScenarioItem } from "../../types";
import { DualCoreRaceVisualizer } from "./DualCoreRaceVisualizer";

interface LabCompareProps {
  scenarios: ScenarioItem[];
}

export const LabCompareCores: React.FC<LabCompareProps> = ({ scenarios }) => {
  const supportedScenarios = scenarios.filter((s) => s.single_cycle_compatible);
  const [selectedId, setSelectedId] = useState(supportedScenarios[0]?.id || "canon_prog1_alu");
  const [comparison, setComparison] = useState<CoreComparisonData | null>(null);
  const [loading, setLoading] = useState(false);

  const handleRunComparison = async () => {
    setLoading(true);
    try {
      const data = await compareCores(selectedId);
      setComparison(data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
      {/* Intro Banner */}
      <div className="glass-panel">
        <h2 style={{ fontSize: "18px", fontWeight: 700, marginBottom: "8px", color: "var(--accent-cyan)", display: "flex", alignItems: "center", gap: "8px" }}>
          <GitCompare size={20} />
          Compare Cores: SingleCycleCore vs 5-Stage PipelinedCore
        </h2>
        <p style={{ color: "var(--text-secondary)", fontSize: "13px", lineHeight: "1.6" }}>
          Execute the same program simultaneously on the golden reference <strong>SingleCycleCore</strong> and the
          high-performance <strong>5-Stage PipelinedCore</strong>. Observe measured simulated cycles, effective CPI,
          and microarchitectural hazard stall accounting.
        </p>
      </div>

      {/* Selector & Run Button */}
      <div className="glass-panel" style={{ padding: "16px 20px" }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: "12px" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
            <span style={{ fontSize: "12px", color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>
              SELECT COMMON SUBSET PROGRAM:
            </span>
            <select
              value={selectedId}
              onChange={(e) => setSelectedId(e.target.value)}
              style={{
                background: "#1e293b",
                color: "#fff",
                border: "1px solid var(--border-subtle)",
                borderRadius: "6px",
                padding: "6px 12px",
                fontSize: "12px",
                fontFamily: "var(--font-mono)",
                outline: "none",
              }}
            >
              {supportedScenarios.map((sc) => (
                <option key={sc.id} value={sc.id}>
                  {sc.title}
                </option>
              ))}
            </select>
          </div>

          <button className="btn btn-primary" onClick={handleRunComparison} disabled={loading}>
            <Play size={14} /> {loading ? "Running Simulation..." : "Run Side-by-Side Comparison"}
          </button>
        </div>
      </div>

      {/* Comparison Results Card */}
      {comparison && comparison.compatible && comparison.single_cycle && comparison.pipelined && (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "20px" }}>
          {/* SingleCycleCore Card */}
          <div className="glass-panel">
            <div className="panel-header">
              <span className="panel-title">
                <Cpu size={16} color="var(--accent-amber)" />
                SingleCycleCore (Golden Reference)
              </span>
              <span className="stage-status-badge badge-bubble">1.00 CPI</span>
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: "12px", fontFamily: "var(--font-mono)", fontSize: "13px" }}>
              <div style={{ display: "flex", justifyContent: "space-between", padding: "8px 0", borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
                <span style={{ color: "var(--text-muted)" }}>Instructions Retired:</span>
                <span style={{ fontWeight: 700, color: "#fff" }}>{comparison.single_cycle.instructions}</span>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between", padding: "8px 0", borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
                <span style={{ color: "var(--text-muted)" }}>Simulated Clock Cycles:</span>
                <span style={{ fontWeight: 700, color: "var(--accent-amber)" }}>{comparison.single_cycle.cycles}</span>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between", padding: "8px 0", borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
                <span style={{ color: "var(--text-muted)" }}>Cycles Per Instruction (CPI):</span>
                <span style={{ fontWeight: 700, color: "var(--accent-emerald)" }}>{comparison.single_cycle.cpi.toFixed(2)}</span>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between", padding: "8px 0", borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
                <span style={{ color: "var(--text-muted)" }}>Pipeline Stalls / Bubbles:</span>
                <span style={{ color: "var(--text-muted)" }}>— (N/A)</span>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between", padding: "8px 0" }}>
                <span style={{ color: "var(--text-muted)" }}>Architectural Class:</span>
                <span style={{ color: "var(--text-secondary)", fontSize: "11px" }}>Single-cycle unpipelined</span>
              </div>
            </div>
          </div>

          {/* 5-Stage PipelinedCore Card */}
          <div className="glass-panel" style={{ borderLeft: "4px solid var(--accent-cyan)" }}>
            <div className="panel-header">
              <span className="panel-title">
                <Cpu size={16} color="var(--accent-cyan)" />
                5-Stage PipelinedCore (Full Hardware)
              </span>
              <span className="stage-status-badge badge-valid">{comparison.pipelined.cpi.toFixed(2)} CPI</span>
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: "12px", fontFamily: "var(--font-mono)", fontSize: "13px" }}>
              <div style={{ display: "flex", justifyContent: "space-between", padding: "8px 0", borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
                <span style={{ color: "var(--text-muted)" }}>Instructions Retired:</span>
                <span style={{ fontWeight: 700, color: "#fff" }}>{comparison.pipelined.instructions}</span>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between", padding: "8px 0", borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
                <span style={{ color: "var(--text-muted)" }}>Simulated Clock Cycles:</span>
                <span style={{ fontWeight: 700, color: "var(--accent-cyan)" }}>{comparison.pipelined.cycles}</span>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between", padding: "8px 0", borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
                <span style={{ color: "var(--text-muted)" }}>Cycles Per Instruction (CPI):</span>
                <span style={{ fontWeight: 700, color: "var(--accent-emerald)" }}>{comparison.pipelined.cpi.toFixed(2)}</span>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between", padding: "8px 0", borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
                <span style={{ color: "var(--text-muted)" }}>Load-Use Stalls:</span>
                <span style={{ fontWeight: 700, color: "var(--accent-amber)" }}>{comparison.pipelined.load_use_stalls}</span>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between", padding: "8px 0" }}>
                <span style={{ color: "var(--text-muted)" }}>Branch Flush Penalty Cycles:</span>
                <span style={{ fontWeight: 700, color: "var(--accent-red)" }}>{comparison.pipelined.branch_flushes}</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Dual Core Race Track & Speedometer Visualizer */}
      <DualCoreRaceVisualizer data={comparison} />

      {/* Scope Note */}
      <div className="glass-panel" style={{ background: "rgba(0,0,0,0.3)" }}>
        <div style={{ fontSize: "12px", color: "var(--text-secondary)", lineHeight: "1.6" }}>
          <strong>Architectural Scope Distinction:</strong> SingleCycleCore is the frozen baseline reference implementing the canonical RV32I subset. Extended hardware features (full RV32M multi-cycle multiplier/divider, System MMIO telemetry, CapabilityLite bounded registers, and dedicated precise traps) are implemented exclusively in PipelinedCore.
        </div>
      </div>
    </div>
  );
};
