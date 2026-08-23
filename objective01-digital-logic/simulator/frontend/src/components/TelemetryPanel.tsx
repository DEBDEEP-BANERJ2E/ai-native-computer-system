import { Activity, Gauge, Thermometer, Zap } from "lucide-react";

interface RegMeta {
  key: string;
  label: string;
  sub: string;
  icon: typeof Zap;
  color: string;
  addrOffset: string;
}

const REGISTERS: RegMeta[] = [
  {
    key: "rev_energy_acc",
    label: "REV_ENERGY_ACC",
    sub: "Reversible counter (reserved, unwired in subsystem)",
    icon: Zap,
    color: "amber",
    addrOffset: "0x00",
  },
  {
    key: "cla_switching",
    label: "CLA_SWITCHING",
    sub: "Result-bus Hamming distance (ADD/SUB ops)",
    icon: Activity,
    color: "green",
    addrOffset: "0x04",
  },
  {
    key: "mul_thermal",
    label: "MUL_THERMAL",
    sub: "Result-bus Hamming distance (MUL ops)",
    icon: Thermometer,
    color: "orange",
    addrOffset: "0x08",
  },
  {
    key: "edp_current",
    label: "EDP_CURRENT",
    sub: "EDP activity proxy = (REV + CLA + MUL) × CONFIG",
    icon: Gauge,
    color: "blue",
    addrOffset: "0x0C",
  },
  {
    key: "edp_config",
    label: "EDP_CONFIG",
    sub: "Scale constant fixed to 1 (read-only prototype)",
    icon: Gauge,
    color: "slate",
    addrOffset: "0x10",
  },
];

function format(value: number) {
  return `0x${(value >>> 0).toString(16).padStart(8, "0").toUpperCase()}`;
}

export function TelemetryPanel({ telemetry }: { telemetry: Record<string, number> }) {
  const max = Math.max(1, ...REGISTERS.map(({ key }) => telemetry[key] ?? 0));

  return (
    <section className="panel telemetry-panel">
      <div className="panel-heading">
        <div>
          <span className="kicker">MMIO MONITOR</span>
          <h2>Hardware Telemetry</h2>
        </div>
        <span className="address-label">Base: 0x80001000</span>
      </div>

      <div className="register-list">
        {REGISTERS.map(({ key, label, sub, icon: Icon, color, addrOffset }) => {
          const value = telemetry[key] ?? 0;
          return (
            <div className="register-row" key={key}>
              <div className="register-icon">
                <Icon size={15} />
              </div>
              <div className="register-meta">
                <div>
                  <span>{label}</span>
                  <small>0x800010{addrOffset.slice(2)}</small>
                </div>
                <small className="reg-desc">{sub}</small>
                <div className="meter">
                  <i
                    className={color}
                    style={{ width: `${Math.max(3, (value / max) * 100)}%` }}
                  />
                </div>
              </div>
              <strong>{format(value)}</strong>
            </div>
          );
        })}
      </div>

      <div className="telemetry-foot">
        <span>RESULT-BUS ACTIVITY PROXIES</span>
        <span>READ ONLY · 5 MMIO REGS</span>
      </div>
    </section>
  );
}