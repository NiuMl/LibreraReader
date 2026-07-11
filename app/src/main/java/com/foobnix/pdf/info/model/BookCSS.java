package com.foobnix.pdf.info.model;

import static com.foobnix.pdf.info.AppsConfig.MUPDF_1_11;
import static com.foobnix.pdf.info.AppsConfig.MUPDF_FZ_VERSION;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Environment;

import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.IO;
import com.foobnix.android.utils.JsonDB;
import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.Objects;
import com.foobnix.android.utils.Objects.IgnoreHashCode;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.dao2.FileMeta;
import com.foobnix.model.AppBook;
import com.foobnix.model.AppProfile;
import com.foobnix.model.AppSP;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.wrapper.MagicHelper;
import com.foobnix.ui2.AppDB;
import com.foobnix.ui2.FileMetaCore;

import org.ebookdroid.common.settings.books.SharedBooks;
import org.librera.LinkedJSONObject;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 书籍样式配置类。
 * <p>
 * 管理阅读时的样式设置，包括字体、颜色、间距、对齐方式等。
 * 采用单例模式，全局共享样式配置。
 */
public class BookCSS {
    /// PATHS

    /** 云端同步路径 - Dropbox */
    public static final String LIBRERA_CLOUD_DROPBOX = "Librera.Cloud-Dropbox";
    /** 云端同步路径 - Google Drive */
    public static final String LIBRERA_CLOUD_GOOGLEDRIVE = "Librera.Cloud-GoogleDrive";
    /** 云端同步路径 - OneDrive */
    public static final String LIBRERA_CLOUD_ONEDRIVE = "Librera.Cloud-OneDrive";
    /** 默认链接颜色 */
    public static final String LINK_COLOR_UNIVERSAL = "#0066cc";
    /** 文本对齐 - 两端对齐 */
    public static final int TEXT_ALIGN_JUSTIFY = 0;
    /** 文本对齐 - 左对齐 */
    public static final int TEXT_ALIGN_LEFT = 1;
    /** 文本对齐 - 右对齐 */
    public static final int TEXT_ALIGN_RIGHT = 2;
    /** 文本对齐 - 居中 */
    public static final int TEXT_ALIGN_CENTER = 3;

    /** 字体名称 - Times New Roman */
    public static final String TIMES_NEW_ROMAN = "Times New Roman";
    /** 字体名称 - Arial */
    public static final String ARIAL = "Arial";
    /** 字体名称 - Courier */
    public static final String COURIER = "Courier";
    /** 字体名称 - Charis SIL */
    public static final String CHARIS_SIL = "Charis SIL";

    /** 默认字体 */
    public static final String DEFAULT_FONT = CHARIS_SIL;

    /** 日间模式链接颜色选项 */
    public static final String LINKCOLOR_DAYS = "#001BA5, #9F0600" + "," + LINK_COLOR_UNIVERSAL;
    /** 夜间模式链接颜色选项 */
    public static final String LINKCOLOR_NIGHTS = "#7494B2, #B99D83" + "," + LINK_COLOR_UNIVERSAL;
    /** 日志标签 */
    private static final Object TAG = "BookCSS";
    /** 样式模式 - 文档和用户样式 */
    public static int STYLES_DOC_AND_USER = 0;
    /** 样式模式 - 仅文档样式 */
    public static int STYLES_ONLY_DOC = 1;
    /** 样式模式 - 仅用户样式 */
    public static int STYLES_ONLY_USER = 2;
    /** 字体文件扩展名 */
    public static List<String> fontExts = Arrays.asList(".ttf", ".otf");
    /** 单例实例 */
    private static BookCSS instance = new BookCSS();
    /** 搜索路径JSON */
    public String searchPathsJson;

    /** 缓存路径 */
    public String cachePath = new File(AppProfile.DOWNLOADS_DIR, "Librera/Cache").getPath();
    /** 下载路径 */
    public String downlodsPath;

    ///
    /** TTS语音路径 */
    public String ttsSpeakPath = new File(AppProfile.DOWNLOADS_DIR, "Librera/TTS").getPath();
    /** 备份路径 */
    public String backupPath = new File(AppProfile.DOWNLOADS_DIR, "Librera/Backup").getPath();

