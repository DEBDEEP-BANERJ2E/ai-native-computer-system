# nextpnr-Xilinx / Project X-Ray Flow

This directory contains the open-source replacement for the optional Vivado
stage. Yosys creates an XC7 JSON netlist, then `nextpnr-xilinx` places and
routes it using a Project X-Ray-derived chip database.

The flow requires:

- `nextpnr-xilinx`
- an XC7 chip database file for the exact target part
- the Project X-Ray database/environment used to produce that chip database

The current macOS workspace does not have these installed. The flow therefore
does not claim timing, Fmax, or power results until it runs on a machine with
the matching database.