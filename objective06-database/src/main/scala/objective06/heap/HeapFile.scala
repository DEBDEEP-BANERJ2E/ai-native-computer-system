package objective06.heap

import objective06.buffer.BufferPool
import objective06.page.SlottedPage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import scala.collection.mutable

/** HeapFile: append/scan record storage over slotted pages (O6).
  *
  * Page `base` is a header page (raw bytes) holding a directory of record
  * page pids, so record pages need not be contiguous in the store:
  *   [magic u32][numPages u32][pid0 u32][pid1 u32]...
  * Record pages base+? are SlottedPage instances allocated via pool.alloc().
  *
  * RecordHandle = (pageId, slotIndex); records are opaque byte arrays that
  * the executor encodes/decodes per table schema.
  */
class HeapFile(pool: BufferPool, val base: Int, pageSize: Int = 512) {

  private val H_MAGIC = 0; private val H_COUNT = 4
  private val MAX_PAGES = (pageSize - 8) / 4   // pid directory capacity

  private def w32(b:Array[Byte],o:Int,v:Int):Unit =
    ByteBuffer.wrap(b,o,4).order(ByteOrder.LITTLE_ENDIAN).putInt(v)
  private def r32(b:Array[Byte],o:Int):Int =
    ByteBuffer.wrap(b,o,4).order(ByteOrder.LITTLE_ENDIAN).getInt()

  private def hdr: Array[Byte] = pool.get(base)
  private def numPages:Int = r32(hdr,H_COUNT)
  private def pagePid(i:Int):Int = r32(hdr, 8 + i*4)

  /** Initialise header if absent. */
  def init(): Unit = {
    val b = pool.get(base)
    if (r32(b,H_MAGIC) != 0x48454150) { // 'HEAP'
      java.util.Arrays.fill(b,0.toByte)
      w32(b,H_MAGIC,0x48454150); w32(b,H_COUNT,0)
      pool.setDirty(base)
    }
  }

  private def allocRecordPage(): Int = {
    val pid = pool.alloc()
    val b = pool.get(pid); java.util.Arrays.fill(b,0.toByte)
    new SlottedPage(pid,b).init(); pool.setDirty(pid)
    // register pid in header directory
    val hb = hdr; val n = numPages
    w32(hb, 8 + n*4, pid); w32(hb, H_COUNT, n+1); pool.setDirty(base)
    pid
  }

  /** Insert record -> (pageId,slot). Append-mostly. */
  def insert(rec: Array[Byte]): Option[(Int,Int)] = {
    init()
    var pid = if (numPages>0) pagePid(numPages-1) else allocRecordPage()
    val b = pool.get(pid)
    val pg = new SlottedPage(pid,b)
    var slot = pg.insert(rec)
    if (slot < 0) {
      pid = allocRecordPage()
      val b2 = pool.get(pid); val pg2 = new SlottedPage(pid,b2)
      slot = pg2.insert(rec)
      if (slot < 0) return None
    }
    pool.setDirty(pid)
    Some((pid, slot))
  }

  def get(h:(Int,Int)): Option[Array[Byte]] = {
    val b = pool.get(h._1); new SlottedPage(h._1,b).get(h._2)
  }

  /** Scan all records -> Seq[((pageId,slot), bytes)]. */
  def scan(): Seq[((Int,Int),Array[Byte])] = {
    init()
    val out = mutable.ArrayBuffer[((Int,Int),Array[Byte])]()
    var i = 0
    while (i < numPages) {
      val p = pagePid(i); val b = pool.get(p)
      new SlottedPage(p,b).foreach { (s, bytes) => out += ((p,s) -> bytes) }
      i += 1
    }
    out.toSeq
  }

  def numRecords: Int = scan().length
}
