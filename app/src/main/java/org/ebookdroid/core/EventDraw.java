package org.ebookdroid.core;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.text.TextPaint;

import androidx.core.graphics.ColorUtils;

import com.foobnix.LibreraApp;
import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppSP;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.model.BookCSS;
import com.foobnix.pdf.info.wrapper.MagicHelper;

import org.ebookdroid.core.codec.PageLink;
import org.ebookdroid.ui.viewer.IActivityController;
import org.emdev.utils.LengthUtils;

import java.util.Queue;

/**
 * 页面绘制事件类。
 * <p>
 * 负责页面的绘制工作，包括背景绘制、页面内容渲染、链接高亮、选中文本高亮等。
 * 实现了IEvent接口，作为事件驱动架构中的绘制事件。
 */
public class EventDraw implements IEvent {

    /** 矩形绘制画笔 */
    static Paint rect = new Paint();

    static {
        rect.setColor(Color.DKGRAY);
        rect.setStrokeWidth(Dips.DP_1);
        rect.setStyle(Style.STROKE);
    }

    /** 固定页面边界 */
    final RectF fixedPageBounds = new RectF();
    /** 事件队列，用于对象复用 */
    private final Queue<EventDraw> eventQueue;
    /** 视图状态 */
    public ViewState viewState;
    /** 页面树层级 */
    public PageTreeLevel level;
    /** 画布对象 */
    public Canvas canvas;
    /** 页面边界 */
    RectF pageBounds;
    /** 链接绘制画笔 */
    Paint paintWrods = new Paint();
    /** 活动控制器 */
    private IActivityController base;

    /**
     * 构造函数。
     *
     * @param eventQueue 事件队列，用于对象复用
     */
    EventDraw(final Queue<EventDraw> eventQueue) {
        this.eventQueue = eventQueue;
        paintWrods.setAlpha(60);
        paintWrods.setStrokeWidth(Dips.dpToPx(1));
        paintWrods.setTextSize(30);
    }

    /**
     * 初始化事件。
     *
     * @param viewState 视图状态
     * @param canvas    画布对象
     * @param base      活动控制器
     */
    void init(final ViewState viewState, final Canvas canvas, IActivityController base) {
        this.viewState = viewState;
        this.base = base;
        this.level = PageTreeLevel.getLevel(viewState.zoom);
        this.canvas = canvas;

    }

    /**
     * 从已有事件复制初始化。
     *
     * @param event   源事件
     * @param canvas  画布对象
     * @param base    活动控制器
     */
    void init(final EventDraw event, final Canvas canvas, IActivityController base) {
        this.base = base;
        this.viewState = event.viewState;
        this.level = event.level;
        this.canvas = canvas;
    }

    /**
     * 释放资源并将对象放回队列。
     */
    void release() {
        this.canvas = null;
        this.level = null;
        this.pageBounds = null;
        this.viewState = null;
        eventQueue.offer(this);
    }

    /**
     * 处理绘制事件，绘制整个视图。
     *
     * @return 视图状态
     */
    @Override
    public ViewState process() {
        try {

//            if (AppState.get().isOLED && !AppState.get().isDayNotInvert /* && MagicHelper.getBgColor() == Color.BLACK */) {
//                viewState.paint.backgroundFillPaint.setColor(Color.BLACK);
//            } else {
//                viewState.paint.backgroundFillPaint.setColor(MagicHelper.ligtherColor(MagicHelper.getBgColor()));
//            }
            viewState.paint.backgroundFillPaint.setColor(MagicHelper.getForegroundColor());
            if (canvas != null) {
                canvas.drawRect(canvas.getClipBounds(), viewState.paint.backgroundFillPaint);
            }

            viewState.ctrl.drawView(this);
            return viewState;
        } finally {
            release();
        }
    }

