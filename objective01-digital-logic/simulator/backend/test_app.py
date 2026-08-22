import unittest

from fastapi.testclient import TestClient

from app import app


class WorkbenchApiTest(unittest.TestCase):
    def test_health_and_execute_are_rtl_backed(self):
        with TestClient(app) as client:
            self.assertTrue(client.get("/api/health").json()["ok"])
            response = client.post("/api/execute", json={"a": 5, "b": 3, "opcode": 0})
            self.assertEqual(response.status_code, 200)
            body = response.json()
            self.assertEqual(body["result"], 8)
            self.assertEqual(body["telemetry"]["cla_switching"], 1)

    def test_reset_clears_telemetry(self):
        with TestClient(app) as client:
            client.post("/api/execute", json={"a": 5, "b": 3, "opcode": 0})
            self.assertEqual(client.post("/api/reset").status_code, 200)
            registers = client.get("/api/telemetry").json()["registers"]
            self.assertEqual(registers["cla_switching"], 0)


if __name__ == "__main__":
    unittest.main()