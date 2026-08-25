#pragma once

#include "source_location.hpp"
#include <string>
#include <vector>
#include <string_view>
#include <memory>
#include <optional>

namespace an32asm {

struct SourceBuffer {
    uint32_t id;
    std::string filename;
    std::string content;
    std::vector<uint32_t> line_offsets; // Byte offset where each 1-indexed line begins

    SourceBuffer(uint32_t id_, std::string filename_, std::string content_);
    
    SourcePos get_pos(uint32_t byte_offset) const;
    std::string_view get_line_text(uint32_t line_num) const;
};

class SourceManager {
public:
    SourceManager();

    uint32_t add_buffer(std::string filename, std::string content);
    std::optional<uint32_t> load_file(const std::string& filepath);

    const SourceBuffer* get_buffer(uint32_t file_id) const;
    const std::string& get_filename(uint32_t file_id) const;
    std::string_view get_line_text(uint32_t file_id, uint32_t line_num) const;

    SourcePos get_pos(uint32_t file_id, uint32_t byte_offset) const;

private:
    std::vector<std::unique_ptr<SourceBuffer>> buffers_;
};

} // namespace an32asm