    /**
     * 处理页面绘制。
     * <p>
     * 绘制页面背景、页面内容、背景图片、页码等。
     *
     * @param page 页面对象
     * @return 是否处理成功
     */
    @Override
    public boolean process(final Page page) {
        pageBounds = viewState.getBounds(page);

        drawPageBackground(page);

        final boolean res = process(page.nodes);

        if (MagicHelper.isNeedBookBackgroundImage()) {


            Bitmap bgBitmap = MagicHelper.getBackgroundImage();
            Matrix m = new Matrix();
            float width = fixedPageBounds.width();
            float height = fixedPageBounds.height();
            m.setScale(width / bgBitmap.getWidth(), height / bgBitmap.getHeight());
            m.postTranslate(fixedPageBounds.left, fixedPageBounds.top);

            Paint p = new Paint();
            p.setFilterBitmap(false);
            p.setAntiAlias(false);
            p.setDither(false);

            p.setAlpha(255 - MagicHelper.getTransparencyInt());
            canvas.drawBitmap(bgBitmap, m, p);
        }
        //if (AppState.get().isOLED && !AppState.get().isDayNotInvert/* && !TempHolder.get().isTextFormat */) {
          //  canvas.drawRect(fixedPageBounds.left - Dips.DP_1, fixedPageBounds.top - Dips.DP_1,
           //     fixedPageBounds.right + Dips.DP_1, fixedPageBounds.bottom + Dips.DP_1, rect);
        //}

        if (AppState.get().isShowLastPageRed && AppSP.get().readingMode == AppState.READING_MODE_MUSICIAN && page.isLastPage) {
            rect.setColor(ColorUtils.setAlphaComponent(Color.RED, 150));
            rect.setStyle(Style.FILL);
            canvas.drawRect(fixedPageBounds.left - Dips.DP_1, fixedPageBounds.bottom - Dips.DP_25, fixedPageBounds.right + Dips.DP_1, fixedPageBounds.bottom + Dips.DP_1, rect);
            canvas.drawRect(fixedPageBounds.left - Dips.DP_1, fixedPageBounds.bottom - fixedPageBounds.height() / 4 - Dips.DP_5, fixedPageBounds.right + Dips.DP_1, fixedPageBounds.bottom - fixedPageBounds.height() / 4, rect);

        } else if (AppState.get().isShowLineDividing && AppSP.get().readingMode == AppState.READING_MODE_MUSICIAN) {
            rect.setColor(ColorUtils.setAlphaComponent(Color.GRAY, 200));
            rect.setStyle(Style.FILL);
            canvas.drawRect(fixedPageBounds.left - Dips.DP_1, fixedPageBounds.bottom - Dips.DP_2, fixedPageBounds.right + Dips.DP_1, fixedPageBounds.bottom + Dips.DP_1, rect);

        }


        // TODO Draw there
        // drawLine(page);
        if (!(BookCSS.get().isTextFormat() || AppSP.get().readingMode == AppState.READING_MODE_MUSICIAN)) {
            drawPageLinks(page);
        }
        // drawSomething(page);
        // drawHighlights(page);
        drawSelectedText(page);

        return res;
    }

    /**
     * 处理页面树绘制。
     *
     * @param nodes 页面树
     * @return 是否处理成功
     */
    @Override
    public boolean process(final PageTree nodes) {
        return process(nodes, level);
    }

    /**
     * 处理页面树绘制（指定层级）。
     *
     * @param nodes  页面树
     * @param level  页面树层级
     * @return 是否处理成功
     */
    @Override
    public boolean process(final PageTree nodes, final PageTreeLevel level) {
        return nodes.process(this, level, false);
    }

    /**
     * 处理页面树节点绘制。
     *
     * @param node 页面树节点
     * @return 是否处理成功
     */
    @Override
    public boolean process(final PageTreeNode node) {
        final RectF nodeRect = node.getTargetRect(pageBounds);

        if (!viewState.isNodeVisible(nodeRect)) {
            return false;
        }

        try {
            if (node.holder.drawBitmap(canvas, viewState.paint, viewState.viewBase, nodeRect, nodeRect)) {
                return true;
            }

            if (node.parent != null) {
                final RectF parentRect = node.parent.getTargetRect(pageBounds);
                if (node.parent.holder.drawBitmap(canvas, viewState.paint, viewState.viewBase, parentRect, nodeRect)) {
                    return true;
                }
            }

            return node.page.nodes.paintChildren(this, node, nodeRect);

        } finally {
        }
    }

