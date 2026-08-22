MASK32 = (1 << 32) - 1


def add(width, a, b, carry_in=0):
    mask = (1 << width) - 1
    total = (a & mask) + (b & mask) + carry_in
    return total & mask, (total >> width) & 1


def signed(value, width):
    value &= (1 << width) - 1
    sign = 1 << (width - 1)
    return value - (1 << width) if value & sign else value


def simple_multiply(width, a, b):
    mask = (1 << width) - 1
    return ((a & mask) * (b & mask)) & ((1 << (2 * width)) - 1)


def booth_multiply(width, a, b):
    product = signed(a, width) * signed(b, width)
    return product & ((1 << (2 * width)) - 1)


def alu(width, a, b, opcode):
    mask = (1 << width) - 1
    a &= mask
    b &= mask
    shift = b & (width - 1)
    if opcode == 0:
        result, carry = add(width, a, b)
    elif opcode == 1:
        result, carry = add(width, a, (~b) & mask, 1)
    elif opcode == 2:
        result, carry = a & b, 0
    elif opcode == 3:
        result, carry = a | b, 0
    elif opcode == 4:
        result, carry = a ^ b, 0
    elif opcode == 5:
        result, carry = a << shift, 0
    elif opcode == 6:
        result, carry = a >> shift, 0
    elif opcode == 7:
        result, carry = signed(a, width) >> shift, 0
    elif opcode == 8:
        result, carry = int(signed(a, width) < signed(b, width)), 0
    elif opcode == 9:
        result, carry = int(a < b), 0
    elif opcode == 10:
        result, carry = booth_multiply(width, a, b), 0
    else:
        result, carry = 0, 0
    result &= mask
    a_sign = (a >> (width - 1)) & 1
    b_sign = (b >> (width - 1)) & 1
    result_sign = (result >> (width - 1)) & 1
    overflow = int(
        (opcode == 0 and a_sign == b_sign and result_sign != a_sign)
        or (opcode == 1 and a_sign != b_sign and result_sign != a_sign)
    )
    return {
        "result": result,
        "zero": int(result == 0),
        "negative": result_sign,
        "carry": carry if opcode in (0, 1) else 0,
        "overflow": overflow,
    }


class TelemetryModel:
    def __init__(self, width):
        self.mask = (1 << width) - 1
        self.reversible_energy = 0
        self.cla_switching = 0
        self.multiplier_thermal = 0
        self.edp_config = 1
        self.previous_result = 0

    def observe(self, valid, reversible=False, cla=False, multiplier=False, result=0):
        if valid:
            activity = (result ^ self.previous_result).bit_count()
            self.reversible_energy += int(reversible)
            self.cla_switching += activity if cla else 0
            self.multiplier_thermal += activity if multiplier else 0
            self.previous_result = result & self.mask

    def read(self, address):
        values = {
            0x80001000: self.reversible_energy,
            0x80001004: self.cla_switching,
            0x80001008: self.multiplier_thermal,
            0x8000100C: (self.reversible_energy + self.cla_switching + self.multiplier_thermal)
            * self.edp_config,
            0x80001010: self.edp_config,
        }
        return values.get(address, 0) & MASK32