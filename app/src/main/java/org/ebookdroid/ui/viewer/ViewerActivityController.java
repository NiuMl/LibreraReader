package org.ebookdroid.ui.viewer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.Intents;
import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.ResultResponse;
import com.foobnix.android.utils.Safe;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.dao2.FileMeta;
import com.foobnix.ext.CacheZipUtils;
import com.foobnix.model.AppBook;
import com.foobnix.model.AppSP;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.model.OutlineLinkWrapper;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.pdf.info.wrapper.DocumentWrapperUI;
import com.foobnix.pdf.search.activity.HorizontalModeController;
import com.foobnix.sys.TempHolder;
import com.foobnix.sys.VerticalModeController;
import com.foobnix.tts.TTSEngine;
import com.foobnix.tts.TTSNotification;
import com.foobnix.ui2.AdsFragmentActivity;
import com.foobnix.ui2.FileMetaCore;

import org.ebookdroid.BookType;
import org.ebookdroid.common.settings.SettingsManager;
import org.ebookdroid.common.settings.listeners.IBookSettingsChangeListener;
import org.ebookdroid.common.settings.types.DocumentViewMode;
import org.ebookdroid.core.DecodeService;
import org.ebookdroid.core.ViewState;
import org.ebookdroid.core.events.CurrentPageListener;
import org.ebookdroid.core.events.DecodingProgressListener;
import org.ebookdroid.core.models.DocumentModel;
import org.ebookdroid.core.models.ZoomModel;
import org.ebookdroid.droids.mupdf.codec.exceptions.MuPdfPasswordException;
import org.ebookdroid.ui.viewer.stubs.ActivityControllerStub;
import org.ebookdroid.ui.viewer.stubs.ViewContollerStub;
import org.emdev.ui.actions.ActionController;
import org.emdev.ui.actions.ActionEx;
import org.emdev.ui.actions.IActionController;
import org.emdev.ui.actions.params.EditableValue.PasswordEditable;
import org.emdev.ui.progress.IProgressIndicator;
import org.emdev.ui.tasks.BaseAsyncTask;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 阅读活动控制器。
 * <p>
 * 负责管理阅读活动的生命周期、文档加载、页面导航、设置监听等核心功能。
 * 实现了多个接口以响应解码进度、页面变化和设置变更事件。
 */
