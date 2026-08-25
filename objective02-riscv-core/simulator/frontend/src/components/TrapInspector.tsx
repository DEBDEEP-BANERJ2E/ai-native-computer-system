import React from "react";
import { ShieldAlert, AlertOctagon, Terminal } from "lucide-react";
import { MMIORegisters, HardwareSignals } from "../types";

interface TrapInspectorProps {
  mmio: MMIORegisters;
  signals: HardwareSignals;
}

export const TrapInspector: React.FC<TrapInspectorProps> = ({ mmio, signals }) => {
  const isTrapActive = signals.trapActive;
  const isDoubleFault = signals.doubleFault;
  const isSecAuditLogged = mmio.SEC_STATUS !== 0;

  const accessTypeMap: Record<number, string> = {
    0: "READ",
    1: "WRITE",
    2: "EXECUTE",
    3: "CAP_DERIVATION",
  };

  const reasonMap: Record<number, string> = {
    1: "TAG_VIOLATION (Uninitialized / Revoked)",
    2: "BOUNDS_VIOLATION (Spatial Overflow)",
    3: "INVALID_CAPABILITY (Malformed / Restricted)",
    4: "PERMISSION_VIOLATION (Privilege Escalation)",
    5: "OFFSET_VIOLATION (Negative / Underflow)",
  };

  const trapAccess = (mmio.TRAP_CAUSE >> 4) & 0x3;
  const trapReason = mmio.TRAP_CAUSE & 0xF;

  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "20px" }}>
      {/* Precise Trap Engine (0x80002114 - 0x80002130) */}
      <div className={`glass-panel ${isTrapActive ? "panel-active-trap" : ""}`} style={{ borderColor: isTrapActive ? "var(--accent-magenta)" : "var(--border-subtle)" }}>
        <div className="panel-header">
          <div className="panel-title">
            <ShieldAlert size={16} color="var(--accent-magenta)" />
            <span>Dedicated Architectural Precise Trap Engine</span>
          </div>
          <div style={{ display: "flex", gap: "8px" }}>
            {isTrapActive && (
              <span className="stage-status-badge badge-flush" style={{ animation: "pulse 1.5s infinite" }}>
                TRAP ACTIVE
              </span>
            )}
            {isDoubleFault && (
              <span className="stage-status-badge badge-flush">
                DOUBLE FAULT (W1C)
              </span>
            )}
          </div>
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: "10px", fontFamily: "var(--font-mono)", fontSize: "12px" }}>
          <div style={{ display: "flex", justifyContent: "space-between", padding: "6px 0", borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
            <span style={{ color: "var(--text-muted)" }}>TRAP_CONTROL (0x80002114):</span>
            <span style={{ color: mmio.TRAP_CONTROL & 1 ? "var(--accent-emerald)" : "var(--text-muted)" }}>
              {mmio.TRAP_CONTROL & 1 ? "ENABLE = 1 (Active Trapping)" : "ENABLE = 0 (Suppressed)"}
            </span>
          </div>

          <div style={{ display: "flex", justifyContent: "space-between", padding: "6px 0", borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
            <span style={{ color: "var(--text-muted)" }}>TRAP_STATUS (0x80002118):</span>
            <span>
              ACTIVE: {mmio.TRAP_STATUS & 1 ? "1" : "0"} | DOUBLE_FAULT: {(mmio.TRAP_STATUS >> 1) & 1 ? "1" : "0"}
            </span>
          </div>

          <div style={{ display: "flex", justifyContent: "space-between", padding: "6px 0", borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
            <span style={{ color: "var(--text-muted)" }}>TRAP_VECTOR (0x8000211C):</span>
            <span style={{ color: "var(--accent-cyan)" }}>0x{mmio.TRAP_VECTOR.toString(16).padStart(8, "0").toUpperCase()}</span>
          </div>

          <div style={{ display: "flex", justifyContent: "space-between", padding: "6px 0", borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
            <span style={{ color: "var(--text-muted)" }}>TRAP_EPC (0x80002120):</span>
            <span style={{ color: isTrapActive ? "var(--accent-magenta)" : "var(--text-primary)" }}>
              0x{mmio.TRAP_EPC.toString(16).padStart(8, "0").toUpperCase()}
            </span>
          </div>

          <div style={{ display: "flex", justifyContent: "space-between", padding: "6px 0", borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
            <span style={{ color: "var(--text-muted)" }}>TRAP_CAUSE (0x80002124):</span>
            <span>
              0x{mmio.TRAP_CAUSE.toString(16).toUpperCase()} ({accessTypeMap[trapAccess] || "NONE"} / {reasonMap[trapReason] || "NONE"})
            </span>
          </div>

          <div style={{ display: "flex", justifyContent: "space-between", padding: "6px 0", borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
            <span style={{ color: "var(--text-muted)" }}>TRAP_ADDR (0x80002128):</span>
            <span>0x{mmio.TRAP_ADDR.toString(16).padStart(8, "0").toUpperCase()}</span>
          </div>

          <div style={{ display: "flex", justifyContent: "space-between", padding: "6px 0" }}>
            <span style={{ color: "var(--text-muted)" }}>TRAP_CONTEXT (0x8000212C):</span>
            <span>0x{mmio.TRAP_CONTEXT.toString(16).toUpperCase()}</span>
          </div>
        </div>
      </div>

      {/* Sticky Security Violation Audit Logger (0x80002100) */}
      <div className="glass-panel">
        <div className="panel-header">
          <div className="panel-title">
            <AlertOctagon size={16} color="var(--accent-amber)" />
            <span>Sticky First-Event Security Audit Logger</span>
          </div>
          <div>
            {isSecAuditLogged ? (
              <span className="stage-status-badge badge-bubble">EVENT LOGGED</span>
            ) : (
              <span className="stage-status-badge badge-valid">CLEAR</span>
            )}
          </div>
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: "10px", fontFamily: "var(--font-mono)", fontSize: "12px" }}>
          <div style={{ display: "flex", justifyContent: "space-between", padding: "6px 0", borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
            <span style={{ color: "var(--text-muted)" }}>SEC_STATUS (0x80002100):</span>
            <span>{mmio.SEC_STATUS !== 0 ? "1 (LATCHED)" : "0 (IDLE)"}</span>
          </div>

          <div style={{ display: "flex", justifyContent: "space-between", padding: "6px 0", borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
            <span style={{ color: "var(--text-muted)" }}>SEC_PC (0x80002104):</span>
            <span>0x{mmio.SEC_PC.toString(16).padStart(8, "0").toUpperCase()}</span>
          </div>

          <div style={{ display: "flex", justifyContent: "space-between", padding: "6px 0", borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
            <span style={{ color: "var(--text-muted)" }}>SEC_ADDR (0x80002108):</span>
            <span>0x{mmio.SEC_ADDR.toString(16).padStart(8, "0").toUpperCase()}</span>
          </div>

          <div style={{ display: "flex", justifyContent: "space-between", padding: "6px 0", borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
            <span style={{ color: "var(--text-muted)" }}>SEC_INFO (0x8000210C):</span>
            <span>0x{mmio.SEC_INFO.toString(16).toUpperCase()}</span>
          </div>

          <div style={{ display: "flex", justifyContent: "space-between", padding: "6px 0" }}>
            <span style={{ color: "var(--text-muted)" }}>SEC_CONTEXT (0x80002110):</span>
            <span>0x{mmio.SEC_CONTEXT.toString(16).toUpperCase()}</span>
          </div>
        </div>
      </div>
    </div>
  );
};
