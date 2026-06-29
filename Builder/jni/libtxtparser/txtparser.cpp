#include "txtparser.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <ctype.h>

#ifdef __ANDROID__
#include <android/log.h>
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "TxtParser", __VA_ARGS__)
#else
#define LOGD(...) printf("[TxtParser] " __VA_ARGS__)
#endif



static TxtEncoding detect_encoding(uint8_t* data, uint64_t size);
static int is_utf8_bom(uint8_t* data);
static int is_utf16le_bom(uint8_t* data);
static int is_utf16be_bom(uint8_t* data);
static int detect_encoding_by_stats(uint8_t* data, uint64_t size);
static uint64_t gbk_to_utf8(uint8_t* src, uint64_t src_len, char* dst, uint64_t dst_len);
static uint64_t utf16_to_utf8(uint8_t* src, uint64_t src_len, int little_endian, char* dst, uint64_t dst_len);
static void find_sections(TxtParserImpl* parser);
static int is_section_title(uint8_t* start, uint64_t max_len, TxtEncoding encoding);
static void write_html_header(FILE* fp, const char* title);
static void write_html_footer(FILE* fp);
static void write_epub_container(const char* output_path);
static void write_epub_content_opf(TxtParserImpl* parser, const char* output_path);
static void write_epub_chapter(TxtParserImpl* parser, int section_idx, const char* output_path);
static void create_epub_zip(const char* output_path);

TxtParser* txt_parser_open(const char* filepath) {
    TxtParserImpl* parser = (TxtParserImpl*)malloc(sizeof(TxtParserImpl));
    if (!parser) {
        LOGD("Failed to allocate parser memory\n");
        return NULL;
    }
    memset(parser, 0, sizeof(TxtParserImpl));
    strcpy(parser->section_pattern, "第[一二三四五六七八九十百千万0-9]+[章回卷节]");

    int fd = open(filepath, O_RDONLY);
    if (fd < 0) {
        LOGD("Failed to open file: %s\n", strerror(errno));
        free(parser);
        return NULL;
    }
    parser->fd = fd;

    struct stat st;
    if (fstat(fd, &st) != 0) {
        LOGD("Failed to get file stat: %s\n", strerror(errno));
        close(fd);
        free(parser);
        return NULL;
    }
    parser->file_size = st.st_size;

    if (parser->file_size == 0) {
        LOGD("Empty file\n");
        close(fd);
        free(parser);
        return NULL;
    }

    uint8_t* data = (uint8_t*)mmap(NULL, parser->file_size, PROT_READ, MAP_SHARED, fd, 0);
    if (data == MAP_FAILED) {
        LOGD("Failed to mmap file: %s\n", strerror(errno));
        close(fd);
        free(parser);
        return NULL;
    }
    parser->data = data;

    parser->encoding = detect_encoding(data, parser->file_size);
    LOGD("Detected encoding: %d\n", parser->encoding);

    find_sections(parser);
    LOGD("Found %d sections\n", parser->section_count);

    if (parser->section_count > 0) {
        strncpy(parser->title, parser->sections[0].name, TXT_PARSER_SECTION_NAME_LEN - 1);
    } else {
        const char* filename = strrchr(filepath, '/');
        if (!filename) filename = strrchr(filepath, '\\');
        if (!filename) filename = filepath;
        else filename++;
        strncpy(parser->title, filename, TXT_PARSER_SECTION_NAME_LEN - 1);
        size_t dot = strlen(parser->title);
        while (dot > 0 && parser->title[dot - 1] != '.') dot--;
        if (dot > 0) parser->title[dot - 1] = '\0';
    }

    return (TxtParser*)parser;
}

void txt_parser_close(TxtParser* parser) {
    if (!parser) return;
    TxtParserImpl* impl = (TxtParserImpl*)parser;

    if (impl->data && impl->data != MAP_FAILED) {
        munmap(impl->data, impl->file_size);
    }
    if (impl->fd >= 0) {
        close(impl->fd);
    }

    for (int i = 0; i < impl->section_count; i++) {
        if (impl->sections[i].name) {
            free((void*)impl->sections[i].name);
        }
    }

    free(impl);
}

