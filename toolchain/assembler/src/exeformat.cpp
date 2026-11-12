#include "an32exe/exeformat.hpp"
#include <fstream>
#include <cstring>

namespace an32exe {
namespace {

void put32(std::vector<uint8_t>& v, uint32_t x) {
  v.push_back(x & 0xff); v.push_back((x >> 8) & 0xff);
  v.push_back((x >> 16) & 0xff); v.push_back((x >> 24) & 0xff);
}
uint32_t get32(const uint8_t* p) {
  return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}

} // namespace

bool write_exe(const ExeImage& img, const std::string& path) {
  const uint32_t nph = (uint32_t)img.phdrs.size();
  const uint32_t nsh = (uint32_t)img.shdrs.size();

  // layout: ehdr | phdrs | shdrs | strtab | segment data
  const uint32_t ehdrOff = 0;
  const uint32_t phOff = ehdrOff + EHDR_SZ;
  const uint32_t shOff = phOff + nph * PHDR_SZ;
  const uint32_t strOff = shOff + nsh * SHDR_SZ;
  const uint32_t strSize = (uint32_t)img.strtab.size() + 1;
  const uint32_t dataOff = (strOff + strSize + 3) & ~3u;

  std::vector<uint8_t> buf;
  buf.reserve(dataOff + img.data.size());
  // ehdr: magic8 + version + entry + phoff + shoff + phnum + shnum + strsize + flags
  buf.insert(buf.end(), MAGIC, MAGIC + 8);
  put32(buf, VERSION); put32(buf, img.entry);
  put32(buf, phOff); put32(buf, shOff); put32(buf, strOff);
  put32(buf, nph); put32(buf, nsh); put32(buf, strSize); put32(buf, 0);

  // phdrs (offsets already absolute into file)
  for (auto& p : img.phdrs) {
    put32(buf, p.type); put32(buf, p.flags); put32(buf, p.offset);
    put32(buf, p.vaddr); put32(buf, p.filesz); put32(buf, p.memsz);
    put32(buf, p.align); put32(buf, p.reserved);
  }
  // shdrs
  for (auto& s : img.shdrs) {
    put32(buf, s.name_off); put32(buf, s.type); put32(buf, s.flags);
    put32(buf, s.vaddr); put32(buf, s.size); put32(buf, s.offset);
    put32(buf, s.reserved);
  }
  // strtab
  buf.insert(buf.end(), img.strtab.begin(), img.strtab.end());
  buf.push_back(0);
  while (buf.size() < dataOff) buf.push_back(0);
  buf.insert(buf.end(), img.data.begin(), img.data.end());

  std::ofstream os(path, std::ios::binary);
  if (!os) return false;
  os.write((const char*)buf.data(), (std::streamsize)buf.size());
  return os.good();
}

bool read_exe(const std::string& path, ExeImage& out) {
  std::ifstream is(path, std::ios::binary | std::ios::ate);
  if (!is) return false;
  auto sz = is.tellg(); is.seekg(0);
  std::vector<uint8_t> buf((size_t)sz);
  is.read((char*)buf.data(), (std::streamsize)buf.size());
  if (!is || buf.size() < EHDR_SZ) return false;
  if (std::memcmp(buf.data(), MAGIC, 8) != 0) return false;
  const uint8_t* h = buf.data();
  if (get32(h + 8) != VERSION) return false;
  out.entry = get32(h + 12);
  uint32_t phOff = get32(h + 16), shOff = get32(h + 20), strOff = get32(h + 24);
  uint32_t nph = get32(h + 28), nsh = get32(h + 32), strSize = get32(h + 36);
  if (phOff + nph * PHDR_SZ > buf.size()) return false;
  if (shOff + nsh * SHDR_SZ > buf.size()) return false;
  if (strOff + strSize > buf.size()) return false;

  for (uint32_t i = 0; i < nph; ++i) {
    const uint8_t* p = buf.data() + phOff + i * PHDR_SZ;
    ProgramHeader ph;
    ph.type = get32(p); ph.flags = get32(p+4); ph.offset = get32(p+8);
    ph.vaddr = get32(p+12); ph.filesz = get32(p+16); ph.memsz = get32(p+20);
    ph.align = get32(p+24); ph.reserved = get32(p+28);
    out.phdrs.push_back(ph);
  }
  for (uint32_t i = 0; i < nsh; ++i) {
    const uint8_t* s = buf.data() + shOff + i * SHDR_SZ;
    SectionHeader sh;
    sh.name_off = get32(s); sh.type = get32(s+4); sh.flags = get32(s+8);
    sh.vaddr = get32(s+12); sh.size = get32(s+16); sh.offset = get32(s+20);
    sh.reserved = get32(s+24);
    out.shdrs.push_back(sh);
  }
  out.strtab.assign((const char*)(buf.data() + strOff), strSize);
  // collect segment payload bytes into a flat data blob for the writer's model
  uint32_t maxEnd = 0;
  for (auto& p : out.phdrs)
    if (p.type == PT_LOAD) maxEnd = std::max(maxEnd, p.offset + p.filesz);
  if (maxEnd <= buf.size())
    out.data.assign(buf.begin(), buf.begin() + maxEnd);
  return true;
}

} // namespace an32exe
