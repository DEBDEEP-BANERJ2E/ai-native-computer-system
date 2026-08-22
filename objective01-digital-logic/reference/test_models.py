import random
import unittest

from models import TelemetryModel, add, alu, booth_multiply, simple_multiply


class GoldenModelTest(unittest.TestCase):
    def test_add_exhaustive_8_bit(self):
        for a in range(256):
            for b in range(256):
                for carry in range(2):
                    result, carry_out = add(8, a, b, carry)
                    expected = a + b + carry
                    self.assertEqual(result, expected & 0xFF)
                    self.assertEqual(carry_out, expected >> 8)

    def test_unsigned_multiplier_exhaustive_8_bit(self):
        for a in range(256):
            for b in range(256):
                self.assertEqual(simple_multiply(8, a, b), a * b)

    def test_signed_booth_exhaustive_8_bit(self):
        for a in range(-128, 128):
            for b in range(-128, 128):
                self.assertEqual(booth_multiply(8, a, b), (a * b) & 0xFFFF)

    def test_alu_randomized_32_bit(self):
        random.seed(4)
        for _ in range(1000):
            a = random.getrandbits(32)
            b = random.getrandbits(32)
            for opcode in range(11):
                result = alu(32, a, b, opcode)
                self.assertEqual(result["zero"], int(result["result"] == 0))
                self.assertEqual(result["negative"], result["result"] >> 31)

    def test_telemetry_activity(self):
        telemetry = TelemetryModel(8)
        telemetry.observe(True, reversible=True, cla=True, result=0)
        telemetry.observe(True, cla=True, result=0b10110000)
        telemetry.observe(True, cla=True, result=0b11100100)
        self.assertEqual(telemetry.read(0x80001000), 1)
        self.assertEqual(telemetry.read(0x80001004), 6)


if __name__ == "__main__":
    unittest.main()