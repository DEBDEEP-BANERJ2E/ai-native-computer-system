"""
Unit tests for FastAPI backend endpoints.
"""

from fastapi.testclient import TestClient
from app import app

client = TestClient(app)


def test_manifest_endpoint():
    response = client.get("/api/manifest")
    assert response.status_code == 200
    data = response.json()
    assert data["objective"] == 2
    assert data["tag"] == "objective2-freeze-v1.0"


def test_health_endpoint():
    response = client.get("/api/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["objective"] == 2


def test_scenarios_endpoint():
    response = client.get("/api/scenarios")
    assert response.status_code == 200
    data = response.json()
    assert data["count"] > 0
    assert len(data["scenarios"]) == data["count"]


def test_scenario_load_and_step():
    response = client.post("/api/scenario/load", json={"scenario_id": "canon_prog1_alu"})
    assert response.status_code == 200
    state = response.json()
    assert state["scenario_id"] == "canon_prog1_alu"
    assert state["engine"] == "rtl"

    # Step 1 cycle
    step_resp = client.post("/api/scenario/step")
    assert step_resp.status_code == 200
    step_state = step_resp.json()
    assert step_state["cycle_count"] == 1


def test_reference_assemble_and_load():
    code = "addi x1, x0, 10\naddi x2, x0, 20\nadd x3, x1, x2"
    asm_resp = client.post("/api/reference/assemble", json={"source": code})
    assert asm_resp.status_code == 200
    asm_data = asm_resp.json()
    assert asm_data["success"] is True
    assert len(asm_data["instructions"]) == 3

    load_resp = client.post("/api/reference/load", json={"source": code})
    assert load_resp.status_code == 200
    state = load_resp.json()
    assert state["engine"] == "reference"

    run_resp = client.post("/api/reference/run")
    assert run_resp.status_code == 200
    final_state = run_resp.json()
    assert final_state["gpr"][3]["val"] == 30


def test_compare_cores():
    resp = client.post("/api/compare", json={"scenario_id": "canon_prog1_alu"})
    assert resp.status_code == 200
    data = resp.json()
    assert data["compatible"] is True
    assert data["single_cycle"]["cpi"] == 1.00
    assert "pipelined" in data


def test_mmio_write_architectural():
    resp = client.post("/api/mmio/write", json={"register": "SCHED_HINT", "value": 7})
    assert resp.status_code == 200
    state = resp.json()
    assert state["mmio"]["SCHED_HINT"] == 7
    assert state["signals"]["schedHint"] == 7


if __name__ == "__main__":
    test_manifest_endpoint()
    print("test_manifest_endpoint: PASS")
    test_health_endpoint()
    print("test_health_endpoint: PASS")
    test_scenarios_endpoint()
    print("test_scenarios_endpoint: PASS")
    test_scenario_load_and_step()
    print("test_scenario_load_and_step: PASS")
    test_reference_assemble_and_load()
    print("test_reference_assemble_and_load: PASS")
    test_compare_cores()
    print("test_compare_cores: PASS")
    test_mmio_write_architectural()
    print("test_mmio_write_architectural: PASS")
    print("\nALL FASTAPI BACKEND TESTS PASSED (7/7) ✅")
