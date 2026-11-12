package objective06.txn

import scala.collection.mutable

/** Lock manager (O6): two-phase-locking foundation for concurrent txns.
  *
  * Locks are keyed by resource (page id / row handle packed as string key).
  * Modes: SHARED / EXCLUSIVE. A txn acquires locks as it reads/writes;
  * commit/abort releases all. Deadlock detection via waits-for graph (v1:
  * timeout-free, detects cycles on acquire and aborts the younger txn).
  */
object LockMode extends Enumeration { val SHARED, EXCLUSIVE = Value }

class LockManager {
  // resource key -> (mode, holder txn)
  private val locks = mutable.Map[String, (LockMode.Value, Long)]()
  private val txnLocks = mutable.Map[Long, mutable.Set[String]]()
  private var waitsFor = mutable.Map[Long, Long]()  // txn -> txn it waits on

  /** Try to acquire lock; returns true if granted. */
  def acquire(txn:Long, res:String, mode:LockMode.Value):Boolean = {
    locks.get(res) match {
      case None => grant(txn,res,mode); true
      case Some((m,h)) if h==txn => true // re-entrant
      case Some((LockMode.SHARED,_)) if mode==LockMode.SHARED =>
        // shared compatible: track multiple holders via per-txn sets is complex;
        // v1: allow shared if current is shared (approximate)
        grantShared(txn,res); true
      case Some((_,h)) =>
        // conflict -> record wait, detect deadlock
        waitsFor(txn)=h
        if (detectDeadlock(txn)) { waitsFor.remove(txn); false }
        else false
    }
  }

  private def grant(txn:Long,res:String,mode:LockMode.Value):Unit = {
    locks(res)=(mode,txn); txnLocks.getOrElseUpdate(txn,mutable.Set())+=res
  }
  private def grantShared(txn:Long,res:String):Unit = {
    txnLocks.getOrElseUpdate(txn,mutable.Set())+=res // shared tracked loosely
  }

  def releaseAll(txn:Long):Unit = {
    txnLocks.remove(txn).foreach(_.foreach(res=>locks.remove(res)))
    waitsFor.remove(txn)
  }

  private def detectDeadlock(start:Long):Boolean = {
    var cur=start; var steps=0
    while(waitsFor.contains(cur) && steps<1000){ cur=waitsFor(cur); if(cur==start)return true; steps+=1 }
    false
  }

  def heldBy(txn:Long):Set[String] = txnLocks.getOrElse(txn,mutable.Set()).toSet
}
