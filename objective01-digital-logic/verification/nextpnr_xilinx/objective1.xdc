# nextpnr-Xilinx accepts this XDC for the registered benchmark wrappers.
# The wrappers expose a clock port but no board pin is assigned.
create_clock -period 10.000 [get_ports clock]