    /** Dropbox同步路径 */
    public String syncDropboxPath = new File(AppProfile.DOWNLOADS_DIR, "Librera/" + LIBRERA_CLOUD_DROPBOX).getPath();
    /** Google Drive同步路径 */
    public String syncGdrivePath = new File(AppProfile.DOWNLOADS_DIR, "Librera/" + LIBRERA_CLOUD_GOOGLEDRIVE).getPath();
    /** OneDrive同步路径 */
    public String syncOneDrivePath = new File(AppProfile.DOWNLOADS_DIR, "Librera/" + LIBRERA_CLOUD_ONEDRIVE).getPath();
    /** 词典路径 */
    public String dictPath;
    /** 字体文件夹 */
    public String fontFolder;
    /** 字体大小（SP） */
    public volatile int fontSizeSp = Dips.isXLargeScreen() ? 24 : 20;
    /** 应用字体缩放 */
    public float appFontScale = 1.0f;
    /** MP3书籍路径JSON */
    public String mp3BookPathJson;
    /** 最后访问目录路径 */
    public String dirLastPath = Environment.getExternalStorageDirectory().getPath();
    /** SAF路径 */
    public String pathSAF = "";
    /** 是否仅在WiFi下同步 */
    public boolean isSyncWifiOnly;
    /** 是否启用下拉刷新 */
    public boolean isSyncPullToRefresh = true;
    /** 是否启用同步动画 */
    public boolean isSyncAnimation = true;
    /** 文档样式模式 */
    public int documentStyle = STYLES_DOC_AND_USER;
    /** 上边距 */
    public int marginTop;
    /** 右边距 */
    public int marginRight;
    /** 下边距 */
    public int marginBottom;
    /** 左边距 */
    public int marginLeft;
    /** 空行大小 */
    public int emptyLine;
    /** 行高 */
    public int lineHeight12;
    /** 段落间距 */
    public int paragraphHeight;
    /** 首行缩进 */
    public int textIndent;
    /** 字体粗细 */
    public int fontWeight;
    /** 自定义CSS */
    public String customCSS2;
    /** 文本对齐方式 */
    public int textAlign;
    /** 显示字体名称 */
    public String displayFontName;
    /** 普通字体 */
    public String normalFont;
    /** 粗体字体 */
    public String boldFont;
    /** 粗斜体字体 */
    public String boldItalicFont;
    /** 斜体字体 */
    public String italicFont;
    /** 标题字体 */
    public String headersFont;
    /** 首字下沉字体 */
    public String capitalFont;
    /** 是否自动连字 */
    public boolean isAutoHypens;
    /** 日间模式链接颜色 */
    public String linkColorDay;
    /** 夜间模式链接颜色 */
    public String linkColorNight;
    /** 是否启用首字下沉 */
    public boolean isCapitalLetter = false;
    /** 首字下沉大小 */
    public int capitalLetterSize = 20;
    /** 首字下沉颜色 */
    public String capitalLetterColor = "#ff0000";
    /** 图片缩放 */
    public float imageScale = 2.0f;

    @IgnoreHashCode
    /** 哈希码 */
    public int hashCode = 0;

    @IgnoreHashCode
    /** 日间模式链接颜色选项 */
    public String linkColorDays = LINKCOLOR_DAYS;
    @IgnoreHashCode
    /** 夜间模式链接颜色选项 */
    public String linkColorNigths = LINKCOLOR_NIGHTS;
    /** 用户样式CSS文件 */
    public String userStyleCss = MUPDF_FZ_VERSION.equals(MUPDF_1_11) ? "app-Librera.css" : "app-Librera-Tables.css";
    /** 最后书籍路径缓存 */
    private String lastBookPathCache = "";
    /** 轨道路径缓存 */
    private String trackPathCache;

    /**
     * 过滤字体名称。
     * <p>
     * 移除字体名称中的特殊字符（-、_、空格），保留扩展名。
     *
     * @param fontName 字体名称
     * @return 过滤后的字体名称
     */
    public static String filterFontName(String fontName) {
        if (!fontName.contains(".")) {
            return fontName;
        }
        String ext = ExtUtils.getFileExtension(fontName);
        if (fontName.contains("-")) {
            fontName = fontName.substring(0, fontName.indexOf("-")) + "." + ext;
        } else if (fontName.contains("_")) {
            fontName = fontName.substring(0, fontName.indexOf("_")) + "." + ext;
        } else if (fontName.contains(" ")) {
            fontName = fontName.substring(0, fontName.indexOf(" ")) + "." + ext;
        }
        return fontName;
    }

    /**
     * 获取单例实例。
     *
     * @return BookCSS实例
     */
    public static BookCSS get() {
        return instance;
    }

