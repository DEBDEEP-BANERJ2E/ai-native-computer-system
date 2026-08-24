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
module RegisterFile(
  input   io_writeEnable // @[src/main/scala/objective02/datapath/RegisterFile.scala 21:14]
);
endmodule
module CLA4(
  input  [3:0] io_b, // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 6:14]
  input        io_carryIn, // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 6:14]
  output [3:0] io_sum, // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 6:14]
  output       io_groupPropagate // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 6:14]
);
  wire  propagate_0 = io_b[0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 23:39]
  wire  inputCarryTerm = propagate_0 & io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 28:67]
  wire  sums_0 = propagate_0 ^ io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 30:33]
  wire  propagate_1 = io_b[1]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 23:39]
  wire  inputCarryTerm_1 = propagate_0 & propagate_1 & io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 28:67]
  wire  sums_1 = propagate_1 ^ inputCarryTerm; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 30:33]
  wire  propagate_2 = io_b[2]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 23:39]
  wire  _inputCarryTerm_T_2 = propagate_0 & propagate_1 & propagate_2; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 28:61]
  wire  inputCarryTerm_2 = propagate_0 & propagate_1 & propagate_2 & io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 28:67]
  wire  sums_2 = propagate_2 ^ inputCarryTerm_1; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 30:33]
  wire  propagate_3 = io_b[3]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 23:39]
  wire  sums_3 = propagate_3 ^ inputCarryTerm_2; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 30:33]
  wire [1:0] io_sum_lo = {sums_1,sums_0}; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 33:18]
  wire [1:0] io_sum_hi = {sums_3,sums_2}; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 33:18]
  assign io_sum = {io_sum_hi,io_sum_lo}; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 33:18]
  assign io_groupPropagate = _inputCarryTerm_T_2 & propagate_3; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 35:43]
