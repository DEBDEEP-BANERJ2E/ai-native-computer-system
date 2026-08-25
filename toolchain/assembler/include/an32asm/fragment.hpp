#pragma once

#include "source_location.hpp"
#include "ast.hpp"
#include <cstdint>
#include <vector>
#include <memory>
#include <optional>
#include <string>

namespace an32asm {

enum class FragmentKind {
    INSTRUCTION,
    DATA,
    ZERO_FILL,
    ALIGN
};

class Fragment {
public:
    FragmentKind kind;
    uint64_t offset = 0; // Offset in section after layout
    SourceSpan span;

    Fragment(FragmentKind k, SourceSpan sp) : kind(k), span(sp) {}
    virtual ~Fragment() = default;

    virtual uint64_t get_size() const = 0;
};

using FragmentPtr = std::unique_ptr<Fragment>;

class InstructionFragment : public Fragment {
public:
    std::string mnemonic;
    std::vector<OperandPtr> operands;
    uint32_t raw_word = 0; // Encoded 32-bit machine word

    InstructionFragment(std::string mnem, std::vector<OperandPtr> ops, SourceSpan sp)
        : Fragment(FragmentKind::INSTRUCTION, sp), mnemonic(std::move(mnem)), operands(std::move(ops)) {}

    uint64_t get_size() const override { return 4; }
};

class DataFragment : public Fragment {
public:
    std::vector<uint8_t> data;

    DataFragment(std::vector<uint8_t> d, SourceSpan sp)
        : Fragment(FragmentKind::DATA, sp), data(std::move(d)) {}

    uint64_t get_size() const override { return data.size(); }
};

class ZeroFillFragment : public Fragment {
public:
    uint64_t count = 0;

    ZeroFillFragment(uint64_t cnt, SourceSpan sp)
        : Fragment(FragmentKind::ZERO_FILL, sp), count(cnt) {}

    uint64_t get_size() const override { return count; }
};

class AlignFragment : public Fragment {
public:
    uint64_t alignment_bytes = 1;     // e.g. 4, 8, 16
    std::optional<uint8_t> fill_byte; // If nullopt, use default section policy (NOP for text, 0 for data)
    uint64_t max_bytes_to_pad = 0;    // 0 = unbounded

    AlignFragment(uint64_t align, std::optional<uint8_t> fill, uint64_t max_pad, SourceSpan sp)
        : Fragment(FragmentKind::ALIGN, sp), alignment_bytes(align), fill_byte(fill), max_bytes_to_pad(max_pad) {}

    uint64_t get_size() const override { return 0; }
};

} // namespace an32asm
