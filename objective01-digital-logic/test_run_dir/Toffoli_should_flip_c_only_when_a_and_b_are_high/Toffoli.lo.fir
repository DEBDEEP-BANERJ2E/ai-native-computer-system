FIRRTL version 1.2.0
circuit Toffoli :
  module Toffoli :
    input clock : Clock
    input reset : UInt<1>
    input io_a : UInt<1> @[src/main/scala/gates/Toffoli.scala 6:14]
    input io_b : UInt<1> @[src/main/scala/gates/Toffoli.scala 6:14]
    input io_c : UInt<1> @[src/main/scala/gates/Toffoli.scala 6:14]
    output io_p : UInt<1> @[src/main/scala/gates/Toffoli.scala 6:14]
    output io_q : UInt<1> @[src/main/scala/gates/Toffoli.scala 6:14]
    output io_r : UInt<1> @[src/main/scala/gates/Toffoli.scala 6:14]

    node _io_r_T = and(io_a, io_b) @[src/main/scala/gates/Toffoli.scala 17:24]
    node _io_r_T_1 = xor(io_c, _io_r_T) @[src/main/scala/gates/Toffoli.scala 17:16]
    io_p <= io_a @[src/main/scala/gates/Toffoli.scala 15:8]
    io_q <= io_b @[src/main/scala/gates/Toffoli.scala 16:8]
    io_r <= _io_r_T_1 @[src/main/scala/gates/Toffoli.scala 17:8]
