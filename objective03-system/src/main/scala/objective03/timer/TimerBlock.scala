package objective03.timer

import chisel3._
import chisel3.util._
import objective02.decode.MemWidth

// =========================================================================
// TimerBlock — AN32-System-v1 64-bit MTIME / MTIMECMP subsystem.
//
// MMIO window at 0x80003000..0x8000300F (see spec/system-v1-requirements.md §5):
//   MTIME_LO    0x80003000  RO  lower 32 bits of free-running counter
//   MTIME_HI    0x80003004  RO  upper 32 bits
//   MTIMECMP_LO 0x80003008  RW  lower 32 bits of comparator
//   MTIMECMP_HI 0x8000300C  RW  upper 32 bits of comparator
//
// Atomic write protocol is software-side (write LO=0xFFFFFFFF first); the
// block simply exposes word-wide RW regs and compares the full 64-bit
// values each cycle to drive mip.MTIP.
// =========================================================================

class TimerBlockIO extends Bundle {
  // Bus interface (mirrors SystemMMIO port conventions)
  val address       = Input(UInt(32.W))
  val memReadReq    = Input(Bool())
  val memWriteReq   = Input(Bool())
  val writeData     = Input(UInt(32.W))
  val memWidth      = Input(UInt(3.W))
  val readData      = Output(UInt(32.W))
  val windowHit     = Output(Bool())
  val readAccepted  = Output(Bool())
  val writeAccepted = Output(Bool())

  // Interrupt output: mtime >= mtimecmp asserts MTIP only. STIP is set by
  // the M-mode timer handler (M-mediated path; no Sstc in System-v1).
  val mtip = Output(Bool())
}

class TimerBlock extends Module {
  val io = IO(new TimerBlockIO)

  // ---- 64-bit free-running counter ----
  val mtime = RegInit(0.U(64.W))
  mtime := mtime + 1.U

  // ---- 64-bit comparator ----
  val mtimecmpLo = RegInit(0.U(32.W))
  val mtimecmpHi = RegInit(0.U(32.W))
  val mtimecmp   = Cat(mtimecmpHi, mtimecmpLo)

  // mtime >= mtimecmp -> pending (64-bit unsigned compare)
  val timeUp = mtime >= mtimecmp
  io.mtip := timeUp

  // ---- Window decode: address[31:16] == 0x8000 and low16 in 0x3000..0x300F ----
  val inWindow = (io.address(31, 16) === "h8000".U) &&
                 (io.address(15, 4) === "h300".U) // 0x80003000-0x800030FF word aligned region
  io.windowHit := inWindow

  val isAlignedWord = (io.address(1, 0) === 0.U) && (io.memWidth === MemWidth.WORD)

  val rd = WireDefault(0.U(32.W))
  val rdOK = WireDefault(false.B)
  val wrOK = WireDefault(false.B)

  when(inWindow && isAlignedWord) {
    when(io.memReadReq) {
      switch(io.address(3, 2)) {
        is(0.U) { rd := mtime(31, 0);  rdOK := true.B } // MTIME_LO
        is(1.U) { rd := mtime(63, 32); rdOK := true.B } // MTIME_HI
        is(2.U) { rd := mtimecmpLo;   rdOK := true.B } // MTIMECMP_LO
        is(3.U) { rd := mtimecmpHi;   rdOK := true.B } // MTIMECMP_HI
      }
    }
    when(io.memWriteReq) {
      switch(io.address(3, 2)) {
        is(2.U) { mtimecmpLo := io.writeData; wrOK := true.B }
        is(3.U) { mtimecmpHi := io.writeData; wrOK := true.B }
        // MTIME_* are read-only: writes ignored (not accepted)
      }
    }
  }

  io.readData      := rd
  io.readAccepted  := rdOK
  io.writeAccepted := wrOK
}
