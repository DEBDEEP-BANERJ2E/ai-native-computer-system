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

    # B5: mscratch = M stack top so M traps can borrow the M stack.
    li   t0, M_STACK_TOP
    csrrw x0, mscratch, t0

    li   t0, m_timer_entry
    csrrw x0, mtvec, t0           # M trap vector -> timer/trap entry

    li   t0, 0x100                # medeleg bit 8 = ECALL-from-U delegated to S
    csrrs x0, medeleg, t0

    li   t0, MIDELEG_STIP         # mideleg bit5 = STIP delegated to S
    csrrs x0, mideleg, t0

    li   t0, MIE_MTIE_STIE       # mie = MTIE|STIE
    csrrs x0, mie, t0

    # Program mtimecmp = mtime + TIMER_INTERVAL (64-bit safe write order:
    # LO=0xFFFFFFFF, HI=new_hi, LO=new_lo) so MTIP cannot fire half-init.
    # MMIO timer window base = MTIME_LO = 0x80003000; MTIME_HI=+4,
    # MTIMECMP_LO=+8, MTIMECMP_HI=+12 (see memmap.inc).
    lui  t0, 0x80003              # 0x80003000 = MTIME_LO
    lw   t1, 0(t0)                # mtime lo
    lw   t2, 4(t0)                # mtime hi
    li   t3, TIMER_INTERVAL
    add  t3, t1, t3               # new_lo = lo + interval
    sltu t0, t3, t1               # carry
    add  t2, t2, t0               # new_hi = hi + carry
    li   t1, 0xFFFFFFFF
    sw   t1, 8(t0)                # MTIMECMP_LO = 0xFFFFFFFF
    sw   t2, 12(t0)               # MTIMECMP_HI = new_hi
    sw   t3, 8(t0)                # MTIMECMP_LO = new_lo

    li   t0, s_start
    csrrw x0, mepc, t0            # return pc for mret

    li   t0, MSTATUS_ALL          # MIE|SIE|MPP=S
    csrrw x0, mstatus, t0

    mret                          # -> S-mode at s_start

# -----------------------------------------------------------------------------
# m_timer_entry  [M-mode] - machine timer service (mtvec target).
#   Only MTIP interrupts are serviced; anything else is a fatal spin.
#   Rearm mtimecmp (deasserts level-sensitive MTIP), raise software STIP for
#   S, restore the interrupted context, mret.
# -----------------------------------------------------------------------------
m_timer_entry:
    csrrw sp, mscratch, sp        # sp = M stack, mscratch = interrupted sp
    addi sp, sp, -16
    sw   t0, 0(sp)
    sw   t1, 4(sp)
    sw   t2, 8(sp)
    sw   t3, 12(sp)

    csrrs t0, mcause, x0
    li   t1, 0x80000007           # MTIP interrupt cause (INT|7)
    bne  t0, t1, m_bad_trap       # unexpected M trap -> diagnostic spin

    # rearm mtimecmp = mtime + TIMER_INTERVAL
    lui  t0, 0x80003              # 0x80003000 = MTIME_LO
    lw   t1, 0(t0)                # mtime lo
    lw   t2, 4(t0)                # mtime hi
    li   t3, TIMER_INTERVAL
    add  t3, t1, t3
    sltu t0, t3, t1
    add  t2, t2, t0
    li   t1, 0xFFFFFFFF
    sw   t1, 8(t0)                # MTIMECMP_LO = 0xFFFFFFFF
    sw   t2, 12(t0)               # MTIMECMP_HI = new_hi
    sw   t3, 8(t0)                # MTIMECMP_LO = new_lo

    # raise supervisor timer pending: mip.STIP = 1 (delivered to S via mideleg)
    li   t0, MIP_STIP
    csrrs x0, sip, t0

    lw   t0, 0(sp)
    lw   t1, 4(sp)
    lw   t2, 8(sp)
    lw   t3, 12(sp)
    addi sp, sp, 16
    csrrw sp, mscratch, sp        # restore interrupted sp
    mret                          # -> interrupted privilege

m_bad_trap:
    j    m_bad_trap               # unexpected M trap: surface it (spin)
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

    li   t0, MIE_STIE             # sie.STIE = supervisor timer int enable
    csrrs x0, sie, t0

    li   sp, U_STACK_TOP
    sret                          # -> U-mode at u_start

# -----------------------------------------------------------------------------
# u_start  [U-mode]
#   Runs on the U stack, leaves a sentinel, issues ecall, then (after sret)
#   writes a resume marker and spins.
# -----------------------------------------------------------------------------
u_start:
    # Preserve-check init on NON-syscall registers (a0-a5,a7 are the ABI
    # arg/return regs and legitimately change across ecall).
    li   ra,  0xA11A0001
    li   gp,  0xA11A0003
    li   tp,  0xA11A0004
    li   s0,  0xA11A0008
    li   s1,  0xA11A0009
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

    # B5: busy delay so real MTIP->STIP interrupts fire while U is running.
    li   t0, 500
