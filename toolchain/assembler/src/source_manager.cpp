#include "an32asm/source_manager.hpp"
#include <fstream>
#include <sstream>
#include <algorithm>

namespace an32asm {

SourceBuffer::SourceBuffer(uint32_t id_, std::string filename_, std::string content_)
    : id(id_), filename(std::move(filename_)), content(std::move(content_)) {
    line_offsets.push_back(0); // Line 1 begins at offset 0
    for (uint32_t i = 0; i < content.size(); ++i) {
        if (content[i] == '\n') {
            line_offsets.push_back(i + 1);
        }
    }
}

SourcePos SourceBuffer::get_pos(uint32_t byte_offset) const {
    SourcePos pos;
    pos.file_id = id;
    pos.offset = byte_offset;

    if (line_offsets.empty()) {
        pos.line = 1;
        pos.column = byte_offset + 1;
        return pos;
    }

    // Binary search for line index
    auto it = std::upper_bound(line_offsets.begin(), line_offsets.end(), byte_offset);
    size_t line_idx = std::distance(line_offsets.begin(), it); // 1-based line number
    pos.line = static_cast<uint32_t>(line_idx);
    uint32_t line_start = line_offsets[line_idx - 1];
    pos.column = (byte_offset >= line_start) ? (byte_offset - line_start + 1) : 1;
    return pos;
}

std::string_view SourceBuffer::get_line_text(uint32_t line_num) const {
    if (line_num == 0 || line_num > line_offsets.size()) {
        return "";
    }
    uint32_t start = line_offsets[line_num - 1];
    uint32_t end = (line_num < line_offsets.size()) ? line_offsets[line_num] : static_cast<uint32_t>(content.size());
    // Strip trailing \r and \n
    while (end > start && (content[end - 1] == '\n' || content[end - 1] == '\r')) {
        --end;
    }
    return std::string_view(content.data() + start, end - start);
}

SourceManager::SourceManager() = default;

uint32_t SourceManager::add_buffer(std::string filename, std::string content) {
    uint32_t id = static_cast<uint32_t>(buffers_.size());
    buffers_.push_back(std::make_unique<SourceBuffer>(id, std::move(filename), std::move(content)));
    return id;
}

std::optional<uint32_t> SourceManager::load_file(const std::string& filepath) {
    std::ifstream file(filepath, std::ios::in | std::ios::binary);
    if (!file.is_open()) {
        return std::nullopt;
    }
    std::ostringstream ss;
    ss << file.rdbuf();
    return add_buffer(filepath, ss.str());
}

const SourceBuffer* SourceManager::get_buffer(uint32_t file_id) const {
    if (file_id < buffers_.size()) {
        return buffers_[file_id].get();
    }
    return nullptr;
}

const std::string& SourceManager::get_filename(uint32_t file_id) const {
    static const std::string empty;
    const auto* buf = get_buffer(file_id);
    return buf ? buf->filename : empty;
}

std::string_view SourceManager::get_line_text(uint32_t file_id, uint32_t line_num) const {
    const auto* buf = get_buffer(file_id);
    return buf ? buf->get_line_text(line_num) : std::string_view("");
}

SourcePos SourceManager::get_pos(uint32_t file_id, uint32_t byte_offset) const {
    const auto* buf = get_buffer(file_id);
    if (buf) {
        return buf->get_pos(byte_offset);
    }
    return SourcePos{file_id, 1, byte_offset + 1, byte_offset};
}

} // namespace an32asm
