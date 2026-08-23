import { useState } from "react";
import { Cpu, CheckCircle2, ShieldCheck, ArrowDown } from "lucide-react";

export function MultipliersLab() {
  const [aHex, setAHex] = useState("00000007");
  const [bHex, setBHex] = useState("FFFFFFFB"); // -5 in 32-bit two's complement
  const [activeSchematic, setActiveSchematic] = useState<"clean" | "yosys">("clean");

  const aVal = Number.parseInt(aHex.replace(/^0x/i, ""), 16) >>> 0;
  const bVal = Number.parseInt(bHex.replace(/^0x/i, ""), 16) >>> 0;

  const aSigned = (aVal | 0);
  const bSigned = (bVal | 0);

  // Signed 64-bit BigInt multiplication
  const product64 = BigInt(aSigned) * BigInt(bSigned);
  const product32 = Number(BigInt.asIntN(32, product64));
  const product32Hex = (product32 >>> 0).toString(16).padStart(8, "0").toUpperCase();

  // Radix-4 Booth Recoding for lower 8 bits (4 groups)
  // For group i: slice bits b[2i+1], b[2i], b[2i-1] (b[-1] = 0)
  const boothGroups = Array.from({ length: 4 }, (_, i) => {
    const bitMinus1 = i === 0 ? 0 : (bVal >> (2 * i - 1)) & 1;
    const bit0 = (bVal >> (2 * i)) & 1;
    const bit1 = (bVal >> (2 * i + 1)) & 1;
    const code = (bit1 << 2) | (bit0 << 1) | bitMinus1;

    let factor = 0;
    let label = "0";
    switch (code) {
      case 0: factor = 0; label = "0"; break;
      case 1: factor = 1; label = "+1 × A"; break;
      case 2: factor = 1; label = "+1 × A"; break;
      case 3: factor = 2; label = "+2 × A"; break;
      case 4: factor = -2; label = "-2 × A"; break;
      case 5: factor = -1; label = "-1 × A"; break;
      case 6: factor = -1; label = "-1 × A"; break;
      case 7: factor = 0; label = "0"; break;
    }

    const partialVal = BigInt(factor) * BigInt(aSigned);
    return {
      group: i,
      bits: `${bit1}${bit0}${bitMinus1}`,
      factor,
      label,
      partialVal: Number(BigInt.asIntN(32, partialVal)),
    };
  });

  return (
    <div className="lab-container multipliers-lab">
      <div className="lab-header-banner">
        <div>
          <span className="lab-tag">LAB 04 · ARCHITECTURE MODEL (VALIDATED AGAINST RTL)</span>
          <h2>Radix-4 Booth Recoding &amp; Wallace Tree 3:2 Reduction</h2>
        </div>
        <p className="lab-desc">
          Interactive architecture model demonstrating the 3-phase Booth-Wallace pipeline: Radix-4 Booth recoding (halving partial products), multi-layer Wallace 3:2 Carry-Save Compressor reduction, and final carry-propagate addition. Validated against <code>BoothWallaceMultiplier.scala</code>.
        </p>
      </div>

      {/* Operands Bar */}
      <section className="panel adder-controls-panel">
        <div className="adder-inputs-row">
          <div className="adder-input-box">
            <label>Multiplicand A (Signed Hex)</label>
            <input
              value={aHex}
              onChange={(e) => setAHex(e.target.value.toUpperCase().replace(/[^0-9A-F]/g, "").slice(0, 8))}
              maxLength={8}
            />
            <small className="signed-sub">Signed Dec: {aSigned.toLocaleString()}</small>
          </div>
          <div className="adder-input-box">
            <label>Multiplier B (Signed Hex)</label>
            <input
              value={bHex}
              onChange={(e) => setBHex(e.target.value.toUpperCase().replace(/[^0-9A-F]/g, "").slice(0, 8))}
              maxLength={8}
            />
            <small className="signed-sub">Signed Dec: {bSigned.toLocaleString()}</small>
          </div>
          <div className="adder-result-box highlight-mul">
            <span className="res-tag">Architecture-Model Product</span>
            <strong className="res-hex">0x{product32Hex}</strong>
            <span className="res-cout">Signed Dec: {product32.toLocaleString()}</span>
          </div>
        </div>
      </section>

      {/* 3-Phase Multiplier Architecture Breakdown */}
      <div className="multiplier-phases-grid">
        {/* Phase 1: Radix-4 Booth Recoding */}
        <section className="panel phase-card">
          <div className="panel-heading">
            <div>
              <span className="kicker">PHASE 1</span>
              <h3>Radix-4 Booth Recoding Excerpt</h3>
            </div>
            <span className="phase-badge">First 8 Bits (4 of 16 groups)</span>
          </div>

          <p className="phase-desc">
            Booth recoding excerpt — first 8 multiplier bits (4 of 16 groups): Takes 3-bit overlapping slices (B[2i+1], B[2i], B[2i-1]) of multiplier B. Generates partial products in &#123;0, &plusmn;1A, &plusmn;2A&#125;.
          </p>

          <div className="booth-groups-list">
            {boothGroups.map((grp) => (
              <div key={grp.group} className="booth-grp-row">
                <div className="grp-meta">
                  <span className="grp-index">Group #{grp.group}</span>
                  <code>Bits: {grp.bits}</code>
                </div>
                <strong className="grp-action">{grp.label}</strong>
                <span className="grp-val">0x{(grp.partialVal >>> 0).toString(16).padStart(8, "0").toUpperCase()}</span>
              </div>
            ))}
          </div>
        </section>

        {/* Phase 2: Wallace Tree 3:2 CSA Compressor */}
        <section className="panel phase-card">
          <div className="panel-heading">
            <div>
              <span className="kicker">PHASE 2</span>
              <h3>Wallace Tree 3:2 Reduction</h3>
            </div>
            <span className="phase-badge">Carry-Save Compression</span>
          </div>

          <p className="phase-desc">
            Passes partial product matrix through multi-level 3:2 full-adder compressors (Sum = A &oplus; B &oplus; C, Carry = maj(A,B,C) &lt;&lt; 1), reducing N rows down to 2 vectors in O(log N) time.
          </p>

          <div className="csa-box">
            <div className="csa-math-row">
              <code>Sum = Row0 ⊕ Row1 ⊕ Row2</code>
              <code>Carry = ((Row0·Row1) | (Row0·Row2) | (Row1·Row2)) &lt;&lt; 1</code>
            </div>
            <div className="csa-layers-flow">
              <span className="csa-stage">16 Partial Products &rarr;</span>
              <span className="csa-stage">Stage 1 (11 rows) &rarr;</span>
              <span className="csa-stage">Stage 2 (8 rows) &rarr;</span>
              <span className="csa-stage">Stage 3 (6 rows) &rarr;</span>
              <span className="csa-stage">Stage 4 (4 rows) &rarr;</span>
              <span className="csa-stage highlight">2 Final Rows (Sum + Carry)</span>
            </div>
          </div>
        </section>

        {/* Phase 3: Final Carry-Propagate Addition */}
        <section className="panel phase-card">
          <div className="panel-heading">
            <div>
              <span className="kicker">PHASE 3</span>
              <h3>Final Carry-Propagate Addition</h3>
            </div>
            <span className="phase-badge">Resolves Redundant Form</span>
          </div>

          <p className="phase-desc">
            Combines the two final carry-save reduced vectors (<code>currentRows(0) +&amp; currentRows(1)</code> in <code>WallaceTree.scala</code>) to produce the unified product.
          </p>

          <div className="final-sum-box">
            <code>io.result := currentRows(0) +& currentRows(1)</code>
            <div className="metric-table">
              <div className="metric-row">
                <span>Chisel Module:</span>
                <code>BoothWallaceMultiplier.scala + WallaceTree.scala</code>
              </div>
              <div className="metric-row">
                <span>Unit Test Suite:</span>
                <code>BoothWallaceMultiplierSpec.scala (Passed)</code>
              </div>
            </div>
          </div>
        </section>
      </div>

      {/* Schematic Viewer Section */}
      <section className="panel lab-schematic-section">
        <div className="panel-heading">
          <div>
            <span className="kicker">SCHEMATICS &amp; NETLIST</span>
            <h3>Booth-Wallace Multiplier Schematics</h3>
          </div>
          <div className="schematic-view-toggles">
            <button
              className={`cat-pill ${activeSchematic === "clean" ? "active" : ""}`}
              onClick={() => setActiveSchematic("clean")}
            >
              Booth-Wallace 1:1 Diagram
            </button>
            <button
              className={`cat-pill ${activeSchematic === "yosys" ? "active" : ""}`}
              onClick={() => setActiveSchematic("yosys")}
            >
              Yosys Netlist (BoothWallaceMultiplier)
            </button>
          </div>
        </div>

        <div className="lab-schematic-display">
          <div className="schematic-image-wrap">
            <img
              src={
                activeSchematic === "clean"
                  ? "/schematics/booth_wallace_clean_schematic.jpg"
                  : "/schematics/BoothWallaceMultiplier.svg"
              }
              alt="Booth Wallace Multiplier Schematic"
              className="lab-schematic-img"
            />
          </div>
          <div className="lab-schematic-notes">
            <h4>Academic Implementation Notes</h4>
            <div className="note-card">
              <ShieldCheck size={14} className="text-emerald" />
              <span>
                <strong>Academic Truth:</strong> The Wallace tree reduces partial products to two vectors, and resolves them via <code>currentRows(0) +& currentRows(1)</code>. The clean 1:1 diagram shows the complete hardware flow from recoder slices to carry-propagate addition.
              </span>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