void txt_parser_set_section_pattern(TxtParser* parser, const char* pattern) {
    if (!parser || !pattern) return;
    TxtParserImpl* impl = (TxtParserImpl*)parser;
    strncpy(impl->section_pattern, pattern, sizeof(impl->section_pattern) - 1);
}

static TxtEncoding detect_encoding(uint8_t* data, uint64_t size) {
    if (size >= 3 && is_utf8_bom(data)) return TXT_ENCODING_UTF8;
    if (size >= 2 && is_utf16le_bom(data)) return TXT_ENCODING_UTF16_LE;
    if (size >= 2 && is_utf16be_bom(data)) return TXT_ENCODING_UTF16_BE;
    return (TxtEncoding)detect_encoding_by_stats(data, size);
}

static int is_utf8_bom(uint8_t* data) {
    return data[0] == 0xEF && data[1] == 0xBB && data[2] == 0xBF;
}

static int is_utf16le_bom(uint8_t* data) {
    return data[0] == 0xFF && data[1] == 0xFE;
}

static int is_utf16be_bom(uint8_t* data) {
    return data[0] == 0xFE && data[1] == 0xFF;
}

static int detect_encoding_by_stats(uint8_t* data, uint64_t size) {
    int utf8_valid = 0, utf8_total = 0;
    int gbk_valid = 0, gbk_total = 0;
    int i = 0;

    while (i < (int)size) {
        if ((data[i] & 0xF0) == 0xE0) {
            utf8_total++;
            if (i + 2 < (int)size &&
                (data[i + 1] & 0xC0) == 0x80 &&
                (data[i + 2] & 0xC0) == 0x80) {
                utf8_valid++;
            }
            i += 3;
        } else if ((data[i] & 0xE0) == 0xC0) {
            utf8_total++;
            if (i + 1 < (int)size && (data[i + 1] & 0xC0) == 0x80) {
                utf8_valid++;
            }
            i += 2;
        } else if ((data[i] & 0xF8) == 0xF0) {
            utf8_total++;
            if (i + 3 < (int)size &&
                (data[i + 1] & 0xC0) == 0x80 &&
                (data[i + 2] & 0xC0) == 0x80 &&
                (data[i + 3] & 0xC0) == 0x80) {
                utf8_valid++;
            }
            i += 4;
        } else if (data[i] >= 0x81 && data[i] <= 0xFE && i + 1 < (int)size) {
            gbk_total++;
            uint8_t second = data[i + 1];
            if ((second >= 0x40 && second <= 0x7E) || (second >= 0x80 && second <= 0xFE)) {
                gbk_valid++;
            }
            i += 2;
        } else {
            i++;
        }
    }

    if (utf8_total > 0 && (double)utf8_valid / utf8_total > 0.95) {
        return TXT_ENCODING_UTF8;
    }
    if (gbk_total > 0 && (double)gbk_valid / gbk_total > 0.95) {
        return TXT_ENCODING_GBK;
    }
    return TXT_ENCODING_UTF8;
}

static uint64_t gbk_to_utf8(uint8_t* src, uint64_t src_len, char* dst, uint64_t dst_len) {
    uint64_t src_pos = 0, dst_pos = 0;
    while (src_pos < src_len && dst_pos + 4 < dst_len) {
        if (src[src_pos] < 0x80) {
            dst[dst_pos++] = src[src_pos++];
        } else if (src[src_pos] >= 0x81 && src[src_pos] <= 0xFE && src_pos + 1 < src_len) {
            uint16_t gbk = (src[src_pos] << 8) | src[src_pos + 1];
            src_pos += 2;

            if (gbk >= 0x8140 && gbk <= 0xFEFE) {
                uint32_t unicode = 0;
                if (gbk >= 0x8140 && gbk <= 0xA0FE) {
                    unicode = (gbk - 0x8140) * 0x100 + (gbk & 0xFF);
                    if (unicode >= 0x0000 && unicode <= 0xFFFF) {
                        unicode = (unicode >= 0x8080) ? (unicode + 0xF800) : unicode;
                    }
                } else {
                    uint8_t high = (gbk >> 8) - 0xA1;
                    uint8_t low = (gbk & 0xFF) - 0x40;
                    unicode = (high * 0x5E + low) + 0x4E00;
                }

                if (unicode <= 0x7F) {
                    dst[dst_pos++] = unicode;
                } else if (unicode <= 0x7FF) {
                    dst[dst_pos++] = 0xC0 | (unicode >> 6);
                    dst[dst_pos++] = 0x80 | (unicode & 0x3F);
                } else if (unicode <= 0xFFFF) {
                    dst[dst_pos++] = 0xE0 | (unicode >> 12);
                    dst[dst_pos++] = 0x80 | ((unicode >> 6) & 0x3F);
                    dst[dst_pos++] = 0x80 | (unicode & 0x3F);
                }
            } else {
                dst[dst_pos++] = '?';
            }
        } else {
            dst[dst_pos++] = src[src_pos++];
        }
    }
    dst[dst_pos] = '\0';
    return dst_pos;
}

