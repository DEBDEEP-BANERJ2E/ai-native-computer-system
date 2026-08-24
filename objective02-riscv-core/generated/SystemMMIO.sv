module TelemetryBlock(
  input         clock,
  input         reset,
  input         io_operationValid, // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 18:14]
  input         io_claActive, // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 18:14]
  input         io_multiplierActive, // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 18:14]
  input  [31:0] io_result, // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 18:14]
  input  [31:0] io_readAddress, // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 18:14]
  output [31:0] io_readData // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 18:14]
);
`ifdef RANDOMIZE_REG_INIT
  reg [31:0] _RAND_0;
  reg [31:0] _RAND_1;
  reg [31:0] _RAND_2;
  reg [31:0] _RAND_3;
`endif // RANDOMIZE_REG_INIT
  reg [31:0] reversibleEnergy; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 28:33]
  reg [31:0] claSwitching; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 29:29]
  reg [31:0] multiplierThermal; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 30:34]
  reg [31:0] previousResult; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 32:31]
  wire [31:0] _changedBits_T = io_result ^ previousResult; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:40]
  wire [1:0] _changedBits_T_33 = _changedBits_T[0] + _changedBits_T[1]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [1:0] _changedBits_T_35 = _changedBits_T[2] + _changedBits_T[3]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [2:0] _changedBits_T_37 = _changedBits_T_33 + _changedBits_T_35; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [1:0] _changedBits_T_39 = _changedBits_T[4] + _changedBits_T[5]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [1:0] _changedBits_T_41 = _changedBits_T[6] + _changedBits_T[7]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [2:0] _changedBits_T_43 = _changedBits_T_39 + _changedBits_T_41; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [3:0] _changedBits_T_45 = _changedBits_T_37 + _changedBits_T_43; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [1:0] _changedBits_T_47 = _changedBits_T[8] + _changedBits_T[9]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [1:0] _changedBits_T_49 = _changedBits_T[10] + _changedBits_T[11]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [2:0] _changedBits_T_51 = _changedBits_T_47 + _changedBits_T_49; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [1:0] _changedBits_T_53 = _changedBits_T[12] + _changedBits_T[13]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [1:0] _changedBits_T_55 = _changedBits_T[14] + _changedBits_T[15]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [2:0] _changedBits_T_57 = _changedBits_T_53 + _changedBits_T_55; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [3:0] _changedBits_T_59 = _changedBits_T_51 + _changedBits_T_57; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [4:0] _changedBits_T_61 = _changedBits_T_45 + _changedBits_T_59; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [1:0] _changedBits_T_63 = _changedBits_T[16] + _changedBits_T[17]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [1:0] _changedBits_T_65 = _changedBits_T[18] + _changedBits_T[19]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [2:0] _changedBits_T_67 = _changedBits_T_63 + _changedBits_T_65; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [1:0] _changedBits_T_69 = _changedBits_T[20] + _changedBits_T[21]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [1:0] _changedBits_T_71 = _changedBits_T[22] + _changedBits_T[23]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [2:0] _changedBits_T_73 = _changedBits_T_69 + _changedBits_T_71; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [3:0] _changedBits_T_75 = _changedBits_T_67 + _changedBits_T_73; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [1:0] _changedBits_T_77 = _changedBits_T[24] + _changedBits_T[25]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [1:0] _changedBits_T_79 = _changedBits_T[26] + _changedBits_T[27]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [2:0] _changedBits_T_81 = _changedBits_T_77 + _changedBits_T_79; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [1:0] _changedBits_T_83 = _changedBits_T[28] + _changedBits_T[29]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [1:0] _changedBits_T_85 = _changedBits_T[30] + _changedBits_T[31]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [2:0] _changedBits_T_87 = _changedBits_T_83 + _changedBits_T_85; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [3:0] _changedBits_T_89 = _changedBits_T_81 + _changedBits_T_87; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [4:0] _changedBits_T_91 = _changedBits_T_75 + _changedBits_T_89; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [5:0] changedBits = _changedBits_T_61 + _changedBits_T_91; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 34:29]
  wire [5:0] activity = io_operationValid ? changedBits : 6'h0; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 35:21]
  wire [32:0] _energyProxy_T = reversibleEnergy + claSwitching; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 36:38]
  wire [32:0] _GEN_4 = {{1'd0}, multiplierThermal}; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 36:54]
  wire [33:0] energyProxy = _energyProxy_T + _GEN_4; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 36:54]
  wire [65:0] edpProxy = energyProxy * 32'h1; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 37:30]
  wire [32:0] _reversibleEnergy_T_1 = {{1'd0}, reversibleEnergy}; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 40:42]
  wire [5:0] _claSwitching_T = io_claActive ? activity : 6'h0; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 41:39]
  wire [31:0] _GEN_5 = {{26'd0}, _claSwitching_T}; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 41:34]
  wire [31:0] _claSwitching_T_2 = claSwitching + _GEN_5; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 41:34]
  wire [5:0] _multiplierThermal_T = io_multiplierActive ? activity : 6'h0; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 42:49]
  wire [31:0] _GEN_6 = {{26'd0}, _multiplierThermal_T}; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 42:44]
  wire [31:0] _multiplierThermal_T_2 = multiplierThermal + _GEN_6; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 42:44]
  wire [31:0] _io_readData_T_2 = 32'h80001000 == io_readAddress ? reversibleEnergy : 32'h0; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 46:54]
  wire [31:0] _io_readData_T_4 = 32'h80001004 == io_readAddress ? claSwitching : _io_readData_T_2; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 46:54]
  wire [31:0] _io_readData_T_6 = 32'h80001008 == io_readAddress ? multiplierThermal : _io_readData_T_4; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 46:54]
  wire [31:0] _io_readData_T_8 = 32'h8000100c == io_readAddress ? edpProxy[31:0] : _io_readData_T_6; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 46:54]
  assign io_readData = 32'h80001010 == io_readAddress ? 32'h1 : _io_readData_T_8; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 46:54]
  always @(posedge clock) begin
    if (reset) begin // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 28:33]
      reversibleEnergy <= 32'h0; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 28:33]
    end else if (io_operationValid) begin // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 39:28]
      reversibleEnergy <= _reversibleEnergy_T_1[31:0]; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 40:22]
    end
    if (reset) begin // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 29:29]
      claSwitching <= 32'h0; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 29:29]
    end else if (io_operationValid) begin // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 39:28]
      claSwitching <= _claSwitching_T_2; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 41:18]
    end
    if (reset) begin // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 30:34]
      multiplierThermal <= 32'h0; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 30:34]
    end else if (io_operationValid) begin // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 39:28]
      multiplierThermal <= _multiplierThermal_T_2; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 42:23]
    end
    if (reset) begin // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 32:31]
      previousResult <= 32'h0; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 32:31]
    end else if (io_operationValid) begin // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 39:28]
      previousResult <= io_result; // @[Users/debdeepbanerjee/Desktop/ai-native-computer-system/objective01-digital-logic/src/main/scala/telemetry/TelemetryBlock.scala 43:20]
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
  reversibleEnergy = _RAND_0[31:0];
  _RAND_1 = {1{`RANDOM}};
  claSwitching = _RAND_1[31:0];
  _RAND_2 = {1{`RANDOM}};
  multiplierThermal = _RAND_2[31:0];
  _RAND_3 = {1{`RANDOM}};
  previousResult = _RAND_3[31:0];
`endif // RANDOMIZE_REG_INIT
  `endif // RANDOMIZE
end // initial
`ifdef FIRRTL_AFTER_INITIAL
`FIRRTL_AFTER_INITIAL
`endif
`endif // SYNTHESIS
endmodule
module SystemMMIO(
  input         clock,
  input         reset,
  input  [31:0] io_address, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  input         io_memReadReq, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  input         io_memWriteReq, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  input  [31:0] io_writeData, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  input  [2:0]  io_memWidth, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  output [31:0] io_readData, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  output        io_windowHit, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  output        io_readAccepted, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  output        io_writeAccepted, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  output [31:0] io_schedHint, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  output [31:0] io_processBehaviorClass, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  output [31:0] io_currentContext, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  input         io_retireEvent, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  input  [31:0] io_commitPc, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  input         io_branchTaken, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  input         io_loadUseStall, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  input         io_dividerBusy, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  input         io_pipelineStall, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  input         io_telemetryValid, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  input         io_telemetryClaActive, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  input         io_telemetryMulActive, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  input  [31:0] io_telemetryResult, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  input         io_securityEvent_valid, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  input  [31:0] io_securityEvent_pc, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  input  [31:0] io_securityEvent_address, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  input  [1:0]  io_securityEvent_accessType, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  input  [3:0]  io_securityEvent_reason, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  input  [31:0] io_securityEvent_context, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  output        io_trapEnable, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  output        io_trapActive, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  output [31:0] io_trapVector, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  output [31:0] io_trapEpc, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  output        io_takeTrapReturn, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  output        io_trapDoubleFault, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  output [31:0] io_trapCause, // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
  output [31:0] io_trapAddr // @[src/main/scala/objective02/system/SystemMMIO.scala 54:14]
);
`ifdef RANDOMIZE_REG_INIT
  reg [31:0] _RAND_0;
  reg [31:0] _RAND_1;
  reg [31:0] _RAND_2;
  reg [31:0] _RAND_3;
  reg [31:0] _RAND_4;
  reg [31:0] _RAND_5;
  reg [31:0] _RAND_6;
  reg [31:0] _RAND_7;
  reg [31:0] _RAND_8;
  reg [31:0] _RAND_9;
  reg [31:0] _RAND_10;
  reg [31:0] _RAND_11;
  reg [31:0] _RAND_12;
  reg [31:0] _RAND_13;
  reg [31:0] _RAND_14;
  reg [31:0] _RAND_15;
  reg [31:0] _RAND_16;
  reg [31:0] _RAND_17;
  reg [31:0] _RAND_18;
  reg [31:0] _RAND_19;
  reg [31:0] _RAND_20;
  reg [31:0] _RAND_21;
  reg [31:0] _RAND_22;
`endif // RANDOMIZE_REG_INIT
  wire  telemetry_clock; // @[src/main/scala/objective02/system/SystemMMIO.scala 59:25]
  wire  telemetry_reset; // @[src/main/scala/objective02/system/SystemMMIO.scala 59:25]
  wire  telemetry_io_operationValid; // @[src/main/scala/objective02/system/SystemMMIO.scala 59:25]
  wire  telemetry_io_claActive; // @[src/main/scala/objective02/system/SystemMMIO.scala 59:25]
  wire  telemetry_io_multiplierActive; // @[src/main/scala/objective02/system/SystemMMIO.scala 59:25]
  wire [31:0] telemetry_io_result; // @[src/main/scala/objective02/system/SystemMMIO.scala 59:25]
  wire [31:0] telemetry_io_readAddress; // @[src/main/scala/objective02/system/SystemMMIO.scala 59:25]
  wire [31:0] telemetry_io_readData; // @[src/main/scala/objective02/system/SystemMMIO.scala 59:25]
  reg [31:0] processBehaviorClassReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 70:40]
  reg [31:0] schedHintReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 71:40]
  reg [31:0] currentContextReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 72:40]
  reg [31:0] retiredCountReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 73:40]
  reg [31:0] branchTakenCountReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 74:40]
  reg [31:0] loadUseStallCountReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 75:41]
  reg [31:0] divBusyCyclesReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 76:40]
  reg [31:0] pipelineStallCountReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 77:40]
  reg [31:0] lastCommitPcReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 78:40]
  wire [31:0] _retiredCountReg_T_1 = retiredCountReg + 32'h1; // @[src/main/scala/objective02/system/SystemMMIO.scala 86:40]
  wire [31:0] _branchTakenCountReg_T_1 = branchTakenCountReg + 32'h1; // @[src/main/scala/objective02/system/SystemMMIO.scala 90:48]
  wire [31:0] _loadUseStallCountReg_T_1 = loadUseStallCountReg + 32'h1; // @[src/main/scala/objective02/system/SystemMMIO.scala 93:50]
  wire [31:0] _divBusyCyclesReg_T_1 = divBusyCyclesReg + 32'h1; // @[src/main/scala/objective02/system/SystemMMIO.scala 96:42]
  wire [31:0] _pipelineStallCountReg_T_1 = pipelineStallCountReg + 32'h1; // @[src/main/scala/objective02/system/SystemMMIO.scala 99:52]
  wire  isAlignedWord = io_address[1:0] == 2'h0 & io_memWidth == 3'h2; // @[src/main/scala/objective02/system/SystemMMIO.scala 108:50]
  reg  secPendingReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 113:33]
  reg [31:0] secPcReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 114:33]
  reg [31:0] secAddrReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 115:33]
  reg [1:0] secAccessTypeReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 116:33]
  reg [3:0] secReasonReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 117:33]
  reg [31:0] secContextReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 118:33]
  wire  _w1cClear_T = isAlignedWord & io_memWriteReq; // @[src/main/scala/objective02/system/SystemMMIO.scala 121:32]
  wire  w1cClear = isAlignedWord & io_memWriteReq & io_address == 32'h80002100 & io_writeData[0]; // @[src/main/scala/objective02/system/SystemMMIO.scala 121:93]
  wire  _GEN_6 = w1cClear ? 1'h0 : secPendingReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 123:18 124:19 113:33]
  wire  _GEN_7 = io_securityEvent_valid & (~secPendingReg | w1cClear) | _GEN_6; // @[src/main/scala/objective02/system/SystemMMIO.scala 128:64 129:22]
  reg  trapEnableReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 140:35]
  reg  trapActiveReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 141:35]
  reg  trapDoubleFaultReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 142:35]
  reg [31:0] trapVectorReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 143:35]
  reg [31:0] trapEpcReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 144:35]
  reg [31:0] trapCauseReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 145:35]
  reg [31:0] trapAddrReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 146:35]
  reg [31:0] trapContextReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 147:35]
  wire  _takePreciseTrap_T = io_securityEvent_valid & trapEnableReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 150:48]
  wire  takePreciseTrap = io_securityEvent_valid & trapEnableReg & ~trapActiveReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 150:65]
  wire  nestedFault = _takePreciseTrap_T & trapActiveReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 151:65]
  wire  trapReturnReq = _w1cClear_T & io_address == 32'h80002130 & io_writeData[0] & trapActiveReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 154:129]
  wire  w1cDoubleFault = _w1cClear_T & io_address == 32'h80002118 & io_writeData[1]; // @[src/main/scala/objective02/system/SystemMMIO.scala 158:100]
  wire  _GEN_13 = w1cDoubleFault ? 1'h0 : trapDoubleFaultReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 160:24 161:24 142:35]
  wire  _GEN_14 = nestedFault | _GEN_13; // @[src/main/scala/objective02/system/SystemMMIO.scala 164:21 165:24]
  wire [31:0] _trapCauseReg_T = {26'h0,io_securityEvent_accessType,io_securityEvent_reason}; // @[src/main/scala/objective02/system/SystemMMIO.scala 172:26]
  wire  _GEN_15 = trapReturnReq ? 1'h0 : trapActiveReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 175:30 176:20 141:35]
  wire  _GEN_16 = takePreciseTrap | _GEN_15; // @[src/main/scala/objective02/system/SystemMMIO.scala 169:25 170:20]
  wire [31:0] _GEN_17 = takePreciseTrap ? io_securityEvent_pc : trapEpcReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 169:25 171:20 144:35]
  wire  _T_9 = 32'h80002004 == io_address; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26]
  wire  _T_10 = 32'h80002008 == io_address; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26]
  wire  _T_17 = 32'h80002024 == io_address; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26]
  wire  _T_18 = 32'h80002100 == io_address; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26]
  wire [31:0] _readDataWire_T = {31'h0,secPendingReg}; // @[src/main/scala/objective02/system/SystemMMIO.scala 265:34]
  wire [31:0] _readDataWire_T_1 = {26'h0,secAccessTypeReg,secReasonReg}; // @[src/main/scala/objective02/system/SystemMMIO.scala 277:34]
  wire  _T_23 = 32'h80002114 == io_address; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26]
  wire [31:0] _readDataWire_T_2 = {31'h0,trapEnableReg}; // @[src/main/scala/objective02/system/SystemMMIO.scala 287:34]
  wire  _T_24 = 32'h80002118 == io_address; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26]
  wire [31:0] _readDataWire_T_3 = {30'h0,trapDoubleFaultReg,trapActiveReg}; // @[src/main/scala/objective02/system/SystemMMIO.scala 291:34]
  wire  _T_25 = 32'h8000211c == io_address; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26]
  wire  _T_26 = 32'h80002120 == io_address; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26]
  wire  _T_30 = 32'h80002130 == io_address; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26]
  wire [31:0] _GEN_23 = 32'h8000212c == io_address ? trapContextReg : 32'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 311:28]
  wire  _GEN_24 = 32'h8000212c == io_address | 32'h80002130 == io_address; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 312:28]
  wire [31:0] _GEN_25 = 32'h80002128 == io_address ? trapAddrReg : _GEN_23; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 307:28]
  wire  _GEN_26 = 32'h80002128 == io_address | _GEN_24; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 308:28]
  wire [31:0] _GEN_27 = 32'h80002124 == io_address ? trapCauseReg : _GEN_25; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 303:28]
  wire  _GEN_28 = 32'h80002124 == io_address | _GEN_26; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 304:28]
  wire [31:0] _GEN_29 = 32'h80002120 == io_address ? trapEpcReg : _GEN_27; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 299:28]
  wire  _GEN_30 = 32'h80002120 == io_address | _GEN_28; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 300:28]
  wire [31:0] _GEN_31 = 32'h8000211c == io_address ? trapVectorReg : _GEN_29; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 295:28]
  wire  _GEN_32 = 32'h8000211c == io_address | _GEN_30; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 296:28]
  wire [31:0] _GEN_33 = 32'h80002118 == io_address ? _readDataWire_T_3 : _GEN_31; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 291:28]
  wire  _GEN_34 = 32'h80002118 == io_address | _GEN_32; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 292:28]
  wire [31:0] _GEN_35 = 32'h80002114 == io_address ? _readDataWire_T_2 : _GEN_33; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 287:28]
  wire  _GEN_36 = 32'h80002114 == io_address | _GEN_34; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 288:28]
  wire [31:0] _GEN_37 = 32'h80002110 == io_address ? secContextReg : _GEN_35; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 281:28]
  wire  _GEN_38 = 32'h80002110 == io_address | _GEN_36; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 282:28]
  wire [31:0] _GEN_39 = 32'h8000210c == io_address ? _readDataWire_T_1 : _GEN_37; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 277:28]
  wire  _GEN_40 = 32'h8000210c == io_address | _GEN_38; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 278:28]
  wire [31:0] _GEN_41 = 32'h80002108 == io_address ? secAddrReg : _GEN_39; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 273:28]
  wire  _GEN_42 = 32'h80002108 == io_address | _GEN_40; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 274:28]
  wire [31:0] _GEN_43 = 32'h80002104 == io_address ? secPcReg : _GEN_41; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 269:28]
  wire  _GEN_44 = 32'h80002104 == io_address | _GEN_42; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 270:28]
  wire [31:0] _GEN_45 = 32'h80002100 == io_address ? _readDataWire_T : _GEN_43; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 265:28]
  wire  _GEN_46 = 32'h80002100 == io_address | _GEN_44; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 266:28]
  wire [31:0] _GEN_47 = 32'h80002024 == io_address ? currentContextReg : _GEN_45; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 259:28]
  wire  _GEN_48 = 32'h80002024 == io_address | _GEN_46; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 260:28]
  wire [31:0] _GEN_49 = 32'h80002020 == io_address ? lastCommitPcReg : _GEN_47; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 255:28]
  wire  _GEN_50 = 32'h80002020 == io_address | _GEN_48; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 256:28]
  wire [31:0] _GEN_51 = 32'h8000201c == io_address ? pipelineStallCountReg : _GEN_49; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 251:28]
  wire  _GEN_52 = 32'h8000201c == io_address | _GEN_50; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 252:28]
  wire [31:0] _GEN_53 = 32'h80002018 == io_address ? divBusyCyclesReg : _GEN_51; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 247:28]
  wire  _GEN_54 = 32'h80002018 == io_address | _GEN_52; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 248:28]
  wire [31:0] _GEN_55 = 32'h80002014 == io_address ? loadUseStallCountReg : _GEN_53; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 243:28]
  wire  _GEN_56 = 32'h80002014 == io_address | _GEN_54; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 244:28]
  wire [31:0] _GEN_57 = 32'h80002010 == io_address ? branchTakenCountReg : _GEN_55; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 239:28]
  wire  _GEN_58 = 32'h80002010 == io_address | _GEN_56; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 240:28]
  wire [31:0] _GEN_59 = 32'h8000200c == io_address ? retiredCountReg : _GEN_57; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 235:28]
  wire  _GEN_60 = 32'h8000200c == io_address | _GEN_58; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 236:28]
  wire [31:0] _GEN_61 = 32'h80002008 == io_address ? schedHintReg : _GEN_59; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 231:28]
  wire  _GEN_62 = 32'h80002008 == io_address | _GEN_60; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 232:28]
  wire [31:0] _GEN_63 = 32'h80002004 == io_address ? processBehaviorClassReg : _GEN_61; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 227:28]
  wire  _GEN_64 = 32'h80002004 == io_address | _GEN_62; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 228:28]
  wire [31:0] _GEN_65 = 32'h80002000 == io_address ? 32'h0 : _GEN_63; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 223:28]
  wire  _GEN_66 = 32'h80002000 == io_address | _GEN_64; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 224:28]
  wire [31:0] _GEN_67 = 32'h80001010 == io_address ? telemetry_io_readData : _GEN_65; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 217:28]
  wire  _GEN_68 = 32'h80001010 == io_address | _GEN_66; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 218:28]
  wire [31:0] _GEN_69 = 32'h8000100c == io_address ? telemetry_io_readData : _GEN_67; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 213:28]
  wire  _GEN_70 = 32'h8000100c == io_address | _GEN_68; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 214:28]
  wire [31:0] _GEN_71 = 32'h80001008 == io_address ? telemetry_io_readData : _GEN_69; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 209:28]
  wire  _GEN_72 = 32'h80001008 == io_address | _GEN_70; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 210:28]
  wire [31:0] _GEN_73 = 32'h80001004 == io_address ? telemetry_io_readData : _GEN_71; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 205:28]
  wire  _GEN_74 = 32'h80001004 == io_address | _GEN_72; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 206:28]
  wire [31:0] _GEN_75 = 32'h80001000 == io_address ? telemetry_io_readData : _GEN_73; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 201:28]
  wire  _GEN_76 = 32'h80001000 == io_address | _GEN_74; // @[src/main/scala/objective02/system/SystemMMIO.scala 198:26 202:28]
  wire [31:0] _GEN_77 = io_memReadReq ? _GEN_75 : 32'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 197:25 189:38]
  wire  _GEN_78 = io_memReadReq & _GEN_76; // @[src/main/scala/objective02/system/SystemMMIO.scala 197:25 190:38]
  wire [31:0] _trapVectorReg_T = io_writeData & 32'hfffffffc; // @[src/main/scala/objective02/system/SystemMMIO.scala 349:45]
  wire [31:0] _GEN_79 = trapActiveReg ? _trapVectorReg_T : _GEN_17; // @[src/main/scala/objective02/system/SystemMMIO.scala 353:31 354:24]
  wire [31:0] _GEN_81 = _T_26 ? _GEN_79 : _GEN_17; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26]
  wire  _GEN_82 = _T_26 | _T_30; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26 356:29]
  wire [31:0] _GEN_83 = _T_25 ? _trapVectorReg_T : trapVectorReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26 349:29 143:35]
  wire  _GEN_84 = _T_25 | _GEN_82; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26 350:29]
  wire [31:0] _GEN_85 = _T_25 ? _GEN_17 : _GEN_81; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26]
  wire  _GEN_86 = _T_24 | _GEN_84; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26 346:29]
  wire [31:0] _GEN_87 = _T_24 ? trapVectorReg : _GEN_83; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26 143:35]
  wire [31:0] _GEN_88 = _T_24 ? _GEN_17 : _GEN_85; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26]
  wire  _GEN_89 = _T_23 ? io_writeData[0] : trapEnableReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26 342:29 140:35]
  wire  _GEN_90 = _T_23 | _GEN_86; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26 343:29]
  wire [31:0] _GEN_91 = _T_23 ? trapVectorReg : _GEN_87; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26 143:35]
  wire [31:0] _GEN_92 = _T_23 ? _GEN_17 : _GEN_88; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26]
  wire  _GEN_93 = _T_18 | _GEN_90; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26 339:29]
  wire  _GEN_94 = _T_18 ? trapEnableReg : _GEN_89; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26 140:35]
  wire [31:0] _GEN_95 = _T_18 ? trapVectorReg : _GEN_91; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26 143:35]
  wire [31:0] _GEN_96 = _T_18 ? _GEN_17 : _GEN_92; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26]
  wire [31:0] _GEN_97 = _T_17 ? io_writeData : currentContextReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26 335:29 72:40]
  wire  _GEN_98 = _T_17 | _GEN_93; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26 336:29]
  wire  _GEN_99 = _T_17 ? trapEnableReg : _GEN_94; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26 140:35]
  wire [31:0] _GEN_100 = _T_17 ? trapVectorReg : _GEN_95; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26 143:35]
  wire [31:0] _GEN_101 = _T_17 ? _GEN_17 : _GEN_96; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26]
  wire [31:0] _GEN_102 = _T_10 ? io_writeData : schedHintReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26 331:29 71:40]
  wire  _GEN_103 = _T_10 | _GEN_98; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26 332:29]
  wire [31:0] _GEN_104 = _T_10 ? currentContextReg : _GEN_97; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26 72:40]
  wire  _GEN_105 = _T_10 ? trapEnableReg : _GEN_99; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26 140:35]
  wire [31:0] _GEN_106 = _T_10 ? trapVectorReg : _GEN_100; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26 143:35]
  wire [31:0] _GEN_107 = _T_10 ? _GEN_17 : _GEN_101; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26]
  wire  _GEN_109 = _T_9 | _GEN_103; // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26 328:35]
  wire  _GEN_116 = io_memWriteReq & _GEN_109; // @[src/main/scala/objective02/system/SystemMMIO.scala 324:26 191:38]
  TelemetryBlock telemetry ( // @[src/main/scala/objective02/system/SystemMMIO.scala 59:25]
    .clock(telemetry_clock),
    .reset(telemetry_reset),
    .io_operationValid(telemetry_io_operationValid),
    .io_claActive(telemetry_io_claActive),
    .io_multiplierActive(telemetry_io_multiplierActive),
    .io_result(telemetry_io_result),
    .io_readAddress(telemetry_io_readAddress),
    .io_readData(telemetry_io_readData)
  );
  assign io_readData = isAlignedWord ? _GEN_77 : 32'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 193:23 189:38]
  assign io_windowHit = io_address[31:16] == 16'h8000; // @[src/main/scala/objective02/system/SystemMMIO.scala 105:39]
  assign io_readAccepted = isAlignedWord & _GEN_78; // @[src/main/scala/objective02/system/SystemMMIO.scala 193:23 190:38]
  assign io_writeAccepted = isAlignedWord & _GEN_116; // @[src/main/scala/objective02/system/SystemMMIO.scala 193:23 191:38]
  assign io_schedHint = schedHintReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 80:27]
  assign io_processBehaviorClass = processBehaviorClassReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 81:27]
  assign io_currentContext = currentContextReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 82:27]
  assign io_trapEnable = trapEnableReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 179:22]
  assign io_trapActive = trapActiveReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 180:22]
  assign io_trapVector = trapVectorReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 181:22]
  assign io_trapEpc = trapEpcReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 182:22]
  assign io_takeTrapReturn = _w1cClear_T & io_address == 32'h80002130 & io_writeData[0] & trapActiveReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 154:129]
  assign io_trapDoubleFault = trapDoubleFaultReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 184:22]
  assign io_trapCause = trapCauseReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 185:22]
  assign io_trapAddr = trapAddrReg; // @[src/main/scala/objective02/system/SystemMMIO.scala 186:22]
  assign telemetry_clock = clock;
  assign telemetry_reset = reset;
  assign telemetry_io_operationValid = io_telemetryValid; // @[src/main/scala/objective02/system/SystemMMIO.scala 60:36]
  assign telemetry_io_claActive = io_telemetryClaActive; // @[src/main/scala/objective02/system/SystemMMIO.scala 62:36]
  assign telemetry_io_multiplierActive = io_telemetryMulActive; // @[src/main/scala/objective02/system/SystemMMIO.scala 63:36]
  assign telemetry_io_result = io_telemetryResult; // @[src/main/scala/objective02/system/SystemMMIO.scala 64:36]
  assign telemetry_io_readAddress = io_address; // @[src/main/scala/objective02/system/SystemMMIO.scala 65:36]
  always @(posedge clock) begin
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 70:40]
      processBehaviorClassReg <= 32'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 70:40]
    end else if (isAlignedWord) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 193:23]
      if (io_memWriteReq) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 324:26]
        if (_T_9) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26]
          processBehaviorClassReg <= io_writeData; // @[src/main/scala/objective02/system/SystemMMIO.scala 327:35]
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 71:40]
      schedHintReg <= 32'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 71:40]
    end else if (isAlignedWord) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 193:23]
      if (io_memWriteReq) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 324:26]
        if (!(_T_9)) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26]
          schedHintReg <= _GEN_102;
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 72:40]
      currentContextReg <= 32'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 72:40]
    end else if (isAlignedWord) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 193:23]
      if (io_memWriteReq) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 324:26]
        if (!(_T_9)) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26]
          currentContextReg <= _GEN_104;
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 73:40]
      retiredCountReg <= 32'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 73:40]
    end else if (io_retireEvent) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 85:24]
      retiredCountReg <= _retiredCountReg_T_1; // @[src/main/scala/objective02/system/SystemMMIO.scala 86:21]
    end
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 74:40]
      branchTakenCountReg <= 32'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 74:40]
    end else if (io_branchTaken) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 89:24]
      branchTakenCountReg <= _branchTakenCountReg_T_1; // @[src/main/scala/objective02/system/SystemMMIO.scala 90:25]
    end
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 75:41]
      loadUseStallCountReg <= 32'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 75:41]
    end else if (io_loadUseStall) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 92:25]
      loadUseStallCountReg <= _loadUseStallCountReg_T_1; // @[src/main/scala/objective02/system/SystemMMIO.scala 93:26]
    end
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 76:40]
      divBusyCyclesReg <= 32'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 76:40]
    end else if (io_dividerBusy) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 95:24]
      divBusyCyclesReg <= _divBusyCyclesReg_T_1; // @[src/main/scala/objective02/system/SystemMMIO.scala 96:22]
    end
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 77:40]
      pipelineStallCountReg <= 32'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 77:40]
    end else if (io_pipelineStall) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 98:26]
      pipelineStallCountReg <= _pipelineStallCountReg_T_1; // @[src/main/scala/objective02/system/SystemMMIO.scala 99:27]
    end
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 78:40]
      lastCommitPcReg <= 32'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 78:40]
    end else if (io_retireEvent) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 85:24]
      lastCommitPcReg <= io_commitPc; // @[src/main/scala/objective02/system/SystemMMIO.scala 87:21]
    end
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 113:33]
      secPendingReg <= 1'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 113:33]
    end else begin
      secPendingReg <= _GEN_7;
    end
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 114:33]
      secPcReg <= 32'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 114:33]
    end else if (io_securityEvent_valid & (~secPendingReg | w1cClear)) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 128:64]
      secPcReg <= io_securityEvent_pc; // @[src/main/scala/objective02/system/SystemMMIO.scala 130:22]
    end
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 115:33]
      secAddrReg <= 32'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 115:33]
    end else if (io_securityEvent_valid & (~secPendingReg | w1cClear)) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 128:64]
      secAddrReg <= io_securityEvent_address; // @[src/main/scala/objective02/system/SystemMMIO.scala 131:22]
    end
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 116:33]
      secAccessTypeReg <= 2'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 116:33]
    end else if (io_securityEvent_valid & (~secPendingReg | w1cClear)) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 128:64]
      secAccessTypeReg <= io_securityEvent_accessType; // @[src/main/scala/objective02/system/SystemMMIO.scala 132:22]
    end
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 117:33]
      secReasonReg <= 4'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 117:33]
    end else if (io_securityEvent_valid & (~secPendingReg | w1cClear)) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 128:64]
      secReasonReg <= io_securityEvent_reason; // @[src/main/scala/objective02/system/SystemMMIO.scala 133:22]
    end
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 118:33]
      secContextReg <= 32'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 118:33]
    end else if (io_securityEvent_valid & (~secPendingReg | w1cClear)) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 128:64]
      secContextReg <= io_securityEvent_context; // @[src/main/scala/objective02/system/SystemMMIO.scala 134:22]
    end
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 140:35]
      trapEnableReg <= 1'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 140:35]
    end else if (isAlignedWord) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 193:23]
      if (io_memWriteReq) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 324:26]
        if (!(_T_9)) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26]
          trapEnableReg <= _GEN_105;
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 141:35]
      trapActiveReg <= 1'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 141:35]
    end else begin
      trapActiveReg <= _GEN_16;
    end
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 142:35]
      trapDoubleFaultReg <= 1'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 142:35]
    end else begin
      trapDoubleFaultReg <= _GEN_14;
    end
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 143:35]
      trapVectorReg <= 32'h800; // @[src/main/scala/objective02/system/SystemMMIO.scala 143:35]
    end else if (isAlignedWord) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 193:23]
      if (io_memWriteReq) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 324:26]
        if (!(_T_9)) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26]
          trapVectorReg <= _GEN_106;
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 144:35]
      trapEpcReg <= 32'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 144:35]
    end else if (isAlignedWord) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 193:23]
      if (io_memWriteReq) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 324:26]
        if (_T_9) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 325:26]
          trapEpcReg <= _GEN_17;
        end else begin
          trapEpcReg <= _GEN_107;
        end
      end else begin
        trapEpcReg <= _GEN_17;
      end
    end else begin
      trapEpcReg <= _GEN_17;
    end
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 145:35]
      trapCauseReg <= 32'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 145:35]
    end else if (takePreciseTrap) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 169:25]
      trapCauseReg <= _trapCauseReg_T; // @[src/main/scala/objective02/system/SystemMMIO.scala 172:20]
    end
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 146:35]
      trapAddrReg <= 32'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 146:35]
    end else if (takePreciseTrap) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 169:25]
      trapAddrReg <= io_securityEvent_address; // @[src/main/scala/objective02/system/SystemMMIO.scala 173:20]
    end
    if (reset) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 147:35]
      trapContextReg <= 32'h0; // @[src/main/scala/objective02/system/SystemMMIO.scala 147:35]
    end else if (takePreciseTrap) begin // @[src/main/scala/objective02/system/SystemMMIO.scala 169:25]
      trapContextReg <= io_securityEvent_context; // @[src/main/scala/objective02/system/SystemMMIO.scala 174:20]
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
  processBehaviorClassReg = _RAND_0[31:0];
  _RAND_1 = {1{`RANDOM}};
  schedHintReg = _RAND_1[31:0];
  _RAND_2 = {1{`RANDOM}};
  currentContextReg = _RAND_2[31:0];
  _RAND_3 = {1{`RANDOM}};
  retiredCountReg = _RAND_3[31:0];
  _RAND_4 = {1{`RANDOM}};
  branchTakenCountReg = _RAND_4[31:0];
  _RAND_5 = {1{`RANDOM}};
  loadUseStallCountReg = _RAND_5[31:0];
  _RAND_6 = {1{`RANDOM}};
  divBusyCyclesReg = _RAND_6[31:0];
  _RAND_7 = {1{`RANDOM}};
  pipelineStallCountReg = _RAND_7[31:0];
  _RAND_8 = {1{`RANDOM}};
  lastCommitPcReg = _RAND_8[31:0];
  _RAND_9 = {1{`RANDOM}};
  secPendingReg = _RAND_9[0:0];
  _RAND_10 = {1{`RANDOM}};
  secPcReg = _RAND_10[31:0];
  _RAND_11 = {1{`RANDOM}};
  secAddrReg = _RAND_11[31:0];
  _RAND_12 = {1{`RANDOM}};
  secAccessTypeReg = _RAND_12[1:0];
  _RAND_13 = {1{`RANDOM}};
  secReasonReg = _RAND_13[3:0];
  _RAND_14 = {1{`RANDOM}};
  secContextReg = _RAND_14[31:0];
  _RAND_15 = {1{`RANDOM}};
  trapEnableReg = _RAND_15[0:0];
  _RAND_16 = {1{`RANDOM}};
  trapActiveReg = _RAND_16[0:0];
  _RAND_17 = {1{`RANDOM}};
  trapDoubleFaultReg = _RAND_17[0:0];
  _RAND_18 = {1{`RANDOM}};
  trapVectorReg = _RAND_18[31:0];
  _RAND_19 = {1{`RANDOM}};
  trapEpcReg = _RAND_19[31:0];
  _RAND_20 = {1{`RANDOM}};
  trapCauseReg = _RAND_20[31:0];
  _RAND_21 = {1{`RANDOM}};
  trapAddrReg = _RAND_21[31:0];
  _RAND_22 = {1{`RANDOM}};
  trapContextReg = _RAND_22[31:0];
`endif // RANDOMIZE_REG_INIT
  `endif // RANDOMIZE
end // initial
`ifdef FIRRTL_AFTER_INITIAL
`FIRRTL_AFTER_INITIAL
`endif
`endif // SYNTHESIS
endmodule
