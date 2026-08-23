package objective02.pipeline

import chisel3._

// =========================================================================
// Data Forwarding Unit
// Resolves RAW (Read-After-Write) data hazards by forwarding results to EX stage:
// 1. EX/MEM pipeline register -> EX stage (1 cycle latency, arithmetic/imm/link)
// 2. MEM/WB pipeline register -> EX stage (2 cycle latency, all wbData including loads)
// Note: EX/MEM forwarding is inhibited if the producer is a Load instruction (memRead=true).
// =========================================================================
class ForwardingUnitIO extends Bundle {
  // Source register addresses of the instruction in EX stage
  val idExRs1       = Input(UInt(5.W))
  val idExRs2       = Input(UInt(5.W))

  // Producer state in EX/MEM stage
  val exMemValid    = Input(Bool())
  val exMemRegWrite = Input(Bool())
  val exMemMemRead  = Input(Bool()) // Inhibits EX/MEM forward if true (load address is not load data)
  val exMemRd       = Input(UInt(5.W))

  // Producer state in MEM/WB stage
  val memWbValid    = Input(Bool())
  val memWbRegWrite = Input(Bool())
  val memWbRd       = Input(UInt(5.W))

  // Forwarding select signals for RS1 and RS2:
  // 0.U = Use ID/EX latched value (no forward)
  // 1.U = Forward from MEM/WB stage (wbData)
  // 2.U = Forward from EX/MEM stage (exMemForwardData)
  val forwardA      = Output(UInt(2.W))
  val forwardB      = Output(UInt(2.W))
}

class ForwardingUnit extends Module {
  val io = IO(new ForwardingUnitIO)

  // EX/MEM hazard: valid non-load instruction writes to rd != 0 matching rs
  val exMemHazardA = io.exMemValid && io.exMemRegWrite && !io.exMemMemRead && (io.exMemRd =/= 0.U) && (io.exMemRd === io.idExRs1)
  val exMemHazardB = io.exMemValid && io.exMemRegWrite && !io.exMemMemRead && (io.exMemRd =/= 0.U) && (io.exMemRd === io.idExRs2)

  // MEM/WB hazard: valid instruction writes to rd != 0 matching rs
  val memWbHazardA = io.memWbValid && io.memWbRegWrite && (io.memWbRd =/= 0.U) && (io.memWbRd === io.idExRs1)
  val memWbHazardB = io.memWbValid && io.memWbRegWrite && (io.memWbRd =/= 0.U) && (io.memWbRd === io.idExRs2)

  // Priority: EX/MEM (most recent write) > MEM/WB > ID/EX
  when(exMemHazardA) {
    io.forwardA := 2.U
  }.elsewhen(memWbHazardA) {
    io.forwardA := 1.U
  }.otherwise {
    io.forwardA := 0.U
  }

  when(exMemHazardB) {
    io.forwardB := 2.U
  }.elsewhen(memWbHazardB) {
    io.forwardB := 1.U
  }.otherwise {
    io.forwardB := 0.U
  }
}
