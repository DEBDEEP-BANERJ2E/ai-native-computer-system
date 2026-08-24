module ProgramCounter(
  input         clock,
  input         reset,
  output [31:0] io_pc // @[src/main/scala/objective02/datapath/ProgramCounter.scala 14:14]
);
`ifdef RANDOMIZE_REG_INIT
  reg [31:0] _RAND_0;
`endif // RANDOMIZE_REG_INIT
  reg [31:0] pcReg; // @[src/main/scala/objective02/datapath/ProgramCounter.scala 16:22]
  wire [31:0] nextPc = pcReg + 32'h4; // @[src/main/scala/objective02/datapath/ProgramCounter.scala 25:21]
  assign io_pc = pcReg; // @[src/main/scala/objective02/datapath/ProgramCounter.scala 30:9]
  always @(posedge clock) begin
    if (reset) begin // @[src/main/scala/objective02/datapath/ProgramCounter.scala 16:22]
      pcReg <= 32'h0; // @[src/main/scala/objective02/datapath/ProgramCounter.scala 16:22]
    end else begin
      pcReg <= nextPc; // @[src/main/scala/objective02/datapath/ProgramCounter.scala 28:9]
    end
  end
// Register and memory initialization
`ifdef RANDOMIZE_GARBAGE_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_INVALID_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_REG_INIT
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_MEM_INIT
`define RANDOMIZE
`endif
`ifndef RANDOM
`define RANDOM $random
`endif
`ifdef RANDOMIZE_MEM_INIT
  integer initvar;
`endif
`ifndef SYNTHESIS
`ifdef FIRRTL_BEFORE_INITIAL
`FIRRTL_BEFORE_INITIAL
`endif
initial begin
  `ifdef RANDOMIZE
    `ifdef INIT_RANDOM
      `INIT_RANDOM
    `endif
    `ifndef VERILATOR
      `ifdef RANDOMIZE_DELAY
        #`RANDOMIZE_DELAY begin end
      `else
        #0.002 begin end
      `endif
    `endif
`ifdef RANDOMIZE_REG_INIT
  _RAND_0 = {1{`RANDOM}};
  pcReg = _RAND_0[31:0];
`endif // RANDOMIZE_REG_INIT
  `endif // RANDOMIZE
end // initial
`ifdef FIRRTL_AFTER_INITIAL
`FIRRTL_AFTER_INITIAL
`endif
`endif // SYNTHESIS
endmodule
module ImmediateGenerator(
  output [31:0] io_immI, // @[src/main/scala/objective02/decode/ImmediateGenerator.scala 20:14]
  output [31:0] io_immOut // @[src/main/scala/objective02/decode/ImmediateGenerator.scala 20:14]
);
  assign io_immI = 32'h0; // @[src/main/scala/objective02/decode/ImmediateGenerator.scala 55:8]
  assign io_immOut = io_immI; // @[src/main/scala/objective02/decode/ImmediateGenerator.scala 38:40]
endmodule
module Decoder(
  output [31:0] io_imm // @[src/main/scala/objective02/decode/Decoder.scala 18:14]
);
  wire [31:0] immGen_io_immI; // @[src/main/scala/objective02/decode/Decoder.scala 30:22]
  wire [31:0] immGen_io_immOut; // @[src/main/scala/objective02/decode/Decoder.scala 30:22]
  ImmediateGenerator immGen ( // @[src/main/scala/objective02/decode/Decoder.scala 30:22]
    .io_immI(immGen_io_immI),
    .io_immOut(immGen_io_immOut)
  );
  assign io_imm = immGen_io_immOut; // @[src/main/scala/objective02/decode/Decoder.scala 32:10]
endmodule
module CLA4(
  input  [3:0] io_a, // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 6:14]
  input  [3:0] io_b, // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 6:14]
  input        io_carryIn, // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 6:14]
  output [3:0] io_sum, // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 6:14]
  output       io_groupPropagate, // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 6:14]
  output       io_groupGenerate // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 6:14]
);
  wire  propagate_0 = io_a[0] ^ io_b[0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 23:33]
  wire  generate_0 = io_a[0] & io_b[0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 24:32]
  wire  inputCarryTerm = propagate_0 & io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 28:67]
  wire  carries_1 = generate_0 | inputCarryTerm; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 29:96]
  wire  sums_0 = propagate_0 ^ io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 30:33]
  wire  propagate_1 = io_a[1] ^ io_b[1]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 23:33]
  wire  generate_1 = io_a[1] & io_b[1]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 24:32]
  wire  generatedTerms_0 = propagate_1 & generate_0; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 26:59]
  wire  inputCarryTerm_1 = propagate_0 & propagate_1 & io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 28:67]
  wire  carries_2 = generate_1 | generatedTerms_0 | inputCarryTerm_1; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 29:96]
  wire  sums_1 = propagate_1 ^ carries_1; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 30:33]
  wire  propagate_2 = io_a[2] ^ io_b[2]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 23:33]
  wire  generate_2 = io_a[2] & io_b[2]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 24:32]
  wire  generatedTerms_0_1 = propagate_1 & propagate_2 & generate_0; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 26:59]
  wire  generatedTerms_1 = propagate_2 & generate_1; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 26:59]
  wire  _inputCarryTerm_T_2 = propagate_0 & propagate_1 & propagate_2; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 28:61]
  wire  inputCarryTerm_2 = propagate_0 & propagate_1 & propagate_2 & io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 28:67]
  wire  carries_3 = generate_2 | generatedTerms_0_1 | generatedTerms_1 | inputCarryTerm_2; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 29:96]
  wire  sums_2 = propagate_2 ^ carries_2; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 30:33]
  wire  propagate_3 = io_a[3] ^ io_b[3]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 23:33]
  wire  generate_3 = io_a[3] & io_b[3]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 24:32]
  wire  generatedTerms_2 = propagate_3 & generate_2; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 26:59]
  wire  sums_3 = propagate_3 ^ carries_3; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 30:33]
  wire [1:0] io_sum_lo = {sums_1,sums_0}; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 33:18]
  wire [1:0] io_sum_hi = {sums_3,sums_2}; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 33:18]
  wire  _io_groupGenerate_T_1 = generate_3 | generatedTerms_2; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 36:35]
  wire  _io_groupGenerate_T_2 = propagate_3 & propagate_2; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 38:19]
  wire  _io_groupGenerate_T_3 = propagate_3 & propagate_2 & generate_1; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 38:35]
  wire  _io_groupGenerate_T_4 = _io_groupGenerate_T_1 | _io_groupGenerate_T_3; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 37:35]
  wire  _io_groupGenerate_T_7 = _io_groupGenerate_T_2 & propagate_1 & generate_0; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 39:51]
  assign io_sum = {io_sum_hi,io_sum_lo}; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 33:18]
  assign io_groupPropagate = _inputCarryTerm_T_2 & propagate_3; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 35:43]
  assign io_groupGenerate = _io_groupGenerate_T_4 | _io_groupGenerate_T_7; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 38:51]
endmodule
module HierarchicalCarryLookaheadAdder(
  input  [31:0] io_a, // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 46:14]
  input  [31:0] io_b, // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 46:14]
  input         io_carryIn, // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 46:14]
  output [31:0] io_sum // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 46:14]
);
  wire [3:0] cla4_io_a; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_io_sum; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_io_groupGenerate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_1_io_a; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_1_io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_1_io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_1_io_sum; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_1_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_1_io_groupGenerate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_2_io_a; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_2_io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_2_io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_2_io_sum; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_2_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_2_io_groupGenerate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_3_io_a; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_3_io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_3_io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_3_io_sum; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_3_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_3_io_groupGenerate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_4_io_a; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_4_io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_4_io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_4_io_sum; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_4_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_4_io_groupGenerate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_5_io_a; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_5_io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_5_io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_5_io_sum; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_5_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_5_io_groupGenerate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_6_io_a; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_6_io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_6_io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_6_io_sum; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_6_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_6_io_groupGenerate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_7_io_a; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_7_io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_7_io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_7_io_sum; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_7_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_7_io_groupGenerate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  sumBits_0 = cla4_io_sum[0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_1 = cla4_io_sum[1]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_2 = cla4_io_sum[2]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_3 = cla4_io_sum[3]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  groupPropagate_0 = cla4_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 54:28 67:27]
  wire  inputCarryTerm = groupPropagate_0 & io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 76:74]
  wire  groupGenerate_0 = cla4_io_groupGenerate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 55:27 68:26]
  wire  sumBits_4 = cla4_1_io_sum[0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_5 = cla4_1_io_sum[1]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_6 = cla4_1_io_sum[2]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_7 = cla4_1_io_sum[3]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  groupPropagate_1 = cla4_1_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 54:28 67:27]
  wire  generatedTerms_0 = groupPropagate_1 & groupGenerate_0; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 74:66]
  wire  inputCarryTerm_1 = groupPropagate_0 & groupPropagate_1 & io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 76:74]
  wire  groupGenerate_1 = cla4_1_io_groupGenerate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 55:27 68:26]
  wire  sumBits_8 = cla4_2_io_sum[0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_9 = cla4_2_io_sum[1]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_10 = cla4_2_io_sum[2]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_11 = cla4_2_io_sum[3]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  groupPropagate_2 = cla4_2_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 54:28 67:27]
  wire  generatedTerms_0_1 = groupPropagate_1 & groupPropagate_2 & groupGenerate_0; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 74:66]
  wire  generatedTerms_1 = groupPropagate_2 & groupGenerate_1; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 74:66]
  wire  inputCarryTerm_2 = groupPropagate_0 & groupPropagate_1 & groupPropagate_2 & io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 76:74]
  wire  groupGenerate_2 = cla4_2_io_groupGenerate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 55:27 68:26]
  wire  sumBits_12 = cla4_3_io_sum[0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_13 = cla4_3_io_sum[1]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_14 = cla4_3_io_sum[2]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_15 = cla4_3_io_sum[3]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  groupPropagate_3 = cla4_3_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 54:28 67:27]
  wire  generatedTerms_0_2 = groupPropagate_1 & groupPropagate_2 & groupPropagate_3 & groupGenerate_0; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 74:66]
  wire  generatedTerms_1_1 = groupPropagate_2 & groupPropagate_3 & groupGenerate_1; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 74:66]
  wire  generatedTerms_2 = groupPropagate_3 & groupGenerate_2; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 74:66]
  wire  inputCarryTerm_3 = groupPropagate_0 & groupPropagate_1 & groupPropagate_2 & groupPropagate_3 & io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 76:74]
  wire  groupGenerate_3 = cla4_3_io_groupGenerate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 55:27 68:26]
  wire  sumBits_16 = cla4_4_io_sum[0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_17 = cla4_4_io_sum[1]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_18 = cla4_4_io_sum[2]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_19 = cla4_4_io_sum[3]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  groupPropagate_4 = cla4_4_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 54:28 67:27]
  wire  generatedTerms_0_3 = groupPropagate_1 & groupPropagate_2 & groupPropagate_3 & groupPropagate_4 & groupGenerate_0
    ; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 74:66]
  wire  generatedTerms_1_2 = groupPropagate_2 & groupPropagate_3 & groupPropagate_4 & groupGenerate_1; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 74:66]
  wire  generatedTerms_2_1 = groupPropagate_3 & groupPropagate_4 & groupGenerate_2; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 74:66]
  wire  generatedTerms_3 = groupPropagate_4 & groupGenerate_3; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 74:66]
  wire  inputCarryTerm_4 = groupPropagate_0 & groupPropagate_1 & groupPropagate_2 & groupPropagate_3 & groupPropagate_4
     & io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 76:74]
  wire  groupGenerate_4 = cla4_4_io_groupGenerate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 55:27 68:26]
  wire  sumBits_20 = cla4_5_io_sum[0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_21 = cla4_5_io_sum[1]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_22 = cla4_5_io_sum[2]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_23 = cla4_5_io_sum[3]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  groupPropagate_5 = cla4_5_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 54:28 67:27]
  wire  generatedTerms_0_4 = groupPropagate_1 & groupPropagate_2 & groupPropagate_3 & groupPropagate_4 &
    groupPropagate_5 & groupGenerate_0; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 74:66]
  wire  generatedTerms_1_3 = groupPropagate_2 & groupPropagate_3 & groupPropagate_4 & groupPropagate_5 & groupGenerate_1
    ; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 74:66]
  wire  generatedTerms_2_2 = groupPropagate_3 & groupPropagate_4 & groupPropagate_5 & groupGenerate_2; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 74:66]
  wire  generatedTerms_3_1 = groupPropagate_4 & groupPropagate_5 & groupGenerate_3; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 74:66]
  wire  generatedTerms_4 = groupPropagate_5 & groupGenerate_4; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 74:66]
  wire  inputCarryTerm_5 = groupPropagate_0 & groupPropagate_1 & groupPropagate_2 & groupPropagate_3 & groupPropagate_4
     & groupPropagate_5 & io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 76:74]
  wire  groupGenerate_5 = cla4_5_io_groupGenerate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 55:27 68:26]
  wire  sumBits_24 = cla4_6_io_sum[0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_25 = cla4_6_io_sum[1]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_26 = cla4_6_io_sum[2]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_27 = cla4_6_io_sum[3]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  groupPropagate_6 = cla4_6_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 54:28 67:27]
  wire  generatedTerms_0_5 = groupPropagate_1 & groupPropagate_2 & groupPropagate_3 & groupPropagate_4 &
    groupPropagate_5 & groupPropagate_6 & groupGenerate_0; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 74:66]
  wire  generatedTerms_1_4 = groupPropagate_2 & groupPropagate_3 & groupPropagate_4 & groupPropagate_5 &
    groupPropagate_6 & groupGenerate_1; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 74:66]
  wire  generatedTerms_2_3 = groupPropagate_3 & groupPropagate_4 & groupPropagate_5 & groupPropagate_6 & groupGenerate_2
    ; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 74:66]
  wire  generatedTerms_3_2 = groupPropagate_4 & groupPropagate_5 & groupPropagate_6 & groupGenerate_3; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 74:66]
  wire  generatedTerms_4_1 = groupPropagate_5 & groupPropagate_6 & groupGenerate_4; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 74:66]
  wire  generatedTerms_5 = groupPropagate_6 & groupGenerate_5; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 74:66]
  wire  inputCarryTerm_6 = groupPropagate_0 & groupPropagate_1 & groupPropagate_2 & groupPropagate_3 & groupPropagate_4
     & groupPropagate_5 & groupPropagate_6 & io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 76:74]
  wire  groupGenerate_6 = cla4_6_io_groupGenerate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 55:27 68:26]
  wire  sumBits_28 = cla4_7_io_sum[0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_29 = cla4_7_io_sum[1]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_30 = cla4_7_io_sum[2]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_31 = cla4_7_io_sum[3]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire [7:0] io_sum_lo_lo = {sumBits_7,sumBits_6,sumBits_5,sumBits_4,sumBits_3,sumBits_2,sumBits_1,sumBits_0}; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 80:21]
  wire [15:0] io_sum_lo = {sumBits_15,sumBits_14,sumBits_13,sumBits_12,sumBits_11,sumBits_10,sumBits_9,sumBits_8,
    io_sum_lo_lo}; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 80:21]
  wire [7:0] io_sum_hi_lo = {sumBits_23,sumBits_22,sumBits_21,sumBits_20,sumBits_19,sumBits_18,sumBits_17,sumBits_16}; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 80:21]
  wire [15:0] io_sum_hi = {sumBits_31,sumBits_30,sumBits_29,sumBits_28,sumBits_27,sumBits_26,sumBits_25,sumBits_24,
    io_sum_hi_lo}; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 80:21]
  CLA4 cla4 ( // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
    .io_a(cla4_io_a),
    .io_b(cla4_io_b),
    .io_carryIn(cla4_io_carryIn),
    .io_sum(cla4_io_sum),
    .io_groupPropagate(cla4_io_groupPropagate),
    .io_groupGenerate(cla4_io_groupGenerate)
  );
  CLA4 cla4_1 ( // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
    .io_a(cla4_1_io_a),
    .io_b(cla4_1_io_b),
    .io_carryIn(cla4_1_io_carryIn),
    .io_sum(cla4_1_io_sum),
    .io_groupPropagate(cla4_1_io_groupPropagate),
    .io_groupGenerate(cla4_1_io_groupGenerate)
  );
  CLA4 cla4_2 ( // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
    .io_a(cla4_2_io_a),
    .io_b(cla4_2_io_b),
    .io_carryIn(cla4_2_io_carryIn),
    .io_sum(cla4_2_io_sum),
    .io_groupPropagate(cla4_2_io_groupPropagate),
    .io_groupGenerate(cla4_2_io_groupGenerate)
  );
  CLA4 cla4_3 ( // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
    .io_a(cla4_3_io_a),
    .io_b(cla4_3_io_b),
    .io_carryIn(cla4_3_io_carryIn),
    .io_sum(cla4_3_io_sum),
    .io_groupPropagate(cla4_3_io_groupPropagate),
    .io_groupGenerate(cla4_3_io_groupGenerate)
  );
  CLA4 cla4_4 ( // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
    .io_a(cla4_4_io_a),
    .io_b(cla4_4_io_b),
    .io_carryIn(cla4_4_io_carryIn),
    .io_sum(cla4_4_io_sum),
    .io_groupPropagate(cla4_4_io_groupPropagate),
    .io_groupGenerate(cla4_4_io_groupGenerate)
  );
  CLA4 cla4_5 ( // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
    .io_a(cla4_5_io_a),
    .io_b(cla4_5_io_b),
    .io_carryIn(cla4_5_io_carryIn),
    .io_sum(cla4_5_io_sum),
    .io_groupPropagate(cla4_5_io_groupPropagate),
    .io_groupGenerate(cla4_5_io_groupGenerate)
  );
  CLA4 cla4_6 ( // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
    .io_a(cla4_6_io_a),
    .io_b(cla4_6_io_b),
    .io_carryIn(cla4_6_io_carryIn),
    .io_sum(cla4_6_io_sum),
    .io_groupPropagate(cla4_6_io_groupPropagate),
    .io_groupGenerate(cla4_6_io_groupGenerate)
  );
  CLA4 cla4_7 ( // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
    .io_a(cla4_7_io_a),
    .io_b(cla4_7_io_b),
    .io_carryIn(cla4_7_io_carryIn),
    .io_sum(cla4_7_io_sum),
    .io_groupPropagate(cla4_7_io_groupPropagate),
    .io_groupGenerate(cla4_7_io_groupGenerate)
  );
  assign io_sum = {io_sum_hi,io_sum_lo}; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 80:21]
  assign cla4_io_a = io_a[3:0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 64:22]
  assign cla4_io_b = io_b[3:0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 65:22]
  assign cla4_io_carryIn = io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 56:26 58:19]
  assign cla4_1_io_a = io_a[7:4]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 64:22]
  assign cla4_1_io_b = io_b[7:4]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 65:22]
  assign cla4_1_io_carryIn = groupGenerate_0 | inputCarryTerm; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 77:110]
  assign cla4_2_io_a = io_a[11:8]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 64:22]
  assign cla4_2_io_b = io_b[11:8]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 65:22]
  assign cla4_2_io_carryIn = groupGenerate_1 | generatedTerms_0 | inputCarryTerm_1; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 77:110]
  assign cla4_3_io_a = io_a[15:12]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 64:22]
  assign cla4_3_io_b = io_b[15:12]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 65:22]
  assign cla4_3_io_carryIn = groupGenerate_2 | generatedTerms_0_1 | generatedTerms_1 | inputCarryTerm_2; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 77:110]
  assign cla4_4_io_a = io_a[19:16]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 64:22]
  assign cla4_4_io_b = io_b[19:16]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 65:22]
  assign cla4_4_io_carryIn = groupGenerate_3 | generatedTerms_0_2 | generatedTerms_1_1 | generatedTerms_2 |
    inputCarryTerm_3; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 77:110]
  assign cla4_5_io_a = io_a[23:20]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 64:22]
  assign cla4_5_io_b = io_b[23:20]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 65:22]
  assign cla4_5_io_carryIn = groupGenerate_4 | generatedTerms_0_3 | generatedTerms_1_2 | generatedTerms_2_1 |
    generatedTerms_3 | inputCarryTerm_4; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 77:110]
  assign cla4_6_io_a = io_a[27:24]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 64:22]
  assign cla4_6_io_b = io_b[27:24]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 65:22]
  assign cla4_6_io_carryIn = groupGenerate_5 | generatedTerms_0_4 | generatedTerms_1_3 | generatedTerms_2_2 |
    generatedTerms_3_1 | generatedTerms_4 | inputCarryTerm_5; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 77:110]
  assign cla4_7_io_a = io_a[31:28]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 64:22]
  assign cla4_7_io_b = io_b[31:28]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 65:22]
  assign cla4_7_io_carryIn = groupGenerate_6 | generatedTerms_0_5 | generatedTerms_1_4 | generatedTerms_2_3 |
    generatedTerms_3_2 | generatedTerms_4_1 | generatedTerms_5 | inputCarryTerm_6; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 77:110]
endmodule
module ALU(
  input  [31:0] io_a, // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 24:14]
  input  [31:0] io_b, // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 24:14]
  output [31:0] io_result // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 24:14]
);
  wire [31:0] adder_io_a; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 39:21]
  wire [31:0] adder_io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 39:21]
  wire  adder_io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 39:21]
  wire [31:0] adder_io_sum; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 39:21]
  wire [31:0] subtractor_io_a; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 40:26]
  wire [31:0] subtractor_io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 40:26]
  wire  subtractor_io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 40:26]
  wire [31:0] subtractor_io_sum; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 40:26]
  wire [31:0] _io_result_T_6 = adder_io_sum; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 62:50]
  wire [62:0] _io_result_T_16 = {{31'd0}, _io_result_T_6}; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 62:50]
  HierarchicalCarryLookaheadAdder adder ( // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 39:21]
    .io_a(adder_io_a),
    .io_b(adder_io_b),
    .io_carryIn(adder_io_carryIn),
    .io_sum(adder_io_sum)
  );
  HierarchicalCarryLookaheadAdder subtractor ( // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 40:26]
    .io_a(subtractor_io_a),
    .io_b(subtractor_io_b),
    .io_carryIn(subtractor_io_carryIn),
    .io_sum(subtractor_io_sum)
  );
  assign io_result = _io_result_T_16[31:0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 62:13]
  assign adder_io_a = io_a; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 43:14]
  assign adder_io_b = io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 44:14]
  assign adder_io_carryIn = 1'h0; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 45:20]
  assign subtractor_io_a = io_a; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 47:19]
  assign subtractor_io_b = ~io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 48:23]
  assign subtractor_io_carryIn = 1'h1; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 49:25]
endmodule
module BranchJumpUnit(
  input  [31:0] io_pc, // @[src/main/scala/objective02/datapath/BranchJumpUnit.scala 20:14]
  input  [31:0] io_imm, // @[src/main/scala/objective02/datapath/BranchJumpUnit.scala 20:14]
  output [31:0] io_targetAddress // @[src/main/scala/objective02/datapath/BranchJumpUnit.scala 20:14]
);
  assign io_targetAddress = io_pc + io_imm; // @[src/main/scala/objective02/datapath/BranchJumpUnit.scala 50:26]
endmodule
module IterativeDivider(
  input        clock,
  input        reset,
  output       io_busy, // @[src/main/scala/objective02/execute/IterativeDivider.scala 22:14]
  output       io_done, // @[src/main/scala/objective02/execute/IterativeDivider.scala 22:14]
  output [5:0] io_iteration // @[src/main/scala/objective02/execute/IterativeDivider.scala 22:14]
);
`ifdef RANDOMIZE_REG_INIT
  reg [31:0] _RAND_0;
  reg [31:0] _RAND_1;
`endif // RANDOMIZE_REG_INIT
  reg [1:0] state; // @[src/main/scala/objective02/execute/IterativeDivider.scala 26:22]
  reg [5:0] count; // @[src/main/scala/objective02/execute/IterativeDivider.scala 27:22]
  wire [5:0] _count_T_1 = count - 6'h1; // @[src/main/scala/objective02/execute/IterativeDivider.scala 116:24]
  assign io_busy = state != 2'h0; // @[src/main/scala/objective02/execute/IterativeDivider.scala 52:20]
  assign io_done = state == 2'h2; // @[src/main/scala/objective02/execute/IterativeDivider.scala 53:20]
  assign io_iteration = count; // @[src/main/scala/objective02/execute/IterativeDivider.scala 56:16]
  always @(posedge clock) begin
    if (reset) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 26:22]
      state <= 2'h0; // @[src/main/scala/objective02/execute/IterativeDivider.scala 26:22]
    end else if (!(2'h0 == state)) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 75:19]
      if (2'h1 == state) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 75:19]
        if (count == 6'h1) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 117:29]
          state <= 2'h2; // @[src/main/scala/objective02/execute/IterativeDivider.scala 118:17]
        end
      end else if (2'h2 == state) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 75:19]
        state <= 2'h0; // @[src/main/scala/objective02/execute/IterativeDivider.scala 129:15]
      end
    end
    if (reset) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 27:22]
      count <= 6'h0; // @[src/main/scala/objective02/execute/IterativeDivider.scala 27:22]
    end else if (!(2'h0 == state)) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 75:19]
      if (2'h1 == state) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 75:19]
        count <= _count_T_1; // @[src/main/scala/objective02/execute/IterativeDivider.scala 116:15]
      end
    end
  end
// Register and memory initialization
`ifdef RANDOMIZE_GARBAGE_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_INVALID_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_REG_INIT
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_MEM_INIT
`define RANDOMIZE
`endif
`ifndef RANDOM
`define RANDOM $random
`endif
`ifdef RANDOMIZE_MEM_INIT
  integer initvar;
`endif
`ifndef SYNTHESIS
`ifdef FIRRTL_BEFORE_INITIAL
`FIRRTL_BEFORE_INITIAL
`endif
initial begin
  `ifdef RANDOMIZE
    `ifdef INIT_RANDOM
      `INIT_RANDOM
    `endif
    `ifndef VERILATOR
      `ifdef RANDOMIZE_DELAY
        #`RANDOMIZE_DELAY begin end
      `else
        #0.002 begin end
      `endif
    `endif
`ifdef RANDOMIZE_REG_INIT
  _RAND_0 = {1{`RANDOM}};
  state = _RAND_0[1:0];
  _RAND_1 = {1{`RANDOM}};
  count = _RAND_1[5:0];
`endif // RANDOMIZE_REG_INIT
  `endif // RANDOMIZE
end // initial
`ifdef FIRRTL_AFTER_INITIAL
`FIRRTL_AFTER_INITIAL
`endif
`endif // SYNTHESIS
endmodule
module IF_ID_Register(
  input         clock,
  input         reset,
  input         io_in_valid, // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 19:14]
  input  [31:0] io_in_pc, // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 19:14]
  output        io_out_valid, // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 19:14]
  output [31:0] io_out_pc // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 19:14]
);
`ifdef RANDOMIZE_REG_INIT
  reg [31:0] _RAND_0;
  reg [31:0] _RAND_1;
`endif // RANDOMIZE_REG_INIT
  reg  reg_valid; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 26:20]
  reg [31:0] reg_pc; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 26:20]
  assign io_out_valid = reg_valid; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 44:10]
  assign io_out_pc = reg_pc; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 44:10]
  always @(posedge clock) begin
    if (reset) begin // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 26:20]
      reg_valid <= 1'h0; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 26:20]
    end else begin
      reg_valid <= io_in_valid;
    end
    if (reset) begin // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 26:20]
      reg_pc <= 32'h0; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 26:20]
    end else begin
      reg_pc <= io_in_pc;
    end
  end