static uint64_t utf16_to_utf8(uint8_t* src, uint64_t src_len, int little_endian, char* dst, uint64_t dst_len) {
    uint64_t src_pos = 0, dst_pos = 0;
    while (src_pos + 1 < src_len && dst_pos + 4 < dst_len) {
        uint16_t utf16;
        if (little_endian) {
            utf16 = (src[src_pos + 1] << 8) | src[src_pos];
        } else {
            utf16 = (src[src_pos] << 8) | src[src_pos + 1];
        }
        src_pos += 2;

        if (utf16 <= 0x7F) {
            dst[dst_pos++] = utf16;
        } else if (utf16 <= 0x7FF) {
            dst[dst_pos++] = 0xC0 | (utf16 >> 6);
            dst[dst_pos++] = 0x80 | (utf16 & 0x3F);
        } else if (utf16 >= 0xD800 && utf16 <= 0xDFFF) {
            if (src_pos + 1 < src_len) {
                uint16_t utf16_2;
                if (little_endian) {
                    utf16_2 = (src[src_pos + 1] << 8) | src[src_pos];
                } else {
                    utf16_2 = (src[src_pos] << 8) | src[src_pos + 1];
                }
                src_pos += 2;
                uint32_t unicode = 0x10000 + ((utf16 - 0xD800) << 10) + (utf16_2 - 0xDC00);
                dst[dst_pos++] = 0xF0 | (unicode >> 18);
                dst[dst_pos++] = 0x80 | ((unicode >> 12) & 0x3F);
                dst[dst_pos++] = 0x80 | ((unicode >> 6) & 0x3F);
                dst[dst_pos++] = 0x80 | (unicode & 0x3F);
            }
        } else {
            dst[dst_pos++] = 0xE0 | (utf16 >> 12);
            dst[dst_pos++] = 0x80 | ((utf16 >> 6) & 0x3F);
            dst[dst_pos++] = 0x80 | (utf16 & 0x3F);
        }
    }
    dst[dst_pos] = '\0';
    return dst_pos;
}

static uint64_t decode_to_utf8(TxtParserImpl* parser, uint8_t* src, uint64_t src_len, char* dst, uint64_t dst_len) {
    uint64_t bom_offset = 0;
    if (parser->encoding == TXT_ENCODING_UTF8 && src_len >= 3 && is_utf8_bom(src)) {
        bom_offset = 3;
    } else if ((parser->encoding == TXT_ENCODING_UTF16_LE || parser->encoding == TXT_ENCODING_UTF16_BE) && src_len >= 2) {
        bom_offset = 2;
    }

    if (src_len <= bom_offset) {
        dst[0] = '\0';
        return 0;
    }

    switch (parser->encoding) {
        case TXT_ENCODING_GBK:
        case TXT_ENCODING_GB18030:
            return gbk_to_utf8(src + bom_offset, src_len - bom_offset, dst, dst_len);
        case TXT_ENCODING_UTF16_LE:
            return utf16_to_utf8(src + bom_offset, src_len - bom_offset, 1, dst, dst_len);
        case TXT_ENCODING_UTF16_BE:
            return utf16_to_utf8(src + bom_offset, src_len - bom_offset, 0, dst, dst_len);
        case TXT_ENCODING_BIG5:
        case TXT_ENCODING_ISO8859_1:
        case TXT_ENCODING_UTF8:
        default:
            memcpy(dst, src + bom_offset, src_len - bom_offset);
            dst[src_len - bom_offset] = '\0';
            return src_len - bom_offset;
    }
}

