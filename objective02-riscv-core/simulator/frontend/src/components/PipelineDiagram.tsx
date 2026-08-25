import React from "react";
import { ArrowRight, AlertTriangle, ShieldAlert, FastForward, Activity } from "lucide-react";
import { SimulationState } from "../types";

interface PipelineDiagramProps {
  state: SimulationState | null;
}

export const PipelineDiagram: React.FC<PipelineDiagramProps> = ({ state }) => {
  if (!state) return null;

  const { stages, signals } = state;

  return (
    <div style={{ marginBottom: "24px" }}>
      {/* Active Hazards & Forwarding Signals Banner */}
      <div
        style={{
          display: "flex",
          gap: "12px",
          marginBottom: "12px",
          flexWrap: "wrap",
        }}
      >
        {signals.forwardA > 0 && (
          <span
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "6px",
              padding: "4px 10px",
              borderRadius: "6px",
              background: "rgba(0, 245, 212, 0.15)",
              color: "var(--accent-cyan)",
              fontSize: "11px",
              fontFamily: "var(--font-mono)",
              border: "1px solid var(--border-active)",
            }}
          >
            <FastForward size={12} />
            ForwardA: {signals.forwardA === 2 ? "EX/MEM -> EX" : "MEM/WB -> EX"}
          </span>
        )}

        {signals.forwardB > 0 && (
          <span
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "6px",
              padding: "4px 10px",
              borderRadius: "6px",
              background: "rgba(0, 245, 212, 0.15)",
              color: "var(--accent-cyan)",
              fontSize: "11px",
              fontFamily: "var(--font-mono)",
              border: "1px solid var(--border-active)",
            }}
          >
            <FastForward size={12} />
            ForwardB: {signals.forwardB === 2 ? "EX/MEM -> EX" : "MEM/WB -> EX"}
          </span>
        )}

        {signals.loadUseHazard && (
          <span
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "6px",
              padding: "4px 10px",
              borderRadius: "6px",
              background: "rgba(245, 158, 11, 0.15)",
              color: "var(--accent-amber)",
              fontSize: "11px",
              fontFamily: "var(--font-mono)",
              border: "1px solid rgba(245, 158, 11, 0.4)",
            }}
          >
            <AlertTriangle size={12} />
            LOAD-USE HAZARD: IF/ID Stalled, ID/EX Bubble Inserted
          </span>
        )}

        {signals.branchTaken && (
          <span
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "6px",
              padding: "4px 10px",
              borderRadius: "6px",
              background: "rgba(239, 68, 68, 0.15)",
              color: "var(--accent-red)",
              fontSize: "11px",
              fontFamily: "var(--font-mono)",
              border: "1px solid rgba(239, 68, 68, 0.4)",
            }}
          >
            <AlertTriangle size={12} />
            BRANCH/JUMP TAKEN: 2-Cycle Flush (IF/ID & ID/EX) -&gt; Target: 0x{signals.redirectTarget.toString(16).toUpperCase()}
          </span>
        )}

        {signals.trapTaken && (
          <span
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "6px",
              padding: "4px 10px",
              borderRadius: "6px",
              background: "rgba(236, 72, 153, 0.2)",
              color: "var(--accent-magenta)",
              fontSize: "11px",
              fontFamily: "var(--font-mono)",
              border: "1px solid rgba(236, 72, 153, 0.5)",
              boxShadow: "0 0 12px rgba(236, 72, 153, 0.3)",
            }}
          >
            <ShieldAlert size={12} />
            PRECISE SECURITY TRAP: MEM Fault -&gt; Flush Younger -&gt; Redirect to 0x{signals.trapTarget.toString(16).toUpperCase()}
          </span>
        )}

        {signals.dividerBusy && (
          <span
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "6px",
              padding: "4px 10px",
              borderRadius: "6px",
              background: "rgba(168, 85, 247, 0.15)",
              color: "var(--accent-purple)",
              fontSize: "11px",
              fontFamily: "var(--font-mono)",
              border: "1px solid rgba(168, 85, 247, 0.4)",
            }}
          >
            <Activity size={12} />
            DIVIDER ACTIVE: Iteration Remaining {signals.dividerIterationRemaining} / 32
          </span>
        )}
      </div>

      {/* 5-Stage Visual Grid */}
      <div className="pipeline-container">
        {/* IF Stage */}
        <div className={`stage-card ${stages.IF.valid ? "active" : ""} ${signals.stallIF ? "stalled" : ""}`}>
          <div className="stage-header">
            <span className="stage-tag">IF</span>
            <span className="stage-pc">PC: 0x{stages.IF.pc.toString(16).padStart(8, "0").toUpperCase()}</span>
          </div>
          <div className="stage-inst">{stages.IF.mnemonic}</div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: "auto" }}>
            <span style={{ fontSize: "10px", color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>
              0x{stages.IF.instruction.toString(16).padStart(8, "0").toUpperCase()}
            </span>
            <span className={`stage-status-badge ${signals.stallIF ? "badge-bubble" : stages.IF.valid ? "badge-valid" : ""}`}>
              {signals.stallIF ? "STALL" : stages.IF.valid ? "ACTIVE" : "IDLE"}
            </span>
          </div>
        </div>

        {/* ID Stage */}
        <div className={`stage-card ${stages.ID.valid ? "active" : ""} ${signals.stallID ? "stalled" : ""} ${signals.flushIFID ? "flushed" : ""}`}>
          <div className="stage-header">
            <span className="stage-tag">ID</span>
            <span className="stage-pc">PC: 0x{stages.ID.pc.toString(16).padStart(8, "0").toUpperCase()}</span>
          </div>
          <div className="stage-inst">{stages.ID.mnemonic}</div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: "auto" }}>
            <span style={{ fontSize: "10px", color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>
              {signals.capHazard ? "CAP RAW STALL" : "Decode & RF"}
            </span>
            <span className={`stage-status-badge ${signals.flushIFID ? "badge-flush" : signals.stallID ? "badge-bubble" : stages.ID.valid ? "badge-valid" : ""}`}>
              {signals.flushIFID ? "FLUSHED" : signals.stallID ? "STALL" : stages.ID.valid ? "ACTIVE" : "IDLE"}
            </span>
          </div>
        </div>

        {/* EX Stage */}
        <div className={`stage-card ${stages.EX.valid ? "active" : ""} ${signals.flushIDEX ? "flushed" : ""}`}>
          <div className="stage-header">
            <span className="stage-tag">EX</span>
            <span className="stage-pc">PC: 0x{stages.EX.pc.toString(16).padStart(8, "0").toUpperCase()}</span>
          </div>
          <div className="stage-inst">{stages.EX.mnemonic}</div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: "auto" }}>
            <span style={{ fontSize: "10px", color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>
              ALU / MUL / DIV
            </span>
            <span className={`stage-status-badge ${signals.flushIDEX ? "badge-flush" : stages.EX.valid ? "badge-valid" : ""}`}>
              {signals.flushIDEX ? "BUBBLE" : stages.EX.valid ? "ACTIVE" : "IDLE"}
            </span>
          </div>
        </div>

        {/* MEM Stage */}
        <div className={`stage-card ${stages.MEM.valid ? "active" : ""} ${signals.trapTaken ? "flushed" : ""}`}>
          <div className="stage-header">
            <span className="stage-tag">MEM</span>
            <span className="stage-pc">PC: 0x{stages.MEM.pc.toString(16).padStart(8, "0").toUpperCase()}</span>
          </div>
          <div className="stage-inst">{stages.MEM.mnemonic}</div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: "auto" }}>
            <span style={{ fontSize: "10px", color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>
              RAM / CapChecker
            </span>
            <span className={`stage-status-badge ${signals.trapTaken ? "badge-flush" : stages.MEM.valid ? "badge-valid" : ""}`}>
              {signals.trapTaken ? "FAULT" : stages.MEM.valid ? "ACTIVE" : "IDLE"}
            </span>
          </div>
        </div>

        {/* WB Stage */}
        <div className={`stage-card ${stages.WB.valid ? "active" : ""}`}>
          <div className="stage-header">
            <span className="stage-tag">WB</span>
            <span className="stage-pc">PC: 0x{stages.WB.pc.toString(16).padStart(8, "0").toUpperCase()}</span>
          </div>
          <div className="stage-inst">{stages.WB.mnemonic}</div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: "auto" }}>
            <span style={{ fontSize: "10px", color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>
              Commit / RF Write
            </span>
            <span className={`stage-status-badge ${stages.WB.valid ? "badge-valid" : ""}`}>
              {stages.WB.valid ? "COMMIT" : "IDLE"}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
};