// Register and memory initialization
`ifdef RANDOMIZE_GARBAGE_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_INVALID_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_REG_INIT
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_MEM_INIT
`define RANDOMIZE
`endif
`ifndef RANDOM
`define RANDOM $random
`endif
`ifdef RANDOMIZE_MEM_INIT
  integer initvar;
`endif
`ifndef SYNTHESIS
`ifdef FIRRTL_BEFORE_INITIAL
`FIRRTL_BEFORE_INITIAL
`endif
initial begin
  `ifdef RANDOMIZE
    `ifdef INIT_RANDOM
      `INIT_RANDOM
    `endif
    `ifndef VERILATOR
      `ifdef RANDOMIZE_DELAY
        #`RANDOMIZE_DELAY begin end
      `else
        #0.002 begin end
      `endif
    `endif
`ifdef RANDOMIZE_REG_INIT
  _RAND_0 = {1{`RANDOM}};
  reg_valid = _RAND_0[0:0];
  _RAND_1 = {1{`RANDOM}};
  reg_pc = _RAND_1[31:0];
`endif // RANDOMIZE_REG_INIT
  `endif // RANDOMIZE
end // initial
`ifdef FIRRTL_AFTER_INITIAL
`FIRRTL_AFTER_INITIAL
`endif
`endif // SYNTHESIS
endmodule
module ID_EX_Register(
  input         clock,
  input         reset,
  input         io_in_valid, // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 74:14]
  input  [31:0] io_in_pc, // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 74:14]
  input  [31:0] io_in_imm, // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 74:14]
  output        io_out_valid, // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 74:14]
  output [31:0] io_out_pc, // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 74:14]
  output [31:0] io_out_imm, // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 74:14]
  output [1:0]  io_out_controls_aluSrcA // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 74:14]
);
`ifdef RANDOMIZE_REG_INIT
  reg [31:0] _RAND_0;
  reg [31:0] _RAND_1;
  reg [31:0] _RAND_2;
  reg [31:0] _RAND_3;
`endif // RANDOMIZE_REG_INIT
  reg  reg_valid; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 81:20]
  reg [31:0] reg_pc; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 81:20]
  reg [31:0] reg_imm; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 81:20]
  reg [1:0] reg_controls_aluSrcA; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 81:20]
  assign io_out_valid = reg_valid; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 159:10]
  assign io_out_pc = reg_pc; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 159:10]
  assign io_out_imm = reg_imm; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 159:10]
  assign io_out_controls_aluSrcA = reg_controls_aluSrcA; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 159:10]
  always @(posedge clock) begin
    if (reset) begin // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 81:20]
      reg_valid <= 1'h0; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 81:20]
    end else begin
      reg_valid <= io_in_valid;
    end
    if (reset) begin // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 81:20]
      reg_pc <= 32'h0; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 81:20]
    end else begin
      reg_pc <= io_in_pc;
    end
    if (reset) begin // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 81:20]
      reg_imm <= 32'h0; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 81:20]
    end else begin
      reg_imm <= io_in_imm;
    end
    if (reset) begin // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 81:20]
      reg_controls_aluSrcA <= 2'h2; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 81:20]
    end else begin
      reg_controls_aluSrcA <= 2'h0;
    end
  end
