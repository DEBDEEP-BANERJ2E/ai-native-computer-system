import { useEffect, useRef, useState } from "react";
import { Activity, Cpu, Layers, Play, RotateCcw, ShieldAlert, Zap } from "lucide-react";
import { execute, health, reset } from "./api";
import { OPERATIONS, type HistoryEntry, type SimulationResponse } from "./types";
import { InputPanel } from "./components/InputPanel";
import { DatapathView } from "./components/DatapathView";
import { ResultPanel } from "./components/ResultPanel";
import { TelemetryPanel } from "./components/TelemetryPanel";
import { WaveformView } from "./components/WaveformView";
import { CircuitInspectorModal, type InspectableUnit } from "./components/CircuitInspectorModal";

const initialResponse: SimulationResponse = {
  result: 0,
  zero: true,
  negative: false,
  carry: false,
  overflow: false,
  busy: false,
  done: true,
  valid: false,
  telemetry_data: 0,
  telemetry: {
    rev_energy_acc: 0,
    cla_switching: 0,
    mul_thermal: 0,
    edp_current: 0,
    edp_config: 1,
  },
};

function App() {
  const [a, setA] = useState("00000005");
  const [b, setB] = useState("00000003");
  const [opcode, setOpcode] = useState(0);
  const [response, setResponse] = useState<SimulationResponse>(initialResponse);
  const [history, setHistory] = useState<HistoryEntry[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [rtlOnline, setRtlOnline] = useState(false);
  const [inspectUnit, setInspectUnit] = useState<InspectableUnit | null>(null);

  const cycleRef = useRef(0);
  const operation = OPERATIONS[opcode];

  useEffect(() => {
    let cancelled = false;
    async function probe() {
      try {
        const ok = await health();
        if (!cancelled) setRtlOnline(ok);
      } catch {
        if (!cancelled) setRtlOnline(false);
      }
    }
    probe();
    const timer = window.setInterval(probe, 3000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, []);

  async function handleExecute() {
    setBusy(true);
    setError("");
    try {
      const parsedA = Number.parseInt(a.replace(/^0x/i, ""), 16) >>> 0;
      const parsedB = Number.parseInt(b.replace(/^0x/i, ""), 16) >>> 0;
      const next = await execute(parsedA, parsedB, opcode);
      setResponse(next);
      setRtlOnline(true);
      cycleRef.current += 1;
      setHistory((current) => [
        ...current.slice(-19),
        {
          ...next,
          cycle: cycleRef.current,
          a: parsedA,
          b: parsedB,
          operation: operation.label,
        },
      ]);
    } catch (reason) {
      setRtlOnline(false);
      setError(reason instanceof Error ? reason.message : "RTL Simulator process unavailable");
    } finally {
      setBusy(false);
    }
  }

  async function handleReset() {
    setBusy(true);
    setError("");
    try {
      await reset();
      cycleRef.current = 0;
      setResponse(initialResponse);
      setHistory([]);
      setRtlOnline(true);
    } catch (reason) {
      setRtlOnline(false);
      setError(reason instanceof Error ? reason.message : "RTL Simulator process unavailable");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="shell">
      <header className="topbar">
        <div className="brand">
          <div className="brand-mark">
            <Cpu size={22} />
          </div>
          <div>
            <span className="eyebrow">AI-NATIVE COMPUTER SYSTEM · OBJECTIVE 1</span>
            <h1>Hardware Datapath &amp; Telemetry Workbench</h1>
          </div>
        </div>
        <div className="topbar-right">
          <div className={`status ${rtlOnline ? "ok" : "down"}`}>
            <span className="status-dot" />
            <span>RTL {rtlOnline ? "ONLINE" : "OFFLINE"}</span>
            <span className="chip">VERILATOR PERSISTENT ENGINE</span>
          </div>
        </div>
      </header>

      <section className="hero-strip">
        <div>
          <span className="kicker">ACCURATE DIGITAL SIMULATION</span>
          <h2>Operate and inspect the hardware you built from first principles.</h2>
          <p>
            Every calculation is simulated through persistent cycle-accurate Verilator execution of <code>Objective1Subsystem.sv</code> with real switching activity telemetry.
          </p>
        </div>
        <div className="hero-metrics">
          <div>
            <strong>11</strong>
            <span>ALU Opcodes</span>
          </div>
          <div>
            <strong>32-bit</strong>
            <span>Datapath Width</span>
          </div>
          <div>
            <strong>5</strong>
            <span>MMIO Regs</span>
          </div>
        </div>
      </section>

      <section className="workbench-grid">
        <InputPanel
          a={a}
          b={b}
          opcode={opcode}
          disabled={busy || !rtlOnline}
          onA={setA}
          onB={setB}
          onOpcode={setOpcode}
          onExecute={handleExecute}
          onReset={handleReset}
        />
        <DatapathView
          operation={operation}
          result={response.result}
          onInspect={(unit) => setInspectUnit(unit)}
        />
      </section>

      {error && (
        <div className="error-banner">
          <ShieldAlert size={18} />
          <div>
            <strong>Simulation Engine Error:</strong> {error}
          </div>
        </div>
      )}

      <section className="output-grid">
        <ResultPanel response={response} />
        <TelemetryPanel telemetry={response.telemetry} />
      </section>

      <section className="trace-section">
        <div className="section-heading">
          <div>
            <span className="kicker">HARDWARE TIMING DIAGRAM</span>
            <h2>Cycle-Accurate Digital Waveforms</h2>
          </div>
          <span className="trace-count">
            {history.length.toString().padStart(2, "0")} Cycles Executed
          </span>
        </div>
        <WaveformView history={history} />
      </section>

      {inspectUnit && (
        <CircuitInspectorModal
          unitKey={inspectUnit}
          operation={operation}
          a={Number.parseInt(a.replace(/^0x/i, ""), 16) >>> 0}
          b={Number.parseInt(b.replace(/^0x/i, ""), 16) >>> 0}
          result={response.result}
          zero={response.zero}
          negative={response.negative}
          carry={response.carry}
          overflow={response.overflow}
          telemetry={response.telemetry}
          onClose={() => setInspectUnit(null)}
        />
      )}

      <footer>
        <span>
          <Activity size={14} /> GENERATED HARDWARE · OBJECTIVE1SUBSYSTEM.SV (CHISEL 3.6.0)
        </span>
        <span>
          <Zap size={14} /> SWITCHING HAMMING PROXIES &amp; MMIO 0x80001000 MAP
        </span>
      </footer>
    </main>
  );
}

export default App;