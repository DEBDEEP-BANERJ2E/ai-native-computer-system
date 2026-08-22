module TelemetryWaveform_tb;
  logic clock = 0;
  logic reset = 1;
  logic io_operationValid = 0;
  logic io_reversibleOperation = 0;
  logic io_claActive = 0;
  logic io_multiplierActive = 0;
  logic [31:0] io_result = 0;
  logic [31:0] io_readAddress = 32'h80001004;
  wire [31:0] io_readData;

  TelemetryBlock dut (
    .clock(clock), .reset(reset), .io_operationValid(io_operationValid),
    .io_reversibleOperation(io_reversibleOperation), .io_claActive(io_claActive),
    .io_multiplierActive(io_multiplierActive), .io_result(io_result),
    .io_readAddress(io_readAddress), .io_readData(io_readData)
  );
  always #1 clock = ~clock;

  initial begin
    $dumpfile("visualization/waveforms/telemetry.vcd");
    $dumpvars(0, TelemetryWaveform_tb);
    #2 reset = 0; io_operationValid = 1; io_claActive = 1; io_result = 0;
    #2 io_result = 32'h000000ff;
    #2 io_claActive = 0; io_multiplierActive = 1; io_readAddress = 32'h80001008;
    #2 io_result = 32'h00000000;
    #2 io_readAddress = 32'h8000100c;
    #2 $finish;
  end
endmodule