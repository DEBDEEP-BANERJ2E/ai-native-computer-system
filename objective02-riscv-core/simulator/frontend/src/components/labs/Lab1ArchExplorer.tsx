import React, { useState } from "react";
import { Cpu, Shield, Layers, HelpCircle, FileCode, CheckCircle2, ChevronRight, X } from "lucide-react";

interface ModuleDetail {
  id: string;
  name: string;
  stage: string;
  phase: string;
  sourceFile: string;
  role: string;
  inputs: string[];
  outputs: string[];
  designRationale: string;
}

const MODULE_DETAILS: Record<string, ModuleDetail> = {
  IF: {
    id: "IF",
    name: "Instruction Fetch (IF) Stage & PC Unit",
    stage: "IF",
    phase: "Phase 1 & 2",
    sourceFile: "objective02/datapath/ProgramCounter.scala",
    role: "Maintains 32-bit architectural Program Counter (PC) and fetches 32-bit instruction words from InstructionMemory ROM. Evaluates stallIF and branch/trap redirects.",
    inputs: ["clock", "reset", "hazardUnit.io.stallIF", "branchJumpUnit.io.targetAddress", "takePreciseTrap", "trapVector"],
    outputs: ["pc", "nextPc (PC+4)", "instruction (32-bit raw)"],
    designRationale: "Sequential fetch baseline. If a branch is taken or a precise trap occurs in later stages, PC redirect target is selected combinationally with top priority over normal PC+4 increment."
  },
  ID: {
    id: "ID",
    name: "Instruction Decode (ID) & Register Files",
    stage: "ID",
    phase: "Phase 1, 2 & 7",
    sourceFile: "objective02/decode/Decoder.scala, RegisterFile.scala, CapabilityRegFile.scala",
    role: "Decodes 32-bit RISC-V opcodes, generates immediate values, reads rs1/rs2 from integer RegisterFile (x0-x31), and reads cs1 from CapabilityRegFile (c0-c7). Same-cycle WB->ID bypass avoids NOP stalls on register writes.",
    inputs: ["instruction", "regWrite/writeData (from WB)", "capWrite/writeCapData (from WB)"],
    outputs: ["controlSignals", "rs1Data", "rs2Data", "immOut", "capData (101-bit)"],
    designRationale: "Hardware-immutable roots (c0=NULL, c1=RAM, c2=MMIO) are permanently locked against software writes in CapabilityRegFile. Asynchronous read with synchronous writeback enables 0-cycle forwarding."
  },
  EX: {
    id: "EX",
    name: "Execute (EX) Stage: ALU, Multiplier & Divider",
    stage: "EX",
    phase: "Phase 2, 4 & 5",
    sourceFile: "objective02/execute/ALU.scala, RV32MMultiplier.scala, IterativeDivider.scala",
    role: "Executes 32-bit arithmetic/logical operations via Objective-1 HCLA & ALU, 34-bit signed/unsigned Radix-4 Booth-Wallace multiplication (68-bit product), and 33-cycle non-restoring iterative division. Evaluates branch conditions.",
    inputs: ["operandA (forwarded)", "operandB (forwarded)", "aluOp", "mOp", "divKill"],
    outputs: ["aluResult", "mulResult64", "divResult", "branchTaken", "redirectTarget"],
    designRationale: "Reuses Objective 1 arithmetic IP (Booth-Wallace tree with 17 Radix-4 groups and 3:2 reduction). Divider features an explicit io.kill port to immediately abort on traps and prevent pipeline deadlocks."
  },
  MEM: {
    id: "MEM",
    name: "Memory (MEM) Stage: DataMemory, MMIO & CapabilityChecker",
    stage: "MEM",
    phase: "Phase 3, 6, 7 & 8",
    sourceFile: "objective02/system/SystemMMIO.scala, CapabilityChecker.scala",
    role: "Accesses DataMemory (4KB) and SystemMMIO (0x80000000+). Performs 33-bit widened CapabilityLite Tag, Bounds, and Permission authorization. On violation, fires combinational takePreciseTrap and captures trap metadata.",
    inputs: ["effectiveAddress", "writeData", "memRead", "memWrite", "activeCap (101-bit)"],
    outputs: ["readData", "takePreciseTrap", "trapMetadata", "secAuditLog"],
    designRationale: "MEM is the single convergence point for all memory and capability security decisions. 33-bit widened bounds checking prevents integer overflow bypasses. Traps combinationally flush younger stages in the same cycle."
  },
  WB: {
    id: "WB",
    name: "Writeback (WB) Stage & Telemetry Retirement",
    stage: "WB",
    phase: "Phase 2 & 6",
    sourceFile: "objective02/pipeline/PipelinedCore.scala",
    role: "Commits instruction results to GPR (rd) and CapabilityRegFile (cd). Drives top-level architectural commit telemetry (RETIRED_COUNT, LAST_COMMIT_PC) and triggers Objective-1 CLA switching updates.",
    inputs: ["memWbReg.valid", "memWbReg.pc", "memWbReg.aluResult", "memWbReg.readData"],
    outputs: ["commit.valid", "commit.rd", "commit.writeData", "commit.pc"],
    designRationale: "Architectural commit is strictly separated from speculative execution. Faulting instructions are suppressed before entering WB (valid=0), guaranteeing that invalid operations leave zero architectural side effects."
  }
};