public class ViewerActivityController extends ActionController<VerticalViewActivity> implements IActivityController,
        DecodingProgressListener, CurrentPageListener, IBookSettingsChangeListener {

    /** 视图控制器引用 */
    private final AtomicReference<IViewController> ctrl = new AtomicReference<IViewController>(ViewContollerStub.STUB);

    /** 缩放模型 */
    private ZoomModel zoomModel;

    /** 文档模型 */
    private DocumentModel documentModel;

    /** 编解码器类型 */
    private BookType codecType;

    /** 活动意图 */
    private final Intent intent;

    /** 加载计数器 */
    private int loadingCount = 0;

    /** 文件名 */
    private String m_fileName;
    /** 标题 */
    private String title;

    /** 文档包装控制器 */
    private DocumentWrapperUI wrapperControlls;

    /** 垂直模式控制器 */
    private VerticalModeController controller;

    /** 查看器活动 */
    VerticalViewActivity viewerActivity;

    /**
     * Instantiates a new base viewer activity.
     */
    public ViewerActivityController(final VerticalViewActivity activity) {
        super(activity);
        this.viewerActivity = activity;
        this.intent = activity.getIntent();
        SettingsManager.addListener(this);

        controller = new VerticalModeController(activity, this);
        wrapperControlls = new DocumentWrapperUI(controller);
        LOG.d("ViewerActivityController create");
    }

    @Override public VerticalModeController getListener() {
        return controller;
    }

    /**
     * 创建前准备。
     *
     * @param activity 查看器活动
     */
    public void beforeCreate(final VerticalViewActivity activity) {
        if (getManagedComponent() != activity) {
            setManagedComponent(activity);
        }
    }

    /** 书籍加载完成回调 */
    Runnable onBookLoaded;

    /**
     * 设置书籍加载完成回调。
     *
     * @param onBookLoaded 加载完成后执行的回调
     */
    public void onBookLoaded(Runnable onBookLoaded) {
        this.onBookLoaded = onBookLoaded;
    }

    /**
     * 创建后初始化。
     * <p>
     * 初始化全屏模式、文档模型、标题等，处理书籍打开流程。
     *
     * @param a 查看器活动
     */
    public void afterCreate(VerticalViewActivity a) {
        final VerticalViewActivity activity = getManagedComponent();

        DocumentController.chooseFullScreen(activity, AppState.get().fullScreenMode);

        if (++loadingCount == 1) {
            documentModel = ActivityControllerStub.DM_STUB;

            if (intent == null || intent.getData() == null) {
                return;
            }

            String filePath = Apps.getBookPathFromActivity(activity);
            m_fileName = filePath;
            codecType = BookType.getByUri(m_fileName);

            FileMeta meta = FileMetaCore.createMetaIfNeed(m_fileName, false);
            title = meta.getTitle();
            if (TxtUtils.isEmpty(title)) {
                title = ExtUtils.getFileName(m_fileName);
            }
            LOG.d("Book-title", title);

            if (codecType == null) {
                if (getActivity() != null) {
                    Toast.makeText(getActivity(),
                                 Apps.getApplicationName(getActivity()) + " " + getActivity().getString(
                                         R.string.application_cannot_open_the_book), Toast.LENGTH_LONG)
                         .show();
                    getActivity().finish();
                }
                return;
            }

            LOG.d("codecType last", codecType);
            documentModel = new DocumentModel(codecType, getView());
            documentModel.addListener(ViewerActivityController.this);

            controller.setCurrentBook(new File(filePath));

            wrapperControlls.hideShowEditIcon();

            controller.addRecent(filePath);
            SettingsManager.getBookSettings(filePath);

            final AppBook.Diff diff = new AppBook.Diff(null, SettingsManager.getBookSettings());
            onBookSettingsChanged(null, SettingsManager.getBookSettings(), diff);

            if (intent.hasExtra("id2")) {
                wrapperControlls.showSelectTextMenu();
            }

            wrapperControlls.setTitle(title);
        }
        wrapperControlls.updateUI();

    }

    /**
     * 后处理创建。
     * <p>
     * 在视图创建完成后启动文档解码。
     */
    public void afterPostCreate() {

        if (loadingCount == 1 && documentModel != ActivityControllerStub.DM_STUB) {
            String stringExtra = intent.getStringExtra(DocumentController.EXTRA_PASSWORD);
            if (stringExtra == null) {
                stringExtra = "";
            }
            startDecoding(m_fileName, stringExtra);
        }

    }

    /** 总页数 */
    public int pageCount;

    /**
     * 启动文档解码。
     * <p>
     * 创建BookLoadTask异步加载文档，加载完成后处理页码跳转和目录加载。
     *
     * @param fileName 文件路径
     * @param password 密码（可为空）
     */
    public void startDecoding(final String fileName, final String password) {
        getManagedComponent().view.getView()
                                  .post(new BookLoadTask(fileName, password, new Runnable() {

                                      @Override public void run() {

                                          intent.putExtra(HorizontalModeController.EXTRA_PASSWORD, password);

                                          if (onBookLoaded != null) {
                                              onBookLoaded.run();
                                          }

                                          pageCount = controller.getPageCount();



                                          float percent =
                                                  Intents.getFloatAndClear(intent, DocumentController.EXTRA_PERCENT);

                                          if (percent > 0f) {
                                              LOG.d("startDecoding-onGoToPage", percent, pageCount);
                                              controller.onGoToPage(Math.round(pageCount * percent));

                                          }

                                          controller.loadOutline(new ResultResponse<List<OutlineLinkWrapper>>() {

                                              @Override public boolean onResultRecive(List<OutlineLinkWrapper> result) {
                                                  wrapperControlls.showOutline(result, controller.getPageCount());

                                                  return false;
                                              }
                                          });

                                      }
                                  }));
    }

    /**
     * 暂停活动。
     */
    public void onPause() {
        if (wrapperControlls != null) {
            wrapperControlls.onPause();
        }
    }

    /**
     * 销毁活动。
     */
    public void onDestroy() {
        if (wrapperControlls != null) {
            wrapperControlls.onDestroy();
        }
        LOG.d("ViewerActivityController onDestroy");
    }

    /**
     * 销毁前处理。
     * <p>
     * 回收文档模型，移除设置监听器。
     */
    public void beforeDestroy() {
        final boolean finishing = getManagedComponent().isFinishing();
        if (finishing) {
            getManagedComponent().view.onDestroy();
            if (documentModel != null) {
                documentModel.recycle();
            }
            SettingsManager.removeListener(this);
        }
        LOG.d("ViewerActivityController beforeDestroy");

    }

    /**
     * 销毁后处理。
     *
     * @param finishing 是否正在结束活动
     */
    public void afterDestroy(boolean finishing) {
        getDocumentController().onDestroy();
        LOG.d("ViewerActivityController afterDestroy");
    }

    /**
     * 弹出密码输入对话框。
     *
     * @param fileName 文件名
     * @param promtId 提示消息ID
     */
    public void askPassword(final String fileName, final int promtId) {
        final EditText input = new EditText(getManagedComponent());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        AlertDialog.Builder dialog = new AlertDialog.Builder(getManagedComponent());
        dialog.setTitle(R.string.enter_password);
        dialog.setView(input);
        dialog.setCancelable(false);
        dialog.setNegativeButton(R.string.cancel, new OnClickListener() {

            @Override public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                controller.onCloseActivityAdnShowInterstial();
            }
        });
        dialog.setPositiveButton(R.string.open_file, new OnClickListener() {

            @Override public void onClick(DialogInterface dialog, int which) {
                String txt = input.getText()
                                  .toString();
                if (TxtUtils.isNotEmpty(txt)) {
                    dialog.dismiss();
                    startDecoding(fileName, input.getText()
                                                 .toString());
                } else {
                    controller.onCloseActivityAdnShowInterstial();
                }
            }
        });
        dialog.show();

        //
    }

    /**
     * 显示错误对话框。
     *
     * @param msgId 消息ID
     * @param args 消息参数
     */
    public void showErrorDlg(final int msgId, final Object... args) {
        Toast.makeText(getManagedComponent(), msgId, Toast.LENGTH_SHORT)
             .show();
    }

    /**
     * 切换文档控制器。
     * <p>
     * 根据设置创建新的视图控制器，并切换当前控制器。
     *
     * @param bs 书籍设置
     * @return 新的视图控制器，失败返回null
     */
    protected IViewController switchDocumentController(final AppBook bs) {
        if (bs != null) {
            try {
                final IViewController newDc = DocumentViewMode.VERTICALL_SCROLL.create(this);
                if (newDc != null) {
                    final IViewController oldDc = ctrl.getAndSet(newDc);
                    getZoomModel().removeListener(oldDc);
                    getZoomModel().addListener(newDc);
                    return ctrl.get();
                }
            } catch (final Throwable e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * 解码进度变化回调。
     * <p>
     * 当前未实现具体逻辑。
     *
     * @param currentlyDecoding 当前解码的页码
     */
    @Override public void decodingProgressChanged(final int currentlyDecoding) {
    }

    /**
     * 当前页面变化回调。
     * <p>
     * 更新页码显示和标题。
     *
     * @param page 当前页码（从0开始）
     * @param pages 总页数
     */
    @Override public void currentPageChanged(final int page, int pages) {
        final int pageCount = documentModel.getPageCount();
        String pageText = "";
        if (pageCount > 0) {
            pageText = (page + 1) + "/" + pageCount;
        }

        wrapperControlls.updateUI();

        wrapperControlls.setTitle(title);
        controller.setTitle(title);

    }

    /**
     * 创建包装器。
     * <p>
     * 初始化UI包装控制器，处理文本格式文件的锁定状态。
     *
     * @param a 活动
     */
    public void createWrapper(AdsFragmentActivity a) {
        try {
            String file = Apps.getBookPathFromActivity(a);



            LOG.d("createWrapper", file);
            if (ExtUtils.isTextFomat(file)) {
                AppSP.get().isLocked = true;
            } else {
                if (AppState.get().isLockPDF) {
                    AppSP.get().isLocked = true;
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }

        wrapperControlls.initUI(a);
    }

    /**
     * 恢复活动。
     */
    public void onResume() {
        if (controller != null) {
            controller.onResume();
        }
        if (wrapperControlls != null) {
            wrapperControlls.onResume();
        }
    }

    /**
     * 配置变化处理。
     */
    public void onConfigChanged() {
        wrapperControlls.onConfigChanged();
    }

    /**
     * 跳转到指定页面。
     *
     * @param viewIndex 视图索引
     * @param offsetX X偏移量
     * @param offsetY Y偏移量
     * @param addToHistory 是否添加到历史记录
     * @return 视图状态
     */
    @Override
    public ViewState jumpToPage(final int viewIndex, final float offsetX, final float offsetY, boolean addToHistory) {
        // getDocumentController().goToPage(viewIndex, x, y);
        ViewState goToPage;
        if (addToHistory) {
            int curY = getDocumentController().getView()
                                              .getScrollY();
            goToPage = getDocumentController().goToPage(viewIndex);
            controller.getLinkHistory()
                      .add(curY);
            wrapperControlls.showHideHistory();
        } else {
            // getDocumentController().goToPage(viewIndex, x, y);
            goToPage = getDocumentController().goToPage(viewIndex);
        }
        return goToPage;
    }

    /**
     * 执行文本搜索。
     *
     * @param text 搜索文本
     * @param result 搜索结果回调
     * @param firstPage 起始页码
     * @param lastPage 结束页码
     */
    public final void doSearch(final String text, final ResultResponse<Integer> result, int firstPage, int lastPage) {
        getDecodeService().searchText(text, documentModel.getPages(), result, new Runnable() {

            @Override public void run() {
                getView().redrawView();
            }
        }, firstPage, lastPage);
    }

    /**
     * 显示对话框。
     *
     * @param action 动作对象
     */
    public void showDialog(final ActionEx action) {
        final Integer dialogId = action.getParameter("dialogId");
        getManagedComponent().showDialog(dialogId);

    }

    /**
     * 切换夜间模式。
     */
    public void toggleNightMode() {
        getDocumentController().toggleRenderingEffects();
        currentPageChanged(documentModel.getCurrentIndex().docIndex, getDocumentController().getBase()
                                                                                            .getDocumentModel()
                                                                                            .getPageCount());
    }

    /**
     * 切换裁剪模式。
     *
     * @param isCrop 是否裁剪
     */
    public void toggleCrop(boolean isCrop) {
        getDocumentController().toggleRenderingEffects();

        final IViewController newDc = switchDocumentController(SettingsManager.getBookSettings());
        newDc.init(null);
        newDc.show();

        currentPageChanged(documentModel.getCurrentIndex().docIndex, getDocumentController().getBase()
                                                                                            .getDocumentModel()
                                                                                            .getPageCount());

    }

    /**
     * 获取缩放模型。
     * <p>
     * 延迟初始化，首次调用时创建实例。
     *
     * @return 缩放模型
     */
    @Override public ZoomModel getZoomModel() {
        if (zoomModel == null) {
            zoomModel = new ZoomModel();
        }
        return zoomModel;
    }

    /**
     * 获取解码服务。
     *
     * @return 解码服务，文档模型为空时返回null
     */
    @Override public DecodeService getDecodeService() {
        return documentModel != null ? documentModel.decodeService : null;
    }

    /**
     * 获取文档模型。
     *
     * @return 文档模型
     */
    @Override public DocumentModel getDocumentModel() {
        return documentModel;
    }

    /**
     * 获取文档控制器。
     *
     * @return 视图控制器
     */
    @Override public IViewController getDocumentController() {
        return ctrl.get();
    }

    /**
     * 获取上下文。
     *
     * @return 上下文对象
     */
    @Override public Context getContext() {
        return getManagedComponent();
    }

    /**
     * 获取视图。
     *
     * @return 视图对象
     */
    @Override public IView getView() {
        return getManagedComponent().view;
    }

    /**
     * 获取活动。
     *
     * @return 活动对象
     */
    @Override public Activity getActivity() {
        return getManagedComponent();
    }

    /**
     * 获取动作控制器。
     *
     * @return 动作控制器（自身）
     */
    @Override public IActionController<?> getActionController() {
        return this;
    }

    /**
     * 关闭活动（显示插页广告）。
     *
     * @param action 动作对象
     */
    public void closeActivity(final ActionEx action) {
        viewerActivity.showInterstitial();
        LOG.d("ViewerActivityController closeActivity");
    }

    /**
     * 最终关闭活动。
     * <p>
     * 停止TTS朗读，回收文档模型，执行GC，完成活动关闭。
     *
     * @param action 关闭后执行的回调
     */
    public void closeActivityFinal(final Runnable action) {

        Safe.run(new Runnable() {

            @Override public void run() {

                TTSEngine.get()
                         .stop(null);
                TTSNotification.hideNotification();

                LOG.d("closeActivity 1");
                if (documentModel != null) {
                    documentModel.recycle();
                }

                LOG.d("closeActivity 2");
                LOG.d("closeActivity 3");
                getManagedComponent().finish();

                System.gc();
                //BitmapManager.clear("finish");

                if (action != null) {
                    action.run();
                }
            }
        });

        LOG.d("closeActivity DONE");
    }

    /**
     * 关闭活动（直接关闭）。
     *
     * @param action 动作对象
     */
    public void closeActivity1(final ActionEx action) {
        getManagedComponent().finish();

        System.gc();
        //BitmapManager.clear("finish");

        LOG.d("ViewerActivityController closeActivity1");
    }

    @Override
    public void onBookSettingsChanged(final AppBook oldSettings, final AppBook newSettings, final AppBook.Diff diff) {
        if (newSettings == null) {
            return;
        }

        boolean redrawn = false;
        if (diff.isSplitPagesChanged() || diff.isCropPagesChanged()) {
            redrawn = true;
            final IViewController newDc = switchDocumentController(newSettings);
            if (!diff.isFirstTime() && newDc != null) {
                newDc.init(null);
                newDc.show();
            }
        }

        if (diff.isFirstTime()) {
            getZoomModel().initZoom(newSettings.getZoom());
        }

        final IViewController dc = getDocumentController();

        if (!redrawn && (diff.isEffectsChanged())) {
            redrawn = true;
            dc.toggleRenderingEffects();
        }

        if (diff.isAnimationTypeChanged()) {
            dc.updateAnimationType();
        }

        // currentPageChanged(PageIndex.NULL, documentModel.getCurrentIndex());
        //currentPageChanged(newSettings.currentPage.do, -1);

    }

    public DocumentWrapperUI getWrapperControlls() {
        return wrapperControlls;
    }

    /**
     * 书籍加载任务。
     * <p>
     * 异步加载书籍文档，处理密码验证和异常情况。
     */
    final class BookLoadTask extends BaseAsyncTask<String, Throwable> implements IProgressIndicator, Runnable {

        /** 文件名 */
        private String m_fileName;
        /** 密码 */
        private final String m_password;
        /** 加载完成回调 */
        private final Runnable onBookLoaded;

        /**
         * 构造函数。
         *
         * @param fileName 文件路径
         * @param password 密码
         * @param onBookLoaded 加载完成回调
         */
        public BookLoadTask(final String fileName, final String password, Runnable onBookLoaded) {
            super(getManagedComponent());
            m_fileName = fileName;
            m_password = password;
            this.onBookLoaded = onBookLoaded;
        }

        /**
         * 执行任务。
         */
        @Override public void run() {
            execute();
        }

        /**
         * 书籍加载取消回调。
         */
        @Override public void onBookCancel() {
            super.onBookCancel();
            LOG.d("onBookCancel");
            closeActivity(null);
        }

        /**
         * 后台加载书籍。
         * <p>
         * 打开文档并初始化文档控制器。
         *
         * @param params 参数
         * @return 异常（如有），成功返回null
         */
        @Override protected Throwable doInBackground(final String... params) {
            try {
                //Thread.sleep(3000);
                m_fileName = Apps.getBookPathFromActivity(getActivity());

                documentModel.open(m_fileName, m_password);
                getDocumentController().init(this);
                return null;
            } catch (final MuPdfPasswordException pex) {
                return pex;
            } catch (final Exception e) {
                CacheZipUtils.createAllCacheDirs();
                LOG.e(e);
                return e;
            } catch (final Throwable th) {
                LOG.e(th);
                return th;
            }
        }

        /**
         * 加载完成处理。
         * <p>
         * 处理加载结果，显示文档，处理TXT文件的延迟跳转，处理密码错误和异常情况。
         *
         * @param result 异常结果
         */
        @Override protected void onPostExecute(Throwable result) {
            try {
                LOG.d("onPostExecute");
                if (TempHolder.get().loadingCancelled.get()) {
                    super.onPostExecute(result);
                    closeActivity(null);
                    return;
                }

                wrapperControlls.onLoadBookFinish();
                if (result == null) {
                    try {
                        getDocumentController().show();

                        final DocumentModel dm = getDocumentModel();
                        currentPageChanged(dm.getCurrentIndex().docIndex, -1);
                        
                        final AppBook bs = SettingsManager.getBookSettings();
                        if (bs.path != null && ExtUtils.isTextFomat(bs.path)) {
                            int currentPage = dm.getCurrentIndex().docIndex;
                            LOG.d("ViewerActivityController", "TXT file detected, currentPage: " + currentPage);
                            if (currentPage > 0) {
                                getDocumentController().getView().getView().postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        getDocumentController().goToPage(currentPage, bs.x, bs.y);
                                    }
                                }, 50);
                            }
                        }
                        
                        onBookLoaded.run();

                    } catch (final Throwable th) {
                        result = th;
                    }
                }

                super.onPostExecute(result);
                if (result instanceof MuPdfPasswordException) {
                    final MuPdfPasswordException pex = (MuPdfPasswordException) result;
                    final int promptId =
                            pex.isWrongPasswordEntered() ? R.string.msg_wrong_password : R.string.msg_password_required;

                    askPassword(m_fileName, promptId);

                } else if (result != null) {
                    final String msg = result.getMessage();
                    showErrorDlg(R.string.msg_unexpected_error, msg);
                }
            } catch (final Throwable th) {
                LOG.e(th);
            }

        }

        @Override public void setProgressDialogMessage(final int resourceID, final Object... args) {
            publishProgress(getManagedComponent().getString(resourceID, args));
        }
    }

}
