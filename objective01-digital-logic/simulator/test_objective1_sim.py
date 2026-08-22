import json
import random
import subprocess
import unittest
from pathlib import Path

from reference.models import TelemetryModel, alu


ROOT = Path(__file__).resolve().parents[1]
SIMULATOR = ROOT / "simulator" / "objective1_sim"
ADDRESSES = [0x80001000, 0x80001004, 0x80001008, 0x8000100C, 0x80001010]


class SimulatorProcess:
    def __init__(self):
        self.process = subprocess.Popen(
            [str(SIMULATOR)],
            cwd=ROOT,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )

    def request(self, payload):
        self.process.stdin.write(json.dumps(payload) + "\n")
        self.process.stdin.flush()
        line = self.process.stdout.readline()
        if not line:
            error = self.process.stderr.read()
            raise AssertionError(f"simulator exited without a response: {error}")
        return json.loads(line)

    def close(self):
        self.process.stdin.close()
        self.process.wait(timeout=3)
        if self.process.returncode != 0:
            raise AssertionError(self.process.stderr.read())
        self.process.stdout.close()
        self.process.stderr.close()


class Objective1SimulatorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not SIMULATOR.exists():
            raise unittest.SkipTest("build simulator first with bash simulator/build_simulator.sh")

    def test_reset_and_all_telemetry_registers(self):
        simulator = SimulatorProcess()
        try:
            self.assertEqual(simulator.request({"command": "reset"}),
                             {"ok": True, "command": "reset"})
            response = simulator.request({
                "command": "execute", "a": 5, "b": 3, "opcode": 0,
                "operation_valid": True, "telemetry_address": ADDRESSES[1],
            })
            self.assertEqual(response["result"], 8)
            self.assertEqual(response["telemetry"]["cla_switching"], 1)
            for address, key in zip(ADDRESSES, (
                "rev_energy_acc", "cla_switching", "mul_thermal", "edp_current", "edp_config")):
                response = simulator.request({
                    "command": "execute", "a": 0, "b": 0, "opcode": 15,
                    "operation_valid": False, "telemetry_address": address,
                })
                self.assertEqual(response["telemetry_data"], response["telemetry"][key])
        finally:
            simulator.close()

    def test_randomized_alu_and_cumulative_telemetry(self):
        simulator = SimulatorProcess()
        model = TelemetryModel(32)
        random.seed(7)
        try:
            simulator.request({"command": "reset"})
            vectors = [(0xFFFFFFFF, 1), (0x7FFFFFFF, 1), (0x80000000, 1)]
            vectors.extend((random.getrandbits(32), random.getrandbits(32)) for _ in range(24))
            for index, (a, b) in enumerate(vectors):
                for opcode in range(11):
                    expected = alu(32, a, b, opcode)
                    model.observe(True, cla=opcode in (0, 1), multiplier=opcode == 10,
                                  result=expected["result"])
                    response = simulator.request({
                        "command": "execute", "a": a, "b": b, "opcode": opcode,
                        "operation_valid": True, "telemetry_address": ADDRESSES[index % len(ADDRESSES)],
                    })
                    for key in ("result", "zero", "negative", "carry", "overflow"):
                        self.assertEqual(response[key], expected[key], f"vector {index}, opcode {opcode}, {key}")
                    self.assertEqual(response["busy"], False)
                    self.assertEqual(response["done"], True)
                    self.assertEqual(response["valid"], True)
                    self.assertEqual(response["telemetry"]["rev_energy_acc"], model.reversible_energy)
                    self.assertEqual(response["telemetry"]["cla_switching"], model.cla_switching)
                    self.assertEqual(response["telemetry"]["mul_thermal"], model.multiplier_thermal)
                    self.assertEqual(response["telemetry"]["edp_current"], model.read(0x8000100C))
        finally:
            simulator.close()

    def test_invalid_operation_does_not_update_telemetry(self):
        simulator = SimulatorProcess()
        try:
            simulator.request({"command": "reset"})
            before = simulator.request({
                "command": "execute", "a": 1, "b": 2, "opcode": 0,
                "operation_valid": True, "telemetry_address": ADDRESSES[1],
            })["telemetry"]
            after = simulator.request({
                "command": "execute", "a": 0, "b": 0, "opcode": 15,
                "operation_valid": False, "telemetry_address": ADDRESSES[1],
            })["telemetry"]
            self.assertEqual(after, before)
        finally:
            simulator.close()

    def test_malformed_command_exits_with_diagnostic(self):
        process = subprocess.run(
            [str(SIMULATOR)], input='{"command":"execute","a":"bad"}\n',
            cwd=ROOT, text=True, capture_output=True, check=False,
        )
        self.assertEqual(process.returncode, 2)
        self.assertIn("simulator error", process.stderr)
        self.assertEqual(process.stdout, "")


if __name__ == "__main__":
    unittest.main()