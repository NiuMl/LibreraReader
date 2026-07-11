package com.foobnix.ext;

import android.util.Log;

import java.io.Closeable;
import java.io.File;

/**
 * TXT文件解析器。
 * <p>
 * 通过JNI调用原生库解析TXT文件，支持章节提取、编码检测、HTML和EPUB格式转换。
 */
public class TxtParser implements Closeable {
    /** 日志标签 */
    private static final String TAG = "TxtParser";
    /** 原生库是否已加载 */
    private static boolean libraryLoaded = false;

    static {
        try {
            System.loadLibrary("txtparser");
            libraryLoaded = true;
            Log.d(TAG, "Successfully loaded txtparser library");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Failed to load txtparser library: " + e.getMessage());
            libraryLoaded = false;
        }
    }

    /**
     * 检查原生库是否可用。
     *
     * @return 原生库是否已加载
     */
    public static boolean isLibraryAvailable() {
        return libraryLoaded;
    }

    /** 原生句柄 */
    private long nativeHandle;

    /**
     * 构造函数。
     */
    public TxtParser() {
        this.nativeHandle = 0;
    }

    /**
     * 打开TXT文件。
     *
     * @param filepath 文件路径
     * @return 是否打开成功
     */
    public boolean open(String filepath) {
        if (!libraryLoaded) {
            Log.e(TAG, "Library not loaded");
            return false;
        }
        return nativeOpen(filepath);
    }

    /**
     * 打开TXT文件。
     *
     * @param file 文件对象
     * @return 是否打开成功
     */
    public boolean open(File file) {
        return open(file.getAbsolutePath());
    }

    /**
     * 关闭解析器，释放原生资源。
     */
    @Override
    public void close() {
        if (nativeHandle != 0) {
            nativeClose();
            nativeHandle = 0;
        }
    }

    /**
     * 获取章节数量。
     *
     * @return 章节数量，失败返回0
     */
    public int getSectionCount() {
        if (!libraryLoaded || nativeHandle == 0) return 0;
        return nativeGetSectionCount();
    }

    /**
     * 获取指定索引的章节名称。
     *
     * @param index 章节索引
     * @return 章节名称，失败返回null
     */
    public String getSectionName(int index) {
        if (!libraryLoaded || nativeHandle == 0) return null;
        return nativeGetSectionName(index);
    }

    /**
     * 获取书籍标题。
     *
     * @return 标题，失败返回null
     */
    public String getTitle() {
        if (!libraryLoaded || nativeHandle == 0) return null;
        return nativeGetTitle();
    }

    /**
     * 获取文件编码。
     *
     * @return 编码类型，失败返回-1
     */
    public int getEncoding() {
        if (!libraryLoaded || nativeHandle == 0) return -1;
        return nativeGetEncoding();
    }

    /**
     * 获取文件大小。
     *
     * @return 文件大小，失败返回0
     */
    public long getFileSize() {
        if (!libraryLoaded || nativeHandle == 0) return 0;
        return nativeGetFileSize();
    }

    /**
     * 将TXT文件提取为HTML格式。
     *
     * @param outputPath 输出路径
     * @return 提取状态码，失败返回-1
     */
    public int extractToHtml(String outputPath) {
        if (!libraryLoaded || nativeHandle == 0) return -1;
        return nativeExtractToHtml(outputPath);
    }

    /**
     * 将TXT文件提取为EPUB格式。
     *
     * @param outputPath 输出路径
     * @return 提取状态码，失败返回-1
     */
    public int extractToEpub(String outputPath) {
        if (!libraryLoaded || nativeHandle == 0) return -1;
        return nativeExtractToEpub(outputPath);
    }

    /**
     * 设置章节匹配模式。
     *
     * @param pattern 正则表达式模式
     */
    public void setSectionPattern(String pattern) {
        if (libraryLoaded && nativeHandle != 0) {
            nativeSetSectionPattern(pattern);
        }
    }

    /**
     * 原生方法：打开文件
     */
    private native boolean nativeOpen(String filepath);
    /**
     * 原生方法：关闭文件
     */
    private native void nativeClose();
    /**
     * 原生方法：获取章节数量
     */
    private native int nativeGetSectionCount();
    /**
     * 原生方法：获取章节名称
     */
    private native String nativeGetSectionName(int index);
    /**
     * 原生方法：获取标题
     */
    private native String nativeGetTitle();
    /**
     * 原生方法：获取编码
     */
    private native int nativeGetEncoding();
    /**
     * 原生方法：获取文件大小
     */
    private native long nativeGetFileSize();
    /**
     * 原生方法：提取为HTML
     */
    private native int nativeExtractToHtml(String outputPath);
    /**
     * 原生方法：提取为EPUB
     */
    private native int nativeExtractToEpub(String outputPath);
    /**
     * 原生方法：设置章节模式
     */
    private native void nativeSetSectionPattern(String pattern);
}