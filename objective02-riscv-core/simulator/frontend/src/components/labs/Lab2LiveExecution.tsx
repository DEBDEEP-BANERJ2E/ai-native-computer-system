import React, { useState } from "react";
import { Play, SkipForward, RotateCcw, Code, Cpu, Layers } from "lucide-react";
import { SimulationState, ScenarioItem } from "../../types";
import { RegisterFileView } from "../RegisterFileView";
import { PipelineDiagram } from "../PipelineDiagram";

interface Lab2Props {
  state: SimulationState | null;
  scenarios: ScenarioItem[];
  currentScenarioId: string;
  onSelectScenario: (id: string) => void;
  onStep: () => void;
  onRun: () => void;
  onReset: () => void;
  onLoadCustomAssembly: (code: string) => void;
}

export const Lab2LiveExecution: React.FC<Lab2Props> = ({
  state,
  scenarios,
  currentScenarioId,
  onSelectScenario,
  onStep,
  onRun,
  onReset,
  onLoadCustomAssembly,
}) => {
  const currentScenario = scenarios.find((s) => s.id === currentScenarioId);
  const [editorCode, setEditorCode] = useState(currentScenario ? currentScenario.assembly : "");

  const handleScenarioChange = (id: string) => {
    onSelectScenario(id);
    const sc = scenarios.find((s) => s.id === id);
    if (sc) setEditorCode(sc.assembly);
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
      {/* Control Bar */}
      <div className="control-bar">
        <div className="control-group">
          <button className="btn btn-primary" onClick={onStep} title="Step 1 Clock Cycle">
            <SkipForward size={14} /> Step 1 Cycle
          </button>
          <button className="btn btn-purple" onClick={onRun} title="Run to Completion">
            <Play size={14} /> Run Program
          </button>
          <button className="btn btn-secondary" onClick={onReset} title="Reset Pipeline">
            <RotateCcw size={14} /> Reset
          </button>
        </div>

        <div className="control-group">
          <div className="metric-pill">
            <span className="metric-pill-label">CLOCK CYCLES</span>
            <span className="metric-pill-val">{state?.cycle_count ?? 0}</span>
          </div>
          <div className="metric-pill">
            <span className="metric-pill-label">RETIRED INST</span>
            <span className="metric-pill-val">{state?.instruction_count ?? 0}</span>
          </div>
          <div className="metric-pill">
            <span className="metric-pill-label">CPI</span>
            <span className="metric-pill-val">{state?.cpi ?? 0}</span>
          </div>
          <div className="metric-pill">
            <span className="metric-pill-label">PC</span>
            <span className="metric-pill-val">0x{(state?.pc ?? 0).toString(16).padStart(8, "0").toUpperCase()}</span>
          </div>
        </div>
      </div>

      {/* 5-Stage Live Pipeline */}
      <PipelineDiagram state={state} />

      {/* Main Split: Code Editor vs Register File */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1.2fr", gap: "20px" }}>
        {/* Code Editor Panel */}
        <div className="glass-panel">
          <div className="panel-header">
            <span className="panel-title">
              <Code size={16} color="var(--accent-cyan)" />
              Assembly Program Editor
            </span>
            <button
              className="btn btn-primary"
              style={{ padding: "4px 10px", fontSize: "11px" }}
              onClick={() => onLoadCustomAssembly(editorCode)}
            >
              Load into Engine
            </button>
          </div>

          <textarea
            className="code-editor"
            value={editorCode}
            onChange={(e) => setEditorCode(e.target.value)}
            placeholder="Enter RV32IM assembly here..."
            spellCheck={false}
          />

          <div style={{ marginTop: "12px", fontSize: "11px", color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>
            * Note: Custom assembly executes in Python Reference Mode. Predefined presets run on the cycle-accurate RTL model.
          </div>
        </div>

        {/* GPR Register File */}
        {state && <RegisterFileView registers={state.gpr} />}
      </div>
    </div>
  );
};
