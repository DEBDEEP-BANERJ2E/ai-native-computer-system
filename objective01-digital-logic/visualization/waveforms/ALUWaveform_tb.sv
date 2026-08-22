module ALUWaveform_tb;
  logic clock = 0;
  logic reset = 0;
  logic [31:0] io_a = 0;
  logic [31:0] io_b = 0;
  logic [3:0] io_opcode = 0;
  wire [31:0] io_result;
  wire io_zero, io_negative, io_carry, io_overflow;
  wire io_busy, io_done, io_valid;

  ALU dut (
    .clock(clock), .reset(reset), .io_a(io_a), .io_b(io_b), .io_opcode(io_opcode),
    .io_result(io_result), .io_zero(io_zero), .io_negative(io_negative),
    .io_carry(io_carry), .io_overflow(io_overflow), .io_busy(io_busy),
    .io_done(io_done), .io_valid(io_valid)
  );
  always #1 clock = ~clock;

  initial begin
    $dumpfile("visualization/waveforms/alu_operations.vcd");
    $dumpvars(0, ALUWaveform_tb);
    #2 io_a = 32'd5; io_b = 32'd3; io_opcode = 4'd0;
    #2 io_a = 32'd5; io_b = 32'd3; io_opcode = 4'd1;
    #2 io_a = 32'hf0; io_b = 32'h0f; io_opcode = 4'd4;
    #2 io_a = 32'h80000000; io_b = 32'd1; io_opcode = 4'd7;
    #2 io_a = 32'd7; io_b = 32'd6; io_opcode = 4'd10;
    #2 $finish;
  end
endmodule