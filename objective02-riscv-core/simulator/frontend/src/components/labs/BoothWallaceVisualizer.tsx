import React, { useState } from "react";
import { Cpu, Layers, Activity, ChevronRight, Zap } from "lucide-react";

export const BoothWallaceVisualizer: React.FC = () => {
  const [valA, setValA] = useState<number>(-17);
  const [valB, setValB] = useState<number>(25);

  // Compute 68-bit product from 34-bit sign-extended operands
  const productBig = BigInt(valA) * BigInt(valB);
  const low32 = Number(BigInt.asIntN(32, productBig));
  const high32 = Number(BigInt.asIntN(32, productBig >> 32n));

  // Generate 17 Radix-4 Booth Groups (34 bits / 2)
  const boothGroups = Array.from({ length: 17 }, (_, i) => {
    const bitPos = i * 2;
    const digit = ((valB >> (bitPos - 1)) & 0x7); // 3-bit window
    const multiplier = [-0, 1, 1, 2, -2, -1, -1, -0][digit] || 0;
    return {
      index: i,
      bits: `b[${bitPos + 1}:${Math.max(0, bitPos - 1)}]`,
      digit,
      multiplier,
      partialProduct: valA * multiplier,
    };
  });

  return (
    <div className="glass-panel" style={{ marginTop: "16px" }}>
      <div className="panel-header">
        <div className="panel-title">
          <Cpu size={16} color="var(--accent-purple)" />
          <span>Interactive 34-Bit Booth-Wallace Multiplier Visualizer (17 Radix-4 Groups)</span>
        </div>
        <span style={{ fontSize: "11px", color: "var(--accent-cyan)", fontFamily: "var(--font-mono)" }}>
          Objective 1 IP Architecture Reuse
        </span>
      </div>

      {/* Input Operand Sliders */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", marginBottom: "16px" }}>
        <div style={{ background: "rgba(0,0,0,0.3)", padding: "12px", borderRadius: "8px" }}>
          <div style={{ display: "flex", justifyContent: "space-between", fontSize: "11px", fontFamily: "var(--font-mono)", marginBottom: "4px" }}>
            <span>Operand A (Multiplicand rs1):</span>
            <span style={{ color: "var(--accent-cyan)", fontWeight: 700 }}>{valA}</span>
          </div>
          <input
            type="range"
            min="-128"
            max="127"
            value={valA}
            onChange={(e) => setValA(parseInt(e.target.value, 10))}
            style={{ width: "100%", accentColor: "var(--accent-cyan)" }}
          />
        </div>

        <div style={{ background: "rgba(0,0,0,0.3)", padding: "12px", borderRadius: "8px" }}>
          <div style={{ display: "flex", justifyContent: "space-between", fontSize: "11px", fontFamily: "var(--font-mono)", marginBottom: "4px" }}>
            <span>Operand B (Multiplier rs2):</span>
            <span style={{ color: "var(--accent-purple)", fontWeight: 700 }}>{valB}</span>
          </div>
          <input
            type="range"
            min="-128"
            max="127"
            value={valB}
            onChange={(e) => setValB(parseInt(e.target.value, 10))}
            style={{ width: "100%", accentColor: "var(--accent-purple)" }}
          />
        </div>
      </div>

      {/* 17 Radix-4 Booth Groups Grid */}
      <div style={{ marginBottom: "16px" }}>
        <div style={{ fontSize: "12px", fontWeight: 700, marginBottom: "8px", color: "var(--text-primary)" }}>
          17 Radix-4 Booth Groups (34-Bit Expanded Operand / 2 = 17 Groups):
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(105px, 1fr))", gap: "6px" }}>
          {boothGroups.map((g) => (
            <div
              key={g.index}
              style={{
                background: "rgba(0,0,0,0.4)",
                border: "1px solid var(--border-subtle)",
                borderRadius: "6px",
                padding: "6px",
                textAlign: "center",
                fontFamily: "var(--font-mono)",
                fontSize: "10px",
              }}
            >
              <div style={{ color: "var(--text-muted)" }}>Group {g.index}</div>
              <div style={{ color: "var(--accent-amber)", fontWeight: 700, margin: "2px 0" }}>
                {g.multiplier > 0 ? `+${g.multiplier} × A` : g.multiplier < 0 ? `${g.multiplier} × A` : "0"}
              </div>
              <div style={{ color: "var(--text-secondary)", fontSize: "9px" }}>
                {g.partialProduct}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* 3:2 Wallace Tree CSA Reduction Stages */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "10px", textAlign: "center", fontSize: "11px", fontFamily: "var(--font-mono)" }}>
        <div style={{ background: "rgba(0,0,0,0.3)", padding: "10px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
          <div style={{ color: "var(--accent-cyan)", fontWeight: 700 }}>17 Partial Products</div>
          <div style={{ color: "var(--text-muted)", fontSize: "10px", marginTop: "4px" }}>Radix-4 Recoded</div>
        </div>
        <div style={{ background: "rgba(0,0,0,0.3)", padding: "10px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
          <div style={{ color: "var(--accent-purple)", fontWeight: 700 }}>3:2 Wallace Tree</div>
          <div style={{ color: "var(--text-muted)", fontSize: "10px", marginTop: "4px" }}>CSA Compressor Layers</div>
        </div>
        <div style={{ background: "rgba(0,0,0,0.3)", padding: "10px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
          <div style={{ color: "var(--accent-amber)", fontWeight: 700 }}>68-Bit Hardware Sum</div>
          <div style={{ color: "var(--text-muted)", fontSize: "10px", marginTop: "4px" }}>Final CLA Vector Add</div>
        </div>
        <div style={{ background: "rgba(0,0,0,0.3)", padding: "10px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
          <div style={{ color: "var(--accent-emerald)", fontWeight: 700 }}>RV32M Extraction</div>
          <div style={{ color: "var(--text-muted)", fontSize: "10px", marginTop: "4px" }}>MUL: [31:0] | MULH: [63:32]</div>
        </div>
      </div>

      {/* Final Product Output */}
      <div style={{ display: "flex", justifyContent: "space-around", marginTop: "16px", background: "rgba(0,0,0,0.4)", padding: "12px", borderRadius: "8px", border: "1px solid var(--border-subtle)", fontFamily: "var(--font-mono)", fontSize: "12px" }}>
        <div>
          <span style={{ color: "var(--text-muted)" }}>Full 64-Bit Product: </span>
          <span style={{ color: "#fff", fontWeight: 700 }}>{productBig.toString()} (0x{BigInt.asUintN(64, productBig).toString(16).toUpperCase()})</span>
        </div>
        <div>
          <span style={{ color: "var(--text-muted)" }}>MUL (Lower 32-bit): </span>
          <span style={{ color: "var(--accent-cyan)", fontWeight: 700 }}>{low32} (0x{Number(BigInt.asUintN(32, BigInt(low32))).toString(16).toUpperCase()})</span>
        </div>
        <div>
          <span style={{ color: "var(--text-muted)" }}>MULH (Upper 32-bit): </span>
          <span style={{ color: "var(--accent-purple)", fontWeight: 700 }}>{high32} (0x{Number(BigInt.asUintN(32, BigInt(high32))).toString(16).toUpperCase()})</span>
        </div>
      </div>
    </div>
  );
};
