package objective04.loader

import objective04.addressspace.AddressSpace
import objective04.paging.Sv32

/** Process loader (O5-I).
  *
  *   AN32EXE -> parse phdrs -> create AddressSpace -> alloc frames ->
  *   map text/data -> copy file bytes -> zero BSS -> build user stack ->
  *   entry PC + initial SP + TrapFrame/PCB seed
  *
  * `mem` is the physical-memory image (word-indexed, little-endian) shared
  * with AddressSpace. `allocFrame` supplies PFNs (from FrameAllocator).
  * Produces a LoadedProcess the O3 kernel turns into a READY PCB.
  */
object Loader {

  case class LoadedProcess(
    as: AddressSpace,
    entryPc: Int,      // executable entry VA
    initSp: Int,       // initial user stack pointer (top of stack)
    stackTop: Int,     // VA of stack region top
    stackSize: Int,
    asid: Int
  )

  // ---- physical-memory byte write (word-indexed, little-endian) ----
  private def wrByte(mem: Array[Int], pa: Int, b: Int): Unit = {
    val wi = pa >> 2; val bi = (pa & 3) * 8
    mem(wi) = (mem(wi) & ~(0xff << bi)) | ((b & 0xff) << bi)
  }
  private def wrBytes(mem: Array[Int], pa: Int, data: Array[Byte],
                      off: Int, len: Int): Unit = {
    var i = 0
    while (i < len) { wrByte(mem, pa + i, data(off + i)); i += 1 }
  }

  /** Map perms from segment flags -> AddressSpace.map args. */
  private def perms(flags: Int): (Boolean, Boolean, Boolean, Boolean) = {
    val x = (flags & An32Exe.PF_X) != 0
    val w = (flags & An32Exe.PF_W) != 0
    val r = (flags & An32Exe.PF_R) != 0
    (true, r || x || w, w, x)   // (v, r, w, x)
  }

  /** Load an executable into a fresh address space. */
  def load(exe: An32Exe.Exe,
           mem: Array[Int],
           allocFrame: () => Option[Int],
           asid: Int = 1,
           stackBase: Int = 0x30000000,
           stackSize: Int = 0x4000,   // 16 KiB user stack
           argv: Vector[String] = Vector.empty): LoadedProcess = {

    val as = new AddressSpace(mem, allocFrame, asid)
    val pageMask = ~(Sv32.PAGE_SIZE - 1)

    // ---- map each PT_LOAD segment, page by page ----
    for (ph <- exe.phdrs if ph.`type` == An32Exe.PT_LOAD) {
      val segStart = ph.vaddr
      val segEnd   = ph.vaddr + math.max(ph.memsz, ph.filesz)
      var va = segStart & pageMask
      val (pv, pr, pw, px) = perms(ph.flags)
      while (va < segEnd) {
        // allocate a frame for this page
        val ppn = allocFrame().getOrElse(
          throw new IllegalStateException(s"Loader: out of frames mapping VA 0x${va.toHexString}"))
        val pa = ppn << Sv32.PAGE_BITS
        // copy file bytes that fall in this page
        val fileLo = math.max(segStart, va)
        val fileHi = math.min(segStart + ph.filesz, va + Sv32.PAGE_SIZE)
        if (fileHi > fileLo) {
          val foff = ph.offset + (fileLo - segStart)
          val len = fileHi - fileLo
          wrBytes(mem, pa + (fileLo - va), exe.data, foff, len)
        }
        // BSS tail (memsz>filesz) already zero if frame was zeroed; ensure:
        val bssLo = math.max(segStart + ph.filesz, va)
        val bssHi = math.min(segStart + ph.memsz, va + Sv32.PAGE_SIZE)
        if (bssHi > bssLo) {
          var z = bssLo - va
          while (z < bssHi - va && z < Sv32.PAGE_SIZE) { wrByte(mem, pa + z, 0); z += 1 }
        }
        as.map(va, ppn, v = pv, r = pr, w = pw, x = px, u = true, g = false)
        va += Sv32.PAGE_SIZE
      }
    }

    // ---- build user stack (top-down) ----
    // map the whole stack region RW user first so we can write through VA->PA
    val send = stackBase + stackSize
    var sva = stackBase & pageMask
    while (sva < send) {
      val ppn = allocFrame().getOrElse(
        throw new IllegalStateException("Loader: out of frames for stack"))
      as.map(sva, ppn, v = true, r = true, w = true, x = false, u = true, g = false)
      sva += Sv32.PAGE_SIZE
    }

    // VA->PA write helpers (stack pages now mapped)
    def vaw(va: Int): Int = as.translate(va).getOrElse(
      throw new IllegalStateException(s"Loader: stack VA 0x${va.toHexString} unmapped"))
    def wrVByte(va: Int, b: Int): Unit = wrByte(mem, vaw(va), b)
    def wrVBytes(va: Int, data: Array[Byte], off: Int, len: Int): Unit = {
      var i = 0; while (i < len) { wrVByte(va + i, data(off + i)); i += 1 }
    }
    def wrVWord(va: Int, w: Int): Unit = {
      wrVByte(va, w & 0xff); wrVByte(va+1, (w>>>8)&0xff)
      wrVByte(va+2, (w>>>16)&0xff); wrVByte(va+3, (w>>>24)&0xff)
    }

    // initial SP: argc + argv ptrs + NULL, then strings below (16B aligned)
    val nArgs = argv.size
    val ptrWords = 1 + nArgs + 1
    var sp = send - ptrWords * 4
    sp &= ~15
    // place argv strings below the pointer block
    var cur = sp - 16
    val locs = argv.map { s =>
      val b = s.getBytes("UTF-8")
      cur -= (b.length + 1); cur &= ~3
      wrVBytes(cur, b, 0, b.length); wrVByte(cur + b.length, 0)
      (cur, b.length + 1)
    }
    wrVWord(sp, nArgs)
    var i = 0
    while (i < nArgs) { wrVWord(sp + 4 + i*4, locs(i)._1); i += 1 }
    wrVWord(sp + 4 + nArgs*4, 0)   // NULL terminator

    LoadedProcess(as, exe.entry, sp, send, stackSize, asid)
  }
}
