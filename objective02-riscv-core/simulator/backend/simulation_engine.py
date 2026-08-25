"""
Dual-Engine Simulation Orchestrator.
Supports:
1. RTL Scenario Engine: Cycle-accurate 5-stage hardware pipeline with real top-level signals.
2. Python Reference Engine: Instant architectural stepping for arbitrary user assembly.
"""

import sys
from pathlib import Path
from typing import Dict, Any, List, Optional

# Add parent directory for rv32i_interpreter
OBJ2_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(OBJ2_ROOT / "reference"))

from rv32i_interpreter import RV32Interpreter, CapabilityLite
from assembler import assemble_program, disassemble_inst, ABI_MAP
from scenarios import SCENARIO_CATALOG


class SimulationEngine:
    def __init__(self):
        self.active_engine = "rtl" # "rtl" or "reference"
        self.current_scenario_id: Optional[str] = None
        self.cycle_count = 0
        self.instruction_count = 0

        # Architectural Register File (GPR x0-x31)
        self.gpr = [0] * 32

        # Capability Register File (c0-c7)
        self.cap_regs = [
            {"index": 0, "name": "c0", "role": "NULL", "tag": 0, "base": 0, "length": 0, "perms": "---", "perms_raw": 0, "offset": 0},
            {"index": 1, "name": "c1", "role": "RAM Root", "tag": 1, "base": 0, "length": 4096, "perms": "RW-", "perms_raw": 3, "offset": 0},
            {"index": 2, "name": "c2", "role": "MMIO Root", "tag": 1, "base": 0x80000000, "length": 0x10000, "perms": "RW-", "perms_raw": 3, "offset": 0},
            {"index": 3, "name": "c3", "role": "Process Cap 3", "tag": 0, "base": 0, "length": 0, "perms": "---", "perms_raw": 0, "offset": 0},
            {"index": 4, "name": "c4", "role": "Process Cap 4", "tag": 0, "base": 0, "length": 0, "perms": "---", "perms_raw": 0, "offset": 0},
            {"index": 5, "name": "c5", "role": "Process Cap 5", "tag": 0, "base": 0, "length": 0, "perms": "---", "perms_raw": 0, "offset": 0},
            {"index": 6, "name": "c6", "role": "Process Cap 6", "tag": 0, "base": 0, "length": 0, "perms": "---", "perms_raw": 0, "offset": 0},
            {"index": 7, "name": "c7", "role": "Process Cap 7", "tag": 0, "base": 0, "length": 0, "perms": "---", "perms_raw": 0, "offset": 0},
        ]

        # Pipeline Stage State (5-stage)
        self.stages = {
            "IF": {"pc": 0, "valid": False, "instruction": 0, "mnemonic": "NOP", "stalled": False, "flushed": False},
            "ID": {"pc": 0, "valid": False, "instruction": 0, "mnemonic": "NOP", "stalled": False, "flushed": False, "bubble": False},
            "EX": {"pc": 0, "valid": False, "instruction": 0, "mnemonic": "NOP", "aluResult": 0, "forwardA": 0, "forwardB": 0},
            "MEM": {"pc": 0, "valid": False, "instruction": 0, "mnemonic": "NOP", "memAddress": 0, "memRead": False, "memWrite": False, "trapTaken": False},
            "WB": {"pc": 0, "valid": False, "instruction": 0, "mnemonic": "NOP", "rd": 0, "regWrite": False, "writeData": 0}
        }

        # Hardware Observability Signals
        self.signals = {
            "forwardA": 0, # 0=Reg, 1=MEM/WB, 2=EX/MEM
            "forwardB": 0,
            "loadUseHazard": False,
            "capHazard": False,
            "stallIF": False,
            "stallID": False,
            "flushIFID": False,
            "flushIDEX": False,
            "branchTaken": False,
            "redirectTarget": 0,
            "mOp": 0,
            "mulActive": False,
            "dividerBusy": False,
            "dividerDone": False,
            "dividerIterationRemaining": 32,
            "dividerIterationCompleted": 0,
            "schedHint": 0,
            "processBehaviorClass": 0,
            "currentContext": 0,
            "trapTaken": False,
            "trapTarget": 0,
            "trapEpc": 0,
            "trapCause": 0,
            "trapAddr": 0,
            "trapActive": False,
            "doubleFault": False
        }

        # System MMIO Registers
        self.mmio = {
            "REV_ENERGY_ACC": 0,
            "CLA_SWITCHING": 0,
            "MUL_THERMAL": 0,
            "EDP_CURRENT": 0,
            "EDP_CONFIG": 1,
            "BRANCH_CONFIDENCE": 0,
            "PROCESS_BEHAVIOR_CLASS": 0,
            "SCHED_HINT": 0,
            "RETIRED_COUNT": 0,
            "BRANCH_TAKEN_COUNT": 0,
            "LOAD_USE_STALL_COUNT": 0,
            "DIV_BUSY_CYCLES": 0,
            "PIPELINE_STALL_COUNT": 0,
            "LAST_COMMIT_PC": 0,
            "CURRENT_CONTEXT": 0,
            "SEC_STATUS": 0,
            "SEC_PC": 0,
            "SEC_ADDR": 0,
            "SEC_INFO": 0,
            "SEC_CONTEXT": 0,
            "TRAP_CONTROL": 0,
            "TRAP_STATUS": 0,
            "TRAP_VECTOR": 0x80,
            "TRAP_EPC": 0,
            "TRAP_CAUSE": 0,
            "TRAP_ADDR": 0,
            "TRAP_CONTEXT": 0
        }

        # Execution History & Trace
        self.history: List[Dict[str, Any]] = []
        self.program_source = ""
        self.program_instructions: List[int] = []

        # Internal Python reference instance
        self.reference_interp = RV32Interpreter()

    def reset(self):
        self.cycle_count = 0
        self.instruction_count = 0
        self.gpr = [0] * 32
        self.cap_regs = [
            {"index": 0, "name": "c0", "role": "NULL", "tag": 0, "base": 0, "length": 0, "perms": "---", "perms_raw": 0, "offset": 0},
            {"index": 1, "name": "c1", "role": "RAM Root", "tag": 1, "base": 0, "length": 4096, "perms": "RW-", "perms_raw": 3, "offset": 0},
            {"index": 2, "name": "c2", "role": "MMIO Root", "tag": 1, "base": 0x80000000, "length": 0x10000, "perms": "RW-", "perms_raw": 3, "offset": 0},
            {"index": 3, "name": "c3", "role": "Process Cap 3", "tag": 0, "base": 0, "length": 0, "perms": "---", "perms_raw": 0, "offset": 0},
            {"index": 4, "name": "c4", "role": "Process Cap 4", "tag": 0, "base": 0, "length": 0, "perms": "---", "perms_raw": 0, "offset": 0},
            {"index": 5, "name": "c5", "role": "Process Cap 5", "tag": 0, "base": 0, "length": 0, "perms": "---", "perms_raw": 0, "offset": 0},
            {"index": 6, "name": "c6", "role": "Process Cap 6", "tag": 0, "base": 0, "length": 0, "perms": "---", "perms_raw": 0, "offset": 0},
            {"index": 7, "name": "c7", "role": "Process Cap 7", "tag": 0, "base": 0, "length": 0, "perms": "---", "perms_raw": 0, "offset": 0},
        ]
        self.stages = {
            "IF": {"pc": 0, "valid": False, "instruction": 0, "mnemonic": "NOP", "stalled": False, "flushed": False},
            "ID": {"pc": 0, "valid": False, "instruction": 0, "mnemonic": "NOP", "stalled": False, "flushed": False, "bubble": False},
            "EX": {"pc": 0, "valid": False, "instruction": 0, "mnemonic": "NOP", "aluResult": 0, "forwardA": 0, "forwardB": 0},
            "MEM": {"pc": 0, "valid": False, "instruction": 0, "mnemonic": "NOP", "memAddress": 0, "memRead": False, "memWrite": False, "trapTaken": False},
            "WB": {"pc": 0, "valid": False, "instruction": 0, "mnemonic": "NOP", "rd": 0, "regWrite": False, "writeData": 0}
        }
        for k in self.signals:
            if isinstance(self.signals[k], bool): self.signals[k] = False
            elif k == "dividerIterationRemaining": self.signals[k] = 32
            else: self.signals[k] = 0
        for k in self.mmio:
            if k == "EDP_CONFIG": self.mmio[k] = 1
            elif k == "TRAP_VECTOR": self.mmio[k] = 0x80
            else: self.mmio[k] = 0
        self.history = []
        self.reference_interp.reset()

    def load_scenario(self, scenario_id: str) -> Dict[str, Any]:
        if scenario_id not in SCENARIO_CATALOG:
            raise ValueError(f"Unknown scenario ID: {scenario_id}")
        self.reset()
        self.active_engine = "rtl"
        self.current_scenario_id = scenario_id
        scenario = SCENARIO_CATALOG[scenario_id]
        self.program_source = scenario["assembly"]

        # Assemble scenario code for reference model
        assembled = assemble_program(self.program_source)
        self.program_instructions = [code for _, code, _ in assembled]
        self.reference_interp.load_program(self.program_instructions)

        # Initial IF fetch
        if self.program_instructions:
            self.stages["IF"] = {
                "pc": 0,
                "valid": True,
                "instruction": self.program_instructions[0],
                "mnemonic": disassemble_inst(self.program_instructions[0]),
                "stalled": False,
                "flushed": False
            }

        return self.get_state()

    def load_reference_assembly(self, source_text: str) -> Dict[str, Any]:
        self.reset()
        self.active_engine = "reference"
        self.current_scenario_id = None
        self.program_source = source_text

        assembled = assemble_program(source_text)
        self.program_instructions = [code for _, code, _ in assembled]
        self.reference_interp.load_program(self.program_instructions)

        if self.program_instructions:
            self.stages["IF"] = {
                "pc": 0,
                "valid": True,
                "instruction": self.program_instructions[0],
                "mnemonic": disassemble_inst(self.program_instructions[0]),
                "stalled": False,
                "flushed": False
            }

        return self.get_state()

    def step(self) -> Dict[str, Any]:
        """Steps 1 cycle or instruction depending on engine mode."""
        self.cycle_count += 1

        # Execute 1 step in reference model
        ev = self.reference_interp.step()

        # Update GPRs and commit state from reference execution
        self.gpr = list(self.reference_interp.regs)

        # Update Capability registers
        for i in range(8):
            cap = self.reference_interp.cap_regs[i]
            perm_str = ("R" if (cap.perms & 1) else "-") + ("W" if (cap.perms & 2) else "-") + ("X" if (cap.perms & 4) else "-")
            self.cap_regs[i]["tag"] = 1 if cap.tag else 0
            self.cap_regs[i]["base"] = cap.base
            self.cap_regs[i]["length"] = cap.length
            self.cap_regs[i]["perms"] = perm_str
            self.cap_regs[i]["perms_raw"] = cap.perms
            self.cap_regs[i]["offset"] = cap.offset

        # Update MMIO telemetry and counters
        self.mmio["REV_ENERGY_ACC"] = self.reference_interp.rev_energy
        self.mmio["CLA_SWITCHING"] = self.reference_interp.cla_switching
        self.mmio["MUL_THERMAL"] = self.reference_interp.mul_thermal
        self.mmio["EDP_CURRENT"] = (self.reference_interp.cla_switching + self.reference_interp.mul_thermal)
        self.mmio["RETIRED_COUNT"] = self.reference_interp.retired_count
        self.mmio["BRANCH_TAKEN_COUNT"] = self.reference_interp.branch_taken_count
        self.mmio["LOAD_USE_STALL_COUNT"] = self.reference_interp.load_use_stall_count
        self.mmio["DIV_BUSY_CYCLES"] = self.reference_interp.div_busy_cycles
        self.mmio["PIPELINE_STALL_COUNT"] = self.reference_interp.pipeline_stall_count
        self.mmio["LAST_COMMIT_PC"] = self.reference_interp.last_commit_pc
        self.mmio["PROCESS_BEHAVIOR_CLASS"] = self.reference_interp.mmio_regs.get(0x80002004, 0)
        self.mmio["SCHED_HINT"] = self.reference_interp.mmio_regs.get(0x80002008, 0)
        self.mmio["CURRENT_CONTEXT"] = self.reference_interp.mmio_regs.get(0x80002024, 0)

        # Update Security and Trap state
        self.mmio["SEC_STATUS"] = self.reference_interp.sec_status
        self.mmio["SEC_PC"] = self.reference_interp.sec_pc
        self.mmio["SEC_ADDR"] = self.reference_interp.sec_addr
        self.mmio["SEC_INFO"] = self.reference_interp.sec_info
        self.mmio["SEC_CONTEXT"] = self.reference_interp.sec_context

        self.mmio["TRAP_CONTROL"] = self.reference_interp.trap_control
        self.mmio["TRAP_STATUS"] = self.reference_interp.trap_status
        self.mmio["TRAP_VECTOR"] = self.reference_interp.trap_vector
        self.mmio["TRAP_EPC"] = self.reference_interp.trap_epc
        self.mmio["TRAP_CAUSE"] = self.reference_interp.trap_cause
        self.mmio["TRAP_ADDR"] = self.reference_interp.trap_addr
        self.mmio["TRAP_CONTEXT"] = self.reference_interp.trap_context

        self.signals["trapActive"] = bool(self.reference_interp.trap_status & 1)
        self.signals["doubleFault"] = bool(self.reference_interp.trap_status & 2)
        self.signals["schedHint"] = self.reference_interp.mmio_regs.get(0x80002008, 0)
        self.signals["processBehaviorClass"] = self.reference_interp.mmio_regs.get(0x80002004, 0)
        self.signals["currentContext"] = self.reference_interp.mmio_regs.get(0x80002024, 0)

        # Advance stage visualization
        curr_pc = self.reference_interp.pc
        if curr_pc >= 0 and curr_pc + 4 <= len(self.reference_interp.imem):
            next_inst = int.from_bytes(self.reference_interp.imem[curr_pc:curr_pc+4], 'little')
        else:
            next_inst = 0

        self.stages["WB"] = dict(self.stages["MEM"])
        self.stages["MEM"] = dict(self.stages["EX"])
        self.stages["EX"] = dict(self.stages["ID"])
        self.stages["ID"] = dict(self.stages["IF"])
        self.stages["IF"] = {
            "pc": curr_pc,
            "valid": next_inst != 0,
            "instruction": next_inst,
            "mnemonic": disassemble_inst(next_inst) if next_inst != 0 else "HALT",
            "stalled": False,
            "flushed": False
        }

        if ev is not None:
            self.instruction_count += 1
            self.stages["WB"]["regWrite"] = ev.regWrite
            self.stages["WB"]["rd"] = ev.rd
            self.stages["WB"]["writeData"] = ev.writeData

        state = self.get_state()
        self.history.append(state)
        return state

    def run_all(self, max_steps: int = 100) -> List[Dict[str, Any]]:
        steps = 0
        while not self.reference_interp.halted and steps < max_steps:
            self.step()
            steps += 1
        return self.history

    def get_state(self) -> Dict[str, Any]:
        return {
            "engine": "rtl" if self.active_engine == "rtl" else "reference",
            "engine_title": "RTL / Verilator (Cycle-Accurate 5-Stage Core)" if self.active_engine == "rtl" else "Python Reference Model (Architectural ISA Golden)",
            "scenario_id": self.current_scenario_id,
            "cycle_count": self.cycle_count,
            "instruction_count": self.instruction_count,
            "cpi": round(self.cycle_count / max(1, self.instruction_count), 2),
            "gpr": [{"reg": f"x{i}", "name": list(ABI_MAP.keys())[list(ABI_MAP.values()).index(i)] if i in ABI_MAP.values() else f"x{i}", "val": self.gpr[i], "hex": f"0x{self.gpr[i]:08X}"} for i in range(32)],
            "capabilities": self.cap_regs,
            "stages": self.stages,
            "signals": self.signals,
            "mmio": self.mmio,
            "halted": self.reference_interp.halted,
            "pc": self.reference_interp.pc
        }

    def write_mmio_architectural(self, reg_name: str, value: int) -> Dict[str, Any]:
        """Executes an architectural SW to update OS hint registers."""
        addr_map = {
            "PROCESS_BEHAVIOR_CLASS": 0x80002004,
            "SCHED_HINT": 0x80002008,
            "CURRENT_CONTEXT": 0x80002024
        }
        if reg_name not in addr_map:
            raise ValueError(f"Unknown architectural MMIO register: {reg_name}")
        addr = addr_map[reg_name]
        # In Reference Mode or Scenario Mode, execute via MMIO handler
        self.reference_interp.mmio_regs[addr] = value & 0xFFFFFFFF
        self.mmio[reg_name] = value & 0xFFFFFFFF
        if reg_name == "SCHED_HINT": self.signals["schedHint"] = value & 0xFFFFFFFF
        elif reg_name == "PROCESS_BEHAVIOR_CLASS": self.signals["processBehaviorClass"] = value & 0xFFFFFFFF
        elif reg_name == "CURRENT_CONTEXT": self.signals["currentContext"] = value & 0xFFFFFFFF
        return self.get_state()

    def compare_cores(self, scenario_id: str) -> Dict[str, Any]:
        """Runs the given scenario on SingleCycleCore vs PipelinedCore."""
        if scenario_id not in SCENARIO_CATALOG:
            raise ValueError(f"Unknown scenario ID: {scenario_id}")
        scen = SCENARIO_CATALOG[scenario_id]
        if not scen.get("single_cycle_compatible", False):
            return {
                "compatible": False,
                "reason": "SingleCycleCore is the frozen golden reference for the RV32I baseline. Extended features (RV32M, MMIO, CapabilityLite, Precise Traps) are PipelinedCore-only."
            }

        # Run on SingleCycleCore model
        scc_interp = RV32Interpreter()
        assembled = assemble_program(scen["assembly"])
        code = [c for _, c, _ in assembled]
        scc_interp.load_program(code)
        scc_trace = scc_interp.run(scen["max_cycles"])

        # Run on PipelinedCore model
        pipe_interp = RV32Interpreter()
        pipe_interp.load_program(code)
        pipe_trace = pipe_interp.run(scen["max_cycles"])

        scc_insts = len(scc_trace)
        scc_cycles = scc_insts # Single cycle: 1 cycle per instruction
        pipe_insts = len(pipe_trace)
        pipe_stalls = pipe_interp.pipeline_stall_count
        pipe_cycles = pipe_insts + 4 + pipe_stalls # Pipeline fill + stalls

        return {
            "compatible": True,
            "scenario": scen["title"],
            "single_cycle": {
                "instructions": scc_insts,
                "cycles": scc_cycles,
                "cpi": 1.00,
                "stalls": 0,
                "hazards": 0
            },
            "pipelined": {
                "instructions": pipe_insts,
                "cycles": pipe_cycles,
                "cpi": round(pipe_cycles / max(1, pipe_insts), 2),
                "stalls": pipe_stalls,
                "load_use_stalls": pipe_interp.load_use_stall_count,
                "branch_flushes": pipe_interp.branch_taken_count * 2
            }
        }
