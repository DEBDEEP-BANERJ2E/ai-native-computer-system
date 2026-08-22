FIRRTL version 1.2.0
circuit Mux4 :
  module Mux4 :
    input clock : Clock
    input reset : UInt<1>
    input io_in0 : UInt<8> @[src/main/scala/mux/Mux4.scala 9:14]
    input io_in1 : UInt<8> @[src/main/scala/mux/Mux4.scala 9:14]
    input io_in2 : UInt<8> @[src/main/scala/mux/Mux4.scala 9:14]
    input io_in3 : UInt<8> @[src/main/scala/mux/Mux4.scala 9:14]
    input io_select : UInt<2> @[src/main/scala/mux/Mux4.scala 9:14]
    output io_y : UInt<8> @[src/main/scala/mux/Mux4.scala 9:14]

    node _io_y_T = eq(UInt<1>("h1"), io_select) @[src/main/scala/mux/Mux4.scala 18:39]
    node _io_y_T_1 = mux(_io_y_T, io_in1, io_in0) @[src/main/scala/mux/Mux4.scala 18:39]
    node _io_y_T_2 = eq(UInt<2>("h2"), io_select) @[src/main/scala/mux/Mux4.scala 18:39]
    node _io_y_T_3 = mux(_io_y_T_2, io_in2, _io_y_T_1) @[src/main/scala/mux/Mux4.scala 18:39]
    node _io_y_T_4 = eq(UInt<2>("h3"), io_select) @[src/main/scala/mux/Mux4.scala 18:39]
    node _io_y_T_5 = mux(_io_y_T_4, io_in3, _io_y_T_3) @[src/main/scala/mux/Mux4.scala 18:39]
    io_y <= _io_y_T_5 @[src/main/scala/mux/Mux4.scala 18:8]
