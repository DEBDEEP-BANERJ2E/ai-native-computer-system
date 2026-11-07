#include "an32asm/section.hpp"

namespace an32asm {

void Section::add_fragment(FragmentPtr frag) {
    fragments.push_back(std::move(frag));
}

SectionTable::SectionTable() = default;

Section* SectionTable::get_or_create(const std::string& name, SectionType type, uint32_t flags) {
    for (auto& sec : sections_) {
        if (sec->name == name) {
            return sec.get();
        }
    }

    uint32_t id = static_cast<uint32_t>(sections_.size());
    auto sec = std::make_unique<Section>(name, id, type, flags);
    Section* ptr = sec.get();
    sections_.push_back(std::move(sec));
    if (!current_section_) {
        current_section_ = ptr;
    }
    return ptr;
}

Section* SectionTable::find(const std::string& name) {
    for (auto& sec : sections_) {
        if (sec->name == name) {
            return sec.get();
        }
    }
    return nullptr;
}

const Section* SectionTable::find(const std::string& name) const {
    for (const auto& sec : sections_) {
        if (sec->name == name) {
            return sec.get();
        }
    }
    return nullptr;
}

Section* SectionTable::find_by_id(uint32_t id) {
    if (id < sections_.size()) {
        return sections_[id].get();
    }
    return nullptr;
}

const Section* SectionTable::find_by_id(uint32_t id) const {
    if (id < sections_.size()) {
        return sections_[id].get();
    }
    return nullptr;
}

void SectionTable::clear() {
    sections_.clear();
    current_section_ = nullptr;
}

} // namespace an32asm
