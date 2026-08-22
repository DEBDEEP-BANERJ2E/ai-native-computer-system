import { useState } from "react";
import { Activity, Cpu, Play, RotateCcw, ShieldCheck, Zap } from "lucide-react";
import { execute, reset } from "./api";
import { OPERATIONS, type HistoryEntry, type SimulationResponse } from "./types";
import { InputPanel } from "./components/InputPanel";
import { DatapathView } from "./components/DatapathView";
import { ResultPanel } from "./components/ResultPanel";
import { TelemetryPanel } from "./components/TelemetryPanel";
import { WaveformView } from "./components/WaveformView";

const initialResponse: SimulationResponse = {
  result: 0, zero: true, negative: false, carry: false, overflow: false,
  busy: false, done: true, valid: false, telemetry_data: 0,
  telemetry: { rev_energy_acc: 0, cla_switching: 0, mul_thermal: 0, edp_current: 0, edp_config: 1 },
};

function App() {
  const [a, setA] = useState("00000005");
  const [b, setB] = useState("00000003");
  const [opcode, setOpcode] = useState(0);
  const [response, setResponse] = useState(initialResponse);
  const [history, setHistory] = useState<HistoryEntry[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const operation = OPERATIONS[opcode];

  async function handleExecute() {
    setBusy(true); setError("");
    try {
      const parsedA = Number.parseInt(a.replace(/^0x/i, ""), 16) >>> 0;
      const parsedB = Number.parseInt(b.replace(/^0x/i, ""), 16) >>> 0;
      const next = await execute(parsedA, parsedB, opcode);
      setResponse(next);
      setHistory((current) => [...current.slice(-19), {
        ...next, cycle: current.length + 1, a: parsedA, b: parsedB, operation: operation.label,
      }]);
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Simulator unavailable"); }
    finally { setBusy(false); }
  }

  async function handleReset() {
    setBusy(true); setError("");
    try { await reset(); setResponse(initialResponse); setHistory([]); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Simulator unavailable"); }
    finally { setBusy(false); }
  }

  return <main className="shell">
    <header className="topbar">
      <div className="brand"><div className="brand-mark"><Cpu size={22} /></div><div><span className="eyebrow">AI-NATIVE COMPUTER SYSTEM</span><h1>Objective 1 Hardware Workbench</h1></div></div>
      <div className="status"><span className="status-dot" /> RTL SIMULATION ONLINE <span className="chip">VERILATOR</span></div>
    </header>
    <section className="hero-strip"><div><span className="kicker">LIVE DIGITAL SYSTEM</span><h2>Operate the hardware you built.</h2><p>Every result below comes from Chisel-generated SystemVerilog running through a persistent Verilator simulator.</p></div><div className="hero-metrics"><div><strong>11</strong><span>ALU ops</span></div><div><strong>32</strong><span>bit datapath</span></div><div><strong>5</strong><span>MMIO regs</span></div></div></section>
    <section className="workbench-grid">
      <InputPanel a={a} b={b} opcode={opcode} disabled={busy} onA={setA} onB={setB} onOpcode={setOpcode} onExecute={handleExecute} onReset={handleReset} />
      <DatapathView operation={operation} />
    </section>
    {error && <div className="error-banner"><ShieldCheck size={16} /> {error}</div>}
    <section className="output-grid"><ResultPanel response={response} /><TelemetryPanel telemetry={response.telemetry} /></section>
    <section className="trace-section"><div className="section-heading"><div><span className="kicker">SEQUENTIAL TRACE</span><h2>Operation history</h2></div><span className="trace-count">{history.length.toString().padStart(2, "0")} cycles</span></div><WaveformView history={history} /></section>
    <footer><span><Activity size={14} /> GENERATED RTL / OBJECTIVE1SUBSYSTEM.SV</span><span><Zap size={14} /> PROXY METRICS, NOT SILICON MEASUREMENTS</span></footer>
  </main>;
}

export default App;