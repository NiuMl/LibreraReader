package org.ebookdroid.core.codec;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;

import com.foobnix.pdf.info.model.AnnotationType;

import org.ebookdroid.common.bitmaps.BitmapRef;
import org.ebookdroid.droids.mupdf.codec.TextWord;

import java.util.List;

/**
 * 编解码器页面接口。
 * <p>
 * 定义页面操作的核心方法，包括渲染、链接获取、注释管理等功能。
 */
public interface CodecPage {

    /**
     * 获取页面宽度。
     *
     * @return 宽度
     */
	int getWidth();

    /**
     * 获取页面高度。
     *
     * @return 高度
     */
	int getHeight();

    /**
     * 渲染页面位图。
     *
     * @param width          宽度
     * @param height         高度
     * @param pageSliceBounds 页面切片边界
     * @param cache          是否缓存
     * @return 位图引用
     */
	BitmapRef renderBitmap(int width, int height, RectF pageSliceBounds, boolean cache);

    /**
     * 简单渲染页面位图。
     *
     * @param width          宽度
     * @param height         高度
     * @param pageSliceBounds 页面切片边界
     * @return 位图引用
     */
    BitmapRef renderBitmapSimple(int width, int height, RectF pageSliceBounds);


    /**
     * 渲染缩略图。
     *
     * @param width 宽度
     * @return 缩略图位图
     */
	Bitmap renderThumbnail(int width);

    /**
     * 渲染缩略图（指定原始尺寸）。
     *
     * @param width   宽度
     * @param originW 原始宽度
     * @param originH 原始高度
     * @return 缩略图位图
     */
	Bitmap renderThumbnail(int width, int originW, int originH);

    /**
     * 获取页面链接列表。
     *
     * @return 链接列表
     */
	List<PageLink> getPageLinks();

    /**
     * 获取页面注释列表。
     *
     * @return 注释列表
     */
	List<Annotation> getAnnotations();

    /**
     * 获取页面注释列表（实现方法）。
     *
     * @return 注释列表
     */
	List<Annotation> getAnnotationsImpl();

    /**
     * 获取页面文本词数据。
     *
     * @return 文本词二维数组
     */
	public TextWord[][] getText();

    /**
     * 回收资源。
     */
	void recycle();

    /**
     * 检查页面是否已回收。
     *
     * @return 如果已回收返回true
     */
	boolean isRecycled();

    /**
     * 添加注释。
     *
     * @param color  颜色数组
     * @param points 点坐标数组
     * @param width  线宽
     * @param alpha  透明度
     */
    public void addAnnotation(float[] color, PointF[][] points, float width, float alpha);

    /**
     * 获取页面句柄。
     *
     * @return 页面句柄
     */
	long getPageHandle();

    /**
     * 添加标记注释。
     *
     * @param quadPoints 四角点坐标
     * @param type       注释类型
     * @param color      颜色数组
     */
	void addMarkupAnnotation(PointF[] quadPoints, AnnotationType type, float[] color);

    /**
     * 获取页面HTML内容。
     *
     * @return HTML内容
     */
    String getPageHTML();

    /**
     * 获取包含图片的页面HTML内容。
     *
     * @return HTML内容
     */
    String getPageHTMLWithImages();

    /**
     * 获取字符数量。
     *
     * @return 字符数
     */
    int getCharCount();

}
