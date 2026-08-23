import { useState } from "react";
import { Zap, ShieldCheck, RefreshCw, ZoomIn, ZoomOut } from "lucide-react";

export function GatesLab() {
  // Fredkin Gate State: control (c), in1 (b), in2 (c_in)
  const [fredControl, setFredControl] = useState(1);
  const [fredA, setFredA] = useState(1);
  const [fredB, setFredB] = useState(0);

  // Toffoli Gate State: a, b, c
  const [tofA, setTofA] = useState(1);
  const [tofB, setTofB] = useState(1);
  const [tofC, setTofC] = useState(0);

  const [activeGateView, setActiveGateView] = useState<"fredkin" | "toffoli">("fredkin");
  const [zoom, setZoom] = useState(1);

  // Fredkin evaluations: p = c, q = c ? b : a, r = c ? a : b
  const fredP = fredControl;
  const fredQ = fredControl ? fredB : fredA;
  const fredR = fredControl ? fredA : fredB;

  // Toffoli evaluations: p = a, q = b, r = c ^ (a & b)
  const tofP = tofA;
  const tofQ = tofB;
  const tofR = tofC ^ (tofA & tofB);

  return (
    <div className="lab-container gates-lab">
      <div className="lab-header-banner">
        <div>
          <span className="lab-tag">LAB 02 · ARCHITECTURE MODEL (VALIDATED AGAINST RTL)</span>
          <h2>Logically Reversible Gates (Landauer Principles)</h2>
        </div>
        <p className="lab-desc">
          Logically reversible gates motivated by Landauer&apos;s principle; this FPGA/RTL implementation does not claim zero physical dissipation. Explores bijective logic mappings (f: &#123;0,1&#125;^n &rarr; &#123;0,1&#125;^n) where every input pattern maps to a unique output pattern with zero information erasure.
        </p>
      </div>

      <div className="gates-grid">
        {/* Fredkin (Controlled-SWAP) Interactive Workbench */}
        <section className="panel gate-card">
          <div className="panel-heading">
            <div>
              <span className="kicker">UNIVERSAL CONSERVATIVE GATE</span>
              <h3>Fredkin Gate (Controlled-SWAP)</h3>
            </div>
            <span className="gate-badge">3-In / 3-Out Bijective</span>
          </div>

          <p className="gate-summary">
            Swaps input bits $A$ and $B$ only when the Control line $C = 1$. Preserves the total number of 1s (conservative logic).
          </p>

          <div className="interactive-gate-box">
            <div className="gate-inputs">
              <span className="gate-section-title">Inputs (Click to toggle)</span>
              <button
                className={`bit-toggle ${fredControl ? "on" : "off"}`}
                onClick={() => setFredControl(fredControl ? 0 : 1)}
              >
                <span>Control (C)</span>
                <strong>{fredControl}</strong>
              </button>
              <button
                className={`bit-toggle ${fredA ? "on" : "off"}`}
                onClick={() => setFredA(fredA ? 0 : 1)}
              >
                <span>Input A</span>
                <strong>{fredA}</strong>
              </button>
              <button
                className={`bit-toggle ${fredB ? "on" : "off"}`}
                onClick={() => setFredB(fredB ? 0 : 1)}
              >
                <span>Input B</span>
                <strong>{fredB}</strong>
              </button>
            </div>

            <div className="gate-diagram-visual">
              <div className="gate-symbol fredkin">
                <span className="sym-label">CSWAP</span>
                <span className="sym-math">P = C<br />Q = C?B:A<br />R = C?A:B</span>
              </div>
            </div>

            <div className="gate-outputs">
              <span className="gate-section-title">Outputs (Evaluated)</span>
              <div className={`bit-output ${fredP ? "on" : "off"}`}>
                <span>P = C</span>
                <strong>{fredP}</strong>
              </div>
              <div className={`bit-output ${fredQ ? "on" : "off"}`}>
                <span>Q = {fredControl ? "B" : "A"}</span>
                <strong>{fredQ}</strong>
              </div>
              <div className={`bit-output ${fredR ? "on" : "off"}`}>
                <span>R = {fredControl ? "A" : "B"}</span>
                <strong>{fredR}</strong>
              </div>
            </div>
          </div>

          <div className="truth-table-wrap">
            <span className="table-title">Fredkin Gate Truth Table</span>
            <table className="mini-table">
              <thead>
                <tr><th>C</th><th>A</th><th>B</th><th>P</th><th>Q</th><th>R</th><th>Status</th></tr>
              </thead>
              <tbody>
                {[
                  [0,0,0, 0,0,0], [0,0,1, 0,0,1], [0,1,0, 0,1,0], [0,1,1, 0,1,1],
                  [1,0,0, 1,0,0], [1,0,1, 1,1,0], [1,1,0, 1,0,1], [1,1,1, 1,1,1]
                ].map(([c, a, b, p, q, r], i) => {
                  const isCurrent = c === fredControl && a === fredA && b === fredB;
                  return (
                    <tr key={i} className={isCurrent ? "active-row" : ""}>
                      <td>{c}</td><td>{a}</td><td>{b}</td><td>{p}</td><td>{q}</td><td>{r}</td>
                      <td>{isCurrent ? <span className="active-dot">● Active</span> : "—"}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>

        {/* Toffoli (CCNOT) Interactive Workbench */}
        <section className="panel gate-card">
          <div className="panel-heading">
            <div>
              <span className="kicker">UNIVERSAL QUANTUM GATE</span>
              <h3>Toffoli Gate (Controlled-Controlled-NOT)</h3>
            </div>
            <span className="gate-badge">3-In / 3-Out Bijective</span>
          </div>

          <p className="gate-summary">
            Inverts target bit $C$ if and only if both control inputs $A = 1$ and $B = 1$. Universally implements any Boolean logic function without information erasure.
          </p>

          <div className="interactive-gate-box">
            <div className="gate-inputs">
              <span className="gate-section-title">Inputs (Click to toggle)</span>
              <button
                className={`bit-toggle ${tofA ? "on" : "off"}`}
                onClick={() => setTofA(tofA ? 0 : 1)}
              >
                <span>Control A</span>
                <strong>{tofA}</strong>
              </button>
              <button
                className={`bit-toggle ${tofB ? "on" : "off"}`}
                onClick={() => setTofB(tofB ? 0 : 1)}
              >
                <span>Control B</span>
                <strong>{tofB}</strong>
              </button>
              <button
                className={`bit-toggle ${tofC ? "on" : "off"}`}
                onClick={() => setTofC(tofC ? 0 : 1)}
              >
                <span>Target C</span>
                <strong>{tofC}</strong>
              </button>
            </div>

            <div className="gate-diagram-visual">
              <div className="gate-symbol toffoli">
                <span className="sym-label">CCNOT</span>
                <span className="sym-math">P = A<br />Q = B<br />R = C ⊕ (A·B)</span>
              </div>
            </div>

            <div className="gate-outputs">
              <span className="gate-section-title">Outputs (Evaluated)</span>
              <div className={`bit-output ${tofP ? "on" : "off"}`}>
                <span>P = A</span>
                <strong>{tofP}</strong>
              </div>
              <div className={`bit-output ${tofQ ? "on" : "off"}`}>
                <span>Q = B</span>
                <strong>{tofQ}</strong>
              </div>
              <div className={`bit-output ${tofR ? "on" : "off"}`}>
                <span>R = C ⊕ (A·B)</span>
                <strong>{tofR}</strong>
              </div>
            </div>
          </div>

          <div className="truth-table-wrap">
            <span className="table-title">Toffoli Gate Truth Table</span>
            <table className="mini-table">
              <thead>
                <tr><th>A</th><th>B</th><th>C</th><th>P</th><th>Q</th><th>R</th><th>Status</th></tr>
              </thead>
              <tbody>
                {[
                  [0,0,0, 0,0,0], [0,0,1, 0,0,1], [0,1,0, 0,1,0], [0,1,1, 0,1,1],
                  [1,0,0, 1,0,0], [1,0,1, 1,0,1], [1,1,0, 1,1,1], [1,1,1, 1,1,0]
                ].map(([a, b, c, p, q, r], i) => {
                  const isCurrent = a === tofA && b === tofB && c === tofC;
                  return (
                    <tr key={i} className={isCurrent ? "active-row" : ""}>
                      <td>{a}</td><td>{b}</td><td>{c}</td><td>{p}</td><td>{q}</td><td>{r}</td>
                      <td>{isCurrent ? <span className="active-dot">● Active</span> : "—"}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>
      </div>

      {/* Schematic Viewer Section */}
      <section className="panel lab-schematic-section">
        <div className="panel-heading">
          <div>
            <span className="kicker">SCHEMATICS &amp; SYNTHESIZED NETLISTS</span>
            <h3>Reversible Gate Architecture</h3>
          </div>
          <div className="schematic-view-toggles">
            <button
              className={`cat-pill ${activeGateView === "fredkin" ? "active" : ""}`}
              onClick={() => setActiveGateView("fredkin")}
            >
              Fredkin Gate (CSWAP)
            </button>
            <button
              className={`cat-pill ${activeGateView === "toffoli" ? "active" : ""}`}
              onClick={() => setActiveGateView("toffoli")}
            >
              Toffoli Gate (CCNOT)
            </button>
          </div>
        </div>

        <div className="lab-schematic-display">
          <div className="schematic-image-wrap">
            <img
              src="/schematics/reversible_clean_schematic.jpg"
              alt="Reversible Gates Logic Diagram"
              className="lab-schematic-img"
            />
          </div>
          <div className="lab-schematic-notes">
            <h4>Chisel RTL Implementation ([`Gates.scala`](file:///Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/gates/Gates.scala))</h4>
            <div className="code-block">
              <code>{`// Fredkin Controlled-SWAP
class Fredkin extends Module {
  val io = IO(new Bundle {
    val control = Input(Bool())
    val b = Input(Bool())
    val c = Input(Bool())
    val p = Output(Bool())
    val q = Output(Bool())
    val r = Output(Bool())
  })
  io.p := io.control
  io.q := Mux(io.control, io.c, io.b)
  io.r := Mux(io.control, io.b, io.c)
}`}</code>
            </div>
            <div className="note-card">
              <ShieldCheck size={14} className="text-emerald" />
              <span>
                <strong>System Note:</strong> Reversible gates are verified standalone in <code>GatesSpec.scala</code>. In the top-level <code>Objective1Subsystem.scala</code>, <code>telemetry.io.reversibleOperation</code> is currently tied to <code>false.B</code> (reserved for future reversible ALU extensions).
              </span>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
