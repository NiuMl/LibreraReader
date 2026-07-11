package org.ebookdroid.ui.viewer;

import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;

import org.ebookdroid.common.settings.types.PageAlign;
import org.ebookdroid.core.EventDraw;
import org.ebookdroid.core.Page;
import org.ebookdroid.core.ViewState;
import org.ebookdroid.core.events.ZoomListener;
import org.emdev.ui.progress.IProgressIndicator;

/**
 * 视图控制器接口。
 * <p>
 * 定义阅读器视图的核心控制方法，包括页面导航、视图渲染、触摸事件处理等。
 */
public interface IViewController extends ZoomListener {

    /**
     * 判断是否已初始化。
     *
     * @return 是否已初始化
     */
    public boolean isInitialized();

    /**
     * 初始化视图控制器。
     *
     * @param bookLoadTask 书籍加载进度指示器
     */
    void init(IProgressIndicator bookLoadTask);

    /**
     * 显示视图。
     */
    void show();

    /* Page related methods */

    /**
     * 跳转到指定页面。
     *
     * @param page 页面索引
     * @return 视图状态
     */
    ViewState goToPage(int page);

    /**
     * 跳转到指定页面并居中。
     *
     * @param page 页面索引
     * @return 视图状态
     */
    ViewState goToPageAndCenter(int page);

    /**
     * 跳转到指定页面。
     *
     * @param page    页面索引
     * @param animate 是否动画
     * @return 视图状态
     */
    ViewState goToPage(int page, boolean animate);

    /**
     * 跳转到指定页面并设置偏移。
     *
     * @param page     页面索引
     * @param offsetX  X偏移量
     * @param offsetY  Y偏移量
     * @return 视图状态
     */
    ViewState goToPage(int page, float offsetX, float offsetY);

    /**
     * 跳转到链接目标。
     *
     * @param pageDocIndex 文档页面索引
     * @param targetRect   目标矩形
     * @param addToHistory 是否添加到历史记录
     * @return 视图状态
     */
    ViewState goToLink(int pageDocIndex, RectF targetRect, boolean addToHistory);

    /**
     * 计算页面边界。
     *
     * @param pageAlign      页面对齐方式
     * @param pageAspectRatio 页面宽高比
     * @param width          宽度
     * @param height         高度
     * @return 页面边界矩形
     */
    RectF calcPageBounds(PageAlign pageAlign, float pageAspectRatio, int width, int height);

    /**
     * 使页面尺寸失效。
     *
     * @param reason      失效原因
     * @param changedPage 变化的页面
     */
    void invalidatePageSizes(InvalidateSizeReason reason, Page changedPage);

    /**
     * 获取第一个可见页面。
     *
     * @return 页面索引
     */
    int getFirstVisiblePage();

    /**
     * 计算当前页面。
     *
     * @param viewState    视图状态
     * @param firstVisible 第一个可见页面
     * @param lastVisible  最后一个可见页面
     * @return 当前页面索引
     */
    int calculateCurrentPage(ViewState viewState, int firstVisible, int lastVisible);

    /**
     * 获取最后一个可见页面。
     *
     * @return 页面索引
     */
    int getLastVisiblePage();

    // void verticalConfigScroll(int i);

    /**
     * 重绘视图。
     */
    void redrawView();

    /**
     * 根据视图状态重绘视图。
     *
     * @param viewState 视图状态
     */
    void redrawView(ViewState viewState);

    /**
     * 设置页面对齐方式。
     *
     * @param byResValue 对齐方式
     */
    void setAlign(PageAlign byResValue);

    /* Infrastructure methods */

    /**
     * 获取活动控制器。
     *
     * @return 活动控制器
     */
    IActivityController getBase();

    /**
     * 获取视图。
     *
     * @return 视图
     */
    IView getView();

    /**
     * 更新动画类型。
     */
    void updateAnimationType();

    /**
     * 更新内存设置。
     */
    void updateMemorySettings();

    /**
     * 尺寸失效原因枚举。
     */
    public static enum InvalidateSizeReason {
        INIT, LAYOUT, PAGE_ALIGN, PAGE_LOADED;
    }

    /**
     * 处理布局变化。
     *
     * @param layoutChanged 是否布局变化
     * @return 是否处理成功
     */
    boolean onLayoutChanged(boolean layoutChanged);

    /**
     * 获取滚动限制。
     *
     * @return 滚动限制矩形
     */
    Rect getScrollLimits();

    /**
     * 获取底部滚动限制。
     *
     * @return 底部滚动限制
     */
    int getBottomScrollLimit();

    /**
     * 判断页面是否可见。
     *
     * @param page      页面
     * @param viewState 视图状态
     * @return 是否可见
     */
    boolean isPageVisible(Page page, ViewState viewState);
    
    /**
     * 处理触摸事件。
     *
     * @param ev 触摸事件
     * @return 是否处理
     */
    boolean onTouchEvent(MotionEvent ev);

    /**
     * 处理滚动变化。
     *
     * @param dX X滚动变化量
     * @param dY Y滚动变化量
     */
    void onScrollChanged(int dX, final int dY);

    /**
     * 切换渲染效果。
     */
    void toggleRenderingEffects();

    /**
     * 绘制视图。
     *
     * @param eventDraw 绘制事件
     */
    void drawView(EventDraw eventDraw);

    /**
     * 页面更新。
     *
     * @param viewState 视图状态
     * @param page      页面
     */
    void pageUpdated(ViewState viewState, Page page);

    /**
     * 使滚动失效。
     */
    void invalidateScroll();

    /**
     * 销毁视图控制器。
     */
    void onDestroy();

    /**
     * 清除选中的文本。
     */
    void clearSelectedText();
}
