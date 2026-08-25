import React, { useState } from "react";
import { Activity, Play, SkipForward, RotateCcw, AlertTriangle, ShieldCheck } from "lucide-react";

export const IterativeDividerVisualizer: React.FC = () => {
  const [dividend, setDividend] = useState<number>(100);
  const [divisor, setDivisor] = useState<number>(7);
  const [step, setStep] = useState<number>(0);
  const [isRunning, setIsRunning] = useState<boolean>(false);

  // Compute intermediate state for step (0..32)
  const isZeroDiv = divisor === 0;
  const isOverflow = dividend === -2147483648 && divisor === -1;

  const expectedQuotient = isZeroDiv ? -1 : isOverflow ? -2147483648 : Math.trunc(dividend / divisor);
  const expectedRemainder = isZeroDiv ? dividend : isOverflow ? 0 : dividend % divisor;

  const currentRemaining = Math.max(0, 32 - step);
  const isDone = step >= 32;

  const handleStep = () => {
    if (step < 33) setStep((s) => s + 1);
  };

  const handleReset = () => {
    setStep(0);
    setIsRunning(false);
  };

  return (
    <div className="glass-panel" style={{ marginTop: "16px" }}>
      <div className="panel-header">
        <div className="panel-title">
          <Activity size={16} color="var(--accent-amber)" />
          <span>Interactive 33-Cycle Restoring Iterative Divider State Machine</span>
        </div>
        <div style={{ display: "flex", gap: "8px" }}>
          {isDone ? (
            <span className="stage-status-badge badge-valid">sDone (CYCLE 33)</span>
          ) : step > 0 ? (
            <span className="stage-status-badge badge-bubble">sCompute (ITERATION {step}/32)</span>
          ) : (
            <span className="stage-status-badge" style={{ background: "#1e293b", color: "#fff" }}>sIdle (READY)</span>
          )}
        </div>
      </div>

      {/* Operands & Stepper Controls */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "16px", marginBottom: "16px" }}>
        <div style={{ background: "rgba(0,0,0,0.3)", padding: "10px", borderRadius: "8px" }}>
          <span style={{ fontSize: "11px", color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>Dividend (rs1):</span>
          <input
            type="number"
            value={dividend}
            onChange={(e) => { setDividend(parseInt(e.target.value, 10) || 0); handleReset(); }}
            style={{ width: "100%", background: "#1e293b", color: "#fff", border: "1px solid var(--border-subtle)", borderRadius: "4px", padding: "6px", marginTop: "4px", fontFamily: "var(--font-mono)" }}
          />
        </div>

        <div style={{ background: "rgba(0,0,0,0.3)", padding: "10px", borderRadius: "8px" }}>
          <span style={{ fontSize: "11px", color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>Divisor (rs2):</span>
          <input
            type="number"
            value={divisor}
            onChange={(e) => { setDivisor(parseInt(e.target.value, 10) || 0); handleReset(); }}
            style={{ width: "100%", background: "#1e293b", color: "#fff", border: "1px solid var(--border-subtle)", borderRadius: "4px", padding: "6px", marginTop: "4px", fontFamily: "var(--font-mono)" }}
          />
        </div>

        <div style={{ display: "flex", alignItems: "flex-end", gap: "8px" }}>
          <button className="btn btn-primary" onClick={handleStep} disabled={isDone}>
            <SkipForward size={13} /> Step Cycle
          </button>
          <button className="btn btn-secondary" onClick={handleReset}>
            <RotateCcw size={13} /> Reset
          </button>
        </div>
      </div>

      {/* 33-Cycle Progress Countdown */}
      <div style={{ background: "rgba(0,0,0,0.4)", padding: "14px", borderRadius: "8px", marginBottom: "16px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", fontSize: "11px", fontFamily: "var(--font-mono)", marginBottom: "6px" }}>
          <span style={{ color: "var(--accent-amber)" }}>Hardware Countdown Signal: {currentRemaining} cycles remaining</span>
          <span style={{ color: isDone ? "var(--accent-emerald)" : "var(--text-muted)" }}>{step} / 32 Completed</span>
        </div>
        <div style={{ height: "8px", background: "#1e293b", borderRadius: "4px", overflow: "hidden" }}>
          <div
            style={{
              height: "100%",
              width: `${(Math.min(32, step) / 32) * 100}%`,
              background: isDone ? "var(--accent-emerald)" : "linear-gradient(90deg, var(--accent-amber), var(--accent-cyan))",
              transition: "width 0.15s ease",
            }}
          />
        </div>
      </div>

      {/* Live State Machine & Result Registers */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "12px", fontFamily: "var(--font-mono)", fontSize: "12px" }}>
        <div style={{ background: "rgba(0,0,0,0.3)", padding: "10px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
          <span style={{ color: "var(--text-muted)", fontSize: "10px" }}>STATE MACHINE:</span>
          <div style={{ color: isDone ? "var(--accent-emerald)" : step > 0 ? "var(--accent-amber)" : "var(--text-muted)", fontWeight: 700, fontSize: "14px" }}>
            {isDone ? "sDone (Result Ready)" : step > 0 ? `sCompute [${step}]` : "sIdle"}
          </div>
        </div>

        <div style={{ background: "rgba(0,0,0,0.3)", padding: "10px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
          <span style={{ color: "var(--text-muted)", fontSize: "10px" }}>DIV QUOTIENT (rd):</span>
          <div style={{ color: isDone ? "var(--accent-cyan)" : "var(--text-muted)", fontWeight: 700, fontSize: "14px" }}>
            {isDone ? expectedQuotient : "Computing..."}
          </div>
        </div>

        <div style={{ background: "rgba(0,0,0,0.3)", padding: "10px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
          <span style={{ color: "var(--text-muted)", fontSize: "10px" }}>REM REMAINDER (rd):</span>
          <div style={{ color: isDone ? "var(--accent-purple)" : "var(--text-muted)", fontWeight: 700, fontSize: "14px" }}>
            {isDone ? expectedRemainder : "Computing..."}
          </div>
        </div>
      </div>
    </div>
  );
};
