package com.foobnix.pdf.info;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.EncodeStrategy;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;
import com.foobnix.LibreraApp;
import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.wrapper.MagicHelper;
import com.foobnix.pdf.search.activity.HorizontalViewActivity;
import com.foobnix.sys.ImageExtractor;
import com.foobnix.ui2.MainTabs2;

import org.ebookdroid.ui.viewer.VerticalViewActivity;

/**
 * 图片加载与管理工具类
 * <p>
 * 基于 Glide 实现的图片加载管理，提供封面图片加载、缓存管理、图片尺寸计算等功能。
 * 支持书籍封面显示效果和主题切换。
 */
public class IMG {

    /** 封面宽高比 */
    public static final float WIDTH_DK = 1.4f;
    /** 两列显示时封面大小 */
    public static final int TWO_LINE_COVER_SIZE = 74;
    /** 带标记效果的书籍背景 */
    public static Drawable bookBGWithMark;
    /** 无标记效果的书籍背景 */
    public static Drawable bookBGNoMark;
    /** 全局上下文 */
    public static Context context;

    /**
     * 初始化图片资源
     * <p>
     * 加载书籍背景效果资源。
     *
     * @param context 上下文
     */
    public static void init(Context context) {
        IMG.context = context;
        bookBGWithMark = ContextCompat.getDrawable(context, R.drawable.bookeffect2);
        bookBGNoMark = ContextCompat.getDrawable(context, R.drawable.bookeffect1);
    }

    /**
     * 获取图片尺寸
     * <p>
     * 返回封面大图和小图中的最大值。
     *
     * @return 图片尺寸（像素）
     */
    public static int getImageSize() {
        return Dips.dpToPx(Math.max(AppState.get().coverSmallSize, AppState.get().coverBigSize));
    }

    /**
     * 更新图片为小尺寸
     * <p>
     * 根据配置的封面小尺寸设置ImageView大小，保持宽高比。
     *
     * @param imageView ImageView控件
     * @return 更新后的LayoutParams
     */
    public static LayoutParams updateImageSizeSmall(View imageView) {
        if (imageView == null || imageView.getLayoutParams() == null) {
            return null;
        }
        LayoutParams lp = imageView.getLayoutParams();
        lp.width = Dips.dpToPx(AppState.get().coverSmallSize);
        lp.height = (int) (lp.width * WIDTH_DK);
        return lp;
    }

    /**
     * 更新目录图片为小尺寸
     * <p>
     * 目录视图中的图片尺寸比普通小尺寸更小。
     *
     * @param imageView ImageView控件
     * @return 更新后的LayoutParams
     */
    public static LayoutParams updateImageSizeSmallDir(View imageView) {
        if (imageView == null || imageView.getLayoutParams() == null) {
            return null;
        }
        LayoutParams lp = imageView.getLayoutParams();
        lp.width = (int) (Dips.dpToPx(AppState.get().coverSmallSize) / 1.5);
        lp.height = (int) (lp.width * WIDTH_DK);
        return lp;
    }

    /**
     * 更新图片为大尺寸
     * <p>
     * 根据配置的封面大尺寸设置ImageView大小。
     *
     * @param imageView ImageView控件
     */
    public static void updateImageSizeBig(View imageView) {
        if (imageView == null || imageView.getLayoutParams() == null) {
            return;
        }
        LayoutParams lp = imageView.getLayoutParams();
        lp.width = Dips.dpToPx(AppState.get().coverBigSize);
        lp.height = (int) (lp.width * WIDTH_DK);
    }

    /**
     * 更新图片为指定大尺寸
     *
     * @param imageView ImageView控件
     * @param sizeDP    目标尺寸（DP）
     */
    public static void updateImageSizeBig(View imageView, int sizeDP) {
        if (imageView == null || imageView.getLayoutParams() == null) {
            return;
        }
        LayoutParams lp = imageView.getLayoutParams();
        lp.width = Dips.dpToPx(sizeDP);
        lp.height = (int) (lp.width * WIDTH_DK);
    }

    /**
     * 设置颜色透明度
     * <p>
     * 根据百分比调整颜色的透明度。
     *
     * @param persent 透明度百分比（0-100）
     * @param color   原始颜色（#RRGGBB格式）
     * @return 带透明度的颜色值
     */
    public static int alphaColor(int persent, String color) {
        try {
            int parseColor = Color.parseColor(color);
            int alpha = 255 * persent / 100;
            return Color.argb(alpha, Color.red(parseColor), Color.green(parseColor), Color.blue(parseColor));
        } catch (Exception e) {
            return Color.WHITE;
        }
    }

    /**
     * 清除内存缓存
     * <p>
     * 调用Glide清除内存中的图片缓存。
     */
    public static void clearMemoryCache() {
        if (LibreraApp.context != null) {
            LOG.d("clearMemoryCache", "Bitmap-test-2 clearMemoryCache");
            Glide.get(LibreraApp.context).clearMemory();
        }
    }

    /**
     * 获取Glide请求管理器
     * <p>
     * 根据上下文类型返回对应的RequestManager，确保生命周期管理正确。
     *
     * @param a 上下文
     * @return Glide RequestManager
     */
    public static RequestManager with(Context a) {
        if (a instanceof HorizontalViewActivity) {
            return Glide.with((HorizontalViewActivity) a);
        } else if (a instanceof VerticalViewActivity) {
            return Glide.with((VerticalViewActivity) a);
        } else if (a instanceof MainTabs2) {
            return Glide.with((MainTabs2) a);
        } else {
            return Glide.with(LibreraApp.context);
        }
    }

