FIRRTL version 1.2.0
circuit Shifter :
  module Shifter :
    input clock : Clock
    input reset : UInt<1>
    input io_input : UInt<8> @[src/main/scala/datapath/Shifter.scala 16:14]
    input io_amount : UInt<3> @[src/main/scala/datapath/Shifter.scala 16:14]
    input io_operation : UInt<2> @[src/main/scala/datapath/Shifter.scala 16:14]
    output io_output : UInt<8> @[src/main/scala/datapath/Shifter.scala 16:14]

    node _io_output_T = dshl(io_input, io_amount) @[src/main/scala/datapath/Shifter.scala 24:38]
    node _io_output_T_1 = dshr(io_input, io_amount) @[src/main/scala/datapath/Shifter.scala 25:47]
    node _io_output_T_2 = asSInt(io_input) @[src/main/scala/datapath/Shifter.scala 26:50]
    node _io_output_T_3 = dshr(_io_output_T_2, io_amount) @[src/main/scala/datapath/Shifter.scala 26:57]
    node _io_output_T_4 = asUInt(_io_output_T_3) @[src/main/scala/datapath/Shifter.scala 26:71]
    node _io_output_T_5 = eq(UInt<2>("h0"), io_operation) @[src/main/scala/datapath/Shifter.scala 23:53]
    node _io_output_T_6 = mux(_io_output_T_5, _io_output_T, UInt<8>("h0")) @[src/main/scala/datapath/Shifter.scala 23:53]
    node _io_output_T_7 = eq(UInt<2>("h1"), io_operation) @[src/main/scala/datapath/Shifter.scala 23:53]
    node _io_output_T_8 = mux(_io_output_T_7, _io_output_T_1, _io_output_T_6) @[src/main/scala/datapath/Shifter.scala 23:53]
    node _io_output_T_9 = eq(UInt<2>("h2"), io_operation) @[src/main/scala/datapath/Shifter.scala 23:53]
    node _io_output_T_10 = mux(_io_output_T_9, _io_output_T_4, _io_output_T_8) @[src/main/scala/datapath/Shifter.scala 23:53]
    io_output <= bits(_io_output_T_10, 7, 0) @[src/main/scala/datapath/Shifter.scala 23:13]
