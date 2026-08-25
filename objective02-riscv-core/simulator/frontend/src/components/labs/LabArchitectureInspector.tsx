import React, { useState } from "react";
import { FileCode, HelpCircle, ChevronRight, CheckCircle2, Shield, Cpu, Layers } from "lucide-react";

interface Snippet {
  id: string;
  name: string;
  file: string;
  code: string;
  whyDesignedThisWay: string;
}

const SNIPPETS: Snippet[] = [
  {
    id: "cap_checker",
    name: "CapabilityChecker (33-Bit Widened Bounds Checking)",
    file: "objective02/capability/CapabilityChecker.scala",
    code: `// 33-bit widened address calculation to prevent 32-bit integer overflow wrapping
val base33      = Cat(0.U(1.W), io.cap.base)
val length33    = Cat(0.U(1.W), io.cap.length)
val top33       = base33 + length33
val offset33    = Cat(0.U(1.W), io.cap.offset)
val imm33       = Cat(io.imm(31), io.imm) // sign-extended
val cursor33    = base33 + offset33
val effAddr33   = cursor33 + imm33
val accessEnd33 = effAddr33 + accessLen33

val boundsOk = (effAddr33 >= base33) && (accessEnd33 <= top33)
val permOk   = Mux(io.isWrite, io.cap.perms(1), io.cap.perms(0))

io.allow := io.cap.tag && boundsOk && permOk`,
    whyDesignedThisWay: "Why 33 bits? To eliminate integer overflow wrapping attacks. In 32-bit math, base (0xFFFFFF00) + offset (0x200) wraps to 0x100, which would falsely pass a naive bounds check (< top). 33-bit representation preserves the carry bit and guarantees strict mathematical containment."
  },
  {
    id: "precise_trap",
    name: "Combinational MEM-Stage Precise Trap Redirection",
    file: "objective02/pipeline/PipelinedCore.scala",
    code: `// Combinational MEM-stage trap redirect in the fault cycle
val takePreciseTrap =
  securityEvent.valid &&
  trapEnableReg &&
  !trapActiveReg

// Atomic writeback suppression for faulting instruction
memEnterWbValid := exMemReg.io.out.valid && !takePreciseTrap

// Immediate younger pipeline flush & divider kill
hazardUnit.io.trapTaken := takePreciseTrap
divRem.io.kill          := takePreciseTrap || takeTrapReturn`,
    whyDesignedThisWay: "Why combinational? If we waited for TRAP_ACTIVE to register on the next clock, a younger instruction in EX or ID would advance or corrupt memory. Combinational takePreciseTrap simultaneously redirects PC to TRAP_VECTOR, kills younger stages, and aborts any active divider in the exact same cycle as the fault."
  },
  {
    id: "double_fault",
    name: "Double-Fault Latching with Set-Over-W1C Priority",
    file: "objective02/system/SystemMMIO.scala",
    code: `// Set-over-W1C priority prevents lost faults when software clears concurrently
when (io.nestedFault) {
  doubleFaultReg := true.B // Fresh fault always wins
} .elsewhen (isWrite && isTrapStatusAddr && io.writeData(1)) {
  doubleFaultReg := false.B // W1C clear
}`,
    whyDesignedThisWay: "Why Set-over-W1C? If an OS trap handler executes a store with bit 1=1 to clear DOUBLE_FAULT in the exact same clock cycle that another nested fault occurs, the hardware gives priority to the fault. This guarantees no security violation is ever silently lost."
  },
  {
    id: "booth_groups",
    name: "34-Bit Radix-4 Booth Multiplier (17 Groups)",
    file: "objective01-digital-logic/src/main/scala/arithmetic/BoothMultiplier.scala",
    code: `// Objective 2 instantiates BoothWallaceMultiplier(34)
val width = 34
val groups = width / 2 // 34 / 2 = 17 Radix-4 Booth groups

val partialProducts = Wire(Vec(groups, UInt(width.W)))
// 17 partial products reduced via 3:2 Wallace Tree CSA to 68-bit full product`,
    whyDesignedThisWay: "Why 34 bits and 17 groups? To handle signed and unsigned RV32M operands simultaneously in a single datapath, operands are extended by 2 bits. 34 bits divided by 2 bits per radix-4 digit yields exactly 17 groups."
  }
];

