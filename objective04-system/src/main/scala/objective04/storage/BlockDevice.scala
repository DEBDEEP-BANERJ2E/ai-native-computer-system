package objective04.storage

/** Block device abstraction (O4-H1/H2).
  *
  * A block device stores fixed-size blocks addressed by block number.
  * The filesystem / block cache layers sit on top; O3's FD layer reaches
  * files through them. Keeps the storage-block address space distinct from
  * physical frames and virtual addresses.
  */
trait BlockDevice {
  val blockSize: Int      // bytes per block (power of two)
  val numBlocks: Int      // total blocks
  def readBlock(blk: Int): Array[Byte]
  def writeBlock(blk: Int, data: Array[Byte]): Unit
  def read(blk: Int, off: Int, len: Int): Array[Byte]
  def write(blk: Int, off: Int, data: Array[Byte]): Unit
}

/** RAM-backed reference block device (H2). */
class RamDisk(override val blockSize: Int = 512,
              override val numBlocks: Int = 2048)
    extends BlockDevice {
  private val disk = Array.fill(numBlocks)(new Array[Byte](blockSize))

  def readBlock(blk: Int): Array[Byte] = {
    require(blk >= 0 && blk < numBlocks, s"block $blk out of range")
    disk(blk).clone()
  }
  def writeBlock(blk: Int, data: Array[Byte]): Unit = {
    require(blk >= 0 && blk < numBlocks, s"block $blk out of range")
    require(data.length == blockSize, "block size mismatch")
    System.arraycopy(data, 0, disk(blk), 0, blockSize)
  }
  def read(blk: Int, off: Int, len: Int): Array[Byte] = {
    val b = readBlock(blk)
    b.slice(off, off + len)
  }
  def write(blk: Int, off: Int, data: Array[Byte]): Unit = {
    val b = readBlock(blk)
    System.arraycopy(data, 0, b, off, data.length)
    writeBlock(blk, b)
  }
}
