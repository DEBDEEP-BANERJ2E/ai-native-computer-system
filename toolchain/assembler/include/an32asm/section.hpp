#pragma once

#include "fragment.hpp"
#include <string>
#include <vector>
#include <memory>
#include <cstdint>
#include <optional>

namespace an32asm {

enum class SectionType {
    PROGBITS,
    NOBITS
};

namespace SectionFlags {
    constexpr uint32_t ALLOC     = 1 << 0; // "a"
    constexpr uint32_t WRITE     = 1 << 1; // "w"
    constexpr uint32_t EXECINSTR = 1 << 2; // "x"
}

class Section {
public:
    std::string name;
    uint32_t id = 0;
    SectionType type = SectionType::PROGBITS;
    uint32_t flags = SectionFlags::ALLOC;
    uint64_t alignment = 4;
    uint64_t memory_size = 0;           // Total size occupied in memory
    std::vector<uint8_t> data;          // Payload bytes (empty for NOBITS)
    std::vector<FragmentPtr> fragments;

    Section(std::string name_, uint32_t id_, SectionType type_, uint32_t flags_, uint64_t align_ = 4)
        : name(std::move(name_)), id(id_), type(type_), flags(flags_), alignment(align_) {}

    bool is_executable() const noexcept { return (flags & SectionFlags::EXECINSTR) != 0; }
    bool is_writable() const noexcept { return (flags & SectionFlags::WRITE) != 0; }
    bool is_nobits() const noexcept { return type == SectionType::NOBITS; }

    void add_fragment(FragmentPtr frag);
};

class SectionTable {
public:
    SectionTable();

    Section* get_or_create(const std::string& name, SectionType type = SectionType::PROGBITS, uint32_t flags = SectionFlags::ALLOC);
    Section* find(const std::string& name);
    const Section* find(const std::string& name) const;
    Section* find_by_id(uint32_t id);
    const Section* find_by_id(uint32_t id) const;

    Section* get_current_section() noexcept { return current_section_; }
    void set_current_section(Section* sec) noexcept { current_section_ = sec; }

    const std::vector<std::unique_ptr<Section>>& get_sections() const noexcept { return sections_; }
    std::vector<std::unique_ptr<Section>> extract_sections() noexcept { return std::move(sections_); }
    void clear();

private:
    std::vector<std::unique_ptr<Section>> sections_;
    Section* current_section_ = nullptr;
};

} // namespace an32asm
