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

public class TxtExtract {

    public static final String OUT_FB2_XML = "txt.html";

    static char[] endChars = new char[]{'.', '!', '?', ';'};

    public static String foramtUB(String line) {
        if (line != null && line.trim()
                                .startsWith("(*)") && TxtUtils.isLastCharEq(line, endChars)) {
            line = "<b><u>" + line + "</u></b>";
        }
        return line;
    }

    public static String extract1(String inputPath, String outputDir) throws IOException {
        return extract(inputPath, outputDir);
    }

    public static String extract(String inputPath, String outputDir) throws IOException {
        File inputFile = new File(inputPath);
        long inputLastModified = inputFile.lastModified();
        String cacheFileName = inputPath.hashCode() + "_" + inputLastModified + "_" + AppState.get().isPreText + OUT_FB2_XML;
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
            new InputStreamReader(new FileInputStream(inputPath), charset), 256 * 1024);
        
        BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(cacheFile), StandardCharsets.UTF_8), 256 * 1024);

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

            StringBuilder batch = new StringBuilder(64 * 1024);
            int batchSize = 0;
            final int MAX_BATCH = 1000;
            
            String line;
            while ((line = input.readLine()) != null) {
                String outLn = processLine(line, replacements, isJSON);
                
                if (outLn != null) {
                    batch.append(outLn).append('\n');
                    batchSize++;
                    
                    if (batchSize >= MAX_BATCH) {
                        writer.write(batch.toString());
                        batch.setLength(0);
                        batchSize = 0;
                    }
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