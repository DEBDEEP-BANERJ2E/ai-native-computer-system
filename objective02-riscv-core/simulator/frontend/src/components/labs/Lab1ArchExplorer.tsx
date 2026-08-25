import React, { useState } from "react";
import { Cpu, Shield, Layers, HelpCircle, FileCode, CheckCircle2, ChevronRight, X, Zap } from "lucide-react";
import { InteractiveDatapath } from "../InteractiveDatapath";
import { SimulationState } from "../../types";

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
  PC: {
    id: "PC",
    name: "Program Counter & Next-PC Unit",
    stage: "IF",
    phase: "Phase 1 & 2",
    sourceFile: "objective02/datapath/ProgramCounter.scala",
    role: "Maintains the 32-bit architectural Program Counter (PC). Increments by +4 on sequential fetch, or loads branch/jump targets from EX stage or trap vectors from MEM stage.",
    inputs: ["clock", "reset", "hazardUnit.io.stallIF", "branchJumpUnit.io.targetAddress", "takePreciseTrap", "trapVector"],
    outputs: ["io.pc (32-bit)", "nextPc (PC+4)"],
    designRationale: "Sequential fetch baseline. Priority mux selects takePreciseTrap > branchTaken > stallIF > PC+4."
  },
  IMEM: {
    id: "IMEM",
    name: "Instruction Memory ROM (VecInit)",
    stage: "IF",
    phase: "Phase 1",
    sourceFile: "objective02/memory/InstructionMemory.scala",
    role: "Stores the elaboration-time binary program in a high-speed synchronous ROM. Provides 32-bit instruction words to the IF/ID register.",
    inputs: ["address (from PC)"],
    outputs: ["instruction (32-bit)"],
    designRationale: "Bakes frozen verified binary programs at Chisel elaboration time to guarantee zero unintended runtime ROM mutation."
  },
  DECODER: {
    id: "DECODER",
    name: "Instruction Decoder & Immediate Generator",
    stage: "ID",
    phase: "Phase 1, 2 & 7",
    sourceFile: "objective02/decode/Decoder.scala, ImmediateGenerator.scala",
    role: "Decodes 32-bit RISC-V opcodes into ALU operations, register write enables, memory access controls, branch conditions, and custom CapabilityLite manipulation flags.",
    inputs: ["instruction (32-bit)"],
    outputs: ["aluOp", "regWrite", "memRead", "memWrite", "branch", "capOp", "immOut"],
    designRationale: "Custom-0 (0x0B) and Custom-1 (0x2B) capability opcodes are decoded alongside canonical RV32IM instructions."
  },
  REGFILE: {
    id: "REGFILE",
    name: "Integer Register File (x0–x31) with WB Bypass",
    stage: "ID",
    phase: "Phase 1 & 2",
    sourceFile: "objective02/datapath/RegisterFile.scala",
    role: "32 x 32-bit dual-read single-write register file. Hardwired x0=0. Features same-cycle WB->ID bypass to resolve same-cycle write/read RAW hazards without pipeline stalls.",
    inputs: ["rs1Addr", "rs2Addr", "rdAddr (WB)", "writeData (WB)", "regWrite (WB)"],
    outputs: ["rs1Data", "rs2Data"],
    designRationale: "WB->ID internal bypass provides immediate zero-cycle data availability to younger dependent instructions in ID."
  },
  CAPREGFILE: {
    id: "CAPREGFILE",
    name: "Capability Register File (c0–c7) — 101-Bit Bounded Registers",
    stage: "ID",
    phase: "Phase 7 & 8",
    sourceFile: "objective02/capability/CapabilityRegFile.scala",
    role: "Stores 8 x 101-bit capability registers (tag, base, length, perms, offset). Hardwires immutable roots c0(NULL), c1(RAM), c2(MMIO). Protects process registers c3–c7.",
    inputs: ["cs1Addr", "cdAddr (WB)", "writeCapData (WB)", "capWrite (WB)"],
    outputs: ["cs1Data (101-bit)"],
    designRationale: "Hardware-enforced root immutability permanently protects RAM and MMIO roots from software modification or corruption."
  },
  ALU: {
    id: "ALU",
    name: "32-Bit Arithmetic Logic Unit (Objective 1 HCLA & ALU)",
    stage: "EX",
    phase: "Phase 1 & 2",
    sourceFile: "objective01-digital-logic/src/main/scala/datapath/ALU.scala",
    role: "Performs 32-bit arithmetic (ADD, SUB), logical operations (AND, OR, XOR), shifts (SLL, SRL, SRA), and comparisons (SLT, SLTU).",
    inputs: ["operandA (forwarded)", "operandB (forwarded)", "aluOp"],
    outputs: ["aluResult (32-bit)", "statusFlags (Zero, Carry, Overflow, Negative)"],
    designRationale: "Reuses Objective 1 4-bit block Hierarchical Carry-Lookahead Adder (HCLA) for low-latency addition."
  },
  MUL: {
    id: "MUL",
    name: "34-Bit Booth-Wallace High-Throughput Multiplier",
    stage: "EX",
    phase: "Phase 4",
    sourceFile: "objective02/execute/RV32MMultiplier.scala",
    role: "Single-cycle multiplier executing MUL, MULH, MULHSU, MULHU. 17 Radix-4 Booth groups reduce partial products via 3:2 Wallace Tree CSA.",
    inputs: ["operandA (34-bit sign/zero ext)", "operandB (34-bit sign/zero ext)", "mOp"],
    outputs: ["mulResult64 (68-bit product, low 64 bits extracted)"],
    designRationale: "Operands extended to 34 bits to simultaneously handle signed/unsigned combinations in a unified hardware tree."
  },
  DIV: {
    id: "DIV",
    name: "33-Cycle Iterative Restoring Divider with Trap Kill",
    stage: "EX",
    phase: "Phase 5 & 8",
    sourceFile: "objective02/execute/IterativeDivider.scala",
    role: "Multi-cycle divider executing DIV, DIVU, REM, REMU over 32 compute cycles + 1 done cycle. Features io.kill abort port to terminate on traps.",
    inputs: ["dividend", "divisor", "mOp", "divKill (takePreciseTrap || takeTrapReturn)"],
    outputs: ["quotient", "remainder", "io.busy", "io.done", "io.iteration"],
    designRationale: "io.kill port prevents pipeline deadlocks when a memory security exception occurs during an active division."
  },
  DATAMEM: {
    id: "DATAMEM",
    name: "Data Memory (4KB SRAM) with Atomic Suppression",
    stage: "MEM",
    phase: "Phase 3 & 7",
    sourceFile: "objective02/memory/DataMemory.scala",
    role: "4096-byte byte-addressable SRAM supporting LB, LH, LW, LBU, LHU, SB, SH, SW. Suppresses writeback when capability check fails.",
    inputs: ["effectiveAddress", "writeData", "memRead", "memWrite", "byteMask", "allowAccess"],
    outputs: ["readData (32-bit)"],
    designRationale: "Memory writes are atomically blocked before clock edge if CapabilityChecker denies access."
  },
  CAPCHECKER: {
    id: "CAPCHECKER",
    name: "CapabilityChecker (33-Bit Widened Bounds Checking)",
    stage: "MEM",
    phase: "Phase 7 & 8",
    sourceFile: "objective02/capability/CapabilityChecker.scala",
    role: "Verifies Tag, Bounds, and Permissions on protected CL*/CS* accesses using 33-bit widened arithmetic. Combinationally produces takePreciseTrap on violations.",
    inputs: ["activeCap (101-bit)", "imm", "accessLen", "isWrite"],
    outputs: ["io.allow", "securityEvent.valid", "securityEvent.reason", "securityEvent.accessType"],
    designRationale: "33-bit widened arithmetic prevents integer overflow wrapping attacks from bypassing bounds checks."
  },
  SYSTEM_MMIO: {
    id: "SYSTEM_MMIO",
    name: "System MMIO, Telemetry & Precise Trap Subsystem",
    stage: "MEM",
    phase: "Phase 6 & 8",
    sourceFile: "objective02/system/SystemMMIO.scala",
    role: "Memory-mapped register bank (0x80000000+) housing performance counters, Objective 1 telemetry, OS context registers (SCHED_HINT), and dedicated precise trap registers (TRAP_*).",
    inputs: ["address", "writeData", "isWrite", "nestedFault", "takeTrapReturn"],
    outputs: ["readData", "TRAP_ACTIVE", "TRAP_EPC", "TRAP_CAUSE", "TRAP_ADDR", "DOUBLE_FAULT"],
    designRationale: "Double-fault latch uses set-over-W1C priority to guarantee no nested security fault is ever lost."
  }
};

