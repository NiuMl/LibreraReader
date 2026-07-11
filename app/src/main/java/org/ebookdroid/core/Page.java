package org.ebookdroid.core;

import android.graphics.Matrix;
import android.graphics.RectF;

import com.foobnix.android.utils.TxtUtils;
import com.foobnix.model.AppBook;
import com.foobnix.model.AppState;

import org.ebookdroid.common.bitmaps.Bitmaps;
import org.ebookdroid.common.settings.SettingsManager;
import org.ebookdroid.common.settings.types.PageType;
import org.ebookdroid.core.codec.Annotation;
import org.ebookdroid.core.codec.CodecPageInfo;
import org.ebookdroid.core.codec.PageLink;
import org.ebookdroid.droids.mupdf.codec.TextWord;
import org.ebookdroid.ui.viewer.IActivityController;
import org.emdev.utils.MathUtils;
import org.emdev.utils.MatrixUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 页面模型类。
 * <p>
 * 表示文档中的一页，包含页面索引、类型、边界、链接、文本和注释等信息。
 */
public class Page {

    /** 页面索引 */
    public final PageIndex index;
    /** 页面类型（左页、右页、全屏页等） */
    public final PageType type;
    /** 编解码器页面信息 */
    public final CodecPageInfo cpi;

    /** 活动控制器 */
    final IActivityController base;
    /** 页面树节点 */
    public final PageTree nodes;

    /** 页面边界 */
    RectF bounds;
    /** 宽高比（放大128倍存储） */
    int aspectRatio;
    /** 是否已回收 */
    boolean recycled;
    /** 存储的缩放比例 */
    float storedZoom;
    /** 缩放后的边界 */
    RectF zoomedBounds;

    /** 页面链接列表 */
    List<PageLink> links;
    /** 页面文本词数据 */
    TextWord[][] texts;
    /** 选中的文本 */
    public List<TextWord> selectedText = new ArrayList<TextWord>();
    /** 页面注释列表 */
    public List<Annotation> annotations;

    /** 选择注释区域 */
    public RectF selectionAnnotion;
    /** 是否为最后一页 */
    public boolean isLastPage = false;


    /**
     * 构造函数。
     *
     * @param base 活动控制器
     * @param index 页面索引
     * @param pt 页面类型
     * @param cpi 编解码器页面信息
     */
    public Page(final IActivityController base, final PageIndex index, final PageType pt, final CodecPageInfo cpi) {
        this.base = base;
        this.index = index;
        this.cpi = cpi;
        this.type = pt != null ? pt : PageType.FULL_PAGE;
        this.bounds = new RectF(0, 0, cpi.width / type.getWidthScale(), cpi.height);

        setAspectRatio(cpi);

        nodes = new PageTree(this);
    }

    /**
     * 在当前页面搜索文本。
     *
     * @param text 搜索文本
     * @return 匹配的文本词列表
     */
    public List<TextWord> findText(String text) {
        return findText(text, texts);
    }

    /**
     * 在文本词数据中搜索文本。
     * <p>
     * 支持逐字母匹配和词匹配两种模式，处理连字符连接的词。
     *
     * @param text  搜索文本
     * @param texts 文本词数据
     * @return 匹配的文本词列表
     */
    public static List<TextWord> findText(String text, TextWord[][] texts) {
        List<TextWord> result = new ArrayList<TextWord>();
        if (texts == null) {
            return result;
        }
        text = text.toLowerCase(Locale.US);
        int index = 0;
        List<TextWord> find = new ArrayList<TextWord>();

        boolean nextWorld = false;
        String firstPart = "";
        TextWord firstWord = null;

        for (final TextWord[] lines : texts) {
            find.clear();
            index = 0;
            for (final TextWord word : lines) {
                if (AppState.get().selectingByLetters) {
                    String it = String.valueOf(text.charAt(index));
                    if (word.w.toLowerCase(Locale.US).equals(it)) {
                        index++;
                        find.add(word);
                    } else {
                        index = 0;
                        find.clear();
                    }

                    if (index == text.length()) {
                        index = 0;
                        for (TextWord t : find) {
                            result.add(t);
                        }
                    }
                } else if (word.w.toLowerCase(Locale.US).contains(text)) {
                    result.add(word);
                } else if (word.w.length() >= 3 && word.w.endsWith("-")) {
                    nextWorld = true;
                    firstWord = word;
                    firstPart = word.w.replace("-", "");
                } else if (nextWorld && (firstPart + word.w.toLowerCase(Locale.US)).contains(text)) {
                    result.add(firstWord);
                    result.add(word);
                    nextWorld = false;
                    firstWord = null;
                    firstPart = "";
                } else if (nextWorld && TxtUtils.isNotEmpty(word.w)) {
                    nextWorld = false;
                    firstWord = null;
                }
            }
        }
        return result;
    }

    /**
     * 获取页面边界。
     *
     * @return 边界矩形
     */
    public RectF getBounds() {
        return bounds;
    }

    /**
     * 回收页面资源。
     *
     * @param bitmapsToRecycle 需要回收的位图列表
     */
    public void recycle(final List<Bitmaps> bitmapsToRecycle) {
        texts = null;
        recycled = true;
        nodes.recycleAll(bitmapsToRecycle, true);
    }

