# Objective 3 — Known Issues Ledger

Deferred defects discovered during Stage-B coding. Verification is deferred to a
dedicated bottom-up pass after the full implementation lands.

## O3-KI-001 — B5 MTIP→STIP handoff not yet verified

Status: OPEN (deferred)

Symptoms observed in `KernelStackSpec` (pre-observability-fix):
- U execution healthy (`uCommits > 0`), ends in U-mode
- `KTICKS = 0` — `s_timer_irq` never ran
- trap debug cause output misreported interrupts as `ECALL_U` (see O3-KI-002)
- ~125 trap-like events clustered in `u_delay`, consistent with an **MTIP storm**:
  MTIP pending → M handler → rearm fails to deassert MTIP → `mret` → one U
  retirement → MTIP still pending → M handler again. MTIP has priority over STIP,
  so `s_timer_irq` is starved and `KTICKS` stays 0.

Likely root cause (unconfirmed): `mtimecmp` rearm in `m_timer_entry` does not
make `mtimecmp > mtime`, leaving level-sensitive MTIP asserted. Diagnostic
KDATA added (`KMTICKS`, `KMIP_AFTER_SET`, `KMTIME_*`, `KMTIMECMP_*`) to prove it.

## O3-KI-002 — SystemCore interrupt observability misreports cause

Status: FIXED (commit pending) — observability-only, no behavior change

`io.sysTrapCause`/`io.sysTrapEpc`/`io.trap.trapCause|Epc|Addr` were driven by
`sysExcCause`/`sysExcTval` (the synchronous-exception generator). During an
asynchronous interrupt `sysExcCause` can still evaluate to `ECALL_U=8`, so
interrupts were mislabeled as syscalls.

Fix: route through `trapCtrl.io.trapEntry.{cause,epc,tval,targetPriv}` which
carries the real winning cause (interrupt bit `0x80000000` set for interrupts)
and added `io.sysTrapTargetPriv`.

## O3-KI-003 — Serializing-retire younger-instruction hole (FIXED)

Status: FIXED — `5616b4b`

`wbSerializingRetire` suppressed younger MEM exceptions/side-effects but let the
younger instruction still enter WB. After an older `MRET`/`SRET`/`FENCE.I`
redirected, the ghost younger instruction formed a bogus post-return
commit → `intrQualify` → interrupt could be accepted on a non-architectural
boundary. B5 exposed it: `mret`→`s_start` was being hijacked to `mtvec`.

Fix: `memEnterWbValid` and the `sysMemWb` sideband input now require
`!wbSerializingRetire`.
