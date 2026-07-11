package org.ebookdroid.droids.mupdf.codec;

import android.graphics.RectF;

import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.ext.CacheZipUtils;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.model.BookCSS;
import com.foobnix.sys.TempHolder;

import org.ebookdroid.BookType;
import org.ebookdroid.core.codec.AbstractCodecDocument;
import org.ebookdroid.core.codec.CodecPage;
import org.ebookdroid.core.codec.CodecPageInfo;
import org.ebookdroid.core.codec.OutlineLink;
import org.ebookdroid.droids.EpubContext;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MuPDF文档编解码器实现类。
 * <p>
 * 基于MuPDF库实现文档解码、页面渲染、注释管理等功能，支持PDF、EPUB等多种格式。
 */
public class MuPdfDocument extends AbstractCodecDocument {

    /** PDF格式标识 */
    public static final int FORMAT_PDF = 0;

    /** 元信息：作者 */
    public static final String META_INFO_AUTHOR = "info:Author";
    /** 元信息：标题 */
    public static final String META_INFO_TITLE = "info:Title";
    /** 元信息：主题 */
    public static final String META_INFO_SUBJECT = "info:Subject";
    /** 元信息：关键词 */
    public static final String META_INFO_KEYWORDS = "info:Keywords";
    /** 元信息：创建者 */
    public static final String META_INFO_CREATOR = "info:Creator";
    /** 元信息：生产者 */
    public static final String META_INFO_PRODUCER = "info:Producer";
    /** 元信息：创建日期 */
    public static final String META_INFO_CREATIONDATE = "info:CreationDate";
    /** 元信息：修改日期 */
    public static final String META_INFO_MODIFICATIONDATE = "info:ModDate";

    /** 缓存的文档句柄 */
    private static long cacheHandle;
    /** 缓存的宽高和 */
    private static int cacheWH;
    /** 缓存的字体大小 */
    private static long cacheSize;
    /** 缓存的页数 */
    private static int cacheCount;

    /** 页面宽度 */
    int w, h;
    /** 书籍类型 */
    BookType bookType;
    /** 是否为EPUB格式 */
    private boolean isEpub = false;
    /** 脚注映射 */
    private volatile Map<String, String> footNotes;
    /** 媒体附件列表 */
    private volatile List<String> mediaAttachment;
    /** 页数 */
    private int pagesCount = -1;
    /** 文件路径 */
    private String fname;

    /**
     * 构造函数。
     *
     * @param context MuPDF上下文
     * @param format  格式标识
     * @param fname   文件路径
     * @param pwd     密码
     */
    public MuPdfDocument(final MuPdfContext context, final int format, final String fname, final String pwd) {
        super(context, openFile(format, fname, pwd, BookCSS.get()
                                                           .toCssString(fname)));
        this.fname = fname;
        isEpub = ExtUtils.isTextFomat(fname);
        bookType = BookType.getByUri(fname);
    }

    /**
     * 归一化链接目标矩形。
     * <p>
     * 将链接目标矩形从页面坐标转换为归一化坐标。
     *
     * @param docHandle 文档句柄
     * @param targetPage 目标页面
     * @param targetRect 目标矩形
     * @param flags      标志位
     */
    static void normalizeLinkTargetRect(final long docHandle, final int targetPage, final RectF targetRect,
                                        final int flags) {

        if ((flags & 0x0F) == 0) {
            targetRect.right = targetRect.left = 0;
            targetRect.bottom = targetRect.top = 0;
            return;
        }

        final CodecPageInfo cpi = new CodecPageInfo();
        TempHolder.lock.lock();
        try {
            MuPdfDocument.getPageInfo(docHandle, targetPage, cpi);
        } finally {
            TempHolder.lock.unlock();
        }

        final float left = targetRect.left;
        final float top = targetRect.top;

        if (((cpi.rotation / 90) % 2) != 0) {
            targetRect.right = targetRect.left = left / cpi.height;
            targetRect.bottom = targetRect.top = 1.0f - top / cpi.width;
        } else {
            targetRect.right = targetRect.left = left / cpi.width;
            targetRect.bottom = targetRect.top = 1.0f - top / cpi.height;
        }
    }

    /**
     * 获取页面信息。
     *
     * @param docHandle 文档句柄
     * @param pageNumber 页面索引
     * @param cpi       页面信息对象
     * @return 结果码
     */
    native static int getPageInfo(long docHandle, int pageNumber, CodecPageInfo cpi);

