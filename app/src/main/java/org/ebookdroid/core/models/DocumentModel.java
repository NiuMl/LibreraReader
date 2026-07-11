package org.ebookdroid.core.models;

import com.foobnix.android.utils.LOG;
import com.foobnix.dao2.FileMeta;
import com.foobnix.ext.CacheZipUtils;
import com.foobnix.model.AppBook;
import com.foobnix.model.AppSP;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.model.BookCSS;
import com.foobnix.sys.TempHolder;
import com.foobnix.ui2.AppDB;

import org.ebookdroid.BookType;
import org.ebookdroid.common.bitmaps.BitmapManager;
import org.ebookdroid.common.bitmaps.Bitmaps;
import org.ebookdroid.common.cache.PageCacheFile;
import org.ebookdroid.common.settings.SettingsManager;
import org.ebookdroid.common.settings.types.PageType;
import org.ebookdroid.core.DecodeService;
import org.ebookdroid.core.DecodeServiceBase;
import org.ebookdroid.core.DecodeServiceStub;
import org.ebookdroid.core.Page;
import org.ebookdroid.core.PageIndex;
import org.ebookdroid.core.codec.CodecContext;
import org.ebookdroid.core.codec.CodecPageInfo;
import org.ebookdroid.core.events.CurrentPageListener;
import org.ebookdroid.ui.viewer.IActivityController;
import org.ebookdroid.ui.viewer.IView;
import org.emdev.ui.progress.IProgressIndicator;
import org.emdev.utils.LengthUtils;
import org.emdev.utils.listeners.ListenerProxy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * 文档模型类。
 * <p>
 * 管理文档的页面数据、解码服务和页面索引，提供页面获取、回收、初始化等功能。
 */
public class DocumentModel extends ListenerProxy {

    /** 解码服务 */
    public final DecodeService decodeService;

    /** 当前页面索引 */
    protected PageIndex currentIndex = PageIndex.FIRST;

    /** 空页面数组 */
    private static final Page[] EMPTY_PAGES = {};

    /** 编解码器上下文 */
    private final CodecContext context;

    /** 页面数组 */
    private Page[] pages = EMPTY_PAGES;

    /**
     * 构造函数。
     *
     * @param activityType 书籍类型
     * @param view         视图
     */
    public DocumentModel(final BookType activityType, IView view) {
        super(CurrentPageListener.class);
        LOG.d("Document activityType Type", activityType);
        if (activityType != null) {
            try {
                context = BookType.getCodecContextByType(activityType);
                LOG.d("Document context Type", context);
                decodeService = new DecodeServiceBase(context, view);
            } catch (final Throwable th) {
                throw new RuntimeException(th);
            }
        } else {
            context = null;
            decodeService = new DecodeServiceStub();
        }
    }

    /** 页数缓存 */
    private static final java.util.Map<String, Integer> pageCountCache = new java.util.HashMap<>();
    
    /**
     * 打开文档。
     *
     * @param fileName 文件路径
     * @param password 密码
     */
    public void open(String fileName, String password) {
        if (!ExtUtils.isValidFile(fileName)) {
            throw new IllegalArgumentException("Invalid file:" + fileName);
        }
        decodeService.open(fileName, password);
    }
    
    /**
     * 获取缓存的页数。
     * <p>
     * 首先从内存缓存中查找，然后从数据库中查找。
     *
     * @param path      文件路径
     * @param w         宽度
     * @param h         高度
     * @param fontSize  字体大小
     * @return 页数，0表示没有缓存
     */
    private int getCachedPageCount(String path, int w, int h, int fontSize) {
        String key = path + "_" + w + "_" + h + "_" + fontSize;
        Integer cached = pageCountCache.get(key);
        if (cached != null) {
            LOG.d("DocumentModel", "Using memory cached page count for " + path + ": " + cached);
            return cached;
        }
        
        FileMeta meta = AppDB.get().load(path);
        if (meta != null && meta.getPages() > 0) {
            LOG.d("DocumentModel", "Using DB cached page count for " + path + ": " + meta.getPages());
            pageCountCache.put(key, meta.getPages());
            return meta.getPages();
        }
        
        LOG.d("DocumentModel", "No cached page count for " + path);
        return 0;
    }
    
    /**
     * 缓存页数。
     * <p>
     * 将页数缓存到内存和数据库中。
     *
     * @param path      文件路径
     * @param w         宽度
     * @param h         高度
     * @param fontSize  字体大小
     * @param count     页数
     */
    private void cachePageCount(String path, int w, int h, int fontSize, int count) {
        if (count > 0) {
            String key = path + "_" + w + "_" + h + "_" + fontSize;
            pageCountCache.put(key, count);
            LOG.d("DocumentModel", "Cached page count for " + path + ": " + count);
            
            try {
                FileMeta meta = AppDB.get().load(path);
                if (meta != null) {
                    meta.setPages(count);
                    AppDB.get().save(meta);
                }
            } catch (Exception e) {
                LOG.e(e);
            }
        }
    }

