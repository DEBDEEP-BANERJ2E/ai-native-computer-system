#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace an32exe {

/** AN32EXE executable format (O5-H).
  *
  * Loader-facing image produced by the linker (O5-G) from AN32OBJ objects.
  * Keeps file offset / virtual address / memory size distinct per segment:
  *
  *   VA != file offset != physical frame != storage block
  *
  * Layout:
  *   Header        : magic, version, entry VA, phnum, shstrtab
  *   Program headers: one per LOAD segment {type, flags, offset, vaddr,
  *                    filesz, memsz, align}
  *   Segment data  : file bytes (filesz); memsz-filesz is zero-filled (BSS)
  *   Section headers (optional debug): name/vaddr/size
  *
  * Segment types mirror ELF-ish PT_* but AN32-specific.
  */
constexpr char    MAGIC[8]   = {'A','N','3','2','E','X','E','\0'};
constexpr uint32_t VERSION   = 1;
constexpr uint32_t EHDR_SZ   = 64;    // bytes
constexpr uint32_t PHDR_SZ   = 32;    // bytes per program header
constexpr uint32_t SHDR_SZ   = 32;    // bytes per section header

enum SegType : uint32_t {
  PT_NULL = 0,
  PT_LOAD = 1,     // loadable segment
  PT_NOTE = 2      // metadata (entry, stack, caps) -- reserved
};

// segment flags
enum SegFlag : uint32_t {
  PF_X = 1 << 0,
  PF_W = 1 << 1,
  PF_R = 1 << 2
};

struct ProgramHeader {
  uint32_t type;      // SegType
  uint32_t flags;     // PF_*
  uint32_t offset;    // file offset of segment data
  uint32_t vaddr;     // virtual address (VA)
  uint32_t filesz;    // bytes present in file
  uint32_t memsz;     // bytes in memory (>= filesz; BSS = memsz>filesz)
  uint32_t align;
  uint32_t reserved;
};

struct SectionHeader {
  uint32_t name_off;  // into string table
  uint32_t type;      // 1=text 2=rodata 3=data 4=bss
  uint32_t flags;
  uint32_t vaddr;
  uint32_t size;
  uint32_t offset;    // file offset (0 for bss)
  uint32_t reserved;
};

/** An in-memory executable image the writer serializes. */
struct ExeImage {
  uint32_t entry = 0;                 // entry VA
  std::vector<ProgramHeader> phdrs;
  std::vector<uint8_t> data;          // concatenated segment payloads
  std::vector<SectionHeader> shdrs;
  std::string strtab;
};

// Serialize an ExeImage to path. Returns false on IO error.
bool write_exe(const ExeImage& img, const std::string& path);

// Parse an executable file into an ExeImage. Returns false on error.
bool read_exe(const std::string& path, ExeImage& out);

} // namespace an32exe
