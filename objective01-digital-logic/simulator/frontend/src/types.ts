export type Operation = { label: string; opcode: number; unit: string };

export const OPERATIONS: Operation[] = [
  { label: "ADD", opcode: 0, unit: "Hierarchical CLA" },
  { label: "SUB", opcode: 1, unit: "Hierarchical CLA" },
  { label: "AND", opcode: 2, unit: "Logic fabric" },
  { label: "OR", opcode: 3, unit: "Logic fabric" },
  { label: "XOR", opcode: 4, unit: "Logic fabric" },
  { label: "SLL", opcode: 5, unit: "Shifter" },
  { label: "SRL", opcode: 6, unit: "Shifter" },
  { label: "SRA", opcode: 7, unit: "Shifter" },
  { label: "SLT", opcode: 8, unit: "Comparator" },
  { label: "SLTU", opcode: 9, unit: "Comparator" },
  { label: "MUL", opcode: 10, unit: "Booth-Wallace" },
];

export type SimulationResponse = {
  result: number;
  zero: boolean;
  negative: boolean;
  carry: boolean;
  overflow: boolean;
  busy: boolean;
  done: boolean;
  valid: boolean;
  telemetry_data: number;
  telemetry: Record<string, number>;
};

export type HistoryEntry = SimulationResponse & { cycle: number; a: number; b: number; operation: string };