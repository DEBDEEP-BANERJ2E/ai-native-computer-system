package objective06.buffer

import scala.collection.mutable

/** PageStore: byte-addressable backing for database pages (O6).
  *
  * pid is a dense page number within a file/volume. Implementations:
  * MemoryPageStore (dev/tests) now; O4 BlockDevice-backed store at O9.
  */
trait PageStore {
  val pageSize: Int
  def numPages: Int
  def read(pid: Int): Array[Byte]
  def write(pid: Int, data: Array[Byte]): Unit
  def allocPage(): Int          // returns new pid, grows store
}

/** In-memory PageStore: dense array of fixed-size pages. */
class MemoryPageStore(override val pageSize: Int = 512) extends PageStore {
  private val pages = mutable.ArrayBuffer.fill(0)(new Array[Byte](pageSize))
  override def numPages: Int = pages.length
  override def read(pid: Int): Array[Byte] = pages(pid).clone()
  override def write(pid: Int, data: Array[Byte]): Unit = {
    require(data.length == pageSize, "page size mismatch")
    System.arraycopy(data, 0, pages(pid), 0, pageSize)
  }
  override def allocPage(): Int = { pages += new Array[Byte](pageSize); pages.length - 1 }
}

/** BufferPool: LRU page cache over a PageStore (O6).
  *
  * get(pid) returns the live (mutable) page bytes; caller edits then
  * calls unpin/flush. We track dirty pages; flush writes them back.
  * Simple clock/LRU via access counter.
  */
class BufferPool(store: PageStore, capacity: Int = 64) {
  import scala.collection.mutable
  private case class Frame(pid: Int, data: Array[Byte], dirty: Boolean, age: Long)
  private val frames = mutable.LinkedHashMap[Int, Frame]()
  private var clock = 0L
  private var hits = 0L; private var misses = 0L

  def pageSize: Int = store.pageSize

  /** Fetch page pid -> mutable byte array (cached). */
  def get(pid: Int): Array[Byte] = {
    frames.get(pid) match {
      case Some(f) =>
        hits += 1
        f.data // live reference; mark accessed via reinsert order
      case None =>
        misses += 1
        val data = store.read(pid)
        if (frames.size >= capacity) evictOne()
        frames(pid) = Frame(pid, data, dirty=false, age=clock)
        data
    }
  }

  /** Mark page dirty (will be flushed). */
  def setDirty(pid: Int): Unit = frames.get(pid).foreach{ f =>
    val nf = f.copy(dirty=true); frames.remove(pid); frames(pid)=nf }

  /** Allocate a fresh zeroed page in the store and cache it. */
  def alloc(): Int = {
    val pid = store.allocPage()
    if (frames.size >= capacity) evictOne()
    frames(pid) = Frame(pid, new Array[Byte](store.pageSize), dirty=true, age=clock)
    pid
  }

  private def evictOne(): Unit = {
    // evict least-recently-used (first in LinkedHashMap order we maintain by reinsert)
    val victim = frames.head._1
    val f = frames(victim)
    if (f.dirty) store.write(victim, f.data)
    frames.remove(victim)
  }

  /** Flush all dirty pages to the store. */
  def flush(): Unit = {
    frames.foreach{ case (pid,f) => if(f.dirty) store.write(pid,f.data) }
    // clear dirty flags
    val keys = frames.keys.toList
    keys.foreach{ k => frames.get(k).foreach{ f => frames.remove(k); frames(k)=f.copy(dirty=false) } }
  }

  def stats: (Long,Long) = (hits,misses)
}
