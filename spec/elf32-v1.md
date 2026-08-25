# AI-Native ELF32 Binary Object & Executable Specification v1.0

**Author**: Advanced Agentic Coding & Systems Engineering  
**Status**: APPROVED ARCHITECTURAL CONTRACT  
**Standard Reference**: System V ABI & RISC-V ELF32 psABI  

---

## 1. ELF32 File Layout Overview

The AN32 Toolchain produces and consumes standard **ELF32 (32-bit Little-Endian)** binaries:
- **Relocatable Objects (`ET_REL = 1`)**: Produced by the Assembler.
- **Executables (`ET_EXEC = 2`)**: Produced by the Linker, consumed by the Loader.
- **Dynamic Objects (`ET_DYN = 3`)**: Reserved for future position-independent shared libraries.

```
┌─────────────────────────────────────────────────────────┐
│                    ELF32 Header (52 B)                  │
├─────────────────────────────────────────────────────────┤
│            Program Header Table (PT_LOAD Segments)      │
├─────────────────────────────────────────────────────────┤
│  .text Section (Executable Code)                        │
├─────────────────────────────────────────────────────────┤
│  .rodata Section (Read-Only String & Numeric Constants) │
├─────────────────────────────────────────────────────────┤
│  .data Section (Initialized Global Variables)           │
├─────────────────────────────────────────────────────────┤
│  .bss Section (Uninitialized Data - SHT_NOBITS)         │
├─────────────────────────────────────────────────────────┤
│  .symtab Section (Symbol Table)                         │
├─────────────────────────────────────────────────────────┤
│  .strtab Section (String Table)                         │
├─────────────────────────────────────────────────────────┤
│  .rela.text / .rela.data (Relocation Tables)            │
├─────────────────────────────────────────────────────────┤
│  .shstrtab Section (Section Name String Table)          │
├─────────────────────────────────────────────────────────┤
│             Section Header Table (Shdr entries)         │
└─────────────────────────────────────────────────────────┘
```

---

## 2. ELF Header Structure (`Elf32_Ehdr` — 52 Bytes)

```c
typedef struct {
    unsigned char e_ident[16]; /* Magic: 0x7F 'E' 'L' 'F', ELFCLASS32(1), ELFDATA2LSB(1), EV_CURRENT(1) */
    uint16_t      e_type;      /* ET_REL(1), ET_EXEC(2), ET_DYN(3) */
    uint16_t      e_machine;   /* EM_RISCV = 243 (0x00F3) */
    uint32_t      e_version;   /* EV_CURRENT = 1 */
    uint32_t      e_entry;     /* Link-time virtual entry address (e.g. 0x00010000 or 0x00000000) */
    uint32_t      e_phoff;     /* Program header table file offset in bytes */
    uint32_t      e_shoff;     /* Section header table file offset in bytes */
    uint32_t      e_flags;     /* EF_RISCV_RVC(0), EF_RISCV_FLOAT_ABI_SOFT(0x0000) */
    uint16_t      e_ehsize;    /* Size of this header: 52 bytes */
    uint16_t      e_phentsize; /* Size of program header entry: 32 bytes */
    uint16_t      e_phnum;     /* Number of program header entries */
    uint16_t      e_shentsize; /* Size of section header entry: 40 bytes */
    uint16_t      e_shnum;     /* Number of section header entries */
    uint16_t      e_shstrndx;  /* Section header index of .shstrtab */
} Elf32_Ehdr;
```

---

## 3. Program Header Structure (`Elf32_Phdr` — 32 Bytes)

In executable binaries (`ET_EXEC`), program headers define how memory segments are mapped into the virtual address space:

```c
typedef struct {
    uint32_t p_type;   /* PT_LOAD = 1 */
    uint32_t p_offset; /* File offset where segment begins */
    uint32_t p_vaddr;  /* Virtual address where segment must be loaded */
    uint32_t p_paddr;  /* Physical address (matching vaddr in flat systems) */
    uint32_t p_filesz; /* Number of bytes in file image */
    uint32_t p_memsz;  /* Number of bytes in memory (memsz >= filesz; diff zeroed for .bss) */
    uint32_t p_flags;  /* PF_X (1) | PF_W (2) | PF_R (4) */
    uint32_t p_align;  /* Segment alignment (4096 bytes for Sv32 pages) */
} Elf32_Phdr;
```

---

## 4. Section Header Structure (`Elf32_Shdr` — 40 Bytes)

```c
typedef struct {
    uint32_t sh_name;      /* Name offset in .shstrtab */
    uint32_t sh_type;      /* SHT_PROGBITS(1), SHT_SYMTAB(2), SHT_STRTAB(3), SHT_RELA(4), SHT_NOBITS(8) */
    uint32_t sh_flags;     /* SHF_WRITE(1), SHF_ALLOC(2), SHF_EXECINSTR(4) */
    uint32_t sh_addr;      /* Link-time virtual address of section */
    uint32_t sh_offset;    /* File offset of section */
    uint32_t sh_size;      /* Section size in bytes */
    uint32_t sh_link;      /* Linked section index (e.g. associated string table) */
    uint32_t sh_info;      /* Extra section info */
    uint32_t sh_addralign; /* Address alignment constraint (e.g. 4 or 16) */
    uint32_t sh_entsize;   /* Entry size if section contains fixed-size records */
} Elf32_Shdr;
```

---

## 5. Symbol Table (`Elf32_Sym` — 16 Bytes)

```c
typedef struct {
    uint32_t      st_name;  /* Symbol name offset in .strtab */
    uint32_t      st_value; /* Symbol value (section-relative offset or virtual address) */
    uint32_t      st_size;  /* Size of object / function in bytes */
    unsigned char st_info;  /* [7:4] Binding (STB_LOCAL=0, STB_GLOBAL=1), [3:0] Type (STT_NOTYPE=0, STT_OBJECT=1, STT_FUNC=2) */
    unsigned char st_other; /* Symbol visibility (STV_DEFAULT=0) */
    uint16_t      st_shndx; /* Section index (or SHN_UNDEF=0, SHN_ABS=0xFFF1, SHN_COMMON=0xFFF2) */
} Elf32_Sym;
```

---

## 6. Relocation Record (`Elf32_Rela` — 12 Bytes)

```c
typedef struct {
    uint32_t r_offset; /* Location (section offset) where relocation must be applied */
    uint32_t r_info;   /* [31:8] Symbol table index, [7:0] Relocation type */
    int32_t  r_addend; /* Explicit signed addend */
} Elf32_Rela;
```
