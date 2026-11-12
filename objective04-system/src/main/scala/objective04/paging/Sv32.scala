package objective04.paging

/** Sv32 paging structures (O4-B).
  *
  *   VA[31:22] -> VPN1 (level-1 / root table index)
  *   VA[21:12] -> VPN0 (level-0 / leaf table index)
  *   VA[11:0]  -> page offset
  *
  * PTE (32-bit):
  *   V R W X U G A D  PPN[21:0]
  *   bit31=V 30=R 29=W 28=X 27=U 26=G 25=A 24=D  [23:0]=PPN
  *
  * Level-1 (root) PTEs point to level-0 tables (PPN = table frame) or, if W=0
  * at level 1, encode a 4MiB superpage. Level-0 leaf PTEs map 4KiB pages.
  */
object Sv32 {

  val PAGE_BITS   = 12
  val PAGE_SIZE   = 1 << PAGE_BITS          // 4096
  val PTE_BITS    = 32

  // VA field extraction (VA is a 32-bit word)
  def vpn1(va: Int): Int = (va >>> 22) & 0x3FF   // VA[31:22]
  def vpn0(va: Int): Int = (va >>> 12) & 0x3FF   // VA[21:12]
  def offset(va: Int): Int = va & (PAGE_SIZE - 1) // VA[11:0]

  // PTE bit positions
  val PTE_V = 31; val PTE_R = 30; val PTE_W = 29; val PTE_X = 28
  val PTE_U = 27; val PTE_G = 26; val PTE_A = 25; val PTE_D = 24
  val PTE_PPN_SHIFT = 0                       // PPN occupies [23:0]
  val PPN_BITS = 22                           // PA[31:12]

  // PTE accessors on a raw 32-bit entry
  def pteV(pte: Int)   = (pte >>> PTE_V)  & 1
  def pteR(pte: Int)   = (pte >>> PTE_R)  & 1
  def pteW(pte: Int)   = (pte >>> PTE_W)  & 1
  def pteX(pte: Int)   = (pte >>> PTE_X)  & 1
  def pteU(pte: Int)   = (pte >>> PTE_U)  & 1
  def pteG(pte: Int)   = (pte >>> PTE_G)  & 1
  def pteA(pte: Int)   = (pte >>> PTE_A)  & 1
  def pteD(pte: Int)   = (pte >>> PTE_D)  & 1
  def ptePpn(pte: Int) = pte & ((1 << PPN_BITS) - 1)

  // Build a leaf PTE mapping `ppn` (physical frame) with flags.
  def makePte(ppn: Int, v: Boolean = true, r: Boolean = true,
              w: Boolean = true, x: Boolean = false, u: Boolean = false,
              g: Boolean = false, a: Boolean = true, d: Boolean = true): Int = {
    var p = 0
    if (v) p |= 1 << PTE_V
    if (r) p |= 1 << PTE_R
    if (w) p |= 1 << PTE_W
    if (x) p |= 1 << PTE_X
    if (u) p |= 1 << PTE_U
    if (g) p |= 1 << PTE_G
    if (a) p |= 1 << PTE_A
    if (d) p |= 1 << PTE_D
    p | ((ppn & ((1 << PPN_BITS) - 1)) << PTE_PPN_SHIFT)
  }

  // Is a PTE a leaf (level-0 page) vs a pointer to a level-0 table?
  // Leaf when R or X set; pointer when neither (W must be 0 for pointer).
  def isLeaf(pte: Int): Boolean = pteR(pte) == 1 || pteX(pte) == 1

  // Malformed check: pointer PTE (non-leaf) must have W=0, R=0, X=0.
  def isMalformedPointer(pte: Int): Boolean =
    pteV(pte) == 1 && !isLeaf(pte) && (pteW(pte) == 1 || pteR(pte) == 1 || pteX(pte) == 1)

  // Translate VA -> PA given a resolved leaf PTE (caller walks tables).
  // PA = (PPN << PAGE_BITS) | offset
  def pteToPa(pte: Int, va: Int): Int =
    (ptePpn(pte) << PAGE_BITS) | offset(va)

  // Access permission check for a leaf PTE.
  // access: 0=read/fetch-inst? -> we distinguish: isExec, isWrite
  // Returns fault type or 0=ok. Simplified; U/S handled by caller.
  sealed trait Access
  case object Read  extends Access
  case object Write extends Access
  case object Exec  extends Access

  // Page-fault causes (RISC-V): inst=12, load=13, store=15, access=1
  val CAUSE_INST_PAGE_FAULT = 12
  val CAUSE_LOAD_PAGE_FAULT = 13
  val CAUSE_STORE_PAGE_FAULT = 15
  val CAUSE_ACCESS_FAULT    = 1

  // returns Some(cause) if fault else None
  def checkAccess(pte: Int, access: Access): Option[Int] = {
    if (pteV(pte) == 0) return Some(access match {
      case Exec => CAUSE_INST_PAGE_FAULT
      case Read => CAUSE_LOAD_PAGE_FAULT
      case Write => CAUSE_STORE_PAGE_FAULT
    })
    access match {
      case Read  => if (pteR(pte) == 0) Some(CAUSE_LOAD_PAGE_FAULT) else None
      case Write => if (pteW(pte) == 0 || pteR(pte) == 0) Some(CAUSE_STORE_PAGE_FAULT) else None
      case Exec  => if (pteX(pte) == 0) Some(CAUSE_INST_PAGE_FAULT) else None
    }
  }
}
