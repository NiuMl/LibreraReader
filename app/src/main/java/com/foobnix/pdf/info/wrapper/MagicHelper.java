package com.foobnix.pdf.info.wrapper;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.foobnix.LibreraApp;
import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.Objects;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.AppsConfig;
import com.foobnix.pdf.info.IMG;
import com.foobnix.pdf.info.model.BookCSS;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * 颜色和图像处理辅助类。
 * <p>
 * 提供颜色转换、图像缩放、背景处理、对比度调整等功能。
 * 支持日间/夜间模式的颜色适配和图像魔术效果。
 */
public class MagicHelper {

    /** 亮度阈值 */
    private static final int LIGHT_VALUE = 450;
    /** 是否需要亮度/对比度调整 */
    public static volatile boolean isNeedBC = true;

    /**
     * 计算配置哈希值。
     * <p>
     * 根据AppState和BookCSS的哈希值生成组合哈希。
     *
     * @return 哈希值
     */
    public static int hash() {
        StringBuilder builder = new StringBuilder();

        builder.append(Objects.hashCode(AppState.get()));
        builder.append(Objects.hashCode(BookCSS.get()));

        return builder.toString()
                      .hashCode();

    }

    /**
     * 获取更深的颜色。
     *
     * @param color 原始颜色
     * @return 加深后的颜色
     */
    public static int darkerColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] -= 0.1f;
        return Color.HSVToColor(hsv);
    }

    /**
     * 调整颜色亮度。
     * <p>
     * 值为负时加深颜色，值为正时变亮颜色。
     *
     * @param color 原始颜色
     * @param value 亮度调整值（负值加深，正值变亮）
     * @return 调整后的颜色
     */
    public static int otherColor(int color, float value) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] -= value;
        return Color.HSVToColor(hsv);
    }

    /**
     * 缩放并居中裁剪图像（字节数组版本）。
     *
     * @param source 源图像字节数组
     * @param w 目标宽度
     * @param h 目标高度
     * @return 处理后的图像字节流
     */
    public static ByteArrayInputStream scaleCenterCrop(byte[] source, int w, int h) {
        Bitmap decodeStream = BitmapFactory.decodeStream(new ByteArrayInputStream(source));

        Bitmap scaleCenterCrop = scaleCenterCrop(decodeStream, h, w, true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaleCenterCrop.compress(CompressFormat.PNG, 95, out);

        scaleCenterCrop.recycle();

        return new ByteArrayInputStream(out.toByteArray());

    }

    /**
     * 缩放并居中裁剪图像（Bitmap版本）。
     *
     * @param source 源Bitmap
     * @param newHeight 目标高度
     * @param newWidth 目标宽度
     * @param withEffect 是否应用书籍效果
     * @return 处理后的Bitmap
     */
    public static Bitmap scaleCenterCrop(Bitmap source, int newHeight, int newWidth, boolean withEffect) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();

        LOG.d("scaleCenterCrop", sourceWidth, sourceHeight, newHeight, newWidth);

        LOG.d("RATIO", (float) sourceHeight / sourceWidth);

        // Compute the scaling factors to fit the new height and width,
        // respectively.
        // To cover the final image, the final scaling will be the bigger
        // of these two.
        float xScale = (float) newWidth / sourceWidth;
        float yScale = (float) newHeight / sourceHeight;

        float scale = Math.max(xScale, yScale);

        // Now get the size of the source bitmap when scaled
        float scaledWidth = scale * sourceWidth;
        float scaledHeight = scale * sourceHeight;

        // Let's find out the upper left coordinates if the scaled bitmap
        // should be centered in the new size give by the parameters
        float left = (newWidth - scaledWidth) / 2;
        float top = (newHeight - scaledHeight) / 2;

        // The target rectangle for the new, scaled version of the source
        // bitmap
        // will now
        // be
        RectF targetRect = new RectF(left, top, left + scaledWidth, top + scaledHeight);

        // Finally, we create a new bitmap of the specified size and draw
        // our
        // new,
        // scaled bitmap onto it.
        Bitmap dest = Bitmap.createBitmap(newWidth, newHeight, Config.ARGB_4444);
        Canvas canvas = new Canvas(dest);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);

        canvas.drawBitmap(source, null, targetRect, paint);

        if (withEffect) {
            applyBookEffect(dest);
        }

        source.recycle();
        source = null;

        return dest;
    }

    /**
     * 应用书籍封面效果。
     *
     * @param dest 目标Bitmap
     */
    public static void applyBookEffect(Bitmap dest) {
        if (AppState.get().isBookCoverEffect) {
            Canvas canvas = new Canvas(dest);
            IMG.bookBGNoMark.setBounds(0, 0, dest.getWidth(), dest.getHeight());
            IMG.bookBGNoMark.draw(canvas);
        }
    }

    /**
     * 应用带Logo的书籍封面效果。
     *
     * @param dest 目标Bitmap
     */
    public static void applyBookEffectWithLogo(Bitmap dest) {
        try {
            Canvas canvas = new Canvas(dest);
            IMG.bookBGWithMark.setBounds(0, 0, dest.getWidth(), dest.getHeight());
            IMG.bookBGWithMark.draw(canvas);
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /**
     * 判断是否需要颜色魔术效果。
     * <p>
     * 检查日间/夜间模式下颜色是否与默认值不同。
     *
     * @return 是否需要魔术效果
     */
    public static boolean isNeedMagic() {

        boolean isDay = AppState.get().isDayNotInvert && //
                (AppState.get().colorDayBg != AppState.COLOR_WHITE || //
                        AppState.get().colorDayText != AppState.COLOR_BLACK); //

        boolean isNigth = !AppState.get().isDayNotInvert && //
                (AppState.get().colorNigthBg != AppState.COLOR_BLACK || //
                        AppState.get().colorNigthText != AppState.COLOR_WHITE); //

        return isDay || isNigth;
    }

    /**
     * 判断是否需要简单的颜色魔术效果（仅日间模式）。
     *
     * @return 是否需要魔术效果
     */
    public static boolean isNeedMagicSimple() {
        boolean isDay = AppState.get().isDayNotInvert && //
                (AppState.get().colorDayBg != AppState.COLOR_WHITE || //
                        AppState.get().colorDayText != AppState.COLOR_BLACK); //
        return isDay;
    }

    /**
     * 判断是否需要书籍背景图片。
     *
     * @return 是否需要背景图片
     */
    public static boolean isNeedBookBackgroundImage() {
        return (!AppState.get().isDayNotInvert && AppState.get().isUseBGImageNight) || (AppState.get().isDayNotInvert && AppState.get().isUseBGImageDay);
    }

    /**
     * 获取当前模式的背景图片路径。
     *
     * @return 背景图片路径
     */
    public static String getImagePath() {
        return !AppState.get().isDayNotInvert ? AppState.get().bgImageNightPath : AppState.get().bgImageDayPath;
    }

    /**
     * 获取指定模式的背景图片路径。
     *
     * @param isDay 是否日间模式
     * @return 背景图片路径
     */
    public static String getImagePath(boolean isDay) {
        return !isDay ? AppState.get().bgImageNightPath : AppState.get().bgImageDayPath;
    }

    /**
     * 获取当前模式的背景透明度。
     *
     * @return 透明度值
     */
    public static int getTransparencyInt() {
        return AppState.get().isDayNotInvert ? AppState.get().bgImageDayTransparency :
                AppState.get().bgImageNightTransparency;
    }

    /** 默认背景图片1 */
    public static final String IMAGE_BG_1 = "bg/bg1.jpg";
    /** 默认背景图片2 */
    public static final String IMAGE_BG_2 = "bg/bg2.jpg";
    /** 默认背景图片3 */
    public static final String IMAGE_BG_3 = "bg/bg3.jpg";

    /**
     * 更新TextView的背景。
     *
     * @param textView TextView
     * @param transparency 透明度
     * @param path 背景图片路径
     * @return 更新后的Bitmap
     */
    public static Bitmap updateTextViewBG(TextView textView, int transparency, String path) {
        textView.setDrawingCacheEnabled(true);
        textView.buildDrawingCache(true);

        Bitmap drawingCache = textView.getDrawingCache();
        if (drawingCache == null) {
            return null;
        }

        Bitmap updates = null;
        try {
            updates = updateWithBackground(drawingCache, transparency, loadBitmap(path));
        } catch (Exception e) {
            LOG.e(e);
        }

        textView.setDrawingCacheEnabled(false);
        return updates;

    }

    /**
     * 获取背景图片Drawable。
     *
     * @param name 图片名称
     * @return Drawable对象
     */
    public static Drawable getBgImageDrawable(String name) {
        final BitmapDrawable background = new BitmapDrawable(Resources.getSystem(), loadBitmap(name));
        background.setAlpha(AppState.get().bgImageDayTransparency);
        return background;
    }

    /**
     * 从Assets加载Bitmap。
     *
     * @param context 上下文
     * @param filePath 文件路径
     * @return Bitmap对象
     */
    public static Bitmap getBitmapFromAsset(Context context, String filePath) {
        try {
            return BitmapFactory.decodeStream(context.getAssets()
                                                     .open(filePath));
        } catch (IOException e) {
            LOG.e(e);
        }
        return null;
    }

    /**
     * 获取日间模式背景图片Drawable。
     *
     * @param withAlpa 是否使用透明度
     * @return Drawable对象
     */
    public static Drawable getBgImageDayDrawable(boolean withAlpa) {
        final Bitmap bitmap = updateWithBackground(loadBitmap(AppState.get().bgImageDayPath),
                withAlpa ? AppState.get().bgImageDayTransparency : AppState.DAY_TRANSPARENCY, Color.WHITE);
        if (bitmap == null) {
            return new ColorDrawable(Color.WHITE);
        }
        return new BitmapDrawable(Resources.getSystem(), bitmap);
    }

    /**
     * 获取夜间模式背景图片Drawable。
     *
     * @param withAlpa 是否使用透明度
     * @return Drawable对象
     */
    public static Drawable getBgImageNightDrawable(boolean withAlpa) {
        final Bitmap bitmap = updateWithBackground(loadBitmap(AppState.get().bgImageNightPath),
                withAlpa ? AppState.get().bgImageNightTransparency : AppState.NIGHT_TRANSPARENCY, Color.BLACK);
        if (bitmap == null) {
            return new ColorDrawable(Color.BLACK);
        }
        return new BitmapDrawable(Resources.getSystem(), bitmap);
    }

    /** 背景图片缓存 */
    static Bitmap bg1;
    /** 背景图片路径缓存 */
    static String bgPath = getImagePath();
    /** 主颜色 */
    static int mainColor = Color.TRANSPARENT;

    /**
     * 获取背景图片（带缓存）。
     *
     * @return 背景图片Bitmap
     */
    public static Bitmap getBackgroundImage() {
        if (bg1 != null && getImagePath().equals(bgPath)) {
            return bg1;
        }
        bgPath = getImagePath();
        bg1 = loadBitmap(bgPath);
        mainColor = getDominantColor(bg1);
        return bg1;
    }

    /**
     * 加载Bitmap。
     * <p>
     * 支持从文件系统和Assets加载，大文件自动缩放。
     *
     * @param name 文件路径或Assets路径
     * @return Bitmap对象
     */
    public static Bitmap loadBitmap(String name) {
        LOG.d("loadBitmap", name);
        if (TxtUtils.isEmpty(name)) {
            return loadBitmap(MagicHelper.IMAGE_BG_1);
        }

        if (name.startsWith("/") && !new File(name).exists()) {
            return loadBitmap(MagicHelper.IMAGE_BG_1);
        }

        try {

            if (name.startsWith("/")) {
                //BitmapFactory.Options opt = new BitmapFactory.Options();
                //opt.inPreferredConfig = AppsConfig.CURRENT_BITMAP_ARGB;
                return decodeScaledBitmap(name);
            }

            InputStream oldBook = LibreraApp.context.getAssets()
                                                    .open(name);
            Bitmap decodeStream = BitmapFactory.decodeStream(oldBook);
            Bitmap res = decodeStream.copy(AppsConfig.CURRENT_BITMAP_ARGB, false);
            decodeStream.recycle();
            return res;
        } catch (Exception e) {
            LOG.e(e);
            return loadBitmap(MagicHelper.IMAGE_BG_1);
        }
    }

    /**
     * 解码并缩放Bitmap（大文件优化）。
     *
     * @param name 文件路径
     * @return Bitmap对象
     */
    public static Bitmap decodeScaledBitmap(String name) {
        File file = new File(name);
        long fileSizeInBytes = file.length();
        long limitInBytes = 1024 * 1024; // 1 MB

        BitmapFactory.Options opt = new BitmapFactory.Options();

        if (fileSizeInBytes > limitInBytes) {
            // 1. First, decode with inJustDecodeBounds = true to check dimensions
            opt.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(name, opt);

            // 2. Calculate inSampleSize (must be a power of 2, e.g., 2, 4, 8)
            // We calculate how much larger the file is than the limit
            opt.inSampleSize = calculateInSampleSize(fileSizeInBytes, limitInBytes);

            // 3. Decode again with inJustDecodeBounds = false
            opt.inJustDecodeBounds = false;
        }

        opt.inPreferredConfig = AppsConfig.CURRENT_BITMAP_ARGB;
        return BitmapFactory.decodeFile(name, opt);
    }

    /**
     * 计算采样大小。
     *
     * @param fileSize 文件大小
     * @param limit 限制大小
     * @return 采样大小
     */
    private static int calculateInSampleSize(long fileSize, long limit) {
        int inSampleSize = 1;
        if (fileSize > limit) {
            // We estimate scaling. Since area scales quadratically,
            // a sample size of 2 reduces memory by 4x.
            float ratio = (float) fileSize / limit;
            inSampleSize = (int) Math.ceil(Math.sqrt(ratio));
        }
        // Standard practice: ensure it's a power of 2 for better performance
        return Integer.highestOneBit(inSampleSize);
    }

    /**
     * 使用当前配置更新背景。
     *
     * @param bitmap 源Bitmap
     * @return 更新后的Bitmap
     */
    public static Bitmap updateWithBackground(Bitmap bitmap) {
        return updateWithBackground(bitmap, getTransparencyInt(), getBackgroundImage());
    }

    /**
     * 使用指定背景图片更新Bitmap。
     *
     * @param bitmap 源Bitmap
     * @param alpha 透明度
     * @param bgBitmap 背景Bitmap
     * @return 更新后的Bitmap
     */
    public static Bitmap updateWithBackground(Bitmap bitmap, int alpha, Bitmap bgBitmap) {
        Paint p = new Paint();
        p.setAlpha(alpha);

        Bitmap result = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
        Canvas canvas = new Canvas(result);

        // for PDF only
        Matrix m = new Matrix();
        float sx = (float) bitmap.getWidth() / bgBitmap.getWidth();
        float sy = (float) bitmap.getHeight() / bgBitmap.getHeight();
        m.setScale(sx, sy);
        canvas.drawBitmap(bgBitmap, m, new Paint());
        canvas.drawBitmap(bitmap, 0, 0, p);

        bitmap.recycle();
        bitmap = null;
        return result;
    }

    /**
     * 使用自定义背景更新Bitmap。
     *
     * @param bitmap 源Bitmap
     * @param alpha 透明度
     * @param bgBitmap 背景Bitmap
     * @return 更新后的Bitmap
     */
    public static Bitmap updateWithBackground_customBG(Bitmap bitmap, int alpha, Bitmap bgBitmap) {
        Paint p = new Paint();
        p.setAlpha(255 - alpha);

        float k1 = (float) bitmap.getHeight() / bitmap.getWidth();
        float k2 = (float) Dips.screenHeight() / Dips.screenWidth();

        float k = Math.max(k1, k2);

        Bitmap result = Bitmap.createBitmap(bitmap.getWidth(), (int) (bitmap.getWidth() * k), bitmap.getConfig());
        Canvas canvas = new Canvas(result);
        canvas.drawColor(Color.WHITE);

        // for PDF only
        Matrix m = new Matrix();
        float sx = (float) result.getWidth() / bgBitmap.getWidth();
        float sy = (float) result.getHeight() / bgBitmap.getHeight();
        m.setScale(sx, sy);
        int h = (result.getHeight() - bitmap.getHeight()) / 2;

        canvas.drawBitmap(bitmap, 0, h, new Paint());
        canvas.drawBitmap(bgBitmap, m, p);

        Paint p1 = new Paint();
        p1.setColor(Color.WHITE);
        p1.setAlpha(alpha);
        // canvas.drawRect(0, 0, result.getWidth(), h, p1);
        // canvas.drawRect(0, result.getHeight() - h, result.getWidth(),
        // result.getHeight(), p1);

        bitmap.recycle();
        bitmap = null;
        return result;
    }

    /**
     * 使用指定颜色更新背景。
     *
     * @param bitmap 源Bitmap
     * @param alpha 透明度
     * @param color 背景颜色
     * @return 更新后的Bitmap
     */
    public static Bitmap updateWithBackground(Bitmap bitmap, int alpha, int color) {
        if (bitmap == null) {
            return null;
        }

        Paint p = new Paint();
        p.setAlpha(alpha);

        Bitmap result = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
        Canvas canvas = new Canvas(result);

        canvas.drawColor(color);
        canvas.drawBitmap(bitmap, 0, 0, p);
        return result;
    }

    /**
     * 获取当前模式的文本颜色。
     *
     * @return 文本颜色
     */
    public static int getTextColor() {
        return AppState.get().isDayNotInvert ? AppState.get().colorDayText : AppState.get().colorNigthText;
    }

    /**
     * 获取当前模式的背景颜色。
     *
     * @return 背景颜色
     */
    public static int getBgColor() {
        if (AppState.get().isDayNotInvert && AppState.get().isUseBGImageDay) {
            // return Color.parseColor("#EFEBDE");
        }
        if (!AppState.get().isDayNotInvert && AppState.get().isUseBGImageNight) {
            // return Color.parseColor("#52493A");
        }

        return AppState.get().isDayNotInvert ? AppState.get().colorDayBg : AppState.get().colorNigthBg;
    }

    /**
     * 获取当前模式的前景颜色。
     *
     * @return 前景颜色
     */
    public static int getForegroundColor() {
        return AppState.get().isDayNotInvert ? AppState.get().colorDayForeground : AppState.get().colorNigthForeground;
    }

    /**
     * 获取颜色的HSV值。
     *
     * @param color 颜色值
     * @return HSV数组
     */
    public static float[] getHSV(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        return hsv;
    }

//   public static int alpha1(int alpha,int colorInt ) {
// Color color = Color.valueOf(colorInt);
//  return Color.argb(alpha,color.red(),color.green(),color.blue());
//  }

    /**
     * 设置颜色的透明度。
     *
     * @param alpha 透明度
     * @param colorInt 颜色值
     * @return 设置透明度后的颜色
     */
    public static int alpha(int alpha, int colorInt) {
        int red = (colorInt >> 16) & 0xFF;
        int green = (colorInt >> 8) & 0xFF;
        int blue = colorInt & 0xFF;
        return android.graphics.Color.argb(alpha, red, green, blue);
    }

    /**
     * 计算颜色亮度。
     *
     * @param color 颜色值
     * @return 亮度值
     */
    private static float lightness(int color) {
        int R = Color.red(color);
        int G = Color.green(color);
        int B = Color.blue(color);

        return (float) (0.2126 * R + 0.7152 * G + 0.0722 * B);
    }

    /** 颜色缓存输入 */
    static int colorCacheInput = Color.TRANSPARENT;
    /** 颜色缓存输出 */
    static int colorCache = Color.TRANSPARENT;

    /**
     * 获取更亮的颜色（带缓存）。
     *
     * @param color 原始颜色
     * @return 变亮后的颜色
     */
    public static int ligtherColor(int color) {
        if (color == colorCacheInput) {
            return colorCache;
        }
        if (color == Color.WHITE) {
            return Color.WHITE;
        }
        if (color == Color.BLACK) {
            return Color.BLACK;
        }

        int r1 = Color.red(color);
        int g1 = Color.green(color);
        int b1 = Color.blue(color);
        int k = 5;
        int res = Color.rgb(r1 > 128 ? r1 - k : r1 + k, g1 > 128 ? g1 - k : g1 + k, b1 > 128 ? b1 - k : b1 + k);
        colorCacheInput = color;
        colorCache = res;
        return res;

    }

    /** 魔术颜色 */
    public static final int myColorIng = Color.BLUE;

    /** 魔术颜色R分量 */
    public static final int addR = Color.red(myColorIng);
    /** 魔术颜色G分量 */
    public static final int addG = Color.green(myColorIng);
    /** 魔术颜色B分量 */
    public static final int addB = Color.blue(myColorIng);

    /** 魔术颜色HSL值 */
    public static float[] myColorHSL = new float[3];

    static {
        ColorUtils.colorToHSL(myColorIng, myColorHSL);
    }

    /**
     * 更新Bitmap的颜色魔术效果。
     *
     * @param bitmap Bitmap
     */
    public void udpateColorsMagic(Bitmap bitmap) {
        if (!isNeedMagic()) {
            return;
        }
        int pixels[] = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        MagicHelper.udpateColorsMagic(pixels);
        bitmap.setPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
    }

    /**
     * 将指定颜色替换为目标颜色。
     *
     * @param from 源颜色
     * @param to 目标颜色
     * @param allpixels 像素数组
     * @return 更新后的像素数组
     */
    public static int[] updatePixelsFromTo(int from, int to, int[] allpixels) {
        for (int i = 0; i < allpixels.length; i++) {
            if (allpixels[i] == from) {
                allpixels[i] = to;
                continue;
            }
        }
        return allpixels;
    }

    /**
     * 获取文本或图标颜色。
     *
     * @return 文本/图标颜色
     */
    public static int getTextOrIconColor() {
        int textColor = AppState.get().isUiTextColor ? AppState.get().uiTextColor : Color.WHITE;

        if (AppState.get().uiTextColor == AppState.get().tintColor) {
            textColor = Color.WHITE;
        }
        if (AppState.get().appTheme == AppState.THEME_DARK_OLED && !AppState.get().isUiTextColor) {
            textColor = Color.WHITE;
        }
        return textColor;
    }

    /**
     * 判断颜色是否为亮色。
     *
     * @param color 颜色值
     * @return 是否为亮色
     */
    public static boolean isLight(int color) {
        return color >= 200 && color <= 255;
    }

    /**
     * 判断颜色是否为暗色。
     *
     * @param color 颜色值
     * @return 是否为暗色
     */
    public static boolean isDark(int color) {
        return color >= 0 && color <= 55;
    }

    /**
     * 更新颜色魔术效果（实验性，已废弃）。
     *
     * @param allpixels 像素数组
     */
    @Deprecated
    public static void udpateColorsMagic1(int[] allpixels) {
        if (!isNeedMagic()) {
            return;
        }

        int textColor = MagicHelper.getTextColor();
        int bgColor = MagicHelper.getBgColor();

        boolean isNeedFont = AppState.get().isCustomizeBgAndColors;

        for (int i = 0; i < allpixels.length; i++) {

            if (isNeedFont && allpixels[i] == Color.BLACK) {
                allpixels[i] = textColor;
                continue;
            }

            if (allpixels[i] == Color.WHITE) {
                allpixels[i] = bgColor;
                continue;
            }

            int color = allpixels[i];
            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);
            if (isLight(r) && isLight(g) && isLight(b)) {
                allpixels[i] = mixColorsFontColor(color, bgColor);
                continue;
            }
            if (isNeedFont && isDark(r) && isDark(g) && isDark(b)) {
                allpixels[i] = mixColorsFontColor(~color, textColor);
                continue;
            }

        }

    }

    /**
     * 更新颜色魔术效果（简单版本）。
     *
     * @param allpixels 像素数组
     */
    public static void udpateColorsMagicSimple(int[] allpixels) {
        int bgColor = MagicHelper.getBgColor();
        int first = allpixels[0];
        boolean useFirst = (first == allpixels[allpixels.length - 1]);

        for (int i = 0; i < allpixels.length; i++) {
            int color = allpixels[i];
            if (color == Color.WHITE || (useFirst && color == first)) {
                allpixels[i] = bgColor;
                continue;
            }

            int k = Color.red(color) + Color.green(color) + Color.blue(color);
            if (k > LIGHT_VALUE) {
                // ligth font color
                allpixels[i] = mixColorsFontColor(color, bgColor);
            }
        }
    }

    /**
     * 更新颜色魔术效果（标准版本）。
     * <p>
     * 根据当前主题颜色配置，将黑白像素转换为主题色。
     *
     * @param allpixels 像素数组
     */
    public static void udpateColorsMagic(int[] allpixels) {
        if (!isNeedMagic()) {
            return;
        }

        int textColor = MagicHelper.getTextColor();
        int bgColor = MagicHelper.getBgColor();
        int first = allpixels[0];
        boolean useFirst = (first == allpixels[allpixels.length - 1]);

        LOG.d("MAGIC0ON", textColor, bgColor, first);

        for (int i = 0; i < allpixels.length; i++) {
            int color = allpixels[i];

            if (color == Color.BLACK) {
                allpixels[i] = textColor;
                continue;
            }
            //
            if (color == Color.WHITE || (useFirst && color == first)) {
                allpixels[i] = bgColor;
                continue;
            }

            int k = Color.red(color) + Color.green(color) + Color.blue(color);
            if (k > LIGHT_VALUE) {
                // ligth font color
                allpixels[i] = mixColorsFontColor(color, bgColor);
            } else {
                // dark font color
                allpixels[i] = mixColorsFontColor(~color, textColor);
            }
        }
    }

    /**
     * 混合背景颜色。
     *
     * @param col1 颜色1
     * @param col2 颜色2
     * @return 混合后的颜色
     */
    public static int mixColorsBg(int col1, int col2) {
        int r1, g1, b1, r2, g2, b2;

        r1 = Color.red(col1);
        g1 = Color.green(col1);
        b1 = Color.blue(col1);

        r2 = Color.red(col2);
        g2 = Color.green(col2);
        b2 = Color.blue(col2);

        int r3 = overlay(r1, r2);
        int g3 = overlay(g1, g2);
        int b3 = overlay(b1, b2);

        return Color.rgb(r3, g3, b3);
    }

    /**
     * 混合字体颜色。
     *
     * @param col1 颜色1
     * @param col2 颜色2
     * @return 混合后的颜色
     */
    public static int mixColorsFontColor(int col1, int col2) {
        int r1, g1, b1, r2, g2, b2;

        r1 = Color.red(col1);
        g1 = Color.green(col1);
        b1 = Color.blue(col1);

        r2 = Color.red(col2);
        g2 = Color.green(col2);
        b2 = Color.blue(col2);

        int r3 = multiply(r1, r2);
        int g3 = multiply(g1, g2);
        int b3 = multiply(b1, b2);

        return Color.rgb(r3, g3, b3);
    }

    /**
     * 颜色乘法混合。
     *
     * @param r1 值1
     * @param r2 值2
     * @return 混合结果
     */
    public static int multiply(int r1, int r2) {
        return r1 * r2 / 255;
    }

    /**
     * 颜色屏幕混合。
     *
     * @param c1 颜色1
     * @param c2 颜色2
     * @return 混合结果
     */
    public static int screen(int c1, int c2) {
        return 255 - (((255 - c1) * (255 - c2)) / 255);
    }

    /**
     * 颜色叠加混合。
     *
     * @param c1 颜色1
     * @param c2 颜色2
     * @return 混合结果
     */
    public static int overlay(int c1, int c2) {
        return (c1 < 128) ? (2 * c2 * c1 / 255) : (255 - 2 * (255 - c2) * (255 - c1) / 255);
    }

    /**
     * 混合两种颜色。
     *
     * @param color1 颜色1
     * @param color2 颜色2
     * @param ratio 混合比例
     * @return 混合后的颜色
     */
    private static int blendColors(int color1, int color2, float ratio) {
        final float inverseRation = 1f - ratio;
        float r = (Color.red(color1) * ratio) + (Color.red(color2) * inverseRation);
        float g = (Color.green(color1) * ratio) + (Color.green(color2) * inverseRation);
        float b = (Color.blue(color1) * ratio) + (Color.blue(color2) * inverseRation);
        return Color.rgb((int) r, (int) g, (int) b);
    }

    /**
     * 混合两种颜色（带透明度支持）。
     *
     * @param color1 颜色1
     * @param color2 颜色2
     * @param amount 混合比例
     * @return 混合后的颜色
     */
    public static int mixTwoColors(int color1, int color2, float amount) {
        final byte ALPHA_CHANNEL = 24;
        final byte RED_CHANNEL = 16;
        final byte GREEN_CHANNEL = 8;
        final byte BLUE_CHANNEL = 0;

        final float inverseAmount = 1.0f - amount;

        int a =
                ((int) (((color1 >> ALPHA_CHANNEL & 0xff) * amount) + ((color2 >> ALPHA_CHANNEL & 0xff) * inverseAmount))) & 0xff;
        int r =
                ((int) (((color1 >> RED_CHANNEL & 0xff) * amount) + ((color2 >> RED_CHANNEL & 0xff) * inverseAmount))) & 0xff;
        int g =
                ((int) (((color1 >> GREEN_CHANNEL & 0xff) * amount) + ((color2 >> GREEN_CHANNEL & 0xff) * inverseAmount))) & 0xff;
        int b = ((int) (((color1 & 0xff) * amount) + ((color2 & 0xff) * inverseAmount))) & 0xff;

        return a << ALPHA_CHANNEL | r << RED_CHANNEL | g << GREEN_CHANNEL | b << BLUE_CHANNEL;
    }

    /**
     * 将颜色转换为十六进制字符串。
     *
     * @param intColor 颜色值
     * @return 十六进制颜色字符串
     */
    public static String colorToString(int intColor) {
        return String.format("#%06X", (0xFFFFFF & intColor));
    }

    /**
     * 获取Bitmap的主色调。
     *
     * @param bitmap Bitmap
     * @return 主色调颜色值
     */
    public static int getDominantColor(Bitmap bitmap) {
        if (null == bitmap) return Color.TRANSPARENT;

        int redBucket = 0;
        int greenBucket = 0;
        int blueBucket = 0;

        int pixelCount = bitmap.getWidth() * bitmap.getHeight();
        int[] pixels = new int[pixelCount];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        for (int y = 0, h = bitmap.getHeight(); y < h; y++) {
            for (int x = 0, w = bitmap.getWidth(); x < w; x++) {
                int color = pixels[x + y * w]; // x + y * width
                redBucket += (color >> 16) & 0xFF; // Color.red
                greenBucket += (color >> 8) & 0xFF; // Color.greed
                blueBucket += (color & 0xFF); // Color.blue
            }
        }

        return Color.rgb(redBucket / pixelCount, greenBucket / pixelCount, blueBucket / pixelCount);
    }

    /**
     * 裁剪Bitmap的空白区域。
     *
     * @param bmp Bitmap
     * @return 裁剪后的Bitmap
     */
    public static Bitmap trimBitmap(Bitmap bmp) {
        int bgColor = getBgColor();
        int imgHeight = bmp.getHeight();
        int imgWidth = bmp.getWidth();

        // TRIM WIDTH - LEFT
        int startWidth = 0;
        for (int x = 0; x < imgWidth; x++) {
            if (startWidth == 0) {
                for (int y = 0; y < imgHeight; y++) {
                    if (bmp.getPixel(x, y) != bgColor) {
                        startWidth = x;
                        break;
                    }
                }
            } else break;
        }

        // TRIM WIDTH - RIGHT
        int endWidth = 0;
        for (int x = imgWidth - 1; x >= 0; x--) {
            if (endWidth == 0) {
                for (int y = 0; y < imgHeight; y++) {
                    if (bmp.getPixel(x, y) != bgColor) {
                        endWidth = x;
                        break;
                    }
                }
            } else break;
        }

        // TRIM HEIGHT - TOP
        int startHeight = 0;
        for (int y = 0; y < imgHeight; y++) {
            if (startHeight == 0) {
                for (int x = 0; x < imgWidth; x++) {
                    if (bmp.getPixel(x, y) != bgColor) {
                        startHeight = y;
                        break;
                    }
                }
            } else break;
        }

        // TRIM HEIGHT - BOTTOM
        int endHeight = 0;
        for (int y = imgHeight - 1; y >= 0; y--) {
            if (endHeight == 0) {
                for (int x = 0; x < imgWidth; x++) {
                    if (bmp.getPixel(x, y) != bgColor) {
                        endHeight = y;
                        break;
                    }
                }
            } else break;
        }

        return Bitmap.createBitmap(bmp, startWidth, startHeight, endWidth - startWidth, endHeight - startHeight);

    }

    /**
     * 判断颜色是否为暗色（简单版本）。
     *
     * @param color 颜色值
     * @return 是否为暗色
     */
    public static boolean isColorDarkSimple(int color) {
        int k = Color.red(color) + Color.green(color) + Color.blue(color);
        if (k > 550) {// 550
            return false;
        } else {
            return true;
        }
    }

    /**
     * 判断颜色是否为暗色（标准版本）。
     *
     * @param color 颜色值
     * @return 是否为暗色
     */
    public static boolean isColorDark(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        if (darkness < 0.5) {
            return false; // It's a light color
        } else {
            return true; // It's a dark color
        }
    }

    /**
     * 应用快速对比度和亮度调整。
     *
     * @param arr 像素数组
     * @param w 宽度
     * @param h 高度
     */
    public static void applyQuickContrastAndBrightness(int[] arr, int w, int h) {
        if (AppState.get().isEnableBCOptional1) {
            if (AppState.get().contrastImage != 0 || AppState.get().brigtnessImage != 0) {
                quickContrast3(arr, AppState.get().contrastImage, AppState.get().brigtnessImage * -1);
            }
            if (AppState.get().bolderTextOnImage) {
                ivanEbolden(arr);
            }
        }

    }

    /**
     * 加粗文本（Ivan算法）。
     *
     * @param arr 像素数组
     */
    public static void ivanEbolden(int[] arr) {
        int prevSum = 0;
        for (int i = 0; i < arr.length; i++) {
            int color = arr[i];
            if (color == Color.BLACK) {
                prevSum = 0;
                continue;
            }
            if (color == Color.WHITE) {
                arr[i] = Color.rgb(prevSum, prevSum, prevSum);
                prevSum = 255;
                continue;
            }

            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);

            int sum = (r + g + b) / 3;

            int nexSum = sum;
            if (i > 1) {
                nexSum = Math.min(prevSum, sum);
            }
            prevSum = sum;
            arr[i] = Color.rgb(nexSum, nexSum, nexSum);
        }

    }

    /**
     * 调整对比度和亮度（Ivan算法）。
     *
     * @param arr 像素数组
     * @param extra_contrast 额外对比度
     * @param delta_brightness 亮度增量
     */
    public static void ivanContrast(int[] arr, int extra_contrast, int delta_brightness) {
        int prevSum = 0;
        for (int i = 0; i < arr.length; i++) {
            int color = arr[i];
            if (color == Color.BLACK) {
                prevSum = 0;
                continue;
            }
            if (color == Color.WHITE) {
                arr[i] = Color.rgb(prevSum, prevSum, prevSum);
                prevSum = 255;
                continue;
            }

            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);

            int sum = (r + g + b) / 3;

            sum = sum + delta_brightness; // make darker

            if (sum > 128) {
                sum = sum + extra_contrast;
            } else {
                sum = sum - extra_contrast;
            }

            if (sum > 255) {
                sum = 255;
            } else if (sum < 0) {
                sum = 0;
            }

            int nexSum = sum;
            if (AppState.get().bolderTextOnImage) {
                if (i > 1) {
                    nexSum = Math.min(prevSum, sum);
                }
            }

            prevSum = sum;

            arr[i] = Color.rgb(nexSum, nexSum, nexSum);

        }

    }

    /** 亮度对比度映射表 */
    static int[] brightnessContrastMap = new int[256];

    /** 最后哈希值 */
    static int lastHash = -1;

    /**
     * 快速对比度调整（带缓存）。
     *
     * @param arr 像素数组
     * @param extra_contrast 额外对比度
     * @param delta_brightness 亮度增量
     */
    public static void quickContrast3(int[] arr, int extra_contrast, int delta_brightness) {

        int hash = extra_contrast * 31 + delta_brightness;

        if (lastHash != hash) {
            double contrastFactor = (100.0 + extra_contrast) / 100.0;

            for (int i = 0; i < 256; i++) {
                // Apply brightness first (standard approach)
                int val = i + delta_brightness;

                // Apply contrast around the midpoint (128)
                val = (int) (((val - 128) * contrastFactor) + 128);

                // Clamp values to 0-255 range
                if (val < 0) val = 0;
                else if (val > 255) val = 255;

                brightnessContrastMap[i] = val;
            }
            lastHash = hash;
        }

        // process the real image
        for (int i = 0; i < arr.length; i++) {
            int color = arr[i];

            if (color == Color.WHITE || color == Color.BLACK) {
                continue;
            }

            // Extract channels
            int a = (color >> 24) & 0xFF;
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;

            // Apply the pre-calculated mapping to each color channel
            r = brightnessContrastMap[r];
            g = brightnessContrastMap[g];
            b = brightnessContrastMap[b];

            // Recompose the ARGB_8888 integer
            arr[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }

    }

    /** 锐化映射表 */
    static int[] sharpenMap = null;

    /**
     * 加粗文本（锐化算法）。
     *
     * @param arr 像素数组
     */
    public static void embolden(int[] arr) {

        int lum;

        if (sharpenMap == null) {
            sharpenMap = new int[256];
            for (int i = 0; i <= 255; i++) {
                lum = 255 - ((255 - i) * (255 - i) / 255); // inv-mult map (to a
                sharpenMap[i] = (lum << 16) + (lum << 8) + lum;
            }
        }

        int lum_this = arr[0] & 0x000000FF; // lazy read for the first pixel
        for (int i = 0; i < arr.length - 1; i++) {
            int temp = arr[i + 1];
            int lum_next = ((temp & 0x00FF0000) >> 17) + ((temp & 0x0000FF00) >> 10) + ((temp & 0x000000FF) >> 2);

            lum = (lum_this * lum_next) >> 8; // multiply with offset

            arr[i] = sharpenMap[lum];

            lum_this = lum_next;

        }

    }

    /**
     * 快速模糊算法。
     *
     * @param pix 像素数组
     * @param w 宽度
     * @param h 高度
     * @param radius 模糊半径
     */
    public static void fastblur(int[] pix, int w, int h, int radius) {
        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = radius + radius + 1;

        int r[] = new int[wh];
        int g[] = new int[wh];
        int b[] = new int[wh];
        int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
        int vmin[] = new int[Math.max(w, h)];

        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int dv[] = new int[256 * divsum];
        for (i = 0; i < 256 * divsum; i++) {
            dv[i] = (i / divsum);
        }

        yw = yi = 0;

        int[][] stack = new int[div][3];
        int stackpointer;
        int stackstart;
        int[] sir;
        int rbs;
        int r1 = radius + 1;
        int routsum, goutsum, boutsum;
        int rinsum, ginsum, binsum;

        for (y = 0; y < h; y++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            for (i = -radius; i <= radius; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                sir = stack[i + radius];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
            }
            stackpointer = radius;

            for (x = 0; x < w; x++) {

                r[yi] = dv[rsum];
                g[yi] = dv[gsum];
                b[yi] = dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm);
                }
                p = pix[yw + vmin[x]];

                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[(stackpointer) % div];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi++;
            }
            yw += w;
        }
        for (x = 0; x < w; x++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            yp = -radius * w;
            for (i = -radius; i <= radius; i++) {
                yi = Math.max(0, yp) + x;

                sir = stack[i + radius];

                sir[0] = r[yi];
                sir[1] = g[yi];
                sir[2] = b[yi];

                rbs = r1 - Math.abs(i);

                rsum += r[yi] * rbs;
                gsum += g[yi] * rbs;
                bsum += b[yi] * rbs;

                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }

                if (i < hm) {
                    yp += w;
                }
            }
            yi = x;
            stackpointer = radius;
            for (y = 0; y < h; y++) {
                // Preserve alpha channel: ( 0xff000000 & pix[yi] )
                pix[yi] = (0xff000000 & pix[yi]) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w;
                }
                p = x + vmin[y];

                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi += w;
            }
        }

    }

    /**
     * 获取色调颜色。
     *
     * @return 色调颜色
     */
    public static int getTintColor() {
        int colorTint = 0;
        if (AppState.get().isUiTextColor) {
            colorTint = AppState.get().uiTextColor;
        } else {
            colorTint = AppState.get().tintColor;
        }
        if (colorTint == Color.WHITE && AppState.get().isDayNotInvert) {
            colorTint = Color.BLACK;
        }
        if (colorTint == Color.BLACK && !AppState.get().isDayNotInvert) {
            colorTint = Color.WHITE;
        }
        if (colorTint == Color.WHITE) {
            colorTint = MagicHelper.otherColor(Color.WHITE, 0.2f);
        }
        return colorTint;
    }

}
