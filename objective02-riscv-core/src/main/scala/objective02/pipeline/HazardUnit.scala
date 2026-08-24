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
  val idValid       = Input(Bool())
  val idRs1         = Input(UInt(5.W))
  val idRs2         = Input(UInt(5.W))
  val idUsesRs1     = Input(Bool())
  val idUsesRs2     = Input(Bool())
  val idCs1         = Input(UInt(3.W))
  val idUsesCapRs1  = Input(Bool())

  // Producer state in EX stage (Load / Capability instruction)
  val idExValid       = Input(Bool())
  val idExMemRead     = Input(Bool())
  val idExRd          = Input(UInt(5.W))
  val idExCapRegWrite = Input(Bool())
  val idExCapRd       = Input(UInt(3.W))

  // Producer state in MEM stage (Capability instruction)
  val exMemValid       = Input(Bool())
  val exMemCapRegWrite = Input(Bool())
  val exMemCapRd       = Input(UInt(3.W))

  // Branch / Jump redirect from EX stage
  val branchTaken = Input(Bool())

  // Objective 2 Phase 8 Precise Trap & Return redirects from MEM stage (highest priority)
  val trapTaken   = Input(Bool())
  val trapReturn  = Input(Bool())

  // Pipeline control outputs
  val loadUseHazard = Output(Bool())
  val capHazard     = Output(Bool())
  val stallIF       = Output(Bool())
  val stallID       = Output(Bool())
  val flushIFID     = Output(Bool())
  val flushIDEX     = Output(Bool())
}

class HazardUnit extends Module {
  val io = IO(new HazardUnitIO)

  // Load-Use hazard occurs when a valid consumer in ID requires integer rs1/rs2 being loaded by valid instruction in EX
  val isLoadUse = io.idValid && io.idExValid && io.idExMemRead && (io.idExRd =/= 0.U) && (
    (io.idUsesRs1 && (io.idExRd === io.idRs1)) ||
    (io.idUsesRs2 && (io.idExRd === io.idRs2))
  )

  // Capability RAW hazard occurs when ID reads capability cX (cX != 0) and an in-flight producer in ID/EX or EX/MEM will write cX.
  // Note: MEM/WB producer is handled seamlessly by WB->ID bypass without stalling.
  val isCapHazard = io.idValid && io.idUsesCapRs1 && (io.idCs1 =/= 0.U) && (
    (io.idExValid && io.idExCapRegWrite && (io.idExCapRd === io.idCs1)) ||
    (io.exMemValid && io.exMemCapRegWrite && (io.exMemCapRd === io.idCs1))
  )

  val isFlush = io.trapTaken || io.trapReturn || io.branchTaken
  val isStall = (isLoadUse || isCapHazard) && !isFlush

  io.loadUseHazard := isLoadUse
  io.capHazard     := isCapHazard

  // Redirects and flushes have strict priority over stalls
  io.stallIF   := isStall
  io.stallID   := isStall

  io.flushIFID := isFlush
  io.flushIDEX := isFlush || isLoadUse || isCapHazard
}
