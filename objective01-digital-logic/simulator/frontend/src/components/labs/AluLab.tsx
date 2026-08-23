import { useState } from "react";
import { Cpu, Zap, CheckCircle2, Info, ArrowRight } from "lucide-react";
import { OPERATIONS, type Operation } from "../../types";

export function AluLab() {
  const [aHex, setAHex] = useState("0000002A"); // 42
  const [bHex, setBHex] = useState("00000002"); // 2
  const [selectedOpcode, setSelectedOpcode] = useState(0);
  const [activeSchematic, setActiveSchematic] = useState<"alu_arch" | "shifter" | "yosys">("alu_arch");

  const aVal = Number.parseInt(aHex.replace(/^0x/i, ""), 16) >>> 0;
  const bVal = Number.parseInt(bHex.replace(/^0x/i, ""), 16) >>> 0;
  const aSigned = (aVal | 0);
  const bSigned = (bVal | 0);
  const shiftAmt = bVal & 0x1f; // lower 5 bits

  // Local evaluation of all 11 opcodes matching ALU.scala exactly
  let resultVal = 0;
  let carryOut = false;
  let overflowFlag = false;

  switch (selectedOpcode) {
    case 0: { // ADD
      const sum64 = BigInt(aVal) + BigInt(bVal);
      resultVal = Number(BigInt.asUintN(32, sum64));
      carryOut = sum64 > 0xffffffffn;
      const sa = (aVal >> 31) & 1;
      const sb = (bVal >> 31) & 1;
      const sr = (resultVal >> 31) & 1;
      overflowFlag = (sa === sb) && (sa !== sr);
      break;
    }
    case 1: { // SUB
      const sub64 = BigInt(aVal) - BigInt(bVal);
      resultVal = Number(BigInt.asUintN(32, sub64));
      carryOut = aVal >= bVal;
      const sa = (aVal >> 31) & 1;
      const sb = (bVal >> 31) & 1;
      const sr = (resultVal >> 31) & 1;
      overflowFlag = (sa !== sb) && (sa !== sr);
      break;
    }
    case 2: resultVal = (aVal & bVal) >>> 0; break; // AND
    case 3: resultVal = (aVal | bVal) >>> 0; break; // OR
    case 4: resultVal = (aVal ^ bVal) >>> 0; break; // XOR
    case 5: resultVal = (aVal << shiftAmt) >>> 0; break; // SLL
    case 6: resultVal = (aVal >>> shiftAmt) >>> 0; break; // SRL
    case 7: resultVal = (aSigned >> shiftAmt) >>> 0; break; // SRA
    case 8: resultVal = aSigned < bSigned ? 1 : 0; break; // SLT (signed)
    case 9: resultVal = aVal < bVal ? 1 : 0; break; // SLTU (unsigned)
    case 10: { // MUL
      const prod64 = BigInt(aSigned) * BigInt(bSigned);
      resultVal = Number(BigInt.asUintN(32, prod64));
      break;
    }
  }

  const zeroFlag = resultVal === 0;
  const negativeFlag = ((resultVal >> 31) & 1) === 1;

  const currentOp = OPERATIONS.find((o) => o.opcode === selectedOpcode) || OPERATIONS[0];

  return (
    <div className="lab-container alu-lab">
      <div className="lab-header-banner">
        <div>
          <span className="lab-tag">LAB 05 · ARCHITECTURE MODEL (VALIDATED AGAINST RTL)</span>
          <h2>32-Bit RISC-V Arithmetic Logic Unit &amp; Variable Shifter</h2>
        </div>
        <p className="lab-desc">
          Interactive architecture model evaluating all 11 RISC-V compatible ALU opcodes across arithmetic, bitwise logic, logarithmic barrel shifting (SLL, SRL, SRA), and magnitude comparison (SLT, SLTU). Validated against <code>ALU.scala</code>.
        </p>
      </div>

      {/* Inputs Bar */}
      <section className="panel adder-controls-panel">
        <div className="adder-inputs-row">
          <div className="adder-input-box">
            <label>Operand A (32-bit Hex)</label>
            <input
              value={aHex}
              onChange={(e) => setAHex(e.target.value.toUpperCase().replace(/[^0-9A-F]/g, "").slice(0, 8))}
              maxLength={8}
            />
            <small className="signed-sub">Signed Dec: {aSigned.toLocaleString()}</small>
          </div>
          <div className="adder-input-box">
            <label>Operand B (32-bit Hex / Shift Amount: {shiftAmt})</label>
            <input
              value={bHex}
              onChange={(e) => setBHex(e.target.value.toUpperCase().replace(/[^0-9A-F]/g, "").slice(0, 8))}
              maxLength={8}
            />
            <small className="signed-sub">Signed Dec: {bSigned.toLocaleString()} · Shift B[4:0] = {shiftAmt}</small>
          </div>
          <div className="adder-result-box highlight-alu">
            <span className="res-tag">Architecture ALU Result [31:0]</span>
            <strong className="res-hex">0x{resultVal.toString(16).padStart(8, "0").toUpperCase()}</strong>
            <div className="mini-flags-row">
              <span className={`flag-pill ${zeroFlag ? "on" : ""}`}>Z={zeroFlag ? 1 : 0}</span>
              <span className={`flag-pill ${negativeFlag ? "on" : ""}`}>N={negativeFlag ? 1 : 0}</span>
              <span className={`flag-pill ${carryOut ? "on" : ""}`}>C={carryOut ? 1 : 0}</span>
              <span className={`flag-pill ${overflowFlag ? "on" : ""}`}>V={overflowFlag ? 1 : 0}</span>
            </div>
          </div>
        </div>
      </section>

      {/* 11 Opcode Grid */}
      <section className="panel opcode-lab-grid-panel">
        <div className="panel-heading">
          <div>
            <span className="kicker">OPERATION SELECTOR</span>
            <h3>11 RISC-V Compatible Instructions</h3>
          </div>
          <span className="active-op-label">Active: {currentOp.label} (Opcode 0x{currentOp.opcode.toString(16).toUpperCase()})</span>
        </div>

        <div className="alu-opcodes-cards">
          {OPERATIONS.map((op) => (
            <button
              key={op.opcode}
              className={`alu-op-card ${op.opcode === selectedOpcode ? "active" : ""}`}
              onClick={() => setSelectedOpcode(op.opcode)}
            >
              <div className="op-card-top">
                <span className="op-num">0x{op.opcode.toString(16).toUpperCase()}</span>
                <span className="op-sym">{op.symbol}</span>
              </div>
              <strong className="op-name">{op.label}</strong>
              <span className="op-unit-tag">{op.unit}</span>
            </button>
          ))}
        </div>
      </section>

      {/* Shifter & Comparator Technical Breakdown */}
      <div className="architecture-comparison-grid">
        <section className="panel arch-card">
          <div className="panel-heading">
            <div>
              <span className="kicker">VARIABLE SHIFTER</span>
              <h3>Barrel Shifter Synthesis Model</h3>
            </div>
            <span className="complexity-badge good">5 Mux Stages (1, 2, 4, 8, 16)</span>
          </div>

          <p className="arch-desc">
            In Chisel RTL (<code>ALU.scala</code>), shifts execute via <code>io.a &lt;&lt; shiftAmount</code>, <code>&gt;&gt;</code>, and <code>.asSInt &gt;&gt;</code>. In synthesized silicon, variable shifts synthesize into a 5-stage logarithmic multiplexer barrel shifter controlled by $B[4:0]$.
          </p>

          <div className="shifter-stages-box">
            <div className="shift-stage-row">
              <span>Stage 1 ($2^0=1$ bit):</span>
              <strong>Shift bit B[0] = {(shiftAmt >> 0) & 1}</strong>
            </div>
            <div className="shift-stage-row">
              <span>Stage 2 ($2^1=2$ bits):</span>
              <strong>Shift bit B[1] = {(shiftAmt >> 1) & 1}</strong>
            </div>
            <div className="shift-stage-row">
              <span>Stage 3 ($2^2=4$ bits):</span>
              <strong>Shift bit B[2] = {(shiftAmt >> 2) & 1}</strong>
            </div>
            <div className="shift-stage-row">
              <span>Stage 4 ($2^3=8$ bits):</span>
              <strong>Shift bit B[3] = {(shiftAmt >> 3) & 1}</strong>
            </div>
            <div className="shift-stage-row">
              <span>Stage 5 ($2^4=16$ bits):</span>
              <strong>Shift bit B[4] = {(shiftAmt >> 4) & 1}</strong>
            </div>
          </div>
        </section>

        <section className="panel arch-card">
          <div className="panel-heading">
            <div>
              <span className="kicker">COMPARATOR SUBSYSTEM</span>
              <h3>Magnitude Comparators (SLT / SLTU)</h3>
            </div>
            <span className="complexity-badge good">Direct RISC-V Semantics</span>
          </div>

          <p className="arch-desc">
            In <code>ALU.scala</code>, magnitude comparisons evaluate directly in parallel:
          </p>

          <div className="comparator-details-box">
            <div className="comp-eval-row">
              <span>Signed SLT: <code>io.a.asSInt &lt; io.b.asSInt</code></span>
              <strong className={aSigned < bSigned ? "text-emerald" : ""}>
                {aSigned} &lt; {bSigned} &rarr; {aSigned < bSigned ? "1 (TRUE)" : "0 (FALSE)"}
              </strong>
            </div>
            <div className="comp-eval-row">
              <span>Unsigned SLTU: <code>io.a &lt; io.b</code></span>
              <strong className={aVal < bVal ? "text-emerald" : ""}>
                {aVal} &lt; {bVal} &rarr; {aVal < bVal ? "1 (TRUE)" : "0 (FALSE)"}
              </strong>
            </div>
            <div className="note-card">
              <Info size={13} className="text-amber" />
              <span>
                Note: Flag-based equivalents $N \oplus V$ (signed) and $\neg C$ (unsigned) are architectural identities from subtraction; the Chisel RTL executes direct comparator expressions.
              </span>
            </div>
          </div>
        </section>
      </div>

      {/* Schematic Viewer Tab */}
      <section className="panel lab-schematic-section">
        <div className="panel-heading">
          <div>
            <span className="kicker">SCHEMATICS &amp; NETLIST</span>
            <h3>ALU &amp; Shifter Schematics</h3>
          </div>
          <div className="schematic-view-toggles">
            <button
              className={`cat-pill ${activeSchematic === "alu_arch" ? "active" : ""}`}
              onClick={() => setActiveSchematic("alu_arch")}
            >
              ALU 1:1 Architecture
            </button>
            <button
              className={`cat-pill ${activeSchematic === "shifter" ? "active" : ""}`}
              onClick={() => setActiveSchematic("shifter")}
            >
              Barrel Shifter 1:1 Diagram
            </button>
            <button
              className={`cat-pill ${activeSchematic === "yosys" ? "active" : ""}`}
              onClick={() => setActiveSchematic("yosys")}
            >
              Yosys Netlist (ALU)
            </button>
          </div>
        </div>

        <div className="lab-schematic-display">
          <div className="schematic-image-wrap">
            <img
              src={
                activeSchematic === "alu_arch"
                  ? "/schematics/alu_clean_schematic.jpg"
                  : activeSchematic === "shifter"
                  ? "/schematics/barrel_shifter_clean_schematic.jpg"
                  : "/schematics/ALU.svg"
              }
              alt="ALU Schematic"
              className="lab-schematic-img"
            />
          </div>
        </div>
      </section>
    </div>
  );
}
