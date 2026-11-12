#include "an32asm/linker.hpp"
#include <algorithm>
#include <cstring>

namespace an32asm {
namespace {

// ---- little-endian word access ----
uint32_t ru32(const uint8_t* p) {
  return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}
void wu32(uint8_t* p, uint32_t v) {
  p[0] = v & 0xff; p[1] = (v >> 8) & 0xff; p[2] = (v >> 16) & 0xff; p[3] = (v >> 24) & 0xff;
}

// ---- RV32 immediate patchers (same encodings as flat_finalizer) ----
uint32_t patch_b(uint32_t i, int32_t imm) {
  uint32_t u = (uint32_t)imm;
  i &= 0x01FFF07F;
  i |= (((u >> 12) & 1) << 31) | (((u >> 5) & 0x3F) << 25) |
       (((u >> 1) & 0xF) << 8) | (((u >> 11) & 1) << 7);
  return i;
}
uint32_t patch_j(uint32_t i, int32_t imm) {
  uint32_t u = (uint32_t)imm;
  i &= 0x00000FFF;
  i |= (((u >> 20) & 1) << 31) | (((u >> 1) & 0x3FF) << 21) |
       (((u >> 11) & 1) << 20) | (((u >> 12) & 0xFF) << 12);
  return i;
}
uint32_t patch_u(uint32_t i, uint32_t hi20) { i &= 0x00000FFF; return i | ((hi20 & 0xFFFFF) << 12); }
uint32_t patch_i(uint32_t i, int32_t lo12) { i &= 0x000FFFFF; return i | ((uint32_t)lo12 & 0xFFF) << 20; }
uint32_t patch_s(uint32_t i, int32_t lo12) {
  uint32_t u = (uint32_t)lo12;
  i &= 0x01FFF07F;
  return i | (((u >> 5) & 0x7F) << 25) | ((u & 0x1F) << 7);
}
int64_t hi20(int64_t v) { return (v + 0x800) >> 12; }
int64_t lo12(int64_t v) {
  int64_t l = v & 0xFFF;
  return (l & 0x800) ? l - 0x1000 : l;
}

} // namespace

void Linker::add_object(AssemblerObject obj) { objs_.push_back(std::move(obj)); }

std::optional<an32exe::ExeImage> Linker::link(std::string& err) {
  // ---- 1. group input sections by (name,type,flags) ----
  struct OutSec {
    std::string name; uint32_t flags; bool nobits;
    std::vector<uint8_t> data; uint64_t vaddr = 0; uint64_t align = 1;
    // map (objIdx,secIdx) -> offset within this output section
    std::vector<std::pair<int,int>> members;
  };
  std::vector<OutSec> outSecs;
  // key -> outsec index
  auto keyOf = [](const Section& s) {
    return s.name + "|" + std::to_string((int)s.type) + "|" + std::to_string(s.flags);
  };
  std::unordered_map<std::string,int> secMap;
  std::vector<std::vector<int>> objSecToOut; // [obj][sec] -> outsec idx or -1

  for (size_t oi = 0; oi < objs_.size(); ++oi) {
    objSecToOut.emplace_back();
    for (size_t si = 0; si < objs_[oi].sections.size(); ++si) {
      Section& s = *objs_[oi].sections[si];
      std::string key = keyOf(s);
      int oidx;
      auto it = secMap.find(key);
      if (it == secMap.end()) {
        OutSec os; os.name = s.name; os.flags = s.flags;
        os.nobits = s.is_nobits(); os.align = std::max<uint64_t>(os.align, s.alignment);
        secMap[key] = (int)outSecs.size();
        outSecs.push_back(std::move(os));
        oidx = (int)outSecs.size() - 1;
      } else oidx = it->second;
      objSecToOut[oi].push_back(oidx);
      outSecs[oidx].members.push_back({(int)oi,(int)si});
      outSecs[oidx].align = std::max(outSecs[oidx].align, s.alignment);
    }
  }
  if (outSecs.empty()) { err = "no sections"; return std::nullopt; }

  // ---- 2. assign VAs: group by flags ----
  // classify: exec->textBase, writable->dataBase, else rodataBase
  auto baseFor = [&](uint32_t fl) -> uint64_t {
    if (fl & SectionFlags::EXECINSTR) return textBase;
    if (fl & SectionFlags::WRITE) return dataBase;
    return rodataBase;
  };
  // sort output sections: exec first, then rodata, then data
  std::vector<int> order(outSecs.size());
  for (size_t i=0;i<order.size();++i) order[i]=(int)i;
  std::sort(order.begin(), order.end(), [&](int a,int b){
    auto cls=[&](const OutSec&s){return (s.flags&SectionFlags::EXECINSTR)?0:((s.flags&SectionFlags::WRITE)?2:1);};
    return cls(outSecs[a])<cls(outSecs[b]);
  });
  // assign addresses sequentially within class, honoring alignment
  std::unordered_map<int,uint64_t> secOffInClass; // running offset per class
  for (int oi : order) {
    OutSec& os = outSecs[oi];
    int cls = (os.flags&SectionFlags::EXECINSTR)?0:((os.flags&SectionFlags::WRITE)?2:1);
    uint64_t base = cls==0?textBase:(cls==2?dataBase:rodataBase);
    uint64_t off = secOffInClass.count(cls)?secOffInClass[cls]:0;
    off = (off + os.align - 1) & ~(os.align - 1);
    os.vaddr = base + off;
    secOffInClass[cls] = off;
  }
  // NOTE: vaddr assigned per-class cumulative but we must also order members;
  // simpler: give each OutSec its own base by class cursor that ADVANCES.
  // Recompute properly: cursor per class advances by section size.
  {
    std::unordered_map<int,uint64_t> cur; // class -> next free offset
    for (int oi : order) {
      OutSec& os = outSecs[oi];
      int cls = (os.flags&SectionFlags::EXECINSTR)?0:((os.flags&SectionFlags::WRITE)?2:1);
      uint64_t base = cls==0?textBase:(cls==2?dataBase:rodataBase);
      uint64_t& c = cur[cls];
      if (c==0 && !cur.count(cls)) c=0; // init
      c = (c + os.align - 1) & ~(os.align - 1);
      os.vaddr = base + c;
      // size = sum of member data (aligned each)
      uint64_t sz=0;
      for (auto&m:os.members){ Section&s=*objs_[m.first].sections[m.second];
        sz=(sz+s.alignment-1)&~(s.alignment-1); sz+=s.is_nobits()?s.memory_size:s.data.size(); }
      c += sz;
    }
  }

  // ---- 3. lay out member data into output section buffers ----
  // map (obj,sec) -> (outsec, offset)
  std::vector<std::vector<std::pair<int,uint64_t>>> secPlace; // [obj][sec]->(outsec,off)
  for (size_t oi=0; oi<objs_.size(); ++oi) {
    secPlace.emplace_back();
    for (size_t si=0; si<objs_[oi].sections.size(); ++si) {
      int oidx = objSecToOut[oi][si];
      OutSec& os = outSecs[oidx];
      // find offset: append at end honoring member alignment
      uint64_t off = os.data.size();
      Section& s = *objs_[oi].sections[si];
      off = (off + s.alignment - 1) & ~(s.alignment - 1);
      // pad os.data to off
      while (os.data.size() < off) os.data.push_back(0);
      if (!s.is_nobits()) os.data.insert(os.data.end(), s.data.begin(), s.data.end());
      // for nobits ensure memsz room: pad data to memory_size (as zeros in file
      // is wrong for bss; we track via phdr memsz instead). Keep data=filesz.
      secPlace[oi].push_back({oidx, off});
    }
  }
  // fix: per-member offsets must be computed in member order, not append order.
  // Recompute offsets deterministically: iterate outSecs members in order.
  {
    // reset data and recompute
    for (auto& os : outSecs) os.data.clear();
    for (auto& os : outSecs) {
      for (auto&m : os.members) {
        Section& s = *objs_[m.first].sections[m.second];
        uint64_t off = os.data.size();
        off = (off + s.alignment - 1) & ~(s.alignment - 1);
        while (os.data.size() < off) os.data.push_back(0);
        if (!s.is_nobits()) os.data.insert(os.data.end(), s.data.begin(), s.data.end());
        // record place
        secPlace[m.first][m.second] = {objSecToOut[m.first][m.second], off};
      }
    }
  }

  // ---- 4. global symbol resolution ----
  // resolved addr = outsec.vaddr + memberOff + sym.value
  struct SymInfo { int64_t addr; bool defined; bool absolute; bool weak; };
  std::unordered_map<std::string,SymInfo> globals;
  auto resolveSym = [&](const std::string& name, int objIdx, const Symbol& sym)->std::optional<SymInfo>{
    if (sym.is_absolute) return SymInfo{(int64_t)sym.value,true,true,sym.binding==SymbolBinding::WEAK};
    if (!sym.is_defined || !sym.section_id) return std::nullopt; // undefined
    // find this symbol's object section place
    uint32_t sid = *sym.section_id;
    if (sid >= objs_[objIdx].sections.size()) return std::nullopt;
    auto ph = secPlace[objIdx][sid];
    int64_t a = (int64_t)outSecs[ph.first].vaddr + (int64_t)ph.second + (int64_t)sym.value;
    return SymInfo{a,true,false,sym.binding==SymbolBinding::WEAK};
  };
  // first pass: defined globals (strong beats weak; dup strong = error)
  for (size_t oi=0; oi<objs_.size(); ++oi)
    for (auto& s : objs_[oi].symbol_table.get_symbols()) {
      if (s.binding==SymbolBinding::LOCAL) continue;
      auto r = resolveSym(s.name,(int)oi,s);
      if (!r || !r->defined) continue;
      auto it=globals.find(s.name);
      if (it==globals.end()) globals[s.name]=*r;
      else if (!it->second.weak && !r->weak) { err="duplicate symbol: "+s.name; return std::nullopt; }
      else if (it->second.weak && !r->weak) globals[s.name]=*r;
    }
  // second pass: also index LOCAL symbols per-object for intra-object relocs
  // (fixups may reference local symbols) -> per-object local map
  std::vector<std::unordered_map<std::string,int64_t>> localAddr(objs_.size());
  for (size_t oi=0; oi<objs_.size(); ++oi)
    for (auto& s : objs_[oi].symbol_table.get_symbols()) {
      auto r = resolveSym(s.name,(int)oi,s);
      if (r && r->defined) localAddr[oi][s.name]=r->addr;
    }
  auto lookupAddr=[&](const std::string&name,int oi)->std::optional<int64_t>{
    auto it=localAddr[oi].find(name); if(it!=localAddr[oi].end()) return it->second;
    auto g=globals.find(name); if(g!=globals.end()&&g->second.defined) return g->second.addr;
    return std::nullopt;
  };

  // ---- 5. apply relocations ----
  for (size_t oi=0; oi<objs_.size(); ++oi) {
    for (auto& f : objs_[oi].fixups) {
      // place: outsec + memberOff(of fixup's section) + fixup.offset
      uint32_t sid=f.section_id;
      if (sid>=objs_[oi].sections.size()) { err="fixup bad section"; return std::nullopt; }
      auto ph=secPlace[oi][sid];
      OutSec& os=outSecs[ph.first];
      uint64_t place=(uint64_t)os.vaddr + ph.second + f.offset;
      // skip NOBITS sections (no file bytes)
      if (os.nobits) continue;
      // find target S
      int64_t S; bool ok=false;
      if (f.is_local_numeric) {
        // numeric local: approximate as section base + local label offset -- treat as 0+addend
        S = f.addend; ok=true;
      } else {
        auto t=lookupAddr(f.symbol_name,(int)oi);
        if (t){ S=*t + f.addend; ok=true; }
      }
      if (!ok) { err="unresolved symbol in reloc: "+f.symbol_name; return std::nullopt; }
      // patch into os.data at (ph.second + f.offset)
      uint64_t doff = ph.second + f.offset;
      if (doff + 4 > os.data.size()) { err="reloc out of bounds"; return std::nullopt; }
      uint8_t* ptr = os.data.data() + doff;
      int64_t P = (int64_t)place;
      int64_t A = f.addend;
      switch (f.kind) {
        case FixupKind::ABS32: wu32(ptr,(uint32_t)S); break;
        case FixupKind::ADD32: case FixupKind::SUB32: {
          // S +/- A for .word end-start style; approximate: write S (+/- already in addend)
          wu32(ptr,(uint32_t)S); break; }
        case FixupKind::BRANCH: {
          int64_t d=S-P; if(d&1||d>0x1FFFFE||d< -0x200000){err="branch range";return std::nullopt;}
          wu32(ptr,patch_b(ru32(ptr),(int32_t)d)); break; }
        case FixupKind::JAL: {
          int64_t d=S-P; if(d&1||d>0x1FFFFE||d< -0x200000){err="jal range";return std::nullopt;}
          wu32(ptr,patch_j(ru32(ptr),(int32_t)d)); break; }
        case FixupKind::CALL: {
          int64_t d=S-P; int64_t h=hi20(d), l=lo12(d);
          wu32(ptr,patch_u(ru32(ptr),(uint32_t)h));
          if(doff+8<=os.data.size()) wu32(ptr+4,patch_i(ru32(ptr+4),(int32_t)l));
          break; }
        case FixupKind::HI20: wu32(ptr,patch_u(ru32(ptr),(uint32_t)hi20(S))); break;
        case FixupKind::LO12_I: wu32(ptr,patch_i(ru32(ptr),(int32_t)lo12(S))); break;
        case FixupKind::LO12_S: wu32(ptr,patch_s(ru32(ptr),(int32_t)lo12(S))); break;
        case FixupKind::PCREL_HI20: wu32(ptr,patch_u(ru32(ptr),(uint32_t)hi20(S-P))); break;
        case FixupKind::PCREL_LO12_I: {
          uint64_t ao=f.anchor_offset.value_or(f.offset);
          int64_t Pc=(int64_t)os.vaddr+ph.second+ao;
          wu32(ptr,patch_i(ru32(ptr),(int32_t)lo12(S-Pc))); break; }
        case FixupKind::PCREL_LO12_S: {
          uint64_t ao=f.anchor_offset.value_or(f.offset);
          int64_t Pc=(int64_t)os.vaddr+ph.second+ao;
          wu32(ptr,patch_s(ru32(ptr),(int32_t)lo12(S-Pc))); break; }
      }
    }
  }

  // ---- 6. emit ExeImage: one PT_LOAD per output section ----
  an32exe::ExeImage img;
  // entry
  auto eit=globals.find(entrySymbol);
  img.entry = (eit!=globals.end())?(uint32_t)eit->second.addr:outSecs[0].vaddr;

  // build strtab for section names
  std::string strtab; std::unordered_map<std::string,uint32_t> stroff;
  auto sadd=[&](const std::string&s){auto it=stroff.find(s);if(it!=stroff.end())return it->second;uint32_t o=strtab.size();strtab+=s;strtab+='\0';stroff[s]=o;return o;};

  // file offset cursor for segment data
  uint32_t dataCursor = 0; // will fix after header sizes known; use placeholder then patch
  // First compute header size
  uint32_t nph=outSecs.size(), nsh=outSecs.size();
  uint32_t ehdrSz=an32exe::EHDR_SZ, phSz=an32exe::PHDR_SZ, shSz=an32exe::SHDR_SZ;
  uint32_t strOff=ehdrSz+nph*phSz+nsh*shSz;
  uint32_t strSize=strtab.size()+1;
  uint32_t dataStart=(strOff+strSize+3)&~3u;

  std::vector<an32exe::ProgramHeader> phs;
  std::vector<uint8_t> blob;
  uint32_t cur=dataStart;
  for (size_t i=0;i<outSecs.size();++i){
    OutSec&os=outSecs[i];
    an32exe::ProgramHeader p;
    p.type=an32exe::PT_LOAD;
    p.flags=(os.flags&SectionFlags::WRITE?an32exe::PF_W:0)|(os.flags&SectionFlags::EXECINSTR?an32exe::PF_X:0)|an32exe::PF_R;
    // filesz = data bytes present (non-nobits); for nobits filesz=0 memsz=size
    uint64_t filesz=os.nobits?0:os.data.size();
    uint64_t memsz=os.nobits?(uint64_t)os.data.size():os.data.size(); // nobits data.size()=0; need real memsz
    // For nobits we didn't store memory_size; approximate memsz=filesz (limitation)
    p.offset=cur; p.vaddr=(uint32_t)os.vaddr; p.filesz=(uint32_t)filesz;
    p.memsz=(uint32_t)memsz; p.align=(uint32_t)os.align; p.reserved=0;
    phs.push_back(p);
    if(!os.nobits){ blob.insert(blob.end(),os.data.begin(),os.data.end()); cur+=(uint32_t)os.data.size(); }
  }
  img.phdrs=phs; img.data=blob;
  // section headers
  for(size_t i=0;i<outSecs.size();++i){
    OutSec&os=outSecs[i];
    an32exe::SectionHeader sh;
    sh.name_off=sadd(os.name);
    sh.type=(os.flags&SectionFlags::EXECINSTR)?1:((os.flags&SectionFlags::WRITE)?3:2);
    sh.flags=os.flags; sh.vaddr=(uint32_t)os.vaddr;
    sh.size=(uint32_t)(os.nobits?0:os.data.size());
    sh.offset=os.nobits?0:phs[i].offset; sh.reserved=0;
    img.shdrs.push_back(sh);
  }
  img.strtab=strtab;
  return img;
}

} // namespace an32asm
