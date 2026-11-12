# Objective 4 — Memory & Storage

Builds the memory subsystem that Objective 3's deferred Stage C plugs into.
Sv32 paging, the hardware MMU, TLB, ASIDs, faults and address spaces live
here; O3 later consumes them via `satp`/page-fault/syscall integration.

## Layout

- `memory/`   — O4-A physical memory: RAM model, frame allocator, PFN<->PA
- `paging/`   — O4-B Sv32 PTE/page-table structures, walker-facing types
- `mmu/`      — O4-C hardware MMU: PTW, permission/access checks, faults
- `tlb/`      — O4-D TLB: VPN/PPN/ASID/perm/valid/global/level entries
- `addressspace/` — O4-G AddressSpace: root PT, ASID, map/unmap/protect
- `storage/`  — O4-H block device -> cache -> volume -> filesystem

## O4-A physical memory (this commit)

`FrameAllocator`: bitmap of free frames over `memBytes`, PFN alloc/free,
reserve (kernel/MMIO-adjacent), `numFree` accounting. `PhysMem` gives
PFN<->PA helpers. Address model kept explicit:
VA != PA != file offset != storage block.

## Status

- O4-A frame allocator: coded (compile-check pending)
- O4-B..H: pending
