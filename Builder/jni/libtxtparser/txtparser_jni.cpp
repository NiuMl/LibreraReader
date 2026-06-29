#include <jni.h>
#include <android/log.h>
#include "txtparser.h"

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "TxtParserJNI", __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, "TxtParserJNI", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "TxtParserJNI", __VA_ARGS__)

static jfieldID g_nativeHandleField = NULL;

static inline TxtParser* getParser(JNIEnv* env, jobject obj) {
    if (!g_nativeHandleField) {
        jclass clazz = env->GetObjectClass(obj);
        g_nativeHandleField = env->GetFieldID(clazz, "nativeHandle", "J");
        env->DeleteLocalRef(clazz);
    }
    return (TxtParser*)env->GetLongField(obj, g_nativeHandleField);
}

static inline void setParser(JNIEnv* env, jobject obj, TxtParser* parser) {
    if (!g_nativeHandleField) {
        jclass clazz = env->GetObjectClass(obj);
        g_nativeHandleField = env->GetFieldID(clazz, "nativeHandle", "J");
        env->DeleteLocalRef(clazz);
    }
    env->SetLongField(obj, g_nativeHandleField, (jlong)parser);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_foobnix_ext_TxtParser_open(JNIEnv *env, jobject obj, jstring filepath) {
    if (!filepath) return JNI_FALSE;

    const char* path = env->GetStringUTFChars(filepath, NULL);
    if (!path) return JNI_FALSE;

    TxtParser* parser = txt_parser_open(path);
    env->ReleaseStringUTFChars(filepath, path);

    if (!parser) {
        LOGE("Failed to open file: %s", path);
        return JNI_FALSE;
    }

    setParser(env, obj, parser);
    LOGD("Successfully opened file");
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_foobnix_ext_TxtParser_close(JNIEnv *env, jobject obj) {
    TxtParser* parser = getParser(env, obj);
    if (parser) {
        txt_parser_close(parser);
        setParser(env, obj, NULL);
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_foobnix_ext_TxtParser_getSectionCount(JNIEnv *env, jobject obj) {
    TxtParser* parser = getParser(env, obj);
    if (!parser) return 0;
    TxtParserImpl* impl = (TxtParserImpl*)parser;
    return impl->section_count;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_foobnix_ext_TxtParser_getSectionName(JNIEnv *env, jobject obj, jint index) {
    TxtParser* parser = getParser(env, obj);
    if (!parser) return NULL;

    TxtParserImpl* impl = (TxtParserImpl*)parser;
    if (index < 0 || index >= impl->section_count) return NULL;

    return env->NewStringUTF(impl->sections[index].name);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_foobnix_ext_TxtParser_getTitle(JNIEnv *env, jobject obj) {
    TxtParser* parser = getParser(env, obj);
    if (!parser) return NULL;

    TxtParserImpl* impl = (TxtParserImpl*)parser;
    return env->NewStringUTF(impl->title);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_foobnix_ext_TxtParser_getEncoding(JNIEnv *env, jobject obj) {
    TxtParser* parser = getParser(env, obj);
    if (!parser) return -1;

    TxtParserImpl* impl = (TxtParserImpl*)parser;
    return impl->encoding;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_foobnix_ext_TxtParser_extractToHtml(JNIEnv *env, jobject obj, jstring outputPath) {
    TxtParser* parser = getParser(env, obj);
    if (!parser || !outputPath) return -1;

    const char* path = env->GetStringUTFChars(outputPath, NULL);
    if (!path) return -1;

    int result = txt_parser_extract_to_html(parser, path);
    env->ReleaseStringUTFChars(outputPath, path);

    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_foobnix_ext_TxtParser_extractToEpub(JNIEnv *env, jobject obj, jstring outputPath) {
    TxtParser* parser = getParser(env, obj);
    if (!parser || !outputPath) return -1;

    const char* path = env->GetStringUTFChars(outputPath, NULL);
    if (!path) return -1;

    int result = txt_parser_extract_to_epub(parser, path);
    env->ReleaseStringUTFChars(outputPath, path);

    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_foobnix_ext_TxtParser_setSectionPattern(JNIEnv *env, jobject obj, jstring pattern) {
    TxtParser* parser = getParser(env, obj);
    if (!parser || !pattern) return;

    const char* pat = env->GetStringUTFChars(pattern, NULL);
    if (!pat) return;

    txt_parser_set_section_pattern(parser, pat);
    env->ReleaseStringUTFChars(pattern, pat);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_foobnix_ext_TxtParser_getFileSize(JNIEnv *env, jobject obj) {
    TxtParser* parser = getParser(env, obj);
    if (!parser) return 0;

    TxtParserImpl* impl = (TxtParserImpl*)parser;
    return (jint)impl->file_size;
}