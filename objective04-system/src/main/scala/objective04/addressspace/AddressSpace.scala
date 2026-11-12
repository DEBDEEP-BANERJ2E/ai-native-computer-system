package objective04.addressspace

import objective04.paging.Sv32

/** Generic virtual address space (O4-G).
  *
  * Manages an Sv32 two-level page table over a physical-memory image.
  * `mem` is a word-addressed physical memory: mem(pa >> 2) holds the 32-bit
  * word at `pa`. Page tables and leaf PTEs live in `mem`; frames are obtained
  * from `allocFrame` (PFN) supplied by the physical frame allocator.
  *
  *   AddressSpace
  *    ├── rootPpn   (level-1 table frame)
  *    ├── asid
  *    ├── map(va, ppn, perms)
  *    ├── unmap(va)
  *    ├── protect(va, perms)
  *    ├── translate(va) -> PA | fault
  *    └── destroy()
  */
class AddressSpace(val mem: Array[Int],
                   allocFrame: () => Option[Int],
                   val asid: Int = 1) {

  // root level-1 page table frame
  var rootPpn: Int = _
  rootPpn = allocFrame().getOrElse(
    throw new IllegalStateException("AddressSpace: no frame for root PT"))

  // helpers: PTE access in physical memory (word index = pa>>2)
  private def rdPte(pa: Int): Int = mem(pa >> 2)
  private def wrPte(pa: Int, v: Int): Unit = { mem(pa >> 2) = v }

  // level-1 table base PA and entry address for a VA
  private def l1TableBase: Int = rootPpn << Sv32.PAGE_BITS
  private def l1EntryAddr(va: Int): Int = l1TableBase + (Sv32.vpn1(va) << 2)

  // level-0 table base PA (from a level-1 pointer PTE) + entry addr
  private def l0TableBase(pte1: Int): Int = Sv32.ptePpn(pte1) << Sv32.PAGE_BITS
  private def l0EntryAddr(pte1: Int, va: Int): Int =
    l0TableBase(pte1) + (Sv32.vpn0(va) << 2)

  // ensure a level-0 table exists for va; returns level-1 PTE (pointer)
  private def ensureL0(va: Int): Int = {
    val a1 = l1EntryAddr(va)
    var p1 = rdPte(a1)
    if (Sv32.pteV(p1) == 0) {
      // allocate a level-0 table frame, link it
      val p0 = allocFrame().getOrElse(
        throw new IllegalStateException("AddressSpace: no frame for L0 PT"))
      // clear the new table (4KiB = 1024 words)
      val base = p0 << Sv32.PAGE_BITS
      var i = 0
      while (i < 1024) { mem((base >> 2) + i) = 0; i += 1 }
      // level-1 pointer PTE: V=1, pointer (R=W=X=0), PPN=table frame
      p1 = Sv32.makePte(p0, v = true, r = false, w = false, x = false,
                        a = true, d = true)
      wrPte(a1, p1)
    }
    p1
  }

  /** Map va -> physical page `ppn` with permission flags. */
  def map(va: Int, ppn: Int, v: Boolean = true, r: Boolean = true,
          w: Boolean = true, x: Boolean = false, u: Boolean = true,
          g: Boolean = false): Unit = {
    val p1 = ensureL0(va)
    val a0 = l0EntryAddr(p1, va)
    wrPte(a0, Sv32.makePte(ppn, v, r, w, x, u, g))
  }

  /** Unmap va (clear leaf PTE). */
  def unmap(va: Int): Unit = {
    val a1 = l1EntryAddr(va)
    val p1 = rdPte(a1)
    if (Sv32.pteV(p1) == 1 && !Sv32.isLeaf(p1)) {
      wrPte(l0EntryAddr(p1, va), 0)
    }
  }

  /** Change permissions on an existing mapping. */
  def protect(va: Int, v: Boolean, r: Boolean, w: Boolean, x: Boolean,
              u: Boolean, g: Boolean): Unit = {
    val a1 = l1EntryAddr(va)
    val p1 = rdPte(a1)
    if (Sv32.pteV(p1) == 1 && !Sv32.isLeaf(p1)) {
      val a0 = l0EntryAddr(p1, va)
      val old = rdPte(a0)
      if (Sv32.pteV(old) == 1) {
        wrPte(a0, Sv32.makePte(Sv32.ptePpn(old), v, r, w, x, u, g))
      }
    }
  }

  /** Walk the tables for va; returns Some(pa) or None on fault. */
  def translate(va: Int): Option[Int] = {
    val a1 = l1EntryAddr(va)
    val p1 = rdPte(a1)
    if (Sv32.pteV(p1) == 0) return None
    if (Sv32.isLeaf(p1)) {
      // superpage (not emitted by map()); treat as unsupported -> None
      return None
    }
    val a0 = l0EntryAddr(p1, va)
    val p0 = rdPte(a0)
    if (Sv32.pteV(p0) == 0 || !Sv32.isLeaf(p0)) return None
    Some(Sv32.pteToPa(p0, va))
  }

  /** Release the address space (best-effort; frames returned to allocator
    *  by the caller via frame bookkeeping). */
  def destroy(): Unit = {
    // walk level-1, free level-0 tables and note leaf frames for caller
    var vpn1 = 0
    while (vpn1 < 1024) {
      val p1 = rdPte(l1TableBase + (vpn1 << 2))
      if (Sv32.pteV(p1) == 1 && !Sv32.isLeaf(p1)) {
        val tbase = l0TableBase(p1)
        var vpn0 = 0
        while (vpn0 < 1024) {
          val p0 = rdPte(tbase + (vpn0 << 2))
          if (Sv32.pteV(p0) == 1) wrPte(tbase + (vpn0 << 2), 0)
          vpn0 += 1
        }
        wrPte(l1TableBase + (vpn1 << 2), 0)
      }
      vpn1 += 1
    }
  }
}
