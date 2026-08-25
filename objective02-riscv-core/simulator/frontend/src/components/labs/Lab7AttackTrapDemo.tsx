import React, { useState } from "react";
import { ShieldAlert, AlertOctagon, Play, SkipForward, RotateCcw, Flame, CheckCircle2, Image as ImageIcon, Cpu, ShieldCheck, Zap } from "lucide-react";
import { SimulationState } from "../../types";
import { PipelineDiagram } from "../PipelineDiagram";
import { TrapInspector } from "../TrapInspector";
import { CapabilityRegisterView } from "../CapabilityRegisterView";
import { AttackAnimationCanvas } from "./AttackAnimationCanvas";

interface Lab7Props {
  state: SimulationState | null;
  onSelectScenario: (id: string) => void;
  onStep: () => void;
  onRun: () => void;
  onReset: () => void;
}

export const Lab7AttackTrapDemo: React.FC<Lab7Props> = ({
  state,
  onSelectScenario,
  onStep,
  onRun,
  onReset,
}) => {
  const [viewMode, setViewMode] = useState<"interactive" | "schematic">("interactive");

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
      {/* Header Banner with View Mode Switcher */}
      <div className="glass-panel" style={{ padding: "16px 20px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "12px" }}>
          <div>
            <h2 style={{ fontSize: "18px", fontWeight: 700, color: "var(--accent-red)", display: "flex", alignItems: "center", gap: "8px" }}>
              <Flame size={20} color="var(--accent-red)" />
              Lab 7: Attack Scenarios, Hardware Containment & Precise Security Traps
            </h2>
            <div style={{ fontSize: "12px", color: "var(--text-secondary)", marginTop: "4px" }}>
              Cycle-accurate hardware exception model: MEM violation detection, atomic writeback suppression, younger stage squashing, and handler vectoring.
            </div>
          </div>

          <div style={{ display: "flex", gap: "8px" }}>
            <button
              className={`btn ${viewMode === "interactive" ? "btn-danger" : "btn-secondary"}`}
              style={{ fontSize: "12px", padding: "6px 12px" }}
              onClick={() => setViewMode("interactive")}
            >
              <Zap size={14} /> Interactive Attack Launchpad
            </button>
            <button
              className={`btn ${viewMode === "schematic" ? "btn-primary" : "btn-secondary"}`}
              style={{ fontSize: "12px", padding: "6px 12px" }}
              onClick={() => setViewMode("schematic")}
            >
              <ImageIcon size={14} /> Precise Trap Architecture Blueprint
            </button>
          </div>
        </div>
      </div>

      {/* VIEW MODE 1: Interactive Attack Launchpad & Real-Time Defense Canvas */}
      {viewMode === "interactive" && (
        <>
          {/* Attack Launchpad Presets */}
          <div className="glass-panel" style={{ padding: "14px 20px" }}>
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: "12px" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                <Flame size={16} color="var(--accent-red)" />
                <span style={{ fontSize: "14px", fontWeight: 700 }}>LIVE ATTACK LAUNCHPAD:</span>
              </div>

              <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
                <button
                  className="btn btn-danger"
                  onClick={() => onSelectScenario("attack_buffer_overflow")}
                >
                  ▶ 1. Buffer Overflow Containment
                </button>
                <button
                  className="btn btn-danger"
                  onClick={() => onSelectScenario("attack_readonly_violation")}
                >
                  ▶ 2. Write to Read-Only
                </button>
                <button
                  className="btn btn-danger"
                  onClick={() => onSelectScenario("attack_null_deref")}
                >
                  ▶ 3. NULL Capability Dereference
                </button>
                <button
                  className="btn btn-danger"
                  onClick={() => onSelectScenario("attack_trap_vs_div")}
                >
                  ▶ 4. Trap vs Active Divider Kill
                </button>
                <button
                  className="btn btn-danger"
                  onClick={() => onSelectScenario("attack_bounds_retry")}
                >
                  ▶ 5. Bounds Expansion & Retry
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
                <Play size={14} /> Execute Attack Scenario
              </button>
              <button className="btn btn-secondary" onClick={onReset}>
                <RotateCcw size={14} /> Reset
              </button>
            </div>

            <div className="control-group">
              <div className="metric-pill">
                <span className="metric-pill-label">TRAP ACTIVE</span>
                <span className="metric-pill-val" style={{ color: state?.signals?.trapActive ? "var(--accent-magenta)" : "var(--accent-emerald)" }}>
                  {state?.signals?.trapActive ? "1 (IN HANDLER)" : "0 (NORMAL)"}
                </span>
              </div>
              <div className="metric-pill">
                <span className="metric-pill-label">TRAP EPC</span>
                <span className="metric-pill-val">0x{(state?.mmio?.TRAP_EPC ?? 0).toString(16).padStart(8, "0").toUpperCase()}</span>
              </div>
              <div className="metric-pill">
                <span className="metric-pill-label">DOUBLE FAULT</span>
                <span className="metric-pill-val" style={{ color: state?.signals?.doubleFault ? "var(--accent-red)" : "var(--text-muted)" }}>
                  {state?.signals?.doubleFault ? "LATCHED" : "NONE"}
                </span>
              </div>
            </div>
          </div>

          {/* Pipeline Visualizer */}
          <PipelineDiagram state={state} />

          {/* Real-Time Attack & Trap Defense Visualizer */}
          <AttackAnimationCanvas state={state} />

          {/* Trap & Security Inspector */}
          {state && <TrapInspector mmio={state.mmio} signals={state.signals} />}

          {/* Attack Lifecycle Explanation Card */}
          <div className="glass-panel" style={{ background: "rgba(15, 23, 42, 0.75)" }}>
            <div className="panel-header">
              <span className="panel-title">
                <ShieldAlert size={16} color="var(--accent-magenta)" />
                Hardware Precise Trap Execution Lifecycle
              </span>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: "12px", fontSize: "11px", fontFamily: "var(--font-mono)", textAlign: "center" }}>
              <div style={{ background: "rgba(0,0,0,0.3)", padding: "10px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
                <div style={{ color: "var(--accent-red)", fontWeight: 700, marginBottom: "4px" }}>1. MEM VIOLATION</div>
                <div style={{ color: "var(--text-muted)" }}>CapabilityChecker denies Tag/Bounds/Perm.</div>
              </div>
              <div style={{ background: "rgba(0,0,0,0.3)", padding: "10px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
                <div style={{ color: "var(--accent-amber)", fontWeight: 700, marginBottom: "4px" }}>2. ATOMIC KILL</div>
                <div style={{ color: "var(--text-muted)" }}>memEnterWbValid=0. RAM/GPR write cancelled.</div>
              </div>
              <div style={{ background: "rgba(0,0,0,0.3)", padding: "10px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
                <div style={{ color: "var(--accent-magenta)", fontWeight: 700, marginBottom: "4px" }}>3. FLUSH YOUNGER</div>
                <div style={{ color: "var(--text-muted)" }}>IF/ID & ID/EX flushed. Divider aborted.</div>
              </div>
              <div style={{ background: "rgba(0,0,0,0.3)", padding: "10px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
                <div style={{ color: "var(--accent-cyan)", fontWeight: 700, marginBottom: "4px" }}>4. VECTOR & LOG</div>
                <div style={{ color: "var(--text-muted)" }}>PC -&gt; TRAP_VECTOR. Capture TRAP_* & SEC_*.</div>
              </div>
              <div style={{ background: "rgba(0,0,0,0.3)", padding: "10px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
                <div style={{ color: "var(--accent-emerald)", fontWeight: 700, marginBottom: "4px" }}>5. TRAP_RETURN</div>
                <div style={{ color: "var(--text-muted)" }}>Handler writes 0x80002130 -&gt; Jump to EPC.</div>
              </div>
            </div>
          </div>

          {/* Capability Registers */}
          {state && <CapabilityRegisterView capabilities={state.capabilities} />}
        </>
      )}

      {/* VIEW MODE 2: Complete Hardware Blueprint */}
      {viewMode === "schematic" && (
        <div className="glass-panel" style={{ textAlign: "center", background: "#0d1117" }}>
          <div className="panel-header" style={{ marginBottom: "12px" }}>
            <div className="panel-title">
              <ImageIcon size={18} color="var(--accent-red)" />
              <span>Lab 7: Attack Scenarios & Precise Security Traps Architecture Blueprint</span>
            </div>
            <span style={{ fontSize: "11px", color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>
              High-Resolution Hardware Blueprint
            </span>
          </div>

          <div style={{ padding: "8px", background: "#fff", borderRadius: "10px", overflow: "hidden", display: "inline-block", maxWidth: "100%", boxShadow: "0 8px 32px rgba(0,0,0,0.6)" }}>
            <img
              src="/lab7_attack_trap_schematic.png"
              alt="Lab 7 Attack Scenarios & Precise Security Traps Architecture Blueprint"
              style={{
                width: "100%",
                maxWidth: "1100px",
                height: "auto",
                display: "block",
                borderRadius: "6px",
              }}
            />
          </div>

          {/* Precise Trap Principles & Cause Codes Summary */}
          <div style={{ marginTop: "16px", display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: "12px", textAlign: "left", fontSize: "11px", fontFamily: "var(--font-mono)" }}>
            <div style={{ background: "rgba(0,0,0,0.4)", padding: "12px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
              <div style={{ color: "var(--accent-red)", fontWeight: 700, marginBottom: "6px" }}>
                1. Precise Exception Guarantees
              </div>
              <div style={{ color: "var(--text-secondary)", lineHeight: "1.5" }}>
                • <strong>In-Order Retirement:</strong> All older instructions commit<br />
                • <strong>Atomic Suppression:</strong> Faulting inst has zero side effects<br />
                • <strong>Pipeline Squash:</strong> All younger instructions discarded<br />
                • <strong>Restartable State:</strong> System resumes cleanly from EPC
              </div>
            </div>

            <div style={{ background: "rgba(0,0,0,0.4)", padding: "12px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
              <div style={{ color: "var(--accent-amber)", fontWeight: 700, marginBottom: "6px" }}>
                2. Trap Cause Code Mapping
              </div>
              <div style={{ color: "var(--text-secondary)", lineHeight: "1.5" }}>
                • <strong>0x10:</strong> TRAP_CAP_BOUNDS (Spatial overflow)<br />
                • <strong>0x11:</strong> TRAP_CAP_PERM (Read-only / Exec violation)<br />
                • <strong>0x12:</strong> TRAP_CAP_DERIV (Illegal derivation rule)<br />
                • <strong>0x13:</strong> TRAP_CAP_SEAL (Sealed access without unseal)<br />
                • <strong>0x14:</strong> TRAP_CAP_INVALID (Tag = 0 NULL pointer)
              </div>
            </div>

            <div style={{ background: "rgba(0,0,0,0.4)", padding: "12px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
              <div style={{ color: "var(--accent-emerald)", fontWeight: 700, marginBottom: "6px" }}>
                3. Hardware Control Registers
              </div>
              <div style={{ color: "var(--text-secondary)", lineHeight: "1.5" }}>
                • <strong>TRAP_EPC:</strong> Captured faulting program counter<br />
                • <strong>TRAP_CAUSE:</strong> Detailed violation code<br />
                • <strong>TRAP_ADDR:</strong> Target memory address of violation<br />
                • <strong>DOUBLE_FAULT:</strong> Set-over-W1C nested fault latch
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