endmodule
module HierarchicalCarryLookaheadAdder(
  input  [31:0] io_b, // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 46:14]
  input         io_carryIn, // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 46:14]
  output [31:0] io_sum // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 46:14]
);
  wire [3:0] cla4_io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_io_sum; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_1_io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_1_io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_1_io_sum; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_1_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_2_io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_2_io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_2_io_sum; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_2_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_3_io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_3_io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_3_io_sum; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_3_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_4_io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_4_io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_4_io_sum; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_4_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_5_io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_5_io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_5_io_sum; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_5_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_6_io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_6_io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_6_io_sum; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_6_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_7_io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_7_io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire [3:0] cla4_7_io_sum; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  cla4_7_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
  wire  sumBits_0 = cla4_io_sum[0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_1 = cla4_io_sum[1]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_2 = cla4_io_sum[2]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_3 = cla4_io_sum[3]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  groupPropagate_0 = cla4_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 54:28 67:27]
  wire  sumBits_4 = cla4_1_io_sum[0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_5 = cla4_1_io_sum[1]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_6 = cla4_1_io_sum[2]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_7 = cla4_1_io_sum[3]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  groupPropagate_1 = cla4_1_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 54:28 67:27]
  wire  sumBits_8 = cla4_2_io_sum[0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_9 = cla4_2_io_sum[1]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_10 = cla4_2_io_sum[2]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_11 = cla4_2_io_sum[3]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  groupPropagate_2 = cla4_2_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 54:28 67:27]
  wire  sumBits_12 = cla4_3_io_sum[0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_13 = cla4_3_io_sum[1]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_14 = cla4_3_io_sum[2]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_15 = cla4_3_io_sum[3]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  groupPropagate_3 = cla4_3_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 54:28 67:27]
  wire  sumBits_16 = cla4_4_io_sum[0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_17 = cla4_4_io_sum[1]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_18 = cla4_4_io_sum[2]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_19 = cla4_4_io_sum[3]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  groupPropagate_4 = cla4_4_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 54:28 67:27]
  wire  sumBits_20 = cla4_5_io_sum[0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_21 = cla4_5_io_sum[1]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_22 = cla4_5_io_sum[2]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_23 = cla4_5_io_sum[3]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  groupPropagate_5 = cla4_5_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 54:28 67:27]
  wire  sumBits_24 = cla4_6_io_sum[0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_25 = cla4_6_io_sum[1]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_26 = cla4_6_io_sum[2]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  sumBits_27 = cla4_6_io_sum[3]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 70:40]
  wire  groupPropagate_6 = cla4_6_io_groupPropagate; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 54:28 67:27]
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
    .io_b(cla4_io_b),
    .io_carryIn(cla4_io_carryIn),
    .io_sum(cla4_io_sum),
    .io_groupPropagate(cla4_io_groupPropagate)
  );
  CLA4 cla4_1 ( // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
    .io_b(cla4_1_io_b),
    .io_carryIn(cla4_1_io_carryIn),
    .io_sum(cla4_1_io_sum),
    .io_groupPropagate(cla4_1_io_groupPropagate)
  );
  CLA4 cla4_2 ( // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
    .io_b(cla4_2_io_b),
    .io_carryIn(cla4_2_io_carryIn),
    .io_sum(cla4_2_io_sum),
    .io_groupPropagate(cla4_2_io_groupPropagate)
  );
  CLA4 cla4_3 ( // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
    .io_b(cla4_3_io_b),
    .io_carryIn(cla4_3_io_carryIn),
    .io_sum(cla4_3_io_sum),
    .io_groupPropagate(cla4_3_io_groupPropagate)
  );
  CLA4 cla4_4 ( // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
    .io_b(cla4_4_io_b),
    .io_carryIn(cla4_4_io_carryIn),
    .io_sum(cla4_4_io_sum),
    .io_groupPropagate(cla4_4_io_groupPropagate)
  );
  CLA4 cla4_5 ( // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
    .io_b(cla4_5_io_b),
    .io_carryIn(cla4_5_io_carryIn),
    .io_sum(cla4_5_io_sum),
    .io_groupPropagate(cla4_5_io_groupPropagate)
  );
  CLA4 cla4_6 ( // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
    .io_b(cla4_6_io_b),
    .io_carryIn(cla4_6_io_carryIn),
    .io_sum(cla4_6_io_sum),
    .io_groupPropagate(cla4_6_io_groupPropagate)
  );
  CLA4 cla4_7 ( // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 63:22]
    .io_b(cla4_7_io_b),
    .io_carryIn(cla4_7_io_carryIn),
    .io_sum(cla4_7_io_sum),
    .io_groupPropagate(cla4_7_io_groupPropagate)
  );
  assign io_sum = {io_sum_hi,io_sum_lo}; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 80:21]
  assign cla4_io_b = io_b[3:0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 65:22]
  assign cla4_io_carryIn = io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 56:26 58:19]
  assign cla4_1_io_b = io_b[7:4]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 65:22]
  assign cla4_1_io_carryIn = groupPropagate_0 & io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 76:74]
  assign cla4_2_io_b = io_b[11:8]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 65:22]
  assign cla4_2_io_carryIn = groupPropagate_0 & groupPropagate_1 & io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 76:74]
  assign cla4_3_io_b = io_b[15:12]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 65:22]
  assign cla4_3_io_carryIn = groupPropagate_0 & groupPropagate_1 & groupPropagate_2 & io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 76:74]
  assign cla4_4_io_b = io_b[19:16]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 65:22]
  assign cla4_4_io_carryIn = groupPropagate_0 & groupPropagate_1 & groupPropagate_2 & groupPropagate_3 & io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 76:74]
  assign cla4_5_io_b = io_b[23:20]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 65:22]
  assign cla4_5_io_carryIn = groupPropagate_0 & groupPropagate_1 & groupPropagate_2 & groupPropagate_3 &
    groupPropagate_4 & io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 76:74]
  assign cla4_6_io_b = io_b[27:24]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 65:22]
  assign cla4_6_io_carryIn = groupPropagate_0 & groupPropagate_1 & groupPropagate_2 & groupPropagate_3 &
    groupPropagate_4 & groupPropagate_5 & io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 76:74]
  assign cla4_7_io_b = io_b[31:28]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 65:22]
  assign cla4_7_io_carryIn = groupPropagate_0 & groupPropagate_1 & groupPropagate_2 & groupPropagate_3 &
    groupPropagate_4 & groupPropagate_5 & groupPropagate_6 & io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/arithmetic/HierarchicalCarryLookaheadAdder.scala 76:74]
endmodule
module ALU(
  input  [31:0] io_b, // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 24:14]
  output [31:0] io_result // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 24:14]
);
  wire [31:0] adder_io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 39:21]
  wire  adder_io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 39:21]
  wire [31:0] adder_io_sum; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 39:21]
  wire [31:0] subtractor_io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 40:26]
  wire  subtractor_io_carryIn; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 40:26]
  wire [31:0] subtractor_io_sum; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 40:26]
  wire [31:0] _io_result_T_6 = adder_io_sum; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 62:50]
  wire [62:0] _io_result_T_16 = {{31'd0}, _io_result_T_6}; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 62:50]
  HierarchicalCarryLookaheadAdder adder ( // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 39:21]
    .io_b(adder_io_b),
    .io_carryIn(adder_io_carryIn),
    .io_sum(adder_io_sum)
  );
  HierarchicalCarryLookaheadAdder subtractor ( // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 40:26]
    .io_b(subtractor_io_b),
    .io_carryIn(subtractor_io_carryIn),
    .io_sum(subtractor_io_sum)
  );
  assign io_result = _io_result_T_16[31:0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 62:13]
  assign adder_io_b = io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 44:14]
  assign adder_io_carryIn = 1'h0; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 45:20]
  assign subtractor_io_b = ~io_b; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 48:23]
  assign subtractor_io_carryIn = 1'h1; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/datapath/ALU.scala 49:25]
