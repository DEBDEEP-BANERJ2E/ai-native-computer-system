import React, { useState } from "react";
import { Shield, Lock, Eye, ArrowRight, ShieldCheck, ShieldAlert } from "lucide-react";
import { CapabilityRegister } from "../../types";

interface MemoryMapVisualizerProps {
  capabilities: CapabilityRegister[];
}

export const MemoryMapVisualizer: React.FC<MemoryMapVisualizerProps> = ({ capabilities }) => {
  const [selectedCapIndex, setSelectedCapIndex] = useState<number>(3); // c3 by default
  const selectedCap = capabilities.find((c) => c.index === selectedCapIndex) || capabilities[1];

  const totalRamSize = 4096;

  return (
    <div className="glass-panel" style={{ marginTop: "16px" }}>
      <div className="panel-header">
        <div className="panel-title">
          <Shield size={16} color="var(--accent-purple)" />
          <span>2D Spatial Memory Map & Bounded Capability Inspector</span>
        </div>
        <div style={{ display: "flex", gap: "6px" }}>
          {capabilities.map((c) => (
            <button
              key={c.name}
              className={`btn ${selectedCapIndex === c.index ? "btn-primary" : "btn-secondary"}`}
              style={{ padding: "3px 8px", fontSize: "11px" }}
              onClick={() => setSelectedCapIndex(c.index)}
            >
              {c.name}
            </button>
          ))}
        </div>
      </div>

      {/* 2D Memory Space Bar */}
      <div style={{ background: "rgba(0,0,0,0.5)", padding: "16px", borderRadius: "8px", marginBottom: "16px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", fontSize: "11px", fontFamily: "var(--font-mono)", color: "var(--text-muted)", marginBottom: "6px" }}>
          <span>RAM BASE: 0x00000000</span>
          <span style={{ color: "var(--accent-cyan)", fontWeight: 700 }}>
            {selectedCap.name}: [0x{selectedCap.base.toString(16).toUpperCase()} .. 0x{(selectedCap.base + selectedCap.length).toString(16).toUpperCase()}]
          </span>
          <span>RAM TOP: 0x00001000 (4KB)</span>
        </div>

        <div style={{ height: "32px", background: "#1e293b", borderRadius: "6px", position: "relative", overflow: "hidden", border: "1px solid var(--border-subtle)" }}>
          {/* Base RAM fill */}
          <div style={{ position: "absolute", inset: 0, background: "rgba(255,255,255,0.02)" }} />

          {/* Selected Capability Slice Box */}
          {selectedCap.tag === 1 && (
            <div
              style={{
                position: "absolute",
                left: `${Math.min(95, (selectedCap.base / totalRamSize) * 100)}%`,
                width: `${Math.max(2, (selectedCap.length / totalRamSize) * 100)}%`,
                height: "100%",
                background: selectedCap.index === 1 ? "rgba(0, 245, 212, 0.25)" : "linear-gradient(90deg, rgba(168, 85, 247, 0.5), rgba(236, 72, 153, 0.5))",
                border: "2px solid var(--accent-purple)",
                boxShadow: "0 0 12px rgba(168, 85, 247, 0.4)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: "10px",
                fontWeight: 700,
                color: "#fff",
                fontFamily: "var(--font-mono)",
                transition: "all 0.3s ease",
              }}
            >
              {selectedCap.name} ({selectedCap.length}B)
            </div>
          )}
        </div>
      </div>

      {/* Selected Capability Details Card */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "12px", fontFamily: "var(--font-mono)", fontSize: "11px" }}>
        <div style={{ background: "rgba(0,0,0,0.3)", padding: "10px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
          <span style={{ color: "var(--text-muted)" }}>TAG VALIDITY:</span>
          <div style={{ color: selectedCap.tag === 1 ? "var(--accent-emerald)" : "var(--text-muted)", fontWeight: 700, fontSize: "13px", marginTop: "2px" }}>
            {selectedCap.tag === 1 ? "TAG = 1 (VALID)" : "TAG = 0 (INVALID)"}
          </div>
        </div>

        <div style={{ background: "rgba(0,0,0,0.3)", padding: "10px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
          <span style={{ color: "var(--text-muted)" }}>BASE & LENGTH:</span>
          <div style={{ color: "var(--accent-cyan)", fontWeight: 700, fontSize: "13px", marginTop: "2px" }}>
            0x{selectedCap.base.toString(16).toUpperCase()} ({selectedCap.length} B)
          </div>
        </div>

        <div style={{ background: "rgba(0,0,0,0.3)", padding: "10px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
          <span style={{ color: "var(--text-muted)" }}>PERMISSIONS:</span>
          <div style={{ color: "var(--accent-magenta)", fontWeight: 700, fontSize: "13px", marginTop: "2px" }}>
            {selectedCap.perms} (0b{selectedCap.perms_raw.toString(2).padStart(3, "0")})
          </div>
        </div>

        <div style={{ background: "rgba(0,0,0,0.3)", padding: "10px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
          <span style={{ color: "var(--text-muted)" }}>CURSOR OFFSET:</span>
          <div style={{ color: "#fff", fontWeight: 700, fontSize: "13px", marginTop: "2px" }}>
            0x{selectedCap.offset.toString(16).toUpperCase()}
          </div>
        </div>
      </div>
    </div>
  );
};
