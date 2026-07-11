package org.ebookdroid.droids;

import com.foobnix.android.utils.LOG;
import com.foobnix.ext.CacheZipUtils;
import com.foobnix.ext.TxtExtract;
import com.foobnix.ext.TxtParser;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.model.BookCSS;

import org.ebookdroid.core.codec.CodecDocument;
import org.ebookdroid.droids.mupdf.codec.MuPdfDocument;
import org.ebookdroid.droids.mupdf.codec.PdfContext;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * TXT文件上下文处理类。
 * <p>
 * 负责TXT文件的打开、缓存管理和EPUB转换。继承自PdfContext，
 * 将TXT文件转换为EPUB格式后再使用MuPDF渲染。
 */
public class TxtContext extends PdfContext {

    /** 缓存文件对象 */
    File cacheFile;

    /**
     * 获取缓存文件名。
     * <p>
     * 使用文件路径和修改时间作为缓存键，确保文件内容不变时使用缓存。
     *
     * @param fileNameOriginal 原始文件名
     * @return 缓存文件对象
     */
    @Override
    public File getCacheFileName(String fileNameOriginal) {
        File inputFile = new File(fileNameOriginal);
        String cacheKey = fileNameOriginal + "_" + inputFile.lastModified();
        cacheFile = new File(CacheZipUtils.CACHE_BOOK_DIR, cacheKey.hashCode() + ".epub");
        return cacheFile;
    }

    /**
     * 打开TXT文档。
     * <p>
     * 如果缓存文件存在则直接使用，否则将TXT转换为EPUB格式后再打开。
     * 优先使用原生解析器（TxtParser），如果不可用则回退到Java解析器（TxtExtract）。
     *
     * @param fileName 文件路径
     * @param password 密码（TXT文件不使用）
     * @return 文档对象
     */
    @Override
    public CodecDocument openDocumentInner(String fileName, String password) {
        if (cacheFile == null) {
            cacheFile = getCacheFileName(fileName);
        }

        String bookPath = fileName;

        if (!cacheFile.isFile()) {
            try {
                if (TxtParser.isLibraryAvailable()) {
                    bookPath = extractWithNativeParser(fileName, cacheFile.getPath());
                } else {
                    String htmlPath = TxtExtract.extract(fileName, CacheZipUtils.CACHE_TXT_DIR.getPath());
                    bookPath = convertHtmlToEpub(htmlPath, cacheFile.getPath());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            bookPath = cacheFile.getPath();
        }

        MuPdfDocument muPdfDocument = new MuPdfDocument(this, MuPdfDocument.FORMAT_PDF, bookPath, password);
        muPdfDocument.cacheFilename = bookPath;

        return muPdfDocument;
    }

    /**
     * 使用原生解析器提取TXT内容并转换为EPUB。
     * <p>
     * 调用TxtParser.so原生库进行解析，如果失败则回退到Java解析器。
     *
     * @param txtPath  TXT文件路径
     * @param epubPath 输出EPUB文件路径
     * @return EPUB文件路径
     * @throws IOException IO异常
     */
    private String extractWithNativeParser(String txtPath, String epubPath) throws IOException {
        TxtParser parser = new TxtParser();
        try {
            if (parser.open(txtPath)) {
                int result = parser.extractToEpub(epubPath);
                if (result == 0) {
                    return epubPath;
                }
            }
        } finally {
            parser.close();
        }

        String htmlPath = TxtExtract.extract(txtPath, CacheZipUtils.CACHE_TXT_DIR.getPath());
        return convertHtmlToEpub(htmlPath, epubPath);
    }

    /**
     * 将HTML文件转换为EPUB格式。
     * <p>
     * 创建标准的EPUB文件结构，包含mimetype、container.xml、content.opf、ncx和HTML内容。
     *
     * @param htmlPath  HTML文件路径
     * @param epubPath 输出EPUB文件路径
     * @return EPUB文件路径
     * @throws IOException IO异常
     */
    private String convertHtmlToEpub(String htmlPath, String epubPath) throws IOException {
        java.io.FileInputStream fis = new java.io.FileInputStream(htmlPath);
        byte[] htmlBytes = new byte[(int) new File(htmlPath).length()];
        fis.read(htmlBytes);
        fis.close();

        java.io.FileOutputStream fos = new java.io.FileOutputStream(epubPath);
        ZipOutputStream zos = new ZipOutputStream(fos);
        zos.setLevel(0);

        writeToZip(zos, "mimetype", "application/epub+zip");
        writeToZip(zos, "META-INF/container.xml", container_xml);
        writeToZip(zos, "OEBPS/content.opf", content_opf);
        writeToZip(zos, "OEBPS/fb2.ncx", ncx_content);

        ZipEntry entry = new ZipEntry("OEBPS/temp.html");
        zos.putNextEntry(entry);
        zos.write(htmlBytes);
        zos.closeEntry();

        zos.close();
        fos.close();

        return epubPath;
    }

    /**
     * 向ZIP输出流写入文件内容。
     *
     * @param zos     ZIP输出流
     * @param name    文件名
     * @param content 文件内容
     * @throws IOException IO异常
     */
    private static void writeToZip(ZipOutputStream zos, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zos.closeEntry();
    }

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
            "<item id=\"html\" href=\"temp.html\" media-type=\"application/xhtml+xml\"/>\n" +
            "</manifest>\n" +
            "<spine toc=\"ncx\">\n" +
            "<itemref idref=\"html\"/>\n" +
            "</spine>\n" +
            "</package>";

    private static final String ncx_content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
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
            "<navMap>\n" +
            "</navMap>\n" +
            "</ncx>\n";
}