u_delay:
    addi t0, t0, -1
    bne  t0, x0, u_delay

    # ---- syscall 1: SYS_GETABI -> a0 = ABI version ----
    li   a7, SYS_GETABI
    ecall
    mv   s1, a0                     # save ABI version for the test

    # ---- syscall 2: SYS_DEBUG_ARGS with six distinct args ----
    li   a7, SYS_DEBUG_ARGS
    li   a0, 0x11111111
    li   a1, 0x22222222
    li   a2, 0x33333333
    li   a3, 0x44444444
    li   a4, 0x55555555
    li   a5, 0x66666666
    ecall                          # -> a0 = 6, KARGS_BASE[0..5] = args

    # ---- syscall 3: unknown number -> a0 = -ENOSYS ----
    li   a7, 0x7F                   # not a defined syscall
    li   a0, 0
    ecall                          # -> a0 = -ENOSYS

    # ---- surface results for the test ----
    mv   t6, a0                     # t6 = -ENOSYS (last return)
    li   t0, KARGS_BASE
    lw   a1, 0(t0)                  # echoed a0 arg
    lw   a2, 4(t0)                  # echoed a1 arg
    lw   a3, 8(t0)                  # echoed a2 arg
    lw   a4, 12(t0)                 # echoed a3 arg
    lw   a5, 16(t0)                 # echoed a4 arg
    lw   a6, 20(t0)                 # echoed a5 arg
    li   t2, KTICKS
    lw   t2, 0(t2)                  # t2 = kernel tick count (B5 proof)
    li   a0, 0xBB                   # resume marker
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

    j    trap_dispatch

# -----------------------------------------------------------------------------
# trap_dispatch [S] - route by trap type. B4 scope: only ECALL_U (cause 8).
#   Advances the frame's saved sepc by 4, then enters the syscall layer.
# -----------------------------------------------------------------------------
trap_dispatch:
    lw   t0, TF_SCAUSE(sp)
    blt  t0, x0, td_irq           # scause[31]=1 (signed<0) -> interrupt
    li   t1, 8                    # ECALL_U
    beq  t0, t1, td_ecall
    j    s_trap_unhandled         # other exceptions: out of B5 scope
td_irq:
    slli t0, t0, 1                # drop scause[31] -> cause_code
    srli t0, t0, 1
    li   t1, IRQ_STIP             # 5
    beq  t0, t1, s_timer_irq
    j    s_trap_unhandled         # other interrupts: out of B5 scope
td_ecall:
    lw   t0, TF_SEPC(sp)
    addi t0, t0, 4               # skip the ecall in the saved context
    sw   t0, TF_SEPC(sp)
    j    syscall_dispatch

# -----------------------------------------------------------------------------
# s_timer_irq [S] - supervisor timer interrupt service.
#   Runs inside the same 144-byte trap frame as exceptions. Increments the
#   kernel tick counter and clears the STIP pending source. Does NOT advance
#   sepc - an async interrupt resumes at the architectural next instruction.
# -----------------------------------------------------------------------------
s_timer_irq:
    li   t0, KTICKS              # KDATA tick counter
    lw   t1, 0(t0)
    addi t1, t1, 1
    sw   t1, 0(t0)               # KTICKS++
    li   t0, MIP_STIP
    csrrc x0, sip, t0            # clear mip.STIP pending
    j    s_trap_exit             # resume U at saved sepc (no +4)

s_trap_unhandled:
    j    s_trap_unhandled        # unexpected trap: surface it (spin)

# -----------------------------------------------------------------------------
# syscall_dispatch [S] - route by syscall number in TF_A7.
#   ABI: a7=num, a0-a5=args, result written to TF_A0 (negative = -errno).
#   All operands are read/written through the trap frame, never live GPRs.
# -----------------------------------------------------------------------------
syscall_dispatch:
    lw   t0, 64(sp)                 # TF_A7 = syscall number (x17)
    li   t1, SYS_GETABI
    beq  t0, t1, sys_getabi
    li   t1, SYS_DEBUG_ARGS
    beq  t0, t1, sys_debug_args
    li   t0, -ENOSYS                # unknown syscall -> a0 = -ENOSYS
    j    sys_ret_a0

sys_getabi:
    li   t0, SYS_ABI_VERSION        # dependency-free version query
    j    sys_ret_a0

sys_debug_args:
    # Echo the six argument registers (TF_A0..TF_A5) into KDATA so the
    # supervisor-side view of the trap-frame args is externally observable.
    li   t1, KARGS_BASE
    lw   t0, 36(sp) ; sw t0, 0(t1)  # a0
    lw   t0, 40(sp) ; sw t0, 4(t1)  # a1
    lw   t0, 44(sp) ; sw t0, 8(t1)  # a2
    lw   t0, 48(sp) ; sw t0, 12(t1) # a3
    lw   t0, 52(sp) ; sw t0, 16(t1) # a4
    lw   t0, 56(sp) ; sw t0, 20(t1) # a5
    li   t0, 6                      # return: six args captured
    j    sys_ret_a0

sys_ret_a0:
    sw   t0, 36(sp)                 # TF_A0 = return value
    j    s_trap_exit

# -----------------------------------------------------------------------------
# s_trap_exit [S] - restore architectural context and return to U.
# -----------------------------------------------------------------------------
s_trap_exit:
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
