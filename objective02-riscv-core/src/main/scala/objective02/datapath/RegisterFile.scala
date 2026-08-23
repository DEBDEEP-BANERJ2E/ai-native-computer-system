package objective02.datapath

import chisel3._

class RegisterFileIO extends Bundle {
  // Read Port 1
  val rs1Address = Input(UInt(5.W))
  val rs1Data    = Output(UInt(32.W))

  // Read Port 2
  val rs2Address = Input(UInt(5.W))
  val rs2Data    = Output(UInt(32.W))

  // Write Port
  val rdAddress   = Input(UInt(5.W))
  val writeData   = Input(UInt(32.W))
  val writeEnable = Input(Bool())
}

class RegisterFile extends Module {
  val io = IO(new RegisterFileIO)

  // 31 physical 32-bit registers (x1 to x31). x0 is hardwired to 0.
  val regs = RegInit(VecInit(Seq.fill(31)(0.U(32.W))))

  // Synchronous Write Logic (x0 writes are ignored)
  when(io.writeEnable && io.rdAddress =/= 0.U) {
    regs(io.rdAddress - 1.U) := io.writeData
  }

  // Asynchronous Read Port 1 (x0 hardwired to 0)
  when(io.rs1Address === 0.U) {
    io.rs1Data := 0.U(32.W)
  }.otherwise {
    io.rs1Data := regs(io.rs1Address - 1.U)
  }

  // Asynchronous Read Port 2 (x0 hardwired to 0)
  when(io.rs2Address === 0.U) {
    io.rs2Data := 0.U(32.W)
  }.otherwise {
    io.rs2Data := regs(io.rs2Address - 1.U)
  }
}