endmodule
module SingleCycleCore(
  input         clock,
  input         reset,
  output [31:0] io_debugPc, // @[src/main/scala/objective02/core/SingleCycleCore.scala 33:14]
  output [31:0] io_debugInstruction, // @[src/main/scala/objective02/core/SingleCycleCore.scala 33:14]
  output [4:0]  io_debugRd, // @[src/main/scala/objective02/core/SingleCycleCore.scala 33:14]
  output [31:0] io_debugWriteData, // @[src/main/scala/objective02/core/SingleCycleCore.scala 33:14]
  output        io_debugRegWrite, // @[src/main/scala/objective02/core/SingleCycleCore.scala 33:14]
  output        io_debugIllegal, // @[src/main/scala/objective02/core/SingleCycleCore.scala 33:14]
  output        io_debugMemRead, // @[src/main/scala/objective02/core/SingleCycleCore.scala 33:14]
  output        io_debugMemReadReq, // @[src/main/scala/objective02/core/SingleCycleCore.scala 33:14]
  output        io_debugMemWrite, // @[src/main/scala/objective02/core/SingleCycleCore.scala 33:14]
  output        io_debugMemWriteReq, // @[src/main/scala/objective02/core/SingleCycleCore.scala 33:14]
  output [31:0] io_debugMemAddress, // @[src/main/scala/objective02/core/SingleCycleCore.scala 33:14]
  output [31:0] io_debugMemWriteData, // @[src/main/scala/objective02/core/SingleCycleCore.scala 33:14]
  output [31:0] io_debugMemReadData // @[src/main/scala/objective02/core/SingleCycleCore.scala 33:14]
);
  wire  pc_clock; // @[src/main/scala/objective02/core/SingleCycleCore.scala 38:23]
  wire  pc_reset; // @[src/main/scala/objective02/core/SingleCycleCore.scala 38:23]
  wire [31:0] pc_io_pc; // @[src/main/scala/objective02/core/SingleCycleCore.scala 38:23]
  wire [31:0] decoder_io_imm; // @[src/main/scala/objective02/core/SingleCycleCore.scala 40:23]
  wire  rf_io_writeEnable; // @[src/main/scala/objective02/core/SingleCycleCore.scala 41:23]
  wire [31:0] alu_io_b; // @[src/main/scala/objective02/core/SingleCycleCore.scala 42:23]
  wire [31:0] alu_io_result; // @[src/main/scala/objective02/core/SingleCycleCore.scala 42:23]
  ProgramCounter pc ( // @[src/main/scala/objective02/core/SingleCycleCore.scala 38:23]
    .clock(pc_clock),
    .reset(pc_reset),
    .io_pc(pc_io_pc)
  );
  Decoder decoder ( // @[src/main/scala/objective02/core/SingleCycleCore.scala 40:23]
    .io_imm(decoder_io_imm)
  );
  RegisterFile rf ( // @[src/main/scala/objective02/core/SingleCycleCore.scala 41:23]
    .io_writeEnable(rf_io_writeEnable)
  );
  ALU alu ( // @[src/main/scala/objective02/core/SingleCycleCore.scala 42:23]
    .io_b(alu_io_b),
    .io_result(alu_io_result)
  );
  assign io_debugPc = pc_io_pc; // @[src/main/scala/objective02/core/SingleCycleCore.scala 119:24]
  assign io_debugInstruction = 32'h13; // @[src/main/scala/objective02/core/SingleCycleCore.scala 120:24]
  assign io_debugRd = 5'h0; // @[src/main/scala/objective02/core/SingleCycleCore.scala 121:24]
  assign io_debugWriteData = alu_io_result; // @[src/main/scala/objective02/core/SingleCycleCore.scala 104:73]
  assign io_debugRegWrite = rf_io_writeEnable; // @[src/main/scala/objective02/core/SingleCycleCore.scala 123:24]
  assign io_debugIllegal = 1'h0; // @[src/main/scala/objective02/core/SingleCycleCore.scala 124:24]
  assign io_debugMemRead = 1'h0; // @[src/main/scala/objective02/core/SingleCycleCore.scala 126:55]
  assign io_debugMemReadReq = 1'h0; // @[src/main/scala/objective02/core/SingleCycleCore.scala 125:24]
  assign io_debugMemWrite = 1'h0; // @[src/main/scala/objective02/core/SingleCycleCore.scala 128:56]
  assign io_debugMemWriteReq = 1'h0; // @[src/main/scala/objective02/core/SingleCycleCore.scala 127:24]
  assign io_debugMemAddress = alu_io_result; // @[src/main/scala/objective02/core/SingleCycleCore.scala 129:24]
  assign io_debugMemWriteData = 32'h0; // @[src/main/scala/objective02/core/SingleCycleCore.scala 130:24]
  assign io_debugMemReadData = 32'h0; // @[src/main/scala/objective02/core/SingleCycleCore.scala 131:24]
  assign pc_clock = clock;
  assign pc_reset = reset;
  assign rf_io_writeEnable = 1'h0; // @[src/main/scala/objective02/core/SingleCycleCore.scala 114:80]
  assign alu_io_b = decoder_io_imm; // @[src/main/scala/objective02/core/SingleCycleCore.scala 67:67]
endmodule
