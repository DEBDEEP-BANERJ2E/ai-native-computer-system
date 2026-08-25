import React, { useState } from "react";
import { GPRRegister } from "../types";

interface RegisterFileViewProps {
  registers: GPRRegister[];
}

export const RegisterFileView: React.FC<RegisterFileViewProps> = ({ registers }) => {
  const [viewHex, setViewHex] = useState(true);

  return (
    <div className="glass-panel">
      <div className="panel-header">
        <div className="panel-title">
          <span>General Purpose Registers (x0–x31)</span>
        </div>
        <div style={{ display: "flex", gap: "6px" }}>
          <button
            className={`btn btn-secondary ${viewHex ? "btn-primary" : ""}`}
            style={{ padding: "4px 8px", fontSize: "11px" }}
            onClick={() => setViewHex(true)}
          >
            HEX
          </button>
          <button
            className={`btn btn-secondary ${!viewHex ? "btn-primary" : ""}`}
            style={{ padding: "4px 8px", fontSize: "11px" }}
            onClick={() => setViewHex(false)}
          >
            DEC
          </button>
        </div>
      </div>

      <div className="rf-grid">
        {registers.map((r, idx) => {
          const isNonZero = r.val !== 0;
          return (
            <div key={r.reg} className={`rf-cell ${isNonZero ? "modified" : ""}`}>
              <div className="rf-name">
                <span style={{ fontWeight: 700, color: "var(--text-primary)" }}>{r.reg}</span>
                <span>{r.name}</span>
              </div>
              <div className="rf-val">
                {viewHex ? r.hex : r.val.toString()}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
