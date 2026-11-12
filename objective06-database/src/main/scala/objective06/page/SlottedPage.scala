package objective06.page

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Slotted page (O6 storage primitive).
  *
  * Fixed-size page holding variable-length records. Layout (little-endian):
  *   [header][slot directory ... free ... records]
  *   header: pageId u32 | numSlots u16 | freeStart u16 | flags u16
  *   slot i (4B): recordOffset u16 | recordLen u16   (offset from page start)
  *
  * Records are stored from the END of the page backwards; the slot directory
  * grows forward from the header. freeStart = first free byte after dir.
  * Operations: insert, delete (tombstone via len=0xFFFF), iterate, compact.
  */
class SlottedPage(val pageId: Int, val data: Array[Byte]) {
  val size: Int = data.length

  // header field offsets
  private val H_PAGEID = 0;  private val H_NSLOTS = 4
  private val H_FREEST = 6; private val H_FLAGS  = 8
  private val HEADER = 10
  private val SLOT = 4      // bytes per slot entry

  private def u16(o:Int):Int = ByteBuffer.wrap(data,o,2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xffff
  private def w16(o:Int,v:Int):Unit = ByteBuffer.wrap(data,o,2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort)
  private def u32(o:Int):Int = ByteBuffer.wrap(data,o,4).order(ByteOrder.LITTLE_ENDIAN).getInt()
  private def w32(o:Int,v:Int):Unit = ByteBuffer.wrap(data,o,4).order(ByteOrder.LITTLE_ENDIAN).putInt(v)

  def numSlots:Int = u16(H_NSLOTS)
  private def freeStart:Int = u16(H_FREEST)
  private def setFreeStart(v:Int):Unit = w16(H_FREEST,v)
  private def freeEnd:Int = size // records grow down from `size`

  def init():Unit = {
    java.util.Arrays.fill(data,0.toByte)
    w32(H_PAGEID,pageId); w16(H_NSLOTS,0); w16(H_FREEST,HEADER); w16(H_FLAGS,0)
  }

  private def slotOff(i:Int):Int = HEADER + i*SLOT

  /** bytes available for a new record = freeEnd - freeStart - (numSlots+1)*SLOT */
  def freeSpace:Int = {
    val dirEnd = HEADER + (numSlots+1)*SLOT
    freeEnd - math.max(freeStart, dirEnd)
  }

  /** Insert record bytes; returns slot index or -1 if no room. */
  def insert(rec: Array[Byte]): Int = {
    val need = rec.length
    if (freeSpace < need) return -1
    val n = numSlots
    // place record at bottom of free region (just above existing records)
    // find lowest used record offset = min over slots of offset; records packed from top
    // simpler: track records from top; new record goes just below the lowest existing
    var lowest = size
    var i = 0
    while (i < n) { val o = u16(slotOff(i)); if (o!=0 && o<lowest) lowest=o; i+=1 }
    val newOff = lowest - need
    // copy bytes
    System.arraycopy(rec,0,data,newOff,need)
    // write slot entry (slot dir grows forward)
    val so = slotOff(n)
    w16(so,newOff); w16(so+2,need)
    w16(H_NSLOTS,n+1)
    // freeStart = end of slot dir
    setFreeStart(HEADER + (n+1)*SLOT)
    n
  }

  /** Fetch record i -> Option[Array[Byte]] (None if deleted/out of range). */
  def get(i:Int): Option[Array[Byte]] = {
    if(i<0||i>=numSlots) return None
    val so=slotOff(i); val off=u16(so); val len=u16(so+2)
    if(len==0xFFFF||off==0) return None
    if(off+len>size) return None
    Some(data.slice(off,off+len))
  }

  /** Delete record i (tombstone). */
  def delete(i:Int):Unit = {
    if(i<0||i>=numSlots)return
    val so=slotOff(i); w16(so+2,0xFFFF)
  }

  /** Iterate live records. */
  def foreach(f:(Int,Array[Byte])=>Unit):Unit = {
    var i=0; while(i<numSlots){ get(i).foreach(b=>f(i,b)); i+=1 }
  }

  def isFull:Int = if(freeSpace<=0)1 else 0

  /** Compact: rewrite live records packed at top, rebuild slot dir. */
  def compact():Unit = {
    val recs = scala.collection.mutable.ArrayBuffer[(Int,Array[Byte])]()
    foreach{(i,b)=>recs+=((i,b))}
    init()
    recs.foreach{case(_,b)=>insert(b)}
  }
}
