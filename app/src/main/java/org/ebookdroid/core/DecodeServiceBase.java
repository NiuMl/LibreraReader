package org.ebookdroid.core;

import android.graphics.Bitmap.Config;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Pair;

import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.ResultResponse;
import com.foobnix.android.utils.Safe;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.pdf.info.AppsConfig;
import com.foobnix.pdf.info.model.AnnotationType;
import com.foobnix.pdf.info.model.BookCSS;
import com.foobnix.sys.Colors;
import com.foobnix.sys.ImageExtractor;
import com.foobnix.sys.TempHolder;

import org.ebookdroid.BookType;
import org.ebookdroid.common.bitmaps.BitmapManager;
import org.ebookdroid.common.bitmaps.BitmapRef;
import org.ebookdroid.common.settings.CoreSettings;
import org.ebookdroid.core.codec.Annotation;
import org.ebookdroid.core.codec.CodecContext;
import org.ebookdroid.core.codec.CodecDocument;
import org.ebookdroid.core.codec.CodecPage;
import org.ebookdroid.core.codec.CodecPageHolder;
import org.ebookdroid.core.codec.CodecPageInfo;
import org.ebookdroid.core.codec.OutlineLink;
import org.ebookdroid.core.codec.PageLink;
import org.ebookdroid.core.crop.PageCropper;
import org.ebookdroid.droids.mupdf.codec.TextWord;
import org.ebookdroid.ui.viewer.IView;
import org.ebookdroid.ui.viewer.IViewController.InvalidateSizeReason;
import org.emdev.utils.LengthUtils;
import org.emdev.utils.MathUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 文档解码服务基类。
 * <p>
 * 实现DecodeService接口，提供文档解码、页面渲染、注释管理等核心功能。
 * 使用后台线程执行解码任务，支持任务优先级和自动资源回收。
 */
public class DecodeServiceBase implements DecodeService {

    /** 任务ID生成器 */
    static final AtomicLong TASK_ID_SEQ = new AtomicLong();

    /** 编解码器上下文 */
    final CodecContext codecContext;
    /** 是否已回收 */
    final AtomicBoolean isRecycled = new AtomicBoolean();
    /** 视图状态 */
    final AtomicReference<ViewState> viewState = new AtomicReference<ViewState>();
    /** 解码任务映射 */
    final Map<PageTreeNode, DecodeTask> decodingTasks = new IdentityHashMap<PageTreeNode, DecodeTask>();
    /** 任务列表 */
    final List<Task> tasks = new ArrayList<Task>();
    /** 任务执行器 */
    ExecutorRunnable executor = new ExecutorRunnable();
    /** 编解码器文档 */
    private CodecDocument codecDocument;
    /** 页面缓存，自动回收最老的页面 */
    private Map<Integer, CodecPageHolder> pages = new LinkedHashMap<Integer, CodecPageHolder>() {

        private static final long serialVersionUID = -8845124816503128098L;

        @Override
        protected boolean removeEldestEntry(final Map.Entry<Integer, CodecPageHolder> eldest) {
            if (this.size() > getCacheSize()) {
                final CodecPageHolder value = eldest != null ? eldest.getValue() : null;
                if (value != null) {
                    if (value.isInvalid(-1)) {
                        return true;
                    } else {
                        boolean recycled = value.recycle(-1, false);
                        return recycled;
                    }
                }
            }
            return false;
        }

    };
    /** 视图接口 */
    private IView view;

    /**
     * 构造函数。
     *
     * @param codecContext 编解码器上下文
     * @param view         视图接口
     */
    public DecodeServiceBase(final CodecContext codecContext, IView view) {
        this.codecContext = codecContext;
        this.view = view;
    }

    @Override
    public int getPixelFormat() {
        final Config cfg = getBitmapConfig();
        switch (cfg) {
            case ALPHA_8:
                return PixelFormat.A_8;
            case ARGB_8888:
                return PixelFormat.RGBA_8888;
            default:
                return PixelFormat.RGB_565;
        }
    }

    @Override
    public synchronized boolean hasAnnotationChanges() {
        return codecDocument != null && !codecDocument.isRecycled() && codecDocument.hasChanges();
    }

