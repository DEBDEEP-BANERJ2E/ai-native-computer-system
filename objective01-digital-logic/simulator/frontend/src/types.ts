export type OpCategory = "arithmetic" | "logic" | "shift" | "compare" | "mul";

export type Operation = {
  label: string;
  opcode: number;
  unit: string;
  category: OpCategory;
  symbol: string;
  description: string;
};

export const OPERATIONS: Operation[] = [
  { label: "ADD", opcode: 0, unit: "Hierarchical CLA", category: "arithmetic", symbol: "+", description: "32-bit addition with hierarchical carry lookahead" },
  { label: "SUB", opcode: 1, unit: "Hierarchical CLA", category: "arithmetic", symbol: "−", description: "32-bit two's complement subtraction (A + ~B + 1)" },
  { label: "AND", opcode: 2, unit: "Logic fabric", category: "logic", symbol: "&", description: "32-bit bitwise conjunction" },
  { label: "OR", opcode: 3, unit: "Logic fabric", category: "logic", symbol: "|", description: "32-bit bitwise disjunction" },
  { label: "XOR", opcode: 4, unit: "Logic fabric", category: "logic", symbol: "^", description: "32-bit bitwise exclusive-OR" },
  { label: "SLL", opcode: 5, unit: "Shifter", category: "shift", symbol: "<<", description: "Shift Left Logical by B[4:0] bits" },
  { label: "SRL", opcode: 6, unit: "Shifter", category: "shift", symbol: ">>", description: "Shift Right Logical by B[4:0] bits (zero fill)" },
  { label: "SRA", opcode: 7, unit: "Shifter", category: "shift", symbol: ">>>", description: "Shift Right Arithmetic by B[4:0] bits (sign extend)" },
  { label: "SLT", opcode: 8, unit: "Comparator", category: "compare", symbol: "< (s)", description: "Set Less Than (signed comparison: A < B ? 1 : 0)" },
  { label: "SLTU", opcode: 9, unit: "Comparator", category: "compare", symbol: "< (u)", description: "Set Less Than Unsigned (A < B ? 1 : 0)" },
  { label: "MUL", opcode: 10, unit: "Booth-Wallace", category: "mul", symbol: "×", description: "Signed Radix-4 Booth multiplication with Wallace tree reduction" },
];

export type TelemetryData = {
  rev_energy_acc: number;
  cla_switching: number;
  mul_thermal: number;
  edp_current: number;
  edp_config: number;
  [key: string]: number;
};

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
  telemetry: TelemetryData;
};

export type HistoryEntry = SimulationResponse & {
  cycle: number;
  a: number;
  b: number;
  operation: string;
};

export type PresetTestCase = {
  name: string;
  tag: string;
  a: string;
  b: string;
  opcode: number;
  explanation: string;
};

export const PRESET_TEST_CASES: PresetTestCase[] = [
  {
    name: "Signed Overflow Hazard",
    tag: "Overflow",
    a: "7FFFFFFF",
    b: "00000001",
    opcode: 0, // ADD
    explanation: "Max pos (2147483647) + 1 overflows to -2147483648 (V=1, N=1)",
  },
  {
    name: "Carry Generation Rollover",
    tag: "Rollover",
    a: "FFFFFFFF",
    b: "00000001",
    opcode: 0, // ADD
    explanation: "0xFFFFFFFF + 1 rolls over to 0 with Carry Out (C=1, Z=1)",
  },
  {
    name: "Signed Negative Multiply",
    tag: "Booth MUL",
    a: "FFFFFFFB",
    b: "00000007",
    opcode: 10, // MUL
    explanation: "Signed (-5) * 7 = -35 (0xFFFFFFDD) via Radix-4 Booth & Wallace Tree",
  },
  {
    name: "Max Magnitude Multiply",
    tag: "Stress",
    a: "7FFFFFFF",
    b: "00000002",
    opcode: 10, // MUL
    explanation: "Large multiplicand test verifying partial product compression",
  },
  {
    name: "Arithmetic Shift Sign Extend",
    tag: "Shifter",
    a: "80000000",
    b: "00000004",
    opcode: 7, // SRA
    explanation: "Shift negative MSB right by 4 bits replicating sign bit (0xF8000000)",
  },
  {
    name: "Signed vs Unsigned Comparison",
    tag: "Comparator",
    a: "FFFFFFFF",
    b: "00000001",
    opcode: 8, // SLT
    explanation: "Signed -1 < 1 is TRUE (1); change to SLTU and it is FALSE (0)",
  },
  {
    name: "Checkerboard Bit-Flip Stress",
    tag: "EDP Peak",
    a: "55555555",
    b: "AAAAAAAA",
    opcode: 4, // XOR
    explanation: "Alternating bits flipping all 32 bits, generating max switching & EDP",
  },
];