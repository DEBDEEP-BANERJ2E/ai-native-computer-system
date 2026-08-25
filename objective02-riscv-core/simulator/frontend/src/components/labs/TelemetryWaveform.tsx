import React from "react";
import { Activity, TrendingUp, Zap } from "lucide-react";
import { MMIORegisters } from "../../types";

interface TelemetryWaveformProps {
  mmio: MMIORegisters;
}

export const TelemetryWaveform: React.FC<TelemetryWaveformProps> = ({ mmio }) => {
  const claVal = mmio.CLA_SWITCHING;
  const mulVal = mmio.MUL_THERMAL;
  const edpVal = mmio.EDP_CURRENT;
  const retiredVal = mmio.RETIRED_COUNT;

  // Generate bar heights
  const maxVal = Math.max(10, claVal, mulVal, edpVal);
  const claHeight = (claVal / maxVal) * 100;
  const mulHeight = (mulVal / maxVal) * 100;
  const edpHeight = (edpVal / maxVal) * 100;

  return (
    <div className="glass-panel" style={{ marginTop: "20px" }}>
      <div className="panel-header">
        <div className="panel-title">
          <TrendingUp size={16} color="var(--accent-cyan)" />
          <span>Real-Time Hardware Telemetry Signals & Energy-Delay Product (EDP) Meter</span>
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: "20px", alignItems: "flex-end", height: "180px", background: "rgba(0,0,0,0.4)", padding: "16px", borderRadius: "10px", border: "1px solid var(--border-subtle)" }}>
        {/* CLA Switching Signal */}
        <div style={{ display: "flex", flexDirection: "column", alignItems: "center", height: "100%", justifyContent: "flex-end" }}>
          <span style={{ fontSize: "14px", fontWeight: 700, color: "var(--accent-cyan)", fontFamily: "var(--font-mono)", marginBottom: "4px" }}>
            {claVal}
          </span>
          <div style={{ width: "48px", height: `${Math.max(8, claHeight)}%`, background: "linear-gradient(180deg, var(--accent-cyan), #0284c7)", borderRadius: "4px 4px 0 0", transition: "height 0.3s ease" }} />
          <span style={{ fontSize: "11px", color: "var(--text-secondary)", fontFamily: "var(--font-mono)", marginTop: "8px" }}>
            CLA Switching
          </span>
        </div>

        {/* Multiplier Thermal Proxy */}
        <div style={{ display: "flex", flexDirection: "column", alignItems: "center", height: "100%", justifyContent: "flex-end" }}>
          <span style={{ fontSize: "14px", fontWeight: 700, color: "var(--accent-purple)", fontFamily: "var(--font-mono)", marginBottom: "4px" }}>
            {mulVal}
          </span>
          <div style={{ width: "48px", height: `${Math.max(8, mulHeight)}%`, background: "linear-gradient(180deg, var(--accent-purple), #7e22ce)", borderRadius: "4px 4px 0 0", transition: "height 0.3s ease" }} />
          <span style={{ fontSize: "11px", color: "var(--text-secondary)", fontFamily: "var(--font-mono)", marginTop: "8px" }}>
            MUL Thermal Proxy
          </span>
        </div>

        {/* EDP Current */}
        <div style={{ display: "flex", flexDirection: "column", alignItems: "center", height: "100%", justifyContent: "flex-end" }}>
          <span style={{ fontSize: "14px", fontWeight: 700, color: "var(--accent-amber)", fontFamily: "var(--font-mono)", marginBottom: "4px" }}>
            {edpVal}
          </span>
          <div style={{ width: "48px", height: `${Math.max(8, edpHeight)}%`, background: "linear-gradient(180deg, var(--accent-amber), #d97706)", borderRadius: "4px 4px 0 0", transition: "height 0.3s ease" }} />
          <span style={{ fontSize: "11px", color: "var(--text-secondary)", fontFamily: "var(--font-mono)", marginTop: "8px" }}>
            EDP Current Proxy
          </span>
        </div>
      </div>
    </div>
  );
};