    @Override
    public void saveAnnotations(final String path, final Runnable response) {

        executor.addAny(new Task(0) {

            @Override
            public void run() {
                LOG.d("saveAnnotations Begin");
                if (hasAnnotationChanges()) {
                    codecDocument.saveAnnotations(path);
                } else {
                    LOG.d("NO Annotations for save!!!");
                }
                LOG.d("saveAnnotations DONE");
                response.run();
            }
        });
    }

    @Override
    public Config getBitmapConfig() {
        return this.codecContext.getBitmapConfig();
    }

    @Override
    public void open(final String fileName, final String password) {
        ImageExtractor.clearCodeDocument();
        codecDocument = codecContext.openDocument(fileName, password);
        ImageExtractor.init(codecDocument, fileName);

    }

    @Override
    public CodecPageInfo getUnifiedPageInfo() {
        return codecDocument != null ? codecDocument.getUnifiedPageInfo() : null;
    }

    @Override
    public CodecPageInfo getPageInfo(final int pageIndex) {
        return codecDocument != null ? codecDocument.getPageInfo(pageIndex) : null;
    }

    @Override
    public void updateViewState(final ViewState viewState) {
        this.viewState.set(viewState);
    }

    @Override
    public void addAnnotation(Map<Integer, List<PointF>> points, int color, float width, float alpha, ResultResponse<Pair<Integer, List<Annotation>>> result) {
        final AddAnnotationTask anTask = new AddAnnotationTask(points, color, width, alpha, result);
        executor.addAny(anTask);
    }

    @Override
    public void decodePage(final ViewState viewState, final PageTreeNode node) {
        if (isRecycled.get()) {
            return;
        }

        final DecodeTask decodeTask = new DecodeTask(viewState, node);
        updateViewState(viewState);
        executor.add(decodeTask);
    }

    @Override
    public void stopDecoding(final PageTreeNode node, final String reason) {
        executor.stopDecoding(null, node, reason);
    }

    @Override
    public void updateAnnotation(int page, float[] color, PointF[][] points, float width, float alpha) {
        if (points != null) {
            getPage(page).addAnnotation(color, points, width, alpha);
        }

    }

    @Override
    public void deleteAnnotation(final long pageHandle, final int page, final int index, final ResultResponse<List<Annotation>> response) {
        executor.addAny(new Task(0) {

            @Override
            public void run() {
                codecDocument.deleteAnnotation(pageHandle, index);
                pages.clear();
                response.onResultRecive(getPage(page).getAnnotations());
            }
        });

    }

    @Override
    public void searchText(final String text, final Page[] pages, final ResultResponse<Integer> response, final Runnable finish, int firstPage, int lastPage) {
        Thread t = new Thread("@T searchText") {
            @Override
            public void run() {
                PageSearcher pageSearcher = new PageSearcher();
                pageSearcher.setTextForSearch(text);
                pageSearcher.setListener(new PageSearcher.OnWordSearched() {
                    @Override
                    public void onSearch(TextWord word, Object data) {
                        if (!(data instanceof Page)) return;
                        Page page = (Page) data;
                        if (page.selectedText == null || page.selectedText.size() <= 0) {
                            response.onResultRecive(page.index.docIndex);
                            LOG.d("Find on page", page.index.docIndex, text);
                        }
                        if (page.selectedText == null) page.selectedText = new ArrayList<>();
                        if (!page.selectedText.contains(word)) page.selectedText.add(word);
                    }
                });
                for (Page page : pages) {


                    if (!TempHolder.isSeaching) {
                        response.onResultRecive(Integer.MAX_VALUE);
                        finish.run();
                        return;
                    }
                    if (page.index.docIndex < firstPage) {
                        continue;
                    }
                    if (page.index.docIndex > lastPage) {
                        continue;
                    }

                    if (page.index.docIndex > 1) {
                        response.onResultRecive(page.index.docIndex * -1);
                    }

                    if (isRecycled.get()) {
                        TempHolder.isSeaching = false;
                        return;
                    }
                    if (page.texts == null) {
                        final CodecPage page2 = codecDocument.getPage(page.index.docIndex);
                        page.texts = page2.getText();
                        if (!page2.isRecycled()) {
                            executor.addAny(new Task(0) {

                                @Override
                                public void run() {
                                    page2.recycle();
                                }
                            });
                        }
                    }

                    page.selectedText = new ArrayList<TextWord>();
                    List<TextWord> findText = page.findText(text);
                    if (findText != null && !findText.isEmpty()) {
                        page.selectedText = findText;
                        response.onResultRecive(page.index.docIndex);
                        LOG.d("Find on page1", page.index.docIndex, text);
                    }
                    pageSearcher.searchAtPage(page);
                }
                response.onResultRecive(-1);
                finish.run();
                TempHolder.isSeaching = false;
            }

            ;

        };
        t.start();
    }

