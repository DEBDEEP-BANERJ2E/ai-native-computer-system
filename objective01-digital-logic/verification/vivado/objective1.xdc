# Replace the clock pin/package constraint with the actual target board clock.
create_clock -name sys_clk -period 10.000 [get_ports clock]
set_property CLOCK_DEDICATED_ROUTE FALSE [get_nets clock]