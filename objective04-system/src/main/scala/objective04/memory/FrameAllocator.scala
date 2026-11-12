package objective04.memory

import chisel3._
import chisel3.util._

/** Physical frame allocator (O4-A).
  *
  * Manages physical memory as a bitmap of frames. PFN-based alloc/free with
  * reserved-region exclusion. The OS page tables / MMU consume the PFNs.
  *
  *   PA = PFN * FRAME_SIZE + offset     (offset < FRAME_SIZE)
  *   VA != PA != file offset != storage block   (kept distinct by callers)
  */
class FrameAllocator(val frameBytes: Int = 4096,
                     val memBytes: Int = 0x10000) extends Module {

  require(memBytes % frameBytes == 0, "memBytes must be a multiple of frameBytes")
  val numFrames = memBytes / frameBytes
  val pfnWidth = log2Up(numFrames)

  val io = IO(new Bundle {
    val alloc    = Input(Bool())
    val allocPfn = Output(UInt(pfnWidth.W))
    val allocOk  = Output(Bool())
    val free     = Input(Bool())
    val freePfn  = Input(UInt(pfnWidth.W))
    val reserve  = Input(Bool())
    val resPfn   = Input(UInt(pfnWidth.W))
    val numFree  = Output(UInt(log2Up(numFrames + 1).W))
  })

  // bitmap bit i = frame i is FREE. Vec index -> bit i via .asUInt.
  val bitmap = RegInit(VecInit(Seq.fill(numFrames)(true.B)))
  val bitmapU = bitmap.asUInt                       // bit i = frame i free
  val freeCount = RegInit(numFrames.U(log2Up(numFrames + 1).W))

  // lowest set bit -> one-hot -> index = first free PFN
  val lowBit = bitmapU & (~bitmapU + 1.U)           // isolate lowest set bit
  val firstFree = OHToUInt(lowBit)                  // index of lowest free frame
  val hasFree = bitmapU.orR

  // alloc: single-cycle, take lowest free frame
  val doAlloc = io.alloc && hasFree
  val allocIdx = Mux(io.freePfn === firstFree, firstFree, firstFree) // = firstFree
  when(doAlloc) {
    bitmap(firstFree) := false.B
  }
  when(io.free) {
    bitmap(io.freePfn) := true.B
  }
  when(io.reserve) {
    bitmap(io.resPfn) := false.B
  }

  io.allocPfn := firstFree
  io.allocOk  := doAlloc
  io.numFree  := freeCount

  // maintain freeCount via deltas on the pre-update bitmap view
  val allocHit = doAlloc
  val freeHit  = io.free && !bitmap(io.freePfn)      // was used -> now free
  val resHit   = io.reserve && bitmap(io.resPfn)     // was free -> now used
  when(allocHit || resHit) { freeCount := freeCount - 1.U }
  .elsewhen(freeHit)          { freeCount := freeCount + 1.U }
}

/** PFN <-> physical address helpers. */
object PhysMem {
  def pfnToAddr(pfn: UInt, frameBytes: Int): UInt =
    pfn << log2Up(frameBytes)
  def addrToPfn(addr: UInt, frameBytes: Int): UInt =
    addr >> log2Up(frameBytes)
  def offsetInFrame(addr: UInt, frameBytes: Int): UInt =
    addr & (frameBytes - 1).U
}