    /**
     * 暂停图片加载请求
     * <p>
     * 当前未启用，预留接口。
     *
     * @param a 上下文
     */
    public static void pauseRequests(Context a) {
        LOG.d("Glide-pause", a);
        //with(a).pauseRequests();
    }

    /**
     * 恢复图片加载请求
     * <p>
     * 当前未启用，预留接口。
     *
     * @param a 上下文
     */
    public static void resumeRequests(Context a) {
        LOG.d("Glide-resume", a);
        //with(a).resumeRequests();
    }

    /**
     * 清除ImageView缓存
     * <p>
     * 安全清除ImageView的图片缓存，避免Activity已销毁时出错。
     *
     * @param image ImageView控件
     */
    public static void clear(ImageView image) {
        try {
            LOG.d("Glide-clear", image.getContext());
            Activity activity = ((Activity) image.getContext());
            if (Build.VERSION.SDK_INT < 17 || !activity.isDestroyed()) {
                with(image.getContext()).clear(image);
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /**
     * 清除指定Target缓存
     *
     * @param c 上下文
     * @param t Glide Target
     */
    public static void clear(Context c, Target t) {
        LOG.d("Glide-clear", c);
        try {
            with(c).clear(t);
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /**
     * 清除磁盘缓存
     * <p>
     * 在后台线程中清除Glide的磁盘缓存。
     */
    public static void clearDiscCache() {
        LOG.d("clearDiscCache", "Bitmap-test-2 clearDiscCache");
        AppsConfig.executorService.execute(new Runnable() {
            @Override public void run() {
                try {
                    if (LibreraApp.context != null) {
                        Glide.get(LibreraApp.context).clearDiskCache();
                    }
                } catch (Exception e) {
                    LOG.e(e);
                }
            }
        });
    }

    /**
     * 清除指定路径缓存（预留接口）
     *
     * @param path 文件路径
     */
    public static void clearCache(String path) {
        try {

        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /**
     * 图片资源加载完成回调接口
     */
    public static interface ResourceReady {
        /**
         * 资源加载完成时调用
         *
         * @param bitmap 加载的Bitmap
         */
        void onResourceReady(Bitmap bitmap);
    }

    /**
     * 获取封面图片URL
     * <p>
     * 根据书籍路径生成封面图片的URL。
     *
     * @param path 书籍文件路径
     * @return 封面图片URL
     */
    public static String getCoverUrl(String path){
        return toUrl(path, ImageExtractor.COVER_PAGE, IMG.getImageSize(), false);
    }

    /**
     * 自动磁盘缓存策略
     * <p>
     * 只缓存本地数据源的图片，避免网络图片占用缓存空间。
     */
    public static final DiskCacheStrategy AUTOMATIC1 =
            new DiskCacheStrategy() {
                @Override
                public boolean isDataCacheable(DataSource dataSource) {
                    return dataSource == DataSource.LOCAL;
                }

                @Override
                public boolean isResourceCacheable(
                        boolean isFromAlternateCacheKey, DataSource dataSource, EncodeStrategy encodeStrategy) {
                    return dataSource == DataSource.LOCAL;
                }

                @Override
                public boolean decodeCachedResource() {
                    return true;
                }

                @Override
                public boolean decodeCachedData() {
                    return true;
                }
            };

    /**
     * 获取带效果的封面页面
     * <p>
     * 使用Glide加载封面图片，支持封面效果和缓存策略。
     *
     * @param context 上下文
     * @param path    书籍路径
     * @param run     加载完成回调
     * @return Glide RequestBuilder
     */
    public static RequestBuilder<Bitmap> getCoverPageWithEffect(Context context, String path, ResourceReady run) {
        int imageSize = IMG.getImageSize();
        String url = toUrl(path, ImageExtractor.COVER_PAGE, imageSize,false);
        return IMG.with(context)
           .asBitmap()
           .load(url)
           .override(imageSize)
                .onlyRetrieveFromCache(false)
           .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
              .signature(new ObjectKey(url.hashCode()))
           .listener(new RequestListener<>() {
               @Override public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target,
                                                     boolean isFirstResource) {
                   return false;
               }

               @Override
               public boolean onResourceReady(Bitmap bitmap, Object model, Target<Bitmap> target, DataSource dataSource,
                                              boolean isFirstResource) {
                   target.onResourceReady(bitmap, null);
                   LOG.d("Bitmap-test-2", bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig(),
                           dataSource,isFirstResource,model);

                   if (run != null) {
                       run.onResourceReady(null);
                   }
                   return true;
               }
           });
    }

    /**
     * 生成页面图片URL
     * <p>
     * 根据书籍路径、页码、宽度生成图片URL，可选是否包含配置哈希值。
     *
     * @param path     书籍路径
     * @param page     页码
     * @param width    宽度（像素）
     * @param withHash 是否包含配置哈希值（用于主题切换时刷新缓存）
     * @return 页面图片URL
     */
    public static String toUrl(final String path, final int page, final int width, boolean withHash) {
        PageUrl pdfUrl = new PageUrl(path, page, width, 0, false, false, 0,false);
        pdfUrl.setUnic(0);
        if(withHash) {
            pdfUrl.hash =
                    ("" + AppState.get().isBookCoverEffect + TintUtil.getColorInDayNighth() + AppState.get().sortByBrowse).hashCode() + MagicHelper.hash();
        }else{
            pdfUrl.hash = 0;
        }
        return pdfUrl.toString();
    }
}
