FIRRTL version 1.2.0
circuit CarrySaveCompressor :
  module CarrySaveCompressor :
    input clock : Clock
    input reset : UInt<1>
    input io_a : UInt<9> @[src/main/scala/arithmetic/WallaceTree.scala 9:14]
    input io_b : UInt<9> @[src/main/scala/arithmetic/WallaceTree.scala 9:14]
    input io_c : UInt<9> @[src/main/scala/arithmetic/WallaceTree.scala 9:14]
    output io_sum : UInt<9> @[src/main/scala/arithmetic/WallaceTree.scala 9:14]
    output io_carry : UInt<9> @[src/main/scala/arithmetic/WallaceTree.scala 9:14]

    node _io_sum_T = xor(io_a, io_b) @[src/main/scala/arithmetic/WallaceTree.scala 17:18]
    node _io_sum_T_1 = xor(_io_sum_T, io_c) @[src/main/scala/arithmetic/WallaceTree.scala 17:25]
    node _io_carry_T = and(io_a, io_b) @[src/main/scala/arithmetic/WallaceTree.scala 18:22]
    node _io_carry_T_1 = and(io_a, io_c) @[src/main/scala/arithmetic/WallaceTree.scala 18:38]
    node _io_carry_T_2 = or(_io_carry_T, _io_carry_T_1) @[src/main/scala/arithmetic/WallaceTree.scala 18:30]
    node _io_carry_T_3 = and(io_b, io_c) @[src/main/scala/arithmetic/WallaceTree.scala 18:54]
    node _io_carry_T_4 = or(_io_carry_T_2, _io_carry_T_3) @[src/main/scala/arithmetic/WallaceTree.scala 18:46]
    node _io_carry_T_5 = shl(_io_carry_T_4, 1) @[src/main/scala/arithmetic/WallaceTree.scala 18:63]
    io_sum <= _io_sum_T_1 @[src/main/scala/arithmetic/WallaceTree.scala 17:10]
    io_carry <= bits(_io_carry_T_5, 8, 0) @[src/main/scala/arithmetic/WallaceTree.scala 18:12]