    @Override
    public void underlineText(final int page, final PointF[] points, final int color, final AnnotationType type, final ResultResponse<List<Annotation>> callback) {
        executor.addAny(new Task(0) {

            @Override
            public void run() {
                getPage(page).addMarkupAnnotation(points, type, Colors.toMupdfColor(color));
                pages.clear();
                callback.onResultRecive(getPage(page).getAnnotations());
            }
        });

    }

    /**
     * 执行页面解码。
     * <p>
     * 核心解码流程：获取页面Holder、检查裁剪、计算缩放尺寸、渲染位图、
     * 获取链接和注释、完成解码。处理内存溢出异常。
     *
     * @param task 解码任务
     */
    void performDecode(final DecodeTask task) {
        if (executor.isTaskDead(task)) {
            return;
        }

        CodecPageHolder holder = null;
        CodecPage vuPage = null;
        Rect r = null;
        RectF croppedPageBounds = null;

        // TempHolder.lock.lock();
        try {
            holder = getPageHolder(task.id, task.pageNumber);
            vuPage = holder.getPage(task.id);
            if (executor.isTaskDead(task)) {
                return;
            }

            croppedPageBounds = checkCropping(task, vuPage);
            if (executor.isTaskDead(task)) {
                return;
            }

            r = getScaledSize(task.node, task.viewState.zoom, croppedPageBounds, vuPage);

            final RectF actualSliceBounds = task.node.croppedBounds != null ? task.node.croppedBounds : task.node.pageSliceBounds;

            // TempHolder.lock.lock();
            final BitmapRef bitmap = vuPage.renderBitmap(r.width(), r.height(), actualSliceBounds, true);
            // TempHolder.lock.unlock();

            if (executor.isTaskDead(task)) {
                BitmapManager.release(bitmap);
                return;
            }
            // TempHolder.lock.lock();
            if (task.node.page.links == null) {
                task.node.page.links = vuPage.getPageLinks();
                if (LengthUtils.isNotEmpty(task.node.page.links)) {
                }
            }


            if (codecDocument.getBookType() == BookType.PDF && task.node.page.annotations == null) {
                task.node.page.annotations = vuPage.getAnnotations();
            }

            if (task.node.page.texts == null) {
                task.node.page.texts = vuPage.getText();
            }
            // TempHolder.lock.unlock();

            finishDecoding(task, vuPage, bitmap, r, croppedPageBounds);
            // test
            // vuPage.recycle();

        } catch (final OutOfMemoryError ex) {
            for (int i = 0; i <= CoreSettings.getInstance().pagesInMemory; i++) {
                getPages().put(Integer.MAX_VALUE - i, null);
            }
            getPages().clear();
            if (vuPage != null) {
                vuPage.recycle();
            }

            //BitmapManager.clear("DecodeService OutOfMemoryError: ");

            abortDecoding(task, null, null);
        } catch (final Throwable th) {
            th.printStackTrace();
            abortDecoding(task, vuPage, null);
        } finally {
            // TempHolder.lock.unlock();
            if (holder != null) {
                holder.unlock();
            }

        }
    }

