package verification

import chisel3._
import chiseltest._
import chiseltest.RawTester.test
import objective02.decode._
import scala.io.Source
import java.io.File

object DecoderOracle {
  def main(args: Array[String]): Unit = {
    val vectorFilePath = if (args.nonEmpty) args(0) else "test_vectors.csv"
    val vectorFile = new File(vectorFilePath)
    if (!vectorFile.exists()) {
      println(s"Error: Vector file ${vectorFilePath} does not exist.")
      sys.exit(1)
    }

    println("==================================================================")
    println("  FROZEN OBJECTIVE-02 RTL DIFFERENTIAL DECODER ORACLE")
    println(s"  Instantiating Decoder(enableFullM = true, enableCapabilities = true)")
    println(s"  Reading test vectors from ${vectorFilePath}...")
    println("==================================================================")

    val lines = Source.fromFile(vectorFile).getLines().filter(_.trim.nonEmpty).toList
    val totalCount = lines.size
    var passCount = 0
    var mismatchCount = 0

    test(new Decoder(enableFullM = true, enableCapabilities = true)) { dut =>
      for ((line, idx) <- lines.zipWithIndex) {
        // Format: word_hex,rd,rs1,rs2,imm_hex,aluOp,aluSrcA,aluSrcB,regWrite,memRead,memWrite,memWidth,branchType,jumpType,wbSource,isMul,mOp,isSecurityOp,isCapOp,capOp,isCapMem,capRegWrite,usesCapRs1,usesIntRs1,usesIntRs2,illegalInstruction
        val tokens = line.split(",")
        if (tokens.length >= 26) {
          val wordHex = tokens(0).trim
          val word = java.lang.Long.parseLong(wordHex.replace("0x", "").replace("0X", ""), 16)

          val exp_rd = tokens(1).toInt
          val exp_rs1 = tokens(2).toInt
          val exp_rs2 = tokens(3).toInt
          val exp_imm = java.lang.Long.parseLong(tokens(4).replace("0x", "").replace("0X", ""), 16) & 0xFFFFFFFFL
          val exp_aluOp = tokens(5).toInt
          val exp_aluSrcA = tokens(6).toInt
          val exp_aluSrcB = tokens(7).toInt
          val exp_regWrite = tokens(8).toBoolean
          val exp_memRead = tokens(9).toBoolean
          val exp_memWrite = tokens(10).toBoolean
          val exp_memWidth = tokens(11).toInt
          val exp_branchType = tokens(12).toInt
          val exp_jumpType = tokens(13).toInt
          val exp_wbSource = tokens(14).toInt
          val exp_isMul = tokens(15).toBoolean
          val exp_mOp = tokens(16).toInt
          val exp_isSecurityOp = tokens(17).toBoolean
          val exp_isCapOp = tokens(18).toBoolean
          val exp_capOp = tokens(19).toInt
          val exp_isCapMem = tokens(20).toBoolean
          val exp_capRegWrite = tokens(21).toBoolean
          val exp_usesCapRs1 = tokens(22).toBoolean
          val exp_usesIntRs1 = tokens(23).toBoolean
          val exp_usesIntRs2 = tokens(24).toBoolean
          val exp_illegal = tokens(25).toBoolean

          // Poke instruction into frozen RTL decoder
          dut.io.instruction.poke(word.U(32.W))

          // Peek all hardware outputs
          val rtl_rd = dut.io.rd.peek().litValue.toInt
          val rtl_rs1 = dut.io.rs1.peek().litValue.toInt
          val rtl_rs2 = dut.io.rs2.peek().litValue.toInt
          val rtl_imm = dut.io.imm.peek().litValue.toLong & 0xFFFFFFFFL

          val rtl_aluOp = dut.io.controls.aluOp.peek().litValue.toInt
          val rtl_aluSrcA = dut.io.controls.aluSrcA.peek().litValue.toInt
          val rtl_aluSrcB = dut.io.controls.aluSrcB.peek().litValue.toInt
          val rtl_regWrite = dut.io.controls.regWrite.peek().litToBoolean
          val rtl_memRead = dut.io.controls.memRead.peek().litToBoolean
          val rtl_memWrite = dut.io.controls.memWrite.peek().litToBoolean
          val rtl_memWidth = dut.io.controls.memWidth.peek().litValue.toInt
          val rtl_branchType = dut.io.controls.branchType.peek().litValue.toInt
          val rtl_jumpType = dut.io.controls.jumpType.peek().litValue.toInt
          val rtl_wbSource = dut.io.controls.wbSource.peek().litValue.toInt
          val rtl_isMul = dut.io.controls.isMul.peek().litToBoolean
          val rtl_mOp = dut.io.controls.mOp.peek().litValue.toInt
          val rtl_isSecurityOp = dut.io.controls.isSecurityOp.peek().litToBoolean
          val rtl_isCapOp = dut.io.controls.isCapOp.peek().litToBoolean
          val rtl_capOp = dut.io.controls.capOp.peek().litValue.toInt
          val rtl_isCapMem = dut.io.controls.isCapMem.peek().litToBoolean
          val rtl_capRegWrite = dut.io.controls.capRegWrite.peek().litToBoolean
          val rtl_usesCapRs1 = dut.io.controls.usesCapRs1.peek().litToBoolean
          val rtl_usesIntRs1 = dut.io.controls.usesIntRs1.peek().litToBoolean
          val rtl_usesIntRs2 = dut.io.controls.usesIntRs2.peek().litToBoolean
          val rtl_illegal = dut.io.controls.illegalInstruction.peek().litToBoolean

          var error = false
          if (rtl_illegal != exp_illegal) {
            println(f"MISMATCH at vector $idx%d (0x$word%08X): illegalInstruction RTL=$rtl_illegal vs C++=$exp_illegal")
            error = true
          }
          if (rtl_rd != exp_rd) {
            println(f"MISMATCH at vector $idx%d (0x$word%08X): rd RTL=$rtl_rd vs C++=$exp_rd")
            error = true
          }
          if (rtl_rs1 != exp_rs1) {
            println(f"MISMATCH at vector $idx%d (0x$word%08X): rs1 RTL=$rtl_rs1 vs C++=$exp_rs1")
            error = true
          }
          if (rtl_rs2 != exp_rs2) {
            println(f"MISMATCH at vector $idx%d (0x$word%08X): rs2 RTL=$rtl_rs2 vs C++=$exp_rs2")
            error = true
          }
          if (rtl_imm != exp_imm) {
            println(f"MISMATCH at vector $idx%d (0x$word%08X): imm RTL=0x$rtl_imm%08X vs C++=0x$exp_imm%08X")
            error = true
          }

          if (!rtl_illegal && !exp_illegal) {
            if (rtl_aluOp != exp_aluOp) { println(f"MISMATCH at vector $idx%d (0x$word%08X): aluOp RTL=$rtl_aluOp vs C++=$exp_aluOp"); error = true }
            if (rtl_aluSrcA != exp_aluSrcA) { println(f"MISMATCH at vector $idx%d (0x$word%08X): aluSrcA RTL=$rtl_aluSrcA vs C++=$exp_aluSrcA"); error = true }
            if (rtl_aluSrcB != exp_aluSrcB) { println(f"MISMATCH at vector $idx%d (0x$word%08X): aluSrcB RTL=$rtl_aluSrcB vs C++=$exp_aluSrcB"); error = true }
            if (rtl_regWrite != exp_regWrite) { println(f"MISMATCH at vector $idx%d (0x$word%08X): regWrite RTL=$rtl_regWrite vs C++=$exp_regWrite"); error = true }
            if (rtl_memRead != exp_memRead) { println(f"MISMATCH at vector $idx%d (0x$word%08X): memRead RTL=$rtl_memRead vs C++=$exp_memRead"); error = true }
            if (rtl_memWrite != exp_memWrite) { println(f"MISMATCH at vector $idx%d (0x$word%08X): memWrite RTL=$rtl_memWrite vs C++=$exp_memWrite"); error = true }
            if (rtl_memWidth != exp_memWidth) { println(f"MISMATCH at vector $idx%d (0x$word%08X): memWidth RTL=$rtl_memWidth vs C++=$exp_memWidth"); error = true }
            if (rtl_branchType != exp_branchType) { println(f"MISMATCH at vector $idx%d (0x$word%08X): branchType RTL=$rtl_branchType vs C++=$exp_branchType"); error = true }
            if (rtl_jumpType != exp_jumpType) { println(f"MISMATCH at vector $idx%d (0x$word%08X): jumpType RTL=$rtl_jumpType vs C++=$exp_jumpType"); error = true }
            if (rtl_wbSource != exp_wbSource) { println(f"MISMATCH at vector $idx%d (0x$word%08X): wbSource RTL=$rtl_wbSource vs C++=$exp_wbSource"); error = true }
            if (rtl_isMul != exp_isMul) { println(f"MISMATCH at vector $idx%d (0x$word%08X): isMul RTL=$rtl_isMul vs C++=$exp_isMul"); error = true }
            if (rtl_mOp != exp_mOp) { println(f"MISMATCH at vector $idx%d (0x$word%08X): mOp RTL=$rtl_mOp vs C++=$exp_mOp"); error = true }
            if (rtl_isSecurityOp != exp_isSecurityOp) { println(f"MISMATCH at vector $idx%d (0x$word%08X): isSecurityOp RTL=$rtl_isSecurityOp vs C++=$exp_isSecurityOp"); error = true }
            if (rtl_isCapOp != exp_isCapOp) { println(f"MISMATCH at vector $idx%d (0x$word%08X): isCapOp RTL=$rtl_isCapOp vs C++=$exp_isCapOp"); error = true }
            if (rtl_capOp != exp_capOp) { println(f"MISMATCH at vector $idx%d (0x$word%08X): capOp RTL=$rtl_capOp vs C++=$exp_capOp"); error = true }
            if (rtl_isCapMem != exp_isCapMem) { println(f"MISMATCH at vector $idx%d (0x$word%08X): isCapMem RTL=$rtl_isCapMem vs C++=$exp_isCapMem"); error = true }
            if (rtl_capRegWrite != exp_capRegWrite) { println(f"MISMATCH at vector $idx%d (0x$word%08X): capRegWrite RTL=$rtl_capRegWrite vs C++=$exp_capRegWrite"); error = true }
            if (rtl_usesCapRs1 != exp_usesCapRs1) { println(f"MISMATCH at vector $idx%d (0x$word%08X): usesCapRs1 RTL=$rtl_usesCapRs1 vs C++=$exp_usesCapRs1"); error = true }
            if (rtl_usesIntRs1 != exp_usesIntRs1) { println(f"MISMATCH at vector $idx%d (0x$word%08X): usesIntRs1 RTL=$rtl_usesIntRs1 vs C++=$exp_usesIntRs1"); error = true }
            if (rtl_usesIntRs2 != exp_usesIntRs2) { println(f"MISMATCH at vector $idx%d (0x$word%08X): usesIntRs2 RTL=$rtl_usesIntRs2 vs C++=$exp_usesIntRs2"); error = true }
          }

          if (error) {
            mismatchCount += 1
            if (mismatchCount > 10) {
              println("Too many mismatches. Aborting.")
              sys.exit(1)
            }
          } else {
            passCount += 1
          }
        }
      }
    }

    println("==================================================================")
    if (mismatchCount == 0) {
      println(s"  DIFFERENTIAL VERIFICATION SUCCESSFUL: ${passCount}/${totalCount} VECTORS MATCHED (100% BIT-EXACT)")
      println("==================================================================")
    } else {
      println(s"  DIFFERENTIAL VERIFICATION FAILED: ${mismatchCount} MISMATCHES FOUND")
      println("==================================================================")
      sys.exit(1)
    }
  }
}