static int is_section_title(uint8_t* start, uint64_t max_len, TxtEncoding encoding) {
    uint64_t i = 0;

    while (i < max_len && (start[i] == ' ' || start[i] == '\t')) {
        i++;
    }
    if (i >= max_len) return 0;

    if (encoding == TXT_ENCODING_UTF8) {
        if (i + 3 <= max_len &&
            start[i] == 0xE7 && start[i + 1] == 0xAC && start[i + 2] == 0xAC) {
            return 1;
        }
        if (i + 3 <= max_len &&
            start[i] == 0xE7 && start[i + 1] == 0xAD && start[i + 2] == 0x9A) {
            return 1;
        }
        if (i + 3 <= max_len &&
            start[i] == 0xE7 && start[i + 1] == 0xBA && start[i + 2] == 0xB5) {
            return 1;
        }
        if (i + 3 <= max_len &&
            start[i] == 0xE8 && start[i + 1] == 0x8A && start[i + 2] == 0x82) {
            return 1;
        }
    } else if (encoding == TXT_ENCODING_GBK) {
        if (i + 1 <= max_len && start[i] == 0xB5 && start[i + 1] == 0xDA) {
            return 1;
        }
        if (i + 1 <= max_len && start[i] == 0xD5 && start[i + 1] == 0xC2) {
            return 1;
        }
        if (i + 1 <= max_len && start[i] == 0xBE && start[i + 1] == 0xED) {
            return 1;
        }
        if (i + 1 <= max_len && start[i] == 0xBD && start[i + 1] == 0xDA) {
            return 1;
        }
    }

    if (isdigit(start[i])) {
        return 1;
    }

    return 0;
}

static void find_sections(TxtParserImpl* parser) {
    uint64_t pos = 0;
    uint64_t line_start = 0;
    int in_section = 0;
    uint64_t section_start = 0;

    parser->section_count = 0;

    while (pos < parser->file_size) {
        if (parser->data[pos] == '\n' || parser->data[pos] == '\r') {
            uint64_t line_len = pos - line_start;

            if (line_len > 2 && line_len < 200 && is_section_title(parser->data + line_start, line_len, parser->encoding)) {
                if (in_section && parser->section_count > 0) {
                    uint64_t prev_section = parser->section_count - 1;
                    parser->sections[prev_section].length = line_start - parser->sections[prev_section].offset;
                }

                char title[TXT_PARSER_SECTION_NAME_LEN];
                decode_to_utf8(parser, parser->data + line_start, line_len, title, sizeof(title));

                char* cleaned = title;
                while (*cleaned == ' ' || *cleaned == '\t') cleaned++;
                size_t len = strlen(cleaned);
                while (len > 0 && (cleaned[len - 1] == ' ' || cleaned[len - 1] == '\t' ||
                                   cleaned[len - 1] == '\n' || cleaned[len - 1] == '\r')) {
                    cleaned[--len] = '\0';
                }

                if (parser->section_count < TXT_PARSER_MAX_SECTIONS) {
                    parser->sections[parser->section_count].name = strdup(cleaned);
                    parser->sections[parser->section_count].offset = pos + 1;
                    parser->section_count++;
                    in_section = 1;
                }
            }

            line_start = pos + 1;
            if (parser->data[pos] == '\r' && pos + 1 < parser->file_size && parser->data[pos + 1] == '\n') {
                pos++;
            }
        }
        pos++;
    }

    if (in_section && parser->section_count > 0) {
        parser->sections[parser->section_count - 1].length = parser->file_size - parser->sections[parser->section_count - 1].offset;
    }

    if (parser->section_count == 0) {
        parser->sections[0].name = strdup("正文");
        parser->sections[0].offset = 0;
        parser->sections[0].length = parser->file_size;
        parser->section_count = 1;
    }
}

int txt_parser_get_section(TxtParser* parser, int section_index, char* buffer, uint64_t max_size) {
    if (!parser || !buffer) return -1;
    TxtParserImpl* impl = (TxtParserImpl*)parser;

    if (section_index < 0 || section_index >= impl->section_count) {
        return -1;
    }

    TxtSection* section = &impl->sections[section_index];
    uint64_t actual_size = (section->length < max_size - 1) ? section->length : max_size - 1;

    return (int)decode_to_utf8(impl, impl->data + section->offset, actual_size, buffer, max_size);
}

