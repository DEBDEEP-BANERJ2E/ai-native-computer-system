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

.include "memmap.inc"

.text
.globl _start

# -----------------------------------------------------------------------------
# _start  [M-mode, reset pc=0]
#   sp = M stack, mtvec = M handler, delegate U-ECALL to S, mepc=s_start,
#   mstatus.MPP=S, mret -> S.
# -----------------------------------------------------------------------------
_start:
    li   sp, M_STACK_TOP
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
    li   sp, S_STACK_TOP
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

    li   sp, U_STACK_TOP
    sret                          # -> U-mode at u_start

# -----------------------------------------------------------------------------
# u_start  [U-mode]
#   Runs on the U stack, leaves a sentinel, issues ecall, then (after sret)
#   writes a resume marker and spins.
# -----------------------------------------------------------------------------
u_start:
    # Adversarial: give every writable GPR a distinct recognizable value so
    # the trap frame must preserve ALL of them across the U->S->U round trip.
    li   ra,  0xA11A0001
    li   gp,  0xA11A0003
    li   tp,  0xA11A0004
    li   t0,  0xA11A0005
    li   t1,  0xA11A0006
    li   t2,  0xA11A0007
    li   s0,  0xA11A0008
    li   s1,  0xA11A0009
    li   a0,  0xA11A000A
    li   a1,  0xA11A000B
    li   a2,  0xA11A000C
    li   a3,  0xA11A000D
    li   a4,  0xA11A000E
    li   a5,  0xA11A000F
    li   a6,  0xA11A0010
    li   a7,  0xA11A0011
    li   s2,  0xA11A0012
    li   s3,  0xA11A0013
    li   s4,  0xA11A0014
    li   s5,  0xA11A0015
    li   s6,  0xA11A0016
    li   s7,  0xA11A0017
    li   s8,  0xA11A0018
    li   s9,  0xA11A0019
    li   s10, 0xA11A001A
    li   s11, 0xA11A001B
    li   t3,  0xA11A001C
    li   t4,  0xA11A001D
    li   t5,  0xA11A001E
    li   t6,  0xA11A001F

    li   t0, 0x5555AAAA            # U-stack sentinel
    sw   t0, -16(sp)
    lw   t1, -16(sp)              # t1 = sentinel (U stack readback)

    ecall                         # -> S-mode s_trap_entry (delegated)

    li   a0, 0xBB                 # resume marker: proves U continued past ecall
    # Surface the B3 KDATA observation frame for the test to verify.
    li   t0, KOBS_BASE
    lw   a1, 0(t0)                # trapped user sp
    lw   a2, 4(t0)                # ra
    lw   a3, 8(t0)                # s0
    lw   a4, 12(t0)               # t0
    lw   a5, 16(t0)               # sepc
    lw   a6, 20(t0)               # scause
    lw   a7, 24(t0)               # sstatus
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
    # U->S trap. Invariant while U runs: sp=user_sp, sscratch=kernel_sp.
    csrrw sp, sscratch, sp        # sp=kernel top, sscratch=user sp
    addi sp, sp, -TF_SIZE         # frame base (144B, 16B-aligned)
    # Save t0 BEFORE clobbering it to capture the trapped user sp.
    sw   t0, 16(sp)               # TF_T0 (x5)
    csrrs t0, sscratch, x0        # t0 = trapped user sp
    sw   t0, 4(sp)                # TF_SP slot (x2) = user sp, not kernel sp
    # Save every writable GPR x1..x31 except t0(done) and sp(slot done).
    sw   ra,  0(sp)               # x1
    sw   gp,  8(sp)               # x3
    sw   tp,  12(sp)              # x4
    sw   t1,  20(sp)              # x6
    sw   t2,  24(sp)              # x7
    sw   s0,  28(sp)              # x8
    sw   s1,  32(sp)              # x9
    sw   a0,  36(sp)              # x10
    sw   a1,  40(sp)              # x11
    sw   a2,  44(sp)              # x12
    sw   a3,  48(sp)              # x13
    sw   a4,  52(sp)              # x14
    sw   a5,  56(sp)              # x15
    sw   a6,  60(sp)              # x16
    sw   a7,  64(sp)              # x17
    sw   s2,  68(sp)              # x18
    sw   s3,  72(sp)              # x19
    sw   s4,  76(sp)              # x20
    sw   s5,  80(sp)              # x21
    sw   s6,  84(sp)              # x22
    sw   s7,  88(sp)              # x23
    sw   s8,  92(sp)              # x24
    sw   s9,  96(sp)              # x25
    sw   s10, 100(sp)             # x26
    sw   s11, 104(sp)             # x27
    sw   t3,  108(sp)             # x28
    sw   t4,  112(sp)             # x29
    sw   t5,  116(sp)             # x30
    sw   t6,  120(sp)             # x31
    # Save privileged CSRs into the frame.
    csrrs t0, sepc,    x0 ; sw t0, TF_SEPC(sp)
    csrrs t0, sstatus, x0 ; sw t0, TF_SSTATUS(sp)
    csrrs t0, scause,  x0 ; sw t0, TF_SCAUSE(sp)
    csrrs t0, stval,   x0 ; sw t0, TF_STVAL(sp)

    # ---- B3 observation copy: frame -> KDATA (for test verification) ----
    li   t1, KOBS_BASE
    lw   t0, 4(sp)         ; sw t0, 0(t1)    # trapped user sp
    lw   t0, 0(sp)         ; sw t0, 4(t1)    # ra
    lw   t0, 28(sp)        ; sw t0, 8(t1)    # s0
    lw   t0, 16(sp)        ; sw t0, 12(t1)   # t0
    lw   t0, TF_SEPC(sp)   ; sw t0, 16(t1)
    lw   t0, TF_SCAUSE(sp) ; sw t0, 20(t1)
    lw   t0, TF_SSTATUS(sp); sw t0, 24(t1)

    # ---- minimal dispatcher: ECALL_U advances saved sepc by 4 ----
    lw   t0, TF_SCAUSE(sp)
    li   t1, 8
    bne  t0, t1, s_trap_done
    lw   t0, TF_SEPC(sp)
    addi t0, t0, 4
    sw   t0, TF_SEPC(sp)
