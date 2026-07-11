package com.foobnix.ext;

import android.text.TextUtils;

import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.hypen.HypenUtils;
import com.foobnix.model.AppData;
import com.foobnix.model.AppSP;
import com.foobnix.model.AppState;
import com.foobnix.model.SimpleMeta;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.model.BookCSS;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * TXT文件提取与HTML转换工具类。
 * <p>
 * 负责将TXT文件内容转换为HTML格式，支持编码检测、文本替换、连字符处理等功能。
 * 提供缓存机制以避免重复转换，提高大文件处理性能。
 */
public class TxtExtract {

    /** 输出HTML文件的后缀名 */
    public static final String OUT_FB2_XML = "txt.html";

    /** 句子结束字符数组，用于判断标题行 */
    static char[] endChars = new char[]{'.', '!', '?', ';'};

    /**
     * 格式化加粗下划线文本。
     * <p>
     * 如果行以"(*)"开头且以句子结束符结尾，则添加加粗和下划线标签。
     *
     * @param line 输入文本行
     * @return 格式化后的文本行
     */
    public static String foramtUB(String line) {
        if (line != null && line.trim()
                                .startsWith("(*)") && TxtUtils.isLastCharEq(line, endChars)) {
            line = "<b><u>" + line + "</u></b>";
        }
        return line;
    }

    /**
     * 提取TXT文件内容（别名方法）。
     *
     * @param inputPath 输入文件路径
     * @param outputDir 输出目录路径
     * @return 生成的HTML文件路径
     * @throws IOException IO异常
     */
    public static String extract1(String inputPath, String outputDir) throws IOException {
        return extract(inputPath, outputDir);
    }

