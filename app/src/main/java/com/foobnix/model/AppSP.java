package com.foobnix.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.Objects;
import com.foobnix.pdf.info.Android6;
import com.foobnix.pdf.info.AppsConfig;
import com.foobnix.pdf.info.Urls;

import java.io.File;

/**
 * 应用临时状态管理类
 * <p>
 * 采用单例模式，管理应用的临时状态和最近阅读信息，包括当前阅读书籍、页码、阅读模式等。
 * 数据持久化到 SharedPreferences，用于快速恢复阅读状态。
 */
public class AppSP {

    private static AppSP instance = new AppSP();
    /** 最后阅读书籍的路径 */
    public String lastBookPath;

    /** 最后阅读页码 */
    public int lastBookPage = 0;
    /** 最后阅读书籍的总页数 */
    public int lastBookPageCount = 0;
    /** 临时页码 */
    public int tempBookPage = 0;
    /** 最后阅读段落索引 */
    public volatile int lastBookParagraph = 0;
    /** 最后阅读书籍标题 */
    public String lastBookTitle;
    /** 最后阅读书籍宽度 */
    public int lastBookWidth = 0;
    /** 最后阅读书籍高度 */
    public int lastBookHeight = 0;
    /** 最后字体大小 */
    public int lastFontSize = 0;
    /** 最后阅读书籍语言 */
    public String lastBookLang = "";
    /** 是否锁定屏幕方向 */
    public boolean isLocked = false;
    /** 是否首次使用垂直模式 */
    public boolean isFirstTimeVertical = true;
    /** 是否首次使用水平模式 */
    public boolean isFirstTimeHorizontal = true;

    /** 当前阅读模式 */
    public int readingMode = AppState.READING_MODE_BOOK;
    /** 同步时间 */
    public long syncTime;
    /** 同步状态 */
    public int syncTimeStatus;
    /** 连字符语言 */
    public String hypenLang = null;
    /** 是否启用页面分割 */
    public boolean isCut = false;
    /** 是否启用双页模式 */
    public boolean isDouble = false;
    /** 是否从右向左阅读 */
    public boolean isRTL = Urls.isRtl();
    /** 是否封面单独显示（双页模式） */
    public boolean isDoubleCoverAlone = false;
    /** 是否启用页面裁剪 */
    public boolean isCrop = false;
    /** 是否对称裁剪 */
    public boolean isCropSymetry = false;
    /** 是否启用智能重排 */
    public boolean isSmartReflow = false;
    /** 是否启用同步 */
    public boolean isEnableSync;
    /** 同步根目录ID */
    public String syncRootID;

    /** 当前配置文件名称 */
    public String currentProfile = "";
    /** 根目录路径 */
    public String rootPath1 = getRootDir();

    /** SharedPreferences实例 */
    transient SharedPreferences sp;

    /** 插屏广告加载时间 */
    public long interstitialLoadAdTime = 0;
    /** 插屏广告显示时间 */
    public long interstitialAdShowTime = 0;

    /** 奖励广告加载时间 */
    public long rewardedAdLoadedTime = 0;
    /** 奖励广告显示时间 */
    public long rewardShowTime = 0;


    /**
     * 获取单例实例
     *
     * @return AppSP单例对象
     */
    public static AppSP get() {
        return instance;
    }

    /**
     * 初始化SharedPreferences并加载数据
     *
     * @param c 上下文
     */
    public void init(Context c) {
        sp = c.getSharedPreferences("AppTemp", Context.MODE_PRIVATE);
        load(c);
        getRootPath(c);
    }

    /**
     * 获取演示文件临时目录
     *
     * @param c 上下文
     * @return 临时目录路径
     */
    public String getTempDir(Context c){
        return new File(c.getExternalFilesDir(null), "Demo").toString();
    }

    /**
     * 获取应用根目录
     * <p>
     * 默认位于外部存储的Librera文件夹。
     *
     * @return 根目录路径
     */
    public String getRootDir(){
        return new File(Environment.getExternalStorageDirectory(), "Librera").toString();
    }

    /**
     * 获取临时下载书籍目录
     *
     * @param c 上下文
     * @return 临时下载目录
     */
    public File getTempDownloadBooks(Context c){
        return new File(c.getExternalFilesDir(null), "TempDownloads");
    }

    public String getRootPath(Context c){
        LOG.d("rootPath2","getRootPath-1",rootPath1, currentProfile);
        if(instance.currentProfile.isEmpty()) {
            if (!Android6.canWrite(c)) {
                instance.rootPath1 = getTempDir(c);
                instance.currentProfile = "Demo";
            } else {
                instance.rootPath1 =getRootDir();
                instance.currentProfile = AppsConfig.IS_LOG ? "BETA" : "Librera";
            }

        }
        LOG.d("rootPath2","getRootPath-2",rootPath1, currentProfile);
        return instance.rootPath1;
    }

    public void load(Context c) {
        Objects.loadFromSp(instance, sp);
    }

    public void save() {
        Objects.saveToSP(instance, sp);
        LOG.d("rootPath2","save-1",rootPath1, currentProfile);
        LOG.d("rootPath2","save-2",get().rootPath1, get().currentProfile);

    }

}
