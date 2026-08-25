import React from "react";
import { Shield, Lock, Play, SkipForward, RotateCcw, ArrowRight, ShieldCheck } from "lucide-react";
import { SimulationState } from "../../types";
import { CapabilityRegisterView } from "../CapabilityRegisterView";
import { PipelineDiagram } from "../PipelineDiagram";
import { RegisterFileView } from "../RegisterFileView";
import { MemoryMapVisualizer } from "./MemoryMapVisualizer";

interface Lab6Props {
  state: SimulationState | null;
  onSelectScenario: (id: string) => void;
  onStep: () => void;
  onRun: () => void;
  onReset: () => void;
}

export const Lab6CapabilityPlayground: React.FC<Lab6Props> = ({
  state,
  onSelectScenario,
  onStep,
  onRun,
  onReset,
}) => {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
      {/* Presets Header */}
      <div className="glass-panel" style={{ padding: "14px 20px" }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: "12px" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <Shield size={16} color="var(--accent-purple)" />
            <span style={{ fontSize: "14px", fontWeight: 700 }}>CAPABILITY SECURITY LAB PRESETS:</span>
          </div>

          <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
            <button
              className="btn btn-secondary"
              onClick={() => onSelectScenario("cap_derivation_chain")}
            >
              <ShieldCheck size={13} color="var(--accent-purple)" /> Monotonic Derivation & Bounded Authority
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
            <span className="metric-pill-label">IMMUTABLE ROOTS</span>
            <span className="metric-pill-val" style={{ color: "var(--accent-cyan)" }}>c0, c1, c2</span>
          </div>
          <div className="metric-pill">
            <span className="metric-pill-label">PROCESS CAPS</span>
            <span className="metric-pill-val" style={{ color: "var(--accent-purple)" }}>c3–c7 (Writable)</span>
          </div>
        </div>
      </div>

      {/* Pipeline Diagram */}
      <PipelineDiagram state={state} />

      {/* Visual Derivation Tree */}
      <div className="glass-panel" style={{ background: "rgba(15, 23, 42, 0.85)" }}>
        <div className="panel-header">
          <span className="panel-title">
            <ShieldCheck size={16} color="var(--accent-emerald)" />
            Monotonic Authority Reduction Tree (Mathematical Invariance)
          </span>
        </div>

        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-around", padding: "16px 0", flexWrap: "wrap", gap: "16px" }}>
          {/* Step 1: Root */}
          <div style={{ background: "rgba(0,0,0,0.4)", border: "1px solid var(--border-subtle)", borderRadius: "8px", padding: "12px", minWidth: "180px", textAlign: "center" }}>
            <div style={{ fontSize: "12px", fontWeight: 700, color: "var(--accent-cyan)", fontFamily: "var(--font-mono)" }}>c1 RAM ROOT</div>
            <div style={{ fontSize: "11px", color: "var(--text-muted)", marginTop: "4px" }}>Base: 0x00000000</div>
            <div style={{ fontSize: "11px", color: "var(--text-muted)" }}>Length: 4096 B</div>
            <div style={{ fontSize: "11px", color: "var(--accent-emerald)", fontWeight: 600 }}>Perms: RW-</div>
          </div>

          <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "2px" }}>
            <span style={{ fontSize: "10px", fontFamily: "var(--font-mono)", color: "var(--accent-amber)" }}>CSETBOUNDS (16B)</span>
            <ArrowRight size={18} color="var(--accent-amber)" />
          </div>

          {/* Step 2: Bounded Buffer */}
          <div style={{ background: "rgba(0,0,0,0.4)", border: "1px solid var(--border-subtle)", borderRadius: "8px", padding: "12px", minWidth: "180px", textAlign: "center" }}>
            <div style={{ fontSize: "12px", fontWeight: 700, color: "var(--accent-purple)", fontFamily: "var(--font-mono)" }}>c3 Bounded Buffer</div>
            <div style={{ fontSize: "11px", color: "var(--text-muted)", marginTop: "4px" }}>Base: 0x00000200</div>
            <div style={{ fontSize: "11px", color: "var(--text-muted)" }}>Length: 16 B</div>
            <div style={{ fontSize: "11px", color: "var(--accent-emerald)", fontWeight: 600 }}>Perms: RW-</div>
          </div>

          <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "2px" }}>
            <span style={{ fontSize: "10px", fontFamily: "var(--font-mono)", color: "var(--accent-magenta)" }}>CANDPERM (R-)</span>
            <ArrowRight size={18} color="var(--accent-magenta)" />
          </div>

          {/* Step 3: Attenuated Read-Only */}
          <div style={{ background: "rgba(0,0,0,0.4)", border: "1px solid var(--border-subtle)", borderRadius: "8px", padding: "12px", minWidth: "180px", textAlign: "center" }}>
            <div style={{ fontSize: "12px", fontWeight: 700, color: "var(--accent-magenta)", fontFamily: "var(--font-mono)" }}>c4 Read-Only Slice</div>
            <div style={{ fontSize: "11px", color: "var(--text-muted)", marginTop: "4px" }}>Base: 0x00000200</div>
            <div style={{ fontSize: "11px", color: "var(--text-muted)" }}>Length: 16 B</div>
            <div style={{ fontSize: "11px", color: "var(--accent-cyan)", fontWeight: 600 }}>Perms: R--</div>
          </div>
        </div>
      </div>

      {/* 2D Spatial Memory Map & Capability Inspector */}
      {state && <MemoryMapVisualizer capabilities={state.capabilities} />}

      {/* Capability Registers Grid */}
      {state && <CapabilityRegisterView capabilities={state.capabilities} />}

      {/* Integer GPR View */}
      {state && <RegisterFileView registers={state.gpr} />}
    </div>
  );
};