    /**
     * 获取文档元信息。
     * <p>
     * 支持的元信息包括：Title、Author、Subject、Keywords、Creator、Producer、CreationDate、ModDate。
     *
     * @param docHandle 文档句柄
     * @param option    元信息键
     * @return 元信息值
     */
    private native static String getMeta(long docHandle, final String option);

    /**
     * 设置文档元数据。
     *
     * @param docHandle 文档句柄
     * @param key       键
     * @param value     值
     * @return 结果
     */
    private native static String setMetaData(long docHandle, final String key, String value);

    /**
     * 打开文件。
     * <p>
     * 使用MuPDF库打开文档，配置内存大小、样式、抗锯齿等参数。
     *
     * @param format 格式标识
     * @param fname  文件路径
     * @param pwd    密码
     * @param css    CSS样式
     * @return 文档句柄
     */
    private static long openFile(final int format, String fname, final String pwd, String css) {
        TempHolder.lock.lock();
        try {
            int allocatedMemory = AppState.get().allocatedMemorySize * 1024 * 1024;
            // int allocatedMemory = CoreSettings.get().pdfStorageSize;
            LOG.d("allocatedMemory", AppState.get().allocatedMemorySize, " MB " + allocatedMemory);
            int isImageScale = AppState.get().enableImageScale ? 1 : 0;

            LOG.d("accel cache1", fname);
            String accel = new EpubContext().getCacheFileName(fname)
                                            .getPath() + "+accel";
            accel = accel.replace(CacheZipUtils.CACHE_BOOK_DIR.getPath(), CacheZipUtils.CACHE_TEMP.getPath());
            LOG.d("accel cache2", accel, new File(accel).exists());

            final long open = open(allocatedMemory, format, fname, pwd, css,
                    BookCSS.get().documentStyle == BookCSS.STYLES_ONLY_USER ? 0 : 1, BookCSS.get().imageScale,
                    AppState.get().antiAliasLevel, accel, isImageScale);
            LOG.d("TEST", "Open document " + fname + " " + open);
            LOG.d("TEST", "Open document css ", css);
            LOG.d("TEST", "Open document isImageScale ", isImageScale);
            LOG.d("MUPDF! >>> open [document]", open, ExtUtils.getFileName(fname));

            if (open == -1) {
                throw new RuntimeException("Document is corrupted");
            }

            // final int n = getPageCountWithException(open);
            return open;
        } finally {
            TempHolder.lock.unlock();
        }
    }

    /**
     * 获取MuPDF版本号。
     *
     * @return 版本字符串
     */
    public static native String getFzVersion();

    /**
     * 打开文档的原生方法。
     *
     * @param storememory 内存大小
     * @param format      格式
     * @param fname       文件路径
     * @param pwd         密码
     * @param css         CSS样式
     * @param useDocStyle 是否使用文档样式
     * @param scale       缩放比例
     * @param antialias   抗锯齿级别
     * @param accel       加速缓存路径
     * @param isImageScale 是否启用图像缩放
     * @return 文档句柄
     */
    private static native long open(int storememory, int format, String fname, String pwd, String css, int useDocStyle,
                                    float scale, int antialias, String accel, int isImageScale);

    /**
     * 释放文档资源。
     *
     * @param handle 文档句柄
     */
    private static native void free(long handle);

    /**
     * 获取页数（带异常处理）。
     *
     * @param handle 文档句柄
     * @param w      宽度
     * @param h      高度
     * @param size   字体大小
     * @return 页数
     */
    private int getPageCountWithException(final long handle, int w, int h, int size) {
        final int count = getPageCountSafe(handle, w, h, Dips.spToPx(size));
//        if (count == 0) {
//            throw new RuntimeException("Document is corrupted");
//        }
        return count;
    }

    /**
     * 获取页数（安全版本）。
     * <p>
     * 使用缓存机制减少重复计算，支持并发访问。
     *
     * @param handle 文档句柄
     * @param w      宽度
     * @param h      高度
     * @param size   字体大小
     * @return 页数
     */
    private int getPageCountSafe(long handle, int w, int h, int size) {

        LOG.d("getPageCountSafe w h size", w, h, size);

        if (handle == cacheHandle && size == cacheSize && w + h == cacheWH) {
            LOG.d("getPageCount from cache", cacheCount);
            return cacheCount;
        }
        TempHolder.lock.lock();
        try {
            cacheHandle = handle;
            cacheSize = size;
            cacheWH = w + h;
            if(isRecycled()){
                LOG.d("getPageCount","getPageCount isRecycled");
                return 0;
            }
            cacheCount = getPageCount(handle, w, h, size);
            LOG.d("getPageCount put to  cache", cacheCount);
            return cacheCount;
        } catch (Exception e) {
            return -1;
        } finally {
            TempHolder.lock.unlock();
        }
    }