    /**
     * 提取TXT文件内容并转换为HTML格式。
     * <p>
     * 核心处理流程：
     * 1. 检查缓存文件是否存在，存在则直接返回
     * 2. 检测文件编码（自动或使用用户指定）
     * 3. 逐行读取并处理文本
     * 4. 生成HTML文件并缓存
     *
     * @param inputPath 输入文件路径
     * @param outputDir 输出目录路径
     * @return 生成的HTML文件路径
     * @throws IOException IO异常
     */
    public static String extract(String inputPath, String outputDir) throws IOException {
        File inputFile = new File(inputPath);
        long inputLastModified = inputFile.lastModified();
        String cacheFileName = inputPath.hashCode() + "_" + inputLastModified + OUT_FB2_XML;
        File cacheFile = new File(outputDir, cacheFileName);

        if (cacheFile.exists()) {
            return cacheFile.getPath();
        }

        boolean isJSON = inputPath.endsWith(".json");

        Charset charset = StandardCharsets.UTF_8;
        if (AppState.get().isCharacterEncoding) {
            charset = Charset.forName(AppState.get().characterEncoding);
        } else {
            FileInputStream fis = new FileInputStream(inputPath);
            String encoding = ExtUtils.determineTxtEncoding(fis);
            fis.close();
            if (!TextUtils.isEmpty(encoding)) {
                charset = Charset.forName(encoding);
            }
        }

        BufferedReader input = new BufferedReader(
            new InputStreamReader(new FileInputStream(inputPath), charset), 1024 * 1024);
        
        BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(cacheFile), StandardCharsets.UTF_8), 1024 * 1024);

        try {
            StringBuilder header = new StringBuilder(512);
            header.append("<!DOCTYPE html>\n<html>\n");
            if (AppState.get().isPreText) {
                header.append("<head><style>@page{margin:0px 0.5em} pre{margin:0px;white-space:pre !important;} {body:margin:0px}</style></head>\n");
            } else {
                header.append("<head><style>p,p+p{margin:0}</style></head>\n");
            }
            header.append("<body>\n");
            if (AppState.get().isPreText) {
                header.append("<pre>\n");
            }
            if (AppState.get().isLineBreaksText) {
                header.append("<p>\n");
            }
            writer.write(header.toString());

            if (BookCSS.get().isAutoHypens) {
                HypenUtils.applyLanguage(AppSP.get().hypenLang);
            }

            List<SimpleMeta> replacements = AppData.get().getAllTextReplaces();

            StringBuilder batch = new StringBuilder(256 * 1024);
            int batchSize = 0;
            final int MAX_BATCH = 2000;
            
            String line;
            while ((line = input.readLine()) != null) {
                processLineFast(line, replacements, isJSON, batch);
                batchSize++;
                
                if (batchSize >= MAX_BATCH) {
                    writer.write(batch.toString());
                    batch.setLength(0);
                    batchSize = 0;
                }
            }
            
            if (batchSize > 0) {
                writer.write(batch.toString());
            }

            StringBuilder footer = new StringBuilder(64);
            if (AppState.get().isLineBreaksText) {
                footer.append("</p>\n");
            }
            if (AppState.get().isPreText) {
                footer.append("</pre>\n");
            }
            footer.append("</body></html>\n");
            writer.write(footer.toString());
            
        } finally {
            input.close();
            writer.close();
        }

        return cacheFile.getPath();
    }
    
    /**
     * 处理单行文本，转换为HTML格式。
     * <p>
     * 根据用户设置（预格式化、换行处理等）进行不同的转换逻辑。
     *
     * @param line         输入文本行
     * @param replacements 文本替换规则列表
     * @param isJSON       是否为JSON文件
     * @return 转换后的HTML字符串
     */
    private static String processLine(String line, List<SimpleMeta> replacements, boolean isJSON) {
        String outLn = null;

        if (AppState.get().isPreText) {
            outLn = retab(line, 8);
            outLn = TextUtils.htmlEncode(outLn);
            if (TxtUtils.isLineStartEndUpperCase(outLn)) {
                outLn = "<b>" + outLn + "</b>";
            }
        } else {
            if (AppState.get().isLineBreaksText) {
                if (line.trim().length() == 0) {
                    outLn = "<br/>";
                } else {
                    outLn = format(line, replacements);
                }
            } else {
                if (line.trim().length() == 0) {
                    outLn = "<p>&nbsp;</p>";
                } else if (TxtUtils.isLineStartEndUpperCase(line)) {
                    outLn = "<b>" + format(line, replacements) + "</b>";
                } else if (line.contains("Title:")) {
                    outLn = "<b>" + format(line, replacements) + "</b>";
                } else {
                    outLn = "<p>" + format(line, replacements) + "</p>";
                }
            }
        }
        
        if (isJSON && outLn != null) {
            outLn = outLn.replace(",", ",<br/>");
        }

        if (outLn != null) {
            outLn = Fb2Extractor.accurateLine(outLn);
        }
        
        return outLn;
    }

    /**
     * 快速处理单行文本，直接追加到批量缓冲区。
     * <p>
     * 优化版本，避免创建中间字符串对象，提高处理性能。
     *
     * @param line         输入文本行
     * @param replacements 文本替换规则列表
     * @param isJSON       是否为JSON文件
     * @param batch        批量输出缓冲区
     */
    private static void processLineFast(String line, List<SimpleMeta> replacements, boolean isJSON, StringBuilder batch) {
        if (AppState.get().isPreText) {
            String outLn = retab(line, 8);
            outLn = TextUtils.htmlEncode(outLn);
            if (TxtUtils.isLineStartEndUpperCase(outLn)) {
                batch.append("<b>").append(outLn).append("</b>\n");
            } else {
                batch.append(outLn).append('\n');
            }
        } else {
            if (AppState.get().isLineBreaksText) {
                if (line.trim().length() == 0) {
                    batch.append("<br/>\n");
                } else {
                    formatFast(line, replacements, isJSON, batch, false);
                }
            } else {
                if (line.trim().length() == 0) {
                    batch.append("<p>&nbsp;</p>\n");
                } else if (TxtUtils.isLineStartEndUpperCase(line)) {
                    batch.append("<b>");
                    formatFast(line, replacements, isJSON, batch, true);
                } else if (line.contains("Title:")) {
                    batch.append("<b>");
                    formatFast(line, replacements, isJSON, batch, true);
                } else {
                    batch.append("<p>");
                    formatFast(line, replacements, isJSON, batch, true);
                }
            }
        }
    }

    /**
     * 快速格式化文本行，直接追加到批量缓冲区。
     * <p>
     * 包含HTML转义、连字符处理、文本替换等操作。
     *
     * @param line       输入文本行
     * @param replacements 文本替换规则列表
     * @param isJSON     是否为JSON文件
     * @param batch      批量输出缓冲区
     * @param withClose  是否需要添加闭合标签
     */
    private static void formatFast(String line, List<SimpleMeta> replacements, boolean isJSON, StringBuilder batch, boolean withClose) {
        line = line.replace("\n", "").replace("\r", "");
        line = TextUtils.htmlEncode(line);
        if (BookCSS.get().isAutoHypens && TxtUtils.isNotEmpty(AppSP.get().hypenLang)) {
            line = HypenUtils.applyHypnes(line, replacements);
        }
        line = line.trim();
        if (replacements != null && AppState.get().isEnableTextReplacement) {
            for (SimpleMeta simpleMeta : replacements) {
                if (simpleMeta != null && TxtUtils.isNotEmpty(simpleMeta.name)) {
                    line = line.replace(simpleMeta.name, simpleMeta.path);
                }
            }
        }
        line = line.replace("*", "");
        line = foramtUB(line);
        line = Fb2Extractor.accurateLine(line);
        
        if (isJSON) {
            line = line.replace(",", ",<br/>");
        }
        
        batch.append(line);
        if (withClose) {
            batch.append("</").append(isJSON ? "p>" : "p>").append('\n');
        } else {
            batch.append('\n');
        }
    }

    /**
     * 将制表符转换为空格。
     * <p>
     * 根据指定的制表位宽度，将文本中的制表符替换为相应数量的空格。
     *
     * @param text    输入文本
     * @param tabstop 制表位宽度
     * @return 转换后的文本
     */
    public static String retab(final String text, final int tabstop) {
        final char[] input = text.toCharArray();
        final StringBuilder sb = new StringBuilder(input.length + 8);

        int linepos = 0;
        for (int i = 0; i < input.length; i++) {
            final char ch = input[i];
            if (ch == '\t') {
                do {
                    sb.append(' ');
                    linepos++;
                } while (linepos % tabstop != 0);
            } else {
                sb.append(ch);
                linepos++;
            }
        }

        return sb.toString();
    }

    /**
     * 格式化文本行。
     * <p>
     * 包含换行符移除、HTML转义、连字符处理、文本替换等操作。
     *
     * @param line         输入文本行
     * @param replacements 文本替换规则列表
     * @return 格式化后的文本
     */
    public static String format(String line, List<SimpleMeta> replacements) {
        line = line.replace("\n", "").replace("\r", "");
        line = TextUtils.htmlEncode(line);
        if (BookCSS.get().isAutoHypens && TxtUtils.isNotEmpty(AppSP.get().hypenLang)) {
            line = HypenUtils.applyHypnes(line, replacements);
        }
        line = line.trim();
        if (replacements != null && AppState.get().isEnableTextReplacement) {
            for (SimpleMeta simpleMeta : replacements) {
                if (simpleMeta != null && TxtUtils.isNotEmpty(simpleMeta.name)) {
                    line = line.replace(simpleMeta.name, simpleMeta.path);
                }
            }
        }
        line = line.replace("*", "");
        line = foramtUB(line);
        return line;
    }
}