package com.foobnix.ext;

import android.text.TextUtils;

import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.LibreraApp;
import com.foobnix.pdf.info.R;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TXT文件章节分析器（优化版）
 * <p>
 * 用于解析TXT小说的章节结构，与TxtAnalyzer功能相同，但采用内存映射文件（MappedByteBuffer）
 * 替代传统的InputStream读取，显著提升大文件的解析性能。支持自动检测编码、智能匹配目录规则、
 * 生成章节列表和提取章节内容。采用单例模式避免重复解析，带有缓存机制。
 */
public class TxtAnalyzerOptimized {

    /** 读取缓冲区大小 (1MB) */
    private static final int BUFFER_SIZE = 1024 * 1024;
    /** 有目录规则时单章最大长度 (100KB) */
    private static final int MAX_LENGTH_WITH_TOC = 102400;
    /** 无目录规则时单章最大长度 (10KB) */
    private static final int MAX_LENGTH_WITH_NO_TOC = 10 * 1024;
    /** 换行符字节值 */
    private static final byte BLANK = 0x0a;
    /** 回车符字节值 */
    private static final byte CR = 0x0d;

    /**
     * 目录匹配规则数组
     * <p>
     * 包含多种常见的小说章节标题格式：
     * - 第X章/节/卷/集/部/篇
     * - 数字开头的标题
     * - 中文数字章节
     * - 英文Chapter/Section/Part
     * - 带特殊符号的标题（【】、☆★等）
     * - 序章、楔子、正文、终章、后记、尾声、番外等特殊章节
     */
    private static final String[] TOC_RULES = {
        "^[ 　\\t]{0,4}(?:序章|楔子|正文(?!完|结)|终章|后记|尾声|番外|第\\s{0,4}[\\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+?\\s{0,4}(?:章|节(?!课)|卷|集(?![合和])|部(?![分赛游])|篇(?!张))).{0,30}$",
        "^[ 　\\t]{0,4}\\d{1,5}[:：,.， 、_—\\-].{1,30}$",
        "^[ 　\\t]{0,4}(?:序章|楔子|正文(?!完|结)|终章|后记|尾声|番外|[零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}章?)[ 、_—\\-].{1,30}$",
        "^[ 　\\t]{0,4}正文[ 　]{1,4}.{0,20}$",
        "^[ 　\\t]{0,4}(?:[Cc]hapter|[Ss]ection|[Pp]art|[Nn][oO][.、]|[Ee]pisode|序章|楔子|正文(?!完|结)|终章|后记|尾声|番外)\\s{0,4}\\d{1,4}.{0,30}$",
        "(?<=[\\s　])[【〔〖「『〈［\\[](?:第|[Cc]hapter)[\\d零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,10}[章节].{0,20}$",
        "(?<=[\\s　]{0,4})(?:[☆★✦✧].{1,30}|序章|楔子|正文(?!完|结)|终章|后记|尾声|番外)[ 　]{0,4}$",
        "^[ \\t　]{0,4}(?:简介|文案|前言|序章|楔子|正文(?!完|结)|终章|后记|尾声|番外|[卷章][\\d零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8})[ 　]{0,4}.{0,30}$",
        "^[一-龥]{1,20}[ 　\\t]{0,4}[(（][\\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}[)）][ 　\\t]{0,4}$",
        "^[一-龥]{1,20}[ 　\\t]{0,4}[\\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}[ 　\\t]{0,4}$"
    };

    /** 单例实例 */
    private static TxtAnalyzerOptimized instance;
    /** 上次解析的文件路径 */
    private static String lastFilePath;
    /** 上次解析的文件修改时间 */
    private static long lastFileModified;

    /** 文件编码 */
    private Charset charset;
    /** 匹配到的目录规则 */
    private String tocRule;
    /** 文件大小 */
    private long fileSize;
    /** 内存映射缓冲区 */
    private MappedByteBuffer mappedBuffer;
    /** 文件通道 */
    private FileChannel channel;

    /** 缓存的章节列表 */
    private List<TxtChapter> cachedChapters;

    /**
     * 获取单例实例
     * <p>
     * 如果文件路径或修改时间发生变化，创建新实例并清空缓存。
     * 在创建新实例前会先关闭旧实例的资源。
     *
     * @param filePath 文件路径
     * @return TxtAnalyzerOptimized实例
     */
    public static synchronized TxtAnalyzerOptimized getInstance(String filePath) {
        File file = new File(filePath);
        long currentModified = file.lastModified();

        if (instance == null || !filePath.equals(lastFilePath) || currentModified != lastFileModified) {
            if (instance != null) {
                instance.close();
            }
            instance = new TxtAnalyzerOptimized();
            lastFilePath = filePath;
            lastFileModified = currentModified;
            instance.cachedChapters = null;
        }
        return instance;
    }

