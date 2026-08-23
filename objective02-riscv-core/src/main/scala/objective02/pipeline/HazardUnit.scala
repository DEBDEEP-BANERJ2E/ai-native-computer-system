package objective02.pipeline

import chisel3._

// =========================================================================
// Hazard Detection Unit
// Detects Load-Use data hazards and manages pipeline stalls and bubble insertion.
// When an instruction in ID reads a register that is being loaded by the
// instruction in EX, the pipeline is stalled for 1 cycle:
// - Program Counter is held (stallIF)
// - IF/ID pipeline register is held (stallID)
// - ID/EX pipeline register is flushed to insert an invalid bubble (flushIDEX)
// Note: Branch/Jump redirection in EX has strict priority and overrides stalls.
// =========================================================================
class HazardUnitIO extends Bundle {
  // Consumer state in ID stage
  val idValid     = Input(Bool())
  val idRs1       = Input(UInt(5.W))
  val idRs2       = Input(UInt(5.W))
  val idUsesRs1   = Input(Bool())
  val idUsesRs2   = Input(Bool())

  // Producer state in EX stage (Load instruction)
  val idExValid   = Input(Bool())
  val idExMemRead = Input(Bool())
  val idExRd      = Input(UInt(5.W))

  // Branch / Jump redirect from EX stage (higher priority than stall)
  val branchTaken = Input(Bool())

  // Pipeline control outputs
  val loadUseHazard = Output(Bool())
  val stallIF       = Output(Bool())
  val stallID       = Output(Bool())
  val flushIFID     = Output(Bool())
  val flushIDEX     = Output(Bool())
}

class HazardUnit extends Module {
  val io = IO(new HazardUnitIO)

  // Load-Use hazard occurs when a valid consumer in ID requires rs1/rs2 being loaded by valid instruction in EX
  val isLoadUse = io.idValid && io.idExValid && io.idExMemRead && (io.idExRd =/= 0.U) && (
    (io.idUsesRs1 && (io.idExRd === io.idRs1)) ||
    (io.idUsesRs2 && (io.idExRd === io.idRs2))
  )

  io.loadUseHazard := isLoadUse

  // Branch redirect/flush has strict priority over load-use stall
  io.stallIF   := isLoadUse && !io.branchTaken
  io.stallID   := isLoadUse && !io.branchTaken

  io.flushIFID := io.branchTaken
  io.flushIDEX := io.branchTaken || isLoadUse
}