// Register and memory initialization
`ifdef RANDOMIZE_GARBAGE_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_INVALID_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_REG_INIT
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_MEM_INIT
`define RANDOMIZE
`endif
`ifndef RANDOM
`define RANDOM $random
`endif
`ifdef RANDOMIZE_MEM_INIT
  integer initvar;
`endif
`ifndef SYNTHESIS
`ifdef FIRRTL_BEFORE_INITIAL
`FIRRTL_BEFORE_INITIAL
`endif
initial begin
  `ifdef RANDOMIZE
    `ifdef INIT_RANDOM
      `INIT_RANDOM
    `endif
    `ifndef VERILATOR
      `ifdef RANDOMIZE_DELAY
        #`RANDOMIZE_DELAY begin end
      `else
        #0.002 begin end
      `endif
    `endif
`ifdef RANDOMIZE_REG_INIT
  _RAND_0 = {1{`RANDOM}};
  reg_valid = _RAND_0[0:0];
  _RAND_1 = {1{`RANDOM}};
  reg_pc = _RAND_1[31:0];
  _RAND_2 = {1{`RANDOM}};
  reg_imm = _RAND_2[31:0];
  _RAND_3 = {1{`RANDOM}};
  reg_controls_aluSrcA = _RAND_3[1:0];
`endif // RANDOMIZE_REG_INIT
  `endif // RANDOMIZE
