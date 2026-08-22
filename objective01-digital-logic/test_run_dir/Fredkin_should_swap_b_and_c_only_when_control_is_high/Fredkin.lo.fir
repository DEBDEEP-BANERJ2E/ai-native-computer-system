FIRRTL version 1.2.0
circuit Fredkin :
  module Fredkin :
    input clock : Clock
    input reset : UInt<1>
    input io_control : UInt<1> @[src/main/scala/gates/Fredkin.scala 6:14]
    input io_b : UInt<1> @[src/main/scala/gates/Fredkin.scala 6:14]
    input io_c : UInt<1> @[src/main/scala/gates/Fredkin.scala 6:14]
    output io_p : UInt<1> @[src/main/scala/gates/Fredkin.scala 6:14]
    output io_q : UInt<1> @[src/main/scala/gates/Fredkin.scala 6:14]
    output io_r : UInt<1> @[src/main/scala/gates/Fredkin.scala 6:14]

    node _io_q_T = mux(io_control, io_c, io_b) @[src/main/scala/gates/Fredkin.scala 16:14]
    node _io_r_T = mux(io_control, io_b, io_c) @[src/main/scala/gates/Fredkin.scala 17:14]
    io_p <= io_control @[src/main/scala/gates/Fredkin.scala 15:8]
    io_q <= _io_q_T @[src/main/scala/gates/Fredkin.scala 16:8]
    io_r <= _io_r_T @[src/main/scala/gates/Fredkin.scala 17:8]
