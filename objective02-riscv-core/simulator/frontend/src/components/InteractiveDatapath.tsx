import React, { useState } from "react";
import { Cpu, Shield, Activity, Zap, Layers, Info, X, Radio, Eye, CheckCircle2, AlertTriangle, Flame } from "lucide-react";
import { SimulationState } from "../types";

interface InteractiveDatapathProps {
  state: SimulationState | null;
  onSelectModule?: (moduleId: string) => void;
}

export const InteractiveDatapath: React.FC<InteractiveDatapathProps> = ({ state, onSelectModule }) => {
  const [selectedBlock, setSelectedBlock] = useState<string>("ALU");

  if (!state) return null;

  const { stages, signals, mmio, gpr, capabilities } = state;
  const isBranchTaken = signals.branchTaken;
  const isTrapTaken = signals.trapTaken || signals.trapActive;
  const isForwardA = signals.forwardA > 0;
  const isForwardB = signals.forwardB > 0;
  const isDividerActive = signals.dividerBusy;

  const handleBlockClick = (id: string) => {
    setSelectedBlock(id);
    if (onSelectModule) onSelectModule(id);
  };

  // Decode instruction bitfields for probe
  const exInst = stages.EX.instruction;
  const exOpcode = exInst & 0x7F;
  const exFunct3 = (exInst >> 12) & 0x07;
  const exFunct7 = (exInst >> 25) & 0x7F;
  const exRd = (exInst >> 7) & 0x1F;
  const exRs1 = (exInst >> 15) & 0x1F;
  const exRs2 = (exInst >> 20) & 0x1F;

  const idInst = stages.ID.instruction;
  const idOpcode = idInst & 0x7F;
  const idFunct3 = (idInst >> 12) & 0x07;
  const idFunct7 = (idInst >> 25) & 0x7F;
  const idRd = (idInst >> 7) & 0x1F;
  const idRs1 = (idInst >> 15) & 0x1F;
  const idRs2 = (idInst >> 20) & 0x1F;

  const rs1Val = gpr.find((r) => r.reg === `x${exRs1}`)?.val ?? 0;
  const rs2Val = gpr.find((r) => r.reg === `x${exRs2}`)?.val ?? 0;
  const capC1 = capabilities.find((c) => c.name === "c1") || capabilities[1];
  const capC3 = capabilities.find((c) => c.name === "c3") || capabilities[3];

  // Helper to render binary string with color groups
  const renderBitStrip = (val: number, bits: number = 32) => {
    const binStr = (val >>> 0).toString(2).padStart(bits, "0");
    return (
      <div style={{ display: "flex", gap: "2px", overflowX: "auto", padding: "4px 0" }}>
        {binStr.split("").map((b, idx) => (
          <div
            key={idx}
            style={{
              width: "12px",
              height: "20px",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontSize: "10px",
              fontFamily: "var(--font-mono)",
              fontWeight: 700,
              borderRadius: "2px",
              background: b === "1" ? "rgba(0, 245, 212, 0.25)" : "rgba(255, 255, 255, 0.05)",
              color: b === "1" ? "var(--accent-cyan)" : "var(--text-muted)",
              border: b === "1" ? "1px solid var(--accent-cyan)" : "1px solid rgba(255,255,255,0.05)",
            }}
          >
            {b}
          </div>
        ))}
      </div>
    );
  };

  return (
    <div className="glass-panel" style={{ padding: "16px", marginBottom: "20px", position: "relative" }}>
      <div className="panel-header" style={{ marginBottom: "8px" }}>
        <div className="panel-title">
          <Cpu size={18} color="var(--accent-cyan)" />
          <span>Interactive RV32IM 5-Stage Hardware Datapath & Circuit Probe</span>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          <span className="stage-status-badge badge-valid" style={{ fontSize: "10px" }}>
            PROBE ACTIVE: {selectedBlock}
          </span>
          <span style={{ fontSize: "11px", color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>
            CLICK ANY BLOCK TO PROBE WIRES
          </span>
        </div>
      </div>

      {/* SVG Datapath Canvas */}
      <div className="datapath-canvas" style={{ minHeight: "420px", display: "flex", justifyContent: "center" }}>
        <svg viewBox="0 0 1120 440" style={{ width: "100%", height: "auto", maxHeight: "460px" }}>
          <defs>
            {/* Glow Filters */}
            <filter id="glow-cyan" x="-20%" y="-20%" width="140%" height="140%">
              <feGaussianBlur stdDeviation="3" result="blur" />
              <feComposite in="SourceGraphic" in2="blur" operator="over" />
            </filter>
            <filter id="glow-magenta" x="-20%" y="-20%" width="140%" height="140%">
              <feGaussianBlur stdDeviation="4" result="blur" />
              <feComposite in="SourceGraphic" in2="blur" operator="over" />
            </filter>
            <filter id="glow-amber" x="-20%" y="-20%" width="140%" height="140%">
              <feGaussianBlur stdDeviation="3" result="blur" />
              <feComposite in="SourceGraphic" in2="blur" operator="over" />
            </filter>
            <filter id="glow-emerald" x="-20%" y="-20%" width="140%" height="140%">
              <feGaussianBlur stdDeviation="3" result="blur" />
              <feComposite in="SourceGraphic" in2="blur" operator="over" />
            </filter>

            {/* Arrow Markers */}
            <marker id="arrow-cyan" viewBox="0 0 10 10" refX="5" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="#00f5d4" />
            </marker>
            <marker id="arrow-magenta" viewBox="0 0 10 10" refX="5" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="#ec4899" />
            </marker>
            <marker id="arrow-amber" viewBox="0 0 10 10" refX="5" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="#f59e0b" />
            </marker>
            <marker id="arrow-emerald" viewBox="0 0 10 10" refX="5" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="#10b981" />
            </marker>
          </defs>

          {/* Background Grid Accent Lines */}
          <rect x="0" y="0" width="1120" height="440" fill="none" />

          {/* Stage Columns */}
          <rect x="15" y="15" width="195" height="410" rx="8" fill="rgba(255,255,255,0.01)" stroke="rgba(255,255,255,0.04)" />
          <text x="30" y="38" fill="var(--text-muted)" fontSize="11" fontWeight="800" fontFamily="var(--font-mono)">STAGE 1: IF</text>

          <rect x="235" y="15" width="220" height="410" rx="8" fill="rgba(255,255,255,0.01)" stroke="rgba(255,255,255,0.04)" />
          <text x="250" y="38" fill="var(--text-muted)" fontSize="11" fontWeight="800" fontFamily="var(--font-mono)">STAGE 2: ID</text>

          <rect x="480" y="15" width="225" height="410" rx="8" fill="rgba(255,255,255,0.01)" stroke="rgba(255,255,255,0.04)" />
          <text x="495" y="38" fill="var(--text-muted)" fontSize="11" fontWeight="800" fontFamily="var(--font-mono)">STAGE 3: EX</text>

          <rect x="730" y="15" width="210" height="410" rx="8" fill="rgba(255,255,255,0.01)" stroke="rgba(255,255,255,0.04)" />
          <text x="745" y="38" fill="var(--text-muted)" fontSize="11" fontWeight="800" fontFamily="var(--font-mono)">STAGE 4: MEM</text>

          <rect x="965" y="15" width="140" height="410" rx="8" fill="rgba(255,255,255,0.01)" stroke="rgba(255,255,255,0.04)" />
          <text x="980" y="38" fill="var(--text-muted)" fontSize="11" fontWeight="800" fontFamily="var(--font-mono)">STAGE 5: WB</text>

          {/* Pipeline Registers */}
          <rect x="215" y="45" width="14" height="360" rx="4" fill="#1e293b" stroke={signals.flushIFID ? "var(--accent-red)" : signals.stallID ? "var(--accent-amber)" : "var(--border-subtle)"} />
          <text x="222" y="230" fill="#fff" fontSize="9" fontWeight="700" fontFamily="var(--font-mono)" transform="rotate(-90 222 230)" textAnchor="middle">IF / ID</text>

          <rect x="460" y="45" width="14" height="360" rx="4" fill="#1e293b" stroke={signals.flushIDEX ? "var(--accent-red)" : "var(--border-subtle)"} />
          <text x="467" y="230" fill="#fff" fontSize="9" fontWeight="700" fontFamily="var(--font-mono)" transform="rotate(-90 467 230)" textAnchor="middle">ID / EX</text>

          <rect x="710" y="45" width="14" height="360" rx="4" fill="#1e293b" stroke="var(--border-subtle)" />
          <text x="717" y="230" fill="#fff" fontSize="9" fontWeight="700" fontFamily="var(--font-mono)" transform="rotate(-90 717 230)" textAnchor="middle">EX / MEM</text>

          <rect x="945" y="45" width="14" height="360" rx="4" fill="#1e293b" stroke="var(--border-subtle)" />
          <text x="952" y="230" fill="#fff" fontSize="9" fontWeight="700" fontFamily="var(--font-mono)" transform="rotate(-90 952 230)" textAnchor="middle">MEM / WB</text>

          {/* STAGE 1: IF */}
          <g className="datapath-node" onClick={() => handleBlockClick("PC_MUX")}>
            <polygon points="30,120 48,135 48,185 30,200" fill="#1e293b" stroke={selectedBlock === "PC_MUX" ? "var(--accent-cyan)" : "var(--border-subtle)"} strokeWidth={selectedBlock === "PC_MUX" ? 2.5 : 1.5} />
            <text x="36" y="163" fill="var(--accent-cyan)" fontSize="9" fontWeight="700" fontFamily="var(--font-mono)">MUX</text>
          </g>

          <g className="datapath-node" onClick={() => handleBlockClick("PC")}>
            <rect x="65" y="135" width="55" height="50" rx="6" fill="#0f172a" stroke={selectedBlock === "PC" ? "#fff" : "var(--accent-cyan)"} strokeWidth={selectedBlock === "PC" ? 3 : 2} filter="url(#glow-cyan)" />
            <text x="92" y="157" fill="#fff" fontSize="11" fontWeight="700" textAnchor="middle" fontFamily="var(--font-mono)">PC</text>
            <text x="92" y="173" fill="var(--accent-cyan)" fontSize="8.5" fontWeight="600" textAnchor="middle" fontFamily="var(--font-mono)">
              0x{stages.IF.pc.toString(16).toUpperCase()}
            </text>
          </g>

          <g className="datapath-node" onClick={() => handleBlockClick("PC_ADDER")}>
            <polygon points="75,55 90,65 110,65 115,75 110,85 90,85 75,95" fill="#1e293b" stroke={selectedBlock === "PC_ADDER" ? "var(--accent-cyan)" : "var(--border-subtle)"} strokeWidth="1.5" />
            <text x="95" y="78" fill="var(--text-muted)" fontSize="9" fontWeight="700" fontFamily="var(--font-mono)">+4</text>
          </g>

          <g className="datapath-node" onClick={() => handleBlockClick("IMEM")}>
            <rect x="135" y="120" width="70" height="80" rx="6" fill="#0f172a" stroke={selectedBlock === "IMEM" ? "#fff" : "var(--accent-cyan)"} strokeWidth={selectedBlock === "IMEM" ? 2.5 : 1.5} />
            <text x="170" y="148" fill="#fff" fontSize="10" fontWeight="700" textAnchor="middle" fontFamily="var(--font-mono)">Inst Mem</text>
            <text x="170" y="164" fill="var(--text-muted)" fontSize="8.5" textAnchor="middle" fontFamily="var(--font-mono)">ROM (VecInit)</text>
            <text x="170" y="186" fill="var(--text-code)" fontSize="8" fontWeight="600" textAnchor="middle" fontFamily="var(--font-mono)">
              {stages.IF.mnemonic.split(" ")[0]}
            </text>
          </g>

          {/* IF Wires */}
          <path d="M 48 160 L 65 160" stroke="#00f5d4" strokeWidth="2" markerEnd="url(#arrow-cyan)" className="wire-active" />
          <path d="M 120 160 L 135 160" stroke="#00f5d4" strokeWidth="2" markerEnd="url(#arrow-cyan)" className="wire-active" />
          <path d="M 92 135 L 92 85" stroke="var(--border-subtle)" strokeWidth="1.5" />
          <path d="M 115 75 L 125 75 L 125 105 L 20 105 L 20 140 L 30 140" stroke="var(--border-subtle)" strokeWidth="1.5" fill="none" markerEnd="url(#arrow-cyan)" />
          <path d="M 205 160 L 215 160" stroke="#00f5d4" strokeWidth="2" markerEnd="url(#arrow-cyan)" className="wire-active" />

          {/* STAGE 2: ID */}
          <g className="datapath-node" onClick={() => handleBlockClick("DECODER")}>
            <rect x="245" y="60" width="75" height="65" rx="6" fill="#0f172a" stroke={selectedBlock === "DECODER" ? "#fff" : "var(--accent-cyan)"} strokeWidth={selectedBlock === "DECODER" ? 2.5 : 1.5} />
            <text x="282" y="85" fill="#fff" fontSize="10" fontWeight="700" textAnchor="middle" fontFamily="var(--font-mono)">Decoder</text>
            <text x="282" y="102" fill="var(--accent-cyan)" fontSize="8.5" textAnchor="middle" fontFamily="var(--font-mono)">RV32IM+Cap</text>
          </g>

          <g className="datapath-node" onClick={() => handleBlockClick("REGFILE")}>
            <rect x="340" y="60" width="105" height="100" rx="6" fill="#0f172a" stroke={selectedBlock === "REGFILE" ? "#fff" : "var(--accent-purple)"} strokeWidth={selectedBlock === "REGFILE" ? 2.5 : 1.5} filter="url(#glow-purple)" />
            <text x="392" y="85" fill="#fff" fontSize="10" fontWeight="700" textAnchor="middle" fontFamily="var(--font-mono)">Integer RF (x0-31)</text>
            <text x="392" y="105" fill="var(--accent-purple)" fontSize="8.5" textAnchor="middle" fontFamily="var(--font-mono)">Dual Read / Single Write</text>
            <text x="392" y="130" fill="var(--text-muted)" fontSize="8" textAnchor="middle" fontFamily="var(--font-mono)">
              rs1: {stages.ID.valid ? "x" + idRs1 : "-"} | rs2: {stages.ID.valid ? "x" + idRs2 : "-"}
            </text>
            <text x="392" y="148" fill="var(--accent-emerald)" fontSize="8" textAnchor="middle" fontFamily="var(--font-mono)">WB-&gt;ID Bypass</text>
          </g>

          <g className="datapath-node" onClick={() => handleBlockClick("CAPREGFILE")}>
            <rect x="255" y="190" width="190" height="95" rx="6" fill="#0f172a" stroke={selectedBlock === "CAPREGFILE" ? "#fff" : "var(--accent-magenta)"} strokeWidth={selectedBlock === "CAPREGFILE" ? 2.5 : 1.5} />
            <text x="350" y="212" fill="#fff" fontSize="10" fontWeight="700" textAnchor="middle" fontFamily="var(--font-mono)">Capability RF (c0–c7)</text>
            <text x="350" y="230" fill="var(--accent-magenta)" fontSize="8.5" textAnchor="middle" fontFamily="var(--font-mono)">101-Bit Bounded Registers</text>
            <text x="350" y="252" fill="var(--text-muted)" fontSize="8" textAnchor="middle" fontFamily="var(--font-mono)">
              Roots: c0(NULL) c1(RAM) c2(MMIO) | Proc: c3..c7
            </text>
            <text x="350" y="272" fill="var(--accent-cyan)" fontSize="8" textAnchor="middle" fontFamily="var(--font-mono)">Hardware Immutability Guard</text>
          </g>

          <g className="datapath-node" onClick={() => handleBlockClick("IMMGEN")}>
            <rect x="255" y="305" width="85" height="50" rx="6" fill="#1e293b" stroke={selectedBlock === "IMMGEN" ? "#fff" : "var(--border-subtle)"} strokeWidth="1.5" />
            <text x="297" y="328" fill="#fff" fontSize="9" fontWeight="700" textAnchor="middle" fontFamily="var(--font-mono)">Imm Gen</text>
            <text x="297" y="344" fill="var(--text-muted)" fontSize="8" textAnchor="middle" fontFamily="var(--font-mono)">I/S/B/U/J Types</text>
          </g>

          {/* STAGE 3: EX */}
          <g className="datapath-node" onClick={() => handleBlockClick("FORWARD_A")}>
            <polygon points="495,80 515,90 515,130 495,140" fill="#1e293b" stroke={selectedBlock === "FORWARD_A" ? "#fff" : isForwardA ? "var(--accent-amber)" : "var(--border-subtle)"} strokeWidth={selectedBlock === "FORWARD_A" ? 2.5 : 1.5} />
            <text x="502" y="112" fill={isForwardA ? "var(--accent-amber)" : "var(--text-muted)"} fontSize="8" fontWeight="700" fontFamily="var(--font-mono)">FwdA</text>
          </g>

          <g className="datapath-node" onClick={() => handleBlockClick("FORWARD_B")}>
            <polygon points="495,160 515,170 515,210 495,220" fill="#1e293b" stroke={selectedBlock === "FORWARD_B" ? "#fff" : isForwardB ? "var(--accent-amber)" : "var(--border-subtle)"} strokeWidth={selectedBlock === "FORWARD_B" ? 2.5 : 1.5} />
            <text x="502" y="192" fill={isForwardB ? "var(--accent-amber)" : "var(--text-muted)"} fontSize="8" fontWeight="700" fontFamily="var(--font-mono)">FwdB</text>
          </g>

          <g className="datapath-node" onClick={() => handleBlockClick("ALU")}>
            <polygon points="535,80 565,100 595,100 605,115 595,130 565,130 535,150 535,120 545,115 535,110" fill="#0f172a" stroke={selectedBlock === "ALU" ? "#fff" : "var(--accent-cyan)"} strokeWidth={selectedBlock === "ALU" ? 3 : 2} filter="url(#glow-cyan)" />
            <text x="570" y="120" fill="#fff" fontSize="11" fontWeight="800" textAnchor="middle" fontFamily="var(--font-mono)">ALU</text>
          </g>

          <g className="datapath-node" onClick={() => handleBlockClick("MUL")}>
            <rect x="535" y="170" width="135" height="60" rx="6" fill="#0f172a" stroke={selectedBlock === "MUL" ? "#fff" : "var(--accent-purple)"} strokeWidth={selectedBlock === "MUL" ? 2.5 : 1.5} />
            <text x="602" y="192" fill="#fff" fontSize="10" fontWeight="700" textAnchor="middle" fontFamily="var(--font-mono)">Booth-Wallace (34-Bit)</text>
            <text x="602" y="210" fill="var(--accent-purple)" fontSize="8" textAnchor="middle" fontFamily="var(--font-mono)">17 Radix-4 Groups / 68b Product</text>
            <text x="602" y="222" fill="var(--text-muted)" fontSize="7.5" textAnchor="middle" fontFamily="var(--font-mono)">Objective 1 IP Reuse</text>
          </g>

          <g className="datapath-node" onClick={() => handleBlockClick("DIV")}>
            <rect x="535" y="245" width="135" height="65" rx="6" fill="#0f172a" stroke={selectedBlock === "DIV" ? "#fff" : isDividerActive ? "var(--accent-amber)" : "var(--accent-purple)"} strokeWidth={selectedBlock === "DIV" ? 2.5 : 1.5} />
            <text x="602" y="267" fill="#fff" fontSize="10" fontWeight="700" textAnchor="middle" fontFamily="var(--font-mono)">Iterative Divider (33c)</text>
            <text x="602" y="285" fill={isDividerActive ? "var(--accent-amber)" : "var(--accent-emerald)"} fontSize="8.5" fontWeight="600" textAnchor="middle" fontFamily="var(--font-mono)">
              {isDividerActive ? `COUNTDOWN: ${signals.dividerIterationRemaining}/32` : "IDLE (Ready)"}
            </text>
            <text x="602" y="300" fill="var(--accent-red)" fontSize="7.5" textAnchor="middle" fontFamily="var(--font-mono)">io.kill Trap Abort Port</text>
          </g>

          <g className="datapath-node" onClick={() => handleBlockClick("BJU")}>
            <rect x="535" y="325" width="135" height="50" rx="6" fill="#1e293b" stroke={selectedBlock === "BJU" ? "#fff" : isBranchTaken ? "var(--accent-red)" : "var(--border-subtle)"} strokeWidth={selectedBlock === "BJU" ? 2.5 : 1.5} />
            <text x="602" y="348" fill="#fff" fontSize="9.5" fontWeight="700" textAnchor="middle" fontFamily="var(--font-mono)">Branch/Jump Unit</text>
            <text x="602" y="364" fill={isBranchTaken ? "var(--accent-red)" : "var(--text-muted)"} fontSize="8" textAnchor="middle" fontFamily="var(--font-mono)">
              {isBranchTaken ? "REDIRECT EXECUTED" : "Sequential Fetch"}
            </text>
          </g>

          {/* STAGE 4: MEM */}
          <g className="datapath-node" onClick={() => handleBlockClick("DATAMEM")}>
            <rect x="745" y="60" width="85" height="90" rx="6" fill="#0f172a" stroke={selectedBlock === "DATAMEM" ? "#fff" : "var(--accent-cyan)"} strokeWidth={selectedBlock === "DATAMEM" ? 2.5 : 1.5} />
            <text x="787" y="88" fill="#fff" fontSize="10" fontWeight="700" textAnchor="middle" fontFamily="var(--font-mono)">DataMemory</text>
            <text x="787" y="105" fill="var(--accent-cyan)" fontSize="8.5" textAnchor="middle" fontFamily="var(--font-mono)">4KB SRAM</text>
            <text x="787" y="125" fill="var(--text-muted)" fontSize="8" textAnchor="middle" fontFamily="var(--font-mono)">Byte-Masked</text>
            <text x="787" y="140" fill="var(--accent-emerald)" fontSize="7.5" textAnchor="middle" fontFamily="var(--font-mono)">Suppressed on Trap</text>
          </g>

          <g className="datapath-node" onClick={() => handleBlockClick("SYSTEM_MMIO")}>
            <rect x="845" y="60" width="85" height="90" rx="6" fill="#0f172a" stroke={selectedBlock === "SYSTEM_MMIO" ? "#fff" : "var(--accent-amber)"} strokeWidth={selectedBlock === "SYSTEM_MMIO" ? 2.5 : 1.5} />
            <text x="887" y="88" fill="#fff" fontSize="10" fontWeight="700" textAnchor="middle" fontFamily="var(--font-mono)">System MMIO</text>
            <text x="887" y="105" fill="var(--accent-amber)" fontSize="8" textAnchor="middle" fontFamily="var(--font-mono)">0x80000000+</text>
            <text x="887" y="125" fill="var(--text-muted)" fontSize="7.5" textAnchor="middle" fontFamily="var(--font-mono)">Telemetry+O2</text>
            <text x="887" y="140" fill="var(--accent-purple)" fontSize="7.5" textAnchor="middle" fontFamily="var(--font-mono)">SCHED_HINT</text>
          </g>

          <g className="datapath-node" onClick={() => handleBlockClick("CAPCHECKER")}>
            <rect x="745" y="170" width="185" height="110" rx="6" fill="#0f172a" stroke={selectedBlock === "CAPCHECKER" ? "#fff" : isTrapTaken ? "var(--accent-magenta)" : "var(--accent-purple)"} strokeWidth={selectedBlock === "CAPCHECKER" ? 3 : 2} filter={isTrapTaken ? "url(#glow-magenta)" : "none"} />
            <text x="837" y="195" fill="#fff" fontSize="10.5" fontWeight="800" textAnchor="middle" fontFamily="var(--font-mono)">CapabilityChecker</text>
            <text x="837" y="215" fill="var(--accent-cyan)" fontSize="8.5" textAnchor="middle" fontFamily="var(--font-mono)">33-Bit Widened Bounds Arithmetic</text>
            <text x="837" y="235" fill="var(--text-muted)" fontSize="8" textAnchor="middle" fontFamily="var(--font-mono)">
              Tag -&gt; Bounds [base..top] -&gt; Perms (RWX)
            </text>
            <text x="837" y="258" fill={isTrapTaken ? "var(--accent-magenta)" : "var(--accent-emerald)"} fontSize="9" fontWeight="700" textAnchor="middle" fontFamily="var(--font-mono)">
              {isTrapTaken ? "DENY -&gt; takePreciseTrap = 1" : "ALLOW -&gt; Memory Access Authorized"}
            </text>
          </g>

          <g className="datapath-node" onClick={() => handleBlockClick("TRAP_ENGINE")}>
            <rect x="745" y="300" width="185" height="75" rx="6" fill="#1e293b" stroke={selectedBlock === "TRAP_ENGINE" ? "#fff" : isTrapTaken ? "var(--accent-magenta)" : "var(--border-subtle)"} strokeWidth={selectedBlock === "TRAP_ENGINE" ? 2.5 : 1.5} />
            <text x="837" y="322" fill="#fff" fontSize="9.5" fontWeight="700" textAnchor="middle" fontFamily="var(--font-mono)">Precise Trap Engine</text>
            <text x="837" y="340" fill={isTrapTaken ? "var(--accent-magenta)" : "var(--text-muted)"} fontSize="8" textAnchor="middle" fontFamily="var(--font-mono)">
              TRAP_EPC: 0x{mmio.TRAP_EPC.toString(16).toUpperCase()} | CAUSE: 0x{mmio.TRAP_CAUSE.toString(16).toUpperCase()}
            </text>
            <text x="837" y="358" fill="var(--accent-red)" fontSize="7.5" textAnchor="middle" fontFamily="var(--font-mono)">
              Double Fault (Set-over-W1C) Protected
            </text>
          </g>

          {/* STAGE 5: WB */}
          <g className="datapath-node" onClick={() => handleBlockClick("WB_MUX")}>
            <polygon points="985,90 1005,105 1005,155 985,170" fill="#1e293b" stroke={selectedBlock === "WB_MUX" ? "#fff" : "var(--accent-emerald)"} strokeWidth={selectedBlock === "WB_MUX" ? 2.5 : 1.5} />
            <text x="990" y="133" fill="var(--accent-emerald)" fontSize="8.5" fontWeight="700" fontFamily="var(--font-mono)">WB</text>
          </g>

          <g className="datapath-node" onClick={() => handleBlockClick("COMMIT")}>
            <rect x="1020" y="95" width="75" height="70" rx="6" fill="#0f172a" stroke={selectedBlock === "COMMIT" ? "#fff" : "var(--accent-emerald)"} strokeWidth={selectedBlock === "COMMIT" ? 2.5 : 1.5} filter="url(#glow-emerald)" />
            <text x="1057" y="120" fill="#fff" fontSize="10" fontWeight="700" textAnchor="middle" fontFamily="var(--font-mono)">Commit</text>
            <text x="1057" y="136" fill="var(--accent-emerald)" fontSize="8" fontWeight="600" textAnchor="middle" fontFamily="var(--font-mono)">
              {stages.WB.valid ? "RETIRED" : "IDLE"}
            </text>
            <text x="1057" y="152" fill="var(--text-muted)" fontSize="7.5" textAnchor="middle" fontFamily="var(--font-mono)">
              {stages.WB.valid && stages.WB.regWrite ? `rd=x${stages.WB.rd}` : "No Write"}
            </text>
          </g>

          {/* Animated Wires */}
          {isForwardA && (
            <path
              d="M 717 115 L 717 40 L 505 40 L 505 90"
              fill="none"
              stroke="#f59e0b"
              strokeWidth="2.5"
              markerEnd="url(#arrow-amber)"
              className="wire-bypass"
            />
          )}

          {isForwardB && (
            <path
              d="M 952 135 L 952 25 L 500 25 L 500 170"
              fill="none"
              stroke="#f59e0b"
              strokeWidth="2.5"
              markerEnd="url(#arrow-amber)"
              className="wire-bypass"
            />
          )}

          {(isBranchTaken || isTrapTaken) && (
            <path
              d="M 837 375 L 837 420 L 40 420 L 40 180"
              fill="none"
              stroke={isTrapTaken ? "#ec4899" : "#ef4444"}
              strokeWidth="3"
              markerEnd="url(#arrow-magenta)"
              className="wire-active"
            />
          )}

          <path
            d="M 1057 165 L 1057 395 L 392 395 L 392 160"
            fill="none"
            stroke="rgba(16, 185, 129, 0.4)"
            strokeWidth="1.5"
            strokeDasharray="4,4"
            markerEnd="url(#arrow-emerald)"
          />
        </svg>
      </div>

      {/* ========================================================================= */}
      {/* FULLY FUNCTIONAL DYNAMIC CIRCUIT PROBE OSCILLOSCOPE */}
      {/* ========================================================================= */}
      {selectedBlock && (
        <div className="glass-panel" style={{ marginTop: "16px", borderLeft: "4px solid var(--accent-cyan)", background: "rgba(10, 14, 23, 0.95)" }}>
          <div className="panel-header" style={{ paddingBottom: "8px", marginBottom: "12px" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
              <Radio size={18} color="var(--accent-cyan)" className="engine-dot" />
              <span style={{ fontSize: "15px", fontWeight: 700, color: "#fff", letterSpacing: "0.02em" }}>
                CIRCUIT PROBE OSCILLOSCOPE & LOGIC ANALYZER: [{selectedBlock}]
              </span>
            </div>
            <button className="btn btn-secondary" style={{ padding: "4px 8px" }} onClick={() => setSelectedBlock("ALU")}>
              Reset Probe to ALU
            </button>
          </div>

          {/* Module-Specific Live Signal Probes */}
          {selectedBlock === "ALU" && (
            <div>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "12px", marginBottom: "14px", fontFamily: "var(--font-mono)", fontSize: "11px" }}>
                <div style={{ background: "rgba(0,0,0,0.4)", padding: "10px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
                  <span style={{ color: "var(--text-muted)" }}>OPERAND A (rs1):</span>
                  <div style={{ color: "var(--accent-cyan)", fontWeight: 700, fontSize: "13px", marginTop: "2px" }}>
                    {rs1Val} (0x{(rs1Val >>> 0).toString(16).toUpperCase().padStart(8, "0")})
                  </div>
                  <div style={{ color: "var(--text-muted)", fontSize: "10px", marginTop: "2px" }}>
                    Source: {signals.forwardA === 2 ? "EX/MEM Forward" : signals.forwardA === 1 ? "MEM/WB Forward" : `GPR x${exRs1}`}
                  </div>
                </div>

                <div style={{ background: "rgba(0,0,0,0.4)", padding: "10px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
                  <span style={{ color: "var(--text-muted)" }}>OPERAND B (rs2 / Imm):</span>
                  <div style={{ color: "var(--accent-purple)", fontWeight: 700, fontSize: "13px", marginTop: "2px" }}>
                    {rs2Val} (0x{(rs2Val >>> 0).toString(16).toUpperCase().padStart(8, "0")})
                  </div>
                  <div style={{ color: "var(--text-muted)", fontSize: "10px", marginTop: "2px" }}>
                    Source: {signals.forwardB === 2 ? "EX/MEM Forward" : signals.forwardB === 1 ? "MEM/WB Forward" : `GPR x${exRs2}`}
                  </div>
                </div>

                <div style={{ background: "rgba(0,0,0,0.4)", padding: "10px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
                  <span style={{ color: "var(--text-muted)" }}>ALU OPERATION:</span>
                  <div style={{ color: "var(--accent-amber)", fontWeight: 700, fontSize: "13px", marginTop: "2px" }}>
                    {stages.EX.mnemonic.split(" ")[0] || "ADD"}
                  </div>
                  <div style={{ color: "var(--text-muted)", fontSize: "10px", marginTop: "2px" }}>
                    ALU Control: 4-bit Opcode
                  </div>
                </div>

                <div style={{ background: "rgba(0,0,0,0.4)", padding: "10px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
                  <span style={{ color: "var(--text-muted)" }}>STATUS FLAGS:</span>
                  <div style={{ color: "var(--accent-emerald)", fontWeight: 700, fontSize: "13px", marginTop: "2px" }}>
                    Zero={rs1Val === rs2Val ? "1" : "0"} | Neg={rs1Val < 0 ? "1" : "0"}
                  </div>
                  <div style={{ color: "var(--text-muted)", fontSize: "10px", marginTop: "2px" }}>
                    Carry=0 | Overflow=0
                  </div>
                </div>
              </div>

              {/* Bitfield Breakdown */}
              <div style={{ background: "rgba(0,0,0,0.4)", padding: "10px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
                <span style={{ fontSize: "11px", color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>
                  EX-STAGE INSTRUCTION BITFIELD PROBE [31:0]: {stages.EX.mnemonic}
                </span>
                {renderBitStrip(stages.EX.instruction)}
              </div>
            </div>
          )}

          {selectedBlock === "PC" && (
            <div>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "12px", marginBottom: "14px", fontFamily: "var(--font-mono)", fontSize: "11px" }}>
                <div style={{ background: "rgba(0,0,0,0.4)", padding: "10px", borderRadius: "8px" }}>
                  <span style={{ color: "var(--text-muted)" }}>PROGRAM COUNTER (io.pc):</span>
                  <div style={{ color: "var(--accent-cyan)", fontWeight: 700, fontSize: "14px" }}>
                    0x{stages.IF.pc.toString(16).toUpperCase().padStart(8, "0")}
                  </div>
                </div>
                <div style={{ background: "rgba(0,0,0,0.4)", padding: "10px", borderRadius: "8px" }}>
                  <span style={{ color: "var(--text-muted)" }}>SEQUENTIAL NEXT PC:</span>
                  <div style={{ color: "var(--accent-emerald)", fontWeight: 700, fontSize: "14px" }}>
                    0x{(stages.IF.pc + 4).toString(16).toUpperCase().padStart(8, "0")}
                  </div>
                </div>
                <div style={{ background: "rgba(0,0,0,0.4)", padding: "10px", borderRadius: "8px" }}>
                  <span style={{ color: "var(--text-muted)" }}>IF STALL SIGNAL:</span>
                  <div style={{ color: signals.stallIF ? "var(--accent-amber)" : "var(--accent-emerald)", fontWeight: 700, fontSize: "14px" }}>
                    {signals.stallIF ? "1 (FROZEN)" : "0 (ADVANCING)"}
                  </div>
                </div>
                <div style={{ background: "rgba(0,0,0,0.4)", padding: "10px", borderRadius: "8px" }}>
                  <span style={{ color: "var(--text-muted)" }}>REDIRECT TARGET:</span>
                  <div style={{ color: isTrapTaken ? "var(--accent-magenta)" : isBranchTaken ? "var(--accent-red)" : "var(--text-muted)", fontWeight: 700, fontSize: "14px" }}>
                    {isTrapTaken ? `0x${mmio.TRAP_VECTOR.toString(16).toUpperCase()} (TRAP)` : isBranchTaken ? `0x${signals.redirectTarget.toString(16).toUpperCase()} (BRANCH)` : "None"}
                  </div>
                </div>
              </div>
              {renderBitStrip(stages.IF.pc)}
            </div>
          )}

          {selectedBlock === "DECODER" && (
            <div>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: "10px", marginBottom: "14px", fontFamily: "var(--font-mono)", fontSize: "11px" }}>
                <div style={{ background: "rgba(0,0,0,0.4)", padding: "8px", borderRadius: "6px" }}>
                  <span style={{ color: "var(--text-muted)" }}>OPCODE [6:0]:</span>
                  <div style={{ color: "var(--accent-cyan)", fontWeight: 700, fontSize: "13px" }}>
                    0x{idOpcode.toString(16).toUpperCase().padStart(2, "0")}
                  </div>
                </div>
                <div style={{ background: "rgba(0,0,0,0.4)", padding: "8px", borderRadius: "6px" }}>
                  <span style={{ color: "var(--text-muted)" }}>FUNCT3 [14:12]:</span>
                  <div style={{ color: "var(--accent-purple)", fontWeight: 700, fontSize: "13px" }}>
                    0b{idFunct3.toString(2).padStart(3, "0")}
                  </div>
                </div>
                <div style={{ background: "rgba(0,0,0,0.4)", padding: "8px", borderRadius: "6px" }}>
                  <span style={{ color: "var(--text-muted)" }}>DEST REG (rd):</span>
                  <div style={{ color: "var(--accent-emerald)", fontWeight: 700, fontSize: "13px" }}>
                    x{idRd}
                  </div>
                </div>
                <div style={{ background: "rgba(0,0,0,0.4)", padding: "8px", borderRadius: "6px" }}>
                  <span style={{ color: "var(--text-muted)" }}>SRC REG 1 (rs1):</span>
                  <div style={{ color: "var(--accent-cyan)", fontWeight: 700, fontSize: "13px" }}>
                    x{idRs1}
                  </div>
                </div>
                <div style={{ background: "rgba(0,0,0,0.4)", padding: "8px", borderRadius: "6px" }}>
                  <span style={{ color: "var(--text-muted)" }}>SRC REG 2 (rs2):</span>
                  <div style={{ color: "var(--accent-amber)", fontWeight: 700, fontSize: "13px" }}>
                    x{idRs2}
                  </div>
                </div>
              </div>
              {renderBitStrip(stages.ID.instruction)}
            </div>
          )}

          {selectedBlock === "CAPCHECKER" && (
            <div>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "12px", marginBottom: "14px", fontFamily: "var(--font-mono)", fontSize: "11px" }}>
                <div style={{ background: "rgba(0,0,0,0.4)", padding: "10px", borderRadius: "8px" }}>
                  <span style={{ color: "var(--text-muted)" }}>33-BIT WIDENED CURSOR:</span>
                  <div style={{ color: "var(--accent-cyan)", fontWeight: 700, fontSize: "13px" }}>
                    0x{(capC3.base + capC3.offset).toString(16).toUpperCase()}
                  </div>
                </div>
                <div style={{ background: "rgba(0,0,0,0.4)", padding: "10px", borderRadius: "8px" }}>
                  <span style={{ color: "var(--text-muted)" }}>33-BIT TOP BOUND:</span>
                  <div style={{ color: "var(--accent-purple)", fontWeight: 700, fontSize: "13px" }}>
                    0x{(capC3.base + capC3.length).toString(16).toUpperCase()}
                  </div>
                </div>
                <div style={{ background: "rgba(0,0,0,0.4)", padding: "10px", borderRadius: "8px" }}>
                  <span style={{ color: "var(--text-muted)" }}>TAG VALIDITY:</span>
                  <div style={{ color: capC3.tag === 1 ? "var(--accent-emerald)" : "var(--accent-red)", fontWeight: 700, fontSize: "13px" }}>
                    {capC3.tag === 1 ? "TAG = 1 (VALID)" : "TAG = 0 (INVALID)"}
                  </div>
                </div>
                <div style={{ background: "rgba(0,0,0,0.4)", padding: "10px", borderRadius: "8px" }}>
                  <span style={{ color: "var(--text-muted)" }}>COMBINATIONAL TRAP:</span>
                  <div style={{ color: isTrapTaken ? "var(--accent-magenta)" : "var(--accent-emerald)", fontWeight: 700, fontSize: "13px" }}>
                    takePreciseTrap = {isTrapTaken ? "1 (DENY)" : "0 (ALLOW)"}
                  </div>
                </div>
              </div>
              {renderBitStrip(capC3.base, 33)}
            </div>
          )}

          {/* Logic Analyzer Waveform Display */}
          <div style={{ marginTop: "14px", background: "rgba(0,0,0,0.6)", padding: "12px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "8px", fontSize: "11px", fontFamily: "var(--font-mono)" }}>
              <span style={{ color: "var(--accent-cyan)", fontWeight: 700 }}>
                DIGITAL LOGIC ANALYZER CHANNELS (CLOCK CYCLE {state.cycle_count}):
              </span>
              <span style={{ color: "var(--text-muted)" }}>SAMPLING RATE: 1 GHz (1.00 ns)</span>
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: "6px", fontFamily: "var(--font-mono)", fontSize: "10px" }}>
              {/* CLK */}
              <div style={{ display: "grid", gridTemplateColumns: "120px 1fr", alignItems: "center" }}>
                <span style={{ color: "var(--text-muted)" }}>CLK (Clock):</span>
                <div style={{ color: "var(--accent-cyan)", letterSpacing: "3px" }}>
                  _--_--_--_--_--_--_--_--_--_--_--_--_--_--_--_--
                </div>
              </div>
              {/* PC */}
              <div style={{ display: "grid", gridTemplateColumns: "120px 1fr", alignItems: "center" }}>
                <span style={{ color: "var(--text-muted)" }}>STAGE_PC:</span>
                <div style={{ color: "#fff" }}>
                  [0x{stages.EX.pc.toString(16).toUpperCase()}] ───► [0x{(stages.EX.pc + 4).toString(16).toUpperCase()}]
                </div>
              </div>
              {/* FORWARD A */}
              <div style={{ display: "grid", gridTemplateColumns: "120px 1fr", alignItems: "center" }}>
                <span style={{ color: "var(--text-muted)" }}>forwardA:</span>
                <div style={{ color: isForwardA ? "var(--accent-amber)" : "var(--text-muted)" }}>
                  {isForwardA ? "HIGH (2'b10 EX/MEM Bypass)" : "LOW  (2'b00 Normal RS1)"}
                </div>
              </div>
              {/* TRAP */}
              <div style={{ display: "grid", gridTemplateColumns: "120px 1fr", alignItems: "center" }}>
                <span style={{ color: "var(--text-muted)" }}>takePreciseTrap:</span>
                <div style={{ color: isTrapTaken ? "var(--accent-magenta)" : "var(--text-muted)", fontWeight: isTrapTaken ? 700 : 400 }}>
                  {isTrapTaken ? "HIGH (MEM Fault Violation Asserted)" : "LOW  (0b0 Normal Execution)"}
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
