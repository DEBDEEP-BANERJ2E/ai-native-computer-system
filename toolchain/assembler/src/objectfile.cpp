#include "an32asm/objectfile.hpp"
#include <fstream>
#include <cstring>
#include <unordered_map>

namespace an32asm {
namespace an32obj {

namespace {

struct StrTab {
  std::string bytes;
  std::unordered_map<std::string, uint32_t> off;
  uint32_t add(const std::string& s) {
    auto it = off.find(s);
    if (it != off.end()) return it->second;
    uint32_t o = static_cast<uint32_t>(bytes.size());
    bytes += s;
    bytes += '\0';
    off[s] = o;
    return o;
  }
};

void put32(std::vector<uint8_t>& v, uint32_t x) {
  v.push_back(x & 0xff); v.push_back((x >> 8) & 0xff);
  v.push_back((x >> 16) & 0xff); v.push_back((x >> 24) & 0xff);
}
void put64(std::vector<uint8_t>& v, uint64_t x) {
  for (int i = 0; i < 8; ++i) v.push_back((x >> (8 * i)) & 0xff);
}
uint32_t get32(const uint8_t* p) {
  return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}
uint64_t get64(const uint8_t* p) {
  uint64_t x = 0; for (int i = 7; i >= 0; --i) x = (x << 8) | p[i]; return x;
}

uint32_t fixup_kind_to_reloc(FixupKind k) {
  switch (k) {
    case FixupKind::BRANCH: return RK_BRANCH;
    case FixupKind::JAL: return RK_JAL;
    case FixupKind::CALL: return RK_CALL;
    case FixupKind::HI20: return RK_HI20;
    case FixupKind::LO12_I: return RK_LO12_I;
    case FixupKind::LO12_S: return RK_LO12_S;
    case FixupKind::PCREL_HI20: return RK_PCREL_HI20;
    case FixupKind::PCREL_LO12_I: return RK_PCREL_LO12_I;
    case FixupKind::PCREL_LO12_S: return RK_PCREL_LO12_S;
    case FixupKind::ABS32: return RK_ABS32;
    case FixupKind::ADD32: return RK_ADD32;
    case FixupKind::SUB32: return RK_SUB32;
  }
  return 0;
}
FixupKind reloc_to_fixup_kind(uint32_t r) {
  switch (r) {
    case RK_BRANCH: return FixupKind::BRANCH;
    case RK_JAL: return FixupKind::JAL;
    case RK_CALL: return FixupKind::CALL;
    case RK_HI20: return FixupKind::HI20;
    case RK_LO12_I: return FixupKind::LO12_I;
    case RK_LO12_S: return FixupKind::LO12_S;
    case RK_PCREL_HI20: return FixupKind::PCREL_HI20;
    case RK_PCREL_LO12_I: return FixupKind::PCREL_LO12_I;
    case RK_PCREL_LO12_S: return FixupKind::PCREL_LO12_S;
    case RK_ABS32: return FixupKind::ABS32;
    case RK_ADD32: return FixupKind::ADD32;
    case RK_SUB32: return FixupKind::SUB32;
  }
  return FixupKind::ABS32;
}
uint32_t binding_to_u32(SymbolBinding b) {
  return b == SymbolBinding::GLOBAL ? 1 : (b == SymbolBinding::WEAK ? 2 : 0);
}
SymbolBinding u32_to_binding(uint32_t b) {
  return b == 1 ? SymbolBinding::GLOBAL : (b == 2 ? SymbolBinding::WEAK : SymbolBinding::LOCAL);
}
uint32_t type_to_u32(SymbolType t) {
  return t == SymbolType::OBJECT ? 1 : (t == SymbolType::FUNC ? 2 : 0);
}
SymbolType u32_to_type(uint32_t t) {
  return t == 1 ? SymbolType::OBJECT : (t == 2 ? SymbolType::FUNC : SymbolType::NOTYPE);
}

} // namespace

bool write_object(const AssemblerObject& obj, const std::string& path) {
  StrTab st;
  // Pre-register all names so offsets are stable
  for (auto& s : obj.sections) st.add(s->name);
  for (auto& s : obj.symbol_table.get_symbols()) st.add(s.name);
  for (auto& f : obj.fixups) if (!f.symbol_name.empty()) st.add(f.symbol_name);

  const uint32_t nsec = (uint32_t)obj.sections.size();
  const uint32_t nsym = (uint32_t)obj.symbol_table.get_symbols().size();
  const uint32_t nrel = (uint32_t)obj.fixups.size();

  // header: magic8 + version + nsec + nsym + nrel + strtab_size + sec_off + sym_off + rel_off
  const uint32_t hdrSize = 8 + 4 * 8;
  const uint32_t secTabOff = hdrSize;
  const uint32_t strOff = secTabOff + nsec * SEC_HDR_SZ;
  const uint32_t dataOff = strOff + (uint32_t)st.bytes.size();
  // section data blobs laid out sequentially
  std::vector<uint32_t> secDataOff(nsec, 0);
  uint32_t cursor = dataOff;
  for (uint32_t i = 0; i < nsec; ++i) {
    secDataOff[i] = cursor;
    cursor += (uint32_t)obj.sections[i]->data.size();
    cursor = (cursor + 3) & ~3u; // 4-byte align
  }
  const uint32_t symOff = cursor;
  const uint32_t relOff = symOff + nsym * SYM_SZ;

  std::vector<uint8_t> buf;
  buf.insert(buf.end(), MAGIC, MAGIC + 8);
  put32(buf, VERSION); put32(buf, nsec); put32(buf, nsym); put32(buf, nrel);
  put32(buf, (uint32_t)st.bytes.size()); put32(buf, secTabOff);
  put32(buf, symOff); put32(buf, relOff); put32(buf, strOff);

  // section headers
  for (uint32_t i = 0; i < nsec; ++i) {
    auto& s = *obj.sections[i];
    put32(buf, st.off[s.name]);
    put32(buf, s.is_nobits() ? ST_NOBITS : ST_PROGBITS);
    put32(buf, s.flags);
    put32(buf, (uint32_t)s.alignment);
    put32(buf, (uint32_t)s.memory_size);
    put32(buf, s.is_nobits() ? 0u : secDataOff[i]);
    put32(buf, (uint32_t)s.data.size());
    put32(buf, 0);
  }
  // string table
  buf.insert(buf.end(), st.bytes.begin(), st.bytes.end());
  // pad to dataOff alignment already handled; write section data
  while (buf.size() < dataOff) buf.push_back(0);
  for (uint32_t i = 0; i < nsec; ++i) {
    auto& s = *obj.sections[i];
    if (s.is_nobits()) continue;
    while (buf.size() < secDataOff[i]) buf.push_back(0);
    buf.insert(buf.end(), s.data.begin(), s.data.end());
    while (buf.size() % 4) buf.push_back(0);
  }
  while (buf.size() < symOff) buf.push_back(0);
  // symbols
  for (auto& s : obj.symbol_table.get_symbols()) {
    put32(buf, st.off[s.name]);
    put32(buf, s.is_absolute ? 0xFFFFFFFFu : (uint32_t)(s.section_id.value_or(0)));
    put32(buf, (uint32_t)s.value); put32(buf, (uint32_t)s.size);
    put32(buf, binding_to_u32(s.binding)); put32(buf, type_to_u32(s.type));
    put32(buf, (s.is_defined ? 1u : 0u) | (s.is_absolute ? 2u : 0u)); put32(buf, 0);
  }
  // relocations (fixups)
  for (auto& f : obj.fixups) {
    uint32_t flags = 0;
    if (f.group_id) flags |= 1u;
    if (f.anchor_offset) flags |= 2u;
    if (f.is_local_numeric) flags |= 4u;
    put32(buf, fixup_kind_to_reloc(f.kind));
    put32(buf, f.section_id); put32(buf, (uint32_t)f.offset);
    put32(buf, f.symbol_name.empty() ? 0u : st.off[f.symbol_name]);
    put32(buf, (uint32_t)(int32_t)f.addend);
    put32(buf, f.group_id.value_or(0)); put32(buf, (uint32_t)f.anchor_offset.value_or(0));
    put32(buf, flags); put32(buf, 0);
  }

  std::ofstream os(path, std::ios::binary);
  if (!os) return false;
  os.write((const char*)buf.data(), (std::streamsize)buf.size());
  return os.good();
}

bool read_object(const std::string& path, AssemblerObject& out) {
  std::ifstream is(path, std::ios::binary | std::ios::ate);
  if (!is) return false;
  auto sz = is.tellg();
  is.seekg(0);
  std::vector<uint8_t> buf((size_t)sz);
  is.read((char*)buf.data(), (std::streamsize)buf.size());
  if (!is) return false;
  if (buf.size() < 8 + 8 * 4) return false;
  if (std::memcmp(buf.data(), MAGIC, 8) != 0) return false;
  const uint8_t* h = buf.data();
  uint32_t version = get32(h + 8);
  if (version != VERSION) return false;
  uint32_t nsec = get32(h + 12), nsym = get32(h + 16), nrel = get32(h + 20);
  uint32_t strSize = get32(h + 24), secTabOff = get32(h + 28);
  uint32_t symOff = get32(h + 32), relOff = get32(h + 36), strOff = get32(h + 40);
  if (secTabOff + nsec * SEC_HDR_SZ > buf.size()) return false;
  if (strOff + strSize > buf.size()) return false;

  auto strAt = [&](uint32_t o) -> std::string {
    if (o >= strSize) return "";
    const char* p = (const char*)(buf.data() + strOff + o);
    return std::string(p);
  };

  // sections
  for (uint32_t i = 0; i < nsec; ++i) {
    const uint8_t* s = buf.data() + secTabOff + i * SEC_HDR_SZ;
    auto sec = std::make_unique<Section>(
        strAt(get32(s)), (uint32_t)i,
        get32(s + 4) == ST_NOBITS ? SectionType::NOBITS : SectionType::PROGBITS,
        get32(s + 8), get32(s + 12));
    sec->memory_size = get32(s + 16);
    uint32_t doff = get32(s + 20), dsize = get32(s + 24);
    if (sec->type != SectionType::NOBITS && dsize > 0) {
      if (doff + dsize <= buf.size())
        sec->data.assign(buf.begin() + doff, buf.begin() + doff + dsize);
    }
    out.sections.push_back(std::move(sec));
  }

  // symbols
  for (uint32_t i = 0; i < nsym; ++i) {
    const uint8_t* r = buf.data() + symOff + i * SYM_SZ;
    Symbol sym;
    sym.name = strAt(get32(r));
    int32_t sid = (int32_t)get32(r + 4);
    sym.section_id = (sid >= 0) ? std::optional<uint32_t>((uint32_t)sid) : std::optional<uint32_t>();
    sym.value = get32(r + 8); sym.size = get32(r + 12);
    sym.binding = u32_to_binding(get32(r + 16)); sym.type = u32_to_type(get32(r + 20));
    uint32_t fl = get32(r + 24);
    sym.is_defined = (fl & 1u) != 0; sym.is_absolute = (fl & 2u) != 0;
    // register into the object's symbol table
    out.symbol_table.define_symbol(sym.name, sym.section_id, sym.value,
                                   SourceSpan{});
    // patch the stored symbol's extra fields (define_symbol resets some)
    if (auto* ps = out.symbol_table.find(sym.name)) {
      ps->size = sym.size; ps->binding = sym.binding; ps->type = sym.type;
      ps->is_defined = sym.is_defined; ps->is_absolute = sym.is_absolute;
      ps->id = i;
    }
  }

  // relocations -> fixups
  for (uint32_t i = 0; i < nrel; ++i) {
    const uint8_t* r = buf.data() + relOff + i * REL_SZ;
    Fixup f;
    f.kind = reloc_to_fixup_kind(get32(r));
    f.section_id = get32(r + 4); f.offset = get32(r + 8);
    f.symbol_name = strAt(get32(r + 12));
    f.addend = (int32_t)get32(r + 16);
    uint32_t fl = get32(r + 28);
    if (fl & 1u) f.group_id = get32(r + 20);
    if (fl & 2u) f.anchor_offset = get32(r + 24);
    f.is_local_numeric = (fl & 4u) != 0;
    out.fixups.push_back(f);
  }
  return true;
}

} // namespace an32obj
} // namespace an32asm
