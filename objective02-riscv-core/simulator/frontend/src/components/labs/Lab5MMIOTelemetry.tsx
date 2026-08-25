import React from "react";
import { Activity, Cpu, Sliders, Play, SkipForward, RotateCcw, ArrowDown } from "lucide-react";
import { SimulationState } from "../../types";
import { TelemetryTable } from "../TelemetryTable";
import { PipelineDiagram } from "../PipelineDiagram";

interface Lab5Props {
  state: SimulationState | null;
  onSelectScenario: (id: string) => void;
  onStep: () => void;
  onRun: () => void;
  onReset: () => void;
  onWriteMMIO: (register: string, value: number) => void;
}

export const Lab5MMIOTelemetry: React.FC<Lab5Props> = ({
  state,
  onSelectScenario,
  onStep,
  onRun,
  onReset,
  onWriteMMIO,
}) => {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
      {/* Scenario Preset Selector */}
      <div className="glass-panel" style={{ padding: "14px 20px" }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: "12px" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <Activity size={16} color="var(--accent-cyan)" />
            <span style={{ fontSize: "14px", fontWeight: 700 }}>MMIO & TELEMETRY LAB PRESETS:</span>
          </div>

          <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
            <button
              className="btn btn-secondary"
              onClick={() => onSelectScenario("mmio_cross_layer")}
            >
              <Cpu size={13} color="var(--accent-cyan)" /> Cross-Layer MMIO & Telemetry Matrix
            </button>
          </div>
        </div>
      </div>

      {/* Control Bar */}
      <div className="control-bar">
        <div className="control-group">
          <button className="btn btn-primary" onClick={onStep}>
            <SkipForward size={14} /> Step 1 Cycle
          </button>
          <button className="btn btn-purple" onClick={onRun}>
            <Play size={14} /> Run Scenario
          </button>
          <button className="btn btn-secondary" onClick={onReset}>
            <RotateCcw size={14} /> Reset
          </button>
        </div>

        <div className="control-group">
          <div className="metric-pill">
            <span className="metric-pill-label">CLA SWITCHING</span>
            <span className="metric-pill-val">{state?.mmio.CLA_SWITCHING ?? 0}</span>
          </div>
          <div className="metric-pill">
            <span className="metric-pill-label">MUL THERMAL</span>
            <span className="metric-pill-val">{state?.mmio.MUL_THERMAL ?? 0}</span>
          </div>
          <div className="metric-pill">
            <span className="metric-pill-label">EDP CURRENT</span>
            <span className="metric-pill-val">{state?.mmio.EDP_CURRENT ?? 0}</span>
          </div>
        </div>
      </div>

      {/* Pipeline Diagram */}
      <PipelineDiagram state={state} />

      {/* Cross-Layer OS Scheduler Integration Diagram */}
      <div className="glass-panel" style={{ background: "rgba(15, 23, 42, 0.75)" }}>
        <div className="panel-header">
          <span className="panel-title">
            <Sliders size={16} color="var(--accent-amber)" />
            Objective 3 OS Adaptive Scheduler Interface Flow
          </span>
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: "16px", alignItems: "center", textAlign: "center", padding: "10px 0" }}>
          <div style={{ background: "rgba(0,0,0,0.3)", padding: "16px", borderRadius: "10px", border: "1px solid var(--border-subtle)" }}>
            <div style={{ fontSize: "14px", fontWeight: 700, color: "var(--accent-purple)", marginBottom: "4px" }}>
              Objective 3 Adaptive OS
            </div>
            <div style={{ fontSize: "11px", color: "var(--text-secondary)" }}>
              ML Scheduler & Task Manager
            </div>
            <div style={{ fontSize: "10px", color: "var(--text-muted)", marginTop: "8px", fontFamily: "var(--font-mono)" }}>
              Observes EDP & CLA Switching
            </div>
          </div>

          <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "6px" }}>
            <span style={{ fontSize: "11px", fontFamily: "var(--font-mono)", color: "var(--accent-amber)", fontWeight: 600 }}>
              SW SCHED_HINT (0x80002008)
            </span>
            <span style={{ fontSize: "11px", fontFamily: "var(--font-mono)", color: "var(--accent-cyan)", fontWeight: 600 }}>
              SW CURRENT_CONTEXT (0x80002024)
            </span>
            <ArrowDown size={18} color="var(--accent-amber)" />
          </div>

          <div style={{ background: "rgba(0,0,0,0.3)", padding: "16px", borderRadius: "10px", border: "1px solid var(--border-subtle)" }}>
            <div style={{ fontSize: "14px", fontWeight: 700, color: "var(--accent-cyan)", marginBottom: "4px" }}>
              Objective 2 PipelinedCore
            </div>
            <div style={{ fontSize: "11px", color: "var(--text-secondary)" }}>
              Hardware System MMIO Engine
            </div>
            <div style={{ fontSize: "10px", color: "var(--text-muted)", marginTop: "8px", fontFamily: "var(--font-mono)" }}>
              Controls core frequency & power state
            </div>
          </div>
        </div>
      </div>

      {/* Telemetry & MMIO Table */}
      {state && <TelemetryTable mmio={state.mmio} onWriteMMIO={onWriteMMIO} />}
    </div>
  );
};
