# =============================================================================
# AN32 System-v1 kernel image - Stage B2 privilege bootstrap + stacks.
#
# Single translation unit (Phase-2 toolchain has no linker yet); the
# boot/entry/trap/user regions are organized by banner. All cross-references
# are internal labels resolved by an32-as; `li <label>` materializes the
# absolute IMEM address for CSR writes (mtvec/stvec/sepc/mepc).
#
# Physical layout (see memmap.inc / memmap.h):
#   IMEM .text  : this file, loaded at imem offset 0 (_start at reset)
#   DMEM stacks : M 0x10000 / S 0xF000 / U 0xE000, grow downward
#
# Privilege flow:
#   _start[M] -> mret -> s_start[S] -> sret -> u_start[U]
#   u_start ecall -> s_trap_entry[S] -> sret -> u_start (after ecall)
# =============================================================================

# NOTE: stack addresses use the physical values from memmap.inc/memmap.h.
# .equ constants are not yet usable as instruction operands in an32-as
# (equates only feed .if evaluation), so the literals below ARE the memmap
# values - keep them in sync with memmap.inc until equate-operand support lands.
#   M_STACK_TOP=0x10000 S_STACK_TOP=0xF000 U_STACK_TOP=0xE000

.text
.globl _start

# -----------------------------------------------------------------------------
# _start  [M-mode, reset pc=0]
#   sp = M stack, mtvec = M handler, delegate U-ECALL to S, mepc=s_start,
#   mstatus.MPP=S, mret -> S.
# -----------------------------------------------------------------------------
_start:
    li   sp, 0x10000            # M_STACK_TOP          # M boot stack (dmem)
    # M-stack sentinel: prove the M stack region is live.
    li   t0, 0x4D4D4D4D            # 'MMMM'
    sw   t0, -16(sp)
    lw   t1, -16(sp)              # t1 = sentinel (M stack readback)

    li   t0, m_trap_entry
    csrrw x0, mtvec, t0           # M trap vector

    li   t0, 0x100                # medeleg bit 8 = ECALL-from-U delegated to S
    csrrs x0, medeleg, t0

    li   t0, s_start
    csrrw x0, mepc, t0            # return pc for mret

    li   t0, 0x800                # mstatus.MPP = S (01b in bits[12:11])
    csrrw x0, mstatus, t0

    mret                          # -> S-mode at s_start

# -----------------------------------------------------------------------------
# m_trap_entry  [M-mode] - safety net for non-delegated traps.
#   Captures mcause/mepc then spins; B2 expects only delegated U->S traps.
# -----------------------------------------------------------------------------
m_trap_entry:
    csrrs t0, mcause, x0
    csrrs t1, mepc, x0
m_trap_spin:
    j    m_trap_spin

# -----------------------------------------------------------------------------
# s_start  [S-mode]
#   sp = S kernel stack, stvec = S trap entry, sscratch = kernel sp,
#   sepc = u_start, sstatus.SPP = U, sp = U stack, sret -> U.
# -----------------------------------------------------------------------------
s_start:
    li   sp, 0xF000             # S_STACK_TOP          # S kernel stack (dmem)
    # S-stack sentinel.
    li   t0, 0x53535353            # 'SSSS'
    sw   t0, -16(sp)
    lw   t1, -16(sp)

    li   t0, s_trap_entry
    csrrw x0, stvec, t0           # S trap vector

    csrrw x0, sscratch, sp        # sscratch = kernel sp (for trap-entry swap)

    li   t0, u_start
    csrrw x0, sepc, t0            # user resume pc

    li   t0, 0x100                # sstatus.SPP bit -> clear => return to U
    csrrc x0, sstatus, t0

    li   sp, 0xE000             # U_STACK_TOP          # hand the user its own stack
    sret                          # -> U-mode at u_start

# -----------------------------------------------------------------------------
# u_start  [U-mode]
#   Runs on the U stack, leaves a sentinel, issues ecall, then (after sret)
#   writes a resume marker and spins.
# -----------------------------------------------------------------------------
u_start:
    li   t0, 0x5555AAAA            # U-stack sentinel
    sw   t0, -16(sp)
    lw   t1, -16(sp)              # t1 = sentinel (U stack readback)

    ecall                         # -> S-mode s_trap_entry (delegated)

    li   a0, 0xBB                 # resume marker: proves U continued past ecall
u_spin:
    j    u_spin

# -----------------------------------------------------------------------------
# s_trap_entry  [S-mode] - U->S trap entry.
#   Invariant while U runs: sp = user sp, sscratch = kernel sp.
#   csrrw sp,sscratch,sp swaps to the kernel stack and saves user sp.
#   Minimal B2 handler: preserve clobbered regs, advance sepc on ECALL,
#   restore stack+regs, sret.
# -----------------------------------------------------------------------------
s_trap_entry:
    csrrw sp, sscratch, sp        # sp=kernel stack, sscratch=user sp
    addi sp, sp, -48              # trap frame (3 saved regs + slack)
    sw   t0, 0(sp)
    sw   t1, 4(sp)
    sw   t2, 8(sp)

    csrrs t0, scause, x0          # t0 = cause
    csrrs t1, sepc, x0            # t1 = faulting pc

    li   t2, 8                    # ECALL-from-U
    bne  t0, t2, s_trap_unhandled

    addi t1, t1, 4                # ECALL records faulting pc; skip it
    csrrw x0, sepc, t1
    j    s_trap_done

s_trap_unhandled:
    j    s_trap_unhandled         # unexpected trap: surface it (spin)

s_trap_done:
    lw   t0, 0(sp)
    lw   t1, 4(sp)
    lw   t2, 8(sp)
    addi sp, sp, 48
    csrrw sp, sscratch, sp        # restore user sp, reload kernel sp
    sret                          # -> U resumes after ecall
