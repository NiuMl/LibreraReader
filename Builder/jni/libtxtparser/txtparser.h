#ifndef TXTPARSER_H
#define TXTPARSER_H

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>

#define TXT_PARSER_MAX_PATH 4096
#define TXT_PARSER_MAX_SECTIONS 10000
#define TXT_PARSER_SECTION_NAME_LEN 256

typedef enum {
    TXT_ENCODING_UTF8 = 0,
    TXT_ENCODING_GBK = 1,
    TXT_ENCODING_BIG5 = 2,
    TXT_ENCODING_GB18030 = 3,
    TXT_ENCODING_UTF16_LE = 4,
    TXT_ENCODING_UTF16_BE = 5,
    TXT_ENCODING_ISO8859_1 = 6
} TxtEncoding;

typedef struct {
    const char* name;
    uint64_t offset;
    uint64_t length;
} TxtSection;

typedef struct {
    uint8_t* data;
    uint64_t file_size;
    int fd;
    TxtEncoding encoding;
    int section_count;
    TxtSection sections[TXT_PARSER_MAX_SECTIONS];
    char title[TXT_PARSER_SECTION_NAME_LEN];
    char section_pattern[256];
} TxtParser;

typedef TxtParser TxtParserImpl;

TxtParser* txt_parser_open(const char* filepath);
void txt_parser_close(TxtParser* parser);
int txt_parser_get_section(TxtParser* parser, int section_index, char* buffer, uint64_t max_size);
int txt_parser_extract_to_html(TxtParser* parser, const char* output_path);
int txt_parser_extract_to_epub(TxtParser* parser, const char* output_path);
void txt_parser_set_section_pattern(TxtParser* parser, const char* pattern);

#ifdef __cplusplus
}
#endif

#endif