    /**
     * 获取页数的原生方法。
     *
     * @param handle 文档句柄
     * @param w      宽度
     * @param h      高度
     * @param size   字体大小
     * @return 页数
     */
    private static native int getPageCount(long handle, int w, int h, int size);

    /**
     * 获取文件路径。
     *
     * @return 文件路径
     */
    public String getPath() {
        return fname;
    }

    /**
     * 设置元信息。
     *
     * @param key   键
     * @param value 值
     */
    @Override public void setMeta(String key, String value) {
        TempHolder.lock.lock();
        try {
            LOG.d(this.getClass(), "setMetaData", key, value);
            setMetaData(documentHandle, key, value);
        } finally {
            TempHolder.lock.unlock();
        }
    }

    /**
     * 获取书籍类型。
     *
     * @return 书籍类型
     */
    @Override public BookType getBookType() {
        return bookType;
    }

    /**
     * 将文档转换为HTML。
     * <p>
     * 遍历所有页面，将每个页面转换为HTML并拼接。
     *
     * @return HTML字符串
     */
    @Override public String documentToHtml() {
        StringBuilder out = new StringBuilder();
        int pages = getPageCount();
        for (int i = 0; i < pages; i++) {
            CodecPage pageCodec = getPage(i);
            String pageHTML = pageCodec.getPageHTML();
            out.append(pageHTML);
        }
        return out.toString();
    }

    /**
     * 获取脚注映射。
     *
     * @return 脚注映射
     */
    @Override public Map<String, String> getFootNotes() {
        return footNotes;
    }

    /**
     * 设置脚注映射。
     *
     * @param footNotes 脚注映射
     */
    public void setFootNotes(Map<String, String> footNotes) {
        this.footNotes = footNotes;
    }

    /**
     * 获取文档大纲。
     *
     * @return 大纲链接列表
     */
    @Override public synchronized List<OutlineLink> getOutline() {
        if (isRecycled()) {
            LOG.d("getOutline doc isRecycled");
            return Collections.emptyList();
        }
        final MuPdfOutline ou = new MuPdfOutline();
        return ou.getOutline(this);
    }

    /**
     * 获取页面。
     *
     * @param pageNumber 页面索引
     * @return 编解码器页面
     */
    @Override public CodecPage getPageInner(final int pageNumber) {
        MuPdfPage createPage = MuPdfPage.createPage(this, pageNumber + 1);
        return createPage;
    }

    /**
     * 获取页数。
     *
     * @return 页数
     */
    @Override public int getPageCount() {
        LOG.d("MuPdfDocument,getPageCount", getW(), getH(), BookCSS.get().fontSizeSp);
        return getPageCountWithException(documentHandle, getW(), getH(), BookCSS.get().fontSizeSp);
    }

    /**
     * 获取统一页面信息。
     * <p>
     * 对于EPUB格式，返回统一的页面尺寸；对于其他格式，返回null。
     *
     * @return 页面信息
     */
    @Override public CodecPageInfo getUnifiedPageInfo() {
        if (isEpub) {
            LOG.d("MuPdfDocument, getUnifiedPageInfo");
            return new CodecPageInfo(getW(), getH());
        } else {
            return null;
        }
    }

    /**
     * 获取页数。
     *
     * @param w    宽度
     * @param h    高度
     * @param size 字体大小
     * @return 页数
     */
    @Override
    public int getPageCount(int w, int h, int size) {
        this.w = w;
        this.h = h;
        int pageCountWithException = getPageCountWithException(documentHandle, w, h, size);
        LOG.d("MuPdfDocument,, getPageCount", w, h, size, "count", pageCountWithException);
        return pageCountWithException;
    }

    /**
     * 获取宽度。
     *
     * @return 宽度
     */
    public int getW() {
        return w > 0 ? w : Dips.screenWidth();
    }

    /**
     * 获取高度。
     *
     * @return 高度
     */
    public int getH() {
        return h > 0 ? h : Dips.screenHeight();
    }

    /**
     * 获取页面信息。
     *
     * @param pageNumber 页面索引
     * @return 页面信息
     */
    @Override public CodecPageInfo getPageInfo(final int pageNumber) {
        final CodecPageInfo info = new CodecPageInfo();
        TempHolder.lock.lock();
        try {
            final int res = getPageInfo(documentHandle, pageNumber + 1, info);
            if (res == -1) {
                return null;
            } else {
                // Check rotation
                info.rotation = (360 + info.rotation) % 360;
                return info;
            }
        } finally {
            TempHolder.lock.unlock();
        }
    }

