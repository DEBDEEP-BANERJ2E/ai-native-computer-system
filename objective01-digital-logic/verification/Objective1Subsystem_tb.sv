module Objective1Subsystem_tb;
  logic clock = 0;
  logic reset = 1;
  logic [31:0] io_a = 0;
  logic [31:0] io_b = 0;
  logic [3:0] io_opcode = 0;
  logic io_operationValid = 0;
  logic [31:0] io_telemetryAddress = 32'h80001004;
  wire [31:0] io_result;
  wire io_zero;
  wire io_negative;
  wire io_carry;
  wire io_overflow;
  wire io_busy;
  wire io_done;
  wire io_valid;
  wire [31:0] io_telemetryData;

  Objective1Subsystem dut (
    .clock(clock), .reset(reset), .io_a(io_a), .io_b(io_b),
    .io_opcode(io_opcode), .io_operationValid(io_operationValid),
    .io_result(io_result), .io_zero(io_zero), .io_negative(io_negative),
    .io_carry(io_carry), .io_overflow(io_overflow), .io_busy(io_busy),
    .io_done(io_done), .io_valid(io_valid),
    .io_telemetryAddress(io_telemetryAddress),
    .io_telemetryData(io_telemetryData)
  );

  always #1 clock = ~clock;

  initial begin
    #2;
    reset = 0;
    io_a = 32'd2;
    io_b = 32'd3;
    io_opcode = 4'd0;
    io_operationValid = 1;
    #2;
    if (io_result !== 32'd5) $fatal(1, "ADD result mismatch: %0d", io_result);
    if (io_telemetryData !== 32'd2) $fatal(1, "CLA telemetry mismatch: %0d", io_telemetryData);
    if (io_busy !== 1'b0 || io_done !== 1'b1 || io_valid !== 1'b1) $fatal(1, "ALU protocol mismatch");
    $display("Objective1Subsystem Verilator smoke test passed");
    $finish;
  end
endmodule