    /**
     * 检查并处理页面裁剪。
     * <p>
     * 如果启用了裁剪设置且页面未被裁剪过，渲染缩略图并计算裁剪边界。
     *
     * @param task   解码任务
     * @param vuPage 编解码器页面
     * @return 裁剪后的页面边界
     */
    RectF checkCropping(final DecodeTask task, final CodecPage vuPage) {
        // Checks if cropping setting is not set
        if (task.viewState.book == null || !task.viewState.book.cp) {
            return null;
        }
        // Checks if page has been cropped before
        if (task.node.croppedBounds != null) {
            // Page size is actuall now
            return null;
        }

        RectF croppedPageBounds = null;

        // Checks if page root node has been cropped before
        final PageTreeNode root = task.node.page.nodes.root;
        if (root.croppedBounds == null) {
            final Rect rootRect = new Rect(0, 0, Math.min(PageCropper.MAX_WIDTH, vuPage.getWidth()), Math.min(PageCropper.MAX_HEIGHT, vuPage.getHeight()));
            // final Rect rootRect = new Rect(0, 0, rootRect.width(), rootRect.height());

            // TempHolder.lock.lock();
            LOG.d("DJVU1-1", rootRect.width(), rootRect.height());
            final BitmapRef rootBitmap = vuPage.renderBitmapSimple(rootRect.width(), rootRect.height(), new RectF(0, 0, 1f, 1f));
            // TempHolder.lock.unlock();

            LOG.d("DJVU1-1a", vuPage.getWidth(), vuPage.getHeight());
            LOG.d("DJVU1-1b", rootBitmap.getBitmap().getWidth(), rootBitmap.getBitmap().getHeight());

            root.croppedBounds = PageCropper.getCropBounds(rootBitmap.getBitmap(), rootRect, new RectF(0, 0, 1f, 1f));
            LOG.d("DJVU1-2", root.croppedBounds.width(), root.croppedBounds.height());

            BitmapManager.release(rootBitmap);

            final ViewState viewState = task.viewState;
            final float pageWidth = vuPage.getWidth() * root.croppedBounds.width();
            final float pageHeight = vuPage.getHeight() * root.croppedBounds.height();

            LOG.d("DJVU1-3", pageWidth, pageHeight);

            final PageIndex currentPage = viewState.book.getCurrentPage(codecDocument.getPageCount());
            final float offsetX = viewState.book.x;
            final float offsetY = viewState.book.y;

            root.page.setAspectRatio(pageWidth, pageHeight);
            viewState.ctrl.invalidatePageSizes(InvalidateSizeReason.PAGE_LOADED, task.node.page);

            croppedPageBounds = root.page.getBounds(task.viewState.zoom);


        }

        if (task.node != root) {
            task.node.croppedBounds = PageTreeNode.evaluateCroppedPageSliceBounds(task.node.pageSliceBounds, task.node.parent);
        }

        return croppedPageBounds;
    }

    /**
     * 获取缩放后的页面尺寸。
     *
     * @param node              页面树节点
     * @param zoom              缩放比例
     * @param croppedPageBounds 裁剪后的页面边界
     * @param vuPage            编解码器页面
     * @return 缩放后的矩形尺寸
     */
    Rect getScaledSize(final PageTreeNode node, final float zoom, final RectF croppedPageBounds, final CodecPage vuPage) {
        final RectF pageBounds = MathUtils.zoom(croppedPageBounds != null ? croppedPageBounds : node.page.bounds, zoom);
        final RectF r = Page.getTargetRect(node.page.type, pageBounds, node.pageSliceBounds);
        return new Rect(0, 0, (int) r.width(), (int) r.height());
    }

    /**
     * 完成解码。
     *
     * @param currentDecodeTask 当前解码任务
     * @param page              编解码器页面
     * @param bitmap            位图引用
     * @param bitmapBounds      位图边界
     * @param croppedPageBounds 裁剪后的页面边界
     */
    void finishDecoding(final DecodeTask currentDecodeTask, final CodecPage page, final BitmapRef bitmap, final Rect bitmapBounds, final RectF croppedPageBounds) {
        stopDecoding(currentDecodeTask.node, "complete");
        updateImage(currentDecodeTask, page, bitmap, bitmapBounds, croppedPageBounds);
    }

    /**
     * 中止解码。
     *
     * @param currentDecodeTask 当前解码任务
     * @param page              编解码器页面
     * @param bitmap            位图引用
     */
    void abortDecoding(final DecodeTask currentDecodeTask, final CodecPage page, final BitmapRef bitmap) {
        stopDecoding(currentDecodeTask.node, "failed");
        updateImage(currentDecodeTask, page, bitmap, null, null);
    }

