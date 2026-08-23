import { useState } from "react";
import { Layers, ArrowRight, Zap, CheckCircle2, ShieldAlert } from "lucide-react";

export function AddersLab() {
  const [aHex, setAHex] = useState("0000FFFF");
  const [bHex, setBHex] = useState("00000001");
  const [cin, setCin] = useState<0 | 1>(0);
  const [activeSchematic, setActiveSchematic] = useState<"hcla" | "cla4" | "rca">("hcla");

  const aVal = Number.parseInt(aHex.replace(/^0x/i, ""), 16) >>> 0;
  const bVal = Number.parseInt(bHex.replace(/^0x/i, ""), 16) >>> 0;

  // Real 32-bit addition evaluation
  const rawSum = aVal + bVal + cin;
  const sum32 = (rawSum >>> 0);
  const cout = rawSum > 0xffffffff;

  // Bitwise Propagate and Generate vectors
  const pVec = (aVal ^ bVal) >>> 0;
  const gVec = (aVal & bVal) >>> 0;

  // Critical path carry ripple simulation (count full adders where carry propagates)
  let rippleCarries: number[] = [cin];
  for (let i = 0; i < 32; i++) {
    const ai = (aVal >> i) & 1;
    const bi = (bVal >> i) & 1;
    const cPrev = rippleCarries[i];
    const cNext = (ai & bi) | (ai & cPrev) | (bi & cPrev);
    rippleCarries.push(cNext);
  }

  // 8 Block Group Propagate and Generate (Level 2 Hierarchical lookahead)
  const blocks = Array.from({ length: 8 }, (_, blk) => {
    const shift = blk * 4;
    const aNibble = (aVal >> shift) & 0xf;
    const bNibble = (bVal >> shift) & 0xf;
    const pNibble = (pVec >> shift) & 0xf;
    const gNibble = (gVec >> shift) & 0xf;

    const pG = pNibble === 0xf ? 1 : 0;
    const p0 = (pNibble >> 0) & 1;
    const p1 = (pNibble >> 1) & 1;
    const p2 = (pNibble >> 2) & 1;
    const p3 = (pNibble >> 3) & 1;
    const g0 = (gNibble >> 0) & 1;
    const g1 = (gNibble >> 1) & 1;
    const g2 = (gNibble >> 2) & 1;
    const g3 = (gNibble >> 3) & 1;

    const gG = (g3 | (p3 & g2) | (p3 & p2 & g1) | (p3 & p2 & p1 & g0)) & 1;
    const sumNibble = (sum32 >> shift) & 0xf;
    return { blk, aNibble, bNibble, pG, gG, sumNibble };
  });

  return (
    <div className="lab-container adders-lab">
      <div className="lab-header-banner">
        <div>
          <span className="lab-tag">LAB 03 · ARCHITECTURE MODEL (VALIDATED AGAINST RTL)</span>
          <h2>Ripple Carry Adder vs Hierarchical Carry-Lookahead Adder</h2>
        </div>
        <p className="lab-desc">
          Illustrates the algorithmic logic-depth scaling of the linear carry chain in a Ripple Carry Adder (O(n)) versus the parallel lookahead tree in the 32-bit Hierarchical Carry-Lookahead Adder (O(log n)). Theoretical logic-depth estimates shown; physical silicon timing pending post-place-and-route timing analysis.
        </p>
      </div>

      {/* Operands Bar */}
      <section className="panel adder-controls-panel">
        <div className="adder-inputs-row">
          <div className="adder-input-box">
            <label>Operand A (32-bit Hex)</label>
            <input
              value={aHex}
              onChange={(e) => setAHex(e.target.value.toUpperCase().replace(/[^0-9A-F]/g, "").slice(0, 8))}
              maxLength={8}
            />
          </div>
          <div className="adder-input-box">
            <label>Operand B (32-bit Hex)</label>
            <input
              value={bHex}
              onChange={(e) => setBHex(e.target.value.toUpperCase().replace(/[^0-9A-F]/g, "").slice(0, 8))}
              maxLength={8}
            />
          </div>
          <div className="adder-cin-box">
            <label>Carry-In (Cin)</label>
            <button
              className={`cin-btn ${cin ? "active" : ""}`}
              onClick={() => setCin(cin ? 0 : 1)}
            >
              Cin = {cin}
            </button>
          </div>
          <div className="adder-result-box">
            <span className="res-tag">Architecture Sum [31:0]</span>
            <strong className="res-hex">0x{sum32.toString(16).padStart(8, "0").toUpperCase()}</strong>
            <span className="res-cout">Cout = {cout ? "1" : "0"}</span>
          </div>
        </div>
      </section>

      {/* Comparative Architecture Columns */}
      <div className="architecture-comparison-grid">
        {/* RCA Architecture Box */}
        <section className="panel arch-card rca-card">
          <div className="panel-heading">
            <div>
              <span className="kicker">BASELINE ARCHITECTURE</span>
              <h3>Ripple Carry Adder (RCA)</h3>
            </div>
            <span className="complexity-badge bad">Logic Depth: O(n) · ~64 Gate Levels (estimate)</span>
          </div>

          <p className="arch-desc">
            Carries ripple sequentially through 32 cascaded 1-bit Full Adders (FA0 &rarr; FA1 &rarr; ... &rarr; FA31). Critical path delay is linearly proportional to operand width n.
          </p>

          <div className="ripple-chain-visual">
            <div className="ripple-chain-scroll">
              <span className="chain-node start">Cin: {cin}</span>
              {Array.from({ length: 8 }, (_, idx) => {
                const bit = idx * 4;
                const carryVal = rippleCarries[bit + 1];
                return (
                  <div key={idx} className="chain-fa-block">
                    <span className="fa-title">FA[{bit+3}:{bit}]</span>
                    <span className={`fa-carry ${carryVal ? "high" : "low"}`}>
                      C{bit+4} &rarr; {carryVal}
                    </span>
                  </div>
                );
              })}
              <span className={`chain-node end ${cout ? "high" : "low"}`}>Cout: {cout ? "1" : "0"}</span>
            </div>
          </div>

          <div className="metric-table">
            <div className="metric-row">
              <span>Illustrative logic-depth estimate:</span>
              <strong>~64 gate levels (2 delays × 32 FAs)</strong>
            </div>
            <div className="metric-row">
              <span>Chisel Implementation:</span>
              <code>RippleCarryAdder.scala (32 Full Adders)</code>
            </div>
          </div>
        </section>

        {/* HCLA Architecture Box */}
        <section className="panel arch-card hcla-card">
          <div className="panel-heading">
            <div>
              <span className="kicker">OPTIMIZED ARCHITECTURE</span>
              <h3>Hierarchical Carry-Lookahead Adder (HCLA)</h3>
            </div>
            <span className="complexity-badge good">Logic Depth: O(log n) · ~8 Gate Levels (estimate)</span>
          </div>

          <p className="arch-desc">
            Divides 32 bits into <strong>8 × 4-bit CLA blocks (CLA4)</strong>. Generates block Group Propagate (P_G) and Group Generate (G_G) fed into a 2nd-level Lookahead Generator.
          </p>

          {/* 8 CLA4 Blocks Visual */}
          <div className="hcla-blocks-grid">
            {blocks.map((blk) => (
              <div key={blk.blk} className="cla4-mini-card">
                <div className="cla4-top">
                  <span className="cla4-tag">CLA4 #{blk.blk}</span>
                  <span className="cla4-range">[{blk.blk*4+3}:{blk.blk*4}]</span>
                </div>
                <div className="cla4-signals">
                  <span>P_G = {blk.pG}</span>
                  <span>G_G = {blk.gG}</span>
                </div>
                <div className="cla4-sum">
                  Sum: <code>0x{blk.sumNibble.toString(16).toUpperCase()}</code>
                </div>
              </div>
            ))}
          </div>

          <div className="metric-table">
            <div className="metric-row">
              <span>Illustrative logic-depth estimate:</span>
              <strong>~8 gate levels (O(log n) tree)</strong>
            </div>
            <div className="metric-row">
              <span>Chisel Implementation:</span>
              <code>HierarchicalCarryLookaheadAdder.scala</code>
            </div>
          </div>
        </section>
      </div>

      {/* Schematic Viewer Tab */}
      <section className="panel lab-schematic-section">
        <div className="panel-heading">
          <div>
            <span className="kicker">SCHEMATICS &amp; SYNTHESIS NETLISTS</span>
            <h3>Adder Circuit Schematics</h3>
          </div>
          <div className="schematic-view-toggles">
            <button
              className={`cat-pill ${activeSchematic === "hcla" ? "active" : ""}`}
              onClick={() => setActiveSchematic("hcla")}
            >
              32-Bit HCLA Diagram
            </button>
            <button
              className={`cat-pill ${activeSchematic === "cla4" ? "active" : ""}`}
              onClick={() => setActiveSchematic("cla4")}
            >
              4-Bit CLA4 Logic Gates
            </button>
            <button
              className={`cat-pill ${activeSchematic === "rca" ? "active" : ""}`}
              onClick={() => setActiveSchematic("rca")}
            >
              Yosys Netlist (HCLA)
            </button>
          </div>
        </div>

        <div className="lab-schematic-display">
          <div className="schematic-image-wrap">
            <img
              src={
                activeSchematic === "hcla"
                  ? "/schematics/hcla_clean_schematic.jpg"
                  : activeSchematic === "cla4"
                  ? "/schematics/cla4_clean_schematic.jpg"
                  : "/schematics/HierarchicalCarryLookaheadAdder.svg"
              }
              alt="Adder Schematic"
              className="lab-schematic-img"
            />
          </div>
          <div className="lab-schematic-notes">
            <h4>Lookahead Boolean Formulations ([`HierarchicalCarryLookaheadAdder.scala`](file:///Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala))</h4>
            <div className="math-block">
              <code>P_i = A_i ⊕ B_i,   G_i = A_i · B_i</code>
              <code>P_G = P_3 · P_2 · P_1 · P_0</code>
              <code>G_G = G_3 + P_3·G_2 + P_3·P_2·G_1 + P_3·P_2·P_1·G_0</code>
              <code>C_4 = G_G0 + P_G0 · Cin</code>
              <code>C_8 = G_G1 + P_G1 · G_G0 + P_G1 · P_G0 · Cin</code>
            </div>
            <div className="note-card">
              <CheckCircle2 size={14} className="text-emerald" />
              <span>
                <strong>Academic Verification:</strong> Exhaustively verified in <code>HierarchicalCarryLookaheadAdderSpec.scala</code> across 8-bit full permutations and 32-bit randomized CPU datapath vectors.
              </span>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
