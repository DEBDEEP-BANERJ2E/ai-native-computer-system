import React from "react";
import { ShieldAlert, Flame, Zap, CheckCircle2, XCircle, ArrowRight, ShieldCheck } from "lucide-react";
import { SimulationState } from "../../types";

interface AttackAnimationProps {
  state: SimulationState | null;
}

export const AttackAnimationCanvas: React.FC<AttackAnimationProps> = ({ state }) => {
  if (!state) return null;

  const isTrapActive = state.signals.trapActive;
  const trapEpc = state.mmio.TRAP_EPC;
  const trapCause = state.mmio.TRAP_CAUSE;
  const trapAddr = state.mmio.TRAP_ADDR;

  return (
    <div className="glass-panel" style={{ marginTop: "16px", border: `1px solid ${isTrapActive ? "var(--accent-magenta)" : "var(--border-subtle)"}` }}>
      <div className="panel-header">
        <div className="panel-title">
          <Flame size={18} color="var(--accent-red)" />
          <span>Real-Time Exploit Simulation & Hardware Trap Defense Visualizer</span>
        </div>
        <div>
          {isTrapActive ? (
            <span className="stage-status-badge badge-flush" style={{ animation: "pulseRed 1.5s infinite" }}>
              ATTACK DETECTED & CONTAINED
            </span>
          ) : (
            <span className="stage-status-badge badge-valid">HARDWARE READY</span>
          )}
        </div>
      </div>

      {/* Visual Attack & Containment Grid */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "20px" }}>
        {/* Left: Memory Slice Breach Visualizer */}
        <div style={{ background: "rgba(0,0,0,0.5)", padding: "16px", borderRadius: "10px", border: "1px solid var(--border-subtle)" }}>
          <div style={{ fontSize: "12px", fontWeight: 700, color: "var(--text-primary)", marginBottom: "8px" }}>
            Spatial Buffer & Exploit Target
          </div>

          <div style={{ height: "40px", background: "#1e293b", borderRadius: "6px", position: "relative", display: "flex", alignItems: "center", marginBottom: "12px", overflow: "hidden" }}>
            {/* Legal Bounded Range */}
            <div
              style={{
                width: "60%",
                height: "100%",
                background: "rgba(0, 245, 212, 0.15)",
                border: "2px solid var(--accent-cyan)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: "11px",
                fontWeight: 700,
                color: "var(--accent-cyan)",
                fontFamily: "var(--font-mono)",
              }}
            >
              c3 Buffer (16 Bytes) [0x200 .. 0x20F]
            </div>

            {/* Out-of-bounds write target */}
            <div
              style={{
                width: "40%",
                height: "100%",
                background: isTrapActive ? "rgba(239, 68, 68, 0.25)" : "transparent",
                border: isTrapActive ? "2px dashed var(--accent-red)" : "none",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: "11px",
                fontWeight: 700,
                color: "var(--accent-red)",
                fontFamily: "var(--font-mono)",
                animation: isTrapActive ? "pulseRed 1s infinite" : "none",
              }}
            >
              {isTrapActive ? "TARGET: 0x214 ❌ BLOCKED" : "Unallocated Space"}
            </div>
          </div>

          <div style={{ fontSize: "11px", fontFamily: "var(--font-mono)", color: "var(--text-secondary)", display: "flex", flexDirection: "column", gap: "4px" }}>
            <div>• <strong>Offending Instruction:</strong> CSW x7, 20(c3)</div>
            <div>• <strong>Capability Base + Length:</strong> 0x200 + 16 = 0x210</div>
            <div>• <strong>Requested Write Range:</strong> 0x214 .. 0x217 (Exceeds Top by 4 Bytes)</div>
            <div style={{ color: isTrapActive ? "var(--accent-emerald)" : "var(--text-muted)", fontWeight: 700, marginTop: "4px" }}>
              • <strong>RAM State:</strong> Unmodified (Write atomically suppressed in MEM)
            </div>
          </div>
        </div>

        {/* Right: Pipeline Flushing & Exception Dispatch */}
        <div style={{ background: "rgba(0,0,0,0.5)", padding: "16px", borderRadius: "10px", border: "1px solid var(--border-subtle)" }}>
          <div style={{ fontSize: "12px", fontWeight: 700, color: "var(--text-primary)", marginBottom: "8px" }}>
            Pipeline Squashing & Precise Trap Vectoring
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: "8px", fontFamily: "var(--font-mono)", fontSize: "11px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", padding: "6px 8px", background: isTrapActive ? "rgba(236, 72, 153, 0.15)" : "rgba(255,255,255,0.02)", borderRadius: "6px" }}>
              <span>1. MEM takePreciseTrap:</span>
              <span style={{ color: isTrapActive ? "var(--accent-magenta)" : "var(--text-muted)", fontWeight: 700 }}>
                {isTrapActive ? "ASSERTED (Cycle-Accurate)" : "0"}
              </span>
            </div>

            <div style={{ display: "flex", justifyContent: "space-between", padding: "6px 8px", background: isTrapActive ? "rgba(239, 68, 68, 0.15)" : "rgba(255,255,255,0.02)", borderRadius: "6px" }}>
              <span>2. Younger Stages (IF/ID, ID/EX):</span>
              <span style={{ color: isTrapActive ? "var(--accent-red)" : "var(--text-muted)", fontWeight: 700 }}>
                {isTrapActive ? "FLUSHED (2 Instructions Squashed)" : "Normal"}
              </span>
            </div>

            <div style={{ display: "flex", justifyContent: "space-between", padding: "6px 8px", background: isTrapActive ? "rgba(0, 245, 212, 0.15)" : "rgba(255,255,255,0.02)", borderRadius: "6px" }}>
              <span>3. Synchronous Capture:</span>
              <span style={{ color: isTrapActive ? "var(--accent-cyan)" : "var(--text-muted)", fontWeight: 700 }}>
                TRAP_EPC=0x{trapEpc.toString(16).toUpperCase()} | CAUSE=0x{trapCause.toString(16).toUpperCase()}
              </span>
            </div>

            <div style={{ display: "flex", justifyContent: "space-between", padding: "6px 8px", background: isTrapActive ? "rgba(16, 185, 129, 0.15)" : "rgba(255,255,255,0.02)", borderRadius: "6px" }}>
              <span>4. PC Redirection:</span>
              <span style={{ color: isTrapActive ? "var(--accent-emerald)" : "var(--text-muted)", fontWeight: 700 }}>
                PC -&gt; 0x{state.mmio.TRAP_VECTOR.toString(16).toUpperCase()} (Handler Entry)
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
