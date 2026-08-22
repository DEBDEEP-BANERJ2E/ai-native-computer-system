FIRRTL version 1.2.0
circuit RippleCarryAdder :
  module FullAdder :
    input clock : Clock
    input reset : UInt<1>
    input io_a : UInt<1> @[src/main/scala/arithmetic/RippleCarryAdder.scala 6:14]
    input io_b : UInt<1> @[src/main/scala/arithmetic/RippleCarryAdder.scala 6:14]
    input io_carryIn : UInt<1> @[src/main/scala/arithmetic/RippleCarryAdder.scala 6:14]
    output io_sum : UInt<1> @[src/main/scala/arithmetic/RippleCarryAdder.scala 6:14]
    output io_carryOut : UInt<1> @[src/main/scala/arithmetic/RippleCarryAdder.scala 6:14]

    node _io_sum_T = xor(io_a, io_b) @[src/main/scala/arithmetic/RippleCarryAdder.scala 14:18]
    node _io_sum_T_1 = xor(_io_sum_T, io_carryIn) @[src/main/scala/arithmetic/RippleCarryAdder.scala 14:25]
    node _io_carryOut_T = and(io_a, io_b) @[src/main/scala/arithmetic/RippleCarryAdder.scala 15:24]
    node _io_carryOut_T_1 = xor(io_a, io_b) @[src/main/scala/arithmetic/RippleCarryAdder.scala 15:57]
    node _io_carryOut_T_2 = and(io_carryIn, _io_carryOut_T_1) @[src/main/scala/arithmetic/RippleCarryAdder.scala 15:48]
    node _io_carryOut_T_3 = or(_io_carryOut_T, _io_carryOut_T_2) @[src/main/scala/arithmetic/RippleCarryAdder.scala 15:33]
    io_sum <= _io_sum_T_1 @[src/main/scala/arithmetic/RippleCarryAdder.scala 14:10]
    io_carryOut <= _io_carryOut_T_3 @[src/main/scala/arithmetic/RippleCarryAdder.scala 15:15]

  module RippleCarryAdder :
    input clock : Clock
    input reset : UInt<1>
    input io_a : UInt<8> @[src/main/scala/arithmetic/RippleCarryAdder.scala 21:14]
    input io_b : UInt<8> @[src/main/scala/arithmetic/RippleCarryAdder.scala 21:14]
    input io_carryIn : UInt<1> @[src/main/scala/arithmetic/RippleCarryAdder.scala 21:14]
    output io_sum : UInt<8> @[src/main/scala/arithmetic/RippleCarryAdder.scala 21:14]
    output io_carryOut : UInt<1> @[src/main/scala/arithmetic/RippleCarryAdder.scala 21:14]

    inst fullAdder of FullAdder @[src/main/scala/arithmetic/RippleCarryAdder.scala 34:27]
    inst fullAdder_1 of FullAdder @[src/main/scala/arithmetic/RippleCarryAdder.scala 34:27]
    inst fullAdder_2 of FullAdder @[src/main/scala/arithmetic/RippleCarryAdder.scala 34:27]
    inst fullAdder_3 of FullAdder @[src/main/scala/arithmetic/RippleCarryAdder.scala 34:27]
    inst fullAdder_4 of FullAdder @[src/main/scala/arithmetic/RippleCarryAdder.scala 34:27]
    inst fullAdder_5 of FullAdder @[src/main/scala/arithmetic/RippleCarryAdder.scala 34:27]
    inst fullAdder_6 of FullAdder @[src/main/scala/arithmetic/RippleCarryAdder.scala 34:27]
    inst fullAdder_7 of FullAdder @[src/main/scala/arithmetic/RippleCarryAdder.scala 34:27]
    node _fullAdder_io_a_T = bits(io_a, 0, 0) @[src/main/scala/arithmetic/RippleCarryAdder.scala 35:27]
    node _fullAdder_io_b_T = bits(io_b, 0, 0) @[src/main/scala/arithmetic/RippleCarryAdder.scala 36:27]
    node _fullAdder_io_a_T_1 = bits(io_a, 1, 1) @[src/main/scala/arithmetic/RippleCarryAdder.scala 35:27]
    node _fullAdder_io_b_T_1 = bits(io_b, 1, 1) @[src/main/scala/arithmetic/RippleCarryAdder.scala 36:27]
    node _fullAdder_io_a_T_2 = bits(io_a, 2, 2) @[src/main/scala/arithmetic/RippleCarryAdder.scala 35:27]
    node _fullAdder_io_b_T_2 = bits(io_b, 2, 2) @[src/main/scala/arithmetic/RippleCarryAdder.scala 36:27]
    node _fullAdder_io_a_T_3 = bits(io_a, 3, 3) @[src/main/scala/arithmetic/RippleCarryAdder.scala 35:27]
    node _fullAdder_io_b_T_3 = bits(io_b, 3, 3) @[src/main/scala/arithmetic/RippleCarryAdder.scala 36:27]
    node _fullAdder_io_a_T_4 = bits(io_a, 4, 4) @[src/main/scala/arithmetic/RippleCarryAdder.scala 35:27]
    node _fullAdder_io_b_T_4 = bits(io_b, 4, 4) @[src/main/scala/arithmetic/RippleCarryAdder.scala 36:27]
    node _fullAdder_io_a_T_5 = bits(io_a, 5, 5) @[src/main/scala/arithmetic/RippleCarryAdder.scala 35:27]
    node _fullAdder_io_b_T_5 = bits(io_b, 5, 5) @[src/main/scala/arithmetic/RippleCarryAdder.scala 36:27]
    node _fullAdder_io_a_T_6 = bits(io_a, 6, 6) @[src/main/scala/arithmetic/RippleCarryAdder.scala 35:27]
    node _fullAdder_io_b_T_6 = bits(io_b, 6, 6) @[src/main/scala/arithmetic/RippleCarryAdder.scala 36:27]
    node _fullAdder_io_a_T_7 = bits(io_a, 7, 7) @[src/main/scala/arithmetic/RippleCarryAdder.scala 35:27]
    node _fullAdder_io_b_T_7 = bits(io_b, 7, 7) @[src/main/scala/arithmetic/RippleCarryAdder.scala 36:27]
    node sums_1 = fullAdder_1.io_sum @[src/main/scala/arithmetic/RippleCarryAdder.scala 30:18 38:15]
    node sums_0 = fullAdder.io_sum @[src/main/scala/arithmetic/RippleCarryAdder.scala 30:18 38:15]
    node io_sum_lo_lo = cat(sums_1, sums_0) @[src/main/scala/arithmetic/RippleCarryAdder.scala 42:18]
    node sums_3 = fullAdder_3.io_sum @[src/main/scala/arithmetic/RippleCarryAdder.scala 30:18 38:15]
    node sums_2 = fullAdder_2.io_sum @[src/main/scala/arithmetic/RippleCarryAdder.scala 30:18 38:15]
    node io_sum_lo_hi = cat(sums_3, sums_2) @[src/main/scala/arithmetic/RippleCarryAdder.scala 42:18]
    node io_sum_lo = cat(io_sum_lo_hi, io_sum_lo_lo) @[src/main/scala/arithmetic/RippleCarryAdder.scala 42:18]
    node sums_5 = fullAdder_5.io_sum @[src/main/scala/arithmetic/RippleCarryAdder.scala 30:18 38:15]
    node sums_4 = fullAdder_4.io_sum @[src/main/scala/arithmetic/RippleCarryAdder.scala 30:18 38:15]
    node io_sum_hi_lo = cat(sums_5, sums_4) @[src/main/scala/arithmetic/RippleCarryAdder.scala 42:18]
    node sums_7 = fullAdder_7.io_sum @[src/main/scala/arithmetic/RippleCarryAdder.scala 30:18 38:15]
    node sums_6 = fullAdder_6.io_sum @[src/main/scala/arithmetic/RippleCarryAdder.scala 30:18 38:15]
    node io_sum_hi_hi = cat(sums_7, sums_6) @[src/main/scala/arithmetic/RippleCarryAdder.scala 42:18]
    node io_sum_hi = cat(io_sum_hi_hi, io_sum_hi_lo) @[src/main/scala/arithmetic/RippleCarryAdder.scala 42:18]
    node _io_sum_T = cat(io_sum_hi, io_sum_lo) @[src/main/scala/arithmetic/RippleCarryAdder.scala 42:18]
    node carries_0 = io_carryIn @[src/main/scala/arithmetic/RippleCarryAdder.scala 29:21 31:14]
    node carries_1 = fullAdder.io_carryOut @[src/main/scala/arithmetic/RippleCarryAdder.scala 29:21 39:22]
    node carries_2 = fullAdder_1.io_carryOut @[src/main/scala/arithmetic/RippleCarryAdder.scala 29:21 39:22]
    node carries_3 = fullAdder_2.io_carryOut @[src/main/scala/arithmetic/RippleCarryAdder.scala 29:21 39:22]
    node carries_4 = fullAdder_3.io_carryOut @[src/main/scala/arithmetic/RippleCarryAdder.scala 29:21 39:22]
    node carries_5 = fullAdder_4.io_carryOut @[src/main/scala/arithmetic/RippleCarryAdder.scala 29:21 39:22]
    node carries_6 = fullAdder_5.io_carryOut @[src/main/scala/arithmetic/RippleCarryAdder.scala 29:21 39:22]
    node carries_7 = fullAdder_6.io_carryOut @[src/main/scala/arithmetic/RippleCarryAdder.scala 29:21 39:22]
    node carries_8 = fullAdder_7.io_carryOut @[src/main/scala/arithmetic/RippleCarryAdder.scala 29:21 39:22]
    io_sum <= _io_sum_T @[src/main/scala/arithmetic/RippleCarryAdder.scala 42:10]
    io_carryOut <= carries_8 @[src/main/scala/arithmetic/RippleCarryAdder.scala 43:15]
    fullAdder.clock <= clock
    fullAdder.reset <= reset
    fullAdder.io_a <= _fullAdder_io_a_T @[src/main/scala/arithmetic/RippleCarryAdder.scala 35:20]
    fullAdder.io_b <= _fullAdder_io_b_T @[src/main/scala/arithmetic/RippleCarryAdder.scala 36:20]
    fullAdder.io_carryIn <= carries_0 @[src/main/scala/arithmetic/RippleCarryAdder.scala 37:26]
    fullAdder_1.clock <= clock
    fullAdder_1.reset <= reset
    fullAdder_1.io_a <= _fullAdder_io_a_T_1 @[src/main/scala/arithmetic/RippleCarryAdder.scala 35:20]
    fullAdder_1.io_b <= _fullAdder_io_b_T_1 @[src/main/scala/arithmetic/RippleCarryAdder.scala 36:20]
    fullAdder_1.io_carryIn <= carries_1 @[src/main/scala/arithmetic/RippleCarryAdder.scala 37:26]
    fullAdder_2.clock <= clock
    fullAdder_2.reset <= reset
    fullAdder_2.io_a <= _fullAdder_io_a_T_2 @[src/main/scala/arithmetic/RippleCarryAdder.scala 35:20]
    fullAdder_2.io_b <= _fullAdder_io_b_T_2 @[src/main/scala/arithmetic/RippleCarryAdder.scala 36:20]
    fullAdder_2.io_carryIn <= carries_2 @[src/main/scala/arithmetic/RippleCarryAdder.scala 37:26]
    fullAdder_3.clock <= clock
    fullAdder_3.reset <= reset
    fullAdder_3.io_a <= _fullAdder_io_a_T_3 @[src/main/scala/arithmetic/RippleCarryAdder.scala 35:20]
    fullAdder_3.io_b <= _fullAdder_io_b_T_3 @[src/main/scala/arithmetic/RippleCarryAdder.scala 36:20]
    fullAdder_3.io_carryIn <= carries_3 @[src/main/scala/arithmetic/RippleCarryAdder.scala 37:26]
    fullAdder_4.clock <= clock
    fullAdder_4.reset <= reset
    fullAdder_4.io_a <= _fullAdder_io_a_T_4 @[src/main/scala/arithmetic/RippleCarryAdder.scala 35:20]
    fullAdder_4.io_b <= _fullAdder_io_b_T_4 @[src/main/scala/arithmetic/RippleCarryAdder.scala 36:20]
    fullAdder_4.io_carryIn <= carries_4 @[src/main/scala/arithmetic/RippleCarryAdder.scala 37:26]
    fullAdder_5.clock <= clock
    fullAdder_5.reset <= reset
    fullAdder_5.io_a <= _fullAdder_io_a_T_5 @[src/main/scala/arithmetic/RippleCarryAdder.scala 35:20]
    fullAdder_5.io_b <= _fullAdder_io_b_T_5 @[src/main/scala/arithmetic/RippleCarryAdder.scala 36:20]
    fullAdder_5.io_carryIn <= carries_5 @[src/main/scala/arithmetic/RippleCarryAdder.scala 37:26]
    fullAdder_6.clock <= clock
    fullAdder_6.reset <= reset
    fullAdder_6.io_a <= _fullAdder_io_a_T_6 @[src/main/scala/arithmetic/RippleCarryAdder.scala 35:20]
    fullAdder_6.io_b <= _fullAdder_io_b_T_6 @[src/main/scala/arithmetic/RippleCarryAdder.scala 36:20]
    fullAdder_6.io_carryIn <= carries_6 @[src/main/scala/arithmetic/RippleCarryAdder.scala 37:26]
    fullAdder_7.clock <= clock
    fullAdder_7.reset <= reset
    fullAdder_7.io_a <= _fullAdder_io_a_T_7 @[src/main/scala/arithmetic/RippleCarryAdder.scala 35:20]
    fullAdder_7.io_b <= _fullAdder_io_b_T_7 @[src/main/scala/arithmetic/RippleCarryAdder.scala 36:20]
    fullAdder_7.io_carryIn <= carries_7 @[src/main/scala/arithmetic/RippleCarryAdder.scala 37:26]