    /**
     * 清除单例实例和缓存
     * <p>
     * 在清除前会先关闭资源。
     */
    public static void clear() {
        if (instance != null) {
            instance.close();
        }
        instance = null;
        lastFilePath = null;
        lastFileModified = 0;
    }

    /**
     * 关闭资源
     * <p>
     * 使用Unsafe API强制释放MappedByteBuffer，避免内存泄漏。
     */
    private void close() {
        if (mappedBuffer != null) {
            try {
                mappedBuffer.force();
                Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                java.lang.reflect.Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                Object unsafe = unsafeField.get(null);
                java.lang.reflect.Method invokeCleaner = unsafeClass.getMethod("invokeCleaner", ByteBuffer.class);
                invokeCleaner.invoke(unsafe, mappedBuffer);
            } catch (Exception e) {
                LOG.e(e);
            }
            mappedBuffer = null;
        }
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                LOG.e(e);
            }
            channel = null;
        }
    }

    /**
     * 获取章节列表
     * <p>
     * 如果已有缓存直接返回，否则执行完整解析流程：
     * 检测编码 → 检查BOM → 读取样本内容 → 选择最佳目录规则 → 分析章节
     * 使用内存映射文件提升大文件解析性能。
     *
     * @param filePath 文件路径
     * @return 章节列表
     * @throws IOException IO异常
     */
    public List<TxtChapter> getChapterList(String filePath) throws IOException {
        if (cachedChapters != null) {
            return cachedChapters;
        }

        File file = new File(filePath);
        fileSize = file.length();

        // 使用RandomAccessFile获取FileChannel进行内存映射
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        channel = raf.getChannel();
        mappedBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);

        try {
            // 检测文件编码
            detectEncoding(filePath);
            // 检查BOM标记
            int bomSize = checkBOM();

            // 读取样本内容用于目录规则匹配
            String contentSample = readSampleContent(bomSize);
            // 根据样本内容选择最佳目录规则
            selectBestTocRule(contentSample);

            // 使用选定的规则分析完整文件
            cachedChapters = analyze(bomSize);
            return cachedChapters;
        } finally {
            close();
        }
    }

    /**
     * 获取指定章节的内容
     * <p>
     * 根据章节的起始和结束偏移量读取文件内容，去除开头空白字符。
     * 使用RandomAccessFile直接读取指定位置，避免加载整个文件。
     *
     * @param filePath 文件路径
     * @param chapter  章节对象
     * @return 章节内容
     * @throws IOException IO异常
     */
    public String getChapterContent(String filePath, TxtChapter chapter) throws IOException {
        long start = chapter.start;
        long end = chapter.end;

        // 边界检查
        if (end > fileSize) {
            end = fileSize;
        }

        int length = (int) (end - start);
        byte[] buffer = new byte[length];

        RandomAccessFile raf = new RandomAccessFile(filePath, "r");
        try {
            raf.getChannel().read(ByteBuffer.wrap(buffer), start);

            String content = new String(buffer, charset);
            return content.replaceFirst("^[\\n\\r\\s]+", "");
        } finally {
            raf.close();
        }
    }

    /**
     * 检测文件编码
     * <p>
     * 如果用户指定了编码则使用指定编码，否则自动检测。
     * 优先检查UTF-8 BOM标记。
     *
     * @param filePath 文件路径
     * @throws IOException IO异常
     */
    private void detectEncoding(String filePath) throws IOException {
        if (AppState.get().isCharacterEncoding) {
            charset = Charset.forName(AppState.get().characterEncoding);
        } else {
            java.io.FileInputStream fis = new java.io.FileInputStream(filePath);
            try {
                byte[] bomBuffer = new byte[3];
                int read = fis.read(bomBuffer);
                // 检查UTF-8 BOM标记
                if (read >= 3 && bomBuffer[0] == (byte) 0xEF && bomBuffer[1] == (byte) 0xBB && bomBuffer[2] == (byte) 0xBF) {
                    charset = StandardCharsets.UTF_8;
                } else {
                    // 重置流位置并自动检测编码
                    fis.reset();
                    String encoding = ExtUtils.determineTxtEncoding(fis);
                    charset = TextUtils.isEmpty(encoding) ? StandardCharsets.UTF_8 : Charset.forName(encoding);
                }
            } finally {
                fis.close();
            }
        }
    }

    /**
     * 检查UTF-8 BOM标记
     *
     * @return BOM标记长度（3表示有BOM，0表示无BOM）
     */
    private int checkBOM() {
        if (fileSize >= 3) {
            byte b0 = mappedBuffer.get(0);
            byte b1 = mappedBuffer.get(1);
            byte b2 = mappedBuffer.get(2);
            if (b0 == (byte) 0xEF && b1 == (byte) 0xBB && b2 == (byte) 0xBF) {
                return 3;
            }
        }
        return 0;
    }

    /**
     * 读取样本内容
     * <p>
     * 从内存映射缓冲区中读取样本内容，用于目录规则匹配。
     *
     * @param bomSize BOM标记长度
     * @return 样本内容字符串
     */
    private String readSampleContent(int bomSize) {
        int sampleSize = Math.min(BUFFER_SIZE, (int) (fileSize - bomSize));
        byte[] buffer = new byte[sampleSize];

        mappedBuffer.position(bomSize);
        mappedBuffer.get(buffer);

        return new String(buffer, charset);
    }

    /**
     * 选择最佳目录规则
     * <p>
     * 遍历所有规则，选择匹配次数最多的规则。
     *
     * @param content 样本内容
     */
    private void selectBestTocRule(String content) {
        int maxMatches = 0;
        String bestRule = "";

        for (String rule : TOC_RULES) {
            try {
                Pattern pattern = Pattern.compile(rule, Pattern.MULTILINE);
                Matcher matcher = pattern.matcher(content);
                int matches = 0;
                while (matcher.find()) {
                    matches++;
                }
                // 需要比当前最佳规则多匹配至少2次才更新
                if (matches > maxMatches + 1) {
                    maxMatches = matches;
                    bestRule = rule;
                }
            } catch (Exception e) {
                LOG.e(e);
            }
        }

        this.tocRule = bestRule;
    }

    /**
     * 根据是否有目录规则选择分析方法
     *
     * @param bomSize BOM标记长度
     * @return 章节列表
     */
    private List<TxtChapter> analyze(int bomSize) {
        if (TextUtils.isEmpty(tocRule)) {
            return analyzeWithoutRule(bomSize);
        }
        return analyzeWithRule(bomSize);
    }

    /**
     * 使用目录规则分析章节
     * <p>
     * 根据匹配到的规则逐块读取文件，识别章节标题，生成章节列表。
     * 支持处理超长章节自动分割。使用内存映射文件提升性能。
     *
     * @param bomSize BOM标记长度
     * @return 章节列表
     */
    private List<TxtChapter> analyzeWithRule(int bomSize) {
        Pattern pattern = Pattern.compile(tocRule, Pattern.MULTILINE);
        List<TxtChapter> toc = new ArrayList<>();

        long curOffset = bomSize;
        int bufferSize = BUFFER_SIZE;
        byte[] buffer = new byte[bufferSize];

        while (curOffset < fileSize) {
            int bytesToRead = (int) Math.min(bufferSize, fileSize - curOffset);
            mappedBuffer.position((int) curOffset);
            mappedBuffer.get(buffer, 0, bytesToRead);

            int end = bytesToRead;
            // 如果缓冲区满且不是文件末尾，回退到换行符位置
            if (end == bufferSize && curOffset + end < fileSize) {
                for (int i = bytesToRead - 1; i >= 0; i--) {
                    if (buffer[i] == BLANK || buffer[i] == CR) {
                        end = i + 1;
                        break;
                    }
                }
            }

            String blockContent = new String(buffer, 0, end, charset);

            int seekPos = 0;
            Matcher matcher = pattern.matcher(blockContent);

            while (matcher.find()) {
                int chapterStart = matcher.start();
                String chapterContent = blockContent.substring(seekPos, chapterStart);
                long chapterLength = chapterContent.getBytes(charset).length;
                long titleLength = matcher.group().getBytes(charset).length;

                // 处理第一章之前的内容作为前言
                if (toc.isEmpty() && seekPos == 0 && chapterStart != 0) {
                    if (!chapterContent.trim().isEmpty()) {
                        TxtChapter qyChapter = new TxtChapter(LibreraApp.context.getString(R.string.foreword), curOffset, curOffset + chapterLength);
                        toc.add(qyChapter);
                    }
                } else if (!toc.isEmpty()) {
                    // 更新上一章的结束位置
                    TxtChapter lastChapter = toc.get(toc.size() - 1);
                    long newEnd = lastChapter.end + chapterLength;

                    // 如果章节过长，自动分割
                    if (newEnd - lastChapter.start > MAX_LENGTH_WITH_TOC) {
                        List<TxtChapter> subChapters = splitChapter(lastChapter.title, lastChapter.start, newEnd);
                        toc.remove(toc.size() - 1);
                        toc.addAll(subChapters);
                    } else {
                        lastChapter.end = newEnd;
                    }
                }

                // 创建当前章节
                String title = matcher.group().trim();
                TxtChapter curChapter = new TxtChapter(title, curOffset + chapterLength + titleLength, curOffset + chapterLength + titleLength);
                toc.add(curChapter);

                seekPos += chapterContent.length() + matcher.group().length();
            }

            if (!toc.isEmpty()) {
                toc.get(toc.size() - 1).end = curOffset + end;
            }

            curOffset += end;
        }

        // 处理最后一个章节的过长分割
        if (!toc.isEmpty()) {
            TxtChapter last = toc.get(toc.size() - 1);
            if (last.end - last.start > MAX_LENGTH_WITH_TOC) {
                List<TxtChapter> subChapters = splitChapter(last.title, last.start, last.end);
                toc.remove(toc.size() - 1);
                toc.addAll(subChapters);
            }
        }

        return toc;
    }

    /**
     * 无目录规则时分析章节
     * <p>
     * 当无法匹配任何目录规则时，按固定大小分割文件为多个章节。
     * 使用内存映射文件提升性能。
     *
     * @param bomSize BOM标记长度
     * @return 章节列表
     */
    private List<TxtChapter> analyzeWithoutRule(int bomSize) {
        List<TxtChapter> toc = new ArrayList<>();

        long curOffset = bomSize;
        long chapterStart = bomSize;
        int chapterIndex = 1;

        int bufferSize = BUFFER_SIZE;
        byte[] buffer = new byte[bufferSize];

        while (curOffset < fileSize) {
            int bytesToRead = (int) Math.min(bufferSize, fileSize - curOffset);
            mappedBuffer.position((int) curOffset);
            mappedBuffer.get(buffer, 0, bytesToRead);

            int offset = 0;
            while (offset < bytesToRead) {
                long chunkSize = 0;
                int i;

                // 读取到换行符或达到最大长度
                for (i = offset; i < bytesToRead && chunkSize < MAX_LENGTH_WITH_NO_TOC; i++) {
                    chunkSize++;
                    if (buffer[i] == BLANK || buffer[i] == CR) {
                        chunkSize = i - offset + 1;
                        break;
                    }
                }

                // 如果达到最大长度但还没到换行符，继续查找换行符
                if (chunkSize >= MAX_LENGTH_WITH_NO_TOC) {
                    for (; i < bytesToRead; i++) {
                        if (buffer[i] == BLANK || buffer[i] == CR) {
                            chunkSize = i - offset + 1;
                            break;
                        }
                    }
                }

                // 如果当前章节长度超过限制，创建新章节
                if (curOffset + offset + chunkSize > chapterStart + MAX_LENGTH_WITH_NO_TOC) {
                    TxtChapter chapter = new TxtChapter(LibreraApp.context.getString(R.string.chapter) + chapterIndex + "章", chapterStart, curOffset + offset + chunkSize);
                    toc.add(chapter);
                    chapterStart = curOffset + offset + chunkSize;
                    chapterIndex++;
                }

                offset += chunkSize;
            }

            curOffset += bytesToRead;
        }

        // 处理最后一个章节
        if (chapterStart < fileSize) {
            TxtChapter chapter = new TxtChapter(LibreraApp.context.getString(R.string.chapter) + chapterIndex + "章", chapterStart, fileSize);
            toc.add(chapter);
        }

        return toc;
    }

    /**
     * 分割超长章节
     * <p>
     * 将单个超长章节按固定大小分割为多个子章节。
     *
     * @param title 原章节标题
     * @param start 起始偏移
     * @param end   结束偏移
     * @return 分割后的子章节列表
     */
    private List<TxtChapter> splitChapter(String title, long start, long end) {
        List<TxtChapter> chapters = new ArrayList<>();

        long length = end - start;
        int numChapters = (int) (length / MAX_LENGTH_WITH_NO_TOC) + 1;
        long chunkSize = length / numChapters;

        for (int i = 0; i < numChapters; i++) {
            long chapterStart = start + i * chunkSize;
            long chapterEnd = (i == numChapters - 1) ? end : start + (i + 1) * chunkSize;

            TxtChapter chapter = new TxtChapter(title + "(" + (i + 1) + ")", chapterStart, chapterEnd);
            chapters.add(chapter);
        }

        return chapters;
    }
}