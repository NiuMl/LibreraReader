package com.foobnix.ext;

import android.text.TextUtils;

import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.LibreraApp;
import com.foobnix.pdf.info.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TXT文件章节分析器
 * <p>
 * 用于解析TXT小说的章节结构，支持自动检测编码、智能匹配目录规则、
 * 生成章节列表和提取章节内容。采用单例模式避免重复解析，带有缓存机制。
 */
public class TxtAnalyzer {
    
    /** 读取缓冲区大小 (512KB) */
    private static final int BUFFER_SIZE = 512 * 1024;
    /** 有目录规则时单章最大长度 (100KB) */
    private static final int MAX_LENGTH_WITH_TOC = 102400;
    /** 无目录规则时单章最大长度 (10KB) */
    private static final int MAX_LENGTH_WITH_NO_TOC = 10 * 1024;
    /** 换行符字节值 */
    private static final byte BLANK = 0x0a;
    
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
    private static TxtAnalyzer instance;
    /** 上次解析的文件路径 */
    private static String lastFilePath;
    /** 上次解析的文件修改时间 */
    private static long lastFileModified;
    
    /** 文件编码 */
    private Charset charset;
    /** 匹配到的目录规则 */
    private String tocRule;
    
    /** 缓存的章节列表 */
    private List<TxtChapter> cachedChapters;
    
    /**
     * 获取单例实例
     * <p>
     * 如果文件路径或修改时间发生变化，创建新实例并清空缓存。
     *
     * @param filePath 文件路径
     * @return TxtAnalyzer实例
     */
    public static synchronized TxtAnalyzer getInstance(String filePath) {
        File file = new File(filePath);
        long currentModified = file.lastModified();
        
        if (instance == null || !filePath.equals(lastFilePath) || currentModified != lastFileModified) {
            instance = new TxtAnalyzer();
            lastFilePath = filePath;
            lastFileModified = currentModified;
            instance.cachedChapters = null;
        }
        return instance;
    }
    
    /**
     * 清除单例实例和缓存
     */
    public static void clear() {
        instance = null;
        lastFilePath = null;
        lastFileModified = 0;
    }
    
    /**
     * 获取章节列表
     * <p>
     * 如果已有缓存直接返回，否则执行完整解析流程：
     * 检测编码 → 读取样本内容 → 选择最佳目录规则 → 分析章节
     *
     * @param filePath 文件路径
     * @return 章节列表
     * @throws IOException IO异常
     */
    public List<TxtChapter> getChapterList(String filePath) throws IOException {
        if (cachedChapters != null) {
            return cachedChapters;
        }
        
        FileInputStream fis = new FileInputStream(filePath);
        try {
            // 检测文件编码
            detectEncoding(filePath, fis);
            
            // 读取样本内容用于目录规则匹配
            String contentSample = readSampleContent(fis);
            fis.close();
            
            // 根据样本内容选择最佳目录规则
            selectBestTocRule(contentSample);
            
            // 使用选定的规则分析完整文件
            FileInputStream fis2 = new FileInputStream(filePath);
            try {
                cachedChapters = analyze(fis2);
                return cachedChapters;
            } finally {
                fis2.close();
            }
        } finally {
            fis.close();
        }
    }
    
    /**
     * 获取指定章节的内容
     * <p>
     * 根据章节的起始和结束偏移量读取文件内容，去除开头空白字符。
     *
     * @param filePath 文件路径
     * @param chapter  章节对象
     * @return 章节内容
     * @throws IOException IO异常
     */
    public String getChapterContent(String filePath, TxtChapter chapter) throws IOException {
        long start = chapter.start;
        long end = chapter.end;
        
        FileInputStream fis = new FileInputStream(filePath);
        try {
            fis.skip(start);
            int length = (int) (end - start);
            byte[] buffer = new byte[length];
            int read = fis.read(buffer);
            
            // 处理实际读取长度与预期不一致的情况
            if (read != length) {
                byte[] actualBuffer = new byte[read];
                System.arraycopy(buffer, 0, actualBuffer, 0, read);
                String content = new String(actualBuffer, charset);
                return content.replaceFirst("^[\\n\\s]+", "");
            }
            
            String content = new String(buffer, charset);
            return content.replaceFirst("^[\\n\\s]+", "");
        } finally {
            fis.close();
        }
    }
    
    /**
     * 检测文件编码
     * <p>
     * 如果用户指定了编码则使用指定编码，否则自动检测。
     *
     * @param filePath 文件路径
     * @param fis      文件输入流
     * @throws IOException IO异常
     */
    private void detectEncoding(String filePath, InputStream fis) throws IOException {
        if (AppState.get().isCharacterEncoding) {
            charset = Charset.forName(AppState.get().characterEncoding);
        } else {
            String encoding = ExtUtils.determineTxtEncoding(fis);
            charset = TextUtils.isEmpty(encoding) ? StandardCharsets.UTF_8 : Charset.forName(encoding);
        }
    }
    
    /**
     * 读取样本内容
     * <p>
     * 读取缓冲区大小的内容，跳过BOM标记。
     *
     * @param fis 文件输入流
     * @return 样本内容字符串
     * @throws IOException IO异常
     */
    private String readSampleContent(InputStream fis) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int length = fis.read(buffer);
        if (length == -1) {
            return "";
        }
        
        // 跳过UTF-8 BOM标记
        int start = 0;
        if (length >= 3 && buffer[0] == (byte) 0xEF && buffer[1] == (byte) 0xBB && buffer[2] == (byte) 0xBF) {
            start = 3;
        }
        
