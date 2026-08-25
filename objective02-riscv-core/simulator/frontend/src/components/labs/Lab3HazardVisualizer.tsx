import React from "react";
import { AlertTriangle, FastForward, GitCommit, Play, SkipForward, RotateCcw } from "lucide-react";
import { SimulationState } from "../../types";
import { PipelineDiagram } from "../PipelineDiagram";
import { RegisterFileView } from "../RegisterFileView";

interface Lab3Props {
  state: SimulationState | null;
  onSelectScenario: (id: string) => void;
  onStep: () => void;
  onRun: () => void;
  onReset: () => void;
}

export const Lab3HazardVisualizer: React.FC<Lab3Props> = ({
  state,
  onSelectScenario,
  onStep,
  onRun,
  onReset,
}) => {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
      {/* Preset Scenario Selector Buttons */}
      <div className="glass-panel" style={{ padding: "14px 20px" }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: "12px" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <AlertTriangle size={16} color="var(--accent-amber)" />
            <span style={{ fontSize: "14px", fontWeight: 700 }}>HAZARD LAB PRESETS:</span>
          </div>

          <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
            <button
              className="btn btn-secondary"
              onClick={() => onSelectScenario("hazard_raw_exmem")}
            >
              <FastForward size={13} color="var(--accent-cyan)" /> RAW Forwarding (EX/MEM & MEM/WB)
            </button>
            <button
              className="btn btn-secondary"
              onClick={() => onSelectScenario("hazard_load_use")}
            >
              <AlertTriangle size={13} color="var(--accent-amber)" /> Load-Use Hazard (1-Cycle Stall)
            </button>
            <button
              className="btn btn-secondary"
              onClick={() => onSelectScenario("hazard_branch_flush")}
            >
              <GitCommit size={13} color="var(--accent-red)" /> Branch Control Hazard (2-Cycle Flush)
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
            <span className="metric-pill-label">LOAD-USE STALLS</span>
            <span className="metric-pill-val">{state?.mmio.LOAD_USE_STALL_COUNT ?? 0}</span>
          </div>
          <div className="metric-pill">
            <span className="metric-pill-label">BRANCHES TAKEN</span>
            <span className="metric-pill-val">{state?.mmio.BRANCH_TAKEN_COUNT ?? 0}</span>
          </div>
          <div className="metric-pill">
            <span className="metric-pill-label">TOTAL STALL CYCLES</span>
            <span className="metric-pill-val">{state?.mmio.PIPELINE_STALL_COUNT ?? 0}</span>
          </div>
        </div>
      </div>

      {/* Live Pipeline Visualizer */}
      <PipelineDiagram state={state} />

      {/* Forwarding & Hazard Unit Theory Card */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "20px" }}>
        <div className="glass-panel">
          <div className="panel-header">
            <span className="panel-title">
              <FastForward size={16} color="var(--accent-cyan)" />
              ForwardingUnit (Forwarding Mux Selection)
            </span>
          </div>
          <div style={{ fontSize: "12px", color: "var(--text-secondary)", lineHeight: "1.6" }}>
            <p style={{ marginBottom: "8px" }}>
              The <strong>ForwardingUnit</strong> detects Read-After-Write (RAW) data dependencies between the EX-stage instruction and uncommitted results in EX/MEM and MEM/WB pipeline registers:
            </p>
            <ul style={{ listStyleType: "disc", paddingLeft: "18px", fontFamily: "var(--font-mono)", fontSize: "11px", color: "var(--text-muted)", display: "flex", flexDirection: "column", gap: "4px" }}>
              <li><strong>ForwardA/B = 0:</strong> Register File Read (No Hazard)</li>
              <li><strong>ForwardA/B = 1:</strong> Forward from MEM/WB Register</li>
              <li><strong>ForwardA/B = 2:</strong> Forward from EX/MEM Register (Most Recent)</li>
            </ul>
          </div>
        </div>

        <div className="glass-panel">
          <div className="panel-header">
            <span className="panel-title">
              <AlertTriangle size={16} color="var(--accent-amber)" />
              HazardUnit (Stall & Bubble Insertion)
            </span>
          </div>
          <div style={{ fontSize: "12px", color: "var(--text-secondary)", lineHeight: "1.6" }}>
            <p style={{ marginBottom: "8px" }}>
              The <strong>HazardUnit</strong> handles dependencies that cannot be resolved combinationally by forwarding:
            </p>
            <ul style={{ listStyleType: "disc", paddingLeft: "18px", fontFamily: "var(--font-mono)", fontSize: "11px", color: "var(--text-muted)", display: "flex", flexDirection: "column", gap: "4px" }}>
              <li><strong>Load-Use Interlock:</strong> ID stage depends on EX stage Load. Holds IF/ID (stall=1) and inserts an ID/EX bubble (flush=1).</li>
              <li><strong>Branch/Jump Flush:</strong> Sequential fetch assumes branch not taken. When taken in EX, flushes both IF/ID and ID/EX (2 bubbles).</li>
              <li><strong>Capability RAW Hazard:</strong> Stalls ID when derivation depends on pending EX/MEM cap operations.</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
};
