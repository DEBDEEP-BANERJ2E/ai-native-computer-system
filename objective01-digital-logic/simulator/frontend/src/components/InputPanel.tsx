import { useState } from "react";
import {
  Play,
  RotateCcw,
  FastForward,
  Sparkles,
  Layers,
  Hash,
  Binary,
  Shuffle,
  Info,
  CheckCircle,
} from "lucide-react";
import {
  OPERATIONS,
  PRESET_TEST_CASES,
  type Operation,
  type OpCategory,
  type PresetTestCase,
} from "../types";

type Props = {
  a: string;
  b: string;
  opcode: number;
  disabled: boolean;
  onA: (value: string) => void;
  onB: (value: string) => void;
  onOpcode: (value: number) => void;
  onExecute: () => void;
  onReset: () => void;
};

const CATEGORIES: { id: OpCategory | "all"; label: string }[] = [
  { id: "all", label: "All Units" },
  { id: "arithmetic", label: "Hierarchical CLA" },
  { id: "mul", label: "Booth Multiplier" },
  { id: "shift", label: "Barrel Shifter" },
  { id: "logic", label: "Logic Fabric" },
  { id: "compare", label: "Comparators" },
];

function hexToDec(hexStr: string): { signed: number; unsigned: number } {
  const clean = hexStr.replace(/^0x/i, "").padStart(8, "0");
  const unsigned = Number.parseInt(clean, 16) >>> 0;
  const signed = (unsigned | 0);
  return { signed, unsigned };
}

function hexToBin(hexStr: string): string {
  const clean = hexStr.replace(/^0x/i, "").padStart(8, "0");
  const num = Number.parseInt(clean, 16) >>> 0;
  return num.toString(2).padStart(32, "0");
}

function formatBinGroups(binStr: string): string {
  return binStr.match(/.{1,4}/g)?.join(" ") || binStr;
}

