package com.foobnix.ext;

import android.text.TextUtils;

import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.ExtUtils;

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

public class TxtAnalyzer {
    
    private static final int BUFFER_SIZE = 512 * 1024;
    private static final int MAX_LENGTH_WITH_TOC = 102400;
    private static final int MAX_LENGTH_WITH_NO_TOC = 10 * 1024;
    private static final byte BLANK = 0x0a;
    
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
    
    private static TxtAnalyzer instance;
    private static String lastFilePath;
    private static long lastFileModified;
    
    private Charset charset;
    private String tocRule;
    
    private List<TxtChapter> cachedChapters;
    
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
    
    public static void clear() {
        instance = null;
        lastFilePath = null;
        lastFileModified = 0;
    }
    
    public List<TxtChapter> getChapterList(String filePath) throws IOException {
        if (cachedChapters != null) {
            return cachedChapters;
        }
        
        FileInputStream fis = new FileInputStream(filePath);
        try {
            detectEncoding(filePath, fis);
            
            String contentSample = readSampleContent(fis);
            fis.close();
            
            selectBestTocRule(contentSample);
            
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
    
    public String getChapterContent(String filePath, TxtChapter chapter) throws IOException {
        long start = chapter.start;
        long end = chapter.end;
        
        FileInputStream fis = new FileInputStream(filePath);
        try {
            fis.skip(start);
            int length = (int) (end - start);
            byte[] buffer = new byte[length];
            int read = fis.read(buffer);
            
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
    
    private void detectEncoding(String filePath, InputStream fis) throws IOException {
        if (AppState.get().isCharacterEncoding) {
            charset = Charset.forName(AppState.get().characterEncoding);
        } else {
            String encoding = ExtUtils.determineTxtEncoding(fis);
            charset = TextUtils.isEmpty(encoding) ? StandardCharsets.UTF_8 : Charset.forName(encoding);
        }
    }
    
    private String readSampleContent(InputStream fis) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int length = fis.read(buffer);
        if (length == -1) {
            return "";
        }
        
        int start = 0;
        if (length >= 3 && buffer[0] == (byte) 0xEF && buffer[1] == (byte) 0xBB && buffer[2] == (byte) 0xBF) {
            start = 3;
        }
        
        return new String(buffer, start, length - start, charset);
    }
    
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
    
    private List<TxtChapter> analyze(InputStream fis) throws IOException {
        if (TextUtils.isEmpty(tocRule)) {
            return analyzeWithoutRule(fis);
        }
        
        return analyzeWithRule(fis);
    }
    
    private List<TxtChapter> analyzeWithRule(InputStream fis) throws IOException {
        Pattern pattern = Pattern.compile(tocRule, Pattern.MULTILINE);
        List<TxtChapter> toc = new ArrayList<>();
        
        byte[] buffer = new byte[BUFFER_SIZE];
        long curOffset = 0;
        int length;
        int bufStart = 3;
        
        fis.read(buffer, 0, 3);
        if (buffer[0] == (byte) 0xEF && buffer[1] == (byte) 0xBB && buffer[2] == (byte) 0xBF) {
            bufStart = 0;
            curOffset = 3;
        }
        
        int bytesRead;
        while ((bytesRead = fis.read(buffer, bufStart, BUFFER_SIZE - bufStart)) > 0) {
            length = bytesRead;
            int end = bufStart + length;
            if (end == BUFFER_SIZE) {
                for (int i = bufStart + length - 1; i >= 0; i--) {
                    if (buffer[i] == BLANK) {
                        end = i;
                        break;
                    }
                }
            }
            
            String blockContent = new String(buffer, 0, end, charset);
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
                
                if (toc.isEmpty() && seekPos == 0 && chapterStart != 0) {
                    if (chapterContent.trim().isEmpty()) {
                        continue;
                    }
                    TxtChapter qyChapter = new TxtChapter("前言", curOffset, curOffset + chapterLength);
                    toc.add(qyChapter);
                } else if (!toc.isEmpty()) {
                    TxtChapter lastChapter = toc.get(toc.size() - 1);
                    long newEnd = lastChapter.end + chapterLength;
                    
                    if (newEnd - lastChapter.start > MAX_LENGTH_WITH_TOC) {
                        List<TxtChapter> subChapters = splitChapter(lastChapter.title, lastChapter.start, newEnd);
                        toc.remove(toc.size() - 1);
                        toc.addAll(subChapters);
                    } else {
                        lastChapter.end = newEnd;
                    }
                }
                
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
    
    private List<TxtChapter> analyzeWithoutRule(InputStream fis) throws IOException {
        List<TxtChapter> toc = new ArrayList<>();
        
        byte[] buffer = new byte[BUFFER_SIZE];
        long curOffset = 0;
        int length = 0;
        int bufStart = 3;
        
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
                    int end = length;
                    for (int i = chapterOffset + MAX_LENGTH_WITH_NO_TOC; i < length; i++) {
                        if (buffer[i] == BLANK) {
                            end = i;
                            break;
                        }
                    }
                    
                    TxtChapter chapter = new TxtChapter("第" + blockPos + "章(" + chapterPos + ")", 
                        toc.isEmpty() ? curOffset : toc.get(toc.size() - 1).end,
                        (toc.isEmpty() ? curOffset : toc.get(toc.size() - 1).end) + (end - chapterOffset));
                    toc.add(chapter);
                    
                    strLength -= (end - chapterOffset);
                    chapterOffset = end;
                } else {
                    System.arraycopy(buffer, length - strLength, buffer, 0, strLength);
                    length -= strLength;
                    bufStart = strLength;
                    strLength = 0;
                }
            }
            
            curOffset += length;
        }
        
        if (bufStart > 100 || toc.isEmpty()) {
            TxtChapter chapter = new TxtChapter("第" + blockPos + "章(" + chapterPos + ")",
                toc.isEmpty() ? curOffset : toc.get(toc.size() - 1).end,
                (toc.isEmpty() ? curOffset : toc.get(toc.size() - 1).end) + bufStart);
            toc.add(chapter);
        }
        
        return toc;
    }
    
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