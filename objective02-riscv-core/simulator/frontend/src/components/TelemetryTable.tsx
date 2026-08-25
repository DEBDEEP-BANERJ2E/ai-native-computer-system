import React, { useState } from "react";
import { Activity, Cpu, Sliders, Send } from "lucide-react";
import { MMIORegisters } from "../types";

interface TelemetryTableProps {
  mmio: MMIORegisters;
  onWriteMMIO: (register: string, value: number) => void;
}

export const TelemetryTable: React.FC<TelemetryTableProps> = ({ mmio, onWriteMMIO }) => {
  const [selectedReg, setSelectedReg] = useState("SCHED_HINT");
  const [inputValue, setInputValue] = useState("3");

  const handleWrite = (e: React.FormEvent) => {
    e.preventDefault();
    const val = parseInt(inputValue, 10);
    if (!isNaN(val)) {
      onWriteMMIO(selectedReg, val);
    }
  };

  const o1Telemetry = [
    { addr: "0x80001000", name: "REV_ENERGY_ACC", val: mmio.REV_ENERGY_ACC, desc: "Reversible-operation activity accumulator" },
    { addr: "0x80001004", name: "CLA_SWITCHING", val: mmio.CLA_SWITCHING, desc: "Carry-lookahead adder bit transition counter" },
    { addr: "0x80001008", name: "MUL_THERMAL", val: mmio.MUL_THERMAL, desc: "Booth-Wallace multiplier switching proxy" },
    { addr: "0x8000100C", name: "EDP_CURRENT", val: mmio.EDP_CURRENT, desc: "Estimated Energy-Delay Product proxy" },
    { addr: "0x80001010", name: "EDP_CONFIG", val: mmio.EDP_CONFIG, desc: "Hardware delay scale constant (RO=1)" },
  ];

  const o2SystemMMIO = [
    { addr: "0x80002000", name: "BRANCH_CONFIDENCE", val: mmio.BRANCH_CONFIDENCE, desc: "Branch predictor confidence proxy (RO)" },
    { addr: "0x80002004", name: "PROCESS_BEHAVIOR_CLASS", val: mmio.PROCESS_BEHAVIOR_CLASS, desc: "OS thread behavioral classification (RW)", rw: true },
    { addr: "0x80002008", name: "SCHED_HINT", val: mmio.SCHED_HINT, desc: "Cross-layer scheduler core allocation hint (RW)", rw: true },
    { addr: "0x8000200C", name: "RETIRED_COUNT", val: mmio.RETIRED_COUNT, desc: "Hardware retired instruction counter (RO)" },
    { addr: "0x80002010", name: "BRANCH_TAKEN_COUNT", val: mmio.BRANCH_TAKEN_COUNT, desc: "Hardware branch taken counter (RO)" },
    { addr: "0x80002014", name: "LOAD_USE_STALL_COUNT", val: mmio.LOAD_USE_STALL_COUNT, desc: "Hardware load-use interlock stall counter (RO)" },
    { addr: "0x80002018", name: "DIV_BUSY_CYCLES", val: mmio.DIV_BUSY_CYCLES, desc: "Hardware iterative divider busy cycles (RO)" },
    { addr: "0x8000201C", name: "PIPELINE_STALL_COUNT", val: mmio.PIPELINE_STALL_COUNT, desc: "Total pipeline stall cycle accumulator (RO)" },
    { addr: "0x80002020", name: "LAST_COMMIT_PC", val: mmio.LAST_COMMIT_PC, desc: "PC of most recently retired instruction (RO)" },
    { addr: "0x80002024", name: "CURRENT_CONTEXT", val: mmio.CURRENT_CONTEXT, desc: "Active OS task/thread context ID (RW)", rw: true },
  ];

  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "20px" }}>
      {/* Objective 1 Telemetry Panel */}
      <div className="glass-panel">
        <div className="panel-header">
          <div className="panel-title">
            <Activity size={16} color="var(--accent-cyan)" />
            <span>Objective 1 Telemetry Subsystem (0x80001000)</span>
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
            {o1Telemetry.map((item) => (
              <tr key={item.name}>
                <td style={{ color: "var(--text-muted)" }}>{item.addr}</td>
                <td style={{ color: "var(--accent-cyan)", fontWeight: 600 }}>{item.name}</td>
                <td style={{ color: "#fff", fontWeight: 700 }}>
                  0x{item.val.toString(16).toUpperCase()} ({item.val})
                </td>
                <td style={{ color: "var(--text-secondary)", fontSize: "11px" }}>{item.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Objective 2 System MMIO & OS Control */}
      <div className="glass-panel">
        <div className="panel-header">
          <div className="panel-title">
            <Cpu size={16} color="var(--accent-purple)" />
            <span>Objective 2 System MMIO & OS Hints (0x80002000)</span>
          </div>
        </div>

        {/* Architectural OS Hint Control Box */}
        <form
          onSubmit={handleWrite}
          style={{
            display: "flex",
            gap: "8px",
            alignItems: "center",
            background: "rgba(0,0,0,0.3)",
            padding: "10px 12px",
            borderRadius: "8px",
            marginBottom: "16px",
            border: "1px solid var(--border-subtle)",
          }}
        >
          <Sliders size={14} color="var(--accent-amber)" />
          <span style={{ fontSize: "11px", color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>
            ARCHITECTURAL MMIO WRITE:
          </span>
          <select
            value={selectedReg}
            onChange={(e) => setSelectedReg(e.target.value)}
            style={{
              background: "#1e293b",
              color: "#fff",
              border: "1px solid var(--border-subtle)",
              borderRadius: "4px",
              padding: "4px 8px",
              fontSize: "11px",
              fontFamily: "var(--font-mono)",
            }}
          >
            <option value="PROCESS_BEHAVIOR_CLASS">PROCESS_BEHAVIOR_CLASS</option>
            <option value="SCHED_HINT">SCHED_HINT</option>
            <option value="CURRENT_CONTEXT">CURRENT_CONTEXT</option>
          </select>
          <input
            type="number"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            style={{
              width: "70px",
              background: "#1e293b",
              color: "#fff",
              border: "1px solid var(--border-subtle)",
              borderRadius: "4px",
              padding: "4px 8px",
              fontSize: "11px",
              fontFamily: "var(--font-mono)",
            }}
          />
          <button type="submit" className="btn btn-primary" style={{ padding: "4px 10px", fontSize: "11px" }}>
            <Send size={11} /> Write (SW)
          </button>
        </form>

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
            {o2SystemMMIO.map((item) => (
              <tr key={item.name}>
                <td style={{ color: "var(--text-muted)" }}>{item.addr}</td>
                <td style={{ color: item.rw ? "var(--accent-amber)" : "var(--accent-purple)", fontWeight: 600 }}>
                  {item.name}
                </td>
                <td style={{ color: "#fff", fontWeight: 700 }}>
                  0x{item.val.toString(16).toUpperCase()} ({item.val})
                </td>
                <td style={{ color: "var(--text-secondary)", fontSize: "11px" }}>{item.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};
