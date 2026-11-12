package objective04.loader

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** AN32EXE executable parser (O5-I input side).
  *
  * Mirrors toolchain/assembler/include/an32exe/exeformat.hpp. Parses the
  * loader-facing image: ehdr + program headers + section headers + strtab +
  * segment payloads. Keeps file offset / vaddr / filesz / memsz distinct.
  */
object An32Exe {

  val MAGIC = "AN32EXE\u0000".getBytes("ISO-8859-1")
  val VERSION = 1
  val PT_LOAD = 1
  val PF_X = 1; val PF_W = 2; val PF_R = 4

  case class ProgramHeader(`type`: Int, flags: Int, offset: Int,
                           vaddr: Int, filesz: Int, memsz: Int,
                           align: Int)
  case class SectionHeader(name: String, `type`: Int, flags: Int,
                           vaddr: Int, size: Int, offset: Int)
  case class Exe(entry: Int, phdrs: Vector[ProgramHeader],
                 shdrs: Vector[SectionHeader], data: Array[Byte])

  private def u32(b: Array[Byte], o: Int): Int =
    ByteBuffer.wrap(b, o, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()

  /** Parse an AN32EXE byte image. Throws on malformed input. */
  def parse(bytes: Array[Byte]): Exe = {
    require(bytes.length >= 64, "AN32EXE too small")
    require((0 until 8).forall(i => bytes(i) == MAGIC(i)), "bad AN32EXE magic")
    val version = u32(bytes, 8)
    require(version == VERSION, s"unsupported AN32EXE version $version")
    val entry  = u32(bytes, 12)
    val phOff  = u32(bytes, 16)
    val shOff  = u32(bytes, 20)
    val strOff = u32(bytes, 24)
    val phnum  = u32(bytes, 28)
    val shnum  = u32(bytes, 32)
    val strsz  = u32(bytes, 36)

    // program headers (32B each)
    val phdrs = (0 until phnum).map { i =>
      val o = phOff + i * 32
      ProgramHeader(u32(bytes,o), u32(bytes,o+4), u32(bytes,o+8),
                    u32(bytes,o+12), u32(bytes,o+16), u32(bytes,o+20),
                    u32(bytes,o+24))
    }.toVector

    // string table
    val strtab = new String(bytes, strOff, math.min(strsz, bytes.length - strOff), "UTF-8")
    def nameAt(off: Int): String = {
      val end = strtab.indexOf('\u0000', off)
      if (off < 0 || off >= strtab.length) "" else strtab.substring(off, if (end<0) strtab.length else end)
    }

    // section headers (32B each)
    val shdrs = (0 until shnum).map { i =>
      val o = shOff + i * 32
      SectionHeader(nameAt(u32(bytes,o)), u32(bytes,o+4), u32(bytes,o+8),
                    u32(bytes,o+12), u32(bytes,o+16), u32(bytes,o+20))
    }.toVector

    Exe(entry, phdrs, shdrs, bytes)
  }
}
