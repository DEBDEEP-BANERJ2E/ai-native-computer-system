FIRRTL version 1.2.0
circuit WallaceTree :
  module CarrySaveCompressor :
    input clock : Clock
    input reset : UInt<1>
    input io_a : UInt<16> @[src/main/scala/arithmetic/WallaceTree.scala 9:14]
    input io_b : UInt<16> @[src/main/scala/arithmetic/WallaceTree.scala 9:14]
    input io_c : UInt<16> @[src/main/scala/arithmetic/WallaceTree.scala 9:14]
    output io_sum : UInt<16> @[src/main/scala/arithmetic/WallaceTree.scala 9:14]
    output io_carry : UInt<16> @[src/main/scala/arithmetic/WallaceTree.scala 9:14]

    node _io_sum_T = xor(io_a, io_b) @[src/main/scala/arithmetic/WallaceTree.scala 17:18]
    node _io_sum_T_1 = xor(_io_sum_T, io_c) @[src/main/scala/arithmetic/WallaceTree.scala 17:25]
    node _io_carry_T = and(io_a, io_b) @[src/main/scala/arithmetic/WallaceTree.scala 18:22]
    node _io_carry_T_1 = and(io_a, io_c) @[src/main/scala/arithmetic/WallaceTree.scala 18:38]
    node _io_carry_T_2 = or(_io_carry_T, _io_carry_T_1) @[src/main/scala/arithmetic/WallaceTree.scala 18:30]
    node _io_carry_T_3 = and(io_b, io_c) @[src/main/scala/arithmetic/WallaceTree.scala 18:54]
    node _io_carry_T_4 = or(_io_carry_T_2, _io_carry_T_3) @[src/main/scala/arithmetic/WallaceTree.scala 18:46]
    node _io_carry_T_5 = shl(_io_carry_T_4, 1) @[src/main/scala/arithmetic/WallaceTree.scala 18:63]
    io_sum <= _io_sum_T_1 @[src/main/scala/arithmetic/WallaceTree.scala 17:10]
    io_carry <= bits(_io_carry_T_5, 15, 0) @[src/main/scala/arithmetic/WallaceTree.scala 18:12]

  module WallaceTree :
    input clock : Clock
    input reset : UInt<1>
    input io_inputs_0 : UInt<16> @[src/main/scala/arithmetic/WallaceTree.scala 26:14]
    input io_inputs_1 : UInt<16> @[src/main/scala/arithmetic/WallaceTree.scala 26:14]
    input io_inputs_2 : UInt<16> @[src/main/scala/arithmetic/WallaceTree.scala 26:14]
    input io_inputs_3 : UInt<16> @[src/main/scala/arithmetic/WallaceTree.scala 26:14]
    input io_inputs_4 : UInt<16> @[src/main/scala/arithmetic/WallaceTree.scala 26:14]
    input io_inputs_5 : UInt<16> @[src/main/scala/arithmetic/WallaceTree.scala 26:14]
    input io_inputs_6 : UInt<16> @[src/main/scala/arithmetic/WallaceTree.scala 26:14]
    input io_inputs_7 : UInt<16> @[src/main/scala/arithmetic/WallaceTree.scala 26:14]
    output io_result : UInt<19> @[src/main/scala/arithmetic/WallaceTree.scala 26:14]

    inst compressor of CarrySaveCompressor @[src/main/scala/arithmetic/WallaceTree.scala 36:30]
    inst compressor_1 of CarrySaveCompressor @[src/main/scala/arithmetic/WallaceTree.scala 36:30]
    inst compressor_2 of CarrySaveCompressor @[src/main/scala/arithmetic/WallaceTree.scala 36:30]
    inst compressor_3 of CarrySaveCompressor @[src/main/scala/arithmetic/WallaceTree.scala 36:30]
    inst compressor_4 of CarrySaveCompressor @[src/main/scala/arithmetic/WallaceTree.scala 36:30]
    inst compressor_5 of CarrySaveCompressor @[src/main/scala/arithmetic/WallaceTree.scala 36:30]
    node currentRows_0 = pad(io_inputs_0, 19) @[src/main/scala/arithmetic/WallaceTree.scala 31:51]
    node currentRows_1 = pad(io_inputs_1, 19) @[src/main/scala/arithmetic/WallaceTree.scala 31:51]
    node currentRows_2 = pad(io_inputs_2, 19) @[src/main/scala/arithmetic/WallaceTree.scala 31:51]
    node currentRows_3 = pad(io_inputs_3, 19) @[src/main/scala/arithmetic/WallaceTree.scala 31:51]
    node currentRows_4 = pad(io_inputs_4, 19) @[src/main/scala/arithmetic/WallaceTree.scala 31:51]
    node currentRows_5 = pad(io_inputs_5, 19) @[src/main/scala/arithmetic/WallaceTree.scala 31:51]
    node currentRows_6 = pad(io_inputs_6, 19) @[src/main/scala/arithmetic/WallaceTree.scala 31:51]
    node currentRows_7 = pad(io_inputs_7, 19) @[src/main/scala/arithmetic/WallaceTree.scala 31:51]
    node _io_result_T = add(compressor_5.io_sum, compressor_5.io_carry) @[src/main/scala/arithmetic/WallaceTree.scala 51:31]
    io_result <= pad(_io_result_T, 19) @[src/main/scala/arithmetic/WallaceTree.scala 51:13]
    compressor.clock <= clock
    compressor.reset <= reset
    compressor.io_a <= bits(currentRows_0, 15, 0) @[src/main/scala/arithmetic/WallaceTree.scala 37:23]
    compressor.io_b <= bits(currentRows_1, 15, 0) @[src/main/scala/arithmetic/WallaceTree.scala 38:23]
    compressor.io_c <= bits(currentRows_2, 15, 0) @[src/main/scala/arithmetic/WallaceTree.scala 39:23]
    compressor_1.clock <= clock
    compressor_1.reset <= reset
    compressor_1.io_a <= bits(currentRows_3, 15, 0) @[src/main/scala/arithmetic/WallaceTree.scala 37:23]
    compressor_1.io_b <= bits(currentRows_4, 15, 0) @[src/main/scala/arithmetic/WallaceTree.scala 38:23]
    compressor_1.io_c <= bits(currentRows_5, 15, 0) @[src/main/scala/arithmetic/WallaceTree.scala 39:23]
    compressor_2.clock <= clock
    compressor_2.reset <= reset
    compressor_2.io_a <= compressor.io_sum @[src/main/scala/arithmetic/WallaceTree.scala 37:23]
    compressor_2.io_b <= compressor.io_carry @[src/main/scala/arithmetic/WallaceTree.scala 38:23]
    compressor_2.io_c <= compressor_1.io_sum @[src/main/scala/arithmetic/WallaceTree.scala 39:23]
    compressor_3.clock <= clock
    compressor_3.reset <= reset
    compressor_3.io_a <= compressor_1.io_carry @[src/main/scala/arithmetic/WallaceTree.scala 37:23]
    compressor_3.io_b <= bits(currentRows_6, 15, 0) @[src/main/scala/arithmetic/WallaceTree.scala 38:23]
    compressor_3.io_c <= bits(currentRows_7, 15, 0) @[src/main/scala/arithmetic/WallaceTree.scala 39:23]
    compressor_4.clock <= clock
    compressor_4.reset <= reset
    compressor_4.io_a <= compressor_2.io_sum @[src/main/scala/arithmetic/WallaceTree.scala 37:23]
    compressor_4.io_b <= compressor_2.io_carry @[src/main/scala/arithmetic/WallaceTree.scala 38:23]
    compressor_4.io_c <= compressor_3.io_sum @[src/main/scala/arithmetic/WallaceTree.scala 39:23]
    compressor_5.clock <= clock
    compressor_5.reset <= reset
    compressor_5.io_a <= compressor_4.io_sum @[src/main/scala/arithmetic/WallaceTree.scala 37:23]
    compressor_5.io_b <= compressor_4.io_carry @[src/main/scala/arithmetic/WallaceTree.scala 38:23]
    compressor_5.io_c <= compressor_3.io_carry @[src/main/scala/arithmetic/WallaceTree.scala 39:23]
