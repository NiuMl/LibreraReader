package org.ebookdroid.droids.mupdf.codec;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;

import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.AppsConfig;
import com.foobnix.pdf.info.Prefs;
import com.foobnix.pdf.info.model.AnnotationType;
import com.foobnix.pdf.info.model.BookCSS;
import com.foobnix.pdf.info.wrapper.MagicHelper;
import com.foobnix.sys.TempHolder;

import org.ebookdroid.common.bitmaps.BitmapManager;
import org.ebookdroid.common.bitmaps.BitmapRef;
import org.ebookdroid.core.codec.AbstractCodecPage;
import org.ebookdroid.core.codec.Annotation;
import org.ebookdroid.core.codec.PageLink;
import org.ebookdroid.core.codec.PageTextBox;
import org.emdev.utils.LengthUtils;
import org.emdev.utils.MatrixUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * MuPDF页面编解码器实现类。
 * <p>
 * 基于MuPDF库实现页面渲染、文本提取、注释管理等功能。
 */
public class MuPdfPage extends AbstractCodecPage {


    /** 页面边界 */
    final RectF pageBounds;
    /** 实际宽度 */
    final int actualWidth;
    /** 实际高度 */
    final int actualHeight;
    /** 文档句柄 */
    private final long docHandle;
    /** MuPDF文档对象 */
    MuPdfDocument muPdfDocument;
    /** 页面句柄 */
    private volatile long pageHandle;
    /** 页面编号 */
    private int pageNumber;

    /**
     * 构造函数。
     *
     * @param pageHandle    页面句柄
     * @param muPdfDocument MuPDF文档对象
     * @param pageNumber    页面编号
     */
    private MuPdfPage(final long pageHandle, final MuPdfDocument muPdfDocument, int pageNumber) {
        super(muPdfDocument.getPath());
        this.pageHandle = pageHandle;
        this.muPdfDocument = muPdfDocument;
        this.docHandle = muPdfDocument.getDocumentHandle();
        this.pageNumber = pageNumber;

        this.pageBounds = getBounds();
        this.actualWidth = (int) pageBounds.width();
        this.actualHeight = (int) pageBounds.height();
    }

    /**
     * 创建页面对象。
     * <p>
     * 打开页面并创建MuPdfPage实例。
     *
     * @param dochandle MuPDF文档对象
     * @param pageno    页面编号
     * @return MuPdfPage实例
     */
    static MuPdfPage createPage(final MuPdfDocument dochandle, final int pageno) {
        TempHolder.lock.lock();
        try {
            if(dochandle.isRecycled()){
                LOG.d("MUPDF! +create page isRecycled");
                return null;
            }
            LOG.d("MUPDF! +create page",  pageno,dochandle.getDocumentHandle());
            final long open = open(dochandle.getDocumentHandle(), pageno);
            return new MuPdfPage(open, dochandle, pageno);
        } finally {
            TempHolder.lock.unlock();
        }
    }

    /**
     * 获取页面边界的原生方法。
     *
     * @param dochandle 文档句柄
     * @param handle    页面句柄
     * @param bounds    边界数组
     */
    private static native void getBounds(long dochandle, long handle, float[] bounds);

    /**
     * 获取字符数的原生方法。
     *
     * @param dochandle 文档句柄
     * @param handle    页面句柄
     * @return 字符数
     */
    private static native int getCharCount(long dochandle, long handle);

    /**
     * 释放页面资源的原生方法。
     *
     * @param dochandle 文档句柄
     * @param handle    页面句柄
     */
    private static native void free(long dochandle, long handle);

    /**
     * 打开页面的原生方法。
     *
     * @param dochandle 文档句柄
     * @param pageno    页面编号
     * @return 页面句柄
     */
    private static native long open(long dochandle, int pageno);

    /**
     * 安全渲染页面。
     * <p>
     * 在渲染前检查文档是否有效。
     *
     * @param dochandle    文档对象
     * @param pagehandle   页面句柄
     * @param viewboxarray 视图框数组
     * @param matrixarray  矩阵数组
     * @param bufferarray  缓冲区数组
     * @param r            红色通道值
     * @param g            绿色通道值
     * @param b            蓝色通道值
     */
    private static void renderPageSafe(MuPdfDocument dochandle, long pagehandle, int[] viewboxarray, float[] matrixarray, int[] bufferarray, int r, int g, int b) {
        if (dochandle != null && dochandle.getDocumentHandle() != 0 && !dochandle.isRecycled()) {
            renderPage(dochandle.getDocumentHandle(), pagehandle, viewboxarray, matrixarray, bufferarray, r, g, b);

        }
    }

