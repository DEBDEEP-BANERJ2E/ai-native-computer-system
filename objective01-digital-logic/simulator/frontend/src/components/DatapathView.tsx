import { CircuitBoard, Info, Zap } from "lucide-react";
import type { Operation } from "../types";
import type { InspectableUnit } from "./CircuitInspectorModal";

interface DatapathViewProps {
  operation: Operation;
  result: number;
  onInspect: (unit: InspectableUnit) => void;
}

const UNIT_BY_OPCODE: Record<number, InspectableUnit> = {
  0: "hcla",
  1: "hcla",
  2: "logic",
  3: "logic",
  4: "logic",
  5: "shifter",
  6: "shifter",
  7: "shifter",
  8: "comparator",
  9: "comparator",
  10: "booth",
};

const UNIT_META: Record<
  InspectableUnit,
  { label: string; sub: string; color: string }
> = {
  hcla: { label: "Hierarchical CLA", sub: "8 × CLA4 blocks / 2-level lookahead", color: "#70cba5" },
  logic: { label: "Logic Unit", sub: "Parallel bitwise AND / OR / XOR fabric", color: "#8cb7db" },
  shifter: { label: "Barrel Shifter", sub: "5-stage log muxes: SLL / SRL / SRA", color: "#85cfd4" },
  comparator: { label: "Comparator", sub: "Signed SLT (N⊕V) · Unsigned SLTU (¬C)", color: "#bda5e0" },
  booth: { label: "Booth-Wallace", sub: "Radix-4 recode → 3:2 Wallace CSA tree", color: "#e1ad82" },
  telemetry: { label: "Telemetry MMIO", sub: "PopCount bit-flip activity & EDP proxy", color: "#8fd5b5" },
  reversible: { label: "Reversible Gates", sub: "Fredkin CSWAP & Toffoli CCNOT", color: "#9ee1c0" },
  ports: { label: "I/O Ports & Mux", sub: "32-bit registers & result multiplexer", color: "#e5b286" },
};

function hex32(value: number) {
  return (value >>> 0).toString(16).padStart(8, "0").toUpperCase();
}

