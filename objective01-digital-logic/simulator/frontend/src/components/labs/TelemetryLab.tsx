import { useState } from "react";
import { Activity, Gauge, Zap, Thermometer, ShieldCheck, Cpu } from "lucide-react";
import type { TelemetryData } from "../../types";

interface Props {
  telemetry: TelemetryData;
}

const MMIO_REGS = [
  {
    address: "0x80001000",
    name: "REV_ENERGY_ACC",
    key: "rev_energy_acc",
    label: "Reversible Energy Accumulator",
    role: "Reserved counter for Landauer-bound zero-entropy reversible operations. Currently fixed to 0 in Objective1Subsystem.",
    color: "amber",
  },
  {
    address: "0x80001004",
    name: "CLA_SWITCHING",
    key: "cla_switching",
    label: "Hierarchical CLA Switching Activity Proxy",
    role: "Result-bus Hamming distance accumulator: PopCount(Result_t ⊕ Result_{t-1}) accumulated during ADD and SUB operations.",
    color: "green",
  },
  {
    address: "0x80001008",
    name: "MUL_THERMAL",
    key: "mul_thermal",
    label: "Multiplier Activity / Thermal Proxy",
    role: "Result-bus Hamming distance accumulator specifically active during Booth-Wallace MUL operations.",
    color: "orange",
  },
  {
    address: "0x8000100C",
    name: "EDP_CURRENT",
    key: "edp_current",
    label: "Energy-Delay Product (EDP) Activity Proxy",
    role: "Combinational output evaluating: (REV_ENERGY_ACC + CLA_SWITCHING + MUL_THERMAL) × EDP_CONFIG.",
    color: "blue",
  },
  {
    address: "0x80001010",
    name: "EDP_CONFIG",
    key: "edp_config",
    label: "EDP Calibration Constant",
    role: "Scaling calibration constant fixed to 1 in current read-only hardware prototype.",
    color: "slate",
  },
];

export function TelemetryLab({ telemetry }: Props) {
  const [selectedAddress, setSelectedAddress] = useState("0x80001000");
  const [activeSchematic, setActiveSchematic] = useState<"clean" | "yosys">("clean");

  const selectedReg = MMIO_REGS.find((r) => r.address === selectedAddress) || MMIO_REGS[0];
  const regValue = telemetry[selectedReg.key] ?? 0;

  return (
    <div className="lab-container telemetry-lab">
      <div className="lab-header-banner">
        <div>
          <span className="lab-tag">LAB 06 · HARDWARE TELEMETRY &amp; EDP</span>
          <h2>Memory-Mapped Telemetry Registers &amp; Result-Bus Activity Proxies</h2>
        </div>
        <p className="lab-desc">
          Live inspection of the 5 memory-mapped telemetry registers starting at base address <code>0x80001000</code>. Tracks result-bus Hamming distance switching metrics (<code>PopCount(Result_t ^ Result_t-1)</code>) and calculates the Energy-Delay Product (EDP) activity proxy.
        </p>
      </div>

      {/* MMIO Register Bus Probe Station */}
      <section className="panel mmio-station-panel">
        <div className="panel-heading">
          <div>
            <span className="kicker">MMIO ADDRESS DECODER &amp; READ BUS</span>
            <h3>Telemetry Address Space (0x80001000 - 0x80001010)</h3>
          </div>
          <span className="mmio-badge">Read-Only Memory-Mapped Interface</span>
        </div>

        <div className="mmio-regs-grid">
          {MMIO_REGS.map((reg) => {
            const isSelected = reg.address === selectedAddress;
            const val = telemetry[reg.key] ?? 0;
            return (
              <button
                key={reg.address}
                className={`mmio-reg-card ${isSelected ? "selected" : ""}`}
                onClick={() => setSelectedAddress(reg.address)}
              >
                <div className="reg-card-top">
                  <code className="reg-addr">{reg.address}</code>
                  <span className="reg-val-hex">0x{(val >>> 0).toString(16).padStart(8, "0").toUpperCase()}</span>
                </div>
                <strong className="reg-name">{reg.name}</strong>
                <span className="reg-val-dec">Value: {val.toLocaleString()} counts</span>
              </button>
            );
          })}
        </div>

        {/* Selected Register Detail Inspector */}
        <div className="selected-reg-inspector">
          <div className="inspector-top">
            <span className="probe-active-dot" />
            <span>Active Bus Probe: <strong>{selectedReg.address}</strong> &rarr; <code>{selectedReg.name}</code></span>
            <span className="inspector-hex">0x{(regValue >>> 0).toString(16).padStart(8, "0").toUpperCase()}</span>
          </div>
          <p className="inspector-role">{selectedReg.role}</p>
        </div>
      </section>

      {/* EDP Proxy Mathematical Breakdown */}
      <div className="architecture-comparison-grid">
        <section className="panel arch-card">
          <div className="panel-heading">
            <div>
              <span className="kicker">ACTIVITY METRIC FORMULA</span>
              <h3>Hamming Distance Switching Logic</h3>
            </div>
            <span className="complexity-badge good">PopCount Tree</span>
          </div>

          <p className="arch-desc">
            In <code>TelemetryBlock.scala</code>, switching activity is measured by comparing consecutive 32-bit ALU output vectors:
          </p>

          <div className="code-block">
            <code>changedBits := PopCount(io.result ^ previousResult)</code>
            <code>activity := Mux(io.operationValid, changedBits, 0.U)</code>
            <code>claSwitching := claSwitching + Mux(io.claActive, activity, 0.U)</code>
            <code>multiplierThermal := multiplierThermal + Mux(io.multiplierActive, activity, 0.U)</code>
          </div>
        </section>

        <section className="panel arch-card">
          <div className="panel-heading">
            <div>
              <span className="kicker">EDP EVALUATION</span>
              <h3>Energy-Delay Product Activity Proxy</h3>
            </div>
            <span className="complexity-badge good">EDP_CONFIG = 1</span>
          </div>

          <p className="arch-desc">
            The hardware computes the Energy-Delay Product proxy by scaling the combined energy proxy:
          </p>

          <div className="code-block">
            <code>energyProxy := revEnergyAcc + claSwitching + mulThermal</code>
            <code>edpProxy := energyProxy * edpConfig  // Config fixed to 1</code>
            <code>EDP_CURRENT := {telemetry.edp_current ?? 0}</code>
          </div>
        </section>
      </div>

      {/* Schematic Viewer Section */}
      <section className="panel lab-schematic-section">
        <div className="panel-heading">
          <div>
            <span className="kicker">SCHEMATICS &amp; NETLIST</span>
            <h3>TelemetryBlock Schematics</h3>
          </div>
          <div className="schematic-view-toggles">
            <button
              className={`cat-pill ${activeSchematic === "clean" ? "active" : ""}`}
              onClick={() => setActiveSchematic("clean")}
            >
              Telemetry 1:1 Diagram
            </button>
            <button
              className={`cat-pill ${activeSchematic === "yosys" ? "active" : ""}`}
              onClick={() => setActiveSchematic("yosys")}
            >
              Yosys Netlist (TelemetryBlock)
            </button>
          </div>
        </div>

        <div className="lab-schematic-display">
          <div className="schematic-image-wrap">
            <img
              src={
                activeSchematic === "clean"
                  ? "/schematics/telemetry_clean_schematic.jpg"
                  : "/schematics/TelemetryBlock.svg"
              }
              alt="Telemetry Schematic"
              className="lab-schematic-img"
            />
          </div>
        </div>
      </section>
    </div>
  );
}
