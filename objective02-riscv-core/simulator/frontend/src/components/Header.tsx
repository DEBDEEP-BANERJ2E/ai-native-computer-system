import React from "react";
import { Cpu, RotateCcw, ShieldCheck, Zap } from "lucide-react";
import { SimulationState, ScenarioItem } from "../types";

interface HeaderProps {
  state: SimulationState | null;
  scenarios: ScenarioItem[];
  currentScenarioId: string;
  onSelectScenario: (id: string) => void;
  onReset: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  state,
  scenarios,
  currentScenarioId,
  onSelectScenario,
  onReset,
}) => {
  const isRtl = state?.engine === "rtl";

  return (
    <header className="app-header">
      <div className="header-brand">
        <span className="brand-badge">Objective 2</span>
        <div>
          <h1 className="brand-title">RVSecure Workbench</h1>
          <div className="brand-subtitle">
            RV32IM 5-Stage Processor, Telemetry & Capability Security Observatory
          </div>
        </div>
      </div>

      <div style={{ display: "flex", alignItems: "center", gap: "16px" }}>
        {/* Scenario Selector */}
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          <span style={{ fontSize: "12px", color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>
            SCENARIO:
          </span>
          <select
            value={currentScenarioId}
            onChange={(e) => onSelectScenario(e.target.value)}
            style={{
              background: "#1e293b",
              color: "#fff",
              border: "1px solid var(--border-subtle)",
              borderRadius: "6px",
              padding: "6px 12px",
              fontSize: "12px",
              fontFamily: "var(--font-mono)",
              outline: "none",
              cursor: "pointer",
            }}
          >
            {scenarios.map((sc) => (
              <option key={sc.id} value={sc.id}>
                [{sc.category}] {sc.title}
              </option>
            ))}
          </select>
        </div>

        {/* Engine Badge */}
        <div className={`header-engine-badge ${isRtl ? "engine-rtl" : "engine-reference"}`}>
          <div className="engine-dot" />
          <span>ENGINE: {isRtl ? "RTL / Verilator (Cycle-Accurate)" : "Python Reference (ISA Golden)"}</span>
        </div>

        {/* Reset Button */}
        <button className="btn btn-secondary" onClick={onReset} title="Reset Simulation">
          <RotateCcw size={14} />
          Reset
        </button>
      </div>
    </header>
  );
};
