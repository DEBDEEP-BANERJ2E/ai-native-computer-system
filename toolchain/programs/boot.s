# Boot and Capability Initialization Routine for AN32-Bare-v1
.text
.globl _start

_start:
    # 1. Initialize integer registers
    li   sp, 0x1000         # Set stack pointer to end of 4 KiB RAM (expands to lui sp, 1)
    addi gp, zero, 0        # Clear global pointer

    # 2. Derive working capabilities from Hardware Root RAM (c1 / cram)
    # cram has base = 0x00000000, length = 0x1000 (4 KiB), permissions = READ | WRITE
    addi a0, zero, 0x100    # length = 256 bytes
    csetbounds ca0, cram, a0 # ca0: base = 0, length = 256, offset = 0

    # 3. Store test data through capability
    addi t0, zero, 0x5A
    csw  t0, 0(ca0)

    # 4. Read back and verify
    clw  t1, 0(ca0)
    bne  t0, t1, fail

    # 5. Clear capability
    cclear cs0

success:
    addi a0, zero, 0        # Exit code 0
1:
    j 1b                    # Spin

fail:
    addi a0, zero, 1        # Exit code 1 (failure)
2:
    j 2b
