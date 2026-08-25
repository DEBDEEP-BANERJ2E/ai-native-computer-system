import React, { useState } from "react";
import { Cpu, Activity, Play, SkipForward, RotateCcw, ShieldCheck, Image as ImageIcon, Layers, Table, CheckCircle2 } from "lucide-react";
import { SimulationState } from "../../types";
import { PipelineDiagram } from "../PipelineDiagram";
import { RegisterFileView } from "../RegisterFileView";
import { BoothWallaceVisualizer } from "./BoothWallaceVisualizer";
import { IterativeDividerVisualizer } from "./IterativeDividerVisualizer";

interface Lab4Props {
  state: SimulationState | null;
  onSelectScenario: (id: string) => void;
  onStep: () => void;
  onRun: () => void;
  onReset: () => void;
}

export const Lab4ArithmeticLab: React.FC<Lab4Props> = ({
  state,
  onSelectScenario,
  onStep,
  onRun,
  onReset,
}) => {
  const [viewMode, setViewMode] = useState<"interactive" | "schematic">("interactive");
  const dividerRemaining = state?.signals?.dividerIterationRemaining ?? 32;
  const dividerCompleted = 32 - dividerRemaining;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
      {/* Header Banner with View Mode Switcher */}
      <div className="glass-panel" style={{ padding: "16px 20px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "12px" }}>
          <div>
            <h2 style={{ fontSize: "18px", fontWeight: 700, color: "var(--accent-cyan)", display: "flex", alignItems: "center", gap: "8px" }}>
              <Cpu size={20} color="var(--accent-purple)" />
              Lab 4: RV32M Hardware Multiplier & Iterative Divider Microarchitecture
            </h2>
            <div style={{ fontSize: "12px", color: "var(--text-secondary)", marginTop: "4px" }}>
              34-bit Booth-Wallace tree multiplier (17 Radix-4 groups, 1-cycle latency) and 33-cycle non-restoring iterative divider with trap abort.
            </div>
          </div>

          <div style={{ display: "flex", gap: "8px" }}>
            <button
              className={`btn ${viewMode === "interactive" ? "btn-primary" : "btn-secondary"}`}
              style={{ fontSize: "12px", padding: "6px 12px" }}
              onClick={() => setViewMode("interactive")}
            >
              <Cpu size={14} /> Interactive Arithmetic Lab
            </button>
            <button
              className={`btn ${viewMode === "schematic" ? "btn-primary" : "btn-secondary"}`}
              style={{ fontSize: "12px", padding: "6px 12px" }}
              onClick={() => setViewMode("schematic")}
            >
              <ImageIcon size={14} /> Multiplier & Divider Blueprint
            </button>
          </div>
        </div>
      </div>

      {/* VIEW MODE 1: Interactive Steppers & Visualizers */}
      {viewMode === "interactive" && (
        <>
          {/* Preset Scenarios */}
          <div className="glass-panel" style={{ padding: "14px 20px" }}>
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: "12px" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                <Cpu size={16} color="var(--accent-purple)" />
                <span style={{ fontSize: "14px", fontWeight: 700 }}>RV32M ARITHMETIC PRESETS:</span>
              </div>

              <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
                <button
                  className="btn btn-secondary"
                  onClick={() => onSelectScenario("rv32m_full_matrix")}
                >
                  <Cpu size={13} color="var(--accent-cyan)" /> Full RV32M Matrix (MUL/DIV/REM)
                </button>
                <button
                  className="btn btn-secondary"
                  onClick={() => onSelectScenario("rv32m_div_corners")}
                >
                  <Activity size={13} color="var(--accent-amber)" /> Divider Corner Cases (Div-by-0 & Overflow)
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
                <span className="metric-pill-label">DIVIDER STATUS</span>
                <span className="metric-pill-val" style={{ color: state?.signals?.dividerBusy ? "var(--accent-amber)" : "var(--accent-emerald)" }}>
                  {state?.signals?.dividerBusy ? "COMPUTING" : "IDLE"}
                </span>
              </div>
              <div className="metric-pill">
                <span className="metric-pill-label">DIV ITERATIONS REMAINING</span>
                <span className="metric-pill-val">{dividerRemaining} / 32</span>
              </div>
              <div className="metric-pill">
                <span className="metric-pill-label">DIV BUSY CYCLES TOTAL</span>
                <span className="metric-pill-val">{state?.mmio?.DIV_BUSY_CYCLES ?? 0}</span>
              </div>
            </div>
          </div>

          {/* Pipeline Diagram */}
          <PipelineDiagram state={state} />

          {/* Multiplier & Divider Architecture Breakdown Cards */}
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "20px" }}>
            {/* Booth-Wallace Multiplier (34-bit, 17 groups) */}
            <div className="glass-panel">
              <div className="panel-header">
                <span className="panel-title">
                  <Cpu size={16} color="var(--accent-cyan)" />
                  Booth-Wallace Multiplier Architecture
                </span>
                <span style={{ fontSize: "10px", color: "var(--accent-cyan)", fontFamily: "var(--font-mono)" }}>
                  Objective 1 IP Reuse
                </span>
              </div>

              <div style={{ fontSize: "12px", color: "var(--text-secondary)", lineHeight: "1.6" }}>
                <p style={{ marginBottom: "8px" }}>
                  Objective 2 instantiates a parameterized <strong>BoothWallaceMultiplier(34)</strong> for high-throughput single-cycle multiplication:
                </p>
                <div style={{ background: "rgba(0,0,0,0.3)", padding: "12px", borderRadius: "8px", fontFamily: "var(--font-mono)", fontSize: "11px", display: "flex", flexDirection: "column", gap: "6px" }}>
                  <div>1. Operands sign/zero-extended to 34 bits (handles signed/unsigned mix).</div>
                  <div>2. <strong>17 Radix-4 Booth Groups</strong> generated (34 bits / 2 = 17 groups).</div>
                  <div>3. 17 signed/shifted partial products reduced via <strong>3:2 Wallace Tree CSA</strong>.</div>
                  <div>4. <strong>68-bit internal hardware product</strong> computed.</div>
                  <div>5. Low 64 bits extracted for RV32M instructions:</div>
                  <div style={{ color: "var(--accent-cyan)", paddingLeft: "12px" }}>
                    - <strong>MUL:</strong> Bits [31:0] (Lower 32-bit product)<br />
                    - <strong>MULH / MULHSU / MULHU:</strong> Bits [63:32] (Upper 32-bit product)
                  </div>
                </div>
              </div>
            </div>

            {/* 33-Cycle Restoring Iterative Divider */}
            <div className="glass-panel">
              <div className="panel-header">
                <span className="panel-title">
                  <Activity size={16} color="var(--accent-purple)" />
                  Iterative Restoring Divider (33 Cycles)
                </span>
                <span style={{ fontSize: "10px", color: "var(--accent-purple)", fontFamily: "var(--font-mono)" }}>
                  Multi-Cycle Extension
                </span>
              </div>

              <div style={{ fontSize: "12px", color: "var(--text-secondary)", lineHeight: "1.6" }}>
                <p style={{ marginBottom: "8px" }}>
                  The <strong>IterativeDivider</strong> module implements non-restoring/restoring division with state machine control:
                </p>

                {/* Iteration Progress Bar */}
                <div style={{ marginBottom: "12px", background: "rgba(0,0,0,0.4)", padding: "10px", borderRadius: "8px" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", fontSize: "11px", fontFamily: "var(--font-mono)", marginBottom: "4px" }}>
                    <span>Hardware Countdown (Remaining): {dividerRemaining}</span>
                    <span>Completed: {dividerCompleted} / 32</span>
                  </div>
                  <div style={{ height: "6px", background: "#1e293b", borderRadius: "3px", overflow: "hidden" }}>
                    <div
                      style={{
                        height: "100%",
                        width: `${(dividerCompleted / 32) * 100}%`,
                        background: "linear-gradient(90deg, var(--accent-purple), var(--accent-cyan))",
                        transition: "width 0.1s ease",
                      }}
                    />
                  </div>
                </div>

                <div style={{ background: "rgba(0,0,0,0.3)", padding: "12px", borderRadius: "8px", fontFamily: "var(--font-mono)", fontSize: "11px", display: "flex", flexDirection: "column", gap: "4px" }}>
                  <div>• <strong>sIdle:</strong> Latch dividend/divisor, initialize count = 32.</div>
                  <div>• <strong>sCompute (32 cycles):</strong> Shift-subtract restoring division step.</div>
                  <div>• <strong>sDone (1 cycle):</strong> Sign correction & result formatting (io.done=1).</div>
                  <div>• <strong>Trap Abort Port (io.kill):</strong> Immediately clears busy state on traps.</div>
                </div>
              </div>
            </div>
          </div>

          {/* Interactive 34-Bit Booth-Wallace Multiplier (17 Radix-4 Groups) */}
          <BoothWallaceVisualizer />

          {/* Interactive 33-Cycle Iterative Divider State Machine */}
          <IterativeDividerVisualizer />

          {/* GPR View */}
          {state && <RegisterFileView registers={state.gpr} />}
        </>
      )}

      {/* VIEW MODE 2: Complete Hardware Blueprint */}
      {viewMode === "schematic" && (
        <div className="glass-panel" style={{ textAlign: "center", background: "#0d1117" }}>
          <div className="panel-header" style={{ marginBottom: "12px" }}>
            <div className="panel-title">
              <ImageIcon size={18} color="var(--accent-cyan)" />
              <span>Lab 4: RV32M Arithmetic Hardware — Multiplier & Divider Microarchitecture Blueprint</span>
            </div>
            <span style={{ fontSize: "11px", color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>
              High-Resolution Hardware Blueprint
            </span>
          </div>

          <div style={{ padding: "8px", background: "#fff", borderRadius: "10px", overflow: "hidden", display: "inline-block", maxWidth: "100%", boxShadow: "0 8px 32px rgba(0,0,0,0.6)" }}>
            <img
              src="/lab4_rv32m_arithmetic_schematic.png"
              alt="Lab 4 RV32M Multiplier & Divider Microarchitecture Blueprint"
              style={{
                width: "100%",
                maxWidth: "1100px",
                height: "auto",
                display: "block",
                borderRadius: "6px",
              }}
            />
          </div>

          {/* Synthesis & Instruction Mapping Cards */}
          <div style={{ marginTop: "16px", display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: "12px", textAlign: "left", fontSize: "11px", fontFamily: "var(--font-mono)" }}>
            <div style={{ background: "rgba(0,0,0,0.4)", padding: "12px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
              <div style={{ color: "var(--accent-cyan)", fontWeight: 700, marginBottom: "6px" }}>
                1. Booth-Wallace Multiplier (34-bit)
              </div>
              <div style={{ color: "var(--text-secondary)", lineHeight: "1.5" }}>
                • <strong>Resource:</strong> 1,728 LUTs | 1,214 FFs | 0 DSPs<br />
                • <strong>Logic Levels:</strong> 6 (approx. ~6.1 ns post-route)<br />
                • <strong>Throughput:</strong> 1 result per clock cycle
              </div>
            </div>

            <div style={{ background: "rgba(0,0,0,0.4)", padding: "12px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
              <div style={{ color: "var(--accent-purple)", fontWeight: 700, marginBottom: "6px" }}>
                2. Iterative Restoring Divider
              </div>
              <div style={{ color: "var(--text-secondary)", lineHeight: "1.5" }}>
                • <strong>Resource:</strong> 391 LUTs | 268 FFs | 0 DSPs<br />
                • <strong>Latency:</strong> 32 cycles (deterministic timing)<br />
                • <strong>Critical Path:</strong> ~2.3 ns per iteration step
              </div>
            </div>

            <div style={{ background: "rgba(0,0,0,0.4)", padding: "12px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
              <div style={{ color: "var(--accent-emerald)", fontWeight: 700, marginBottom: "6px" }}>
                3. RV32M Instruction Mapping
              </div>
              <div style={{ color: "var(--text-secondary)", lineHeight: "1.5" }}>
                • <strong>MUL / MULH:</strong> rs1 × rs2 (lower & upper 32-bit)<br />
                • <strong>DIV / DIVU:</strong> rs1 ÷ rs2 (signed & unsigned quotient)<br />
                • <strong>REM / REMU:</strong> rs1 % rs2 (signed & unsigned remainder)
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