    /**
     * 获取字体Typeface。
     * <p>
     * 根据字体名称返回对应的Typeface对象，支持系统字体和自定义字体文件。
     *
     * @param fontName 字体名称
     * @return Typeface对象
     */
    public static Typeface getTypeFaceForFont(String fontName) {
        if (TxtUtils.isEmpty(fontName)) {
            return Typeface.DEFAULT;
        }
        try {

            if (fontName.equals(BookCSS.ARIAL)) {
                return Typeface.SANS_SERIF;
            } else if (fontName.equals(BookCSS.COURIER)) {
                return Typeface.MONOSPACE;
            } else if (fontName.equals(BookCSS.TIMES_NEW_ROMAN)) {
                return Typeface.SERIF;
            } else {
                return Typeface.createFromFile(fontName);
            }

        } catch (Exception e) {
            return Typeface.DEFAULT;
        }
    }

    /**
     * 设置MP3书籍路径。
     *
     * @param track 轨道路径
     */
    public void mp3BookPath(String track) {
        final LinkedJSONObject obj = (mp3BookPathJson == null) ? new LinkedJSONObject() : new LinkedJSONObject(mp3BookPathJson);
        obj.put(AppSP.get().lastBookPath, track);

        LOG.d("mp3BookPath-set", AppSP.get().lastBookPath, track);
        mp3BookPathJson = obj.toString();

        trackPathCache = track;
        lastBookPathCache = AppSP.get().lastBookPath;
    }

    /**
     * 获取MP3书籍路径。
     *
     * @return 轨道路径
     */
    public String mp3BookPathGet() {
        if (lastBookPathCache != null && lastBookPathCache.equals(AppSP.get().lastBookPath)) {
            return trackPathCache;
        }
        final LinkedJSONObject obj = (mp3BookPathJson == null) ? new LinkedJSONObject() : new LinkedJSONObject(mp3BookPathJson);
        final String track = obj.optString(AppSP.get().lastBookPath);
        LOG.d("mp3BookPath-get", AppSP.get().lastBookPath, track);
        trackPathCache = track;
        lastBookPathCache = AppSP.get().lastBookPath;

        return track;
    }

