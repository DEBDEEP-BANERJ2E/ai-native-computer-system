import React, { useState } from "react";
import { Activity, Cpu, Sliders, Send, ShieldAlert, Sparkles, CheckCircle2, Zap } from "lucide-react";
import { MMIORegisters } from "../types";

interface TelemetryTableProps {
  mmio: MMIORegisters;
  onWriteMMIO: (register: string, value: number) => void;
}

export const TelemetryTable: React.FC<TelemetryTableProps> = ({ mmio, onWriteMMIO }) => {
  const [selectedReg, setSelectedReg] = useState("SCHED_HINT");
  const [inputValue, setInputValue] = useState("3");
  const [viewFormat, setViewFormat] = useState<"hex" | "dec">("hex");

  const handleWrite = (e: React.FormEvent) => {
    e.preventDefault();
    const val = parseInt(inputValue, 10);
    if (!isNaN(val)) {
      onWriteMMIO(selectedReg, val);
    }
  };

  const handleQuickPreset = (reg: string, val: number) => {
    setSelectedReg(reg);
    setInputValue(val.toString());
    onWriteMMIO(reg, val);
  };

  const formatVal = (num: number) => {
    if (viewFormat === "hex") {
      return `0x${(num >>> 0).toString(16).toUpperCase().padStart(8, "0")}`;
    }
    return num.toString();
  };

  const o1Telemetry = [
    { addr: "0x80001000", name: "REV_ENERGY_ACC", val: mmio.REV_ENERGY_ACC, desc: "Reversible-operation activity accumulator" },
    { addr: "0x80001004", name: "CLA_SWITCHING", val: mmio.CLA_SWITCHING, desc: "Carry-lookahead adder bit transition counter" },
    { addr: "0x80001008", name: "MUL_THERMAL", val: mmio.MUL_THERMAL, desc: "Booth-Wallace multiplier switching proxy" },
    { addr: "0x8000100C", name: "EDP_CURRENT", val: mmio.EDP_CURRENT, desc: "Estimated Energy-Delay Product proxy" },
    { addr: "0x80001010", name: "EDP_CONFIG", val: mmio.EDP_CONFIG, desc: "Hardware delay scale constant (RO=1)" },
  ];

  const o2Counters = [
    { addr: "0x80002000", name: "BRANCH_CONFIDENCE", val: mmio.BRANCH_CONFIDENCE, desc: "Branch predictor confidence proxy (RO)" },
    { addr: "0x8000200C", name: "RETIRED_COUNT", val: mmio.RETIRED_COUNT, desc: "Hardware retired instruction counter (RO)" },
    { addr: "0x80002010", name: "BRANCH_TAKEN_COUNT", val: mmio.BRANCH_TAKEN_COUNT, desc: "Hardware branch taken counter (RO)" },
    { addr: "0x80002014", name: "LOAD_USE_STALL_COUNT", val: mmio.LOAD_USE_STALL_COUNT, desc: "Hardware load-use interlock stall counter (RO)" },
    { addr: "0x80002018", name: "DIV_BUSY_CYCLES", val: mmio.DIV_BUSY_CYCLES, desc: "Hardware iterative divider busy cycles (RO)" },
    { addr: "0x8000201C", name: "PIPELINE_STALL_COUNT", val: mmio.PIPELINE_STALL_COUNT, desc: "Total pipeline stall cycle accumulator (RO)" },
    { addr: "0x80002020", name: "LAST_COMMIT_PC", val: mmio.LAST_COMMIT_PC, desc: "PC of most recently retired instruction (RO)" },
  ];

  const o2Context = [
    { addr: "0x80002004", name: "PROCESS_BEHAVIOR_CLASS", val: mmio.PROCESS_BEHAVIOR_CLASS, desc: "OS thread behavioral classification (RW)", rw: true },
    { addr: "0x80002008", name: "SCHED_HINT", val: mmio.SCHED_HINT, desc: "Cross-layer scheduler core allocation hint (RW)", rw: true },
    { addr: "0x80002024", name: "CURRENT_CONTEXT", val: mmio.CURRENT_CONTEXT, desc: "Active OS task/thread context ID (RW)", rw: true },
  ];

  const o2Traps = [
    { addr: "0x80002100", name: "TRAP_VECTOR", val: mmio.TRAP_VECTOR, desc: "Base address of OS trap vector table (0x80002100)" },
    { addr: "0x80002104", name: "TRAP_CONTROL", val: mmio.TRAP_CONTROL, desc: "Precise trap enable & nesting configuration (RW)", rw: true },
    { addr: "0x80002108", name: "TRAP_STATUS", val: mmio.TRAP_STATUS, desc: "Bit 0: TRAP_ACTIVE | Bit 1: DOUBLE_FAULT (W1C)", rw: true },
    { addr: "0x80002110", name: "TRAP_EPC", val: mmio.TRAP_EPC, desc: "Exception Program Counter (PC of faulting MEM instruction)" },
    { addr: "0x80002114", name: "TRAP_CAUSE", val: mmio.TRAP_CAUSE, desc: "Security violation cause code (0x10..0x14)" },
    { addr: "0x80002118", name: "TRAP_ADDR", val: mmio.TRAP_ADDR, desc: "Effective memory address that caused the violation" },
    { addr: "0x80002120", name: "TRAP_CONTEXT", val: mmio.TRAP_CONTEXT, desc: "OS process context ID active when violation occurred" },
  ];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
      {/* Interactive OS Scheduler Hint & Architectural Store Panel */}
      <div className="glass-panel" style={{ borderLeft: "4px solid var(--accent-amber)" }}>
        <div className="panel-header">
          <div className="panel-title">
            <Sliders size={16} color="var(--accent-amber)" />
            <span>Objective 3 OS Adaptive Scheduler Interface (Architectural SW Injection)</span>
          </div>

          <div style={{ display: "flex", gap: "8px" }}>
            <button
              className={`btn ${viewFormat === "hex" ? "btn-primary" : "btn-secondary"}`}
              style={{ fontSize: "11px", padding: "4px 8px" }}
              onClick={() => setViewFormat("hex")}
            >
              HEX View
            </button>
            <button
              className={`btn ${viewFormat === "dec" ? "btn-primary" : "btn-secondary"}`}
              style={{ fontSize: "11px", padding: "4px 8px" }}
              onClick={() => setViewFormat("dec")}
            >
              DEC View
            </button>
          </div>
        </div>

        {/* Quick Presets for OS Scheduler */}
        <div style={{ marginBottom: "14px" }}>
          <span style={{ fontSize: "11px", color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>
            QUICK SCHEDULER HINT PRESETS:
          </span>
          <div style={{ display: "flex", gap: "8px", marginTop: "6px", flexWrap: "wrap" }}>
            <button
              className="btn btn-secondary"
              style={{ fontSize: "11px", padding: "4px 10px" }}
              onClick={() => handleQuickPreset("SCHED_HINT", 1)}
            >
              🚀 High-Performance Turbo (HINT=1)
            </button>
            <button
              className="btn btn-secondary"
              style={{ fontSize: "11px", padding: "4px 10px" }}
              onClick={() => handleQuickPreset("SCHED_HINT", 2)}
            >
              🌱 Energy-Saver / Low-EDP (HINT=2)
            </button>
            <button
              className="btn btn-secondary"
              style={{ fontSize: "11px", padding: "4px 10px" }}
              onClick={() => handleQuickPreset("SCHED_HINT", 3)}
            >
              🛡️ Strict Capability Isolation (HINT=3)
            </button>
            <button
              className="btn btn-secondary"
              style={{ fontSize: "11px", padding: "4px 10px" }}
              onClick={() => handleQuickPreset("CURRENT_CONTEXT", 42)}
            >
              🔀 Switch Context ID (PID=42)
            </button>
          </div>
        </div>

        {/* Manual Architectural Store Form */}
        <form onSubmit={handleWrite} style={{ display: "flex", gap: "12px", alignItems: "center", flexWrap: "wrap" }}>
          <select
            value={selectedReg}
            onChange={(e) => setSelectedReg(e.target.value)}
            style={{
              background: "#1e293b",
              color: "#fff",
              border: "1px solid var(--border-subtle)",
              borderRadius: "6px",
              padding: "8px 12px",
              fontSize: "12px",
              fontFamily: "var(--font-mono)",
              outline: "none",
            }}
          >
            <option value="SCHED_HINT">SCHED_HINT (0x80002008)</option>
            <option value="PROCESS_BEHAVIOR_CLASS">PROCESS_BEHAVIOR_CLASS (0x80002004)</option>
            <option value="CURRENT_CONTEXT">CURRENT_CONTEXT (0x80002024)</option>
            <option value="TRAP_CONTROL">TRAP_CONTROL (0x80002104)</option>
          </select>

          <input
            type="number"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            placeholder="Value"
            style={{
              background: "#1e293b",
              color: "#fff",
              border: "1px solid var(--border-subtle)",
              borderRadius: "6px",
              padding: "8px 12px",
              fontSize: "12px",
              fontFamily: "var(--font-mono)",
              width: "120px",
              outline: "none",
            }}
          />

          <button type="submit" className="btn btn-primary" style={{ fontSize: "12px" }}>
            <Send size={13} /> Execute SW Store Instruction
          </button>
        </form>
      </div>

      {/* Structured Register Tables Grid */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "20px" }}>
        {/* Table 1: Objective 1 Telemetry */}
        <div className="glass-panel">
          <div className="panel-header">
            <div className="panel-title">
              <Activity size={16} color="var(--accent-cyan)" />
              <span>Objective 1 Telemetry (0x80001000)</span>
            </div>
          </div>

          <table className="data-table">
            <thead>
              <tr>
                <th>Address</th>
                <th>Register</th>
                <th>Live Value</th>
                <th>Description</th>
              </tr>
            </thead>
            <tbody>
              {o1Telemetry.map((r) => (
                <tr key={r.addr}>
                  <td style={{ color: "var(--text-code)" }}>{r.addr}</td>
                  <td style={{ fontWeight: 700, color: "#fff" }}>{r.name}</td>
                  <td style={{ color: "var(--accent-cyan)", fontWeight: 700 }}>{formatVal(r.val)}</td>
                  <td style={{ color: "var(--text-secondary)", fontSize: "11px" }}>{r.desc}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Table 2: Objective 2 Performance Counters */}
        <div className="glass-panel">
          <div className="panel-header">
            <div className="panel-title">
              <Cpu size={16} color="var(--accent-purple)" />
              <span>Objective 2 Hardware Counters (0x80002000)</span>
            </div>
          </div>

          <table className="data-table">
            <thead>
              <tr>
                <th>Address</th>
                <th>Register</th>
                <th>Live Value</th>
                <th>Description</th>
              </tr>
            </thead>
            <tbody>
              {o2Counters.map((r) => (
                <tr key={r.addr}>
                  <td style={{ color: "var(--text-code)" }}>{r.addr}</td>
                  <td style={{ fontWeight: 700, color: "#fff" }}>{r.name}</td>
                  <td style={{ color: "var(--accent-purple)", fontWeight: 700 }}>{formatVal(r.val)}</td>
                  <td style={{ color: "var(--text-secondary)", fontSize: "11px" }}>{r.desc}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Second Row: OS Context & Precise Trap Registers */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "20px" }}>
        {/* Table 3: OS Context & Scheduler */}
        <div className="glass-panel">
          <div className="panel-header">
            <div className="panel-title">
              <Sliders size={16} color="var(--accent-amber)" />
              <span>OS Context & Scheduler MMIO (RW)</span>
            </div>
          </div>

          <table className="data-table">
            <thead>
              <tr>
                <th>Address</th>
                <th>Register</th>
                <th>Live Value</th>
                <th>Description</th>
              </tr>
            </thead>
            <tbody>
              {o2Context.map((r) => (
                <tr key={r.addr}>
                  <td style={{ color: "var(--text-code)" }}>{r.addr}</td>
                  <td style={{ fontWeight: 700, color: "#fff" }}>{r.name}</td>
                  <td style={{ color: "var(--accent-amber)", fontWeight: 700 }}>{formatVal(r.val)}</td>
                  <td style={{ color: "var(--text-secondary)", fontSize: "11px" }}>{r.desc}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Table 4: Precise Trap Subsystem */}
        <div className="glass-panel">
          <div className="panel-header">
            <div className="panel-title">
              <ShieldAlert size={16} color="var(--accent-magenta)" />
              <span>Precise Trap MMIO Registers (0x80002100)</span>
            </div>
          </div>

          <table className="data-table">
            <thead>
              <tr>
                <th>Address</th>
                <th>Register</th>
                <th>Live Value</th>
                <th>Description</th>
              </tr>
            </thead>
            <tbody>
              {o2Traps.map((r) => (
                <tr key={r.addr}>
                  <td style={{ color: "var(--text-code)" }}>{r.addr}</td>
                  <td style={{ fontWeight: 700, color: "#fff" }}>{r.name}</td>
                  <td style={{ color: "var(--accent-magenta)", fontWeight: 700 }}>{formatVal(r.val)}</td>
                  <td style={{ color: "var(--text-secondary)", fontSize: "11px" }}>{r.desc}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