interface Lab1Props {
  state?: SimulationState | null;
}

export const Lab1ArchExplorer: React.FC<Lab1Props> = ({ state }) => {
  const [selectedModule, setSelectedModule] = useState<ModuleDetail>(MODULE_DETAILS["EX"] || MODULE_DETAILS["ALU"]);

  const handleSelectModuleFromDatapath = (moduleId: string) => {
    if (MODULE_DETAILS[moduleId]) {
      setSelectedModule(MODULE_DETAILS[moduleId]);
    }
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
      {/* Intro Banner */}
      <div className="glass-panel">
        <h2 style={{ fontSize: "18px", fontWeight: 700, marginBottom: "8px", color: "var(--accent-cyan)", display: "flex", alignItems: "center", gap: "8px" }}>
          <Layers size={20} />
          Lab 1: Full Processor Datapath Architecture Explorer & Circuit Probe
        </h2>
        <p style={{ color: "var(--text-secondary)", fontSize: "13px", lineHeight: "1.6" }}>
          Explore the complete 5-stage architectural datapath of the frozen <strong>Objective 2 RV32IM Pipelined Processor</strong>.
          Click any component on the visual schematic below to probe its internal signals, ports, and design rationale.
        </p>
      </div>

      {/* Interactive SVG Datapath Schematic */}
      {state && <InteractiveDatapath state={state} onSelectModule={handleSelectModuleFromDatapath} />}

      {/* Module Inspector Drawer */}
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
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1.2fr 1fr", gap: "20px", marginTop: "12px" }}>
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
