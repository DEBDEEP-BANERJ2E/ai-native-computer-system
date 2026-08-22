set project_dir [file normalize [file join [file dirname [info script]] vivado-project]]
set rtl [file normalize [file join [file dirname [info script]] .. .. generated Objective1Subsystem.sv]]
set xdc [file normalize [file join [file dirname [info script]] objective1.xdc]]

file mkdir $project_dir
create_project objective1_vivado $project_dir -part xc7a100tcsg324-1 -force
add_files -norecurse $rtl
add_files -fileset constrs_1 -norecurse $xdc
set_property top Objective1Subsystem [current_fileset]
update_compile_order -fileset sources_1

synth_design -top Objective1Subsystem -part xc7a100tcsg324-1
report_utilization -file [file join $project_dir utilization.rpt]
report_timing_summary -file [file join $project_dir timing_summary.rpt]
report_power -file [file join $project_dir power.rpt]

puts "Objective 1 Vivado reports written to $project_dir"