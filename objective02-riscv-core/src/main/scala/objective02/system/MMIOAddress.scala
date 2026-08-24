package objective02.system

import chisel3._

object MMIOAddress {
  // =========================================================================
  // 1. Objective 1 Telemetry Block Address Map (0x80001000 - 0x80001010)
  // =========================================================================
  val TELEMETRY_BASE          = "h80001000".U(32.W)
  val REV_ENERGY_ACC          = "h80001000".U(32.W) // RO, Reversible energy activity proxy (0)
  val CLA_SWITCHING           = "h80001004".U(32.W) // RO, Result-bus Hamming distance activity proxy on CLA ops
  val MUL_THERMAL             = "h80001008".U(32.W) // RO, Multiplier switching activity proxy
  val EDP_CURRENT             = "h8000100c".U(32.W) // RO, Energy-Delay Product proxy
  val EDP_CONFIG              = "h80001010".U(32.W) // RO, Delay/configuration scale constant (1)

  // =========================================================================
  // 2. Objective 2 System Control & Telemetry Registers (0x80002000 - 0x80002020)
  // =========================================================================
  val SYS_BASE                = "h80002000".U(32.W)
  val BRANCH_CONFIDENCE       = "h80002000".U(32.W) // RO, Branch predictor confidence (0 for Phase 6)
  val PROCESS_BEHAVIOR_CLASS  = "h80002004".U(32.W) // RW, Compiler/process behavior classification hint
  val SCHED_HINT              = "h80002008".U(32.W) // RW, OS scheduler hint
  val RETIRED_COUNT           = "h8000200c".U(32.W) // RO, Total retired architectural instructions
  val BRANCH_TAKEN_COUNT      = "h80002010".U(32.W) // RO, Count of taken conditional branches
  val LOAD_USE_STALL_COUNT    = "h80002014".U(32.W) // RO, Count of load-use interlock stall cycles
  val DIV_BUSY_CYCLES         = "h80002018".U(32.W) // RO, Count of divider busy cycles
  val PIPELINE_STALL_COUNT    = "h8000201c".U(32.W) // RO, Count of total frontend stall cycles
  val LAST_COMMIT_PC          = "h80002020".U(32.W) // RO, PC of most recently retired instruction
  val CURRENT_CONTEXT         = "h80002024".U(32.W) // RW, Active OS/thread context ID (Reset 0)

  // =========================================================================
  // 3. Objective 2 Security Event Logger Window (0x80002100 - 0x80002110)
  // =========================================================================
  val SEC_BASE                = "h80002100".U(32.W)
  val SEC_STATUS              = "h80002100".U(32.W) // RW, Bit 0: violation pending (Write 1 to clear)
  val SEC_PC                  = "h80002104".U(32.W) // RO, Offending instruction PC
  val SEC_ADDR                = "h80002108".U(32.W) // RO, Offending memory address
  val SEC_INFO                = "h8000210c".U(32.W) // RO, [5:4] accessType, [3:0] reason
  val SEC_CONTEXT             = "h80002110".U(32.W) // RO, Process/scheduler context at violation

  // =========================================================================
  // 4. Objective 2 Phase 8 Precise Security Trapping Window (0x80002114 - 0x80002130)
  // =========================================================================
  val TRAP_CONTROL            = "h80002114".U(32.W) // RW, Bit 0: TRAP_ENABLE
  val TRAP_STATUS             = "h80002118".U(32.W) // RW, Bit 0: ACTIVE (RO), Bit 1: DOUBLE_FAULT (W1C)
  val TRAP_VECTOR             = "h8000211c".U(32.W) // RW, 4-byte aligned base PC of trap handler
  val TRAP_EPC                = "h80002120".U(32.W) // RW, Offending instruction PC (writeable when ACTIVE)
  val TRAP_CAUSE              = "h80002124".U(32.W) // RO, [5:4] accessType, [3:0] reason
  val TRAP_ADDR               = "h80002128".U(32.W) // RO, Offending memory address
  val TRAP_CONTEXT            = "h8000212c".U(32.W) // RO, Process context at trap
  val TRAP_RETURN             = "h80002130".U(32.W) // WO, Write 1 to return from trap handler
}

