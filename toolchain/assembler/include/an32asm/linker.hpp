#pragma once

#include "object.hpp"
#include "an32exe/exeformat.hpp"
#include <string>
#include <vector>
#include <unordered_map>
#include <optional>

namespace an32asm {

/** Linker (O5-G): AN32OBJ objects -> AN32EXE executable.
  *
  *   .o files -> section merge/layout -> symbol resolution -> relocation
  *            -> address assignment -> executable image
  *
  * Handles strong/weak/undefined/duplicate symbols, section alignment,
  * .bss (NOBITS -> memsz>filesz), entry symbol, and the RV32 relocation
  * kinds (branch/jal/call/hI20/lo12/pcrel/abs/add/sub).
  */
class Linker {
public:
  // Add a relocatable object (already parsed via an32obj::read_object).
  void add_object(AssemblerObject obj);

  // Layout knobs (defaults suit a flat user image).
  uint32_t textBase   = 0x10000;   // VA for first PROGBITS+X section
  uint32_t dataBase   = 0x20000;   // VA for writable/data sections
  uint32_t rodataBase = 0x18000;   // VA for read-only non-exec
  std::string entrySymbol = "_start";

  // Run the link. Returns the executable image; on error returns nullopt and
  // fills `err`.
  std::optional<an32exe::ExeImage> link(std::string& err);

private:
  std::vector<AssemblerObject> objs_;
};

} // namespace an32asm
