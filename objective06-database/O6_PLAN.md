# Objective 6 — Database subsystem

Layers (bottom-up): catalog -> slotted pages -> heap files -> buffer pool
-> B+tree index -> SQL parser -> logical plan -> executor -> transactions
-> WAL/recovery -> O4 block/filesystem backend.

Status:
- scaffold + build.sbt: done
- catalog/page/heap/index/buffer/wal/sql/exec/txn: pending
