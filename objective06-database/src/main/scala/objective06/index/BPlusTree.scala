package objective06.index

import objective06.buffer.BufferPool
import java.nio.ByteBuffer
import java.nio.ByteOrder
import scala.collection.mutable

/** B+tree secondary index (O6).
  *
  * Nodes are fixed-size pages in the BufferPool. Keys are byte arrays with
  * an Ordering (ints encoded big-endian for sortability). Values are packed
  * record handles (pageId<<32 | slot) as u64 -> stored big-endian.
  *
  * Node layout (little-endian header):
  *   [isLeaf u8][numKeys u16][nextLeaf u32][reserved]
  *   leaf:   entries[numKeys] of {keyLen u16, key bytes, handle u64}
  *   internal:{sepKey..., childPid...}  simplified: parallel arrays
  *
  * v1: in-memory node model serialized to pages; insert with leaf split and
  * separator propagation; search + range scan. Delete via tombstone later.
  */
class BPlusTree(pool: BufferPool, rootPid0: Int = -1) {

  private val PAGE = pool.pageSize
  private var rootPid: Int = rootPid0

  // ---- node model ----
  private case class Node(isLeaf: Boolean, keys: Vector[Array[Byte]],
                          vals: Vector[Long], children: Vector[Int],
                          nextLeaf: Int)

  // serialize a node into a page byte array (simple packed format)
  private def serialize(n: Node): Array[Byte] = {
    val buf = ByteBuffer.allocate(PAGE).order(ByteOrder.BIG_ENDIAN)
    buf.put(if(n.isLeaf)1.toByte else 0.toByte)
    buf.putShort(n.keys.size.toShort)
    buf.putInt(n.nextLeaf)
    if (n.isLeaf) {
      for (i <- 0 until n.keys.size) {
        buf.putShort(n.keys(i).length.toShort)
        buf.put(n.keys(i))
        buf.putLong(n.vals(i))
      }
    } else {
      buf.putInt(n.children.size)
      for (i <- 0 until n.keys.size) { buf.putShort(n.keys(i).length.toShort); buf.put(n.keys(i)) }
      for (c <- n.children) buf.putInt(c)
    }
    val a = new Array[Byte](PAGE); val b = buf.array(); System.arraycopy(b,0,a,0,b.length); a
  }

  private def deserialize(pid: Int): Node = {
    val a = pool.get(pid)
    val bb = ByteBuffer.wrap(a).order(ByteOrder.BIG_ENDIAN)
    val isLeaf = bb.get()==1.toByte
    val nk = bb.getShort() & 0xffff
    val nextLeaf = bb.getInt()
    if (isLeaf) {
      val ks = Vector.newBuilder[Array[Byte]]; val vs = Vector.newBuilder[Long]
      var i=0
      while(i<nk){ val kl=bb.getShort()&0xffff; val k=new Array[Byte](kl); bb.get(k); ks+=k; vs+=bb.getLong(); i+=1 }
      Node(true, ks.result(), vs.result(), Vector.empty, nextLeaf)
    } else {
      val nc = bb.getInt()
      val ks = Vector.newBuilder[Array[Byte]]
      var i=0; while(i<nk){ val kl=bb.getShort()&0xffff; val k=new Array[Byte](kl); bb.get(k); ks+=k; i+=1 }
      val ch = Vector.fill(nc)(bb.getInt())
      Node(false, ks.result(), Vector.empty, ch, nextLeaf)
    }
  }

  implicit val ord: Ordering[Array[Byte]] = Ordering.by{ k => new String(k, "ISO-8859-1") }

  private def newNodePid(isLeaf:Boolean): Int = {
    val pid = pool.alloc()
    val n = if(isLeaf) Node(true,Vector.empty,Vector.empty,Vector.empty,-1)
            else Node(false,Vector.empty,Vector.empty,Vector.empty,-1)
    writeNode(pid,n); pid
  }
  private def writeNode(pid:Int,n:Node):Unit = { val a=serialize(n); System.arraycopy(a,0,pool.get(pid),0,PAGE); pool.setDirty(pid) }