    /**
     * 渲染页面的原生方法。
     *
     * @param dochandle    文档句柄
     * @param pagehandle   页面句柄
     * @param viewboxarray 视图框数组
     * @param matrixarray  矩阵数组
     * @param bufferarray  缓冲区数组
     * @param r            红色通道值
     * @param g            绿色通道值
     * @param b            蓝色通道值
     */
    private static native void renderPage(long dochandle, long pagehandle, int[] viewboxarray, float[] matrixarray, int[] bufferarray, int r, int g, int b);

    /**
     * 获取文本字符数据（旧版本格式）。
     *
     * @param docHandle 文档句柄
     * @param pageHandle 页面句柄
     * @return 文本字符数据
     */
    private native static TextChar[][][][] text(long docHandle, long pageHandle);

    /**
     * 获取文本字符数据（新版本格式）。
     *
     * @param docHandle 文档句柄
     * @param pageHandle 页面句柄
     * @return 文本字符列表
     */
    private native static ArrayList<TextChar> text116(long docHandle, long pageHandle);

    /**
     * 获取页面句柄。
     *
     * @return 页面句柄
     */
    @Override
    public long getPageHandle() {
        return pageHandle;
    }

    /**
     * 获取页面宽度。
     *
     * @return 宽度
     */
    @Override
    public int getWidth() {
        return actualWidth;
    }

    /**
     * 获取页面高度。
     *
     * @return 高度
     */
    @Override
    public int getHeight() {
        return actualHeight;
    }

    /**
     * 渲染页面位图。
     *
     * @param width          宽度
     * @param height         高度
     * @param pageSliceBounds 页面切片边界
     * @param cache          是否缓存
     * @return 位图引用
     */
    @Override
    public BitmapRef renderBitmap(final int width, final int height, final RectF pageSliceBounds, boolean cache) {
        final float[] matrixArray = calculateFz(width, height, pageSliceBounds);
        return render(new Rect(0, 0, width, height), matrixArray, cache);
    }

    /**
     * 简化渲染页面位图。
     *
     * @param width          宽度
     * @param height         高度
     * @param pageSliceBounds 页面切片边界
     * @return 位图引用
     */
    @Override
    public BitmapRef renderBitmapSimple(final int width, final int height, final RectF pageSliceBounds) {
        final float[] matrixArray = calculateFz(width, height, pageSliceBounds);
        return renderSimple(new Rect(0, 0, width, height), matrixArray);
    }

    /**
     * 渲染缩略图。
     *
     * @param width 宽度
     * @return 缩略图位图
     */
    @Override
    public Bitmap renderThumbnail(final int width) {
        return renderThumbnail(width, getWidth(), getHeight());
    }

    /**
     * 渲染缩略图。
     *
     * @param width   宽度
     * @param originW 原始宽度
     * @param originH 原始高度
     * @return 缩略图位图
     */
    @Override
    public Bitmap renderThumbnail(final int width, final int originW, final int originH) {
        final RectF rectF = new RectF(0, 0, 1f, 1f);
        final float k = (float) originH / originW;
        LOG.d("TEST", "Render" + " w" + getWidth() + " H " + getHeight() + " " + k + " " + width * k);
        final BitmapRef renderBitmap = renderBitmap(width, (int) (width * k), rectF, false);
        return renderBitmap.getBitmap();
    }

