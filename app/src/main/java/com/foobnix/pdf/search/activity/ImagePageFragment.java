package com.foobnix.pdf.search.activity;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.AsyncTaskLoader;
import androidx.loader.content.Loader;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.foobnix.android.utils.LOG;
import com.foobnix.pdf.info.AppsConfig;
import com.foobnix.pdf.info.IMG;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.MagicHelper;
import com.foobnix.sys.ImageExtractor;

import java.util.concurrent.Future;


/**
 * 图片页面片段。
 * <p>
 * 负责显示漫画/图片格式书籍的页面，支持延迟加载和优先级管理。
 */
public class ImagePageFragment extends Fragment {
    /** 页面位置参数名 */
    public static final String POS = "pos";
    /** 页面路径参数名 */
    public static final String PAGE_PATH = "pagePath";
    /** 是否文本格式参数名 */
    public static final String IS_TEXTFORMAT = "isTEXT";
    /** 计数器 */
    public static volatile int count = 0;
    /** 页码 */
    int page;
    /** 处理器，用于延迟加载 */
    Handler handler;
    /** 生命周期时间 */
    long lifeTime = 0;
    /** 图片加载ID */
    int loadImageId;
    /** 是否首次加载 */
    boolean fistTime = true;
    /** Glide图片加载目标 */
    CustomTarget<Bitmap> target = null;
    /** 异步任务提交 */
    Future<?> submit;
    /** 页面图片视图 */
    private PageImaveView image;
    /** 页码文本视图 */
    private TextView text;
    Runnable callback = new Runnable() {

        @Override
        public void run() {
            if (!isDetached()) {
                loadImageGlide();
            } else {
                LOG.d("Image page is detached");
            }
        }

    };

    /**
     * 获取页面路径。
     *
     * @return 页面图片路径
     */
    public String getPath() {
        return getArguments().getString(PAGE_PATH);
    }

    /**
     * 创建视图。
     * <p>
     * 初始化页面图片视图和页码文本，设置延迟加载机制。
     *
     * @param inflater           布局加载器
     * @param container          容器
     * @param savedInstanceState 保存的实例状态
     * @return 视图对象
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.page_n, container, false);

        page = getArguments().getInt(POS);


        text = (TextView) view.findViewById(R.id.text1);
        image = (PageImaveView) view.findViewById(R.id.myImage1);

        image.setPageNumber(page);
        text.setText(getString(R.string.page) + " " + (page + 1));

        text.setTextColor(MagicHelper.getTextColor());
        //TxtUtils.setLinkTextColor(text);

        handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {

            @Override
            public void run() {
                LOG.d("ImagePageFragment1  run ", page, "getPriority", getPriority(), "counter", count);
                if (isVisible()) {
                    if (0 == getPriority()) {
                        handler.post(callback);
                    } else if (1 == getPriority()) {
                        handler.postDelayed(callback, 100);
                    } else {
                        handler.postDelayed(callback, getPriority() * 100);
                    }
                }

            }
        }, 50);
        lifeTime = System.currentTimeMillis();


        return view;
    }

    /**
     * 使用线程池加载图片。
     * <p>
     * 通过executorServiceSingle提交异步任务加载图片，支持任务取消。
     */
    public void loadImageGlide21() {

        submit = AppsConfig.executorServiceSingle.submit(new Runnable() {
            @Override
            public void run() {
                if (submit.isCancelled()) {
                    LOG.d("loadImageGlide-isCancelled 1");
                    return;
                }
                Bitmap bitmap = ImageExtractor.getInstance(getContext()).proccessOtherPage(getPath());

                if (submit.isCancelled()) {
                    LOG.d("loadImageGlide-isCancelled 2");
                    return;
                }

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        text.setVisibility(View.GONE);
                        if (bitmap != null && image != null) {
                            image.addBitmap(bitmap);
                        }
                    }
                });

            }
        });

    }

    /**
     * 使用Loader加载图片。
     * <p>
     * 通过LoaderManager和AsyncTaskLoader异步加载图片。
     */
    public void loadImageGlide2() {
        final LoaderManager.LoaderCallbacks<Bitmap> callback = new LoaderManager.LoaderCallbacks<Bitmap>() {
            @NonNull
            @Override
            public Loader<Bitmap> onCreateLoader(int id, @Nullable Bundle args) {
                return new AsyncTaskLoader<Bitmap>(getContext()) {

                    @Nullable
                    @Override
                    public Bitmap loadInBackground() {
                        return ImageExtractor.getInstance(getContext()).proccessOtherPage(getPath());

                    }
                };
            }

            @Override
            public void onLoadFinished(@NonNull Loader<Bitmap> loader, Bitmap data) {
                LOG.d("loadImageGlide-onLoadFinished");

                text.setVisibility(View.GONE);
                if (data != null && image != null) {
                    image.addBitmap(data);
                }
            }

            @Override
            public void onLoaderReset(@NonNull Loader<Bitmap> loader) {
                LOG.d("loadImageGlide-onLoaderReset");
            }
        };
        LoaderManager.getInstance(getActivity()).initLoader(getPath().hashCode(), null, callback).forceLoad();
    }

    /**
     * 使用Glide加载图片（当前使用的方法）。
     * <p>
     * 通过Glide库异步加载图片，加载完成后隐藏页码文本。
     */
    public void loadImageGlide() {
        if (image != null && image.getWidth() == 0) {
            return;
        }
        target = new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                text.setVisibility(View.GONE);
                if (resource != null && image != null) {
                    image.addBitmap(resource);
                }
            }

            @Override
            public void onLoadCleared(Drawable placeholder) {

            }

        };
        LOG.d("Glide-load-into", getActivity());
        IMG.with(getActivity())
                .asBitmap()
                .load(getPath())
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(target);
    }

    /**
     * 恢复视图。
     * <p>
     * 初始化点击工具，检查并应用自动适配。
     */
    @Override
    public void onResume() {
        super.onResume();
        if (image != null) {
            image.clickUtils.init();

            float[] values = new float[10];
            if (image != null) {
                image.imageMatrix().getValues(values);
                if (values[Matrix.MSCALE_X] == 0) {
                    PageImageState.get().isAutoFit = true;
                    image.autoFit();
                    LOG.d("fonResume-autofit", page);
                }
            }

        }
    }

    /**
     * 获取页面加载优先级。
     * <p>
     * 根据当前页面与目标页面的距离计算优先级，距离越近优先级越高。
     *
     * @return 优先级值（0-10）
     */
    public int getPriority() {
        return Math.min(Math.abs(PageImageState.currentPage - page), 10);
    }


    /**
     * 销毁视图。
     * <p>
     * 清理Glide加载任务，移除Handler回调，释放资源。
     */
    @Override
    public void onDestroyView() {

        super.onDestroyView();
        LOG.d("loadImageGlide-onDestroyView");
//        if (submit != null) {
//            submit.cancel(true);
//
//        }
//        if (image != null) {
//            image.recycle();
//        }
//        LoaderManager.getInstance(getActivity()).destroyLoader(getPath().hashCode());

        if (Build.VERSION.SDK_INT >= 17 && !getActivity().isDestroyed()) {
            IMG.clear(getActivity(), target);
        } else if (!getActivity().isFinishing()) {
            IMG.clear(getActivity(), target);
        }


        LOG.d("ImagePageFragment1 onDetach ", page, "Lifi Time: ", System.currentTimeMillis() - lifeTime);
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        image = null;
    }

}
