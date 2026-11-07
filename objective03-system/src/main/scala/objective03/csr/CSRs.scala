package objective03.csr

// =========================================================================
// AN32-System-v1 Privileged Architecture Constants
// CSR address map, privilege levels, trap causes, and status-bit layouts.
// Derived from spec/system-v1-requirements.md and spec/machine-profiles.yaml.
// =========================================================================
object Privilege {
  val U = 0 // User mode
  val S = 1 // Supervisor mode
  val M = 3 // Machine mode (binary 11 in the 2-bit privilege field)

  def name(p: Int): String = p match {
    case U => "U"
    case S => "S"
    case M => "M"
    case _ => "?"
  }
}

object CSR {
  // ---- Supervisor-level CSRs (0x100 range) ----
  val SSTATUS    = 0x100
  val SIE        = 0x104
  val STVEC      = 0x105
  val SCOUNTEREN = 0x106
  val SSCRATCH   = 0x140
  val SEPC       = 0x141
  val SCAUSE     = 0x142
  val STVAL      = 0x143
  val SIP        = 0x144
  val SATP       = 0x180

  // ---- Machine-level CSRs (0x300 range) ----
  val MSTATUS    = 0x300
  val MISA       = 0x301
  val MEDELEG    = 0x302
  val MIDELEG    = 0x303
  val MIE        = 0x304
  val MTVEC      = 0x305
  val MCOUNTEREN = 0x306
  val MSCRATCH   = 0x340
  val MEPC       = 0x341
  val MCAUSE     = 0x342
  val MTVAL      = 0x343
  val MIP        = 0x344
}

object SstatusBits {
  val UIE  = 0  // User global interrupt enable
  val SIE  = 1  // Supervisor global interrupt enable
  val UPIE = 2  // Previous UIE
  val SPIE = 5  // Previous SIE (mstatus bit5; saved on trap entry, restored on SRET)
  val SPP  = 8  // Previous privilege mode (0=U, 1=S)
  val FS_LSB = 13 // [14:13] floating-point status (reserved)
  val XS_LSB = 15 // [16:15] extension status (reserved)
  val SUM  = 18 // Permit S-mode data accesses to U pages
  val MXR  = 19 // Make executable readable
  val SD   = 31 // Dirty (FS/XS summary, read-only here)
}

object MstatusBits {
  val UIE  = 0
  val SIE  = 1  // Machine-level SIE mirror position
  val MIE  = 3  // Machine global interrupt enable
  val UPIE = 4
  val SPIE = 5
  val MPIE = 7  // Previous MIE (saved on trap entry, restored on MRET)
  val SPP  = 8  // (machine view alias position; MPP is authoritative)
  val MPP_LSB = 11 // MPP is bits [12:11]
  val MPP  = 12
  val FS   = 13 // [14:13] floating-point status (reserved)
  val XS   = 15 // [16:15] extension status (reserved)
  val MPRV = 17 // Modify privilege for load/store effective privilege
  val SUM  = 18
  val MXR  = 19
  val TVM  = 20 // Trap virtual-memory ops in S/U
  val TW   = 21 // Timeout wait in S/U
  val TSR  = 22 // Trap SRET in U/S
  val SD   = 31
}

object SatpBits {
  // AN32-System-v1 satp layout: MODE[31] | ASID[30:22] | PPN[21:0]
  // MODE: 0 = Bare (no translation), 1 = Sv32
  val MODE   = 31
  val ASID_LSB = 22 // ASID is bits [30:22] (9 bits)
  val PPN     = 0  // PPN is bits [21:0] (22 bits)
  val MODE_SV32 = 1
}

object TrapCause {
  // Exceptions (Interrupt bit = 0)
  val INST_ADDR_MISALIGNED = 0
  val INST_ACCESS_FAULT    = 1
  val ILLEGAL_INSTRUCTION  = 2
  val BREAKPOINT           = 3
  val LOAD_ADDR_MISALIGNED = 4
  val LOAD_ACCESS_FAULT    = 5
  val STORE_ADDR_MISALIGNED= 6
  val STORE_ACCESS_FAULT   = 7
  val ECALL_U              = 8
  val ECALL_S              = 9
  val ECALL_M              = 11
  val INST_PAGE_FAULT      = 12
  val LOAD_PAGE_FAULT      = 13
  val STORE_PAGE_FAULT     = 15

  // Interrupts (Interrupt bit = 1, cause[31]=1)
  val INTERRUPT_BIT        = 0x80000000
  val SSIP                  = 1
  val MSIP                  = 3
  val STIP                  = 5
  val MTIP                  = 7
  val SEIP                  = 9
  val MEIP                  = 11

  def isInterrupt(cause: Int): Boolean = (cause & INTERRUPT_BIT) != 0
  def exceptionCode(cause: Int): Int   = cause & 0x7FFFFFFF
  def interruptCode(cause: Int): Int   = cause & 0x7FFFFFFF
}

object Misa {
  // MISA: XLEN=32 (bit 30), IM=1 (bit 12: I+A), M=1 (bit 12 is M? standard: M=bit12, A=bit0, I=bit8)
  // AN32: M (bit12), A (bit0), I (bit8), U (bit20), S (bit18)
  val M = 1 << 12
  val A = 1 << 0
  val I = 1 << 8
  val S = 1 << 18
  val U = 1 << 20
  val XLEN32 = 1 << 30

  // RV32IMSU (no A/atomics): M | I | S | U | XLEN32
  val RV32IMSU_VALUE: Int = M | I | S | U | XLEN32
}

object TimerMMIO {
  val MTIME_LO    = 0x80003000
  val MTIME_HI    = 0x80003004
  val MTIMECMP_LO = 0x80003008
  val MTIMECMP_HI = 0x8000300C
}