  /** Search key -> Option[value]. */
  def search(key: Array[Byte]): Option[Long] = {
    if(rootPid<0) return None
    var pid = rootPid
    var node = deserialize(pid)
    while(!node.isLeaf){
      // find child: first index where key <= keys(i)
      var i=0; while(i<node.keys.size && ord.gt(key,node.keys(i))) i+=1
      pid = node.children(i); node = deserialize(pid)
    }
    val idx = node.keys.indexWhere(k=>ord.compare(k,key)>=0)
    if(idx>=0 && ord.compare(node.keys(idx),key)==0) Some(node.vals(idx)) else None
  }

  /** Range scan [lo,hi) -> Seq[(key,value)]. */
  def scan(lo: Array[Byte], hi: Array[Byte]): Seq[(Array[Byte],Long)] = {
    if(rootPid<0) return Seq.empty
    val out = mutable.ArrayBuffer[(Array[Byte],Long)]()
    // descend to leftmost leaf >= lo
    var pid=rootPid; var node=deserialize(pid)
    while(!node.isLeaf){ var i=0; while(i<node.keys.size&&ord.gt(lo,node.keys(i)))i+=1; pid=node.children(i); node=deserialize(pid) }
    var leaf=node; var lp=pid
    var done=false
    while(!done){
      var i=0
      while(i<leaf.keys.size){
        if(ord.gteq(leaf.keys(i),lo)&&ord.lt(leaf.keys(i),hi)) out+=((leaf.keys(i),leaf.vals(i)))
        if(ord.gteq(leaf.keys(i),hi)){done=true}
        i+=1
      }
      if(done) ()
      else if(leaf.nextLeaf>=0){ lp=leaf.nextLeaf; leaf=deserialize(lp) }
      else done=true
    }
    out.toSeq
  }

  /** Insert key->value. v1: leaf-level insert + split propagation (recursive). */
  def insert(key: Array[Byte], value: Long): Unit = {
    if(rootPid<0){
      val lp=newNodePid(true); rootPid=lp
      val ln=Node(true,Vector(key),Vector(value),Vector.empty,-1); writeNode(lp,ln); return
    }
    insertRec(rootPid,key,value) match {
      case Some((sep,pid)) =>
        // new root internal
        val nr=Node(false,Vector(sep),Vector.empty,Vector(rootPid,pid),-1)
        val np=newNodePid(false); writeNode(np,nr); rootPid=np
      case None => ()
    }
  }

  // returns Some((separatorKey,newRightPid)) if this node split
  private def insertRec(pid:Int,key:Array[Byte],value:Long): Option[(Array[Byte],Int)] = {
    val n=deserialize(pid)
    if(n.isLeaf){
      // insert sorted
      var i=0; while(i<n.keys.size&&ord.lt(n.keys(i),key))i+=1
      val nk=n.keys.patch(i,Vector(key),0); val nv=n.vals.patch(i,Vector(value),0)
      val nn=Node(true,nk,nv,Vector.empty,n.nextLeaf)
      val maxKeys = (PAGE-16)/24  // rough capacity
      if(nk.size<=maxKeys){ writeNode(pid,nn); None }
      else {
        // split: left keeps half, right gets rest
        val mid=nk.size/2
        val lk=nk.take(mid); val lv=nv.take(mid)
        val rk=nk.drop(mid); val rv=nv.drop(mid)
        val rpid=newNodePid(true)
        writeNode(pid,Node(true,lk,lv,Vector.empty,rpid))
        writeNode(rpid,Node(true,rk,rv,Vector.empty,n.nextLeaf))
        Some((rk.head,rpid))
      }
    } else {
      var i=0; while(i<n.keys.size&&ord.gt(key,n.keys(i)))i+=1
      insertRec(n.children(i),key,value) match {
        case Some((sep,rpid)) =>
          val nk=n.keys.patch(i,Vector(sep),0); val nc=n.children.patch(i+1,Vector(rpid),0)
          val nn=Node(false,nk,Vector.empty,nc,n.nextLeaf)
          val maxK=(PAGE-16)/20
          if(nk.size<=maxK){writeNode(pid,nn);None}
          else { val mid=nk.size/2; val lk=nk.take(mid);val rk=nk.drop(mid);val lc=nc.take(mid+1);val rc=nc.drop(mid+1)
                 writeNode(pid,Node(false,lk,Vector.empty,lc,-1));val np=newNodePid(false);writeNode(np,Node(false,rk,Vector.empty,rc,-1));Some((rk.head,np)) }
        case None => None
      }
    }
  }
}