    /**
     * 获取页面。
     *
     * @param pageIndex 页面索引
     * @return 编解码器页面
     */
    CodecPage getPage(final int pageIndex) {
        return getPageHolder(-2, pageIndex).getPage(-2);
    }

    /**
     * 获取页面Holder，自动清理无效页面并创建新的Holder。
     *
     * @param taskId   任务ID
     * @param pageIndex 页面索引
     * @return 页面Holder
     */
    private synchronized CodecPageHolder getPageHolder(final long taskId, final int pageIndex) {
        for (final Iterator<Map.Entry<Integer, CodecPageHolder>> i = getPages().entrySet().iterator(); i.hasNext(); ) {
            final Map.Entry<Integer, CodecPageHolder> entry = i.next();
            final int index = entry.getKey();
            final CodecPageHolder ref = entry.getValue();
            if (ref.isInvalid(-1)) {
                i.remove();
            }
        }

        CodecPageHolder holder = getPages().get(pageIndex);
        if (holder == null) {
            holder = new CodecPageHolder(codecDocument, pageIndex);
            getPages().put(pageIndex, holder);
        }

        // Preventing problem inside the MuPDF
        if (!codecContext.isParallelPageAccessAvailable()) {
            // holder.getPage(taskId);
            // TODO TEST!!!
        }
        return holder;
    }

    /**
     * 更新图像。
     *
     * @param currentDecodeTask 当前解码任务
     * @param page              编解码器页面
     * @param bitmap            位图引用
     * @param bitmapBounds      位图边界
     * @param croppedPageBounds 裁剪后的页面边界
     */
    void updateImage(final DecodeTask currentDecodeTask, final CodecPage page, final BitmapRef bitmap, final Rect bitmapBounds, final RectF croppedPageBounds) {
        currentDecodeTask.node.decodeComplete(page, bitmap, bitmapBounds, croppedPageBounds);
    }

    @Override
    public int getPageCount() {
        return codecDocument != null ? codecDocument.getPageCount(view.getWidth(), view.getHeight(), BookCSS.get().fontSizeSp) : 0;
    }

    @Override
    public void getOutline(final ResultResponse<List<OutlineLink>> response) {

//        if (true) {
//            AppsConfig.executorService.execute(new Runnable() {
//                @Override
//                public void run() {
//                    if (codecDocument == null) {
//                        response.onResultRecive(null);
//                        return;
//                    }
//                    response.onResultRecive(codecDocument.getOutline());
//                }
//            });
//
//            return;//STOP
//        }
        executor.addAny(new Task(0) {

            @Override
            public void run() {
                if (codecDocument == null) {
                    response.onResultRecive(null);
                    return;
                }
                response.onResultRecive(codecDocument.getOutline());
            }
        });
    }

    @Override
    public void recycle() {
        // TempHolder.codecDocument = null;
        // TempHolder.path = null;
        if (isRecycled.compareAndSet(false, true)) {
            executor.recycle();
        }
    }

    protected int getCacheSize() {
        final ViewState vs = viewState.get();
        int minSize = 1;
        if (vs != null) {
            minSize = vs.pages.lastVisible - vs.pages.firstVisible + 1;
        }
        int pagesInMemory = CoreSettings.getInstance().pagesInMemory;
        return pagesInMemory == 0 ? 1 : Math.max(minSize, pagesInMemory);
    }

    @Override
    public boolean isPageSizeCacheable() {
        return codecContext.isPageSizeCacheable();
    }

    @Override
    public Map<Integer, CodecPageHolder> getPages() {
        return pages;
    }

    @Override
    public void processTextForPages(Page[] pages) {
        for (Page page : pages) {
            if (isRecycled.get()) {
                return;
            }
            if (page.texts == null) {
                CodecPage page2 = codecDocument.getPage(page.index.docIndex);
                page.texts = page2.getText();
                page2.recycle();
            }
        }
    }

    @Override
    public List<PageLink> getLinksForPage(int page) {
        if (codecDocument == null || codecDocument.getPage(page) == null) {
            return null;
        }
        return codecDocument.getPage(page).getPageLinks();
    }

