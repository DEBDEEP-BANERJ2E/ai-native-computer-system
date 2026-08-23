import { InputPanel } from "../InputPanel";
import { DatapathView } from "../DatapathView";
import { ResultPanel } from "../ResultPanel";
import { TelemetryPanel } from "../TelemetryPanel";
import { WaveformView } from "../WaveformView";
import { OPERATIONS, type HistoryEntry, type SimulationResponse } from "../../types";
import type { InspectableUnit } from "../CircuitInspectorModal";

interface Props {
  a: string;
  b: string;
  opcode: number;
  response: SimulationResponse;
  history: HistoryEntry[];
  busy: boolean;
  onA: (val: string) => void;
  onB: (val: string) => void;
  onOpcode: (val: number) => void;
  onExecute: (overrideA?: number, overrideB?: number, overrideOpcode?: number) => Promise<void>;
  onReset: () => void;
  onInspectUnit: (unit: InspectableUnit) => void;
}

export function SystemTopLab({
  a,
  b,
  opcode,
  response,
  history,
  busy,
  onA,
  onB,
  onOpcode,
  onExecute,
  onReset,
  onInspectUnit,
}: Props) {
  const operation = OPERATIONS.find((o) => o.opcode === opcode) || OPERATIONS[0];

  return (
    <div className="lab-container system-top-lab">
      <div className="lab-header-banner">
        <div>
          <span className="lab-tag">LAB 01 · LIVE VERILATOR RTL EXECUTION</span>
          <h2>Integrated Subsystem Datapath &amp; MMIO Bus</h2>
        </div>
        <p className="lab-desc">
          High-level integrated datapath executing 11 RISC-V ALU operations through persistent Verilator hardware simulation with real-time result-bus telemetry decoding at <code>0x80001000</code>.
        </p>
      </div>

      <section className="workbench-grid">
        <InputPanel
          a={a}
          b={b}
          opcode={opcode}
          disabled={busy}
          onA={onA}
          onB={onB}
          onOpcode={onOpcode}
          onExecute={onExecute}
          onReset={onReset}
        />
        <DatapathView
          operation={operation}
          result={response.result}
          onInspect={onInspectUnit}
        />
      </section>

      <section className="output-grid">
        <ResultPanel response={response} />
        <TelemetryPanel telemetry={response.telemetry} />
      </section>

      <WaveformView history={history} />
    </div>
  );
}