export const Lab1ArchExplorer: React.FC = () => {
  const [selectedModule, setSelectedModule] = useState<ModuleDetail | null>(MODULE_DETAILS["EX"]);

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "24px" }}>
      {/* Intro Banner */}
      <div className="glass-panel">
        <h2 style={{ fontSize: "18px", fontWeight: 700, marginBottom: "8px", color: "var(--accent-cyan)" }}>
          Lab 1: Processor Architecture Explorer & Theory of Operation
        </h2>
        <p style={{ color: "var(--text-secondary)", fontSize: "13px", lineHeight: "1.6" }}>
          Explore the internal architecture of the frozen <strong>Objective 2 RV32IM Pipelined Processor</strong>.
          Click any pipeline stage or datapath component below to inspect its Scala/Chisel source location,
          formal hardware boundary, and architectural design rationale.
        </p>
      </div>

      {/* Interactive 5-Stage Block Diagram */}
      <div className="glass-panel">
        <div className="panel-header">
          <span className="panel-title">
            <Layers size={16} color="var(--accent-cyan)" />
            Interactive 5-Stage Datapath Architecture
          </span>
          <span style={{ fontSize: "11px", color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>
            CLICK A STAGE TO INSPECT
          </span>
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: "16px", margin: "16px 0" }}>
          {Object.values(MODULE_DETAILS).map((mod) => {
            const isSelected = selectedModule?.id === mod.id;
            return (
              <div
                key={mod.id}
                onClick={() => setSelectedModule(mod)}
                style={{
                  background: isSelected ? "rgba(0, 245, 212, 0.12)" : "rgba(15, 23, 42, 0.6)",
                  border: `2px solid ${isSelected ? "var(--accent-cyan)" : "var(--border-subtle)"}`,
                  borderRadius: "12px",
                  padding: "16px",
                  cursor: "pointer",
                  transition: "all 0.2s ease",
                  display: "flex",
                  flexDirection: "column",
                  gap: "10px",
                  boxShadow: isSelected ? "var(--shadow-glow)" : "none",
                }}
              >
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <span style={{ fontWeight: 800, fontSize: "16px", fontFamily: "var(--font-mono)", color: isSelected ? "var(--accent-cyan)" : "#fff" }}>
                    {mod.stage}
                  </span>
                  <span style={{ fontSize: "10px", color: "var(--text-muted)" }}>{mod.phase}</span>
                </div>
                <div style={{ fontSize: "12px", color: "var(--text-secondary)", fontWeight: 500 }}>
                  {mod.name.split(":")[0]}
                </div>
                <div style={{ fontSize: "11px", color: "var(--text-muted)", fontFamily: "var(--font-mono)", marginTop: "auto" }}>
                  {mod.sourceFile.split("/").pop()}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Selected Module Detail Modal / Drawer */}
      {selectedModule && (
        <div className="glass-panel" style={{ borderLeft: "4px solid var(--accent-cyan)" }}>
          <div className="panel-header">
            <div>
              <h3 style={{ fontSize: "16px", fontWeight: 700, color: "var(--text-primary)" }}>
                {selectedModule.name}
              </h3>
              <div style={{ fontSize: "12px", color: "var(--accent-cyan)", fontFamily: "var(--font-mono)" }}>
                Source: {selectedModule.sourceFile} ({selectedModule.phase})
              </div>
            </div>
            <button
              className="btn btn-secondary"
              style={{ padding: "4px 8px" }}
              onClick={() => setSelectedModule(null)}
            >
              <X size={14} />
            </button>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "20px", marginTop: "16px" }}>
            <div>
              <h4 style={{ fontSize: "13px", fontWeight: 600, color: "var(--text-primary)", marginBottom: "6px" }}>
                Primary Function & Microarchitecture
              </h4>
              <p style={{ fontSize: "12px", color: "var(--text-secondary)", lineHeight: "1.6" }}>
                {selectedModule.role}
              </p>

              <h4 style={{ fontSize: "13px", fontWeight: 600, color: "var(--text-primary)", marginTop: "14px", marginBottom: "6px" }}>
                Architectural Design Rationale
              </h4>
              <p style={{ fontSize: "12px", color: "var(--text-secondary)", lineHeight: "1.6" }}>
                {selectedModule.designRationale}
              </p>
            </div>

            <div style={{ background: "rgba(0,0,0,0.3)", padding: "14px", borderRadius: "8px", border: "1px solid var(--border-subtle)" }}>
              <div style={{ marginBottom: "12px" }}>
                <span style={{ fontSize: "11px", fontWeight: 700, color: "var(--accent-emerald)", fontFamily: "var(--font-mono)" }}>
                  KEY INPUT PORTS:
                </span>
                <ul style={{ listStyleType: "disc", paddingLeft: "16px", fontSize: "11px", fontFamily: "var(--font-mono)", color: "var(--text-muted)", marginTop: "4px" }}>
                  {selectedModule.inputs.map((inp) => (
                    <li key={inp}>{inp}</li>
                  ))}
                </ul>
              </div>

              <div>
                <span style={{ fontSize: "11px", fontWeight: 700, color: "var(--accent-cyan)", fontFamily: "var(--font-mono)" }}>
                  KEY OUTPUT PORTS:
                </span>
                <ul style={{ listStyleType: "disc", paddingLeft: "16px", fontSize: "11px", fontFamily: "var(--font-mono)", color: "var(--text-muted)", marginTop: "4px" }}>
                  {selectedModule.outputs.map((out) => (
                    <li key={out}>{out}</li>
                  ))}
                </ul>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
