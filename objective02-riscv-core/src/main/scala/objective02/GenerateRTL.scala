package objective02

import chisel3.stage.ChiselStage
import objective02.core.SingleCycleCore
import objective02.pipeline.PipelinedCore
import objective02.execute.IterativeDivider
import objective02.capability.CapabilityRegFile
import objective02.system.SystemMMIO

object GenerateRTL extends App {
  val targetDirectory = "generated"
  (new ChiselStage).emitSystemVerilog(new SingleCycleCore, Array("--target-dir", targetDirectory))
  (new ChiselStage).emitSystemVerilog(new PipelinedCore, Array("--target-dir", targetDirectory))
  (new ChiselStage).emitSystemVerilog(new IterativeDivider, Array("--target-dir", targetDirectory))
  (new ChiselStage).emitSystemVerilog(new CapabilityRegFile, Array("--target-dir", targetDirectory))
  (new ChiselStage).emitSystemVerilog(new SystemMMIO, Array("--target-dir", targetDirectory))
  println(s"Successfully emitted Objective 2 SystemVerilog RTL to $targetDirectory/")
}