end // initial
`ifdef FIRRTL_AFTER_INITIAL
`FIRRTL_AFTER_INITIAL
`endif
`endif // SYNTHESIS
endmodule
module EX_MEM_Register(
  input         clock,
  input         reset,
  input         io_in_valid, // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 204:14]
  input  [31:0] io_in_pc, // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 204:14]
  input  [31:0] io_in_aluResult, // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 204:14]
  output        io_out_valid, // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 204:14]
  output [31:0] io_out_pc, // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 204:14]
  output [31:0] io_out_aluResult // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 204:14]
);
`ifdef RANDOMIZE_REG_INIT
  reg [31:0] _RAND_0;
  reg [31:0] _RAND_1;
  reg [31:0] _RAND_2;
`endif // RANDOMIZE_REG_INIT
  reg  reg_valid; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 211:20]
  reg [31:0] reg_pc; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 211:20]
  reg [31:0] reg_aluResult; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 211:20]
  assign io_out_valid = reg_valid; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 275:10]
  assign io_out_pc = reg_pc; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 275:10]
  assign io_out_aluResult = reg_aluResult; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 275:10]
  always @(posedge clock) begin
    if (reset) begin // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 211:20]
      reg_valid <= 1'h0; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 211:20]
    end else begin
      reg_valid <= io_in_valid;
    end
    if (reset) begin // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 211:20]
      reg_pc <= 32'h0; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 211:20]
    end else begin
      reg_pc <= io_in_pc;
    end
    if (reset) begin // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 211:20]
      reg_aluResult <= 32'h0; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 211:20]
    end else begin
      reg_aluResult <= io_in_aluResult;
    end
  end