static void write_html_header(FILE* fp, const char* title) {
    fprintf(fp, "<!DOCTYPE html>\n<html>\n<head>\n");
    fprintf(fp, "<meta charset=\"UTF-8\">\n");
    fprintf(fp, "<title>%s</title>\n", title);
    fprintf(fp, "<style>body{margin:0 auto;padding:20px;max-width:800px;line-height:1.8;font-size:16px;}</style>\n");
    fprintf(fp, "</head>\n<body>\n");
}

static void write_html_footer(FILE* fp) {
    fprintf(fp, "</body>\n</html>\n");
}

int txt_parser_extract_to_html(TxtParser* parser, const char* output_path) {
    if (!parser || !output_path) return -1;
    TxtParserImpl* impl = (TxtParserImpl*)parser;

    FILE* fp = fopen(output_path, "wb");
    if (!fp) {
        LOGD("Failed to open output file: %s\n", output_path);
        return -1;
    }

    write_html_header(fp, impl->title);

    char line_buffer[8192];
    for (int i = 0; i < impl->section_count; i++) {
        TxtSection* section = &impl->sections[i];
        fprintf(fp, "<h2>%s</h2>\n", section->name);
        fprintf(fp, "<p>");

        uint64_t pos = section->offset;
        uint64_t end = pos + section->length;

        while (pos < end) {
            uint64_t line_len = 0;
            while (pos + line_len < end &&
                   impl->data[pos + line_len] != '\n' &&
                   impl->data[pos + line_len] != '\r' &&
                   line_len < sizeof(line_buffer) - 2) {
                line_len++;
            }

            if (line_len > 0) {
                decode_to_utf8(impl, impl->data + pos, line_len, line_buffer, sizeof(line_buffer));
                fprintf(fp, "%s<br/>\n", line_buffer);
            }

            pos += line_len;
            if (pos < end && impl->data[pos] == '\r') pos++;
            if (pos < end && impl->data[pos] == '\n') pos++;
        }

        fprintf(fp, "</p>\n");
    }

    write_html_footer(fp);
    fclose(fp);
    return 0;
}

#include <zlib.h>

#define ZIP_LOCAL_HEADER_SIZE 30
#define ZIP_CENTRAL_HEADER_SIZE 46
#define ZIP_END_OF_CENTRAL_DIR_SIZE 22

static void zip_write_local_header(FILE* fp, const char* filename, uint32_t compressed_size, uint32_t uncompressed_size, uint32_t crc32) {
    fwrite("\x50\x4B\x03\x04", 1, 4, fp);
    uint16_t version = 20;
    fwrite(&version, 2, 1, fp);
    uint16_t flags = 0;
    fwrite(&flags, 2, 1, fp);
    uint16_t compression = 8;
    fwrite(&compression, 2, 1, fp);
    uint16_t mod_time = 0;
    fwrite(&mod_time, 2, 1, fp);
    uint16_t mod_date = 0;
    fwrite(&mod_date, 2, 1, fp);
    fwrite(&crc32, 4, 1, fp);
    fwrite(&compressed_size, 4, 1, fp);
    fwrite(&uncompressed_size, 4, 1, fp);
    uint16_t name_len = strlen(filename);
    fwrite(&name_len, 2, 1, fp);
    uint16_t extra_len = 0;
    fwrite(&extra_len, 2, 1, fp);
    fwrite(filename, 1, name_len, fp);
}

