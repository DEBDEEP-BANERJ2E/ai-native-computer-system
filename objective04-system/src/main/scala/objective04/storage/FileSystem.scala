package objective04.storage

import scala.collection.mutable

/** Simple filesystem on a BlockDevice (O4-H5..H8).
  *
  * Layout (block 0 = superblock):
  *   block 0        : superblock {magic, inodeStart, inodeBlocks, dataStart}
  *   inode region   : fixed 64-byte inodes, 8 per block
  *   data region    : file contents + directory entry blocks
  *
  * Inode (64B): type(4) size(4) nlink(4) blocks[13](52) = 64B
  *   type: 0=free 1=file 2=dir ; blocks[] = direct data block numbers
  * Directory entries (32B): ino(4) type(1) namelen(1) name(26)
  * Path resolution walks '/'-separated components from root (ino 2).
  */
class FileSystem(val dev: BlockDevice) {
  val BS = dev.blockSize

  // ---- constants ----
  val INODE_SIZE = 64
  val INODES_PER_BLOCK = BS / INODE_SIZE          // 8 for 512B blocks
  val DIRENT_SIZE = 32
  val DIRENTS_PER_BLOCK = BS / DIRENT_SIZE
  val T_FREE = 0; val T_FILE = 1; val T_DIR = 2
  val ROOT_INO = 2
  val SB_MAGIC = 0x414E3332                     // "AN32"

  // in-memory inode cache for simplicity (kernel would page these)
  private val cache = mutable.Map[Int, Array[Int]]()

  // ---- low-level block/inode io ----
  private def getInode(ino: Int): Array[Int] = {
    cache.get(ino) match {
      case Some(a) => a
      case None =>
        val blk = inodeBlockOf(ino); val idx = (ino % INODES_PER_BLOCK) * INODE_SIZE
        val b = dev.readBlock(blk)
        val a = new Array[Int](INODE_SIZE / 4)
        var i = 0; while (i < a.length) { a(i) = bytesToInt(b, idx + i * 4); i += 1 }
        cache(ino) = a; a
    }
  }
  private def putInode(ino: Int, a: Array[Int]): Unit = {
    cache(ino) = a
    val blk = inodeBlockOf(ino); val idx = (ino % INODES_PER_BLOCK) * INODE_SIZE
    val b = dev.readBlock(blk)
    var i = 0; while (i < a.length) { intToBytes(a(i), b, idx + i * 4); i += 1 }
    dev.writeBlock(blk, b)
  }
  private def inodeBlockOf(ino: Int): Int = {
    val sb = readSuperblock()
    sb(1) + (ino / INODES_PER_BLOCK)   // inodeStart + table index
  }
  private def bytesToInt(b: Array[Byte], o: Int): Int =
    (b(o) & 0xff) | ((b(o+1) & 0xff) << 8) | ((b(o+2) & 0xff) << 16) | ((b(o+3) & 0xff) << 24)
  private def intToBytes(v: Int, b: Array[Byte], o: Int): Unit = {
    b(o) = (v & 0xff).toByte; b(o+1) = ((v >>> 8) & 0xff).toByte
    b(o+2) = ((v >>> 16) & 0xff).toByte; b(o+3) = ((v >>> 24) & 0xff).toByte
  }

  // superblock fields: [magic, inodeStart, inodeBlocks, dataStart, nInodes, freeHint]
  private def readSuperblock(): Array[Int] = {
    val b = dev.readBlock(0); val a = new Array[Int](6)
    var i = 0; while (i < 6) { a(i) = bytesToInt(b, i * 4); i += 1 }; a
  }
  private def writeSuperblock(a: Array[Int]): Unit = {
    val b = dev.readBlock(0)
    var i = 0; while (i < 6) { intToBytes(a(i), b, i * 4); i += 1 }
    dev.writeBlock(0, b)
  }

  /** Format the filesystem (H9 metadata init). */
  def format(): Unit = {
    val inodeStart = 1
    val inodeBlocks = 8                          // 64 inodes
    val dataStart = inodeStart + inodeBlocks
    writeSuperblock(Array(SB_MAGIC, inodeStart, inodeBlocks, dataStart, 0, dataStart))
    // mark root dir inode (2) as dir with one data block for "."
    val root = new Array[Int](INODE_SIZE / 4)
    root(0) = T_DIR; root(1) = 0; root(2) = 1
    root(3) = dataStart                          // first data block = dir block
    // init a few inodes free; root uses block dataStart
    var i = 0; while (i < INODES_PER_BLOCK * inodeBlocks) {
      val ino = i; val a = new Array[Int](INODE_SIZE / 4)
      a(0) = T_FREE; putInode(ino, a); i += 1
    }
    putInode(ROOT_INO, root)
    // write "." dirent into root's block
    writeDirentBlock(dataStart, ROOT_INO, ".", T_DIR)
  }

  // ---- directory helpers ----
  private def dirBlockOf(ino: Int): Int = getInode(ino)(3)
  private def writeDirentBlock(blk: Int, ino: Int, name: String, t: Int): Unit = {
    val b = dev.readBlock(blk)
    // entry 0: ino, type, namelen, name
    intToBytes(ino, b, 0)
    b(4) = t.toByte; b(5) = name.length.toByte
    val nb = name.getBytes("UTF-8")
    var i = 0; while (i < nb.length && i < 26) { b(6 + i) = nb(i); i += 1 }
    dev.writeBlock(blk, b)
  }
  // find a name in a directory inode; returns (ino,type) or None
  private def dirLookup(dirIno: Int, name: String): Option[(Int, Int)] = {
    val blk = dirBlockOf(dirIno)
    if (blk == 0) return None
    val b = dev.readBlock(blk)
    var off = 0
    while (off + DIRENT_SIZE <= BS) {
      val ino = bytesToInt(b, off)
      val t = b(off + 4) & 0xff; val nl = b(off + 5) & 0xff
      if (ino != 0 && nl > 0) {
        val nb = new String(b, off + 6, nl, "UTF-8")
        if (nb == name) return Some((ino, t))
      }
      off += DIRENT_SIZE
    }
    None
  }