    /**
     * 计算变换矩阵。
     * <p>
     * 将页面坐标转换为视图坐标，考虑缩放和平移。
     *
     * @param width          宽度
     * @param height         高度
     * @param pageSliceBounds 页面切片边界
     * @return 变换矩阵数组
     */
    private float[] calculateFz(final int width, final int height, final RectF pageSliceBounds) {
        final Matrix matrix = MatrixUtils.get();
        matrix.postScale(width / pageBounds.width(), height / pageBounds.height());
        matrix.postTranslate(-pageSliceBounds.left * width, -pageSliceBounds.top * height);
        matrix.postScale(1 / pageSliceBounds.width(), 1 / pageSliceBounds.height());

        final float[] matrixSource = new float[9];
        matrix.getValues(matrixSource);

        final float[] matrixArray = new float[6];

        matrixArray[0] = matrixSource[0];
        matrixArray[1] = matrixSource[3];
        matrixArray[2] = matrixSource[1];
        matrixArray[3] = matrixSource[4];
        matrixArray[4] = matrixSource[2];
        matrixArray[5] = matrixSource[5];

        return matrixArray;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            // recycle();
        } finally {
            super.finalize();
        }
    }

    /**
     * 回收页面资源。
     */
    @Override
    public void recycle() {
        try {
            TempHolder.lock.lock();
            if (pageHandle != 0 && muPdfDocument != null && muPdfDocument.getDocumentHandle() != 0 && !muPdfDocument.isRecycled()) {
                LOG.d("MUPDF! -recycle page", docHandle, pageNumber);
                free(docHandle, pageHandle);
            }
        } catch (final Exception e) {
            LOG.e(e);
        } finally {
            pageHandle = 0;
            TempHolder.lock.unlock();
        }
    }

    /**
     * 判断页面是否已回收。
     *
     * @return 是否已回收
     */
    @Override
    public boolean isRecycled() {
        return pageHandle == 0 || docHandle == 0 || muPdfDocument.isRecycled();
    }

    /**
     * 获取页面边界。
     *
     * @return 边界矩形
     */
    private RectF getBounds() {
        final float[] box = new float[4];
        TempHolder.lock.lock();
        try {
            getBounds(docHandle, pageHandle, box);
        } finally {
            TempHolder.lock.unlock();
        }

        return new RectF(box[0], box[1], box[2], box[3]);
    }

    /**
     * 简化渲染页面。
     * <p>
     * 不进行颜色处理，直接渲染页面。
     *
     * @param viewbox 视图框
     * @param ctm     变换矩阵
     * @return 位图引用
     */
    public BitmapRef renderSimple(final Rect viewbox, final float[] ctm) {
        TempHolder.lock.lock();
        try {


            if (isRecycled()) {
                throw new RuntimeException("The page has been recycled before: " + this);
            }
            final int[] mRect = new int[4];
            mRect[0] = viewbox.left;
            mRect[1] = viewbox.top;
            mRect[2] = viewbox.right;
            mRect[3] = viewbox.bottom;

            final int width = viewbox.width();
            final int height = viewbox.height();

            final int[] bufferarray = new int[width * height];

            renderPageSafe(muPdfDocument, pageHandle, mRect, ctm, bufferarray, -1, -1, -1);

            final BitmapRef b = BitmapManager.getBitmap("PDF page", width, height, AppsConfig.CURRENT_BITMAP_ARGB);
            b.getBitmap().setPixels(bufferarray, 0, width, 0, 0, width, height);
            return b;
        } finally {
            TempHolder.lock.unlock();
        }
    }

    /**
     * 渲染页面。
     * <p>
     * 支持文本格式、颜色处理、亮度对比度调整和镜像翻转。
     *
     * @param viewbox 视图框
     * @param ctm     变换矩阵
     * @param cache   是否缓存
     * @return 位图引用
     */
    public BitmapRef render(final Rect viewbox, final float[] ctm, boolean cache) {
        TempHolder.lock.lock();
        try {


            if (isRecycled()) {
                throw new RuntimeException("The page has been recycled before: " + this);
            }
            final int[] mRect = new int[4];
            mRect[0] = viewbox.left;
            mRect[1] = viewbox.top;
            mRect[2] = viewbox.right;
            mRect[3] = viewbox.bottom;

            final int width = viewbox.width();
            final int height = viewbox.height();

            final int[] bufferarray = new int[width * height];

            if (BookCSS.get().isTextFormat()) {
                int color = MagicHelper.getBgColor();
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                renderPageSafe(muPdfDocument, pageHandle, mRect, ctm, bufferarray, r, g, b);

                if (AppState.get().isReplaceWhite && MagicHelper.isNeedMagicSimple()) {
                    MagicHelper.udpateColorsMagicSimple(bufferarray);
                }

            } else if (MagicHelper.isNeedMagic()) {

                if (AppState.get().isCustomizeBgAndColors) {
                    renderPageSafe(muPdfDocument, pageHandle, mRect, ctm, bufferarray, -1, -1, -1);
                    MagicHelper.udpateColorsMagic(bufferarray);
                } else {
                    int color = MagicHelper.getBgColor();
                    if (!AppState.get().isDayNotInvert) {
                        color = ~color;
                    }
                    int r = Color.red(color);
                    int g = Color.green(color);
                    int b = Color.blue(color);
                    renderPageSafe(muPdfDocument, pageHandle, mRect, ctm, bufferarray, r, g, b);

                }
            } else {
                renderPageSafe(muPdfDocument, pageHandle, mRect, ctm, bufferarray, -1, -1, -1);
            }

            if (MagicHelper.isNeedBC) {
                MagicHelper.applyQuickContrastAndBrightness(bufferarray, width, height);
            }


            BitmapRef b;
            if (false) {
                b = BitmapManager.getBitmap("PDF page", width, height, AppsConfig.CURRENT_BITMAP_ARGB);
            } else {
                b = new BitmapRef(Bitmap.createBitmap(width, height, AppsConfig.CURRENT_BITMAP_ARGB), 0l);
            }
            b.getBitmap().setPixels(bufferarray, 0, width, 0, 0, width, height);
            if (AppState.get().isMirrorImage) {
                Matrix m = new Matrix();
                m.preScale(-1, 1);
                Bitmap dst = Bitmap.createBitmap(b.getBitmap(), 0, 0, width, height, m, false);
                b.getBitmap().recycle();
                b.setBitmap(dst);
            }

            return b;
        } finally {
            TempHolder.lock.unlock();
        }
    }

    //private static native boolean renderPageSafeBitmap(long dochandle, long pagehandle, int[] viewboxarray, float[] matrixarray, Bitmap bitmap);

    /**
     * 获取页面链接列表。
     *
     * @return 页面链接列表
     */
    @Override
    public List<PageLink> getPageLinks() {


        if (!AppsConfig.IS_ENABLE_1_PAGE_SEARCH && pageNumber == 1) {
            LOG.d("skip links for 1 page");
            return new ArrayList<PageLink>();
        }

        TempHolder.lock.lock();
        try {
            return MuPdfLinks.getPageLinks(docHandle, pageHandle, pageBounds);
        } finally {
            TempHolder.lock.unlock();
        }
    }

    /**
     * 获取字符数。
     *
     * @return 字符数
     */
    @Override
    public int getCharCount() {
        TempHolder.lock.lock();
        try {
            return getCharCount(docHandle, pageHandle);
        } finally {
            TempHolder.lock.unlock();
        }
    }

    /**
     * 添加墨迹注释的原生方法。
     *
     * @param docHandle 文档句柄
     * @param pageHandle 页面句柄
     * @param color     颜色数组
     * @param arcs      点坐标数组
     * @param width     线宽
     * @param alpha     透明度
     */
    private native void addInkAnnotationInternal(long docHandle, long pageHandle, float[] color, PointF[][] arcs, int width, float alpha);

    /**
     * 获取注释的原生方法。
     *
     * @param docHandle 文档句柄
     * @param pageHandle 页面句柄
     * @return 注释数组
     */
    private native Annotation[] getAnnotationsInternal(long docHandle, long pageHandle);

    /**
     * 添加标记注释的原生方法。
     *
     * @param docHandle   文档句柄
     * @param pageHandle  页面句柄
     * @param quadPoints  四边形点数组
     * @param type        注释类型
     * @param color       颜色数组
     */
    private native void addMarkupAnnotationInternal(long docHandle, long pageHandle, PointF[] quadPoints, int type, float color[]);

    /**
     * 获取页面HTML的原生方法。
     *
     * @param docHandle 文档句柄
     * @param pageHandle 页面句柄
     * @param opts       选项标志
     * @return HTML字节数组
     */
    private native byte[] getPageAsHtml(long docHandle, long pageHandle, int opts);

    /**
     * 获取页面HTML内容。
     *
     * @return HTML字符串
     */
    @Override
    public String getPageHTML() {
        LOG.d("getPageAsHtml");
        TempHolder.lock.lock();
        try {
            byte[] pageAsHtml = getPageAsHtml(docHandle, pageHandle, -1);
            String string = new String(pageAsHtml);
            LOG.d("getPageAsHtml", string);
            return string;
        } catch (Exception e) {
            LOG.e(e);
            return "";
        } finally {
            TempHolder.lock.unlock();
        }
    }

    /**
     * 获取页面HTML内容（包含图片）。
     *
     * @return HTML字符串
     */
    @Override
    public String getPageHTMLWithImages() {
        LOG.d("getPageAsHtml");
        TempHolder.lock.lock();
        try {
            // FZ_STEXT_PRESERVE_LIGATURES = 1,
            // FZ_STEXT_PRESERVE_WHITESPACE = 2,
            // FZ_STEXT_PRESERVE_IMAGES = 4,
            byte[] pageAsHtml = getPageAsHtml(docHandle, pageHandle, 4);
            String string = new String(pageAsHtml);
            LOG.d("getPageAsHtml WithImages", string);
            return string;
        } catch (Exception e) {
            LOG.e(e);
            return "";
        } finally {
            TempHolder.lock.unlock();
        }
    }

    /**
     * 添加标记注释。
     *
     * @param quadPoints 四边形点数组
     * @param type       注释类型
     * @param color      颜色数组
     */
    @Override
    public void addMarkupAnnotation(PointF[] quadPoints, AnnotationType type, float color[]) {
        if (quadPoints.length <= 0) {
            LOG.d("addMarkupAnnotation", "skip");
            return;
        }
        LOG.d("addMarkupAnnotation", quadPoints.length, type, color[0], color[1], color[2]);
        TempHolder.lock.lock();
        try {
            addMarkupAnnotationInternal(docHandle, pageHandle, quadPoints, type.ordinal(), color);
        } finally {
            TempHolder.lock.unlock();
        }

    }

    /**
     * 获取注释列表。
     *
     * @return 注释列表
     */
    @Override
    public List<Annotation> getAnnotationsImpl() {
        TempHolder.lock.lock();
        List<Annotation> result = new ArrayList<Annotation>();
        try {
            Annotation[] list = getAnnotationsInternal(docHandle, pageHandle);

            if (list != null) {
                for (int i = 0; i < list.length; i++) {
                    Annotation a = list[i];
                    update(a);
                    a.setIndex(i);
                    a.setPage(pageNumber);
                    a.setPageHandler(pageHandle);
                    result.add(a);
                    LOG.d("getAnnotation1s", pageNumber, i, "h", pageHandle);
                }
            }
        } finally {
            TempHolder.lock.unlock();
        }
        return result;
    }

    /**
     * 添加注释。
     *
     * @param color  颜色数组
     * @param points 点坐标数组
     * @param width  线宽
     * @param alpha  透明度
     */
    @Override
    public void addAnnotation(float[] color, PointF[][] points, float width, float alpha) {
        LOG.d("addInkAnnotationInternal", color[0], color[1], color[2]);
        TempHolder.lock.lock();
        try {
            addInkAnnotationInternal(docHandle, pageHandle, color, points, (int) width, alpha);
        } finally {
            TempHolder.lock.unlock();
        }
    }

    /**
     * 获取文本字符数据。
     *
     * @return 文本字符数据
     */
    public TextChar[][][][] text() {
        TempHolder.lock.lock();
        try {
            return text(docHandle, pageHandle);
        } catch (Throwable e) {
            LOG.e(e);
            return null;
        } finally {
            TempHolder.lock.unlock();
        }
    }

    /**
     * 获取页面文本词数据。
     * <p>
     * 根据MuPDF版本选择不同的解析方法。
     *
     * @return 文本词二维数组
     */
    @Override
    public TextWord[][] getTextImpl() {


        if (isRecycled()) {
            LOG.d("skip isRecycled");
            return new TextWord[0][0];
        }
        //SKIP TEM
        if (!AppsConfig.IS_ENABLE_1_PAGE_SEARCH && pageNumber == 1) {
            LOG.d("skip text for 1 page");
            return new TextWord[0][0];
        }


        if (AppsConfig.MUPDF_FZ_VERSION.equals(AppsConfig.MUPDF_1_11)) {
            return getText_111();
        } else {
            try {
                return getText_116();
            } catch (OutOfMemoryError e) {
                LOG.e(e);
                System.gc();
            }
        }

        return new TextWord[0][0];
    }

    /**
     * 获取文本词数据（新版本格式）。
     *
     * @return 文本词二维数组
     */
    public TextWord[][] getText_116() {
        List<TextChar> chars = null;

        TempHolder.lock.lock();
        try {
            chars = text116(docHandle, pageHandle);
        } finally {
            TempHolder.lock.unlock();
        }

        LOG.d("text116 size", chars.size());

        if (TxtUtils.isListEmpty(chars)) {
            return new TextWord[0][0];
        }

        ArrayList<TextWord[]> lns = new ArrayList<TextWord[]>();

        ArrayList<TextWord> words = new ArrayList<TextWord>();
        TextWord tw = new TextWord();
        for (TextChar tc : chars) {
            if (AppState.get().selectingByLetters) {
                if (tc.c == TxtUtils.NON_BREAKE_SPACE_CHAR) {
                    tc.c = ' ';
                }
                words.add(new TextWord(tc));
                continue;
            }

            if (tc.c == ' ') {
                if (tw.w.length() > 0) {
                    words.add(tw);
                    tw = new TextWord();
                }
                words.add(new TextWord(tc));
            } else {
                tw.Add(tc);
            }
        }
        if (tw.w.length() > 0) {
            words.add(tw);
        }


        if (words.size() > 0)
            lns.add(words.toArray(new TextWord[words.size()]));

        TextWord[][] res = lns.toArray(new TextWord[lns.size()][]);

        for (TextWord[] lines : res) {
            for (TextWord word : lines) {
                update(word);
            }
        }

        return res;
    }

    /**
     * 获取文本词数据（旧版本格式）。
     *
     * @return 文本词二维数组
     */
    public TextWord[][] getText_111() {
        TextChar[][][][] chars = text();
        if (chars == null) {
            return new TextWord[0][0];
        }

        ArrayList<TextWord[]> lns = new ArrayList<TextWord[]>();

        for (TextChar[][][] bl : chars) {

            if (bl == null)
                continue;
            for (TextChar[][] ln : bl) {
                ArrayList<TextWord> words = new ArrayList<TextWord>();
                TextWord word = new TextWord();

                for (TextChar[] sp : ln) {
                    for (TextChar tc : sp) {
                        if (AppState.get().selectingByLetters) {
                            if (tc.c == TxtUtils.NON_BREAKE_SPACE_CHAR) {
                                tc.c = ' ';
                            }
                            words.add(new TextWord(tc));
                            continue;
                        }
                        if (tc.c == ' ') {
                            words.add(new TextWord(tc));
                        }
                        if (tc.c != ' ') {
                            word.Add(tc);
                        } else if (word.w.length() > 0) {
                            words.add(word);
                            word = new TextWord();
                        }
                    }
                }

                if (word.w.length() > 0)
                    words.add(word);

                if (words.size() > 0)
                    lns.add(words.toArray(new TextWord[words.size()]));
            }
        }

        TextWord[][] res = lns.toArray(new TextWord[lns.size()][]);
        for (TextWord[] lines : res) {
            for (TextWord word : lines) {
                update(word);
            }
        }

        return res;
    }


    /**
     * 更新文本词坐标。
     *
     * @param wd 文本词
     */
    public void update(TextWord wd) {
        wd.setOriginal(wd);
        update((RectF) wd);
    }

    /**
     * 更新矩形坐标为归一化坐标。
     *
     * @param wd 矩形
     */
    public void update(RectF wd) {
        wd.left = (wd.left - pageBounds.left) / pageBounds.width();
        wd.top = (wd.top - pageBounds.top) / pageBounds.height();
        wd.right = (wd.right - pageBounds.left) / pageBounds.width();
        wd.bottom = (wd.bottom - pageBounds.top) / pageBounds.height();
    }

    /**
     * 更新搜索结果坐标并去重。
     *
     * @param rects 搜索结果矩形列表
     */
    private void udpateSearchResult(final List<PageTextBox> rects) {
        if (LengthUtils.isNotEmpty(rects)) {
            final Set<String> temp = new HashSet<String>();
            final Iterator<PageTextBox> iter = rects.iterator();
            while (iter.hasNext()) {
                final PageTextBox b = iter.next();
                if (temp.add(b.toString())) {
                    b.left = (b.left - pageBounds.left) / pageBounds.width();
                    b.top = (b.top - pageBounds.top) / pageBounds.height();
                    b.right = (b.right - pageBounds.left) / pageBounds.width();
                    b.bottom = (b.bottom - pageBounds.top) / pageBounds.height();
                } else {
                    iter.remove();
                }
            }
        }
    }
}
