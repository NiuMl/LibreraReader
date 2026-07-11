package org.ebookdroid.core.codec;

import android.graphics.Bitmap;
import android.graphics.RectF;

import org.ebookdroid.BookType;

import java.util.List;
import java.util.Map;

/**
 * 编解码器文档接口。
 * <p>
 * 定义文档操作的核心方法，包括页面获取、搜索、大纲、注释管理等功能。
 */
public interface CodecDocument {

    /**
     * 获取文档句柄。
     *
     * @return 文档句柄
     */
    public long getDocumentHandle();

    /**
     * 获取文档总页数。
     *
     * @return 页数
     */
    int getPageCount();

    /**
     * 获取文档总页数（带渲染参数）。
     *
     * @param w     宽度
     * @param h     高度
     * @param fsize 字体大小
     * @return 页数
     */
    int getPageCount(int w, int h, int fsize);

    /**
     * 获取指定页面。
     *
     * @param pageNuber 页面索引
     * @return 页面对象
     */
    CodecPage getPage(int pageNuber);

    /**
     * 获取指定页面（内部方法）。
     *
     * @param pageNuber 页面索引
     * @return 页面对象
     */
    CodecPage getPageInner(int pageNuber);

    /**
     * 获取统一的页面信息。
     *
     * @return 页面信息
     */
    CodecPageInfo getUnifiedPageInfo();

    /**
     * 获取指定页面的信息。
     *
     * @param pageNuber 页面索引
     * @return 页面信息
     */
    CodecPageInfo getPageInfo(int pageNuber);

    /**
     * 在指定页面搜索文本。
     *
     * @param pageNuber 页面索引
     * @param pattern   搜索模式
     * @return 匹配区域列表
     * @throws DocSearchNotSupported 如果文档不支持搜索
     */
    List<? extends RectF> searchText(int pageNuber, final String pattern) throws DocSearchNotSupported;

    /**
     * 获取文档大纲。
     *
     * @return 大纲链接列表
     */
    List<OutlineLink> getOutline();

    /**
     * 获取页脚注释。
     *
     * @return 注释映射
     */
    Map<String, String> getFootNotes();

    /**
     * 获取媒体附件列表。
     *
     * @return 附件路径列表
     */
    List<String> getMediaAttachments();

    /**
     * 回收资源。
     */
    void recycle();

    /**
     * 获取书籍类型。
     *
     * @return 书籍类型
     */
    BookType getBookType();

    /**
     * 检查文档是否已回收。
     *
     * @return 如果已回收返回true
     */
    boolean isRecycled();

    /**
     * 获取嵌入的缩略图。
     *
     * @return 缩略图位图
     */
    Bitmap getEmbeddedThumbnail();

    /**
     * 文档搜索不支持异常。
     */
    public static class DocSearchNotSupported extends Exception {

        /**
         * Serial version UID
         */
        private static final long serialVersionUID = 6741243859033574916L;

    }

    /**
     * 是否有注释变更。
     *
     * @return 是否有变更
     */
	boolean hasChanges();

    /**
     * 删除注释。
     *
     * @param pageNumber 页面编号
     * @param index      注释索引
     */
	void deleteAnnotation(long pageNumber, int index);

    /**
     * 保存注释。
     *
     * @param path 保存路径
     */
	void saveAnnotations(String path);

    /**
     * 将文档转换为HTML格式。
     *
     * @return HTML内容
     */
    String documentToHtml();

    /**
     * 获取书籍标题。
     *
     * @return 标题
     */
    String getBookTitle();

    /**
     * 获取书籍作者。
     *
     * @return 作者
     */
    String getBookAuthor();

    /**
     * 获取元数据。
     *
     * @param option 元数据键
     * @return 元数据值
     */
    String getMeta(String option);

    /**
     * 获取所有元数据键。
     *
     * @return 元数据键列表
     */
    List<String> getMetaKeys();

    /**
     * 设置元数据。
     *
     * @param key   元数据键
     * @param value 元数据值
     */
    void setMeta(String key, String value);
}