    /**
     * 获取所有页面。
     *
     * @return 页面数组
     */
    public Page[] getPages() {
        return pages;
    }

    /**
     * 获取从指定索引开始的页面。
     *
     * @param start 起始索引
     * @return 页面迭代器
     */
    public Iterable<Page> getPages(final int start) {
        return new PageIterator(start, pages.length);
    }

    /**
     * 获取指定范围的页面。
     *
     * @param start 起始索引
     * @param end   结束索引
     * @return 页面迭代器
     */
    public Iterable<Page> getPages(final int start, final int end) {
        return new PageIterator(start, Math.min(end, pages.length));
    }

    /**
     * 获取页数。
     *
     * @return 页数
     */
    public int getPageCount() {
        return LengthUtils.length(pages);
    }

    /**
     * 回收文档资源。
     *
     * @return 是否回收成功
     */
    public boolean recycle() {
        decodeService.recycle();
        recyclePages();
        return pages == EMPTY_PAGES;
    }

    /**
     * 回收所有页面资源。
     */
    public void recyclePages() {
        if (LengthUtils.isNotEmpty(pages)) {
            final List<Bitmaps> bitmapsToRecycle = new ArrayList<Bitmaps>();
            for (final Page page : pages) {
                page.recycle(bitmapsToRecycle);
            }
            BitmapManager.release(bitmapsToRecycle);
        }
        pages = EMPTY_PAGES;
    }

    /**
     * 根据视图索引获取页面对象。
     *
     * @param viewIndex 视图索引
     * @return 页面对象
     */
    public Page getPageObject(final int viewIndex) {
        return pages != null && 0 <= viewIndex && viewIndex < pages.length ? pages[viewIndex] : null;
    }

    /**
     * 根据文档索引获取页面对象。
     *
     * @param docIndex 文档索引
     * @return 页面对象
     */
    public Page getPageByDocIndex(final int docIndex) {
        for (Page page : pages) {
            if (page.index.docIndex == docIndex) {
                return page;
            }
        }
        return null;
    }

    /**
     * 获取当前页面对象。
     *
     * @return 当前页面对象
     */
    public Page getCurrentPageObject() {
        return getPageObject(this.currentIndex.viewIndex);
    }

    /**
     * 获取最后一页。
     *
     * @return 最后一页对象
     */
    public Page getLastPageObject() {
        return getPageObject(pages.length - 1);
    }

    /**
     * 设置当前页面索引。
     *
     * @param newIndex 新的页面索引
     * @param pages    总页数
     */
    public void setCurrentPageIndex(final PageIndex newIndex, int pages) {
        if (!Objects.equals(currentIndex, newIndex)) {

            this.currentIndex = newIndex;

            this.<CurrentPageListener>getListener()
                .currentPageChanged(newIndex.docIndex, pages);
        }
    }

    /**
     * 获取当前页面索引。
     *
     * @return 当前页面索引
     */
    public PageIndex getCurrentIndex() {
        return this.currentIndex;
    }

    /**
     * 获取当前视图页面索引。
     *
     * @return 当前视图页面索引
     */
    public int getCurrentViewPageIndex() {
        return this.currentIndex.viewIndex;
    }

    /**
     * 获取当前文档页面索引。
     *
     * @return 当前文档页面索引
     */
    public int getCurrentDocPageIndex() {
        // return this.currentIndex.docIndex;
        return this.currentIndex.viewIndex;
    }

    /**
     * 获取已阅读百分比。
     *
     * @return 已阅读百分比
     */
    public double getPercentRead() {
        return (currentIndex.viewIndex + 0.0001) / getPageCount();
    }

    /**
     * 根据第一个可见页面设置当前页面。
     *
     * @param firstVisiblePage 第一个可见页面索引
     * @param pages            总页数
     */
    public void setCurrentPageByFirstVisible(final int firstVisiblePage, int pages) {
        final Page page = getPageObject(firstVisiblePage);
        if (page != null) {
            setCurrentPageIndex(page.index, pages);
        }
    }

