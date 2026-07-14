package com.foobnix.pdf.info;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;

import androidx.work.ExistingWorkPolicy;

import com.bumptech.glide.load.DecodeFormat;
import com.foobnix.LibreraBuildConfig;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppState;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.ebookdroid.droids.mupdf.codec.MuPdfDocument;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应用配置类
 * <p>
 * 管理应用全局配置，包括构建版本、设备类型、线程池、功能开关等。
 * 包含MuPDF库加载、广告控制、测试设备识别等功能。
 */
public class AppsConfig {

    /** 当前位图解码格式 */
    public static final DecodeFormat CURRENT_BITMAP = DecodeFormat.PREFER_ARGB_8888;
    /** 当前位图ARGB配置 */
    public static final Bitmap.Config CURRENT_BITMAP_ARGB = Bitmap.Config.ARGB_8888;
    /** Pro版包名 */
    public static final String PRO_LIBRERA_READER = "com.foobnix.pro.pdf.reader";
    /** 标准版包名 */
    public static final String LIBRERA_READER = "com.foobnix.pdf.reader";
    /** 是否在页面显示广告 */
    public static final boolean ADS_ON_PAGE = false;
    /** 是否为F-Droid或华为版本 */
    public static final boolean IS_FDROID = LibreraBuildConfig.FLAVOR.equals("fdroid") || LibreraBuildConfig.FLAVOR.equals("huawei");
    /** 测试设备ID列表 */
    public static final List<String> testDevices = Arrays.asList(
            "77EA5ED1B6B3C6C8511E2696FB1B7D08",
            "394FC2536F98E69D313F47CA4B26AB2D",
            "15B8E113746E8241A97A23D7F6FEAA2B",
            "DBD4EE494036921735BA1981B883D379",
            "51C86C5778E8E588602F7DD6215975E8",
            "00AC2A127826E69269908EF10F066517");
    /** 是否写入日志 */
    public static final boolean IS_WRITE_LOGS = IS_FDROID;
    /** 当前构建变体 */
    public static final String FLAVOR = LibreraBuildConfig.FLAVOR;
    /** 是否启用单页搜索 */
    public static final boolean IS_ENABLE_1_PAGE_SEARCH = true;

    /** 搜索Fragment Worker名称 */
    public static final String SEARCH_FRAGMENT_WORKER_NAME = "search";
    /** Worker执行策略 */
    public static final ExistingWorkPolicy WORKER_POLICY = ExistingWorkPolicy.REPLACE;

    /** CPU核心数 */
    static int cpuCores = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);

    /** 通用线程池（2个线程） */
    public final static ExecutorService executorService = Executors.newFixedThreadPool(2);
    /** 单线程线程池 */
    public final static ExecutorService executorServiceSingle = Executors.newSingleThreadExecutor();

    /** 是否启用日志 */
    public static boolean IS_LOG = Build.DEVICE.toLowerCase().contains("emu") || Build.MODEL.toLowerCase().contains("sdk");
    /** 是否为模拟器 */
    public static boolean IS_EMULATOR = Build.DEVICE.toLowerCase().contains("emu") || Build.MODEL.toLowerCase().contains("sdk");
    /** 是否为测试设备 */
    public static boolean IS_TEST_DEVICE = false;
    /** MuPDF版本号 */
    public static String MUPDF_FZ_VERSION = "";
    /** MuPDF 1.11版本标识 */
    public static String MUPDF_1_11 = "1.11";
    /** 是否支持DOCX格式 */
    public static boolean isDOCXSupported = Build.VERSION.SDK_INT >= 26;
    /** 是否启用云功能 */
    public static boolean isCloudsEnable = false;

    /**
     * 静态初始化块
     * <p>
     * 加载MuPDF本地库并获取版本号。
     */
    static {
        System.loadLibrary("MuPDF");
        AppsConfig.MUPDF_FZ_VERSION = MuPdfDocument.getFzVersion();
        Log.d("Librera", "MUPDF_VERSION " + MUPDF_FZ_VERSION);
    }

    /**
     * 是否启用PDF绘制
     *
     * @return true（始终启用）
     */
    public static boolean isPDF_DRAW_ENABLE() {
        return true;
    }

    /**
     * 初始化应用配置
     * <p>
     * 检测设备是否为测试设备，设置日志开关。
     *
     * @param c 上下文
     */
    public static void init(Context c) {
        String deviceID = ADS.getByTestID(c);
        IS_TEST_DEVICE = testDevices.contains(deviceID);
        if (IS_TEST_DEVICE) {
            IS_LOG = true;
        }
        Log.d("DEVICE_ID", "DEVICE_ID " + deviceID);
        Log.d("IS_TEST_DEVICE", "IS_TEST_DEVICE  " + AppsConfig.IS_TEST_DEVICE);
        Log.d("IS_LOG", "IS_LOG " + AppsConfig.IS_LOG);
    }

    /**
     * 是否在应用内显示广告
     *
     * @param a 上下文
     * @return 是否显示广告
     */
    public static boolean isShowAdsInApp(final Context a) {
        if (a == null) {
            LOG.d("no-ads error context null");
            return false;
        }

        if (!isGooglePlayServicesAvailable(a)) {
            //no ads for old android and eink
            LOG.d("no-ads isGooglePlayServicesAvailable not available");
            return false;
        }
        if (Build.VERSION.SDK_INT <= 16 || Dips.isEInk()) {
            LOG.d("no-ads old device or eink");
            //no ads for old android and eink
            return false;
        }
        if (AppState.get().isEnableAccessibility) {
            return false;
        }
        if (Apps.isAccessibilityEnable(a)) {
            return false;
        }

        boolean is_pro = isPackageExisted(a, PRO_LIBRERA_READER);
        LOG.d("isPackageExisted", is_pro);
        if (is_pro) {
            return false;
        }
        return true;
    }

    public static boolean isGooglePlayServicesAvailable(Context context) {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context);
        return resultCode == ConnectionResult.SUCCESS;
    }

    public static boolean isPackageExisted(final Context a, final String targetPackage) {
        try {
            final PackageManager pm = a.getPackageManager();
            pm.getPackageInfo(targetPackage, PackageManager.GET_META_DATA);
        } catch (final NameNotFoundException e) {
            return false;
        }
        return true;
    }
}
