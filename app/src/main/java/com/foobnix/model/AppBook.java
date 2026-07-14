package com.foobnix.model;

import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.MyMath;
import com.foobnix.android.utils.Objects;
import com.foobnix.pdf.info.Urls;
import com.foobnix.sys.TempHolder;

import org.ebookdroid.core.PageIndex;
import org.ebookdroid.core.events.CurrentPageListener;

/**
 * 书籍阅读状态类
 * <p>
 * 实现 CurrentPageListener 接口，用于记录和管理单本书籍的阅读状态，
 * 包括缩放级别、页面裁剪、双页模式、阅读进度、翻页速度等参数。
 */
public class AppBook implements CurrentPageListener {
    /** 锁定状态 - 默认 */
    public static int LOCK_NONE = 0;
    /** 锁定状态 - 已锁定 */
    public static int LOCK_YES = 1;
    /** 锁定状态 - 未锁定 */
    public static int LOCK_NOT = 2;


    @Objects.IgnoreCalculateHashCode
    /** 书籍文件路径 */
    public transient String path;

    /** 缩放级别（默认100%） */
    public int z = 100;
    /** 是否启用页面分割 */
    public boolean sp = false;
    /** 是否启用页面裁剪 */
    public boolean cp = false;
    /** 是否启用双页模式 */
    public boolean dp = false;
    /** 是否启用封面单独显示（双页模式下） */
    public boolean dc = false;
    /** 锁定状态 */
    public int lk = LOCK_NONE;
    /** 水平偏移量 */
    public float x;
    /** 垂直偏移量 */
    public float y;
    /** 自动翻页速度 */
    public int s = AppState.get().autoScrollSpeed;
    /** 页码偏移量 */
    public int d = 0;

    /** 阅读进度百分比 */
    public float p;
    /** 最后阅读时间 */
    public long t;
    /** 语言设置 */
    public String ln;

    /** 是否从右向左阅读 */
    public boolean rtl = Urls.isRtl();

    @Override
    public int hashCode() {
        final String s = "" + path + z + sp + cp + dp + dc + lk + (int) (x * 100) + (int) (y * 100) + this.s + d + p + ln + rtl;
        LOG.d("hashCode-appbook", s);
        return s.hashCode();
    }

    public AppBook() {
    }

    public AppBook(final String path) {
        this.path = path;
        this.rtl = AppState.get().isRTLByDefault;
    }

    public void updateFromAppState() {
        cp = AppSP.get().isCrop;
        dp = AppSP.get().isDouble;
        sp = AppSP.get().isCut;
        dc = AppSP.get().isDoubleCoverAlone;
        d = TempHolder.get().pageDelta;
        s = AppState.get().autoScrollSpeed;
        rtl = AppSP.get().isRTL;
        setLock(AppSP.get().isLocked);
    }

    public void setLock(Boolean lock) {
        if (lock == null) {
            this.lk = LOCK_NONE;
        } else {
            this.lk = lock ? LOCK_YES : LOCK_NOT;
        }
    }

    public boolean getLock(boolean isTextFormat, boolean lockedByDefault) {
        if (lk == LOCK_NONE) {
            return isTextFormat || lockedByDefault;
        }
        return lk == LOCK_YES;
    }


    public void currentPageChanged(int page, int pages) {
        if (page <= 0 || pages <= 0) {
            //if (LOG.isEnable) {
            //    throw new RuntimeException("Error!!! " + page + " : " + pages);
            //}
            LOG.d("currentPageChanged ERROR!!!", page + " : " + pages);
            return;
        }
        this.p = MyMath.percent(page, pages);
        LOG.d("currentPageChanged", page, pages, p);
        t = System.currentTimeMillis();
        if (page == pages) {
            LOG.d("currentPageChanged listHash", page, pages, p);
            TempHolder.listHash++;
        }

    }


    public PageIndex getCurrentPage(int pages) {
        if (pages <= 0) {
            //throw new RuntimeException("Error!!! " + pages);
            return new PageIndex(0,0);
        }
        if (this.p > 2) {//old import support
            LOG.d("AppBook-getCurrentPage old", p, pages);

        }

        int p = Math.round(pages * this.p);
        if (p > 0) {
            p = p - 1;
        }
        LOG.d("AppBook-getCurrentPage", p, this.p, pages);

        return new PageIndex(p, p);
    }

    public float getZoom() {
        return z / 100.0f;
    }

    public void setZoom(final float zoom) {
        this.z = Math.round(zoom * 100);
    }


    public static class Diff {

        private static final short D_SplitPages = 0x0001 << 1;
        private static final short D_AnimationType = 0x0001 << 3;
        private static final short D_CropPages = 0x0001 << 4;
        private static final short D_NightMode = 0x0001 << 5;

        private static final short D_Effects = D_NightMode;

        private short mask;
        private final boolean firstTime;

        public Diff(final AppBook olds, final AppBook news) {
            firstTime = olds == null;
            if (firstTime) {
                mask = (short) 0xFFFF;
            } else if (news != null) {
                if (olds.sp != news.sp) {
                    mask |= D_SplitPages;
                }
                if (olds.cp != news.cp) {
                    mask |= D_CropPages;
                }

            }
        }

        public boolean isFirstTime() {
            return firstTime;
        }

        public boolean isSplitPagesChanged() {
            return 0 != (mask & D_SplitPages);
        }

        public boolean isAnimationTypeChanged() {
            return 0 != (mask & D_AnimationType);
        }

        public boolean isCropPagesChanged() {
            return 0 != (mask & D_CropPages);
        }

        public boolean isEffectsChanged() {
            return 0 != (mask & (D_Effects));
        }
    }
}
