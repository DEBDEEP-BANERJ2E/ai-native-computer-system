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

- O4-A frame allocator: DONE (eac557d)
- O4-B Sv32 paging structures: DONE (ecf3549)
- O4-C hardware page-table walker: DONE (b3f0f25) - superpage deferred
- O4-D TLB: DONE (a3a7643)
- O4-E satp/ASID/SFENCE.VMA: DONE (13afeda)
- O4-F MMU fault delivery: DONE (3f8fe3e)
- O4-G AddressSpace: DONE (4f0f368)
- O4-H storage (blockdev+fs): DONE (cfd628f)

All pieces compile. Refinements deferred to verification: PTW superpage
encoding, TLB replacement policy (currently first-invalid), block
allocator bitmap/free-list (currently bump), FS extent/indirect blocks.

O4 complete -> next Objective 5 (CALL toolchain), then O3 integration.