static uint64_t zip_write_central_header(FILE* fp, const char* filename, uint32_t compressed_size, uint32_t uncompressed_size, uint32_t crc32, uint64_t local_offset) {
    fwrite("\x50\x4B\x01\x02", 1, 4, fp);
    uint16_t version_made_by = 0x0314;
    fwrite(&version_made_by, 2, 1, fp);
    uint16_t version_needed = 20;
    fwrite(&version_needed, 2, 1, fp);
    uint16_t flags = 0;
    fwrite(&flags, 2, 1, fp);
    uint16_t compression = 8;
    fwrite(&compression, 2, 1, fp);
    uint16_t mod_time = 0;
    fwrite(&mod_time, 2, 1, fp);
    uint16_t mod_date = 0;
    fwrite(&mod_date, 2, 1, fp);
    fwrite(&crc32, 4, 1, fp);
    fwrite(&compressed_size, 4, 1, fp);
    fwrite(&uncompressed_size, 4, 1, fp);
    uint16_t name_len = strlen(filename);
    fwrite(&name_len, 2, 1, fp);
    uint16_t extra_len = 0;
    fwrite(&extra_len, 2, 1, fp);
    uint16_t comment_len = 0;
    fwrite(&comment_len, 2, 1, fp);
    uint16_t disk_num = 0;
    fwrite(&disk_num, 2, 1, fp);
    uint16_t internal_attr = 0;
    fwrite(&internal_attr, 2, 1, fp);
    uint32_t external_attr = 0;
    fwrite(&external_attr, 4, 1, fp);
    uint32_t offset = (uint32_t)local_offset;
    fwrite(&offset, 4, 1, fp);
    fwrite(filename, 1, name_len, fp);
    return 46 + name_len;
}

static void zip_write_eocd(FILE* fp, uint16_t entry_count, uint64_t central_dir_size, uint64_t central_dir_offset) {
    fwrite("\x50\x4B\x05\x06", 1, 4, fp);
    uint16_t disk_num = 0;
    fwrite(&disk_num, 2, 1, fp);
    uint16_t cd_disk_num = 0;
    fwrite(&cd_disk_num, 2, 1, fp);
    fwrite(&entry_count, 2, 1, fp);
    fwrite(&entry_count, 2, 1, fp);
    uint32_t cd_size = (uint32_t)central_dir_size;
    fwrite(&cd_size, 4, 1, fp);
    uint32_t cd_offset = (uint32_t)central_dir_offset;
    fwrite(&cd_offset, 4, 1, fp);
    uint16_t comment_len = 0;
    fwrite(&comment_len, 2, 1, fp);
}

static uint64_t compress_data(uint8_t* data, uint64_t data_len, uint8_t* compressed, uint64_t max_compressed) {
    z_stream strm;
    memset(&strm, 0, sizeof(strm));
    deflateInit2(&strm, Z_DEFAULT_COMPRESSION, Z_DEFLATED, 15, 8, Z_DEFAULT_STRATEGY);
    strm.next_in = data;
    strm.avail_in = data_len;
    strm.next_out = compressed;
    strm.avail_out = max_compressed;

    deflate(&strm, Z_FINISH);
    uint64_t compressed_len = strm.total_out;
    deflateEnd(&strm);
    return compressed_len;
}

static void write_zip_entry(FILE* fp, const char* filename, const char* content, uint64_t* local_offset) {
    uint64_t uncompressed_size = strlen(content);
    uint32_t crc32_val = crc32(0, (Bytef*)content, uncompressed_size);

    uint64_t max_compressed = uncompressed_size + 1024;
    uint8_t* compressed = (uint8_t*)malloc(max_compressed);
    uint64_t compressed_size = compress_data((uint8_t*)content, uncompressed_size, compressed, max_compressed);

    zip_write_local_header(fp, filename, (uint32_t)compressed_size, (uint32_t)uncompressed_size, crc32_val);
    fwrite(compressed, 1, compressed_size, fp);

    *local_offset = ftell(fp);

    free(compressed);
}

