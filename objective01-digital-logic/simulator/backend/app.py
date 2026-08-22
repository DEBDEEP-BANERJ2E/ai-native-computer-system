from contextlib import asynccontextmanager
from pathlib import Path
from threading import Lock
import json
import subprocess

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field


ROOT = Path(__file__).resolve().parents[2]
SIMULATOR = ROOT / "simulator" / "objective1_sim"
TELEMETRY_ADDRESSES = {
    "rev_energy_acc": 0x80001000,
    "cla_switching": 0x80001004,
    "mul_thermal": 0x80001008,
    "edp_current": 0x8000100C,
    "edp_config": 0x80001010,
}


class ExecuteRequest(BaseModel):
    a: int = Field(ge=0, le=0xFFFFFFFF)
    b: int = Field(ge=0, le=0xFFFFFFFF)
    opcode: int = Field(ge=0, le=15)
    operation_valid: bool = True


class SimulatorBridge:
    def __init__(self):
        self.process = None
        self.lock = Lock()
        self.telemetry = {key: 0 for key in TELEMETRY_ADDRESSES}

    def start(self):
        if not SIMULATOR.exists():
            raise RuntimeError("simulator binary missing; run bash simulator/build_simulator.sh")
        self.process = subprocess.Popen(
            [str(SIMULATOR)], cwd=ROOT, stdin=subprocess.PIPE,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, bufsize=1,
        )

    def stop(self):
        if self.process and self.process.poll() is None:
            self.process.stdin.close()
            self.process.wait(timeout=3)
        if self.process:
            self.process.stdout.close()
            self.process.stderr.close()
        self.process = None

    def request(self, payload):
        with self.lock:
            if not self.process or self.process.poll() is not None:
                raise RuntimeError("Verilator simulator is not running")
            self.process.stdin.write(json.dumps(payload) + "\n")
            self.process.stdin.flush()
            line = self.process.stdout.readline()
            if not line:
                error = self.process.stderr.read()
                raise RuntimeError(f"simulator exited: {error}")
            response = json.loads(line)
            if response.get("telemetry"):
                self.telemetry = response["telemetry"]
            return response


bridge = SimulatorBridge()


@asynccontextmanager
async def lifespan(_app):
    bridge.start()
    yield
    bridge.stop()


app = FastAPI(title="Objective 1 Hardware Workbench", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/api/health")
def health():
    running = bridge.process is not None and bridge.process.poll() is None
    return {"ok": running, "backend": "verilator", "rtl": "Objective1Subsystem.sv"}


@app.post("/api/reset")
def reset():
    try:
        result = bridge.request({"command": "reset"})
        bridge.telemetry = {key: 0 for key in TELEMETRY_ADDRESSES}
        return result
    except RuntimeError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error


@app.post("/api/execute")
def execute(request: ExecuteRequest):
    payload = {"command": "execute", **request.model_dump(), "telemetry_address": 0x80001000}
    try:
        return bridge.request(payload)
    except RuntimeError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error


@app.get("/api/telemetry")
def telemetry():
    return {"registers": bridge.telemetry, "addresses": TELEMETRY_ADDRESSES}