    /**
     * 绘制子节点。
     *
     * @param node     父节点
     * @param child    子节点
     * @param nodeRect 父节点矩形
     * @return 是否绘制成功
     */
    public boolean paintChild(final PageTreeNode node, final PageTreeNode child, final RectF nodeRect) {
        final RectF childRect = child.getTargetRect(pageBounds);
        return child.holder.drawBitmap(canvas, viewState.paint, viewState.viewBase, childRect, nodeRect);
    }

    /**
     * 绘制页面背景。
     * <p>
     * 绘制页面背景颜色，并在非文本格式下显示页码。
     *
     * @param page 页面对象
     */
    protected void drawPageBackground(final Page page) {
        if (canvas == null) {
            LOG.d("canvas is null");
            return;
        }

        fixedPageBounds.set(pageBounds);
        fixedPageBounds.offset(-viewState.viewBase.x, -viewState.viewBase.y);

        viewState.paint.fillPaint.setColor(MagicHelper.getBgColor());
        canvas.drawRect(fixedPageBounds, viewState.paint.fillPaint);

        if (!BookCSS.get().isTextFormat()) {
            if (page.nodes != null && page.nodes.hasContent()) {
                final TextPaint textPaint = viewState.paint.textPaint;
                textPaint.setTextSize(Dips.spToPx(16));
                textPaint.setColor(MagicHelper.getTextColor());

                final String text = LibreraApp.context.getString(R.string.page) + " " + (page.index.viewIndex + 1);
                canvas.drawText(text, fixedPageBounds.centerX(), fixedPageBounds.centerY(), textPaint);
            }
        }
    }


    /**
     * 绘制页面链接。
     * <p>
     * 在链接位置绘制下划线表示可点击区域。
     *
     * @param page 页面对象
     */
    private void drawPageLinks(final Page page) {

        if (LengthUtils.isEmpty(page.links)) {
            return;
        }

        paintWrods.setColor(AppState.get().isDayNotInvert ? Color.BLUE : Color.YELLOW);
        paintWrods.setAlpha(60);

        for (final PageLink link : page.links) {
            final RectF rect = page.getLinkSourceRect(pageBounds, link);
            if (rect != null) {
                rect.offset(-viewState.viewBase.x, -viewState.viewBase.y);
                // canvas.drawRect(rect, paintWrods);
                canvas.drawLine(rect.left, rect.bottom, rect.right, rect.bottom, paintWrods);
            }
        }
    }

    /**
     * 绘制调试内容。
     * <p>
     * 用于调试目的，绘制一个品红色矩形。
     *
     * @param page 页面对象
     */
    private void drawSomething(final Page page) {
        final RectF link = new RectF(0.1f, 0.1f, 0.3f, 0.3f);
        final RectF rect = page.getPageRegion(pageBounds, new RectF(link));
        rect.offset(-viewState.viewBase.x, -viewState.viewBase.y);
        final Paint p = new Paint();
        p.setColor(Color.MAGENTA);
        p.setAlpha(40);
        canvas.drawRect(rect, p);
    }

    /**
     * 绘制选中文本高亮。
     * <p>
     * 绘制选中区域的高亮矩形。
     *
     * @param page 页面对象
     */
    private void drawSelectedText(final Page page) {
        final Paint p = new Paint();
        p.setColor(AppState.get().isDayNotInvert ? Color.BLUE : Color.YELLOW);
        p.setAlpha(60);

        if (page.selectionAnnotion != null) {
            final RectF rect = page.getPageRegion(pageBounds, new RectF(page.selectionAnnotion));
            rect.offset(-viewState.viewBase.x, -viewState.viewBase.y);
            canvas.drawRect(rect, p);
        }

        if (page.selectedText.isEmpty()) {
            return;
        }
        for (RectF selected : page.selectedText) {
            final RectF rect = page.getPageRegion(pageBounds, new RectF(selected));
            rect.offset(-viewState.viewBase.x, -viewState.viewBase.y);
            canvas.drawRect(rect, p);
        }

    }

}