  /** Resolve a '/'-separated path to an inode. */
  def resolve(path: String): Option[Int] = {
    val parts = path.split("/").filter(_.nonEmpty)
    var cur = ROOT_INO
    var i = 0
    while (i < parts.length) {
      val inode = getInode(cur)
      if ((inode(0) & 0xff) != T_DIR && i < parts.length - 1) return None
      dirLookup(cur, parts(i)) match {
        case Some((ino, _)) => cur = ino
        case None => return None
      }
      i += 1
    }
    Some(cur)
  }

  /** Allocate a free inode; returns ino or -1. */
  private def allocInode(t: Int): Int = {
    val sb = readSuperblock()
    val total = sb(4)
    var ino = 0
    while (ino < total) {
      val a = getInode(ino)
      if ((a(0) & 0xff) == T_FREE) {
        val na = new Array[Int](INODE_SIZE / 4)
        na(0) = t; na(1) = 0; na(2) = 0
        putInode(ino, na); return ino
      }
      ino += 1
    }
    // grow: bump inode count in superblock (simplest: fixed region, so -1)
    -1
  }
  // sb(5) = next free data block (bump allocator; H3 bitmap is a refinement)
  private def allocDataBlock(): Int = {
    val sb = readSuperblock()
    val b = sb(5)
    if (b < dev.numBlocks) {
      sb(5) = b + 1
      writeSuperblock(sb)
      b
    } else -1
  }

  /** Create a file or directory. path parent must exist. */
  def create(path: String, isDir: Boolean): Option[Int] = {
    val idx = path.lastIndexOf('/')
    val parentPath = if (idx <= 0) "/" else path.substring(0, idx)
    val name = path.substring(idx + 1)
    resolve(parentPath).flatMap { pino =>
      if (resolve(path).isDefined) None          // exists
      else {
        val t = if (isDir) T_DIR else T_FILE
        val ino = allocInode(t)
        if (ino < 0) None
        else {
          val dblk = allocDataBlock()
          if (dblk < 0) {
            val a = getInode(ino); a(0) = T_FREE; putInode(ino, a); None
          } else {
            val a = getInode(ino); a(3) = dblk; a(1) = 0; putInode(ino, a)
            appendDirent(pino, name, ino, t)
            if (isDir) writeDirentBlock(dblk, ino, ".", T_DIR)
            Some(ino)
          }
        }
      }
    }
  }
  private def appendDirent(dirIno: Int, name: String, ino: Int, t: Int): Unit = {
    val blk = dirBlockOf(dirIno)
    val b = dev.readBlock(blk)
    var off = 0; var slot = -1
    // find first free slot (ino==0)
    while (off + DIRENT_SIZE <= BS) {
      if (bytesToInt(b, off) == 0) { slot = off; off = BS; }
      else off += DIRENT_SIZE
    }
    if (slot >= 0) {
      intToBytes(ino, b, slot); b(slot+4) = t.toByte; b(slot+5) = name.length.toByte
      val nb = name.getBytes("UTF-8"); var i = 0
      while (i < nb.length && i < 26) { b(slot+6+i) = nb(i); i += 1 }
      dev.writeBlock(blk, b)
    }
  }

  /** Read file contents (direct blocks only, up to 13 blocks). */
  def readFile(ino: Int, off: Int, len: Int): Array[Byte] = {
    val inode = getInode(ino)
    if ((inode(0) & 0xff) != T_FILE) return Array.empty
    val out = new Array[Byte](len)
    var done = 0
    while (done < len) {
      val pos = off + done
      val blkIdx = pos / BS; val blkOff = pos % BS
      val n = math.min(len - done, BS - blkOff)
      if (blkIdx >= 13) return out.take(done)
      val dblk = inode(3 + blkIdx)
      if (dblk == 0) return out.take(done)
      val b = dev.readBlock(dblk)
      System.arraycopy(b, blkOff, out, done, n)
      done += n
    }
    out
  }

  /** Write file contents, allocating direct blocks as needed (max 13). */
  def writeFile(ino: Int, off: Int, data: Array[Byte]): Unit = {
    val inode = getInode(ino)
    if ((inode(0) & 0xff) != T_FILE) return
    var done = 0
    while (done < data.length) {
      val pos = off + done
      val blkIdx = pos / BS; val blkOff = pos % BS
      val n = math.min(data.length - done, BS - blkOff)
      if (blkIdx >= 13) return
      var dblk = inode(3 + blkIdx)
      if (dblk == 0) {
        dblk = allocDataBlock()
        if (dblk < 0) return
        inode(3 + blkIdx) = dblk
      }
      val b = dev.readBlock(dblk)
      System.arraycopy(data, done, b, blkOff, n)
      dev.writeBlock(dblk, b)
      done += n
    }
    val newSize = off + data.length
    if (newSize > inode(1)) inode(1) = newSize
    putInode(ino, inode)
  }

  def fileSize(ino: Int): Int = getInode(ino)(1)
  def inodeType(ino: Int): Int = getInode(ino)(0) & 0xff
}