s_trap_done:
    # restore return CSRs from the frame
    lw   t0, TF_SEPC(sp)   ; csrrw x0, sepc, t0
    lw   t0, TF_SSTATUS(sp); csrrw x0, sstatus, t0
    # restore all GPRs (x2/sp handled by final swap; t0 last)
    lw   ra,  0(sp)
    lw   gp,  8(sp)
    lw   tp,  12(sp)
    lw   t1,  20(sp)
    lw   t2,  24(sp)
    lw   s0,  28(sp)
    lw   s1,  32(sp)
    lw   a0,  36(sp)
    lw   a1,  40(sp)
    lw   a2,  44(sp)
    lw   a3,  48(sp)
    lw   a4,  52(sp)
    lw   a5,  56(sp)
    lw   a6,  60(sp)
    lw   a7,  64(sp)
    lw   s2,  68(sp)
    lw   s3,  72(sp)
    lw   s4,  76(sp)
    lw   s5,  80(sp)
    lw   s6,  84(sp)
    lw   s7,  88(sp)
    lw   s8,  92(sp)
    lw   s9,  96(sp)
    lw   s10, 100(sp)
    lw   s11, 104(sp)
    lw   t3,  108(sp)
    lw   t4,  112(sp)
    lw   t5,  116(sp)
    lw   t6,  120(sp)
    lw   t0,  16(sp)             # t0 last (used above for CSR restore)
    addi sp, sp, TF_SIZE         # reclaim frame -> kernel top
    csrrw sp, sscratch, sp       # sp=user sp, sscratch=kernel top
    sret                         # -> U resumes after ecall
