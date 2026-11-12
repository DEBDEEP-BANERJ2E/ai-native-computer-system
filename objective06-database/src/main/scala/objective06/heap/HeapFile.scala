package objective06.heap

import objective06.buffer.{BufferPool, PageStore}
import objective06.page.SlottedPage
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** HeapFile: append/scan record storage over slotted pages (O6).
  *
  * Layout within the page store:
  *   page `base`   = header page (raw bytes): magic, pageCount, nextFreeSlot hint
  *   pages base+1.. = SlottedPage record pages
  *
  * A RecordHandle = (pageId, slotIndex). Records are opaque byte arrays;
  * the executor encodes typed tuples into them.
  */
class HeapFile(pool: BufferPool, val base: Int, pageSize: Int = 512) {

  // header page layout (raw, not slotted)
  private val H_MAGIC = 0;   private val H_COUNT = 4;  private val H_TAIL  = 8
  private val HEADER = 16

  private def hdrBytes: Array[Byte] = pool.get(base)
  private def w32(b:Array[Byte],o:Int,v:Int):Unit =
    ByteBuffer.wrap(b,o,4).order(ByteOrder.LITTLE_ENDIAN).putInt(v)
  private def r32(b:Array[Byte],o:Int):Int =
    ByteBuffer.wrap(b,o,4).order(ByteOrder.LITTLE_ENDIAN).getInt()

  private def pageCount:Int = r32(hdrBytes, H_COUNT)
  private def tailPage:Int = if(pageCount==0) base else base + pageCount - 1

  /** Create header if absent. */
  def init(): Unit = {
    val b = pool.get(base)
    if (r32(b,H_MAGIC) != 0x48454150) { // 'HEAP'
      java.util.Arrays.fill(b,0.toByte)
      w32(b,H_MAGIC,0x48454150); w32(b,H_COUNT,0); w32(b,H_TAIL,0)
      pool.setDirty(base)
    }
  }

  private def growPage(): Int = {
    val pid = pool.alloc()
    // zero + it's a fresh slotted page
    val b = pool.get(pid); java.util.Arrays.fill(b,0.toByte)
    new SlottedPage(pid,b).init()
    pool.setDirty(pid)
    val hb = hdrBytes; w32(hb,H_COUNT,pageCount+1); pool.setDirty(base)
    pid
  }

  /** Insert record -> handle (pageId,slot). Append-mostly: try tail page,
    * grow a fresh page when full. */
  def insert(rec: Array[Byte]): Option[(Int,Int)] = {
    init()
    var pid = tailPage
    // ensure tail page exists and is a valid slotted page
    if (pageCount == 0) pid = growPage()
    val b = pool.get(pid)
    val pg = new SlottedPage(pid, b)
    if (pg.numSlots == 0 && b.count(_ != 0.toByte) <= HEADER) pg.init()
    var slot = pg.insert(rec)
    if (slot < 0) {
      pid = growPage()
      val b2 = pool.get(pid)
      val pg2 = new SlottedPage(pid, b2); pg2.init()
      slot = pg2.insert(rec)
      if (slot < 0) return None
      pool.setDirty(pid)
      return Some((pid, slot))
    }
    pool.setDirty(pid)
    Some((pid, slot))
  }

  /** Fetch record by handle. */
  def get(h:(Int,Int)): Option[Array[Byte]] = {
    val b = pool.get(h._1); new SlottedPage(h._1,b).get(h._2)
  }

  /** Scan all records -> Seq[(handle, bytes)]. */
  def scan(): Seq[((Int,Int),Array[Byte])] = {
    init()
    val out = scala.collection.mutable.ArrayBuffer[((Int,Int),Array[Byte])]()
    var p = base+1
    var i = 0
    while (i < pageCount) {
      val b = pool.get(p)
      val pg = new SlottedPage(p,b)
      pg.foreach { (s, bytes) => out += ((p, s) -> bytes) }
      p += 1; i += 1
    }
    out.toSeq
  }

  def numRecords: Int = scan().length
}
