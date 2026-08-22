import chisel3.stage.ChiselStage
import objective01.arithmetic.{BoothMultiplier, BoothWallaceMultiplier, CarryLookaheadAdder, RippleCarryAdder, SimpleMultiplier}
import objective01.datapath.ALU
import objective01.telemetry.TelemetryBlock

object GenerateRTL extends App {
  val targetDirectory = "generated"
  (new ChiselStage).emitSystemVerilog(new RippleCarryAdder(32), Array("--target-dir", targetDirectory))
  (new ChiselStage).emitSystemVerilog(new CarryLookaheadAdder(32), Array("--target-dir", targetDirectory))
  (new ChiselStage).emitSystemVerilog(new SimpleMultiplier(16), Array("--target-dir", targetDirectory))
  (new ChiselStage).emitSystemVerilog(new BoothMultiplier(16), Array("--target-dir", targetDirectory))
  (new ChiselStage).emitSystemVerilog(new BoothWallaceMultiplier(16), Array("--target-dir", targetDirectory))
  (new ChiselStage).emitSystemVerilog(new ALU(32), Array("--target-dir", targetDirectory))
  (new ChiselStage).emitSystemVerilog(new TelemetryBlock(32), Array("--target-dir", targetDirectory))
  (new ChiselStage).emitSystemVerilog(new Objective1Subsystem(32), Array("--target-dir", targetDirectory))
}