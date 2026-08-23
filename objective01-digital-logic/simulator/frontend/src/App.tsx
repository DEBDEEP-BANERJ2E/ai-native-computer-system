import { useEffect, useRef, useState } from "react";
import { Cpu, ShieldAlert, Zap, Layers, Activity, Server, Radio, BookOpen } from "lucide-react";
import { execute, health, reset } from "./api";
import {
  OPERATIONS,
  LABS,
  type LabId,
  type HistoryEntry,
  type SimulationResponse,
} from "./types";
import { SystemTopLab } from "./components/labs/SystemTopLab";
import { GatesLab } from "./components/labs/GatesLab";
import { AddersLab } from "./components/labs/AddersLab";
import { MultipliersLab } from "./components/labs/MultipliersLab";
import { AluLab } from "./components/labs/AluLab";
import { TelemetryLab } from "./components/labs/TelemetryLab";
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

export default function App() {
  const [activeLab, setActiveLab] = useState<LabId>("system");
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
  const operation = OPERATIONS[opcode] || OPERATIONS[0];

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

  async function handleExecute(
    overrideA?: number,
    overrideB?: number,
    overrideOpcode?: number
  ) {
    setBusy(true);
    setError("");
    try {
      const parsedA =
        overrideA !== undefined
          ? overrideA >>> 0
          : Number.parseInt(a.replace(/^0x/i, ""), 16) >>> 0;
      const parsedB =
        overrideB !== undefined
          ? overrideB >>> 0
          : Number.parseInt(b.replace(/^0x/i, ""), 16) >>> 0;
      const parsedOp = overrideOpcode !== undefined ? overrideOpcode : opcode;

      const next = await execute(parsedA, parsedB, parsedOp);
      setResponse(next);
      setRtlOnline(true);
      cycleRef.current += 1;

      const targetOp = OPERATIONS.find((o) => o.opcode === parsedOp) || operation;

      setHistory((current) => [
        ...current.slice(-19),
        {
          ...next,
          cycle: cycleRef.current,
          a: parsedA,
          b: parsedB,
          operation: targetOp.label,
        },
      ]);
    } catch (reason) {
      setRtlOnline(false);
      setError(
        reason instanceof Error
          ? reason.message
          : "RTL Simulator process unavailable"
      );
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
      setError(
        reason instanceof Error
          ? reason.message
          : "RTL Simulator process unavailable"
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="shell">
      {/* Top Header */}
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
          <div className={`rtl-status-indicator ${rtlOnline ? "online" : "offline"}`}>
            <span className="pulse-dot" />
            <span>{rtlOnline ? "RTL SIMULATOR ONLINE" : "RTL ENGINE OFFLINE"}</span>
          </div>
        </div>
      </header>

      {/* 6 Dedicated Architecture Labs Navigation Bar */}
      <nav className="labs-nav-bar">
        <div className="labs-nav-scroll">
          {LABS.map((lab) => {
            const isActive = activeLab === lab.id;
            return (
              <button
                key={lab.id}
                className={`lab-nav-btn ${isActive ? "active" : ""}`}
                onClick={() => setActiveLab(lab.id)}
              >
                <span className="lab-btn-index">{lab.index}</span>
                <div className="lab-btn-text">
                  <span className="lab-btn-name">{lab.name}</span>
                  <span className="lab-btn-sub">{lab.subtitle}</span>
                </div>
              </button>
            );
          })}
        </div>
      </nav>

      {/* Error Alert */}
      {error && (
        <div className="error-banner">
          <ShieldAlert size={18} />
          <span>
            <strong>Simulation Error:</strong> {error}
          </span>
        </div>
      )}

      {/* Active Lab Routing */}
      {activeLab === "system" && (
        <SystemTopLab
          a={a}
          b={b}
          opcode={opcode}
          response={response}
          history={history}
          busy={busy}
          onA={setA}
          onB={setB}
          onOpcode={setOpcode}
          onExecute={handleExecute}
          onReset={handleReset}
          onInspectUnit={setInspectUnit}
        />
      )}

      {activeLab === "gates" && <GatesLab />}
      {activeLab === "adders" && <AddersLab />}
      {activeLab === "multipliers" && <MultipliersLab />}
      {activeLab === "alu" && <AluLab />}
      {activeLab === "telemetry" && <TelemetryLab telemetry={response.telemetry} />}

      {/* Circuit Inspector Modal */}
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

      {/* Global Footer */}
      <footer>
        <span>Objective 1: Digital Logic &amp; Hardware Datapath Workbench</span>
        <span>Chisel 3.6.0 · Verilator C++ · Yosys XC7 Netlists</span>
      </footer>
    </main>
  );
}