    /**
     * 释放文档资源。
     */
    @Override protected void freeDocument() {
        TempHolder.lock.lock();
        try {
            cacheHandle = -1;
            free(documentHandle);
        } finally {
            TempHolder.lock.unlock();
        }

        LOG.d("MUPDF! <<< recycle [document]", documentHandle, ExtUtils.getFileName(fname));
    }

    /**
     * 获取元信息。
     *
     * @param option 元信息键
     * @return 元信息值
     */
    @Override public String getMeta(final String option) {
        TempHolder.lock.lock();
        try {

            if (true) {
                return getMeta(documentHandle, option);
            }

            final AtomicBoolean ready = new AtomicBoolean(false);
            final StringBuilder info = new StringBuilder();

            new Thread("@T extract meta") {
                @Override public void run() {

                    try {
                        LOG.d("getMeta", option);
                        String key = getMeta(documentHandle, option);
                        info.append(key);
                    } catch (Throwable e) {
                        LOG.e(e);
                    } finally {
                        ready.set(true);
                    }

                }

                ;
            }.start();

            while (!ready.get()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                }
            }

            return info.toString();
        } finally {
            TempHolder.lock.unlock();
        }
    }

    /**
     * 获取书籍标题。
     *
     * @return 标题
     */
    @Override public String getBookTitle() {
        return getMeta("info:Title");
    }

    /**
     * 获取书籍作者。
     *
     * @return 作者
     */
    @Override public String getBookAuthor() {
        return getMeta("info:Author");
    }

    /**
     * 保存文档的原生方法。
     *
     * @param handle 文档句柄
     * @param path   保存路径
     */
    private native void saveInternal(long handle, String path);

    /**
     * 检查文档是否有更改的原生方法。
     *
     * @param handle 文档句柄
     * @return 是否有更改
     */
    private native boolean hasChangesInternal(long handle);

    /** 是否有更改 */
    boolean isHasChanges = false;

    /**
     * 检查文档是否有更改。
     *
     * @return 是否有更改
     */
    @Override public boolean hasChanges() {

        if (isHasChanges) {
            LOG.d("hasChanges cache");
            return true;
        }
        TempHolder.lock.lock();
        try {
            LOG.d("hasChanges internal");
            isHasChanges = hasChangesInternal(documentHandle);
            return isHasChanges;
        } finally {
            TempHolder.lock.unlock();
        }
    }

    /**
     * 保存注释。
     *
     * @param path 保存路径
     */
    @Override public void saveAnnotations(String path) {
        LOG.d("Save Annotations saveInternal 1");
        TempHolder.lock.lock();
        try {
            saveInternal(documentHandle, path);
            LOG.d("Save Annotations saveInternal 2");
        } finally {
            TempHolder.lock.unlock();
        }
    }

    /**
     * 搜索文本。
     * <p>
     * MuPDF文档不支持直接搜索文本，抛出异常。
     *
     * @param pageNuber 页面索引
     * @param pattern   搜索模式
     * @return 匹配区域列表
     * @throws DocSearchNotSupported 如果文档不支持搜索
     */
    @Override public List<RectF> searchText(final int pageNuber, final String pattern) throws DocSearchNotSupported {
        throw new DocSearchNotSupported();
    }

    /**
     * 删除注释。
     *
     * @param pageHandle 页面句柄
     * @param index      注释索引
     */
    @Override public void deleteAnnotation(long pageHandle, int index) {
        TempHolder.lock.lock();
        try {
            deleteAnnotationInternal(documentHandle, pageHandle, index);
        } finally {
            TempHolder.lock.unlock();
        }

    }

    /**
     * 删除注释的原生方法。
     *
     * @param docHandle 文档句柄
     * @param pageHandle 页面句柄
     * @param annot_index 注释索引
     */
    private native void deleteAnnotationInternal(long docHandle, long pageHandle, int annot_index);

    /**
     * 设置媒体附件列表。
     *
     * @param mediaAttachment 媒体附件列表
     */
    public void setMediaAttachment(List<String> mediaAttachment) {
        this.mediaAttachment = mediaAttachment;
    }

    /**
     * 获取媒体附件列表。
     *
     * @return 媒体附件列表
     */
    @Override public List<String> getMediaAttachments() {
        return mediaAttachment;
    }

}
