#pragma once

#include "object.hpp"
#include <cstdint>
#include <string>
#include <vector>

namespace an32asm {

/** AN32OBJ relocatable object file format (O5-F).
  *
  * Binary layout:
  *   Header   : magic[8] "AN32OBJ\0", version u32, counts, strtab size, hdr size
  *   Strtab   : NUL-terminated strings (names)
  *   Sections : section headers then their data blobs
  *   Symbols  : symbol records
  *   Relocs   : fixup/relocation records
  *
  * This is the relocatable object the linker (O5-G) consumes. It is distinct
  * from the flat --emit-hex image and from the AN32EXE executable.
  */
namespace an32obj {

constexpr char     MAGIC[8]   = {'A','N','3','2','O','B','J','\0'};
constexpr uint32_t  VERSION    = 1;
constexpr uint32_t  SEC_HDR_SZ = 32;   // bytes per section header
constexpr uint32_t  SYM_SZ     = 32;   // bytes per symbol record
constexpr uint32_t  REL_SZ     = 32;   // bytes per relocation record

// section type / flags mirror SectionType / SectionFlags
enum SecType : uint32_t { ST_PROGBITS = 0, ST_NOBITS = 1 };

// relocation kinds mirror FixupKind
// (values assigned to keep wire-stable; keep in sync with fixup.hpp)

enum RelocKind : uint32_t {
  RK_BRANCH = 0, RK_JAL, RK_CALL, RK_HI20, RK_LO12_I, RK_LO12_S,
  RK_PCREL_HI20, RK_PCREL_LO12_I, RK_PCREL_LO12_S,
  RK_ABS32, RK_ADD32, RK_SUB32
};

struct SectionHdr {
  uint32_t name_off;
  uint32_t type;        // SecType
  uint32_t flags;
  uint32_t align;
  uint32_t size;        // memory_size
  uint32_t data_off;    // file offset of data (0 for NOBITS)
  uint32_t data_size;
  uint32_t reserved;
};

struct SymbolRec {
  uint32_t name_off;
  int32_t  section_id;  // -1 = absolute/undefined handled via flags
  uint32_t value;
  uint32_t size;
  uint32_t binding;     // 0 local 1 global 2 weak
  uint32_t type;        // 0 notype 1 object 2 func
  uint32_t flags;       // bit0 defined, bit1 absolute
  uint32_t reserved;
};

struct RelocRec {
  uint32_t kind;        // RelocKind
  uint32_t section_id;
  uint32_t offset;
  uint32_t symbol_name_off;
  int32_t  addend;
  uint32_t group_id;    // 0 = none, else 1+group
  uint32_t anchor_off;  // 0 = none
  uint32_t flags;       // bit0 has_group, bit1 has_anchor, bit2 local_numeric
  uint32_t reserved;
};

// Serialize an AssemblerObject to a file. Returns false on IO error.
bool write_object(const AssemblerObject& obj, const std::string& path);

// Deserialize an object file into an AssemblerObject (sections hold data,
// symbols, and fixups reconstructed). Returns false on error.
bool read_object(const std::string& path, AssemblerObject& out);

} // namespace an32obj

} // namespace an32asm
