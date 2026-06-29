package com.foobnix.ext;

import android.text.TextUtils;

import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.ExtUtils;

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

public class TxtAnalyzerOptimized {

    private static final int BUFFER_SIZE = 1024 * 1024;
    private static final int MAX_LENGTH_WITH_TOC = 102400;
    private static final int MAX_LENGTH_WITH_NO_TOC = 10 * 1024;
    private static final byte BLANK = 0x0a;
    private static final byte CR = 0x0d;

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

    private static TxtAnalyzerOptimized instance;
    private static String lastFilePath;
    private static long lastFileModified;

    private Charset charset;
    private String tocRule;
    private long fileSize;
    private MappedByteBuffer mappedBuffer;
    private FileChannel channel;

    private List<TxtChapter> cachedChapters;

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

    public static void clear() {
        if (instance != null) {
            instance.close();
        }
        instance = null;
        lastFilePath = null;
        lastFileModified = 0;
    }

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

    public List<TxtChapter> getChapterList(String filePath) throws IOException {
        if (cachedChapters != null) {
            return cachedChapters;
        }

        File file = new File(filePath);
        fileSize = file.length();

        RandomAccessFile raf = new RandomAccessFile(file, "r");
        channel = raf.getChannel();
        mappedBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);

        try {
            detectEncoding(filePath);
            int bomSize = checkBOM();

            String contentSample = readSampleContent(bomSize);
            selectBestTocRule(contentSample);

            cachedChapters = analyze(bomSize);
            return cachedChapters;
        } finally {
            close();
        }
    }

    public String getChapterContent(String filePath, TxtChapter chapter) throws IOException {
        long start = chapter.start;
        long end = chapter.end;

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

    private void detectEncoding(String filePath) throws IOException {
        if (AppState.get().isCharacterEncoding) {
            charset = Charset.forName(AppState.get().characterEncoding);
        } else {
            java.io.FileInputStream fis = new java.io.FileInputStream(filePath);
            try {
                byte[] bomBuffer = new byte[3];
                int read = fis.read(bomBuffer);
                if (read >= 3 && bomBuffer[0] == (byte) 0xEF && bomBuffer[1] == (byte) 0xBB && bomBuffer[2] == (byte) 0xBF) {
                    charset = StandardCharsets.UTF_8;
                } else {
                    fis.reset();
                    String encoding = ExtUtils.determineTxtEncoding(fis);
                    charset = TextUtils.isEmpty(encoding) ? StandardCharsets.UTF_8 : Charset.forName(encoding);
                }
            } finally {
                fis.close();
            }
        }
    }

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

    private String readSampleContent(int bomSize) {
        int sampleSize = Math.min(BUFFER_SIZE, (int) (fileSize - bomSize));
        byte[] buffer = new byte[sampleSize];

        mappedBuffer.position(bomSize);
        mappedBuffer.get(buffer);

        return new String(buffer, charset);
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

    private List<TxtChapter> analyze(int bomSize) {
        if (TextUtils.isEmpty(tocRule)) {
            return analyzeWithoutRule(bomSize);
        }
        return analyzeWithRule(bomSize);
    }

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

                if (toc.isEmpty() && seekPos == 0 && chapterStart != 0) {
                    if (!chapterContent.trim().isEmpty()) {
                        TxtChapter qyChapter = new TxtChapter("前言", curOffset, curOffset + chapterLength);
                        toc.add(qyChapter);
                    }
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

                seekPos += chapterContent.length() + matcher.group().length();
            }

            if (!toc.isEmpty()) {
                toc.get(toc.size() - 1).end = curOffset + end;
            }

            curOffset += end;
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

                for (i = offset; i < bytesToRead && chunkSize < MAX_LENGTH_WITH_NO_TOC; i++) {
                    chunkSize++;
                    if (buffer[i] == BLANK || buffer[i] == CR) {
                        chunkSize = i - offset + 1;
                        break;
                    }
                }

                if (chunkSize >= MAX_LENGTH_WITH_NO_TOC) {
                    for (; i < bytesToRead; i++) {
                        if (buffer[i] == BLANK || buffer[i] == CR) {
                            chunkSize = i - offset + 1;
                            break;
                        }
                    }
                }

                if (curOffset + offset + chunkSize > chapterStart + MAX_LENGTH_WITH_NO_TOC) {
                    TxtChapter chapter = new TxtChapter("第" + chapterIndex + "章", chapterStart, curOffset + offset + chunkSize);
                    toc.add(chapter);
                    chapterStart = curOffset + offset + chunkSize;
                    chapterIndex++;
                }

                offset += chunkSize;
            }

            curOffset += bytesToRead;
        }

        if (chapterStart < fileSize) {
            TxtChapter chapter = new TxtChapter("第" + chapterIndex + "章", chapterStart, fileSize);
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