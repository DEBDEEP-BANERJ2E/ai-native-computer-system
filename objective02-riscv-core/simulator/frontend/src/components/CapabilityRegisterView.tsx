import React from "react";
import { Shield, ShieldAlert, Lock } from "lucide-react";
import { CapabilityRegister } from "../types";

interface CapabilityRegisterViewProps {
  capabilities: CapabilityRegister[];
}

export const CapabilityRegisterView: React.FC<CapabilityRegisterViewProps> = ({ capabilities }) => {
  return (
    <div className="glass-panel">
      <div className="panel-header">
        <div className="panel-title">
          <Shield size={16} color="var(--accent-purple)" />
          <span>Capability Register File (c0–c7) — 101-Bit Bounded Authority</span>
        </div>
      </div>

      <div className="cap-grid">
        {capabilities.map((cap) => {
          const isValid = cap.tag === 1;
          const isRoot = cap.index === 1 || cap.index === 2;
          const isNull = cap.index === 0;

          return (
            <div key={cap.name} className={`cap-card ${isValid ? "valid" : ""}`}>
              <div className="cap-card-header">
                <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                  <span style={{ color: isValid ? "var(--accent-purple)" : "var(--text-muted)", fontSize: "14px" }}>
                    {cap.name}
                  </span>
                  <span style={{ fontSize: "11px", color: "var(--text-muted)", fontWeight: 400 }}>
                    ({cap.role})
                  </span>
                </div>
                <div>
                  {isNull ? (
                    <span style={{ fontSize: "10px", color: "var(--text-muted)" }}>HARDWIRED NULL</span>
                  ) : isRoot ? (
                    <span style={{ fontSize: "10px", color: "var(--accent-cyan)", display: "flex", alignItems: "center", gap: "2px" }}>
                      <Lock size={10} /> ROOT
                    </span>
                  ) : isValid ? (
                    <span style={{ fontSize: "10px", color: "var(--accent-emerald)" }}>VALID</span>
                  ) : (
                    <span style={{ fontSize: "10px", color: "var(--text-muted)" }}>UNINITIALIZED</span>
                  )}
                </div>
              </div>

              <div className="cap-field-row">
                <span>Tag (Valid):</span>
                <span className="cap-val" style={{ color: isValid ? "var(--accent-emerald)" : "var(--text-muted)" }}>
                  {cap.tag}
                </span>
              </div>
              <div className="cap-field-row">
                <span>Base Address:</span>
                <span className="cap-val">0x{cap.base.toString(16).padStart(8, "0").toUpperCase()}</span>
              </div>
              <div className="cap-field-row">
                <span>Length (Bytes):</span>
                <span className="cap-val">{cap.length} (0x{cap.length.toString(16).toUpperCase()})</span>
              </div>
              <div className="cap-field-row">
                <span>Permissions:</span>
                <span className="cap-val" style={{ color: isValid ? "var(--accent-cyan)" : "var(--text-muted)" }}>
                  {cap.perms} (0b{cap.perms_raw.toString(2).padStart(3, "0")})
                </span>
              </div>
              <div className="cap-field-row">
                <span>Cursor Offset:</span>
                <span className="cap-val">0x{cap.offset.toString(16).toUpperCase()}</span>
              </div>
              <div className="cap-field-row" style={{ borderTop: "1px dashed rgba(255,255,255,0.06)", paddingTop: "4px" }}>
                <span>Effective Range:</span>
                <span className="cap-val" style={{ fontSize: "10px" }}>
                  {isValid
                    ? `0x${cap.base.toString(16).toUpperCase()} .. 0x${(cap.base + cap.length - (cap.length > 0 ? 1 : 0)).toString(16).toUpperCase()}`
                    : "None"}
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