export const LabArchitectureInspector: React.FC = () => {
  const [selectedSnippet, setSelectedSnippet] = useState<Snippet>(SNIPPETS[0]);

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "24px" }}>
      {/* Intro */}
      <div className="glass-panel">
        <h2 style={{ fontSize: "18px", fontWeight: 700, marginBottom: "8px", color: "var(--accent-purple)", display: "flex", alignItems: "center", gap: "8px" }}>
          <FileCode size={20} />
          Architecture Inspector & Oral Defense Guide
        </h2>
        <p style={{ color: "var(--text-secondary)", fontSize: "13px", lineHeight: "1.6" }}>
          Inspect curated, frozen Chisel code snippets and read the formal architectural defense rationale
          for critical design choices (bounds arithmetic, exception timing, hazard interlocks).
        </p>
      </div>

      {/* Hardware Architecture Schematic Reference */}
      <div className="glass-panel" style={{ background: "#0d1117", textAlign: "center" }}>
        <div className="panel-header" style={{ marginBottom: "12px" }}>
          <span className="panel-title" style={{ color: "var(--accent-cyan)" }}>
            <FileCode size={16} color="var(--accent-cyan)" />
            Top-Level RV32IM Pipelined Processor & Capability Security Architecture
          </span>
          <span style={{ fontSize: "11px", color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>
            Hardware System Blueprint
          </span>
        </div>
        <div style={{ padding: "8px", background: "#fff", borderRadius: "10px", overflow: "hidden", display: "inline-block", maxWidth: "100%", boxShadow: "0 8px 32px rgba(0,0,0,0.6)" }}>
          <img
            src="/rv32im_capability_architecture.png"
            alt="Hardware Architecture Blueprint"
            style={{ width: "100%", maxWidth: "1050px", height: "auto", display: "block", borderRadius: "6px" }}
          />
        </div>
      </div>

      {/* Snippet Picker */}
      <div style={{ display: "flex", gap: "10px", flexWrap: "wrap" }}>
        {SNIPPETS.map((s) => (
          <button
            key={s.id}
            className={`btn ${selectedSnippet.id === s.id ? "btn-primary" : "btn-secondary"}`}
            onClick={() => setSelectedSnippet(s)}
          >
            {s.name}
          </button>
        ))}
      </div>

      {/* Code & Rationale Card */}
      <div style={{ display: "grid", gridTemplateColumns: "1.2fr 1fr", gap: "20px" }}>
        {/* Source Code View */}
        <div className="glass-panel">
          <div className="panel-header">
            <div>
              <span className="panel-title">{selectedSnippet.name}</span>
              <div style={{ fontSize: "11px", color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>
                {selectedSnippet.file}
              </div>
            </div>
          </div>

          <pre
            style={{
              background: "#0d1117",
              padding: "16px",
              borderRadius: "8px",
              color: "var(--text-code)",
              fontFamily: "var(--font-mono)",
              fontSize: "12px",
              lineHeight: "1.5",
              overflowX: "auto",
              border: "1px solid var(--border-subtle)",
            }}
          >
            {selectedSnippet.code}
          </pre>
        </div>

        {/* Defense Rationale Card */}
        <div className="glass-panel" style={{ borderLeft: "4px solid var(--accent-purple)" }}>
          <div className="panel-header">
            <span className="panel-title">
              <HelpCircle size={16} color="var(--accent-purple)" />
              Oral Defense Architectural Defense
            </span>
          </div>

          <div style={{ fontSize: "13px", color: "var(--text-primary)", lineHeight: "1.7", background: "rgba(0,0,0,0.3)", padding: "16px", borderRadius: "8px" }}>
            {selectedSnippet.whyDesignedThisWay}
          </div>
        </div>
      </div>
    </div>
  );
};
