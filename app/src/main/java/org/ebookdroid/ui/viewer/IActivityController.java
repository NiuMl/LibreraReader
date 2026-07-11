package org.ebookdroid.ui.viewer;

import android.app.Activity;
import android.content.Context;

import com.foobnix.sys.VerticalModeController;

import org.ebookdroid.core.DecodeService;
import org.ebookdroid.core.ViewState;
import org.ebookdroid.core.models.DocumentModel;
import org.ebookdroid.core.models.ZoomModel;
import org.emdev.ui.actions.IActionController;

/**
 * 活动控制器接口。
 * <p>
 * 定义阅读器活动的核心控制方法，包括上下文获取、解码服务、文档模型、视图管理等。
 */
public interface IActivityController extends IActionController<VerticalViewActivity> {

    /**
     * 获取上下文。
     *
     * @return 上下文
     */
    Context getContext();

    /**
     * 获取活动实例。
     *
     * @return 活动实例
     */
    Activity getActivity();

    /**
     * 获取解码服务。
     *
     * @return 解码服务
     */
    DecodeService getDecodeService();

    /**
     * 获取文档模型。
     *
     * @return 文档模型
     */
    DocumentModel getDocumentModel();

    /**
     * 获取视图。
     *
     * @return 视图
     */
    IView getView();

    /**
     * 获取文档控制器。
     *
     * @return 视图控制器
     */
    IViewController getDocumentController();

    /**
     * 获取动作控制器。
     *
     * @return 动作控制器
     */
    IActionController<?> getActionController();

    /**
     * 获取缩放模型。
     *
     * @return 缩放模型
     */
    ZoomModel getZoomModel();


    /**
     * 跳转到指定页面。
     *
     * @param viewIndex   视图索引
     * @param offsetX     X偏移量
     * @param offsetY     Y偏移量
     * @param addToHistory 是否添加到历史记录
     * @return 视图状态
     */
    ViewState jumpToPage(int viewIndex, float offsetX, float offsetY, boolean addToHistory);

    /**
     * 获取垂直模式控制器。
     *
     * @return 垂直模式控制器
     */
    VerticalModeController getListener();

}