// Register and memory initialization
`ifdef RANDOMIZE_GARBAGE_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_INVALID_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_REG_INIT
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_MEM_INIT
`define RANDOMIZE
`endif
`ifndef RANDOM
`define RANDOM $random
`endif
`ifdef RANDOMIZE_MEM_INIT
  integer initvar;
`endif
`ifndef SYNTHESIS
`ifdef FIRRTL_BEFORE_INITIAL
`FIRRTL_BEFORE_INITIAL
`endif
initial begin
  `ifdef RANDOMIZE
    `ifdef INIT_RANDOM
      `INIT_RANDOM
    `endif
    `ifndef VERILATOR
      `ifdef RANDOMIZE_DELAY
        #`RANDOMIZE_DELAY begin end
      `else
        #0.002 begin end
      `endif
    `endif
`ifdef RANDOMIZE_REG_INIT
  _RAND_0 = {1{`RANDOM}};
  reg_valid = _RAND_0[0:0];
  _RAND_1 = {1{`RANDOM}};
  reg_pc = _RAND_1[31:0];
  _RAND_2 = {1{`RANDOM}};
  reg_aluResult = _RAND_2[31:0];
`endif // RANDOMIZE_REG_INIT
  `endif // RANDOMIZE
end // initial
`ifdef FIRRTL_AFTER_INITIAL
`FIRRTL_AFTER_INITIAL
`endif
`endif // SYNTHESIS
endmodule
module MEM_WB_Register(
  input         clock,
  input         reset,
  input         io_in_valid, // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 319:14]
  input  [31:0] io_in_pc, // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 319:14]
  input  [31:0] io_in_aluResult, // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 319:14]
  input  [31:0] io_in_memAddress, // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 319:14]
  output        io_out_valid, // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 319:14]
  output [31:0] io_out_pc, // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 319:14]
  output [31:0] io_out_aluResult, // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 319:14]
  output [31:0] io_out_memAddress // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 319:14]
);
`ifdef RANDOMIZE_REG_INIT
  reg [31:0] _RAND_0;
  reg [31:0] _RAND_1;
  reg [31:0] _RAND_2;
  reg [31:0] _RAND_3;
`endif // RANDOMIZE_REG_INIT
  reg  reg_valid; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 326:20]
  reg [31:0] reg_pc; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 326:20]
  reg [31:0] reg_aluResult; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 326:20]
  reg [31:0] reg_memAddress; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 326:20]
  assign io_out_valid = reg_valid; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 384:10]
  assign io_out_pc = reg_pc; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 384:10]
  assign io_out_aluResult = reg_aluResult; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 384:10]
  assign io_out_memAddress = reg_memAddress; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 384:10]
  always @(posedge clock) begin
    if (reset) begin // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 326:20]
      reg_valid <= 1'h0; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 326:20]
    end else begin
      reg_valid <= io_in_valid;
    end
    if (reset) begin // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 326:20]
      reg_pc <= 32'h0; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 326:20]
    end else begin
      reg_pc <= io_in_pc;
    end
    if (reset) begin // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 326:20]
      reg_aluResult <= 32'h0; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 326:20]
    end else begin
      reg_aluResult <= io_in_aluResult;
    end
    if (reset) begin // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 326:20]
      reg_memAddress <= 32'h0; // @[src/main/scala/objective02/pipeline/PipelineRegisters.scala 326:20]
    end else begin
      reg_memAddress <= io_in_memAddress;
    end
  end
// Register and memory initialization
`ifdef RANDOMIZE_GARBAGE_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_INVALID_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_REG_INIT
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_MEM_INIT
`define RANDOMIZE
`endif
`ifndef RANDOM
`define RANDOM $random
`endif
`ifdef RANDOMIZE_MEM_INIT
  integer initvar;
`endif
`ifndef SYNTHESIS
`ifdef FIRRTL_BEFORE_INITIAL
`FIRRTL_BEFORE_INITIAL
`endif
initial begin
  `ifdef RANDOMIZE
    `ifdef INIT_RANDOM
      `INIT_RANDOM
    `endif
    `ifndef VERILATOR
      `ifdef RANDOMIZE_DELAY
        #`RANDOMIZE_DELAY begin end
      `else
        #0.002 begin end
      `endif
    `endif
`ifdef RANDOMIZE_REG_INIT
  _RAND_0 = {1{`RANDOM}};
  reg_valid = _RAND_0[0:0];
  _RAND_1 = {1{`RANDOM}};
  reg_pc = _RAND_1[31:0];
  _RAND_2 = {1{`RANDOM}};
  reg_aluResult = _RAND_2[31:0];
  _RAND_3 = {1{`RANDOM}};
  reg_memAddress = _RAND_3[31:0];
`endif // RANDOMIZE_REG_INIT
  `endif // RANDOMIZE
end // initial
`ifdef FIRRTL_AFTER_INITIAL
`FIRRTL_AFTER_INITIAL
`endif
`endif // SYNTHESIS
endmodule
module PipelinedCore(
  input         clock,
  input         reset,
  output        io_commit_valid, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_commit_pc, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_commit_instruction, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [4:0]  io_commit_rd, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_commit_regWrite, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_commit_writeData, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_commit_memRead, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_commit_memReadReq, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_commit_memWrite, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_commit_memWriteReq, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_commit_memAddress, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_commit_memWriteData, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_commit_illegal, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_stageIF_valid, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_stageIF_pc, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_stageIF_instruction, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_stageID_valid, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_stageID_pc, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_stageID_instruction, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_stageEX_valid, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_stageEX_pc, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_stageEX_instruction, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_stageMEM_valid, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_stageMEM_pc, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_stageMEM_instruction, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_stageWB_valid, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_stageWB_pc, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_stageWB_instruction, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_flushIFID, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_flushIDEX, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_branchTaken, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_redirectTarget, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [1:0]  io_forwardA, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [1:0]  io_forwardB, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_loadUseHazard, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_capHazard, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_stallIF, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_stallID, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [3:0]  io_mOp, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_mulActive, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_dividerBusy, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_dividerDone, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [5:0]  io_dividerIteration, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_schedHint, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_processBehaviorClass, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_currentContext, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_trap_trapTaken, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_trap_trapTarget, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_trap_trapEpc, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_trap_trapCause, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output [31:0] io_trap_trapAddr, // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
  output        io_trap_trapActive // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 100:14]
);
  wire  pc_clock; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 105:30]
  wire  pc_reset; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 105:30]
  wire [31:0] pc_io_pc; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 105:30]
  wire [31:0] decoder_io_imm; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 107:30]
  wire [31:0] alu_io_a; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 110:30]
  wire [31:0] alu_io_b; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 110:30]
  wire [31:0] alu_io_result; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 110:30]
  wire [31:0] bju_io_pc; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 111:30]
  wire [31:0] bju_io_imm; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 111:30]
  wire [31:0] bju_io_targetAddress; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 111:30]
  wire  divRem_clock; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 118:30]
  wire  divRem_reset; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 118:30]
  wire  divRem_io_busy; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 118:30]
  wire  divRem_io_done; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 118:30]
  wire [5:0] divRem_io_iteration; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 118:30]
  wire  ifIdReg_clock; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 121:24]
  wire  ifIdReg_reset; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 121:24]
  wire  ifIdReg_io_in_valid; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 121:24]
  wire [31:0] ifIdReg_io_in_pc; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 121:24]
  wire  ifIdReg_io_out_valid; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 121:24]
  wire [31:0] ifIdReg_io_out_pc; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 121:24]
  wire  idExReg_clock; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 122:24]
  wire  idExReg_reset; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 122:24]
  wire  idExReg_io_in_valid; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 122:24]
  wire [31:0] idExReg_io_in_pc; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 122:24]
  wire [31:0] idExReg_io_in_imm; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 122:24]
  wire  idExReg_io_out_valid; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 122:24]
  wire [31:0] idExReg_io_out_pc; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 122:24]
  wire [31:0] idExReg_io_out_imm; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 122:24]
  wire [1:0] idExReg_io_out_controls_aluSrcA; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 122:24]
  wire  exMemReg_clock; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 123:24]
  wire  exMemReg_reset; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 123:24]
  wire  exMemReg_io_in_valid; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 123:24]
  wire [31:0] exMemReg_io_in_pc; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 123:24]
  wire [31:0] exMemReg_io_in_aluResult; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 123:24]
  wire  exMemReg_io_out_valid; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 123:24]
  wire [31:0] exMemReg_io_out_pc; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 123:24]
  wire [31:0] exMemReg_io_out_aluResult; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 123:24]
  wire  memWbReg_clock; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 124:24]
  wire  memWbReg_reset; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 124:24]
  wire  memWbReg_io_in_valid; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 124:24]
  wire [31:0] memWbReg_io_in_pc; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 124:24]
  wire [31:0] memWbReg_io_in_aluResult; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 124:24]
  wire [31:0] memWbReg_io_in_memAddress; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 124:24]
  wire  memWbReg_io_out_valid; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 124:24]
  wire [31:0] memWbReg_io_out_pc; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 124:24]
  wire [31:0] memWbReg_io_out_aluResult; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 124:24]
  wire [31:0] memWbReg_io_out_memAddress; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 124:24]
  wire [31:0] _aluOperandA_T_3 = 2'h1 == idExReg_io_out_controls_aluSrcA ? idExReg_io_out_pc : 32'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 266:64]
  ProgramCounter pc ( // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 105:30]
    .clock(pc_clock),
    .reset(pc_reset),
    .io_pc(pc_io_pc)
  );
  Decoder decoder ( // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 107:30]
    .io_imm(decoder_io_imm)
  );
  ALU alu ( // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 110:30]
    .io_a(alu_io_a),
    .io_b(alu_io_b),
    .io_result(alu_io_result)
  );
  BranchJumpUnit bju ( // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 111:30]
    .io_pc(bju_io_pc),
    .io_imm(bju_io_imm),
    .io_targetAddress(bju_io_targetAddress)
  );
  IterativeDivider divRem ( // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 118:30]
    .clock(divRem_clock),
    .reset(divRem_reset),
    .io_busy(divRem_io_busy),
    .io_done(divRem_io_done),
    .io_iteration(divRem_io_iteration)
  );
  IF_ID_Register ifIdReg ( // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 121:24]
    .clock(ifIdReg_clock),
    .reset(ifIdReg_reset),
    .io_in_valid(ifIdReg_io_in_valid),
    .io_in_pc(ifIdReg_io_in_pc),
    .io_out_valid(ifIdReg_io_out_valid),
    .io_out_pc(ifIdReg_io_out_pc)
  );
  ID_EX_Register idExReg ( // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 122:24]
    .clock(idExReg_clock),
    .reset(idExReg_reset),
    .io_in_valid(idExReg_io_in_valid),
    .io_in_pc(idExReg_io_in_pc),
    .io_in_imm(idExReg_io_in_imm),
    .io_out_valid(idExReg_io_out_valid),
    .io_out_pc(idExReg_io_out_pc),
    .io_out_imm(idExReg_io_out_imm),
    .io_out_controls_aluSrcA(idExReg_io_out_controls_aluSrcA)
  );
  EX_MEM_Register exMemReg ( // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 123:24]
    .clock(exMemReg_clock),
    .reset(exMemReg_reset),
    .io_in_valid(exMemReg_io_in_valid),
    .io_in_pc(exMemReg_io_in_pc),
    .io_in_aluResult(exMemReg_io_in_aluResult),
    .io_out_valid(exMemReg_io_out_valid),
    .io_out_pc(exMemReg_io_out_pc),
    .io_out_aluResult(exMemReg_io_out_aluResult)
  );
  MEM_WB_Register memWbReg ( // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 124:24]
    .clock(memWbReg_clock),
    .reset(memWbReg_reset),
    .io_in_valid(memWbReg_io_in_valid),
    .io_in_pc(memWbReg_io_in_pc),
    .io_in_aluResult(memWbReg_io_in_aluResult),
    .io_in_memAddress(memWbReg_io_in_memAddress),
    .io_out_valid(memWbReg_io_out_valid),
    .io_out_pc(memWbReg_io_out_pc),
    .io_out_aluResult(memWbReg_io_out_aluResult),
    .io_out_memAddress(memWbReg_io_out_memAddress)
  );
  assign io_commit_valid = memWbReg_io_out_valid; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 584:26]
  assign io_commit_pc = memWbReg_io_out_pc; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 585:26]
  assign io_commit_instruction = 32'h13; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 586:26]
  assign io_commit_rd = 5'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 132:29 555:14]
  assign io_commit_regWrite = 1'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 556:53]
  assign io_commit_writeData = memWbReg_io_out_aluResult; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 548:59]
  assign io_commit_memRead = 1'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 590:26]
  assign io_commit_memReadReq = 1'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 591:26]
  assign io_commit_memWrite = 1'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 592:26]
  assign io_commit_memWriteReq = 1'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 593:26]
  assign io_commit_memAddress = memWbReg_io_out_memAddress; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 594:26]
  assign io_commit_memWriteData = 32'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 595:26]
  assign io_commit_illegal = 1'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 596:26]
  assign io_stageIF_valid = pc_io_pc < 32'h1000; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 188:27]
  assign io_stageIF_pc = pc_io_pc; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 602:26]
  assign io_stageIF_instruction = 32'h13; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 603:26]
  assign io_stageID_valid = ifIdReg_io_out_valid; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 605:26]
  assign io_stageID_pc = ifIdReg_io_out_pc; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 606:26]
  assign io_stageID_instruction = 32'h13; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 607:26]
  assign io_stageEX_valid = idExReg_io_out_valid; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 609:26]
  assign io_stageEX_pc = idExReg_io_out_pc; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 610:26]
  assign io_stageEX_instruction = 32'h13; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 611:26]
  assign io_stageMEM_valid = exMemReg_io_out_valid; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 613:27]
  assign io_stageMEM_pc = exMemReg_io_out_pc; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 614:27]
  assign io_stageMEM_instruction = 32'h13; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 615:27]
  assign io_stageWB_valid = memWbReg_io_out_valid; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 617:26]
  assign io_stageWB_pc = memWbReg_io_out_pc; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 618:26]
  assign io_stageWB_instruction = 32'h13; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 619:26]
  assign io_flushIFID = 1'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 177:43]
  assign io_flushIDEX = 1'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 178:43]
  assign io_branchTaken = 1'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 404:29]
  assign io_redirectTarget = bju_io_targetAddress; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 128:29 405:18]
  assign io_forwardA = 2'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 626:21]
  assign io_forwardB = 2'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 627:21]
  assign io_loadUseHazard = 1'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 628:21]
  assign io_capHazard = 1'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 629:21]
  assign io_stallIF = 1'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 175:41]
  assign io_stallID = 1'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 176:41]
  assign io_mOp = 4'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 633:23]
  assign io_mulActive = 1'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 634:23]
  assign io_dividerBusy = divRem_io_busy; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 635:23]
  assign io_dividerDone = divRem_io_done; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 636:23]
  assign io_dividerIteration = divRem_io_iteration; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 637:23]
  assign io_schedHint = 32'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 639:27]
  assign io_processBehaviorClass = 32'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 640:27]
  assign io_currentContext = 32'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 641:27]
  assign io_trap_trapTaken = 1'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 492:65]
  assign io_trap_trapTarget = 32'h800; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 645:22]
  assign io_trap_trapEpc = 32'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 646:22]
  assign io_trap_trapCause = 32'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 647:22]
  assign io_trap_trapAddr = 32'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 648:22]
  assign io_trap_trapActive = 1'h0; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 649:22]
  assign pc_clock = clock;
  assign pc_reset = reset;
  assign alu_io_a = 2'h2 == idExReg_io_out_controls_aluSrcA ? 32'h0 : _aluOperandA_T_3; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 266:64]
  assign alu_io_b = idExReg_io_out_imm; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 272:64]
  assign bju_io_pc = idExReg_io_out_pc; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 397:21]
  assign bju_io_imm = idExReg_io_out_imm; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 400:21]
  assign divRem_clock = clock;
  assign divRem_reset = reset;
  assign ifIdReg_clock = clock;
  assign ifIdReg_reset = reset;
  assign ifIdReg_io_in_valid = pc_io_pc < 32'h1000; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 188:27]
  assign ifIdReg_io_in_pc = pc_io_pc; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 201:29]
  assign idExReg_clock = clock;
  assign idExReg_reset = reset;
  assign idExReg_io_in_valid = ifIdReg_io_out_valid; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 220:40]
  assign idExReg_io_in_pc = ifIdReg_io_out_pc; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 221:29]
  assign idExReg_io_in_imm = decoder_io_imm; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 229:29]
  assign exMemReg_clock = clock;
  assign exMemReg_reset = reset;
  assign exMemReg_io_in_valid = idExReg_io_out_valid; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 431:52]
  assign exMemReg_io_in_pc = idExReg_io_out_pc; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 432:41]
  assign exMemReg_io_in_aluResult = alu_io_result; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 393:21]
  assign memWbReg_clock = clock;
  assign memWbReg_reset = reset;
  assign memWbReg_io_in_valid = exMemReg_io_out_valid; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 512:34]
  assign memWbReg_io_in_pc = exMemReg_io_out_pc; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 517:37]
  assign memWbReg_io_in_aluResult = exMemReg_io_out_aluResult; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 521:37]
  assign memWbReg_io_in_memAddress = exMemReg_io_out_aluResult; // @[src/main/scala/objective02/pipeline/PipelinedCore.scala 531:37]
endmodule
