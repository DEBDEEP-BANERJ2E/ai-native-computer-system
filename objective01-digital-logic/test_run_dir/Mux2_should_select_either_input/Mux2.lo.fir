FIRRTL version 1.2.0
circuit Mux2 :
  module Mux2 :
    input clock : Clock
    input reset : UInt<1>
    input io_a : UInt<8> @[src/main/scala/mux/Mux2.scala 8:14]
    input io_b : UInt<8> @[src/main/scala/mux/Mux2.scala 8:14]
    input io_select : UInt<1> @[src/main/scala/mux/Mux2.scala 8:14]
    output io_y : UInt<8> @[src/main/scala/mux/Mux2.scala 8:14]

    node _io_y_T = mux(io_select, io_b, io_a) @[src/main/scala/mux/Mux2.scala 15:14]
    io_y <= _io_y_T @[src/main/scala/mux/Mux2.scala 15:8]