    /**
     * 获取宽高比。
     *
     * @return 宽高比
     */
    public float getAspectRatio() {
        return aspectRatio / 128.0f;
    }

    /**
     * 设置宽高比。
     *
     * @param aspectRatio 宽高比
     * @return 是否发生变化
     */
    private boolean setAspectRatio(final float aspectRatio) {
        final int newAspectRatio = (int) Math.floor(aspectRatio * 128);
        if (this.aspectRatio != newAspectRatio) {
            this.aspectRatio = newAspectRatio;
            return true;
        }
        return false;
    }

    /**
     * 根据编解码器页面信息设置宽高比。
     *
     * @param page 页面信息
     * @return 是否发生变化
     */
    public boolean setAspectRatio(final CodecPageInfo page) {
        if (page != null) {
            return this.setAspectRatio(page.width / type.getWidthScale(), page.height);
        }
        return false;
    }

    /**
     * 根据宽度和高度设置宽高比。
     *
     * @param width  宽度
     * @param height 高度
     * @return 是否发生变化
     */
    public boolean setAspectRatio(final float width, final float height) {
        return setAspectRatio(width / height);
    }

    /**
     * 设置页面边界。
     *
     * @param pageBounds 边界矩形
     */
    public void setBounds(final RectF pageBounds) {
        storedZoom = 0.0f;
        zoomedBounds = null;
        bounds = pageBounds;
    }

    /**
     * 设置页面边界。
     *
     * @param l 左边界
     * @param t 上边界
     * @param r 右边界
     * @param b 下边界
     */
    public void setBounds(final float l, final float t, final float r, final float b) {
        if (bounds == null) {
            bounds = new RectF(l, t, r, b);
        } else {
            bounds.set(l, t, r, b);
        }
    }

    /**
     * 获取缩放后的页面边界。
     *
     * @param zoom 缩放比例
     * @return 缩放后的边界矩形
     */
    public RectF getBounds(final float zoom) {
        // if (z != storedZoom) {
        // storedZoom = z;
        // zoomedBounds = MathUtils.z(bounds, z);
        // }
        // return zoomedBounds;
        return MathUtils.zoom(bounds, zoom);
    }

    /**
     * 获取目标矩形缩放比例。
     *
     * @return 缩放比例
     */
    public float getTargetRectScale() {
        return type.getWidthScale();
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder("Page");
        buf.append("[");

        buf.append("index").append("=").append(index);
        buf.append(", ");
        buf.append("bounds").append("=").append(bounds);
        buf.append(", ");
        buf.append("aspectRatio").append("=").append(aspectRatio);
        buf.append(", ");
        buf.append("type").append("=").append(type.name());
        buf.append("]");
        return buf.toString();
    }

    /**
     * 获取目标矩形。
     * <p>
     * 将归一化矩形转换为目标页面矩形，考虑页面类型和边界。
     *
     * @param pageType      页面类型
     * @param pageBounds    页面边界
     * @param normalizedRect 归一化矩形
     * @return 目标矩形
     */
    public static RectF getTargetRect(final PageType pageType, final RectF pageBounds, final RectF normalizedRect) {
        final Matrix tmpMatrix = MatrixUtils.get();

        tmpMatrix.postScale(pageBounds.width() * pageType.getWidthScale(), pageBounds.height());
        tmpMatrix.postTranslate(pageBounds.left - pageBounds.width() * pageType.getLeftPos(), pageBounds.top);

        final RectF targetRectF = new RectF();
        tmpMatrix.mapRect(targetRectF, normalizedRect);

        MathUtils.floor(targetRectF);

        return targetRectF;
    }

    /**
     * 获取链接源矩形。
     *
     * @param pageBounds 页面边界
     * @param link       页面链接
     * @return 源矩形
     */
    public RectF getLinkSourceRect(final RectF pageBounds, final PageLink link) {
        if (link == null || link.sourceRect == null) {
            return null;
        }
        return getPageRegion(pageBounds, new RectF(link.sourceRect));
    }

    /**
     * 获取页面区域。
     * <p>
     * 考虑裁剪设置和页面类型，将源矩形转换为目标矩形。
     *
     * @param pageBounds 页面边界
     * @param sourceRect 源矩形
     * @return 页面区域矩形
     */
    public RectF getPageRegion(final RectF pageBounds, final RectF sourceRect) {
        final AppBook bs = SettingsManager.getBookSettings();
        final RectF cb = nodes.root.croppedBounds;
        if (bs != null && bs.cp && cb != null) {
            final Matrix m = MatrixUtils.get();
            final RectF psb = nodes.root.pageSliceBounds;
            m.postTranslate(psb.left - cb.left, psb.top - cb.top);
            m.postScale(psb.width() / cb.width(), psb.height() / cb.height());
            m.mapRect(sourceRect);
        }

        if (type == PageType.LEFT_PAGE && sourceRect.left >= 0.5f) {
            return null;
        }

        if (type == PageType.RIGHT_PAGE && sourceRect.right < 0.5f) {
            return null;
        }

        return getTargetRect(type, pageBounds, sourceRect);
    }
}
