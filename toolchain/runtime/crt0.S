# AN32 crt0 - process entry stub (O5-J runtime)
#
# The O5-I loader builds the initial user stack as:
#   [sp+0]  argc
#   [sp+4]  argv[0] pointer
#   ...     argv[argc-1]
#   [sp+4+4*argc] NULL
# and sets the process entry PC to the executable's entry symbol (_start)
# with sp = that block. _start lifts argc/argv into a0/a1 (C ABI) and
# calls main, then exits with main's return value via SYS_EXIT(4).

    .text
    .globl _start
_start:
    lw    a0, 0(sp)        # argc
    addi  a1, sp, 4        # argv = &argv[0]
    jal   ra, main         # call main(argc, argv); returns to next insn
    # main returned -> exit(status in a0)
    li    a7, 4            # SYS_EXIT
    ecall
