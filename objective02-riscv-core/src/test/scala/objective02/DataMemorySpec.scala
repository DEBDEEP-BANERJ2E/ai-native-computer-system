package objective02

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import objective02.decode.MemWidth
import objective02.memory.DataMemory

class DataMemorySpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "DataMemory"

  it should "correctly write and read 32-bit words with little-endian byte ordering" in {
    test(new DataMemory(sizeBytes = 256)) { dut =>
      // SW 0x12345678 at address 0x10
      dut.io.address.poke("h10".U)
      dut.io.writeData.poke("h12345678".U)
      dut.io.memWrite.poke(true.B)
      dut.io.memRead.poke(false.B)
      dut.io.memWidth.poke(MemWidth.WORD)
      dut.clock.step(1)

      // Verify full word read
      dut.io.memWrite.poke(false.B)
      dut.io.memRead.poke(true.B)
      dut.io.memWidth.poke(MemWidth.WORD)
      dut.io.address.poke("h10".U)
      dut.io.readData.expect("h12345678".U)
      dut.io.misaligned.expect(false.B)

      // Verify individual bytes are stored little-endian:
      // address 0x10 = 0x78 (byte 0)
      dut.io.memWidth.poke(MemWidth.BYTE_U)
      dut.io.address.poke("h10".U)
      dut.io.readData.expect("h78".U)

      // address 0x11 = 0x56 (byte 1)
      dut.io.address.poke("h11".U)
      dut.io.readData.expect("h56".U)

      // address 0x12 = 0x34 (byte 2)
      dut.io.address.poke("h12".U)
      dut.io.readData.expect("h34".U)

      // address 0x13 = 0x12 (byte 3)
      dut.io.address.poke("h13".U)
      dut.io.readData.expect("h12".U)
    }
  }

  it should "correctly perform byte stores (SB) and signed/unsigned byte loads (LB, LBU)" in {
    test(new DataMemory(sizeBytes = 256)) { dut =>
      // SB 0x85 (-123 signed) at address 0x20
      dut.io.address.poke("h20".U)
      dut.io.writeData.poke("h85".U)
      dut.io.memWrite.poke(true.B)
      dut.io.memRead.poke(false.B)
      dut.io.memWidth.poke(MemWidth.BYTE)
      dut.clock.step(1)

      // SB 0x7F (+127 signed) at address 0x21
      dut.io.address.poke("h21".U)
      dut.io.writeData.poke("h7F".U)
      dut.clock.step(1)

      dut.io.memWrite.poke(false.B)
      dut.io.memRead.poke(true.B)

      // LB from 0x20 should sign-extend 0x85 -> 0xFFFFFF85
      dut.io.memWidth.poke(MemWidth.BYTE)
      dut.io.address.poke("h20".U)
      dut.io.readData.expect("hFFFFFF85".U)

      // LBU from 0x20 should zero-extend 0x85 -> 0x00000085
      dut.io.memWidth.poke(MemWidth.BYTE_U)
      dut.io.readData.expect("h00000085".U)

      // LB from 0x21 should sign-extend 0x7F -> 0x0000007F
      dut.io.memWidth.poke(MemWidth.BYTE)
      dut.io.address.poke("h21".U)
      dut.io.readData.expect("h0000007F".U)
    }
  }

  it should "correctly perform halfword stores (SH) and signed/unsigned halfword loads (LH, LHU)" in {
    test(new DataMemory(sizeBytes = 256)) { dut =>
      // SH 0xFC18 (-1000 signed) at address 0x30
      dut.io.address.poke("h30".U)
      dut.io.writeData.poke("hFC18".U)
      dut.io.memWrite.poke(true.B)
      dut.io.memRead.poke(false.B)
      dut.io.memWidth.poke(MemWidth.HALF)
      dut.clock.step(1)

      dut.io.memWrite.poke(false.B)
      dut.io.memRead.poke(true.B)

      // LH from 0x30 should sign-extend 0xFC18 -> 0xFFFFFC18 (-1000)
      dut.io.memWidth.poke(MemWidth.HALF)
      dut.io.address.poke("h30".U)
      dut.io.readData.expect("hFFFFFC18".U)

      // LHU from 0x30 should zero-extend 0xFC18 -> 0x0000FC18 (64536)
      dut.io.memWidth.poke(MemWidth.HALF_U)
      dut.io.readData.expect("h0000FC18".U)
    }
  }

  it should "detect misaligned accesses and suppress writes" in {
    test(new DataMemory(sizeBytes = 256)) { dut =>
      // Attempt misaligned Word write at address 0x01 (not divisible by 4)
      dut.io.address.poke("h01".U)
      dut.io.writeData.poke("hDEADBEEF".U)
      dut.io.memWrite.poke(true.B)
      dut.io.memRead.poke(false.B)
      dut.io.memWidth.poke(MemWidth.WORD)
      dut.io.misaligned.expect(true.B)
      dut.clock.step(1)

      // Attempt misaligned Halfword write at address 0x03 (odd address)
      dut.io.address.poke("h03".U)
      dut.io.writeData.poke("hCAFE".U)
      dut.io.memWidth.poke(MemWidth.HALF)
      dut.io.misaligned.expect(true.B)
      dut.clock.step(1)

      // Verify that word at address 0x00 was not corrupted and remains 0
      dut.io.memWrite.poke(false.B)
      dut.io.memRead.poke(true.B)
      dut.io.memWidth.poke(MemWidth.WORD)
      dut.io.address.poke("h00".U)
      dut.io.readData.expect(0.U)
    }
  }
}