    @Override
    public TextWord[][] getTextForPage(int page) {
        if (codecDocument == null) {
            return null;
        }
        final CodecPage page1 = codecDocument.getPage(page);
        if (page1 == null) {
            return null;
        }
        return page1.getText();
    }

    @Override
    public String getPageHTML(int page) {
        if (codecDocument == null) {
            return null;
        }
        final CodecPage page1 = codecDocument.getPage(page);
        if (page1 == null) {
            return null;
        }
        return page1.getPageHTML();
    }

    @Override
    public String getFooterNote(String input, String chapter) {
        if (codecDocument == null || codecDocument.getFootNotes() == null) {
            return "";
        }
        return TxtUtils.getFooterNote(input, chapter, codecDocument.getFootNotes());
    }

    @Override
    public List<String> getAttachemnts() {
        return codecDocument.getMediaAttachments();
    }

    @Override
    public CodecDocument getCodecDocument() {
        return codecDocument;
    }

    @Override
    public void shutdown() {
        try {
            executor.run.set(false);
            synchronized (executor.run) {
                executor.run.notifyAll();
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    @Override
    public void restore() {
        try {
            if (executor.run.get()) {
                return;
            }
            executor = null;
            executor = new ExecutorRunnable();
            synchronized (executor.run) {
                executor.run.notifyAll();
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /**
     * 任务执行器。
     * <p>
     * 在后台线程中执行解码任务，支持任务优先级排序和任务取消。
     */
    class ExecutorRunnable implements Runnable, org.ebookdroid.core.ExecutorRunnable {

        /** 是否运行中 */
        final AtomicBoolean run = new AtomicBoolean(true);

        /**
         * 构造函数，启动执行器线程。
         */
        ExecutorRunnable() {
            //Thread t = new Thread(this, "@T Decoding");
            //t.setPriority(CoreSettings.getInstance().decodingThreadPriority);
            //t.start();
            AppsConfig.executorService.execute(this);
        }

        @Override
        public void run() {
            try {
                while (run.get()) {
                    final Runnable r = nextTask();
                    if (r != null) {
                        //BitmapManager.release();
                        r.run();
                    }
                }
                LOG.d("Executor stopped");
            } catch (final Throwable th) {
                th.printStackTrace();
            } finally {
                //BitmapManager.release();
            }
        }

        /**
         * 获取下一个任务。
         * <p>
         * 根据优先级排序任务列表，返回优先级最高的任务。如果没有任务则等待1秒。
         *
         * @return 下一个任务，无任务时返回null
         */
        Runnable nextTask() {
            // TempHolder.lock.lock();
            try {

                if (tasks != null && !tasks.isEmpty()) {
                    final TaskComparator comp = new TaskComparator(viewState.get());
                    Task candidate = null;
                    int cindex = 0;

                    int index = 0;
                    while (index < tasks.size() && candidate == null) {
                        candidate = tasks.get(index);
                        cindex = index;
                        index++;
                    }
                    if (candidate == null) {
                        tasks.clear();
                    } else {
                        while (index < tasks.size()) {
                            final Task next = tasks.get(index);
                            if (next != null && comp.compare(next, candidate) < 0) {
                                candidate = next;
                                cindex = index;
                            }
                            index++;
                        }
                        tasks.set(cindex, null);
                    }
                    return candidate;
                }
            } catch (Exception e) {
                LOG.e(e);
            } finally {
                // TempHolder.lock.unlock();
            }
            synchronized (run) {
                try {
                    run.wait(1000);
                } catch (final InterruptedException ex) {
                    Thread.interrupted();
                }
            }
            return null;
        }

        /**
         * 添加任意任务。
         *
         * @param task 任务
         */
        public void addAny(final Task task) {

            // TempHolder.lock.lock();
            try {
                boolean added = false;
                for (int index = 0; index < tasks.size(); index++) {
                    if (null == tasks.get(index)) {
                        tasks.set(index, task);
                        added = true;
                        break;
                    }
                }
                if (!added) {
                    tasks.add(task);
                }

                synchronized (run) {
                    run.notifyAll();
                }
            } catch (Exception e) {
                LOG.e(e);
            } finally {
                // TempHolder.lock.unlock();
            }
        }

        /**
         * 添加解码任务。
         * <p>
         * 如果相同页面已有解码任务且未取消，则跳过。否则取消旧任务并添加新任务。
         *
         * @param task 解码任务
         */
        public void add(final DecodeTask task) {

            // TempHolder.lock.lock();
            try {
                final DecodeTask running = decodingTasks.get(task.node);
                if (running != null && running.equals(task) && !isTaskDead(running)) {
                    return;
                }

                decodingTasks.put(task.node, task);

                boolean added = false;
                for (int index = 0; index < tasks.size(); index++) {
                    if (null == tasks.get(index)) {
                        tasks.set(index, task);
                        added = true;
                        break;
                    }
                }
                if (!added) {
                    tasks.add(task);
                }

                synchronized (run) {
                    run.notifyAll();
                }

                if (running != null) {
                    stopDecoding(running, null, "canceled by new one");
                }
            } catch (Exception e) {
                LOG.e(e);
            } finally {
                // TempHolder.lock.unlock();
            }
        }

        /**
         * 停止解码任务。
         *
         * @param task   任务（可为null，此时通过node查找）
         * @param node   页面树节点
         * @param reason 停止原因
         */
        public void stopDecoding(final DecodeTask task, final PageTreeNode node, final String reason) {
            // TempHolder.lock.lock();
            try {
                final DecodeTask removed = task == null ? decodingTasks.remove(node) : task;

                if (removed != null) {
                    removed.cancelled.set(true);
                    for (int i = 0; i < tasks.size(); i++) {
                        if (removed == tasks.get(i)) {
                            tasks.set(i, null);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                LOG.e(e);
            } finally {
                // TempHolder.lock.unlock();
            }
        }

        /**
         * 检查任务是否已取消。
         *
         * @param task 任务
         * @return 是否已取消
         */
        public boolean isTaskDead(final DecodeTask task) {
            return task.cancelled.get();
        }

        /**
         * 回收资源，停止所有解码任务。
         */
        public void recycle() {
            // TempHolder.lock.lock();
            try {
                for (final DecodeTask task : decodingTasks.values()) {
                    stopDecoding(task, null, "recycling");
                }

                // tasks.add(new ShutdownTask());
                shutdownInner();

                synchronized (run) {
                    run.notifyAll();
                }
            } catch (Exception e) {
                LOG.e(e);
            } finally {
                // TempHolder.lock.unlock();
            }
        }


        /**
         * 关闭执行器。
         */
        public void shutdown() {
            Safe.run(new Runnable() {

                @Override
                public void run() {
                    shutdownInner();
                }
            });
        }

        /**
         * 内部关闭方法。
         * <p>
         * 停止执行、回收页面缓存、回收文档和编解码器上下文。
         */
        private void shutdownInner() {

            LOG.d("Begin shutdown 1");
            run.set(false);

            for (final CodecPageHolder ref : getPages().values()) {
                ref.recycle(-3, true);
            }

            LOG.d("Begin shutdown 2");

            getPages().clear();

            LOG.d("Begin shutdown 3");
            if (getCodecDocument() != null) {
                getCodecDocument().recycle();
                codecDocument = null;
            }
            LOG.d("Begin shutdown 4");
            codecContext.recycle();
            LOG.d("Begin shutdown 5");

            LOG.d("Begin shutdown 6");

            ImageExtractor.clearCodeDocument();

        }

    }

    /**
     * 任务比较器。
     * <p>
     * 首先按优先级排序，然后对解码任务按页面树节点排序，最后按任务ID排序。
     */
    class TaskComparator implements Comparator<Task> {

        /** 页面树节点比较器 */
        final PageTreeNodeComparator cmp;

        /**
         * 构造函数。
         *
         * @param viewState 视图状态
         */
        public TaskComparator(final ViewState viewState) {
            cmp = viewState != null ? new PageTreeNodeComparator(viewState) : null;
        }

        @Override
        public int compare(final Task r1, final Task r2) {
            if (r1.priority < r2.priority) {
                return -1;
            }
            if (r2.priority < r1.priority) {
                return +1;
            }

            if (r1 instanceof DecodeTask && r2 instanceof DecodeTask) {
                final DecodeTask t1 = (DecodeTask) r1;
                final DecodeTask t2 = (DecodeTask) r2;

                if (cmp != null) {
                    return cmp.compare(t1.node, t2.node);
                }
                return 0;
            }

            return Long.compare(r1.id, r2.id);
        }

    }

    /**
     * 抽象任务类。
     * <p>
     * 所有任务的基类，包含任务ID、取消状态和优先级。
     */
    abstract class Task implements Runnable {

        /** 任务ID */
        final long id = TASK_ID_SEQ.incrementAndGet();
        /** 是否已取消 */
        final AtomicBoolean cancelled = new AtomicBoolean();
        /** 优先级 */
        final int priority;

        /**
         * 构造函数。
         *
         * @param priority 优先级
         */
        Task(final int priority) {
            this.priority = priority;
        }

    }

    /**
     * 关闭任务。
     * <p>
     * 用于关闭解码服务。
     */
    class ShutdownTask extends Task {

        /**
         * 构造函数。
         */
        public ShutdownTask() {
            super(0);
        }

        @Override
        public void run() {
            executor.shutdown();
        }
    }

    /**
     * 添加注释任务。
     * <p>
     * 在指定页面添加注释。
     */
    class AddAnnotationTask extends Task {

        /** 点坐标映射 */
        private Map<Integer, List<PointF>> points;
        /** 颜色 */
        private int color;
        /** 结果回调 */
        private ResultResponse<Pair<Integer, List<Annotation>>> onResult;
        /** 线宽 */
        private float width;
        /** 透明度 */
        private float alpha;

        /**
         * 构造函数。
         *
         * @param points 点坐标映射
         * @param color 颜色
         * @param width 线宽
         * @param alpha 透明度
         * @param result 结果回调
         */
        public AddAnnotationTask(Map<Integer, List<PointF>> points, int color, float width, float alpha, ResultResponse<Pair<Integer, List<Annotation>>> result) {
            super(1);
            this.points = points;
            this.width = width;
            this.alpha = alpha;
            this.onResult = result;
            this.color = color;

        }

        @Override
        public void run() {
            Set<Integer> keySet = points.keySet();
            for (int docIndex : keySet) {
                List<PointF> good = points.get(docIndex);
                if (good == null) {
                    continue;
                }
                PointF[][] path = new PointF[1][good.size()];
                path[0] = good.toArray(new PointF[good.size()]);

                updateAnnotation(docIndex, Colors.toMupdfColor(color), path, width, alpha);
                pages.clear();

                onResult.onResultRecive(new Pair<Integer, List<Annotation>>(docIndex, getPage(docIndex).getAnnotations()));
            }
        }
    }

    /**
     * 解码任务。
     * <p>
     * 用于解码指定页面，包含页面树节点、视图状态和页面编号。
     */
    class DecodeTask extends Task {

        /** 任务ID */
        final long id = TASK_ID_SEQ.incrementAndGet();
        /** 是否已取消 */
        final AtomicBoolean cancelled = new AtomicBoolean();

        /** 页面树节点 */
        final PageTreeNode node;
        /** 视图状态 */
        final ViewState viewState;
        /** 页面编号 */
        final int pageNumber;

        /**
         * 构造函数。
         *
         * @param viewState 视图状态
         * @param node 页面树节点
         */
        DecodeTask(final ViewState viewState, final PageTreeNode node) {
            super(2);
            this.pageNumber = node.page.index.docIndex;
            this.viewState = viewState;
            this.node = node;
        }

        @Override
        public void run() {
            performDecode(this);
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof DecodeTask) {
                final DecodeTask that = (DecodeTask) obj;
                return this.pageNumber == that.pageNumber && this.viewState.viewRect.width() == that.viewState.viewRect.width() && this.viewState.zoom == that.viewState.zoom;
            }
            return false;
        }

        @Override
        public String toString() {
            final StringBuilder buf = new StringBuilder("DecodeTask");
            buf.append("[");

            buf.append("id").append("=").append(id);
            buf.append(", ");
            buf.append("target").append("=").append(node);
            buf.append(", ");
            buf.append("width").append("=").append((int) viewState.viewRect.width());
            buf.append(", ");
            buf.append("z").append("=").append(viewState.zoom);

            buf.append("]");
            return buf.toString();
        }

    }


}