    /**
     * 判断当前书籍是否为文本格式。
     *
     * @return 是否为文本格式
     */
    public boolean isTextFormat() {
        try {
            return ExtUtils.isTextFomat(AppSP.get().lastBookPath);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 重置为默认设置。
     * <p>
     * 恢复所有样式参数为默认值，包括字体、颜色、间距等。
     *
     * @param c 上下文
     */
    public void resetToDefault(Context c) {
        textAlign = TEXT_ALIGN_JUSTIFY;

        marginTop = 9;
        marginBottom = 6;

        marginRight = 10;
        marginLeft = 10;

        emptyLine = 1;

        lineHeight12 = 14;
        paragraphHeight = 1;
        textIndent = 10;
        fontWeight = 400;

        fontFolder = AppProfile.syncFontFolder.getPath();
        downlodsPath = new File(AppProfile.DOWNLOADS_DIR, "Librera").getPath();
        displayFontName = DEFAULT_FONT;
        normalFont = DEFAULT_FONT;
        boldFont = DEFAULT_FONT;
        italicFont = DEFAULT_FONT;
        boldItalicFont = DEFAULT_FONT;
        headersFont = DEFAULT_FONT;
        capitalFont = DEFAULT_FONT;

        documentStyle = STYLES_DOC_AND_USER;
        isAutoHypens = true;
        AppSP.get().hypenLang = null;

        linkColorDay = LINK_COLOR_UNIVERSAL;
        linkColorNight = LINK_COLOR_UNIVERSAL;

        linkColorDays = LINKCOLOR_DAYS;
        linkColorNigths = LINKCOLOR_NIGHTS;

        customCSS2 = //
                "code,pre,pre>* {white-space:pre-wrap; font-size:0.8em;}\n" + //
                        ""//
        ;

        LOG.d("BookCSS", "resetToDefault");

    }

    /**
     * 过滤路径列表。
     * <p>
     * 只保留非空且为目录的路径。
     *
     * @param objects 路径列表
     * @return 过滤后的路径列表
     */
    public static List<String> filtered(List<String> objects) {
        if (objects == null || objects.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> res = new ArrayList<>();
        for (String item : objects) {
            if (TxtUtils.isNotEmpty(item) && new File(item).isDirectory()) {
                res.add(item);
            }
        }
        return  res;

    }

    /**
     * 加载配置。
     * <p>
     * 初始化默认设置，从文件读取保存的配置，处理搜索路径。
     *
     * @param c 上下文
     */
    public void load1(Context c) {
        if (c == null) {
            return;
        }
        resetToDefault(c);

        IO.readObj(AppProfile.syncCSS, instance);

        try {
            List<String> filtered = filtered(JsonDB.get(instance.searchPathsJson));
            instance.searchPathsJson = JsonDB.set(filtered);

            LOG.d("searchPaths-all", 1, instance.searchPathsJson,filtered);

            if (TxtUtils.isListEmpty(filtered)) {
                List<String> extFolders = ExtUtils.getAllExternalStorages(c);

                if (!extFolders.contains(Environment.getExternalStorageDirectory().getPath())) {
                    extFolders.add(Environment.getExternalStorageDirectory().getPath());
                }

                instance.searchPathsJson = JsonDB.set(extFolders);
                LOG.d("searchPaths-all", 2, instance.searchPathsJson);
            }
        } catch (Exception e) {
            instance.searchPathsJson = JsonDB.set(List.of(AppProfile.DOWNLOADS_DIR.getPath()));
            LOG.e(e);
        }

    }

    /**
     * 保存配置。
     * <p>
     * 当配置发生变化时，将配置保存到文件。
     *
     * @param c 上下文
     */
    public void save(Context c) {
        if (c == null) {
            return;
        }

        int currentHash = Objects.hashCode(instance, false);
        if (currentHash != instance.hashCode) {
            LOG.d("Objects-save", "SAVE BookCSS");
            hashCode = currentHash;
            IO.writeObj(AppProfile.syncCSS, instance);
        }
    }

    /**
     * 获取字体在列表中的位置。
     *
     * @param fontName 字体名称
     * @return 位置索引
     */
    public int position(String fontName) {
        try {
            List<String> allFonts = getAllFonts();
            return allFonts.indexOf(fontName);
        } catch (Exception e) {
            return 0;
        }

    }

    /**
     * 设置所有字体为同一字体。
     *
     * @param fontName 字体名称
     */
    public void allFonts(String fontName) {
        normalFont = fontName;
    }

    /**
     * 根据字体包重置字体设置。
     * <p>
     * 自动识别字体包中的各种字重（普通、粗体、斜体等）并设置对应字段。
     *
     * @param pack 字体包
     */
    public void resetAll(FontPack pack) {
        LOG.d("resetAll", pack.dispalyName, pack.fontFolder);

        displayFontName = pack.dispalyName;

        normalFont = DEFAULT_FONT;
        boldFont = DEFAULT_FONT;
        italicFont = DEFAULT_FONT;
        boldItalicFont = DEFAULT_FONT;
        headersFont = DEFAULT_FONT;
        capitalFont = DEFAULT_FONT;

        if (displayFontName != null && !displayFontName.contains(".")) {
            normalFont = displayFontName;
            boldFont = displayFontName;
            italicFont = displayFontName;
            boldItalicFont = displayFontName;
            headersFont = displayFontName;
            capitalFont = displayFontName;
            return;
        }

        List<String> all = new ArrayList<String>();

        all.add(TIMES_NEW_ROMAN);
        all.add(ARIAL);
        all.add(COURIER);
        all.add(CHARIS_SIL);

        all.addAll(getAllFontsFromFolder(pack.fontFolder));

        String dispalyName = pack.dispalyName.replace(".ttf", "").replace(".otf", "").trim().toLowerCase(Locale.US);

        for (String fullName : all) {
            String fontName = ExtUtils.getFileName(fullName).replace(".ttf", "").replace(".otf", "").trim().toLowerCase(Locale.US);

            if (fontName.startsWith(dispalyName) || fontName.equals(dispalyName)) {

                if (fontName.equals(dispalyName) || fontName.contains("regular") || fontName.contains("normal") || fontName.contains("light") || fontName.contains("medium") || fontName.endsWith("me")) {
                    if (!fontName.contains("regularitalic")) {
                        normalFont = capitalFont = fullName;
                    }
                } else if (fontName.contains("bolditalic") || fontName.contains("boldoblique") || fontName.contains("boldit") || fontName.contains("boit") || fontName.contains("bold it") || fontName.contains("bold italic")) {
                    boldItalicFont = fullName;

                } else if (fontName.contains("bold") || fontName.endsWith("bo") || fontName.endsWith("bd") || fontName.contains("bolt")) {
                    headersFont = boldFont = fullName;

                } else if (fontName.contains("italic") || fontName.endsWith("it") || fontName.endsWith("oblique")) {
                    italicFont = fullName;
                } else {
                    normalFont = fullName;
                }
            }
        }
        LOG.d("resetAll 2", normalFont);

    }

    /**
     * 获取所有可用字体列表。
     * <p>
     * 从多个目录收集字体文件，包括书籍所在目录、字体文件夹、系统字体等。
     *
     * @return 字体路径列表
     */
    public List<String> getAllFonts() {
        List<String> all = new ArrayList<String>();
        if (AppSP.get().lastBookPath != null) {
            all.addAll(getAllFontsFromFolder(new File(AppSP.get().lastBookPath).getParent()));
        }
        all.add(TIMES_NEW_ROMAN);
        all.add(ARIAL);
        all.add(COURIER);
        all.add(CHARIS_SIL);

        all.addAll(getAllFontsFromFolder(fontFolder));
        all.addAll(getAllFontsFromFolder(new File(Environment.getExternalStorageDirectory(), "fonts").getPath()));
        all.addAll(getAllFontsFromFolder(new File(Environment.getExternalStorageDirectory(), "Fonts").getPath()));
        all.addAll(getAllFontsFromFolder(new File("/system/fonts").getPath()));

        return all;
    }

    /**
     * 获取所有字体包列表。
     * <p>
     * 从多个目录收集字体包，按名称排序。
     *
     * @return 字体包列表
     */
    public List<FontPack> getAllFontsPacks() {
        List<FontPack> all = new ArrayList<FontPack>();

        all.addAll(getAllFontsFiltered(fontFolder));
        all.addAll(getAllFontsFiltered(new File(Environment.getExternalStorageDirectory(), "fonts").getPath()));
        all.addAll(getAllFontsFiltered(new File(Environment.getExternalStorageDirectory(), "Fonts").getPath()));
        all.addAll(getAllFontsFiltered(new File("/system/fonts").getPath(), true));

        Collections.sort(all, new Comparator<FontPack>() {
            @Override
            public int compare(FontPack o1, FontPack o2) {
                return o1.dispalyName.compareTo(o2.dispalyName);
            }
        });

        all.add(0, new FontPack(COURIER));
        all.add(0, new FontPack(ARIAL));
        all.add(0, new FontPack(TIMES_NEW_ROMAN));
        all.add(0, new FontPack(CHARIS_SIL));
        if (AppSP.get().lastBookPath != null) {
            all.addAll(0, getAllFontsFiltered(new File(AppSP.get().lastBookPath).getParent()));
        }

        return all;
    }

    /**
     * 获取过滤后的字体包集合。
     *
     * @param path 路径
     * @return 字体包集合
     */
    private Collection<FontPack> getAllFontsFiltered(String path) {
        return getAllFontsFiltered(path, false);
    }

    /**
     * 获取过滤后的字体包集合。
     * <p>
     * 支持排除特定字体（如Noto、Samsung等）。
     *
     * @param path 路径
     * @param excludeNoto 是否排除Noto字体
     * @return 字体包集合
     */
    private Collection<FontPack> getAllFontsFiltered(String path, final boolean excludeNoto) {
        if (TxtUtils.isNotEmpty(path) && new File(path).isDirectory()) {
            File file = new File(path);
            String[] list = file.list(new FilenameFilter() {

                @Override
                public boolean accept(File dir, String name) {
                    name = name.toLowerCase(Locale.US);
                    LOG.d("name-accept", name);
                    if (excludeNoto) {
                        if (name.startsWith("noto")) {
                            return false;
                        } else if (name.startsWith("sec")) {
                            return false;
                        } else if (name.startsWith("samsung")) {
                            return false;
                        } else if (name.startsWith("clock")) {
                            return false;
                        }
                    }

                    for (
                            String ext : fontExts) {
                        if (name.endsWith(ext)) {
                            return true;
                        }
                    }
                    return false;
                }
            });
            if (list != null && list.length >= 1) {
                List<FontPack> filtered = new ArrayList<FontPack>();

                for (String fontName : list) {

                    String fontNameDisplay = filterFontName(fontName);

                    FontPack e = new FontPack(fontNameDisplay, path);

                    if (!filtered.contains(e)) {
                        e.normalFont = path + "/" + fontName;
                        for (String font : list) {
                            String fontInit = font;

                            font = font.replace(".ttf", "").replace(".otf", "").trim().toLowerCase(Locale.US);
                            fontNameDisplay = fontNameDisplay.replace(".ttf", "").replace(".otf", "").trim().toLowerCase(Locale.US);

                            if (font.startsWith(fontNameDisplay)) {
                                if (font.equals(fontNameDisplay) || font.contains("regular") || font.contains("normal") || font.contains("light") || font.contains("medium") || font.endsWith("me")) {
                                    e.normalFont = path + "/" + fontInit;
                                    break;
                                }
                            }

                        }

                        filtered.add(e);
                    }
                }

                Collections.sort(filtered, new Comparator<FontPack>() {
                    @Override
                    public int compare(FontPack o1, FontPack o2) {
                        return o1.dispalyName.toLowerCase(Locale.US).compareTo(o2.dispalyName.toLowerCase(Locale.US));
                    }

                });
                return filtered;
            }
        }
        return Collections.EMPTY_LIST;
    }

    /**
     * 从文件夹获取所有字体文件。
     *
     * @param path 文件夹路径
     * @return 字体文件路径集合
     */
    private Collection<String> getAllFontsFromFolder(String path) {
        try {
            if (TxtUtils.isNotEmpty(path) && new File(path).isDirectory()) {
                File file = new File(path);
                String[] list = file.list(new FilenameFilter() {

                    @Override
                    public boolean accept(File dir, String name) {
                        name = name.toLowerCase(Locale.US);
                        for (String ext : fontExts) {
                            if (name.endsWith(ext)) {
                                return true;
                            }
                        }
                        return false;
                    }
                });
                if (list != null && list.length >= 1) {
                    List<String> filtered = new ArrayList<String>();

                    for (String fontName : list) {
                        filtered.add(path + "/" + fontName);
                    }

                    Collections.sort(filtered, new Comparator<String>() {
                        @Override
                        public int compare(String o1, String o2) {
                            return o1.toLowerCase(Locale.US).compareTo(o2.toLowerCase(Locale.US));
                        }

                    });
                    return filtered;
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        return Collections.EMPTY_LIST;
    }

    /**
     * 判断是否为URL字体。
     * <p>
     * 检查字体名称是否以字体扩展名结尾。
     *
     * @param name 字体名称
     * @return 是否为URL字体
     */
    public boolean isUrlFont(String name) {
        if (TxtUtils.isEmpty(name)) {
            return false;
        }
        name = name.toLowerCase(Locale.US);
        for (String ext : fontExts) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将值转换为em单位。
     *
     * @param value 值
     * @return em单位字符串
     */
    public String em(int value) {
        if (value == 0) {
            return "0px";
        }
        float em = (float) value / 10;
        return "" + em + "em";
    }

    /**
     * 获取文本对齐常量字符串。
     *
     * @param id 对齐方式ID
     * @return 对齐方式字符串
     */
    public String getTextAlignConst(int id) {
        if (id == TEXT_ALIGN_JUSTIFY) {
            return "justify";
        }
        if (id == TEXT_ALIGN_LEFT) {
            return "left";
        }
        if (id == TEXT_ALIGN_RIGHT) {
            return "right";
        }
        if (id == TEXT_ALIGN_CENTER) {
            return "center";
        }
        return "initial";
    }

    /**
     * 转换为CSS字符串。
     *
     * @return CSS字符串
     */
    public String toCssString() {
        return toCssString("");
    }

    /**
     * 添加!important标记。
     * <p>
     * 根据样式模式决定是否添加!important。
     *
     * @param input 输入字符串
     * @return 添加!important后的字符串
     */
    public String important(String input) {
        if (documentStyle == STYLES_ONLY_USER) {
            return input.replace(";", " !important;");
        }
        return input;

    }

    /**
     * 转换为CSS字符串。
     * <p>
     * 根据当前样式配置生成完整的CSS样式表，包括页面边距、字体、颜色等。
     *
     * @param path 路径
     * @return CSS字符串
     */
    public String toCssString(String path) {

        lineHeight12 = Math.max(10, lineHeight12);

        StringBuilder builder = new StringBuilder();

        File cssFile = new File(AppProfile.SYNC_FOLDER_DEVICE_PROFILE, userStyleCss);
        if (!cssFile.exists()) {
            try {
                String css = IO.readStringFromAsset("app-Librera.css");
                LOG.d("BookCSS-from asset");
                builder.append(css);
            } catch (Exception e) {
                LOG.e(e);
            }
        } else {
            String css = IO.readString(cssFile);
            LOG.d("BookCSS-from file", cssFile);
            builder.append(css);
        }

        String backgroundColor = MagicHelper.colorToString(MagicHelper.getBgColor());
        String textColor = MagicHelper.colorToString(MagicHelper.getTextColor());

        builder.append("documentStyle" + documentStyle + "{}");
        builder.append("isAutoHypens1" + isAutoHypens + AppSP.get().hypenLang + "{}");

        // PAGE BEGIN
        builder.append("@page {");
        builder.append(String.format("margin-top:%s !important;", em(marginTop * 2)));
        builder.append(String.format("margin-right:%s !important;", em(marginRight * 2)));
        builder.append(String.format("margin-bottom:%s !important;", em((marginBottom - 1) * 2)));
        builder.append(String.format("margin-left:%s !important;", em(marginLeft * 2)));
        builder.append("}");
        // PAGE END

        builder.append(String.format("empty-line {padding:%s;}", em(emptyLine)));

        builder.append("t {color:" + (AppState.get().isDayNotInvert ? linkColorDay : linkColorNight) + " !important;}");

        builder.append("svg {");
        builder.append("margin:0 !important;");
        builder.append("padding:0 !important;");
        builder.append("}");

        if (documentStyle == STYLES_DOC_AND_USER || documentStyle == STYLES_ONLY_USER) {

            builder.append("a {color:" + (AppState.get().isDayNotInvert ? linkColorDay : linkColorNight) + " !important;}");
            //apply settings

            if (paragraphHeight > 0) {// bug is here
                builder.append("div, p {");
                builder.append(important(String.format("margin:%s 0;", em(paragraphHeight * 2))));
                builder.append("}");
            }


            if (!AppState.get().isDayNotInvert) {
                builder.append("h1, h2, h3, h4, h5, h6, hr{");
                builder.append(String.format("background-color:%s !important;", backgroundColor));
                builder.append(String.format("color:%s !important;", textColor));
                builder.append("}");
            }

            // <P> begin
            builder.append("body, div, p, span{");
            builder.append(String.format("font-size:medium !important;"));

            if (AppState.get().isDayNotInvert) {
                if (!"#FFFFFF".equals(backgroundColor)) {
                    builder.append(important(String.format("background-color:%s;", backgroundColor)));
                }
                if (!"#000000".equals(textColor)) {
                    builder.append(important(String.format("color:%s;", textColor)));
                }

            } else {
                //Important in the night mode
                builder.append(String.format("background-color:%s !important;", backgroundColor));
                builder.append(String.format("color:%s !important;", textColor));
            }
            //always important
            builder.append(String.format("line-height:%s !important;", em(lineHeight12)));
            builder.append(String.format("text-indent:%s !important;", em(textIndent)));
            builder.append(String.format("text-align:%s;", getTextAlignConst(textAlign)));

            if (isUrlFont(normalFont)) {
                builder.append(important("font-family:'my';"));
            } else {
                builder.append(important("font-family:'" + normalFont + "';"));
            }

            builder.append("}");
            // </P> end
            builder.append("sub, sup{");
            builder.append(String.format("line-height:%s !important;", em(lineHeight12)));
            builder.append("}");


            builder.append("div {");
            builder.append("margin-right:0 !important;");
            builder.append("margin-left:0 !important;");
            builder.append("padding-left:0 !important;");
            builder.append("padding-right:0 !important;");
            builder.append("}");

            // FONTS BEGIN
            if (isUrlFont(normalFont)) {
                builder.append("@font-face {font-family:'my'; src:url('" + normalFont + "'); font-weight:normal; font-style:normal;}");
            }
            if (isUrlFont(boldFont)) {
                builder.append("@font-face {font-family:'my'; src:url('" + boldFont + "'); font-weight:bold; font-style:normal;}");
            } else {
                builder.append("b {font-family:'" + boldFont + "';font-weight:bold;}");
            }

            if (isUrlFont(italicFont)) {
                builder.append("@font-face {font-family:'my'; src:url('" + italicFont + "'); font-weight:normal; font-style:italic;}");
            } else {
                builder.append("i {font-family:'" + italicFont + "'; font-style:italic;}");
            }

            if (isUrlFont(boldItalicFont)) {
                builder.append("@font-face {font-family:'my'; src:url('" + boldItalicFont + "'); font-weight:bold; font-style:italic;}");
            }

            if (isUrlFont(headersFont)) {
                builder.append("@font-face {font-family:'myHeader'; src:url('" + headersFont + "');}");
                builder.append(important("h1,h2,h3,h4,h5,h6 {font-weight:normal; font-family:'myHeader';}"));
                builder.append(important("title,title>p,title>p>strong {font-weight:normal; font-family:'myHeader';}"));
                builder.append(important("subtitle {font-weight:normal; font-family:'myHeader';}"));

            } else {
                builder.append(important("h1,h2,h3,h4,h5,h6 {font-weight:bold; font-family:'" + headersFont + "';}"));
                builder.append(important("title,title>p,title>p>strong {font-weight:bold; font-family:'" + headersFont + "';}"));
                builder.append(important("subtitle, subtitle>p {font-weight:bold; font-family:'" + headersFont + "';}"));
            }

            builder.append(customCSS2.replace("\n", ""));

        }
        //FB2 Capital letter for all styles
        if (isCapitalLetter) {
            if (isUrlFont(capitalFont)) {
                builder.append("@font-face {font-family:myCapital; src:url('" + capitalFont + "') ; font-weight:normal; font-style:normal;}");
            }
            builder.append("letter{");
            if (isUrlFont(capitalFont)) {
                builder.append("font-family:myCapital !important;");
            } else {
                builder.append("font-family:" + capitalFont + " !important;");
            }

            builder.append(String.format("font-size:%s;", em(capitalLetterSize)));

            if (capitalLetterColor.equals("#000000") && !AppState.get().isDayNotInvert) {
                builder.append(String.format("color:%s;", textColor));
            } else if (capitalLetterColor.equals("#FFFFFF") && AppState.get().isDayNotInvert) {
                builder.append(String.format("color:%s;", textColor));
            } else {
                builder.append(String.format("color:%s;", capitalLetterColor));
            }

            builder.append("}");
        }

        String result = builder.toString();
        LOG.d("BookCSS", result);
        return result;
    }

    /**
     * 获取标题字体族名称。
     * <p>
     * 如果是URL字体，返回"myHeader"，否则返回原字体名称。
     *
     * @param fontName 字体名称
     * @return 字体族名称
     */
    public String getHeaderFontFamily(String fontName) {
        return isUrlFont(fontName) ? "myHeader" : fontName;
    }

    /**
     * 检测书籍语言。
     * <p>
     * 根据书籍元数据或共享书籍设置确定连字语言。
     *
     * @param bookPath 书籍路径
     */
    public void detectLang(String bookPath) {

        if (AppState.get().isDefaultHyphenLanguage) {
            AppSP.get().hypenLang = AppState.get().defaultHyphenLanguageCode;
            LOG.d("set defaultHyphenLanguageCode", AppSP.get().hypenLang);
            return;
        }

        FileMeta meta = AppDB.get().load(bookPath);
        if (meta == null) {
            meta = FileMetaCore.createMetaIfNeed(bookPath, false);
        }
        AppSP.get().hypenLang = meta.getLang();
        LOG.d("detectLang", bookPath, AppSP.get().hypenLang);

        if (TxtUtils.isEmpty(AppSP.get().hypenLang)) {
            final AppBook load = SharedBooks.load(bookPath);
            if (load != null) {
                AppSP.get().hypenLang = load.ln;
            }
        }
    }

    /**
     * 字体包类。
     * <p>
     * 封装一组相关字体，包括普通、粗体、斜体等多种字重。
     */
    public static class FontPack {
        /** 显示名称 */
        public String dispalyName = "";
        /** 字体文件夹 */
        public String fontFolder;

        /** 普通字体 */
        public String normalFont;
        /** 粗体字体 */
        public String boldFont;
        /** 斜体字体 */
        public String italicFont;
        /** 粗斜体字体 */
        public String boldItalicFont;
        /** 标题字体 */
        public String headersFont;
        /** 首字下沉字体 */
        public String capitalFont;

        /**
         * 构造函数。
         *
         * @param name 字体名称
         * @param path 字体路径
         */
        public FontPack(String name, String path) {
            fontFolder = path;
            dispalyName = name;
            normalFont = path + "/" + name;
            boldFont = path + "/" + name;
            italicFont = path + "/" + name;
            boldItalicFont = path + "/" + name;
            headersFont = path + "/" + name;
            capitalFont = path + "/" + name;
        }

        /**
         * 构造函数（仅名称）。
         *
         * @param name 字体名称
         */
        public FontPack(String name) {
            dispalyName = name;
            normalFont = name;
            boldFont = name;
            italicFont = name;
            boldItalicFont = name;
            headersFont = name;
            capitalFont = name;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof FontPack && dispalyName.equals(((FontPack) obj).dispalyName);
        }

    }

}
