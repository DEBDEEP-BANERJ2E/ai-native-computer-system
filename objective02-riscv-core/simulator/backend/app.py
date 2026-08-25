"""
FastAPI REST Server for Objective 2 Processor Observatory — RVSecure Workbench.
Port: 8002
"""

import json
from pathlib import Path
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import Optional, Dict, Any, List

from simulation_engine import SimulationEngine
from scenarios import SCENARIO_CATALOG
from assembler import assemble_program, disassemble_inst

BACKEND_DIR = Path(__file__).resolve().parent
SIM_DIR = BACKEND_DIR.parent
MANIFEST_PATH = SIM_DIR / "simulator_manifest.json"

app = FastAPI(title="Objective 2 Processor Observatory — RVSecure Workbench API")

# Enable CORS for Vite frontend (localhost:5173 / localhost:3000 / localhost:8002)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

engine = SimulationEngine()


class ScenarioLoadRequest(BaseModel):
    scenario_id: str


class AssemblyLoadRequest(BaseModel):
    source: str


class AssemblyAssembleRequest(BaseModel):
    source: str


class MMIOWriteRequest(BaseModel):
    reg_name: str = Field(alias="register")
    value: int



class CompareRequest(BaseModel):
    scenario_id: str


@app.get("/api/manifest")
def get_manifest():
    if MANIFEST_PATH.exists():
        with open(MANIFEST_PATH, "r") as f:
            return json.load(f)
    return {"objective": 2, "tag": "objective2-freeze-v1.0", "commit": "1ad498b"}


@app.get("/api/health")
def health():
    return {
        "status": "ok",
        "objective": 2,
        "engine": engine.active_engine,
        "scenario": engine.current_scenario_id,
        "cycle": engine.cycle_count
    }


@app.get("/api/scenarios")
def list_scenarios():
    return {
        "count": len(SCENARIO_CATALOG),
        "scenarios": [
            {
                "id": s["id"],
                "title": s["title"],
                "lab": s["lab"],
                "category": s["category"],
                "description": s["description"],
                "single_cycle_compatible": s.get("single_cycle_compatible", False),
                "assembly": s["assembly"]
            }
            for s in SCENARIO_CATALOG.values()
        ]
    }


@app.post("/api/reset")
def reset_engine():
    engine.reset()
    return engine.get_state()


@app.get("/api/state")
def get_state():
    return engine.get_state()


@app.post("/api/scenario/load")
def load_scenario(req: ScenarioLoadRequest):
    try:
        state = engine.load_scenario(req.scenario_id)
        return state
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))


@app.post("/api/scenario/step")
def step_scenario():
    return engine.step()


@app.post("/api/scenario/run")
def run_scenario(max_steps: Optional[int] = 100):
    engine.run_all(max_steps)
    return engine.get_state()


@app.post("/api/reference/assemble")
def assemble_code(req: AssemblyAssembleRequest):
    try:
        assembled = assemble_program(req.source)
        return {
            "success": True,
            "instructions": [
                {
                    "pc": pc,
                    "hex": f"0x{word:08X}",
                    "raw": word,
                    "disasm": disassemble_inst(word),
                    "source": orig
                }
                for pc, word, orig in assembled
            ]
        }
    except Exception as e:
        return {"success": False, "error": str(e)}


@app.post("/api/reference/load")
def load_reference(req: AssemblyLoadRequest):
    try:
        state = engine.load_reference_assembly(req.source)
        return state
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/api/reference/step")
def step_reference():
    return engine.step()


@app.post("/api/reference/run")
def run_reference(max_steps: Optional[int] = 100):
    engine.run_all(max_steps)
    return engine.get_state()


@app.post("/api/mmio/write")
def write_mmio(req: MMIOWriteRequest):
    try:
        state = engine.write_mmio_architectural(req.reg_name, req.value)
        return state
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/api/compare")
def compare_cores(req: CompareRequest):
    try:
        return engine.compare_cores(req.scenario_id)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.get("/api/verification")
def get_verification_evidence():
    return {
        "title": "Objective 2 Verification & Engineering Evidence",
        "tag": "objective2-freeze-v1.0",
        "commit": "1ad498b",
        "chisel_tests": {
            "total_passed": 108,
            "total_failed": 0,
            "suites": 16,
            "status": "100% GREEN"
        },
        "differential_parity": {
            "sections": 6,
            "benchmarks": 18,
            "retirement_events_matched": 223,
            "parity": "100% BIT-EXACT across Python Reference, SingleCycleCore & PipelinedCore"
        },
        "objective1_regression": {
            "total_passed": 24,
            "suites": 13,
            "status": "100% GREEN (Zero Regressions)"
        },
        "rtl_generation": {
            "SingleCycleCore": "Verified (generated/SingleCycleCore.sv)",
            "PipelinedCore": "Verified (generated/PipelinedCore.sv)",
            "IterativeDivider": "Verified (generated/IterativeDivider.sv)",
            "CapabilityRegFile": "Verified (generated/CapabilityRegFile.sv)",
            "SystemMMIO": "Verified (generated/SystemMMIO.sv)"
        }
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8002)
