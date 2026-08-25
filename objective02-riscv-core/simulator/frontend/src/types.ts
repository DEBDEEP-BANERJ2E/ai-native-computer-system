export type EngineType = "rtl" | "reference";

export interface GPRRegister {
  reg: string;
  name: string;
  val: number;
  hex: string;
}

export interface CapabilityRegister {
  index: number;
  name: string;
  role: string;
  tag: number;
  base: number;
  length: number;
  perms: string;
  perms_raw: number;
  offset: number;
}

export interface PipelineStage {
  pc: number;
  valid: boolean;
  instruction: number;
  mnemonic: string;
  stalled?: boolean;
  flushed?: boolean;
  bubble?: boolean;
  aluResult?: number;
  memAddress?: number;
  memRead?: boolean;
  memWrite?: boolean;
  trapTaken?: boolean;
  rd?: number;
  regWrite?: boolean;
  writeData?: number;
}

export interface HardwareSignals {
  forwardA: number; // 0=None, 1=MEM/WB, 2=EX/MEM
  forwardB: number;
  loadUseHazard: boolean;
  capHazard: boolean;
  stallIF: boolean;
  stallID: boolean;
  flushIFID: boolean;
  flushIDEX: boolean;
  branchTaken: boolean;
  redirectTarget: number;
  mOp: number;
  mulActive: boolean;
  dividerBusy: boolean;
  dividerDone: boolean;
  dividerIterationRemaining: number;
  dividerIterationCompleted: number;
  schedHint: number;
  processBehaviorClass: number;
  currentContext: number;
  trapTaken: boolean;
  trapTarget: number;
  trapEpc: number;
  trapCause: number;
  trapAddr: number;
  trapActive: boolean;
  doubleFault: boolean;
}

export interface MMIORegisters {
  REV_ENERGY_ACC: number;
  CLA_SWITCHING: number;
  MUL_THERMAL: number;
  EDP_CURRENT: number;
  EDP_CONFIG: number;
  BRANCH_CONFIDENCE: number;
  PROCESS_BEHAVIOR_CLASS: number;
  SCHED_HINT: number;
  RETIRED_COUNT: number;
  BRANCH_TAKEN_COUNT: number;
  LOAD_USE_STALL_COUNT: number;
  DIV_BUSY_CYCLES: number;
  PIPELINE_STALL_COUNT: number;
  LAST_COMMIT_PC: number;
  CURRENT_CONTEXT: number;
  SEC_STATUS: number;
  SEC_PC: number;
  SEC_ADDR: number;
  SEC_INFO: number;
  SEC_CONTEXT: number;
  TRAP_CONTROL: number;
  TRAP_STATUS: number;
  TRAP_VECTOR: number;
  TRAP_EPC: number;
  TRAP_CAUSE: number;
  TRAP_ADDR: number;
  TRAP_CONTEXT: number;
}

export interface SimulationState {
  engine: EngineType;
  engine_title: string;
  scenario_id: string | null;
  cycle_count: number;
  instruction_count: number;
  cpi: number;
  gpr: GPRRegister[];
  capabilities: CapabilityRegister[];
  stages: {
    IF: PipelineStage;
    ID: PipelineStage;
    EX: PipelineStage;
    MEM: PipelineStage;
    WB: PipelineStage;
  };
  signals: HardwareSignals;
  mmio: MMIORegisters;
  halted: boolean;
  pc: number;
}

export interface ScenarioItem {
  id: string;
  title: string;
  lab: string;
  category: string;
  description: string;
  single_cycle_compatible: boolean;
  assembly: string;
}

export interface AssembledInstruction {
  pc: number;
  hex: string;
  raw: number;
  disasm: string;
  source: string;
}

export interface AssembleResponse {
  success: boolean;
  instructions?: AssembledInstruction[];
  error?: string;
}

export interface CoreComparisonData {
  compatible: boolean;
  reason?: string;
  scenario?: string;
  single_cycle?: {
    instructions: number;
    cycles: number;
    cpi: number;
    stalls: number;
    hazards: number;
  };
  pipelined?: {
    instructions: number;
    cycles: number;
    cpi: number;
    stalls: number;
    load_use_stalls: number;
    branch_flushes: number;
  };
}

export interface SimulatorManifest {
  objective: number;
  title: string;
  tag: string;
  commit: string;
  branch: string;
  engine_primary: string;
  engine_reference: string;
  verilator_version: string;
  architecture: string;
  security: string;
  telemetry: string;
  verification: {
    chisel_tests: string;
    differential_parity: string;
    objective1_regression: string;
    rtl_generated: boolean;
  };
}