export function DatapathView({ operation, result, onInspect }: DatapathViewProps) {
  const activeKey = UNIT_BY_OPCODE[operation.opcode] ?? "logic";
  const meta = UNIT_META[activeKey];
  const units: InspectableUnit[] = ["hcla", "logic", "shifter", "comparator", "booth"];
  const resultHex = hex32(result);

  return (
    <section className="panel datapath-panel">
      <div className="panel-heading">
        <div>
          <span className="kicker">RTL DATAPATH ARCHITECTURE</span>
          <h2>Live Execution Path &amp; Circuit Inspector</h2>
        </div>
        <div className="datapath-head-action">
          <span className="click-hint">
            <Info size={13} /> Click any block to view circuit diagram
          </span>
          <CircuitBoard size={20} className="heading-icon" />
        </div>
      </div>

      <div className="datapath-svg-wrapper">
        <svg viewBox="0 0 840 370" className="datapath-svg" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <pattern id="dpGrid" width="20" height="20" patternUnits="userSpaceOnUse">
              <path d="M 20 0 L 0 0 0 20" fill="none" stroke="#253534" strokeWidth="0.8" />
            </pattern>
            <linearGradient id="aluGrad" x1="0" x2="1">
              <stop offset="0%" stopColor="#182725" />
              <stop offset="100%" stopColor="#121e1c" />
            </linearGradient>
            <filter id="glowGreen" x="-20%" y="-20%" width="140%" height="140%">
              <feGaussianBlur stdDeviation="3" result="blur" />
              <feComposite in="SourceGraphic" in2="blur" operator="over" />
            </filter>
          </defs>

          {/* Background grid */}
          <rect x="0" y="0" width="840" height="370" fill="url(#dpGrid)" />

          {/* Input A Port */}
          <g className="clickable-block" onClick={() => onInspect("ports")} role="button" tabIndex={0}>
            <rect x="25" y="48" width="70" height="40" rx="3" fill="#172724" stroke="#5a8076" strokeWidth="1.5" />
            <text x="60" y="73" textAnchor="middle" fontSize="13" fontFamily="DM Mono" fontWeight="600" fill="#9ee1c0">A [31:0]</text>
            <title>Input Port A - Click to inspect I/O registers</title>
          </g>

          {/* Input B Port */}
          <g className="clickable-block" onClick={() => onInspect("ports")} role="button" tabIndex={0}>
            <rect x="25" y="278" width="70" height="40" rx="3" fill="#172724" stroke="#5a8076" strokeWidth="1.5" />
            <text x="60" y="303" textAnchor="middle" fontSize="13" fontFamily="DM Mono" fontWeight="600" fill="#9ee1c0">B [31:0]</text>
            <title>Input Port B - Click to inspect I/O registers</title>
          </g>

          {/* Active input bus lines */}
          <path
            d="M 95 68 L 220 68 L 220 180 L 250 180"
            fill="none"
            stroke="#70cba5"
            strokeWidth="2"
            strokeDasharray="6 4"
            className="animated-bus"
          />
          <path
            d="M 95 298 L 220 298 L 220 180 L 250 180"
            fill="none"
            stroke="#70cba5"
            strokeWidth="2"
            strokeDasharray="6 4"
            className="animated-bus"
          />
          <polygon points="244,175 254,180 244,185" fill="#70cba5" />

          {/* Main ALU Enclosure Box */}
          <g>
            <rect x="250" y="30" width="200" height="305" rx="4" fill="url(#aluGrad)" stroke="#4a6360" strokeWidth="1.5" />
            <text x="350" y="58" textAnchor="middle" fontSize="17" fontFamily="Space Grotesk" fill="#dcebe5" fontWeight="700">32-BIT ALU</text>
            <text x="350" y="74" textAnchor="middle" fontSize="9" fontFamily="DM Mono" fill="#728b88" letterSpacing="1.5">PARALLEL EXECUTION FABRIC</text>
            <line x1="250" y1="88" x2="450" y2="88" stroke="#334644" strokeWidth="1" />

            {/* Individual Functional Units inside ALU */}
            {units.map((key, idx) => {
              const isActive = key === activeKey;
              const y = 98 + idx * 42;
              const colors: Record<InspectableUnit, { stroke: string; fill: string; text: string; glow: string }> = {
                hcla: { stroke: isActive ? "#70cba5" : "#344343", fill: isActive ? "#1a362d" : "#14201e", text: isActive ? "#9ee1c0" : "#6c807e", glow: "#70cba5" },
                logic: { stroke: isActive ? "#7aa8ce" : "#344343", fill: isActive ? "#1a2c3a" : "#14201e", text: isActive ? "#9ec5e8" : "#6c807e", glow: "#7aa8ce" },
                shifter: { stroke: isActive ? "#6bbfc4" : "#344343", fill: isActive ? "#163336" : "#14201e", text: isActive ? "#9fe6ec" : "#6c807e", glow: "#6bbfc4" },
                comparator: { stroke: isActive ? "#a88fd3" : "#344343", fill: isActive ? "#2b203a" : "#14201e", text: isActive ? "#d3bef2" : "#6c807e", glow: "#a88fd3" },
                booth: { stroke: isActive ? "#d0966a" : "#344343", fill: isActive ? "#36261d" : "#14201e", text: isActive ? "#f0c29e" : "#6c807e", glow: "#d0966a" },
                telemetry: { stroke: "#344343", fill: "#14201e", text: "#6c807e", glow: "#8fd5b5" },
                reversible: { stroke: "#344343", fill: "#14201e", text: "#6c807e", glow: "#9ee1c0" },
                ports: { stroke: "#344343", fill: "#14201e", text: "#6c807e", glow: "#e5b286" },
              };
              const c = colors[key];
              return (
                <g
                  key={key}
                  className={`clickable-block ${isActive ? "dp-unit-active" : "dp-unit-idle"}`}
                  onClick={() => onInspect(key)}
                  role="button"
                  tabIndex={0}
                >
                  <rect
                    x="262"
                    y={y}
                    width="176"
                    height="34"
                    rx="3"
                    fill={c.fill}
                    stroke={c.stroke}
                    strokeWidth={isActive ? 2 : 1}
                    filter={isActive ? "url(#glowGreen)" : undefined}
                  />
                  {isActive && <rect x="262" y={y} width="5" height="34" fill={c.glow} rx="1" />}
                  <text
                    x="350"
                    y={y + 21}
                    textAnchor="middle"
                    fontSize="11"
                    fontFamily="DM Mono"
                    fontWeight={isActive ? "600" : "400"}
                    fill={c.text}
                  >
                    {UNIT_META[key].label}
                  </text>
                  <title>Click to inspect {UNIT_META[key].label} circuit diagram &amp; equations</title>
                </g>
              );
            })}

            <line x1="250" y1="305" x2="450" y2="305" stroke="#334644" strokeWidth="1" />
            <text x="350" y="322" textAnchor="middle" fontSize="9" fontFamily="DM Mono" fill="#8cb7a7" letterSpacing="0.8">
              OP 0x{operation.opcode.toString(16).toUpperCase()} · {operation.label} ACTIVE
            </text>
          </g>

          {/* Active execution bus connecting ALU to Multiplexer */}
          <line x1="450" y1="182" x2="550" y2="182" stroke="#70cba5" strokeWidth="2.5" strokeDasharray="4 2" className="animated-bus" />
          <polygon points="544,177 554,182 544,187" fill="#70cba5" />
          <text x="500" y="172" textAnchor="middle" fontSize="9" fontFamily="DM Mono" fill="#8cb7a7">32b ALU Bus</text>

          {/* Active Unit Card / Detail Inspector Trigger */}
          <g
            className="clickable-block active-card-block"
            onClick={() => onInspect(activeKey)}
            role="button"
            tabIndex={0}
          >
            <rect
              x="555"
              y="125"
              width="190"
              height="115"
              rx="4"
              fill="#182c27"
              stroke="#5eaf91"
              strokeWidth="1.5"
            />
            <rect x="555" y="125" width="190" height="24" fill="#13231f" rx="3" />
            <text x="650" y="141" textAnchor="middle" fontSize="10" fontFamily="DM Mono" fill="#70cba5" letterSpacing="1" fontWeight="600">
              ACTIVE EXECUTION UNIT
            </text>
            <text x="650" y="172" textAnchor="middle" fontSize="14" fontFamily="Space Grotesk" fontWeight="700" fill="#dcebe5">
              {meta.label}
            </text>
            <text x="650" y="192" textAnchor="middle" fontSize="8.5" fontFamily="DM Mono" fill="#8cb7a7">
              {meta.sub}
            </text>
            <rect x="580" y="206" width="140" height="20" rx="2" fill="#203d35" stroke="#4a806f" strokeWidth="0.8" />
            <text x="650" y="219" textAnchor="middle" fontSize="8.5" fontFamily="DM Mono" fill="#9ee1c0">
              🔍 Inspect Circuit
            </text>
            <title>Click to open full internal schematic and mathematical logic</title>
          </g>

          {/* Result Out Bus */}
          <line x1="745" y1="182" x2="790" y2="182" stroke="#e5b286" strokeWidth="2.5" />
          <polygon points="784,177 794,182 784,187" fill="#e5b286" />

          {/* Output Port */}
          <g className="clickable-block" onClick={() => onInspect("ports")} role="button" tabIndex={0}>
            <rect x="795" y="142" width="35" height="80" rx="3" fill="#1a1816" stroke="#946d4a" strokeWidth="1.5" />
            <text x="812" y="172" textAnchor="middle" fontSize="9" fontFamily="DM Mono" fill="#9e8a76" transform="rotate(90 812 172)">RESULT</text>
            <title>Output Result Register - Click to inspect</title>
          </g>

          {/* Bottom Result Strip */}
          <g className="clickable-block" onClick={() => onInspect("ports")} role="button" tabIndex={0}>
            <rect x="25" y="342" width="790" height="22" rx="2" fill="#0f1919" stroke="#293938" strokeWidth="1" />
            <text x="45" y="357" fontSize="9" fontFamily="DM Mono" fill="#728b88">LATCHED RESULT (HEX)</text>
            <text x="800" y="357" textAnchor="end" fontSize="11" fontFamily="DM Mono" fill="#eac095" fontWeight="600">
              0x{resultHex}
            </text>
          </g>
        </svg>
      </div>

      <div className="path-legend">
        <div className="legend-items">
          <span onClick={() => onInspect("hcla")} className="legend-clickable">
            <i className="legend-active" style={{ background: "#70cba5" }} /> Add/Sub (HCLA)
          </span>
          <span onClick={() => onInspect("logic")} className="legend-clickable">
            <i className="legend-active" style={{ background: "#8cb7db" }} /> Logic Fabric
          </span>
          <span onClick={() => onInspect("shifter")} className="legend-clickable">
            <i className="legend-active" style={{ background: "#85cfd4" }} /> Barrel Shifter
          </span>
          <span onClick={() => onInspect("comparator")} className="legend-clickable">
            <i className="legend-active" style={{ background: "#bda5e0" }} /> Comparator
          </span>
          <span onClick={() => onInspect("booth")} className="legend-clickable">
            <i className="legend-active" style={{ background: "#e1ad82" }} /> Booth-Wallace
          </span>
          <span onClick={() => onInspect("telemetry")} className="legend-clickable">
            <i className="legend-active" style={{ background: "#8fd5b5" }} /> Telemetry MMIO
          </span>
          <span onClick={() => onInspect("reversible")} className="legend-clickable">
            <i className="legend-active" style={{ background: "#9ee1c0" }} /> Reversible Gates
          </span>
        </div>
        <span className="path-op">
          <Zap size={12} className="text-amber" /> Persistent RTL Execution in <code>Objective1Subsystem.sv</code>
        </span>
      </div>
    </section>
  );
}
