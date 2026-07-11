package org.ebookdroid.core;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Pair;

import com.foobnix.android.utils.ResultResponse;
import com.foobnix.pdf.info.model.AnnotationType;

import org.ebookdroid.common.bitmaps.BitmapRef;
import org.ebookdroid.core.codec.Annotation;
import org.ebookdroid.core.codec.CodecDocument;
import org.ebookdroid.core.codec.CodecPage;
import org.ebookdroid.core.codec.CodecPageHolder;
import org.ebookdroid.core.codec.CodecPageInfo;
import org.ebookdroid.core.codec.OutlineLink;
import org.ebookdroid.core.codec.PageLink;
import org.ebookdroid.droids.mupdf.codec.TextWord;

import java.util.List;
import java.util.Map;

/**
 * 文档解码服务接口。
 * <p>
 * 定义文档解码、页面渲染、注释管理等核心功能，用于在后台线程处理文档数据。
 */
public interface DecodeService {

    /**
     * 打开文档。
     *
     * @param fileName 文件路径
     * @param password 密码（可为null）
     */
	void open(String fileName, String password);

    /**
     * 解码页面。
     *
     * @param viewState 视图状态
     * @param node      页面树节点
     */
	void decodePage(ViewState viewState, PageTreeNode node);

    /**
     * 停止页面解码。
     *
     * @param node   页面树节点
     * @param reason 停止原因
     */
	void stopDecoding(PageTreeNode node, String reason);

    /**
     * 获取文档总页数。
     *
     * @return 页数
     */
	int getPageCount();

    /**
     * 获取文档大纲。
     *
     * @param response 结果回调
     */
	void getOutline(final ResultResponse<List<OutlineLink>> response);

    /**
     * 获取页脚注释。
     *
     * @param text    文本
     * @param chapter 章节
     * @return 注释内容
     */
    String getFooterNote(String text, String chapter);

    /**
     * 获取文档附件列表。
     *
     * @return 附件路径列表
     */
    List<String> getAttachemnts();

    /**
     * 获取统一的页面信息。
     *
     * @return 页面信息
     */
	CodecPageInfo getUnifiedPageInfo();

    /**
     * 获取指定页面的信息。
     *
     * @param pageIndex 页面索引
     * @return 页面信息
     */
	CodecPageInfo getPageInfo(int pageIndex);

    /**
     * 回收资源。
     */
	void recycle();

    /**
     * 更新注释。
     *
     * @param page   页面索引
     * @param color  颜色数组
     * @param points 点坐标数组
     * @param width  线宽
     * @param alpha  透明度
     */
    void updateAnnotation(int page, float[] color, PointF[][] points, float width, float alpha);

    /**
     * 更新视图状态。
     *
     * @param viewState 视图状态
     */
	void updateViewState(ViewState viewState);

    /**
     * 页面大小是否可缓存。
     *
     * @return 是否可缓存
     */
	boolean isPageSizeCacheable();

    /**
     * 获取像素格式。
     *
     * @return 像素格式
     */
	int getPixelFormat();

    /**
     * 获取位图配置。
     *
     * @return 位图配置
     */
	Bitmap.Config getBitmapConfig();

    /**
     * 解码回调接口。
     */
	interface DecodeCallback {

        /**
         * 解码完成回调。
         *
         * @param codecPage       解码后的页面
         * @param bitmap          位图引用
         * @param bitmapBounds    位图边界
         * @param croppedPageBounds 裁剪后的页面边界
         */
		void decodeComplete(CodecPage codecPage, BitmapRef bitmap, Rect bitmapBounds, RectF croppedPageBounds);

	}

    /**
     * 添加注释。
     *
     * @param points          点坐标映射
     * @param color           颜色
     * @param width           线宽
     * @param alpha           透明度
     * @param resultResponse  结果回调
     */
    void addAnnotation(Map<Integer, List<PointF>> points, int color, float width, float alpha, ResultResponse<Pair<Integer, List<Annotation>>> resultResponse);

    /**
     * 获取已解码的页面缓存。
     *
     * @return 页面缓存映射
     */
	Map<Integer, CodecPageHolder> getPages();

    /**
     * 删除注释。
     *
     * @param pageHandle      页面句柄
     * @param page            页面索引
     * @param index           注释索引
     * @param response        结果回调
     */
	void deleteAnnotation(final long pageHandle, final int page, final int index, final ResultResponse<List<Annotation>> response);

    /**
     * 是否有注释变更。
     *
     * @return 是否有变更
     */
	boolean hasAnnotationChanges();

    /**
     * 保存注释。
     *
     * @param path     保存路径
     * @param runnable 完成后执行的任务
     */
	void saveAnnotations(String path, Runnable runnable);

    /**
     * 为文本添加下划线注释。
     *
     * @param page            页面索引
     * @param points          点坐标
     * @param color           颜色
     * @param type            注释类型
     * @param resultResponse  结果回调
     */
	void underlineText(int page, PointF[] points, int color, AnnotationType type, ResultResponse<List<Annotation>> resultResponse);

    /**
     * 处理页面文本数据。
     *
     * @param pages 页面数组
     */
	void processTextForPages(Page[] pages);

    /**
     * 搜索文本。
     *
     * @param text    搜索文本
     * @param pages   页面数组
     * @param response 结果回调
     * @param finish  完成后执行的任务
     * @param firstPage 起始页面
     * @param lastPage 结束页面
     */
	void searchText(String text, Page[] pages, ResultResponse<Integer> response, Runnable finish, int firstPage, int lastPage);

    /**
     * 获取页面文本词数据。
     *
     * @param page 页面索引
     * @return 文本词二维数组
     */
    TextWord[][] getTextForPage(int page);

    /**
     * 获取页面HTML内容。
     *
     * @param page 页面索引
     * @return HTML内容
     */
    public String getPageHTML(int page);

    /**
     * 获取页面链接列表。
     *
     * @param page 页面索引
     * @return 链接列表
     */
    List<PageLink> getLinksForPage(int page);

    /**
     * 获取编解码器文档对象。
     *
     * @return 文档对象
     */
    CodecDocument getCodecDocument();

    /**
     * 关闭服务。
     */
    void shutdown() ;

    /**
     * 恢复服务。
     */
    void restore() ;

}
