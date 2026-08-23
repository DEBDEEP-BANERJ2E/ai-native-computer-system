package objective02.memory

import chisel3._
import chisel3.util._
import objective02.decode.MemWidth

class DataMemoryIO extends Bundle {
  val address    = Input(UInt(32.W))
  val writeData  = Input(UInt(32.W))
  val memRead    = Input(Bool())
  val memWrite   = Input(Bool())
  val memWidth   = Input(UInt(3.W))
  val readData   = Output(UInt(32.W))
  val misaligned = Output(Bool())
}

class DataMemory(val sizeBytes: Int = 4096) extends Module {
  val io = IO(new DataMemoryIO)

  require(sizeBytes % 4 == 0, "DataMemory size must be a multiple of 4 bytes")
  val depthWords = sizeBytes / 4

  // Word-organized 4-byte lanes: [lane3, lane2, lane1, lane0]
  val mem = RegInit(VecInit(Seq.fill(depthWords)(VecInit(Seq.fill(4)(0.U(8.W))))))

  val wordIndex  = io.address(31, 2)
  val byteOffset = io.address(1, 0)
  val inBounds   = wordIndex < depthWords.U

  // -------------------------------------------------------------
  // Misalignment Detection
  // -------------------------------------------------------------
  val isHalf = io.memWidth === MemWidth.HALF || io.memWidth === MemWidth.HALF_U
  val isWord = io.memWidth === MemWidth.WORD

  val halfMisaligned = isHalf && (byteOffset(0) =/= 0.U)
  val wordMisaligned = isWord && (byteOffset =/= 0.U)
  val misaligned     = (halfMisaligned || wordMisaligned) && (io.memRead || io.memWrite)

  io.misaligned := misaligned

  // -------------------------------------------------------------
  // Synchronous Store Logic (Little-Endian Byte Lanes)
  // -------------------------------------------------------------
  when(io.memWrite && inBounds && !misaligned) {
    switch(io.memWidth) {
      is(MemWidth.BYTE) {
        // SB: Store 1 byte at address
        mem(wordIndex)(byteOffset) := io.writeData(7, 0)
      }
      is(MemWidth.HALF) {
        // SH: Store 2 bytes at aligned halfword offset
        mem(wordIndex)(byteOffset)     := io.writeData(7, 0)
        mem(wordIndex)(byteOffset + 1.U) := io.writeData(15, 8)
      }
      is(MemWidth.WORD) {
        // SW: Store 4 bytes
        mem(wordIndex)(0) := io.writeData(7, 0)
        mem(wordIndex)(1) := io.writeData(15, 8)
        mem(wordIndex)(2) := io.writeData(23, 16)
        mem(wordIndex)(3) := io.writeData(31, 24)
      }
    }
  }

  // -------------------------------------------------------------
  // Combinational / Asynchronous Read Logic with Sign/Zero Extension
  // -------------------------------------------------------------
  val rawBytes = Wire(Vec(4, UInt(8.W)))
  when(inBounds) {
    rawBytes := mem(wordIndex)
  }.otherwise {
    rawBytes(0) := 0.U
    rawBytes(1) := 0.U
    rawBytes(2) := 0.U
    rawBytes(3) := 0.U
  }

  // Selected Byte (for LB / LBU)
  val selectedByte = rawBytes(byteOffset)

  // Selected Halfword (for LH / LHU)
  val selectedHalf = Mux(byteOffset(1) === 1.U,
    Cat(rawBytes(3), rawBytes(2)),
    Cat(rawBytes(1), rawBytes(0))
  )

  // Full Word (for LW)
  val fullWord = Cat(rawBytes(3), rawBytes(2), rawBytes(1), rawBytes(0))

  val readResult = WireDefault(0.U(32.W))

  when(io.memRead && inBounds && !misaligned) {
    switch(io.memWidth) {
      is(MemWidth.BYTE) {
        // LB: Sign-extend 8-bit byte to 32 bits
        readResult := Cat(Fill(24, selectedByte(7)), selectedByte)
      }
      is(MemWidth.BYTE_U) {
        // LBU: Zero-extend 8-bit byte to 32 bits
        readResult := Cat(0.U(24.W), selectedByte)
      }
      is(MemWidth.HALF) {
        // LH: Sign-extend 16-bit halfword to 32 bits
        readResult := Cat(Fill(16, selectedHalf(15)), selectedHalf)
      }
      is(MemWidth.HALF_U) {
        // LHU: Zero-extend 16-bit halfword to 32 bits
        readResult := Cat(0.U(16.W), selectedHalf)
      }
      is(MemWidth.WORD) {
        // LW: 32-bit word
        readResult := fullWord
      }
    }
  }

  io.readData := readResult
}
