package objective06.wal

import java.nio.ByteBuffer
import java.nio.ByteOrder
import scala.collection.mutable

/** Write-ahead log (O6): page-image WAL for ARIES-style redo/undo.
  *
  * Log is an append-only byte stream. Record types:
  *   UPDATE(pid, beforeImg, afterImg)  — page changed
  *   COMMIT(txnId) / ABORT(txnId)       — transaction boundaries
  *   CHECKPOINT                        — flush point
  *
  * Recovery: scan log; redo all UPDATE after-images (idempotent), then undo
  * uncommitted txns via before-images. v1 keeps full page images.
  */
object Wal {
  // record type tags
  val T_UPDATE = 1; val T_COMMIT = 2; val T_ABORT = 3; val T_BEGIN = 4

  sealed trait Rec
  case class Update(txn:Long,pid:Int,before:Array[Byte],after:Array[Byte]) extends Rec
  case class Commit(txn:Long) extends Rec
  case class Abort(txn:Long) extends Rec
  case class Begin(txn:Long) extends Rec

  /** Append-only in-memory WAL (dev). A PageStore/file-backed WAL slots in
    * at O9 behind the same interface. */
  class Log {
    private val buf = mutable.ArrayBuffer[Byte]()
    def append(rec: Rec): Unit = rec match {
      case Update(txn,pid,b,a) =>
        put32(T_UPDATE); put64(txn); put32(pid); put32(b.length); putBytes(b); put32(a.length); putBytes(a)
      case Commit(txn)   => put32(T_COMMIT); put64(txn)
      case Abort(txn)    => put32(T_ABORT);  put64(txn)
      case Begin(txn)    => put32(T_BEGIN);  put64(txn)
    }
    private def put32(v:Int):Unit={val b=Array.ofDim[Byte](4);ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).putInt(v);buf++=b}
    private def put64(v:Long):Unit={val b=Array.ofDim[Byte](8);ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).putLong(v);buf++=b}
    private def putBytes(a:Array[Byte]):Unit=buf++=a

    /** Parse all records (for tests/recovery). */
    def records: Vector[Rec] = {
      val a=buf.toArray; val out=mutable.ArrayBuffer[Rec](); var p=0
      def g32()={val v=ByteBuffer.wrap(a,p,4).order(ByteOrder.BIG_ENDIAN).getInt;p+=4;v}
      def g64()={val v=ByteBuffer.wrap(a,p,8).order(ByteOrder.BIG_ENDIAN).getLong;p+=8;v}
      while(p<a.length){
        val t=g32()
        t match {
          case T_UPDATE=>{val tx=g64();val pid=g32();val bl=g32();val b=a.slice(p,p+bl);p+=bl;val al=g32();val af=a.slice(p,p+al);p+=al;out+=Update(tx,pid,b,af)}
          case T_COMMIT=>out+=Commit(g64())
          case T_ABORT=>out+=Abort(g64())
          case T_BEGIN=>out+=Begin(g64())
          case _=>p=a.length // bail on unknown
        }
      }
      out.toVector
    }
    def size:Int=buf.size
  }
}
