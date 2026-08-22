package objective01.telemetry

import chisel3._
import chisel3.util._

object TelemetryAddress {
  val Base = "h80001000".U(32.W)
  val RevEnergyAcc = "h80001000".U(32.W)
  val ClaSwitching = "h80001004".U(32.W)
  val MulThermal = "h80001008".U(32.W)
  val EdpCurrent = "h8000100c".U(32.W)
  val EdpConfig = "h80001010".U(32.W)
}

class TelemetryBlock(val width: Int) extends Module {
  require(width > 0, "TelemetryBlock width must be positive")

  val io = IO(new Bundle {
    val operationValid = Input(Bool())
    val reversibleOperation = Input(Bool())
    val claActive = Input(Bool())
    val multiplierActive = Input(Bool())
    val result = Input(UInt(width.W))
    val readAddress = Input(UInt(32.W))
    val readData = Output(UInt(32.W))
  })

  val reversibleEnergy = RegInit(0.U(32.W))
  val claSwitching = RegInit(0.U(32.W))
  val multiplierThermal = RegInit(0.U(32.W))
  val edpConfig = RegInit(1.U(32.W))
  val previousResult = RegInit(0.U(width.W))

  val changedBits = PopCount(io.result ^ previousResult)
  val activity = Mux(io.operationValid, changedBits, 0.U)
  val energyProxy = reversibleEnergy +& claSwitching +& multiplierThermal
  val edpProxy = energyProxy * edpConfig

  when (io.operationValid) {
    reversibleEnergy := reversibleEnergy + Mux(io.reversibleOperation, 1.U, 0.U)
    claSwitching := claSwitching + Mux(io.claActive, activity, 0.U)
    multiplierThermal := multiplierThermal + Mux(io.multiplierActive, activity, 0.U)
    previousResult := io.result
  }

  io.readData := MuxLookup(io.readAddress, 0.U(32.W))(Seq(
    TelemetryAddress.RevEnergyAcc -> reversibleEnergy,
    TelemetryAddress.ClaSwitching -> claSwitching,
    TelemetryAddress.MulThermal -> multiplierThermal,
    TelemetryAddress.EdpCurrent -> edpProxy(31, 0),
    TelemetryAddress.EdpConfig -> edpConfig
  ))
}