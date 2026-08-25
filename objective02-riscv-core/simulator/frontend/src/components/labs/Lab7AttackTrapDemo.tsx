import React from "react";
import { ShieldAlert, AlertOctagon, Play, SkipForward, RotateCcw, Flame, CheckCircle2 } from "lucide-react";
import { SimulationState } from "../../types";
import { PipelineDiagram } from "../PipelineDiagram";
import { TrapInspector } from "../TrapInspector";
import { CapabilityRegisterView } from "../CapabilityRegisterView";

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
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
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
            <span className="metric-pill-val" style={{ color: state?.signals.trapActive ? "var(--accent-magenta)" : "var(--accent-emerald)" }}>
              {state?.signals.trapActive ? "1 (IN HANDLER)" : "0 (NORMAL)"}
            </span>
          </div>
          <div className="metric-pill">
            <span className="metric-pill-label">TRAP EPC</span>
            <span className="metric-pill-val">0x{(state?.mmio.TRAP_EPC ?? 0).toString(16).padStart(8, "0").toUpperCase()}</span>
          </div>
          <div className="metric-pill">
            <span className="metric-pill-label">DOUBLE FAULT</span>
            <span className="metric-pill-val" style={{ color: state?.signals.doubleFault ? "var(--accent-red)" : "var(--text-muted)" }}>
              {state?.signals.doubleFault ? "LATCHED" : "NONE"}
            </span>
          </div>
        </div>
      </div>

      {/* Pipeline Visualizer */}
      <PipelineDiagram state={state} />

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
    </div>
  );
};
