package objective06.txn

import objective06.buffer.BufferPool
import objective06.wal.Wal

/** Transaction manager (O6): WAL-backed commit/abort + recovery.
  *
  * Model: a transaction dirties pages through the buffer pool. Before a page
  * is first written in a txn we snapshot its before-image into the txn's
  * undo set and append an UPDATE record (before+after) to the WAL. Commit
  * appends COMMIT and flushes dirty pages; abort restores before-images.
  * Recovery redoes committed updates then undoes uncommitted ones.
  */
class TxnManager(pool: BufferPool) {
  val wal = new Wal.Log()
  private var nextTxn = 1L
  private val active = scala.collection.mutable.Map[Long, TxnState]()

  private case class TxnState(id:Long, undo:scala.collection.mutable.Map[Int,Array[Byte]])

  def begin(): Long = { val id=nextTxn; nextTxn+=1; wal.append(Wal.Begin(id)); active(id)=TxnState(id,scala.collection.mutable.Map()); id }

  /** Record a page write within txn: snapshot before-image once, log UPDATE. */
  def writePage(txn:Long, pid:Int, newBytes:Array[Byte]): Unit = active.get(txn).foreach { st =>
    if (!st.undo.contains(pid)) {
      val before = pool.get(pid).clone()
      st.undo(pid)=before
      wal.append(Wal.Update(txn,pid,before,newBytes.clone()))
    }
    // apply to pool
    System.arraycopy(newBytes,0,pool.get(pid),0,math.min(newBytes.length,pool.pageSize))
    pool.setDirty(pid)
  }

  def commit(txn:Long): Unit = active.remove(txn).foreach { st =>
    wal.append(Wal.Commit(txn)); pool.flush()
  }

  def abort(txn:Long): Unit = active.remove(txn).foreach { st =>
    wal.append(Wal.Abort(txn))
    // restore before-images (reverse order not needed for page images)
    st.undo.foreach{case(pid,before)=>System.arraycopy(before,0,pool.get(pid),0,pool.pageSize);pool.setDirty(pid)}
    pool.flush()
  }

  /** Crash recovery: redo committed UPDATEs, undo uncommitted. */
  def recover(): Unit = {
    val recs = wal.records
    val committed = scala.collection.mutable.Set[Long]()
    val begun = scala.collection.mutable.Set[Long]()
    recs.foreach{case Wal.Commit(t)=>committed+=t;case Wal.Begin(t)=>begun+=t;case _=>()}
    val open = begun -- committed
    // redo: apply after-image of every UPDATE whose txn committed (idempotent)
    recs.foreach{case Wal.Update(t,pid,_,after) if committed(t)=>
      System.arraycopy(after,0,pool.get(pid),0,math.min(after.length,pool.pageSize));pool.setDirty(pid);case _=>()}
    // undo: restore before-image of updates in still-open txns (last writer wins -> use first before-image per pid per txn is approx)
    val undoMap = scala.collection.mutable.Map[Int,Array[Byte]]()
    recs.foreach{case Wal.Update(t,pid,before,_) if open(t)=> if(!undoMap.contains(pid)) undoMap(pid)=before; case _=>()}
    undoMap.foreach{case(pid,b)=>System.arraycopy(b,0,pool.get(pid),0,pool.pageSize);pool.setDirty(pid)}
    pool.flush()
  }
}