        return new String(buffer, start, length - start, charset);
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
     * @param fis 文件输入流
     * @return 章节列表
     * @throws IOException IO异常
     */
    private List<TxtChapter> analyze(InputStream fis) throws IOException {
        if (TextUtils.isEmpty(tocRule)) {
            return analyzeWithoutRule(fis);
        }
        
        return analyzeWithRule(fis);
    }
    
    /**
     * 使用目录规则分析章节
     * <p>
     * 根据匹配到的规则逐块读取文件，识别章节标题，生成章节列表。
     * 支持处理超长章节自动分割。
     *
     * @param fis 文件输入流
     * @return 章节列表
     * @throws IOException IO异常
     */
    private List<TxtChapter> analyzeWithRule(InputStream fis) throws IOException {
        Pattern pattern = Pattern.compile(tocRule, Pattern.MULTILINE);
        List<TxtChapter> toc = new ArrayList<>();
        
        byte[] buffer = new byte[BUFFER_SIZE];
        long curOffset = 0;
        int length;
        int bufStart = 3;
        
        // 读取前3字节检查BOM
        fis.read(buffer, 0, 3);
        if (buffer[0] == (byte) 0xEF && buffer[1] == (byte) 0xBB && buffer[2] == (byte) 0xBF) {
            bufStart = 0;
            curOffset = 3;
        }
        
        int bytesRead;
        while ((bytesRead = fis.read(buffer, bufStart, BUFFER_SIZE - bufStart)) > 0) {
            length = bytesRead;
            int end = bufStart + length;
            // 如果缓冲区满，回退到换行符位置
            if (end == BUFFER_SIZE) {
                for (int i = bufStart + length - 1; i >= 0; i--) {
                    if (buffer[i] == BLANK) {
                        end = i;
                        break;
                    }
                }
            }
            
            String blockContent = new String(buffer, 0, end, charset);
            // 将未处理的内容移到缓冲区开头
            System.arraycopy(buffer, end, buffer, 0, bufStart + length - end);
            bufStart = bufStart + length - end;
            length = end;
            
            int seekPos = 0;
            Matcher matcher = pattern.matcher(blockContent);
            
            while (matcher.find()) {
                int chapterStart = matcher.start();
                String chapterContent = blockContent.substring(seekPos, chapterStart);
                int chapterContentLength = chapterContent.length();
                long chapterLength = chapterContent.getBytes(charset).length;
                long titleLength = matcher.group().getBytes(charset).length;
                
                // 处理第一章之前的内容作为前言
                if (toc.isEmpty() && seekPos == 0 && chapterStart != 0) {
                    if (chapterContent.trim().isEmpty()) {
                        continue;
                    }
                    TxtChapter qyChapter = new TxtChapter(LibreraApp.context.getString(R.string.foreword), curOffset, curOffset + chapterLength);
                    toc.add(qyChapter);
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
                
                seekPos += chapterContentLength + matcher.group().length();
            }
            
            curOffset += length;
            if (!toc.isEmpty()) {
                toc.get(toc.size() - 1).end = curOffset;
            }
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
     *
     * @param fis 文件输入流
     * @return 章节列表
     * @throws IOException IO异常
     */
    private List<TxtChapter> analyzeWithoutRule(InputStream fis) throws IOException {
        List<TxtChapter> toc = new ArrayList<>();
        
        byte[] buffer = new byte[BUFFER_SIZE];
        long curOffset = 0;
        int length = 0;
        int bufStart = 3;
        
        // 读取前3字节检查BOM
        fis.read(buffer, 0, 3);
        if (buffer[0] == (byte) 0xEF && buffer[1] == (byte) 0xBB && buffer[2] == (byte) 0xBF) {
            bufStart = 0;
            curOffset = 3;
        }
        
        int blockPos = 0;
        int chapterPos = 0;
        
        int bytesRead;
        while ((bytesRead = fis.read(buffer, bufStart, BUFFER_SIZE - bufStart)) > 0) {
            length = bytesRead;
            blockPos++;
            length += bufStart;
            int strLength = length;
            int chapterOffset = 0;
            
            while (strLength > 0) {
                chapterPos++;
                
                if (strLength > MAX_LENGTH_WITH_NO_TOC) {
                    // 按固定大小分割，在换行符处断开
                    int end = length;
                    for (int i = chapterOffset + MAX_LENGTH_WITH_NO_TOC; i < length; i++) {
                        if (buffer[i] == BLANK) {
                            end = i;
                            break;
                        }
                    }
                    
                    // 创建章节，标题格式为"第X章(Y)"
                    TxtChapter chapter = new TxtChapter(LibreraApp.context.getString(R.string.chapter) + blockPos + "章(" + chapterPos + ")", 
                        toc.isEmpty() ? curOffset : toc.get(toc.size() - 1).end,
                        (toc.isEmpty() ? curOffset : toc.get(toc.size() - 1).end) + (end - chapterOffset));
                    toc.add(chapter);
                    
                    strLength -= (end - chapterOffset);
                    chapterOffset = end;
                } else {
                    // 剩余内容不足一章，保留到下次处理
                    System.arraycopy(buffer, length - strLength, buffer, 0, strLength);
                    length -= strLength;
                    bufStart = strLength;
                    strLength = 0;
                }
            }
            
            curOffset += length;
        }
        
        // 处理剩余内容
        if (bufStart > 100 || toc.isEmpty()) {
            TxtChapter chapter = new TxtChapter(LibreraApp.context.getString(R.string.chapter) + blockPos + "章(" + chapterPos + ")",
                toc.isEmpty() ? curOffset : toc.get(toc.size() - 1).end,
                (toc.isEmpty() ? curOffset : toc.get(toc.size() - 1).end) + bufStart);
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