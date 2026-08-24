package objective02

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import objective02.system._
import objective02.decode.MemWidth

class SystemMMIOSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "SystemMMIO"

  it should "correctly detect MMIO window and isolate non-MMIO addresses" in {
    test(new SystemMMIO) { dut =>
      // Non-MMIO RAM address
      dut.io.address.poke("h00000100".U)
      dut.io.memReadReq.poke(true.B)
      dut.io.memWidth.poke(MemWidth.WORD)
      dut.clock.step(1)
      dut.io.windowHit.expect(false.B)
      dut.io.readAccepted.expect(false.B)

      // Objective 1 Telemetry Window
      dut.io.address.poke(MMIOAddress.CLA_SWITCHING)
      dut.clock.step(1)
      dut.io.windowHit.expect(true.B)
      dut.io.readAccepted.expect(true.B)

      // Objective 2 System Window
      dut.io.address.poke(MMIOAddress.SCHED_HINT)
      dut.clock.step(1)
      dut.io.windowHit.expect(true.B)
      dut.io.readAccepted.expect(true.B)

      // Objective 2 Security Window
      dut.io.address.poke(MMIOAddress.SEC_STATUS)
      dut.clock.step(1)
      dut.io.windowHit.expect(true.B)
      dut.io.readAccepted.expect(true.B)

      // Unmapped address in 0x8000xxxx range
      dut.io.address.poke("h80003000".U)
      dut.clock.step(1)
      dut.io.windowHit.expect(true.B)
      dut.io.readAccepted.expect(false.B)
    }
  }

  it should "reject subword or misaligned accesses to MMIO registers" in {
    test(new SystemMMIO) { dut =>
      dut.io.address.poke(MMIOAddress.SCHED_HINT)
      dut.io.writeData.poke("h12345678".U)

      // Byte write rejected
      dut.io.memWriteReq.poke(true.B)
      dut.io.memWidth.poke(MemWidth.BYTE)
      dut.clock.step(1)
      dut.io.writeAccepted.expect(false.B)

      // Halfword write rejected
      dut.io.memWidth.poke(MemWidth.HALF)
      dut.clock.step(1)
      dut.io.writeAccepted.expect(false.B)

      // Misaligned word access rejected
      dut.io.address.poke("h80002009".U) // misaligned SCHED_HINT
      dut.io.memWidth.poke(MemWidth.WORD)
      dut.clock.step(1)
      dut.io.writeAccepted.expect(false.B)
    }
  }

  it should "support RW for PROCESS_BEHAVIOR_CLASS and SCHED_HINT" in {
    test(new SystemMMIO) { dut =>
      dut.io.memWidth.poke(MemWidth.WORD)

      // Initial readback should be 0
      dut.io.address.poke(MMIOAddress.PROCESS_BEHAVIOR_CLASS)
      dut.io.memReadReq.poke(true.B)
      dut.io.memWriteReq.poke(false.B)
      dut.clock.step(1)
      dut.io.readData.expect(0.U)
      dut.io.processBehaviorClass.expect(0.U)

      // Write PROCESS_BEHAVIOR_CLASS
      dut.io.memReadReq.poke(false.B)
      dut.io.memWriteReq.poke(true.B)
      dut.io.writeData.poke("h0000002A".U)
      dut.clock.step(1)
      dut.io.writeAccepted.expect(true.B)

      // Readback PROCESS_BEHAVIOR_CLASS
      dut.io.memWriteReq.poke(false.B)
      dut.io.memReadReq.poke(true.B)
      dut.clock.step(1)
      dut.io.readData.expect("h0000002A".U)
      dut.io.processBehaviorClass.expect("h0000002A".U)

      // Write SCHED_HINT
      dut.io.address.poke(MMIOAddress.SCHED_HINT)
      dut.io.memReadReq.poke(false.B)
      dut.io.memWriteReq.poke(true.B)
      dut.io.writeData.poke("h00000003".U)
      dut.clock.step(1)
      dut.io.writeAccepted.expect(true.B)

      // Readback SCHED_HINT
      dut.io.memWriteReq.poke(false.B)
      dut.io.memReadReq.poke(true.B)
      dut.clock.step(1)
      dut.io.readData.expect("h00000003".U)
      dut.io.schedHint.expect("h00000003".U)
    }
  }

  it should "protect read-only registers against writes" in {
    test(new SystemMMIO) { dut =>
      dut.io.memWidth.poke(MemWidth.WORD)
      dut.io.memWriteReq.poke(true.B)
      dut.io.writeData.poke("hFFFFFFFF".U)

      // Attempt write to RETIRED_COUNT
      dut.io.address.poke(MMIOAddress.RETIRED_COUNT)
      dut.clock.step(1)
      dut.io.writeAccepted.expect(false.B)

      // Attempt write to BRANCH_TAKEN_COUNT
      dut.io.address.poke(MMIOAddress.BRANCH_TAKEN_COUNT)
      dut.clock.step(1)
      dut.io.writeAccepted.expect(false.B)

      // Attempt write to DIV_BUSY_CYCLES
      dut.io.address.poke(MMIOAddress.DIV_BUSY_CYCLES)
      dut.clock.step(1)
      dut.io.writeAccepted.expect(false.B)

      // Attempt write to BRANCH_CONFIDENCE
      dut.io.address.poke(MMIOAddress.BRANCH_CONFIDENCE)
      dut.clock.step(1)
      dut.io.writeAccepted.expect(false.B)

      // Verify RETIRED_COUNT is still 0
      dut.io.memWriteReq.poke(false.B)
      dut.io.memReadReq.poke(true.B)
      dut.io.address.poke(MMIOAddress.RETIRED_COUNT)
      dut.clock.step(1)
      dut.io.readData.expect(0.U)
    }
  }

  it should "accurately accumulate hardware event counters" in {
    test(new SystemMMIO) { dut =>
      dut.io.memWidth.poke(MemWidth.WORD)
      dut.io.memReadReq.poke(true.B)

      // Pulse retirement event
      dut.io.retireEvent.poke(true.B)
      dut.io.commitPc.poke("h00001020".U)
      dut.clock.step(1)
      dut.io.retireEvent.poke(false.B)

      // Pulse branch taken and divider busy
      dut.io.branchTaken.poke(true.B)
      dut.io.dividerBusy.poke(true.B)
      dut.clock.step(1)
      dut.io.branchTaken.poke(false.B)
      dut.io.dividerBusy.poke(false.B)

      // Pulse load-use stall and pipeline stall
      dut.io.loadUseStall.poke(true.B)
      dut.io.pipelineStall.poke(true.B)
      dut.clock.step(1)
      dut.io.loadUseStall.poke(false.B)
      dut.io.pipelineStall.poke(false.B)

      // Verify counter readbacks
      dut.io.address.poke(MMIOAddress.RETIRED_COUNT)
      dut.clock.step(1)
      dut.io.readData.expect(1.U)

      dut.io.address.poke(MMIOAddress.LAST_COMMIT_PC)
      dut.clock.step(1)
      dut.io.readData.expect("h00001020".U)

      dut.io.address.poke(MMIOAddress.BRANCH_TAKEN_COUNT)
      dut.clock.step(1)
      dut.io.readData.expect(1.U)

      dut.io.address.poke(MMIOAddress.DIV_BUSY_CYCLES)
      dut.clock.step(1)
      dut.io.readData.expect(1.U)

      dut.io.address.poke(MMIOAddress.LOAD_USE_STALL_COUNT)
      dut.clock.step(1)
      dut.io.readData.expect(1.U)

      dut.io.address.poke(MMIOAddress.PIPELINE_STALL_COUNT)
      dut.clock.step(1)
      dut.io.readData.expect(1.U)
    }
  }

  it should "integrate Objective 1 TelemetryBlock at retirement" in {
    test(new SystemMMIO) { dut =>
      dut.io.memWidth.poke(MemWidth.WORD)
      dut.io.memReadReq.poke(true.B)

      // Check default EDP_CONFIG
      dut.io.address.poke(MMIOAddress.EDP_CONFIG)
      dut.clock.step(1)
      dut.io.readData.expect(1.U)

      // Feed arithmetic operation at retirement
      dut.io.telemetryValid.poke(true.B)
      dut.io.telemetryClaActive.poke(true.B)
      dut.io.telemetryMulActive.poke(false.B)
      dut.io.telemetryResult.poke("hAAAA5555".U)
      dut.clock.step(1)

      // Feed second arithmetic operation with known bit transitions
      dut.io.telemetryResult.poke("h5555AAAA".U)
      dut.clock.step(1)
      dut.io.telemetryValid.poke(false.B)

      // Read CLA_SWITCHING
      dut.io.address.poke(MMIOAddress.CLA_SWITCHING)
      dut.clock.step(1)
      dut.io.readAccepted.expect(true.B)
      val claSwitching = dut.io.readData.peek().litValue
      assert(claSwitching > 0, "CLA_SWITCHING should accumulate Hamming distance")
    }
  }

  it should "capture sticky first-event security violation and clear via W1C" in {
    test(new SystemMMIO) { dut =>
      dut.io.memWidth.poke(MemWidth.WORD)
      dut.io.memReadReq.poke(true.B)

      // Initial status: no pending violation
      dut.io.address.poke(MMIOAddress.SEC_STATUS)
      dut.clock.step(1)
      dut.io.readData.expect(0.U)

      // Inject first violation event
      dut.io.securityEvent.valid.poke(true.B)
      dut.io.securityEvent.pc.poke("h00000040".U)
      dut.io.securityEvent.address.poke("h80009999".U)
      dut.io.securityEvent.accessType.poke(AccessType.WRITE)
      dut.io.securityEvent.reason.poke(SecurityReason.BOUNDS)
      dut.io.securityEvent.context.poke("h00000007".U)
      dut.clock.step(1)
      dut.io.securityEvent.valid.poke(false.B)

      // Verify captured evidence
      dut.io.address.poke(MMIOAddress.SEC_STATUS)
      dut.clock.step(1)
      dut.io.readData.expect(1.U) // Pending = 1

      dut.io.address.poke(MMIOAddress.SEC_PC)
      dut.clock.step(1)
      dut.io.readData.expect("h00000040".U)

      dut.io.address.poke(MMIOAddress.SEC_ADDR)
      dut.clock.step(1)
      dut.io.readData.expect("h80009999".U)

      dut.io.address.poke(MMIOAddress.SEC_INFO)
      dut.clock.step(1)
      val expectedInfo = (1 << 4) | 2 // accessType = WRITE (1), reason = BOUNDS (2)
      dut.io.readData.expect(expectedInfo.U)

      dut.io.address.poke(MMIOAddress.SEC_CONTEXT)
      dut.clock.step(1)
      dut.io.readData.expect("h00000007".U)

      // Inject second violation while pending: should NOT overwrite first evidence
      dut.io.securityEvent.valid.poke(true.B)
      dut.io.securityEvent.pc.poke("h00000888".U)
      dut.io.securityEvent.address.poke("h00000999".U)
      dut.clock.step(1)
      dut.io.securityEvent.valid.poke(false.B)

      dut.io.address.poke(MMIOAddress.SEC_PC)
      dut.clock.step(1)
      dut.io.readData.expect("h00000040".U) // Still first PC!

      // Write 1 to clear SEC_STATUS
      dut.io.memReadReq.poke(false.B)
      dut.io.memWriteReq.poke(true.B)
      dut.io.address.poke(MMIOAddress.SEC_STATUS)
      dut.io.writeData.poke(1.U)
      dut.clock.step(1)

      // Verify cleared
      dut.io.memWriteReq.poke(false.B)
      dut.io.memReadReq.poke(true.B)
      dut.clock.step(1)
      dut.io.readData.expect(0.U)
    }
  }

  it should "prioritize new security violation arrival over simultaneous W1C clear when pending is 0" in {
    test(new SystemMMIO) { dut =>
      dut.io.memWidth.poke(MemWidth.WORD)

      // Simultaneous W1C write (clear) + incoming security event when pending=0
      dut.io.memWriteReq.poke(true.B)
      dut.io.address.poke(MMIOAddress.SEC_STATUS)
      dut.io.writeData.poke(1.U)

      dut.io.securityEvent.valid.poke(true.B)
      dut.io.securityEvent.pc.poke("h00000111".U)
      dut.io.securityEvent.address.poke("h80002222".U)
      dut.io.securityEvent.accessType.poke(AccessType.READ)
      dut.io.securityEvent.reason.poke(SecurityReason.READ_PERMISSION)
      dut.io.securityEvent.context.poke("h00000001".U)

      dut.clock.step(1)

      dut.io.memWriteReq.poke(false.B)
      dut.io.securityEvent.valid.poke(false.B)

      // Verify new event is captured and pending is 1
      dut.io.memReadReq.poke(true.B)
      dut.io.address.poke(MMIOAddress.SEC_STATUS)
      dut.clock.step(1)
      dut.io.readData.expect(1.U)

      dut.io.address.poke(MMIOAddress.SEC_PC)
      dut.clock.step(1)
      dut.io.readData.expect("h00000111".U)
    }
  }

  it should "replace old evidence with new violation when clear and new event arrive simultaneously while pending is 1" in {
    test(new SystemMMIO) { dut =>
      dut.io.memWidth.poke(MemWidth.WORD)

      // Step 1: Capture initial event
      dut.io.securityEvent.valid.poke(true.B)
      dut.io.securityEvent.pc.poke("h00000111".U)
      dut.io.securityEvent.address.poke("h80001111".U)
      dut.io.securityEvent.accessType.poke(AccessType.WRITE)
      dut.io.securityEvent.reason.poke(SecurityReason.BOUNDS)
      dut.io.securityEvent.context.poke("h00000002".U)
      dut.clock.step(1)
      dut.io.securityEvent.valid.poke(false.B)

      // Verify pending is 1 with first event
      dut.io.memReadReq.poke(true.B)
      dut.io.address.poke(MMIOAddress.SEC_PC)
      dut.clock.step(1)
      dut.io.readData.expect("h00000111".U)

      // Step 2: Simultaneously issue W1C clear AND new security violation event
      dut.io.memReadReq.poke(false.B)
      dut.io.memWriteReq.poke(true.B)
      dut.io.address.poke(MMIOAddress.SEC_STATUS)
      dut.io.writeData.poke(1.U)

      dut.io.securityEvent.valid.poke(true.B)
      dut.io.securityEvent.pc.poke("h00000333".U)
      dut.io.securityEvent.address.poke("h80004444".U)
      dut.io.securityEvent.accessType.poke(AccessType.EXECUTE)
      dut.io.securityEvent.reason.poke(SecurityReason.EXECUTE_PERMISSION)
      dut.io.securityEvent.context.poke("h00000005".U)
      dut.clock.step(1)

      dut.io.memWriteReq.poke(false.B)
      dut.io.securityEvent.valid.poke(false.B)

      // Verify new violation replaced old evidence and pending remains 1
      dut.io.memReadReq.poke(true.B)
      dut.io.address.poke(MMIOAddress.SEC_STATUS)
      dut.clock.step(1)
      dut.io.readData.expect(1.U)

      dut.io.address.poke(MMIOAddress.SEC_PC)
      dut.clock.step(1)
      dut.io.readData.expect("h00000333".U)

      dut.io.address.poke(MMIOAddress.SEC_ADDR)
      dut.clock.step(1)
      dut.io.readData.expect("h80004444".U)

      dut.io.address.poke(MMIOAddress.SEC_INFO)
      dut.clock.step(1)
      val expectedInfo = (2 << 4) | 5 // EXECUTE (2), EXECUTE_PERMISSION (5)
      dut.io.readData.expect(expectedInfo.U)
    }
  }

  it should "support Phase 8 Dedicated Trap MMIO registers and precise trap state transitions" in {
    test(new SystemMMIO) { dut =>
      dut.io.memWidth.poke(MemWidth.WORD)

      // 1. Initial State
      dut.io.trapEnable.expect(false.B)
      dut.io.trapActive.expect(false.B)
      dut.io.trapVector.expect("h00000800".U)

      // 2. Configure TRAP_VECTOR with misaligned value (must be masked with & 0xFFFFFFFC)
      dut.io.memWriteReq.poke(true.B)
      dut.io.address.poke(MMIOAddress.TRAP_VECTOR)
      dut.io.writeData.poke("h00000103".U) // unaligned address
      dut.clock.step(1)
      dut.io.writeAccepted.expect(true.B)
      dut.io.trapVector.expect("h00000100".U) // masked!

      // 3. Enable Traps via TRAP_CONTROL
      dut.io.address.poke(MMIOAddress.TRAP_CONTROL)
      dut.io.writeData.poke(1.U)
      dut.clock.step(1)
      dut.io.writeAccepted.expect(true.B)
      dut.io.trapEnable.expect(true.B)

      // 4. Fire Security Event -> Primary Precise Trap
      dut.io.memWriteReq.poke(false.B)
      dut.io.securityEvent.valid.poke(true.B)
      dut.io.securityEvent.pc.poke("h00000020".U)
      dut.io.securityEvent.address.poke("h00002000".U)
      dut.io.securityEvent.accessType.poke(AccessType.WRITE)
      dut.io.securityEvent.reason.poke(SecurityReason.BOUNDS)
      dut.io.securityEvent.context.poke("h0000000A".U)
      dut.clock.step(1)
      dut.io.securityEvent.valid.poke(false.B)

      // Verify Trap State Captured
      dut.io.trapActive.expect(true.B)
      dut.io.trapEpc.expect("h00000020".U)
      dut.io.trapCause.expect(((1 << 4) | 2).U) // WRITE (1), BOUNDS (2)
      dut.io.trapAddr.expect("h00002000".U)

      // 5. Nested Security Event while ACTIVE=1 -> DOUBLE_FAULT, no EPC overwrite
      dut.io.securityEvent.valid.poke(true.B)
      dut.io.securityEvent.pc.poke("h00000150".U)
      dut.io.securityEvent.address.poke("h00003000".U)
      dut.io.securityEvent.accessType.poke(AccessType.READ)
      dut.io.securityEvent.reason.poke(SecurityReason.READ_PERMISSION)
      dut.clock.step(1)
      dut.io.securityEvent.valid.poke(false.B)

      // Trap EPC and Addr must remain unchanged from primary trap
      dut.io.trapEpc.expect("h00000020".U)
      dut.io.trapAddr.expect("h00002000".U)

      // Read TRAP_STATUS: ACTIVE=1, DOUBLE_FAULT=1 -> 3.U
      dut.io.memReadReq.poke(true.B)
      dut.io.address.poke(MMIOAddress.TRAP_STATUS)
      dut.clock.step(1)
      dut.io.readData.expect(3.U)

      // 6. Test Double-Fault Priority over Simultaneous W1C Clear
      dut.io.memReadReq.poke(false.B)
      dut.io.memWriteReq.poke(true.B)
      dut.io.address.poke(MMIOAddress.TRAP_STATUS)
      dut.io.writeData.poke(2.U) // W1C clear on DOUBLE_FAULT bit

      dut.io.securityEvent.valid.poke(true.B) // simultaneous new nested fault
      dut.io.securityEvent.pc.poke("h00000160".U)
      dut.io.securityEvent.address.poke("h00004000".U)
      dut.io.securityEvent.accessType.poke(AccessType.WRITE)
      dut.io.securityEvent.reason.poke(SecurityReason.WRITE_PERMISSION)
      dut.clock.step(1)
      dut.io.securityEvent.valid.poke(false.B)

      // DOUBLE_FAULT must stay set!
      dut.io.memWriteReq.poke(false.B)
      dut.io.memReadReq.poke(true.B)
      dut.io.address.poke(MMIOAddress.TRAP_STATUS)
      dut.clock.step(1)
      dut.io.readData.expect(3.U)

      // 7. Handler writes updated TRAP_EPC (advance past fault)
      dut.io.memReadReq.poke(false.B)
      dut.io.memWriteReq.poke(true.B)
      dut.io.address.poke(MMIOAddress.TRAP_EPC)
      dut.io.writeData.poke("h00000027".U) // unaligned
      dut.clock.step(1)
      dut.io.trapEpc.expect("h00000024".U) // masked & 0xFFFFFFFC

      // 8. Handler executes TRAP_RETURN command
      dut.io.address.poke(MMIOAddress.TRAP_RETURN)
      dut.io.writeData.poke(1.U)
      dut.io.takeTrapReturn.expect(true.B)
      dut.clock.step(1)

      // ACTIVE is cleared, takeTrapReturn returns to false
      dut.io.memWriteReq.poke(false.B)
      dut.io.trapActive.expect(false.B)
      dut.io.takeTrapReturn.expect(false.B)
    }
  }
}
