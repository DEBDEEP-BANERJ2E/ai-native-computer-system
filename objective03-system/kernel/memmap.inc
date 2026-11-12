# AN32 System-v1 physical memory layout - ASSEMBLY source of truth.
# Mirrors kernel/memmap.h (C/tooling side). Keep the two in sync; a future
# linker script will consume the same numbers. Stacks grow DOWNWARD,
# sp stays 16-byte aligned. Addresses are DMEM (data memory) offsets.

.equ DMEM_BASE,      0x00000000
.equ DMEM_SIZE,      0x00010000
.equ DMEM_TOP,       0x00010000

# Stack tops (first push lands at TOP-16 after alignment).
.equ M_STACK_TOP,    0x00010000     # machine boot stack
.equ M_STACK_SIZE,   0x00001000
.equ M_STACK_BASE,   0x0000F000

.equ S_STACK_TOP,    0x0000F000     # supervisor kernel stack
.equ S_STACK_SIZE,   0x00001000
.equ S_STACK_BASE,   0x0000E000

.equ U_STACK_TOP,    0x0000E000     # user stack
.equ U_STACK_SIZE,   0x00001000
.equ U_STACK_BASE,   0x0000D000

.equ KDATA_BASE,     0x0000C000     # early kernel/trap scratch data
.equ KDATA_SIZE,     0x00001000

# ---- B3 trap-frame ABI (144 bytes, 16B-aligned) ----
# Layout: x1..x31 at (N-1)*4, then sepc/sstatus/scause/stval.
# TF_SP slot holds the TRAPPED user sp (from sscratch), not kernel sp.
.equ TF_SIZE,    144
.equ TF_SEPC,    124
.equ TF_SSTATUS, 128
.equ TF_SCAUSE,  132
.equ TF_STVAL,   136
# KDATA observation area: kernel copies select frame fields here for the test.
.equ KOBS_BASE,  0x0000C000
.equ KOBS_SP,    0x0000C000   # trapped user sp
.equ KOBS_RA,    0x0000C004   # x1
.equ KOBS_S0,    0x0000C008   # x8 saved reg
.equ KOBS_T0,    0x0000C00C   # x5 temp
.equ KOBS_SEPC,  0x0000C010
.equ KOBS_SCAUSE,0x0000C014
.equ KOBS_SSTATUS,0x0000C018

# ---- B4 syscall ABI ----
# a7 = syscall number, a0-a5 = args 0..5, a0 = return (negative = -errno).
.equ SYS_GETABI,      0     # returns ABI/kernel version constant
.equ SYS_DEBUG_ARGS,  1     # stores a0..a5 to KARGS_BASE, returns 6
.equ ENOSYS,          38    # unknown syscall -> a0 = -ENOSYS
.equ SYS_ABI_VERSION, 0xB4  # B4 ABI marker returned by SYS_GETABI
# KDATA area where SYS_DEBUG_ARGS echoes the six argument registers.
.equ KARGS_BASE,      0x0000C040

# ---- B5 timer / interrupt platform constants ----
.equ MTIME_LO,     0x80003000
.equ MTIME_HI,     0x80003004
.equ MTIMECMP_LO,  0x80003008
.equ MTIMECMP_HI,  0x8000300C
.equ IRQ_STIP,     5            # supervisor timer interrupt code
.equ IRQ_MTIP,     7            # machine timer interrupt code
.equ CAUSE_INT,    0x80000000   # scause/mcause interrupt bit
.equ CAUSE_MASK,   0x7FFFFFFF
.equ MIP_STIP,     0x20         # mip/sip bit5 = STIP pending
.equ MIE_MTIE,     0x80         # mie bit7 = MTIE
.equ MIE_STIE,     0x20         # mie/sie bit5 = STIE
.equ MIDELEG_STIP, 0x20         # mideleg bit5 -> STIP targets S
.equ MSTATUS_MIE,  0x08         # mstatus bit3 = MIE
.equ MSTATUS_SIE,  0x02         # mstatus bit1 = SIE (supervisor int enable)
.equ MSTATUS_ALL,  0x80A        # MIE|SIE|MPP=S used at boot
.equ TIMER_INTERVAL, 2048        # mtime ticks between timer interrupts
.equ KTICKS,       0x0000C080   # KDATA kernel tick counter
.equ MIE_MTIE_STIE, 0xA0         # mie = MTIE(bit7)|STIE(bit5)
# B5 M-handler diagnostics (temporary): prove MTIP rearm + STIP set.
.equ KMTICKS,      0x0000C084    # M timer handler entry count
.equ KMIP_AFTER_SET,0x0000C088   # mip snapshot after STIP write
.equ KMTIME_LO,    0x0000C08C    # mtime lo snapshot at rearm
.equ KMTIME_HI,    0x0000C090    # mtime hi snapshot at rearm
.equ KMTIMECMP_LO, 0x0000C094    # mtimecmp lo snapshot after rearm
.equ KMTIMECMP_HI, 0x0000C098    # mtimecmp hi snapshot after rearm

# ---- B6 context switching ----
# Per-process saved TrapFrame slots (144B each) in KDATA. The kernel stack is
# shared scratch: the trap frame is transient, so only one frame is live at a
# time; context_switch copies it to/from these persistent slots.
.equ CTX_BASE,      0x0000C100    # first context slot
.equ CTX_STRIDE,    144           # == TF_SIZE
.equ CTX0,          0x0000C100    # process 0 slot
.equ CTX1,          0x0000C190    # process 1 slot (0xC100+0x90)
.equ CTX2,          0x0000C220    # process 2 slot
.equ CUR_PID,       0x0000C0F8    # KDATA: currently-running pid
.equ NEXT_PID,      0x0000C0FC    # KDATA: pid to switch to
.equ KB_MARKER,     0x0000C0A0    # KDATA: B6 proof - u_alt writes marker here
.equ SYS_SPAWN,     2             # prepare a context slot from live frame
.equ SYS_SWITCH,    3             # context_switch to NEXT_PID
