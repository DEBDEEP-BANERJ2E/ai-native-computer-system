module IterativeDivider(
  input         clock,
  input         reset,
  input         io_start, // @[src/main/scala/objective02/execute/IterativeDivider.scala 22:14]
  input         io_kill, // @[src/main/scala/objective02/execute/IterativeDivider.scala 22:14]
  input  [31:0] io_dividend, // @[src/main/scala/objective02/execute/IterativeDivider.scala 22:14]
  input  [31:0] io_divisor, // @[src/main/scala/objective02/execute/IterativeDivider.scala 22:14]
  input         io_isSigned, // @[src/main/scala/objective02/execute/IterativeDivider.scala 22:14]
  output        io_busy, // @[src/main/scala/objective02/execute/IterativeDivider.scala 22:14]
  output        io_done, // @[src/main/scala/objective02/execute/IterativeDivider.scala 22:14]
  output [31:0] io_quotient, // @[src/main/scala/objective02/execute/IterativeDivider.scala 22:14]
  output [31:0] io_remainder, // @[src/main/scala/objective02/execute/IterativeDivider.scala 22:14]
  output [5:0]  io_iteration // @[src/main/scala/objective02/execute/IterativeDivider.scala 22:14]
);
`ifdef RANDOMIZE_REG_INIT
  reg [31:0] _RAND_0;
  reg [31:0] _RAND_1;
  reg [63:0] _RAND_2;
  reg [31:0] _RAND_3;
  reg [63:0] _RAND_4;
  reg [31:0] _RAND_5;
  reg [31:0] _RAND_6;
  reg [31:0] _RAND_7;
  reg [31:0] _RAND_8;
`endif // RANDOMIZE_REG_INIT
  reg [1:0] state; // @[src/main/scala/objective02/execute/IterativeDivider.scala 26:22]
  reg [5:0] count; // @[src/main/scala/objective02/execute/IterativeDivider.scala 27:22]
  reg [32:0] aReg; // @[src/main/scala/objective02/execute/IterativeDivider.scala 39:21]
  reg [31:0] qReg; // @[src/main/scala/objective02/execute/IterativeDivider.scala 40:21]
  reg [32:0] mReg; // @[src/main/scala/objective02/execute/IterativeDivider.scala 41:21]
  reg  qNeg; // @[src/main/scala/objective02/execute/IterativeDivider.scala 44:21]
  reg  rNeg; // @[src/main/scala/objective02/execute/IterativeDivider.scala 45:21]
  reg [31:0] finalQuotient; // @[src/main/scala/objective02/execute/IterativeDivider.scala 48:30]
  reg [31:0] finalRemainder; // @[src/main/scala/objective02/execute/IterativeDivider.scala 49:31]
  wire  dividendSign = io_isSigned & io_dividend[31]; // @[src/main/scala/objective02/execute/IterativeDivider.scala 59:34]
  wire  divisorSign = io_isSigned & io_divisor[31]; // @[src/main/scala/objective02/execute/IterativeDivider.scala 60:33]
  wire [31:0] _absDividend_T = ~io_dividend; // @[src/main/scala/objective02/execute/IterativeDivider.scala 63:37]
  wire [31:0] _absDividend_T_2 = _absDividend_T + 32'h1; // @[src/main/scala/objective02/execute/IterativeDivider.scala 63:51]
  wire [31:0] absDividend = dividendSign ? _absDividend_T_2 : io_dividend; // @[src/main/scala/objective02/execute/IterativeDivider.scala 63:21]
  wire [31:0] _absDivisor_T = ~io_divisor; // @[src/main/scala/objective02/execute/IterativeDivider.scala 66:35]
  wire [31:0] _absDivisor_T_2 = _absDivisor_T + 32'h1; // @[src/main/scala/objective02/execute/IterativeDivider.scala 66:48]
  wire [31:0] absDivisor = divisorSign ? _absDivisor_T_2 : io_divisor; // @[src/main/scala/objective02/execute/IterativeDivider.scala 66:20]
  wire  isDivByZero = io_divisor == 32'h0; // @[src/main/scala/objective02/execute/IterativeDivider.scala 68:33]
  wire  isOverflow = io_isSigned & io_dividend == 32'h80000000 & io_divisor == 32'hffffffff; // @[src/main/scala/objective02/execute/IterativeDivider.scala 69:67]
  wire [32:0] _mReg_T = {1'h0,absDivisor}; // @[src/main/scala/objective02/execute/IterativeDivider.scala 92:24]
  wire [31:0] _GEN_0 = isOverflow ? 32'h80000000 : finalQuotient; // @[src/main/scala/objective02/execute/IterativeDivider.scala 85:34 86:27 48:30]
  wire [31:0] _GEN_1 = isOverflow ? 32'h0 : finalRemainder; // @[src/main/scala/objective02/execute/IterativeDivider.scala 85:34 87:28 49:31]
  wire [1:0] _GEN_2 = isOverflow ? 2'h2 : 2'h1; // @[src/main/scala/objective02/execute/IterativeDivider.scala 85:34 88:19 94:19]
  wire [32:0] _GEN_3 = isOverflow ? aReg : 33'h0; // @[src/main/scala/objective02/execute/IterativeDivider.scala 39:21 85:34 90:18]
  wire [31:0] _GEN_4 = isOverflow ? qReg : absDividend; // @[src/main/scala/objective02/execute/IterativeDivider.scala 40:21 85:34 91:18]
  wire [32:0] _GEN_5 = isOverflow ? mReg : _mReg_T; // @[src/main/scala/objective02/execute/IterativeDivider.scala 41:21 85:34 92:18]
  wire [5:0] _GEN_6 = isOverflow ? count : 6'h20; // @[src/main/scala/objective02/execute/IterativeDivider.scala 27:22 85:34 93:19]
  wire [31:0] _GEN_7 = isDivByZero ? 32'hffffffff : _GEN_0; // @[src/main/scala/objective02/execute/IterativeDivider.scala 81:29 82:27]
  wire [31:0] _GEN_8 = isDivByZero ? io_dividend : _GEN_1; // @[src/main/scala/objective02/execute/IterativeDivider.scala 81:29 83:28]
  wire [1:0] _GEN_9 = isDivByZero ? 2'h2 : _GEN_2; // @[src/main/scala/objective02/execute/IterativeDivider.scala 81:29 84:19]
  wire [32:0] _GEN_10 = isDivByZero ? aReg : _GEN_3; // @[src/main/scala/objective02/execute/IterativeDivider.scala 39:21 81:29]
  wire [31:0] _GEN_11 = isDivByZero ? qReg : _GEN_4; // @[src/main/scala/objective02/execute/IterativeDivider.scala 40:21 81:29]
  wire [32:0] _GEN_12 = isDivByZero ? mReg : _GEN_5; // @[src/main/scala/objective02/execute/IterativeDivider.scala 41:21 81:29]
  wire [5:0] _GEN_13 = isDivByZero ? count : _GEN_6; // @[src/main/scala/objective02/execute/IterativeDivider.scala 27:22 81:29]
  wire [32:0] shiftedA = {aReg[31:0],qReg[31]}; // @[src/main/scala/objective02/execute/IterativeDivider.scala 100:27]
  wire [31:0] shiftedQ = {qReg[30:0],1'h0}; // @[src/main/scala/objective02/execute/IterativeDivider.scala 101:27]
  wire [32:0] subA = shiftedA - mReg; // @[src/main/scala/objective02/execute/IterativeDivider.scala 103:29]
  wire  isNeg = subA[32]; // @[src/main/scala/objective02/execute/IterativeDivider.scala 104:25]
  wire [31:0] _qReg_T = shiftedQ | 32'h1; // @[src/main/scala/objective02/execute/IterativeDivider.scala 113:28]
  wire [32:0] _GEN_23 = isNeg ? shiftedA : subA; // @[src/main/scala/objective02/execute/IterativeDivider.scala 106:21 108:16 112:16]
  wire [31:0] _GEN_24 = isNeg ? shiftedQ : _qReg_T; // @[src/main/scala/objective02/execute/IterativeDivider.scala 106:21 109:16 113:16]
  wire [5:0] _count_T_1 = count - 6'h1; // @[src/main/scala/objective02/execute/IterativeDivider.scala 116:24]
  wire [31:0] rResult = _GEN_23[31:0]; // @[src/main/scala/objective02/execute/IterativeDivider.scala 121:51]
  wire [31:0] _finalQuotient_T = ~_GEN_24; // @[src/main/scala/objective02/execute/IterativeDivider.scala 123:39]
  wire [31:0] _finalQuotient_T_2 = _finalQuotient_T + 32'h1; // @[src/main/scala/objective02/execute/IterativeDivider.scala 123:49]
  wire [31:0] _finalQuotient_T_3 = qNeg ? _finalQuotient_T_2 : _GEN_24; // @[src/main/scala/objective02/execute/IterativeDivider.scala 123:31]
  wire [31:0] _finalRemainder_T = ~rResult; // @[src/main/scala/objective02/execute/IterativeDivider.scala 124:40]
  wire [31:0] _finalRemainder_T_2 = _finalRemainder_T + 32'h1; // @[src/main/scala/objective02/execute/IterativeDivider.scala 124:50]
  wire [31:0] _finalRemainder_T_3 = rNeg ? _finalRemainder_T_2 : rResult; // @[src/main/scala/objective02/execute/IterativeDivider.scala 124:32]
  wire [1:0] _GEN_25 = count == 6'h1 ? 2'h2 : state; // @[src/main/scala/objective02/execute/IterativeDivider.scala 117:29 118:17 26:22]
  wire [31:0] _GEN_26 = count == 6'h1 ? _finalQuotient_T_3 : finalQuotient; // @[src/main/scala/objective02/execute/IterativeDivider.scala 117:29 123:25 48:30]
  wire [31:0] _GEN_27 = count == 6'h1 ? _finalRemainder_T_3 : finalRemainder; // @[src/main/scala/objective02/execute/IterativeDivider.scala 117:29 124:26 49:31]
  wire [1:0] _GEN_28 = 2'h2 == state ? 2'h0 : state; // @[src/main/scala/objective02/execute/IterativeDivider.scala 129:15 75:19 26:22]
  assign io_busy = state != 2'h0; // @[src/main/scala/objective02/execute/IterativeDivider.scala 52:20]
  assign io_done = state == 2'h2; // @[src/main/scala/objective02/execute/IterativeDivider.scala 53:20]
  assign io_quotient = finalQuotient; // @[src/main/scala/objective02/execute/IterativeDivider.scala 54:15]
  assign io_remainder = finalRemainder; // @[src/main/scala/objective02/execute/IterativeDivider.scala 55:16]
  assign io_iteration = count; // @[src/main/scala/objective02/execute/IterativeDivider.scala 56:16]
  always @(posedge clock) begin
    if (reset) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 26:22]
      state <= 2'h0; // @[src/main/scala/objective02/execute/IterativeDivider.scala 26:22]
    end else if (io_kill) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 71:17]
      state <= 2'h0; // @[src/main/scala/objective02/execute/IterativeDivider.scala 72:11]
    end else if (2'h0 == state) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 75:19]
      if (io_start) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 77:24]
        state <= _GEN_9;
      end
    end else if (2'h1 == state) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 75:19]
      state <= _GEN_25;
    end else begin
      state <= _GEN_28;
    end
    if (reset) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 27:22]
      count <= 6'h0; // @[src/main/scala/objective02/execute/IterativeDivider.scala 27:22]
    end else if (io_kill) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 71:17]
      count <= 6'h0; // @[src/main/scala/objective02/execute/IterativeDivider.scala 73:11]
    end else if (2'h0 == state) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 75:19]
      if (io_start) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 77:24]
        count <= _GEN_13;
      end
    end else if (2'h1 == state) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 75:19]
      count <= _count_T_1; // @[src/main/scala/objective02/execute/IterativeDivider.scala 116:15]
    end
    if (reset) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 39:21]
      aReg <= 33'h0; // @[src/main/scala/objective02/execute/IterativeDivider.scala 39:21]
    end else if (!(io_kill)) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 71:17]
      if (2'h0 == state) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 75:19]
        if (io_start) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 77:24]
          aReg <= _GEN_10;
        end
      end else if (2'h1 == state) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 75:19]
        aReg <= _GEN_23;
      end
    end
    if (reset) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 40:21]
      qReg <= 32'h0; // @[src/main/scala/objective02/execute/IterativeDivider.scala 40:21]
    end else if (!(io_kill)) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 71:17]
      if (2'h0 == state) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 75:19]
        if (io_start) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 77:24]
          qReg <= _GEN_11;
        end
      end else if (2'h1 == state) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 75:19]
        qReg <= _GEN_24;
      end
    end
    if (reset) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 41:21]
      mReg <= 33'h0; // @[src/main/scala/objective02/execute/IterativeDivider.scala 41:21]
    end else if (!(io_kill)) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 71:17]
      if (2'h0 == state) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 75:19]
        if (io_start) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 77:24]
          mReg <= _GEN_12;
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 44:21]
      qNeg <= 1'h0; // @[src/main/scala/objective02/execute/IterativeDivider.scala 44:21]
    end else if (!(io_kill)) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 71:17]
      if (2'h0 == state) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 75:19]
        if (io_start) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 77:24]
          qNeg <= dividendSign ^ divisorSign; // @[src/main/scala/objective02/execute/IterativeDivider.scala 78:16]
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 45:21]
      rNeg <= 1'h0; // @[src/main/scala/objective02/execute/IterativeDivider.scala 45:21]
    end else if (!(io_kill)) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 71:17]
      if (2'h0 == state) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 75:19]
        if (io_start) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 77:24]
          rNeg <= dividendSign; // @[src/main/scala/objective02/execute/IterativeDivider.scala 79:16]
        end
      end
    end
    if (reset) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 48:30]
      finalQuotient <= 32'h0; // @[src/main/scala/objective02/execute/IterativeDivider.scala 48:30]
    end else if (!(io_kill)) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 71:17]
      if (2'h0 == state) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 75:19]
        if (io_start) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 77:24]
          finalQuotient <= _GEN_7;
        end
      end else if (2'h1 == state) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 75:19]
        finalQuotient <= _GEN_26;
      end
    end
    if (reset) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 49:31]
      finalRemainder <= 32'h0; // @[src/main/scala/objective02/execute/IterativeDivider.scala 49:31]
    end else if (!(io_kill)) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 71:17]
      if (2'h0 == state) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 75:19]
        if (io_start) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 77:24]
          finalRemainder <= _GEN_8;
        end
      end else if (2'h1 == state) begin // @[src/main/scala/objective02/execute/IterativeDivider.scala 75:19]
        finalRemainder <= _GEN_27;
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
  _RAND_2 = {2{`RANDOM}};
  aReg = _RAND_2[32:0];
  _RAND_3 = {1{`RANDOM}};
  qReg = _RAND_3[31:0];
  _RAND_4 = {2{`RANDOM}};
  mReg = _RAND_4[32:0];
  _RAND_5 = {1{`RANDOM}};
  qNeg = _RAND_5[0:0];
  _RAND_6 = {1{`RANDOM}};
  rNeg = _RAND_6[0:0];
  _RAND_7 = {1{`RANDOM}};
  finalQuotient = _RAND_7[31:0];
  _RAND_8 = {1{`RANDOM}};
  finalRemainder = _RAND_8[31:0];
`endif // RANDOMIZE_REG_INIT
  `endif // RANDOMIZE
end // initial
`ifdef FIRRTL_AFTER_INITIAL
`FIRRTL_AFTER_INITIAL
`endif
`endif // SYNTHESIS
endmodule