export function InputPanel({
  a,
  b,
  opcode,
  disabled,
  onA,
  onB,
  onOpcode,
  onExecute,
  onReset,
}: Props) {
  const [categoryFilter, setCategoryFilter] = useState<OpCategory | "all">("all");
  const [showPresets, setShowPresets] = useState(false);
  const [isAutoRunning, setIsAutoRunning] = useState(false);

  const selectedOp = OPERATIONS.find((o) => o.opcode === opcode) || OPERATIONS[0];
  const aDec = hexToDec(a);
  const bDec = hexToDec(b);

  const filteredOps =
    categoryFilter === "all"
      ? OPERATIONS
      : OPERATIONS.filter((op) => op.category === categoryFilter);

  const handleApplyPreset = (preset: PresetTestCase) => {
    onA(preset.a);
    onB(preset.b);
    onOpcode(preset.opcode);
  };

  const handleRandomize = () => {
    const randA = Math.floor(Math.random() * 0xffffffff).toString(16).padStart(8, "0").toUpperCase();
    const randB = Math.floor(Math.random() * 0xffffffff).toString(16).padStart(8, "0").toUpperCase();
    onA(randA);
    onB(randB);
  };

  const handleInvertA = () => {
    const num = Number.parseInt(a, 16) >>> 0;
    const inv = (~num >>> 0).toString(16).padStart(8, "0").toUpperCase();
    onA(inv);
  };

  const handleInvertB = () => {
    const num = Number.parseInt(b, 16) >>> 0;
    const inv = (~num >>> 0).toString(16).padStart(8, "0").toUpperCase();
    onB(inv);
  };

  const handleZeroA = () => onA("00000000");
  const handleZeroB = () => onB("00000000");

  const handleAutoRun = async () => {
    if (isAutoRunning || disabled) return;
    setIsAutoRunning(true);
    for (const preset of PRESET_TEST_CASES.slice(0, 5)) {
      handleApplyPreset(preset);
      await new Promise((r) => setTimeout(r, 100));
      onExecute();
      await new Promise((r) => setTimeout(r, 600));
    }
    setIsAutoRunning(false);
  };

  return (
    <section className="panel input-panel enhanced-control-surface">
      <div className="panel-heading">
        <div>
          <span className="kicker">ENHANCED CONTROL SURFACE</span>
          <h2>Operand Generator &amp; Opcode Matrix</h2>
        </div>
        <div className="control-surface-badges">
          <button
            className={`preset-toggle-btn ${showPresets ? "active" : ""}`}
            onClick={() => setShowPresets(!showPresets)}
            title="Toggle Engineering Corner Cases"
          >
            <Sparkles size={13} /> {showPresets ? "Hide Presets" : "Test Presets"}
          </button>
        </div>
      </div>

      {/* Engineering Presets Drawer */}
      {showPresets && (
        <div className="presets-drawer">
          <div className="presets-header">
            <span>Engineering Corner-Case Scenarios</span>
            <small>Click to instantly load vector into hardware datapath</small>
          </div>
          <div className="presets-grid">
            {PRESET_TEST_CASES.map((preset) => (
              <button
                key={preset.name}
                className="preset-card"
                onClick={() => handleApplyPreset(preset)}
                disabled={disabled}
              >
                <div className="preset-top">
                  <span className="preset-tag">{preset.tag}</span>
                  <span className="preset-name">{preset.name}</span>
                </div>
                <div className="preset-values">
                  <code>A: 0x{preset.a}</code>
                  <code>B: 0x{preset.b}</code>
                </div>
                <p className="preset-desc">{preset.explanation}</p>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Operands Entry with Live Radix Conversion */}
      <div className="operands-split">
        {/* Operand A Card */}
        <div className="operand-card">
          <div className="operand-card-header">
            <label htmlFor="operand-a-input">
              <span className="op-label">OPERAND A</span>
              <span className="op-width">[31:0]</span>
            </label>
            <div className="quick-actions">
              <button onClick={handleZeroA} title="Set to 0x00000000" disabled={disabled}>0</button>
              <button onClick={handleInvertA} title="Bitwise NOT (~A)" disabled={disabled}>~A</button>
              <button onClick={() => onA("7FFFFFFF")} title="Max Positive Signed" disabled={disabled}>Max+</button>
              <button onClick={() => onA("FFFFFFFF")} title="All Ones (0xFFFFFFFF)" disabled={disabled}>0xFF</button>
            </div>
          </div>

          <div className="input-with-prefix">
            <span className="prefix">0x</span>
            <input
              id="operand-a-input"
              value={a}
              onChange={(e) =>
                onA(e.target.value.toUpperCase().replace(/[^0-9A-F]/g, "").slice(0, 8))
              }
              maxLength={8}
              placeholder="00000000"
              disabled={disabled}
              spellCheck={false}
            />
          </div>

          <div className="radix-preview-box">
            <div className="radix-item">
              <span className="radix-label"><Hash size={10} /> Signed Dec:</span>
              <span className="radix-val">{aDec.signed.toLocaleString()}</span>
            </div>
            <div className="radix-item">
              <span className="radix-label"><Binary size={10} /> Binary:</span>
              <span className="radix-bin-val" title={hexToBin(a)}>
                {formatBinGroups(hexToBin(a).slice(0, 16))}...
              </span>
            </div>
          </div>
        </div>

        {/* Operand B Card */}
        <div className="operand-card">
          <div className="operand-card-header">
            <label htmlFor="operand-b-input">
              <span className="op-label">OPERAND B</span>
              <span className="op-width">[31:0]</span>
            </label>
            <div className="quick-actions">
              <button onClick={handleZeroB} title="Set to 0x00000000" disabled={disabled}>0</button>
              <button onClick={handleInvertB} title="Bitwise NOT (~B)" disabled={disabled}>~B</button>
              <button onClick={() => onB("00000001")} title="Set to 0x00000001" disabled={disabled}>1</button>
              <button onClick={() => onB("FFFFFFFF")} title="All Ones (0xFFFFFFFF)" disabled={disabled}>0xFF</button>
            </div>
          </div>

          <div className="input-with-prefix">
            <span className="prefix">0x</span>
            <input
              id="operand-b-input"
              value={b}
              onChange={(e) =>
                onB(e.target.value.toUpperCase().replace(/[^0-9A-F]/g, "").slice(0, 8))
              }
              maxLength={8}
              placeholder="00000000"
              disabled={disabled}
              spellCheck={false}
            />
          </div>

          <div className="radix-preview-box">
            <div className="radix-item">
              <span className="radix-label"><Hash size={10} /> Signed Dec:</span>
              <span className="radix-val">{bDec.signed.toLocaleString()}</span>
            </div>
            <div className="radix-item">
              <span className="radix-label"><Binary size={10} /> Binary:</span>
              <span className="radix-bin-val" title={hexToBin(b)}>
                {formatBinGroups(hexToBin(b).slice(0, 16))}...
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Visual Opcode Matrix with Category Tabs */}
      <div className="opcode-selector-section">
        <div className="opcode-header">
          <label>
            <span>ALU INSTRUCTION OPCODES (11 RISC-V OPERATIONS)</span>
          </label>
          <div className="category-pills">
            {CATEGORIES.map((cat) => (
              <button
                key={cat.id}
                className={`cat-pill ${categoryFilter === cat.id ? "active" : ""}`}
                onClick={() => setCategoryFilter(cat.id)}
                type="button"
              >
                {cat.label}
              </button>
            ))}
          </div>
        </div>

        <div className="opcode-tiles-grid">
          {filteredOps.map((item) => {
            const isSelected = item.opcode === opcode;
            return (
              <button
                key={item.opcode}
                className={`opcode-tile ${isSelected ? "selected" : ""} cat-${item.category}`}
                onClick={() => onOpcode(item.opcode)}
                disabled={disabled}
                type="button"
              >
                <div className="tile-top">
                  <span className="tile-code">0x{item.opcode.toString(16).toUpperCase()}</span>
                  <span className="tile-symbol">{item.symbol}</span>
                </div>
                <strong className="tile-label">{item.label}</strong>
                <span className="tile-unit">{item.unit}</span>
              </button>
            );
          })}
        </div>

        <div className="active-opcode-banner">
          <Info size={13} className="text-emerald" />
          <span>
            Active Opcode <code>{selectedOp.opcode}</code> (<strong>{selectedOp.label}</strong>):{" "}
            {selectedOp.description} &rarr; Executing on <strong>{selectedOp.unit}</strong>
          </span>
        </div>
      </div>

      {/* Execution Buttons & Sequence Runner */}
      <div className="button-row">
        <button
          className="primary-button"
          onClick={onExecute}
          disabled={disabled || isAutoRunning}
        >
          <Play size={16} fill="currentColor" />{" "}
          {disabled ? "CLOCKING..." : "EXECUTE CYCLE"}
        </button>

        <button
          className="secondary-btn"
          onClick={handleAutoRun}
          disabled={disabled || isAutoRunning}
          title="Run automated benchmark sequence across 5 presets"
        >
          <FastForward size={14} /> {isAutoRunning ? "STEPPING..." : "AUTO SEQUENCE"}
        </button>

        <button
          className="icon-button"
          onClick={handleRandomize}
          disabled={disabled}
          title="Generate Random 32-bit Operands"
        >
          <Shuffle size={16} />
        </button>

        <button
          className="icon-button"
          onClick={onReset}
          disabled={disabled}
          title="Reset Telemetry Accumulators and Datapath State"
        >
          <RotateCcw size={16} />
        </button>
      </div>

      <div className="source-note">
        <span className="live-dot" /> Real-time Verilator hardware simulation subprocess
        <span className="muted"> · Microarchitecture: Hierarchical CLA + Booth-Wallace Multiplier</span>
      </div>
    </section>
  );
}