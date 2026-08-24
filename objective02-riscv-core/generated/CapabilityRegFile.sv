module CapabilityRegFile(
  input         clock,
  input         reset,
  input  [2:0]  io_raddr1, // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 15:14]
  output        io_rdata1_tag, // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 15:14]
  output [31:0] io_rdata1_base, // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 15:14]
  output [31:0] io_rdata1_length, // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 15:14]
  output [2:0]  io_rdata1_perms, // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 15:14]
  output [31:0] io_rdata1_offset, // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 15:14]
  input         io_wen, // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 15:14]
  input  [2:0]  io_waddr, // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 15:14]
  input         io_wdata_tag, // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 15:14]
  input  [31:0] io_wdata_base, // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 15:14]
  input  [31:0] io_wdata_length, // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 15:14]
  input  [2:0]  io_wdata_perms, // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 15:14]
  input  [31:0] io_wdata_offset // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 15:14]
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
  reg [31:0] _RAND_23;
  reg [31:0] _RAND_24;
`endif // RANDOMIZE_REG_INIT
  reg  c3Reg_tag; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 36:22]
  reg [31:0] c3Reg_base; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 36:22]
  reg [31:0] c3Reg_length; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 36:22]
  reg [2:0] c3Reg_perms; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 36:22]
  reg [31:0] c3Reg_offset; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 36:22]
  reg  c4Reg_tag; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 37:22]
  reg [31:0] c4Reg_base; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 37:22]
  reg [31:0] c4Reg_length; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 37:22]
  reg [2:0] c4Reg_perms; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 37:22]
  reg [31:0] c4Reg_offset; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 37:22]
  reg  c5Reg_tag; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 38:22]
  reg [31:0] c5Reg_base; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 38:22]
  reg [31:0] c5Reg_length; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 38:22]
  reg [2:0] c5Reg_perms; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 38:22]
  reg [31:0] c5Reg_offset; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 38:22]
  reg  c6Reg_tag; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22]
  reg [31:0] c6Reg_base; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22]
  reg [31:0] c6Reg_length; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22]
  reg [2:0] c6Reg_perms; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22]
  reg [31:0] c6Reg_offset; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22]
  reg  c7Reg_tag; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22]
  reg [31:0] c7Reg_base; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22]
  reg [31:0] c7Reg_length; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22]
  reg [2:0] c7Reg_perms; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22]
  reg [31:0] c7Reg_offset; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22]
  wire  _T_1 = io_wen & io_waddr >= 3'h3; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:15]
  wire  _GEN_0 = 3'h7 == io_waddr ? io_wdata_tag : c7Reg_tag; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22 44:22 49:23]
  wire [31:0] _GEN_1 = 3'h7 == io_waddr ? io_wdata_base : c7Reg_base; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22 44:22 49:23]
  wire [31:0] _GEN_2 = 3'h7 == io_waddr ? io_wdata_length : c7Reg_length; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22 44:22 49:23]
  wire [2:0] _GEN_3 = 3'h7 == io_waddr ? io_wdata_perms : c7Reg_perms; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22 44:22 49:23]
  wire [31:0] _GEN_4 = 3'h7 == io_waddr ? io_wdata_offset : c7Reg_offset; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22 44:22 49:23]
  wire  _GEN_5 = 3'h6 == io_waddr ? io_wdata_tag : c6Reg_tag; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22 44:22 48:23]
  wire [31:0] _GEN_6 = 3'h6 == io_waddr ? io_wdata_base : c6Reg_base; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22 44:22 48:23]
  wire [31:0] _GEN_7 = 3'h6 == io_waddr ? io_wdata_length : c6Reg_length; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22 44:22 48:23]
  wire [2:0] _GEN_8 = 3'h6 == io_waddr ? io_wdata_perms : c6Reg_perms; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22 44:22 48:23]
  wire [31:0] _GEN_9 = 3'h6 == io_waddr ? io_wdata_offset : c6Reg_offset; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22 44:22 48:23]
  wire  _GEN_10 = 3'h6 == io_waddr ? c7Reg_tag : _GEN_0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22 44:22]
  wire [31:0] _GEN_11 = 3'h6 == io_waddr ? c7Reg_base : _GEN_1; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22 44:22]
  wire [31:0] _GEN_12 = 3'h6 == io_waddr ? c7Reg_length : _GEN_2; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22 44:22]
  wire [2:0] _GEN_13 = 3'h6 == io_waddr ? c7Reg_perms : _GEN_3; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22 44:22]
  wire [31:0] _GEN_14 = 3'h6 == io_waddr ? c7Reg_offset : _GEN_4; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22 44:22]
  wire  _GEN_15 = 3'h5 == io_waddr ? io_wdata_tag : c5Reg_tag; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 38:22 44:22 47:23]
  wire [31:0] _GEN_16 = 3'h5 == io_waddr ? io_wdata_base : c5Reg_base; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 38:22 44:22 47:23]
  wire [31:0] _GEN_17 = 3'h5 == io_waddr ? io_wdata_length : c5Reg_length; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 38:22 44:22 47:23]
  wire [2:0] _GEN_18 = 3'h5 == io_waddr ? io_wdata_perms : c5Reg_perms; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 38:22 44:22 47:23]
  wire [31:0] _GEN_19 = 3'h5 == io_waddr ? io_wdata_offset : c5Reg_offset; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 38:22 44:22 47:23]
  wire  _GEN_20 = 3'h5 == io_waddr ? c6Reg_tag : _GEN_5; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22 44:22]
  wire [31:0] _GEN_21 = 3'h5 == io_waddr ? c6Reg_base : _GEN_6; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22 44:22]
  wire [31:0] _GEN_22 = 3'h5 == io_waddr ? c6Reg_length : _GEN_7; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22 44:22]
  wire [2:0] _GEN_23 = 3'h5 == io_waddr ? c6Reg_perms : _GEN_8; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22 44:22]
  wire [31:0] _GEN_24 = 3'h5 == io_waddr ? c6Reg_offset : _GEN_9; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22 44:22]
  wire  _GEN_25 = 3'h5 == io_waddr ? c7Reg_tag : _GEN_10; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22 44:22]
  wire [31:0] _GEN_26 = 3'h5 == io_waddr ? c7Reg_base : _GEN_11; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22 44:22]
  wire [31:0] _GEN_27 = 3'h5 == io_waddr ? c7Reg_length : _GEN_12; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22 44:22]
  wire [2:0] _GEN_28 = 3'h5 == io_waddr ? c7Reg_perms : _GEN_13; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22 44:22]
  wire [31:0] _GEN_29 = 3'h5 == io_waddr ? c7Reg_offset : _GEN_14; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22 44:22]
  wire  _GEN_100 = 3'h7 == io_raddr1 & c7Reg_tag; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 63:22 54:27]
  wire [31:0] _GEN_101 = 3'h7 == io_raddr1 ? c7Reg_base : 32'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 63:22 54:27]
  wire [31:0] _GEN_102 = 3'h7 == io_raddr1 ? c7Reg_length : 32'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 63:22 54:27]
  wire [2:0] _GEN_103 = 3'h7 == io_raddr1 ? c7Reg_perms : 3'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 63:22 54:27]
  wire [31:0] _GEN_104 = 3'h7 == io_raddr1 ? c7Reg_offset : 32'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 63:22 54:27]
  wire  _GEN_105 = 3'h6 == io_raddr1 ? c6Reg_tag : _GEN_100; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 62:22]
  wire [31:0] _GEN_106 = 3'h6 == io_raddr1 ? c6Reg_base : _GEN_101; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 62:22]
  wire [31:0] _GEN_107 = 3'h6 == io_raddr1 ? c6Reg_length : _GEN_102; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 62:22]
  wire [2:0] _GEN_108 = 3'h6 == io_raddr1 ? c6Reg_perms : _GEN_103; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 62:22]
  wire [31:0] _GEN_109 = 3'h6 == io_raddr1 ? c6Reg_offset : _GEN_104; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 62:22]
  wire  _GEN_110 = 3'h5 == io_raddr1 ? c5Reg_tag : _GEN_105; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 61:22]
  wire [31:0] _GEN_111 = 3'h5 == io_raddr1 ? c5Reg_base : _GEN_106; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 61:22]
  wire [31:0] _GEN_112 = 3'h5 == io_raddr1 ? c5Reg_length : _GEN_107; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 61:22]
  wire [2:0] _GEN_113 = 3'h5 == io_raddr1 ? c5Reg_perms : _GEN_108; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 61:22]
  wire [31:0] _GEN_114 = 3'h5 == io_raddr1 ? c5Reg_offset : _GEN_109; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 61:22]
  wire  _GEN_115 = 3'h4 == io_raddr1 ? c4Reg_tag : _GEN_110; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 60:22]
  wire [31:0] _GEN_116 = 3'h4 == io_raddr1 ? c4Reg_base : _GEN_111; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 60:22]
  wire [31:0] _GEN_117 = 3'h4 == io_raddr1 ? c4Reg_length : _GEN_112; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 60:22]
  wire [2:0] _GEN_118 = 3'h4 == io_raddr1 ? c4Reg_perms : _GEN_113; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 60:22]
  wire [31:0] _GEN_119 = 3'h4 == io_raddr1 ? c4Reg_offset : _GEN_114; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 60:22]
  wire  _GEN_120 = 3'h3 == io_raddr1 ? c3Reg_tag : _GEN_115; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 59:22]
  wire [31:0] _GEN_121 = 3'h3 == io_raddr1 ? c3Reg_base : _GEN_116; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 59:22]
  wire [31:0] _GEN_122 = 3'h3 == io_raddr1 ? c3Reg_length : _GEN_117; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 59:22]
  wire [2:0] _GEN_123 = 3'h3 == io_raddr1 ? c3Reg_perms : _GEN_118; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 59:22]
  wire [31:0] _GEN_124 = 3'h3 == io_raddr1 ? c3Reg_offset : _GEN_119; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 59:22]
  wire  _GEN_125 = 3'h2 == io_raddr1 | _GEN_120; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 58:22]
  wire [31:0] _GEN_126 = 3'h2 == io_raddr1 ? 32'h80000000 : _GEN_121; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 58:22]
  wire [31:0] _GEN_127 = 3'h2 == io_raddr1 ? 32'h10000 : _GEN_122; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 58:22]
  wire [2:0] _GEN_128 = 3'h2 == io_raddr1 ? 3'h3 : _GEN_123; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 58:22]
  wire [31:0] _GEN_129 = 3'h2 == io_raddr1 ? 32'h0 : _GEN_124; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 58:22]
  wire  _GEN_130 = 3'h1 == io_raddr1 | _GEN_125; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 57:22]
  wire [31:0] _GEN_131 = 3'h1 == io_raddr1 ? 32'h0 : _GEN_126; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 57:22]
  wire [31:0] _GEN_132 = 3'h1 == io_raddr1 ? 32'h1000 : _GEN_127; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 57:22]
  wire [2:0] _GEN_133 = 3'h1 == io_raddr1 ? 3'h3 : _GEN_128; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 57:22]
  wire [31:0] _GEN_134 = 3'h1 == io_raddr1 ? 32'h0 : _GEN_129; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 57:22]
  wire  regOut_tag = 3'h0 == io_raddr1 ? 1'h0 : _GEN_130; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 56:22]
  wire [31:0] regOut_base = 3'h0 == io_raddr1 ? 32'h0 : _GEN_131; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 56:22]
  wire [31:0] regOut_length = 3'h0 == io_raddr1 ? 32'h0 : _GEN_132; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 56:22]
  wire [2:0] regOut_perms = 3'h0 == io_raddr1 ? 3'h0 : _GEN_133; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 56:22]
  wire [31:0] regOut_offset = 3'h0 == io_raddr1 ? 32'h0 : _GEN_134; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 55:21 56:22]
  wire  bypassMatch = _T_1 & io_waddr == io_raddr1; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 67:49]
  assign io_rdata1_tag = bypassMatch ? io_wdata_tag : regOut_tag; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 68:19]
  assign io_rdata1_base = bypassMatch ? io_wdata_base : regOut_base; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 68:19]
  assign io_rdata1_length = bypassMatch ? io_wdata_length : regOut_length; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 68:19]
  assign io_rdata1_perms = bypassMatch ? io_wdata_perms : regOut_perms; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 68:19]
  assign io_rdata1_offset = bypassMatch ? io_wdata_offset : regOut_offset; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 68:19]
  always @(posedge clock) begin
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 36:22]
      c3Reg_tag <= 1'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 36:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (3'h3 == io_waddr) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        c3Reg_tag <= io_wdata_tag; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 45:23]
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 36:22]
      c3Reg_base <= 32'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 36:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (3'h3 == io_waddr) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        c3Reg_base <= io_wdata_base; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 45:23]
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 36:22]
      c3Reg_length <= 32'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 36:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (3'h3 == io_waddr) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        c3Reg_length <= io_wdata_length; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 45:23]
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 36:22]
      c3Reg_perms <= 3'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 36:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (3'h3 == io_waddr) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        c3Reg_perms <= io_wdata_perms; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 45:23]
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 36:22]
      c3Reg_offset <= 32'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 36:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (3'h3 == io_waddr) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        c3Reg_offset <= io_wdata_offset; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 45:23]
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 37:22]
      c4Reg_tag <= 1'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 37:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (!(3'h3 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        if (3'h4 == io_waddr) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
          c4Reg_tag <= io_wdata_tag; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 46:23]
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 37:22]
      c4Reg_base <= 32'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 37:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (!(3'h3 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        if (3'h4 == io_waddr) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
          c4Reg_base <= io_wdata_base; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 46:23]
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 37:22]
      c4Reg_length <= 32'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 37:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (!(3'h3 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        if (3'h4 == io_waddr) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
          c4Reg_length <= io_wdata_length; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 46:23]
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 37:22]
      c4Reg_perms <= 3'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 37:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (!(3'h3 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        if (3'h4 == io_waddr) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
          c4Reg_perms <= io_wdata_perms; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 46:23]
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 37:22]
      c4Reg_offset <= 32'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 37:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (!(3'h3 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        if (3'h4 == io_waddr) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
          c4Reg_offset <= io_wdata_offset; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 46:23]
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 38:22]
      c5Reg_tag <= 1'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 38:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (!(3'h3 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        if (!(3'h4 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
          c5Reg_tag <= _GEN_15;
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 38:22]
      c5Reg_base <= 32'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 38:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (!(3'h3 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        if (!(3'h4 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
          c5Reg_base <= _GEN_16;
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 38:22]
      c5Reg_length <= 32'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 38:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (!(3'h3 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        if (!(3'h4 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
          c5Reg_length <= _GEN_17;
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 38:22]
      c5Reg_perms <= 3'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 38:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (!(3'h3 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        if (!(3'h4 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
          c5Reg_perms <= _GEN_18;
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 38:22]
      c5Reg_offset <= 32'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 38:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (!(3'h3 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        if (!(3'h4 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
          c5Reg_offset <= _GEN_19;
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22]
      c6Reg_tag <= 1'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (!(3'h3 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        if (!(3'h4 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
          c6Reg_tag <= _GEN_20;
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22]
      c6Reg_base <= 32'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (!(3'h3 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        if (!(3'h4 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
          c6Reg_base <= _GEN_21;
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22]
      c6Reg_length <= 32'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (!(3'h3 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        if (!(3'h4 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
          c6Reg_length <= _GEN_22;
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22]
      c6Reg_perms <= 3'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (!(3'h3 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        if (!(3'h4 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
          c6Reg_perms <= _GEN_23;
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22]
      c6Reg_offset <= 32'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 39:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (!(3'h3 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        if (!(3'h4 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
          c6Reg_offset <= _GEN_24;
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22]
      c7Reg_tag <= 1'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (!(3'h3 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        if (!(3'h4 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
          c7Reg_tag <= _GEN_25;
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22]
      c7Reg_base <= 32'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (!(3'h3 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        if (!(3'h4 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
          c7Reg_base <= _GEN_26;
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22]
      c7Reg_length <= 32'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (!(3'h3 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        if (!(3'h4 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
          c7Reg_length <= _GEN_27;
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22]
      c7Reg_perms <= 3'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (!(3'h3 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        if (!(3'h4 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
          c7Reg_perms <= _GEN_28;
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22]
      c7Reg_offset <= 32'h0; // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 40:22]
    end else if (io_wen & io_waddr >= 3'h3) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 43:37]
      if (!(3'h3 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
        if (!(3'h4 == io_waddr)) begin // @[src/main/scala/objective02/capability/CapabilityRegFile.scala 44:22]
          c7Reg_offset <= _GEN_29;
        end
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
  c3Reg_tag = _RAND_0[0:0];
  _RAND_1 = {1{`RANDOM}};
  c3Reg_base = _RAND_1[31:0];
  _RAND_2 = {1{`RANDOM}};
  c3Reg_length = _RAND_2[31:0];
  _RAND_3 = {1{`RANDOM}};
  c3Reg_perms = _RAND_3[2:0];
  _RAND_4 = {1{`RANDOM}};
  c3Reg_offset = _RAND_4[31:0];
  _RAND_5 = {1{`RANDOM}};
  c4Reg_tag = _RAND_5[0:0];
  _RAND_6 = {1{`RANDOM}};
  c4Reg_base = _RAND_6[31:0];
  _RAND_7 = {1{`RANDOM}};
  c4Reg_length = _RAND_7[31:0];
  _RAND_8 = {1{`RANDOM}};
  c4Reg_perms = _RAND_8[2:0];
  _RAND_9 = {1{`RANDOM}};
  c4Reg_offset = _RAND_9[31:0];
  _RAND_10 = {1{`RANDOM}};
  c5Reg_tag = _RAND_10[0:0];
  _RAND_11 = {1{`RANDOM}};
  c5Reg_base = _RAND_11[31:0];
  _RAND_12 = {1{`RANDOM}};
  c5Reg_length = _RAND_12[31:0];
  _RAND_13 = {1{`RANDOM}};
  c5Reg_perms = _RAND_13[2:0];
  _RAND_14 = {1{`RANDOM}};
  c5Reg_offset = _RAND_14[31:0];
  _RAND_15 = {1{`RANDOM}};
  c6Reg_tag = _RAND_15[0:0];
  _RAND_16 = {1{`RANDOM}};
  c6Reg_base = _RAND_16[31:0];
  _RAND_17 = {1{`RANDOM}};
  c6Reg_length = _RAND_17[31:0];
  _RAND_18 = {1{`RANDOM}};
  c6Reg_perms = _RAND_18[2:0];
  _RAND_19 = {1{`RANDOM}};
  c6Reg_offset = _RAND_19[31:0];
  _RAND_20 = {1{`RANDOM}};
  c7Reg_tag = _RAND_20[0:0];
  _RAND_21 = {1{`RANDOM}};
  c7Reg_base = _RAND_21[31:0];
  _RAND_22 = {1{`RANDOM}};
  c7Reg_length = _RAND_22[31:0];
  _RAND_23 = {1{`RANDOM}};
  c7Reg_perms = _RAND_23[2:0];
  _RAND_24 = {1{`RANDOM}};
  c7Reg_offset = _RAND_24[31:0];
`endif // RANDOMIZE_REG_INIT
  `endif // RANDOMIZE
end // initial
`ifdef FIRRTL_AFTER_INITIAL
`FIRRTL_AFTER_INITIAL
`endif
`endif // SYNTHESIS
endmodule
