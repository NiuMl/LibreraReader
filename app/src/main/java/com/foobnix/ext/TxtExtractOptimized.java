package com.foobnix.ext;

import android.text.TextUtils;

import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.model.BookCSS;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class TxtExtractOptimized {

    public static final String OUT_EPUB = ".epub";

    private static final int BATCH_LINES = 1000;
    private static final int BUFFER_SIZE = 64 * 1024;

    public static String extract(String inputPath, String outputDir) throws IOException {
        File inputFile = new File(inputPath);
        String cacheFileName = getCacheFileName(inputPath);
        File cacheFile = new File(outputDir, cacheFileName + OUT_EPUB);

        if (cacheFile.exists()) {
            return cacheFile.getPath();
        }

        Charset charset = detectEncodingFast(inputPath);

        RandomAccessFile raf = new RandomAccessFile(inputFile, "r");
        FileChannel channel = raf.getChannel();
        long fileSize = channel.size();
        MappedByteBuffer mappedBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);

        FileOutputStream fos = new FileOutputStream(cacheFile);
        ZipOutputStream zos = new ZipOutputStream(fos);
        zos.setLevel(0);

        try {
            int bomSize = checkBOM(mappedBuffer);

            writeToZip(zos, "mimetype", mimetype);
            writeToZip(zos, "META-INF/container.xml", container_xml);
            writeToZip(zos, "OEBPS/content.opf", content_opf);

            List<String> titles = extractTitles(mappedBuffer, fileSize, bomSize, charset);
            writeToZip(zos, "OEBPS/fb2.ncx", generateNCX(titles));

            mappedBuffer.position(bomSize);
            writeFb2ContentBatch(zos, mappedBuffer, fileSize, charset);

        } finally {
            zos.close();
            fos.close();
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
            }
            channel.close();
            raf.close();
        }

        return cacheFile.getPath();
    }

    public static String getCacheFileName(String inputPath) {
        String fileNameOriginal = inputPath +
                AppState.get().isShowFooterNotesInText +
                BookCSS.get().isAutoHypens +
                AppState.get().isBionicMode +
                AppState.get().enableImageScale +
                BookCSS.get().documentStyle +
                BookCSS.get().isCapitalLetter;
        return String.valueOf(fileNameOriginal.hashCode());
    }

    private static Charset detectEncodingFast(String filePath) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(filePath);

            byte[] bomBuffer = new byte[3];
            int read = fis.read(bomBuffer);
            if (read >= 3 && bomBuffer[0] == (byte) 0xEF && bomBuffer[1] == (byte) 0xBB && bomBuffer[2] == (byte) 0xBF) {
                return StandardCharsets.UTF_8;
            }

            byte[] buffer = new byte[1024];
            read = fis.read(buffer);
            if (read > 0) {
                boolean hasMultiByte = false;
                for (int i = 0; i < read; i++) {
                    if (buffer[i] < 0) {
                        hasMultiByte = true;
                        break;
                    }
                }

                if (!hasMultiByte) {
                    return StandardCharsets.US_ASCII;
                }

                boolean isGBK = false;
                for (int i = 0; i < read - 1; i++) {
                    if ((buffer[i] & 0xFF) >= 0x81 && (buffer[i] & 0xFF) <= 0xFE) {
                        if ((buffer[i + 1] & 0xFF) >= 0x40 && (buffer[i + 1] & 0xFF) <= 0xFE) {
                            isGBK = true;
                            break;
                        }
                    }
                }

                if (isGBK) {
                    return Charset.forName("GBK");
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    LOG.e(e);
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static int checkBOM(MappedByteBuffer buffer) {
        if (buffer.remaining() >= 3) {
            byte b0 = buffer.get(0);
            byte b1 = buffer.get(1);
            byte b2 = buffer.get(2);
            if (b0 == (byte) 0xEF && b1 == (byte) 0xBB && b2 == (byte) 0xBF) {
                return 3;
            }
        }
        return 0;
    }

    private static List<String> extractTitles(MappedByteBuffer mappedBuffer, long fileSize, int bomSize, Charset charset) {
        List<String> titles = new ArrayList<>();
        long position = bomSize;
        int titleCount = 0;
        int maxTitles = 200;

        while (position < fileSize && titleCount < maxTitles) {
            int lineStart = (int) position;
            while (position < fileSize) {
                byte b = mappedBuffer.get((int) position);
                position++;
                if (b == '\n') {
                    break;
                }
            }

            int lineLength = (int) (position - lineStart);
            if (lineLength > 1) {
                byte[] lineBytes = new byte[lineLength];
                System.arraycopy(mappedBuffer.array(), lineStart, lineBytes, 0, lineLength);

                int end = lineLength - 1;
                if (end >= 0 && lineBytes[end] == '\r') {
                    end--;
                }
                if (end >= 0) {
                    String line = new String(lineBytes, 0, end + 1, charset).trim();
                    if (isTitle(line)) {
                        titles.add(line);
                        titleCount++;
                    }
                }
            }
        }

        return titles;
    }

    private static boolean isTitle(String line) {
        if (line.length() < 2 || line.length() > 60) {
            return false;
        }
        if (line.matches("^[ 　\\t]{0,4}(?:第\\s{0,4}[\\d〇零一二两三四五六七八九十百千万]+\\s{0,4}(?:章|节|卷|集|部|篇)).{0,30}$")) {
            return true;
        }
        if (line.matches("^[ 　\\t]{0,4}\\d{1,5}[:：,.， 、_—\\-].{1,30}$")) {
            return true;
        }
        if (line.matches("^[ 　\\t]{0,4}(?:序章|楔子|正文|终章|后记|尾声|番外).{0,20}$")) {
            return true;
        }
        if (line.matches("^[ 　\\t]{0,4}(?:[Cc]hapter|[Ss]ection)\\s{0,4}\\d{1,4}.{0,20}$")) {
            return true;
        }
        return false;
    }

    private static void writeFb2ContentBatch(ZipOutputStream zos, MappedByteBuffer mappedBuffer, long fileSize, Charset charset) throws IOException {
        ZipEntry entry = new ZipEntry("OEBPS/fb2.fb2");
        zos.putNextEntry(entry);

        zos.write(fb2HeaderBytes);

        long position = mappedBuffer.position();
        byte[] lineBuffer = new byte[BUFFER_SIZE];
        int linePos = 0;
        int linesInBatch = 0;
        StringBuilder batch = new StringBuilder(BUFFER_SIZE * 2);

        while (position < fileSize) {
            byte b = mappedBuffer.get((int) position);
            position++;

            if (b == '\n') {
                if (linePos > 0) {
                    String line = new String(lineBuffer, 0, linePos, charset);
                    batch.append(processLine(line));
                    linesInBatch++;

                    if (linesInBatch >= BATCH_LINES) {
                        zos.write(batch.toString().getBytes(StandardCharsets.UTF_8));
                        batch.setLength(0);
                        linesInBatch = 0;
                    }
                }
                linePos = 0;
            } else if (b != '\r') {
                if (linePos < lineBuffer.length - 1) {
                    lineBuffer[linePos++] = b;
                } else {
                    String line = new String(lineBuffer, 0, linePos, charset);
                    batch.append(processLine(line));
                    linesInBatch++;
                    linePos = 0;
                }
            }
        }

        if (linePos > 0) {
            String line = new String(lineBuffer, 0, linePos, charset);
            batch.append(processLine(line));
        }

        if (batch.length() > 0) {
            zos.write(batch.toString().getBytes(StandardCharsets.UTF_8));
        }

        zos.write(fb2FooterBytes);
        zos.closeEntry();
    }

    private static String processLine(String line) {
        if (AppState.get().isPreText) {
            line = retab(line, 8);
            line = TextUtils.htmlEncode(line);
            if (TxtUtils.isLineStartEndUpperCase(line)) {
                return "<b>" + line + "</b>\n";
            }
            return line + "\n";
        }

        if (line.trim().length() == 0) {
            return "<p>&nbsp;</p>\n";
        }

        if (TxtUtils.isLineStartEndUpperCase(line)) {
            return "<p><b>" + format(line) + "</b></p>\n";
        }

        return "<p>" + format(line) + "</p>\n";
    }

    private static String retab(final String text, final int tabstop) {
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

    private static String format(String line) {
        line = line.replace("\n", "").replace("\r", "");
        line = TextUtils.htmlEncode(line);
        line = line.trim();
        line = line.replace("*", "");
        return line;
    }

    private static void writeToZip(ZipOutputStream zos, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static String generateNCX(List<String> titles) {
        StringBuilder ncx = new StringBuilder();
        ncx.append(ncxHeader);
        for (int i = 0; i < titles.size(); i++) {
            ncx.append("<navPoint id=\"nav-").append(i + 1).append("\" playOrder=\"").append(i + 1).append("\">\n");
            ncx.append("<navLabel>\n");
            ncx.append("<text>").append(TextUtils.htmlEncode(titles.get(i))).append("</text>\n");
            ncx.append("</navLabel>\n");
            ncx.append("<content src=\"fb2.fb2\"/>\n");
            ncx.append("</navPoint>\n");
        }
        ncx.append(ncxFooter);
        return ncx.toString();
    }

    private static final String mimetype = "application/epub+zip";

    private static final String container_xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\" version=\"1.0\">\n" +
            "<rootfiles>\n" +
            "<rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n" +
            "</rootfiles>\n" +
            "</container>";

    private static final String content_opf = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"2.0\" unique-identifier=\"BookId\">\n" +
            "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n" +
            "<dc:identifier id=\"BookId\">urn:uuid:</dc:identifier>\n" +
            "<dc:title>Untitled</dc:title>\n" +
            "<dc:language>zh</dc:language>\n" +
            "</metadata>\n" +
            "<manifest>\n" +
            "<item id=\"ncx\" href=\"fb2.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n" +
            "<item id=\"fb2\" href=\"fb2.fb2\" media-type=\"application/x-fictionbook+xml\"/>\n" +
            "</manifest>\n" +
            "<spine toc=\"ncx\">\n" +
            "<itemref idref=\"fb2\"/>\n" +
            "</spine>\n" +
            "</package>";

    private static final String ncxHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">\n" +
            "<head>\n" +
            "<meta name=\"dtb:uid\" content=\"urn:uuid:\"/>\n" +
            "<meta name=\"dtb:depth\" content=\"1\"/>\n" +
            "<meta name=\"dtb:totalPageCount\" content=\"0\"/>\n" +
            "<meta name=\"dtb:maxPageNumber\" content=\"0\"/>\n" +
            "</head>\n" +
            "<docTitle>\n" +
            "<text>Table of Contents</text>\n" +
            "</docTitle>\n" +
            "<navMap>\n";

    private static final String ncxFooter = "</navMap>\n</ncx>\n";

    private static final byte[] fb2HeaderBytes = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<FictionBook xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\">\n" +
            "<description>\n" +
            "<title-info>\n" +
            "<book-title>Untitled</book-title>\n" +
            "</title-info>\n" +
            "</description>\n" +
            "<body>\n").getBytes(StandardCharsets.UTF_8);

    private static final byte[] fb2FooterBytes = "</body>\n</FictionBook>\n".getBytes(StandardCharsets.UTF_8);
}