int txt_parser_extract_to_epub(TxtParser* parser, const char* output_path) {
    if (!parser || !output_path) return -1;
    TxtParserImpl* impl = (TxtParserImpl*)parser;

    FILE* fp = fopen(output_path, "wb");
    if (!fp) {
        LOGD("Failed to open output file: %s\n", output_path);
        return -1;
    }

    uint64_t local_offset = 0;
    uint64_t central_dir_offset = 0;
    uint64_t central_dir_size = 0;
    uint16_t entry_count = 0;

    char mimetype_content[] = "application/epub+zip";
    uint32_t mimetype_crc32 = crc32(0, (Bytef*)mimetype_content, strlen(mimetype_content));
    fwrite("\x50\x4B\x03\x04", 1, 4, fp);
    uint16_t ver = 20;
    fwrite(&ver, 2, 1, fp);
    uint16_t flags = 0;
    fwrite(&flags, 2, 1, fp);
    uint16_t compression = 0;
    fwrite(&compression, 2, 1, fp);
    uint16_t mod_time = 0;
    fwrite(&mod_time, 2, 1, fp);
    uint16_t mod_date = 0;
    fwrite(&mod_date, 2, 1, fp);
    fwrite(&mimetype_crc32, 4, 1, fp);
    uint32_t mimetype_size = strlen(mimetype_content);
    fwrite(&mimetype_size, 4, 1, fp);
    fwrite(&mimetype_size, 4, 1, fp);
    uint16_t name_len = 9;
    fwrite(&name_len, 2, 1, fp);
    uint16_t extra_len = 0;
    fwrite(&extra_len, 2, 1, fp);
    fwrite("mimetype", 1, 9, fp);
    fwrite(mimetype_content, 1, strlen(mimetype_content), fp);

    char container_xml[] = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\" version=\"1.0\">\n  <rootfiles>\n    <rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n  </rootfiles>\n</container>\n";
    write_zip_entry(fp, "META-INF/container.xml", container_xml, &local_offset);
    central_dir_offset = local_offset;

    char opf_buffer[65536];
    snprintf(opf_buffer, sizeof(opf_buffer),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"2.0\" unique-identifier=\"bookid\">\n"
        "  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n"
        "    <dc:title>%s</dc:title>\n"
        "    <dc:language>zh-CN</dc:language>\n"
        "    <dc:identifier id=\"bookid\">urn:uuid:%s</dc:identifier>\n"
        "  </metadata>\n"
        "  <manifest>\n"
        "    <item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n",
        impl->title, "placeholder-uuid");

    for (int i = 0; i < impl->section_count; i++) {
        snprintf(opf_buffer + strlen(opf_buffer), sizeof(opf_buffer) - strlen(opf_buffer),
            "    <item id=\"ch%d\" href=\"chapter%d.xhtml\" media-type=\"application/xhtml+xml\"/>\n", i + 1, i + 1);
    }

    strcat(opf_buffer, "  </manifest>\n  <spine toc=\"ncx\">\n");
    for (int i = 0; i < impl->section_count; i++) {
        snprintf(opf_buffer + strlen(opf_buffer), sizeof(opf_buffer) - strlen(opf_buffer),
            "    <itemref idref=\"ch%d\"/>\n", i + 1);
    }
    strcat(opf_buffer, "  </spine>\n</package>\n");
    write_zip_entry(fp, "OEBPS/content.opf", opf_buffer, &local_offset);

    char ncx_buffer[65536];
    snprintf(ncx_buffer, sizeof(ncx_buffer),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        "<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">\n"
        "  <head>\n"
        "    <meta name=\"dtb:uid\" content=\"urn:uuid:%s\"/>\n"
        "    <meta name=\"dtb:depth\" content=\"1\"/>\n"
        "    <meta name=\"dtb:totalPageCount\" content=\"0\"/>\n"
        "    <meta name=\"dtb:maxPageNumber\" content=\"0\"/>\n"
        "  </head>\n"
        "  <docTitle><text>%s</text></docTitle>\n"
        "  <navMap>\n",
        "placeholder-uuid", impl->title);

    for (int i = 0; i < impl->section_count; i++) {
        snprintf(ncx_buffer + strlen(ncx_buffer), sizeof(ncx_buffer) - strlen(ncx_buffer),
            "    <navPoint id=\"nav%d\" playOrder=\"%d\">\n"
            "      <navLabel><text>%s</text></navLabel>\n"
            "      <content src=\"chapter%d.xhtml\"/>\n"
            "    </navPoint>\n",
            i + 1, i + 1, impl->sections[i].name, i + 1);
    }
    strcat(ncx_buffer, "  </navMap>\n</ncx>\n");
    write_zip_entry(fp, "OEBPS/toc.ncx", ncx_buffer, &local_offset);

    char chapter_buffer[131072];
    char line_buffer[8192];
    for (int i = 0; i < impl->section_count; i++) {
        TxtSection* section = &impl->sections[i];
        snprintf(chapter_buffer, sizeof(chapter_buffer),
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n"
            "<head><meta charset=\"UTF-8\"/><title>%s</title></head>\n"
            "<body>\n<h1>%s</h1>\n",
            section->name, section->name);

        uint64_t pos = section->offset;
        uint64_t end = pos + section->length;

        while (pos < end) {
            uint64_t line_len = 0;
            while (pos + line_len < end &&
                   impl->data[pos + line_len] != '\n' &&
                   impl->data[pos + line_len] != '\r' &&
                   line_len < sizeof(line_buffer) - 2) {
                line_len++;
            }

            if (line_len > 0) {
                decode_to_utf8(impl, impl->data + pos, line_len, line_buffer, sizeof(line_buffer));
                snprintf(chapter_buffer + strlen(chapter_buffer), sizeof(chapter_buffer) - strlen(chapter_buffer),
                    "<p>%s</p>\n", line_buffer);
            }

            pos += line_len;
            if (pos < end && impl->data[pos] == '\r') pos++;
            if (pos < end && impl->data[pos] == '\n') pos++;
        }

        strcat(chapter_buffer, "</body>\n</html>\n");
        char chapter_name[32];
        snprintf(chapter_name, sizeof(chapter_name), "OEBPS/chapter%d.xhtml", i + 1);
        write_zip_entry(fp, chapter_name, chapter_buffer, &local_offset);
    }

    entry_count = impl->section_count + 4;
    central_dir_size = ftell(fp) - central_dir_offset;

    fclose(fp);

    fp = fopen(output_path, "r+b");
    if (!fp) {
        LOGD("Failed to reopen output file\n");
        return -1;
    }

    fseek(fp, central_dir_offset, SEEK_SET);

    char mimetype_name[] = "mimetype";
    uint32_t mimetype_size2 = strlen(mimetype_content);
    central_dir_size += zip_write_central_header(fp, mimetype_name, mimetype_size2, mimetype_size2, mimetype_crc32, 0);

    central_dir_size += zip_write_central_header(fp, "META-INF/container.xml", (uint32_t)strlen(container_xml), (uint32_t)strlen(container_xml), crc32(0, (Bytef*)container_xml, strlen(container_xml)), ZIP_LOCAL_HEADER_SIZE + 9 + strlen(mimetype_content));

    uint64_t opf_offset = ZIP_LOCAL_HEADER_SIZE + 9 + strlen(mimetype_content) + ZIP_LOCAL_HEADER_SIZE + strlen("META-INF/container.xml") + strlen(container_xml);
    central_dir_size += zip_write_central_header(fp, "OEBPS/content.opf", (uint32_t)strlen(opf_buffer), (uint32_t)strlen(opf_buffer), crc32(0, (Bytef*)opf_buffer, strlen(opf_buffer)), opf_offset);

    uint64_t ncx_offset = opf_offset + ZIP_LOCAL_HEADER_SIZE + strlen("OEBPS/content.opf") + strlen(opf_buffer);
    central_dir_size += zip_write_central_header(fp, "OEBPS/toc.ncx", (uint32_t)strlen(ncx_buffer), (uint32_t)strlen(ncx_buffer), crc32(0, (Bytef*)ncx_buffer, strlen(ncx_buffer)), ncx_offset);

    uint64_t chapter_offset = ncx_offset + ZIP_LOCAL_HEADER_SIZE + strlen("OEBPS/toc.ncx") + strlen(ncx_buffer);
    for (int i = 0; i < impl->section_count; i++) {
        char chapter_name[32];
        snprintf(chapter_name, sizeof(chapter_name), "OEBPS/chapter%d.xhtml", i + 1);
        central_dir_size += zip_write_central_header(fp, chapter_name, (uint32_t)strlen(chapter_buffer), (uint32_t)strlen(chapter_buffer), crc32(0, (Bytef*)chapter_buffer, strlen(chapter_buffer)), chapter_offset);
        chapter_offset += ZIP_LOCAL_HEADER_SIZE + strlen(chapter_name) + strlen(chapter_buffer);
    }

    zip_write_eocd(fp, entry_count, central_dir_size, central_dir_offset);
    fclose(fp);

    return 0;
}