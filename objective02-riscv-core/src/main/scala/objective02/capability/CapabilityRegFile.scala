package objective02.capability

import chisel3._
import chisel3.util._

class CapabilityRegFileIO extends Bundle {
  val raddr1 = Input(UInt(3.W))
  val rdata1 = Output(new CapabilityLite)
  val wen    = Input(Bool())
  val waddr  = Input(UInt(3.W))
  val wdata  = Input(new CapabilityLite)
}

class CapabilityRegFile(dmemSizeBytes: Int = 4096) extends Module {
  val io = IO(new CapabilityRegFileIO)

  // Capability register storage for c1 to c7
  // c1: Data Memory Root (0x00000000 - dmemSizeBytes, RW)
  val c1Init = Wire(new CapabilityLite)
  c1Init.tag    := true.B
  c1Init.base   := "h00000000".U(32.W)
  c1Init.length := dmemSizeBytes.U(32.W)
  c1Init.perms  := CapabilityPerms.RW
  c1Init.offset := 0.U(32.W)

  // c2: System MMIO Root (0x80000000 - 0x80010000, 64 KiB, RW)
  val c2Init = Wire(new CapabilityLite)
  c2Init.tag    := true.B
  c2Init.base   := "h80000000".U(32.W)
  c2Init.length := "h00010000".U(32.W)
  c2Init.perms  := CapabilityPerms.RW
  c2Init.offset := 0.U(32.W)

  val c1Reg = RegInit(c1Init)
  val c2Reg = RegInit(c2Init)
  val c3Reg = RegInit(CapabilityLite.nullCapability())
  val c4Reg = RegInit(CapabilityLite.nullCapability())
  val c5Reg = RegInit(CapabilityLite.nullCapability())
  val c6Reg = RegInit(CapabilityLite.nullCapability())
  val c7Reg = RegInit(CapabilityLite.nullCapability())

  // Write port at WB stage: c0 (NULL), c1 (RAM root), c2 (MMIO root) are hardware-immutable roots
  when(io.wen && (io.waddr >= 3.U)) {
    switch(io.waddr) {
      is(3.U) { c3Reg := io.wdata }
      is(4.U) { c4Reg := io.wdata }
      is(5.U) { c5Reg := io.wdata }
      is(6.U) { c6Reg := io.wdata }
      is(7.U) { c7Reg := io.wdata }
    }
  }

  // Selected register output
  val regOut = WireDefault(CapabilityLite.nullCapability())
  switch(io.raddr1) {
    is(0.U) { regOut := CapabilityLite.nullCapability() }
    is(1.U) { regOut := c1Reg }
    is(2.U) { regOut := c2Reg }
    is(3.U) { regOut := c3Reg }
    is(4.U) { regOut := c4Reg }
    is(5.U) { regOut := c5Reg }
    is(6.U) { regOut := c6Reg }
    is(7.U) { regOut := c7Reg }
  }

  // WB -> ID bypass (only applies to writable process capabilities c3..c7)
  val bypassMatch = io.wen && (io.waddr >= 3.U) && (io.waddr === io.raddr1)
  io.rdata1 := Mux(bypassMatch, io.wdata, regOut)
}