    /**
     * 初始化页面。
     * <p>
     * 回收旧页面，获取页面信息，创建新页面对象。支持切页模式。
     *
     * @param base  活动控制器
     * @param task  进度指示器
     */
    public void initPages(final IActivityController base, final IProgressIndicator task) {
        recyclePages();

        final AppBook bs = SettingsManager.getBookSettings();

        if (base == null || bs == null || context == null || decodeService == null) {
            return;
        }

        final IView view = base.getView();

        final CodecPageInfo defCpi = new CodecPageInfo();
        defCpi.width = (view.getWidth());
        defCpi.height = (view.getHeight());

        LOG.d("initPages", defCpi.width, defCpi.height);

        int viewIndex = 0;

        try {
            final ArrayList<Page> list = new ArrayList<Page>();
            final CodecPageInfo[] infos = retrievePagesInfo(base, bs, task);

            for (int docIndex = 0; docIndex < infos.length; docIndex++) {
                if (TempHolder.get().loadingCancelled.get()) {
                    return;
                }
                if (!AppSP.get().isCut) {
                    CodecPageInfo cpi = infos[docIndex] != null ? infos[docIndex] : defCpi;

                    final Page page = new Page(base, new PageIndex(docIndex, viewIndex++), PageType.FULL_PAGE, cpi);
                    list.add(page);

                } else {
                    final Page page1 =
                            new Page(base, new PageIndex(docIndex, viewIndex++), PageType.LEFT_PAGE, infos[docIndex]);
                    final Page page2 =
                            new Page(base, new PageIndex(docIndex, viewIndex++), PageType.RIGHT_PAGE, infos[docIndex]);

                    if (AppState.get().isCutRTL) {
                        list.add(page2);
                        list.add(page1);
                    } else {
                        list.add(page1);
                        list.add(page2);
                    }
                }
            }
            pages = list.toArray(new Page[list.size()]);
        } finally {
        }
    }

    /**
     * 获取页面信息数组。
     * <p>
     * 从缓存或解码服务获取页面信息，支持统一页面信息（如EPUB格式）。
     *
     * @param base 活动控制器
     * @param bs   书籍设置
     * @param task 进度指示器
     * @return 页面信息数组
     */
    private CodecPageInfo[] retrievePagesInfo(final IActivityController base, final AppBook bs,
                                              final IProgressIndicator task) {
        int w = base.getView().getWidth();
        int h = base.getView().getHeight();
        
        if (w <= 0 || h <= 0) {
            android.util.DisplayMetrics metrics = base.getContext().getResources().getDisplayMetrics();
            w = metrics.widthPixels;
            h = metrics.heightPixels;
            LOG.d("DocumentModel", "Using DisplayMetrics for dimensions: " + w + "x" + h);
        }
        
        int fontSize = BookCSS.get().fontSizeSp;
        
        LOG.d("DocumentModel", "retrievePagesInfo - path: " + bs.path + ", w: " + w + ", h: " + h + ", fontSize: " + fontSize);
        
        int pagesCount = getCachedPageCount(bs.path, w, h, fontSize);
        
        if (pagesCount <= 0) {
            LOG.d("DocumentModel", "No cache hit, calling getPageCount()");
            pagesCount = base.getDecodeService()
                             .getPageCount();
            cachePageCount(bs.path, w, h, fontSize, pagesCount);
        }
        
        LOG.d("DocumentModel", "pagesCount: " + pagesCount);
        
        if (pagesCount <= 0) {
            CacheZipUtils.emptyAllCacheDirs();
            return null;

        }

        final PageCacheFile pagesFile = PageCacheFile.getPageFile(bs.path, pagesCount);

        try {
            FileMeta meta = AppDB.get()
                                 .load(bs.path);
            if (meta != null) {
                meta.setPages(pagesCount);
                AppDB.get()
                     .save(meta);
                LOG.d("update openDocument.getPageCount()", bs.path, pagesCount);
            }
        } catch (Exception e) {
            LOG.e(e);
        }

        if (pagesFile.exists()) {
            final CodecPageInfo[] infos = pagesFile.load();
            if (infos != null && infos.length == pagesCount) {
                return infos;
            }
        }

        final CodecPageInfo[] infos = new CodecPageInfo[pagesCount];
        final CodecPageInfo unified = decodeService.getUnifiedPageInfo();

        for (int i = 0; i < infos.length; i++) {
            if (TempHolder.get().loadingCancelled.get()) {
                return null;
            }
            infos[i] = unified != null ? unified : decodeService.getPageInfo(i);
        }

        // if (decodeService.isPageSizeCacheable()) {
        pagesFile.save(infos);
        //}
        return infos;
    }

    /**
     * 页面迭代器类。
     * <p>
     * 实现Iterable和Iterator接口，用于遍历页面数组。
     */
    private final class PageIterator implements Iterable<Page>, Iterator<Page> {

        /** 结束索引 */
        private final int end;
        /** 当前索引 */
        private int index;

        /**
         * 构造函数。
         *
         * @param start 起始索引
         * @param end   结束索引
         */
        private PageIterator(final int start, final int end) {
            this.index = start;
            this.end = end;
        }

        @Override public boolean hasNext() {
            return 0 <= index && index < end;
        }

        @Override public Page next() {
            return hasNext() ? pages[index++] : null;
        }

        @Override public void remove() {
        }

        @Override public Iterator<Page> iterator() {
            return this;
        }
    }
}
