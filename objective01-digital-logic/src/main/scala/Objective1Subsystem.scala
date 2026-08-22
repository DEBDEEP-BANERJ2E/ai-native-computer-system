import chisel3._
import objective01.datapath.{ALU, ALUOpcode}
import objective01.telemetry.TelemetryBlock

class Objective1Subsystem(val width: Int) extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(width.W))
    val b = Input(UInt(width.W))
    val opcode = Input(UInt(4.W))
    val operationValid = Input(Bool())
    val result = Output(UInt(width.W))
    val zero = Output(Bool())
    val negative = Output(Bool())
    val carry = Output(Bool())
    val overflow = Output(Bool())
    val busy = Output(Bool())
    val done = Output(Bool())
    val valid = Output(Bool())
    val telemetryAddress = Input(UInt(32.W))
    val telemetryData = Output(UInt(32.W))
  })

  val alu = Module(new ALU(width))
  val telemetry = Module(new TelemetryBlock(width))

  alu.io.a := io.a
  alu.io.b := io.b
  alu.io.opcode := io.opcode

  telemetry.io.operationValid := io.operationValid && alu.io.valid
  telemetry.io.reversibleOperation := false.B
  telemetry.io.claActive := io.opcode === ALUOpcode.ADD || io.opcode === ALUOpcode.SUB
  telemetry.io.multiplierActive := io.opcode === ALUOpcode.MUL
  telemetry.io.result := alu.io.result
  telemetry.io.readAddress := io.telemetryAddress

  io.result := alu.io.result
  io.zero := alu.io.zero
  io.negative := alu.io.negative
  io.carry := alu.io.carry
  io.overflow := alu.io.overflow
  io.busy := alu.io.busy
  io.done := alu.io.done
  io.valid := io.operationValid && alu.io.valid
  io.telemetryData := telemetry.io.readData
}