FIRRTL version 1.2.0
circuit Register :
  module Register :
    input clock : Clock
    input reset : UInt<1>
    input io_enable : UInt<1> @[src/main/scala/datapath/Register.scala 8:14]
    input io_d : UInt<8> @[src/main/scala/datapath/Register.scala 8:14]
    output io_q : UInt<8> @[src/main/scala/datapath/Register.scala 8:14]

    reg value : UInt<8>, clock with :
      reset => (UInt<1>("h0"), value) @[src/main/scala/datapath/Register.scala 14:22]
    node _GEN_0 = mux(io_enable, io_d, value) @[src/main/scala/datapath/Register.scala 15:20 16:11 14:22]
    io_q <= value @[src/main/scala/datapath/Register.scala 18:8]
    value <= mux(reset, UInt<8>("h0"), _GEN_0) @[src/main/scala/datapath/Register.scala 14:{22,22}]
