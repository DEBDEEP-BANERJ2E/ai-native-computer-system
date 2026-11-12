package objective04.mmu

import chisel3._
import chisel3.util._

/** Hardware page-table walker + translation interface (O4-C).
  *
  * Walks the two-level Sv32 hierarchy for a VA and returns either a PA or a
  * fault. Interfaces a PTE read port (physical memory) and exposes the same
  * fault causes the O3 trap path expects (inst=12/load=13/store=15/access=1).
  *
  *   CPU VA + access + priv
  *        -> TLB (O4-D, later) -> on miss -> PageTableWalker (this)
  *        -> level-1 PTE -> level-0 PTE -> PA  |  fault
  *
  * Sv32 PTE: V R W X U G A D at [31:24], PPN at [21:0]; PA = PPN<<12 | off.
  * Our physical memory is small so the high PPN bits are always zero; PA is
  * carried on a 32-bit bus.
  */
class PageTableWalker extends Module {

  val io = IO(new Bundle {
    val req        = Input(Bool())
    val va         = Input(UInt(32.W))
    val access     = Input(UInt(2.W))   // 0=read 1=write 2=exec
    val privU      = Input(Bool())      // requesting privilege is U
    val satpPpn    = Input(UInt(22.W))  // root page-table frame (satp.PPN)
    // PTE memory read port (physical, word addressed)
    val memReq     = Output(Bool())
    val memAddr    = Output(UInt(32.W))
    val memReady   = Input(Bool())
    val memData    = Input(UInt(32.W))
    // response
    val busy       = Output(Bool())
    val fault      = Output(Bool())
    val faultCause = Output(UInt(6.W))
    val pa         = Output(UInt(32.W))
  })

  // VA fields
  val vpn1 = io.va(31, 22)              // 10 bits
  val vpn0 = io.va(21, 12)              // 10 bits
  val vaOff = io.va & 4095.U            // 32-bit masked page offset

  // PTE field helpers on a 32-bit entry
  def pteV(e: UInt)   = e(31)
  def pteR(e: UInt)   = e(30)
  def pteW(e: UInt)   = e(29)
  def pteX(e: UInt)   = e(28)
  def pteU(e: UInt)   = e(27)
  def ptePpn(e: UInt) = e(21, 0)        // 22-bit PPN -> PA[33:12]

  def isLeaf(e: UInt) = pteR(e) || pteX(e)

  // PTE address = (ppn << 12) | (vpn << 2), truncated to 32-bit PA bus.
  def pteAddr(ppn: UInt, vpn: UInt): UInt =
    (((ppn << 12.U) | (vpn << 2.U))(31, 0))

  // leaf PA = (ppn << 12) | vaOff, 32-bit
  def leafPa(ppn: UInt): UInt =
    ((ppn << 12.U)(31, 0)) | vaOff

  // fault cause for access type (inst=12 load=13 store=15)
  def causeFor(access: UInt): UInt =
    Mux(access === 2.U, 12.U(6.W), Mux(access === 1.U, 15.U(6.W), 13.U(6.W)))

  // leaf permission/fault -> (fault, cause)
  def leafFault(e: UInt, access: UInt, privU: Bool): (Bool, UInt) = {
    val notV = !pteV(e)
    val privFault = privU && !pteU(e)
    val accFault = Mux(access === 0.U, !pteR(e),
                  Mux(access === 1.U, !(pteW(e) && pteR(e)),
                                    !pteX(e)))
    (notV || privFault || accFault, causeFor(access))
  }

  object State extends ChiselEnum {
    val IDLE, L1_READ, L1_CHECK, L0_READ, L0_CHECK, DONE = Value
  }
  val state = RegInit(State.IDLE)

  val l1Pte = Reg(UInt(32.W))
  val l0Pte = Reg(UInt(32.W))
  val curVa = Reg(UInt(32.W))
  val curAccess = Reg(UInt(2.W))
  val curPrivU = Reg(Bool())
  val resultPa = Reg(UInt(32.W))
  val resultFault = Reg(Bool())
  val resultCause = Reg(UInt(6.W))

  // combinational next addresses
  val l1Addr = pteAddr(io.satpPpn, vpn1)
  val l0Addr = pteAddr(ptePpn(l1Pte), vpn0)

  io.busy := state =/= State.IDLE
  io.memReq := state === State.L1_READ || state === State.L0_READ
  io.memAddr := Mux(state === State.L1_READ, l1Addr, l0Addr)

  switch(state) {
    is(State.IDLE) {
      when(io.req) {
        curVa := io.va; curAccess := io.access; curPrivU := io.privU
        state := State.L1_READ
      }
    }
    is(State.L1_READ) {
      when(io.memReady) { l1Pte := io.memData; state := State.L1_CHECK }
    }
    is(State.L1_CHECK) {
      val l1v = pteV(l1Pte)
      val l1leaf = isLeaf(l1Pte)
      when(!l1v) {
        resultFault := true.B; resultCause := causeFor(curAccess); state := State.DONE
      }.elsewhen(l1leaf) {
        // 4MiB superpage leaf at level 1: superpage encoding deferred (our
        // tables emit only 4KiB leaves) -> conservative page fault.
        resultFault := true.B; resultCause := causeFor(curAccess); state := State.DONE
      }.otherwise {
        state := State.L0_READ
      }
    }
    is(State.L0_READ) {
      when(io.memReady) { l0Pte := io.memData; state := State.L0_CHECK }
    }
    is(State.L0_CHECK) {
      val (f, c) = leafFault(l0Pte, curAccess, curPrivU)
      resultFault := f
      resultCause := c
      resultPa := leafPa(ptePpn(l0Pte))
      state := State.DONE
    }
    is(State.DONE) {
      io.pa := resultPa
      io.fault := resultFault
      io.faultCause := resultCause
      state := State.IDLE
    }
  }
}
