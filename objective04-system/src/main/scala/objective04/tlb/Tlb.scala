package objective04.tlb

import chisel3._
import chisel3.util._

/** Translation lookaside buffer (O4-D).
  *
  * Fully-associative TLB. Each entry caches a leaf translation:
  *   vpn (VA[31:12]) -> ppn (PA[33:12]) with permission bits, ASID, valid,
  *   global, level. Lookup is ASID-aware: an entry matches when VPN matches
  *   and (entry.global || entry.asid == current ASID) and entry.valid.
  *
  *   CPU VA -> TLB lookup -> hit: PA+perms | miss -> PageTableWalker -> refill
  *   SFENCE.VMA -> selective invalidate (by ASID and/or VPN)
  */
class Tlb(val nWays: Int = 8, val asidBits: Int = 16) extends Module {

  val io = IO(new Bundle {
    // lookup
    val lookup   = Input(Bool())
    val vpn      = Input(UInt(20.W))       // VA[31:12]
    val asid     = Input(UInt(asidBits.W)) // current address-space id
    val hit      = Output(Bool())
    val ppn      = Output(UInt(22.W))
    val permV    = Output(Bool()); val permR = Output(Bool())
    val permW    = Output(Bool()); val permX = Output(Bool())
    val permU    = Output(Bool()); val entryGlobal = Output(Bool())
    // refill (from PTW on miss)
    val refill   = Input(Bool())
    val rVpn     = Input(UInt(20.W))
    val rPpn     = Input(UInt(22.W))
    val rAsid    = Input(UInt(asidBits.W))
    val rPermV   = Input(Bool()); val rPermR = Input(Bool())
    val rPermW   = Input(Bool()); val rPermX = Input(Bool())
    val rPermU   = Input(Bool()); val rGlobal = Input(Bool())
    val rValid   = Input(Bool())
    // invalidate (SFENCE.VMA): invAsid!=0 -> flush that ASID; invVpn!=0 ->
    // flush that VPN across all; both zero -> flush all non-global
    val inv      = Input(Bool())
    val invAsid  = Input(UInt(asidBits.W))
    val invVpn   = Input(UInt(20.W))
    val invAll   = Input(Bool())
    // debug
    val usedWays = Output(UInt(log2Up(nWays + 1).W))
  })

  // per-way storage
  val eVpn = Reg(Vec(nWays, UInt(20.W)))
  val ePpn = Reg(Vec(nWays, UInt(22.W)))
  val eAsid = Reg(Vec(nWays, UInt(asidBits.W)))
  val eV = Reg(Vec(nWays, Bool())); val eR = Reg(Vec(nWays, Bool()))
  val eW = Reg(Vec(nWays, Bool())); val eX = Reg(Vec(nWays, Bool()))
  val eU = Reg(Vec(nWays, Bool())); val eG = Reg(Vec(nWays, Bool()))

  // match vector: valid && vpn match && (global || asid match)
  val matchOH = Wire(Vec(nWays, Bool()))
  for (w <- 0 until nWays) {
    matchOH(w) := eV(w) && (eVpn(w) === io.vpn) &&
                 (eG(w) || (eAsid(w) === io.asid))
  }
  val hitIdx = OHToUInt(matchOH.asUInt)
  val anyHit = matchOH.asUInt.orR

  // outputs = lowest-priority (first) matching way
  io.hit := anyHit
  io.ppn := ePpn(hitIdx)
  io.permV := eV(hitIdx); io.permR := eR(hitIdx)
  io.permW := eW(hitIdx); io.permX := eX(hitIdx)
  io.permU := eU(hitIdx); io.entryGlobal := eG(hitIdx)

  // refill: replace the lowest-index INVALID way, else way 0 (simple RR later)
  val invalidOH = Wire(Vec(nWays, Bool()))
  for (w <- 0 until nWays) invalidOH(w) := !eV(w)
  val freeIdx = OHToUInt(invalidOH.asUInt)
  val hasFree = invalidOH.asUInt.orR
  val replIdx = Mux(hasFree, freeIdx, 0.U(log2Up(nWays).W))

  when(io.refill && io.rValid) {
    eVpn(replIdx) := io.rVpn
    ePpn(replIdx) := io.rPpn
    eAsid(replIdx) := io.rAsid
    eV(replIdx) := true.B; eR(replIdx) := io.rPermR
    eW(replIdx) := io.rPermW; eX(replIdx) := io.rPermX
    eU(replIdx) := io.rPermU; eG(replIdx) := io.rGlobal
  }

  // selective invalidation
  for (w <- 0 until nWays) {
    val matchAsid = eAsid(w) === io.invAsid
    val matchVpn  = eVpn(w) === io.invVpn
    val kill = io.inv && (
      io.invAll ||
      (io.invAsid =/= 0.U && matchAsid && (io.invVpn === 0.U || matchVpn)) ||
      (io.invVpn =/= 0.U && io.invAsid === 0.U && matchVpn && !eG(w))
    )
    when(kill) { eV(w) := false.B }
  }

  // used ways count (for debug)
  val usedOH = Wire(Vec(nWays, UInt(1.W)))
  for (w <- 0 until nWays) usedOH(w) := eV(w).asUInt
  io.usedWays := PopCount(usedOH.asUInt)
}
