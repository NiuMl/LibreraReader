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

public class TxtContext extends PdfContext {

    File cacheFile;

    @Override
    public File getCacheFileName(String fileNameOriginal) {
        fileNameOriginal = fileNameOriginal +
                AppState.get().isShowFooterNotesInText +
                BookCSS.get().isAutoHypens +
                AppState.get().isBionicMode +
                AppState.get().enableImageScale +
                BookCSS.get().documentStyle +
                BookCSS.get().isCapitalLetter;
        cacheFile = new File(CacheZipUtils.CACHE_BOOK_DIR, fileNameOriginal.hashCode() + ".epub");
        return cacheFile;
    }

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