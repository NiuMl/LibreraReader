package com.foobnix.wifitransfer;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.foobnix.pdf.info.R;

/**
 * WiFi传输服务器
 * <p>
 * 实现一个轻量级的HTTP服务器，用于通过局域网浏览器上传书籍文件。
 * 支持GET请求返回上传页面，POST请求处理文件上传。
 * 使用线程池处理并发连接，支持多文件批量上传。
 */
public class WifiTransferServer {
    
    /**
     * 上传回调接口
     * <p>
     * 用于通知调用方上传结果和服务器状态。
     */
    public interface UploadCallback {
        /**
         * 文件上传成功时调用
         *
         * @param fileName 上传成功的文件名
         */
        void onUploadSuccess(String fileName);
        
        /**
         * 文件上传失败时调用
         *
         * @param error 错误信息
         */
        void onUploadError(String error);
        
        /**
         * 服务器启动失败时调用
         *
         * @param error 错误信息
         */
        void onServerStartError(String error);
    }
    
    /** 服务器监听端口 */
    private static final int PORT = 18080;
    /** 网络IO缓冲区大小 */
    private static final int BUFFER_SIZE = 8192;
    
    /** ServerSocket实例 */
    private ServerSocket serverSocket;
    /** 服务器运行状态标志 */
    private boolean isRunning = false;
    /** 线程池，用于处理客户端连接 */
    private ExecutorService threadPool;
    /** 上传文件保存目录 */
    private final File uploadDir;
    /** 上传回调监听器 */
    private final UploadCallback callback;
    /** Android上下文，用于获取字符串资源 */
    private final Context context;
    
    /**
     * 构造函数
     *
     * @param context    Android上下文
     * @param uploadDir  上传文件保存目录
     * @param callback   上传回调监听器
     */
    public WifiTransferServer(Context context, File uploadDir, UploadCallback callback) {
        this.context = context;
        this.uploadDir = uploadDir;
        this.callback = callback;
        // 确保上传目录存在
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
    }
    
    /**
     * 启动服务器
     * <p>
     * 在新线程中创建ServerSocket并开始监听端口。
     * 使用线程池处理每个客户端连接。
     */
    public void start() {
        threadPool = Executors.newCachedThreadPool();
        threadPool.execute(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                isRunning = true;
                // 循环接受客户端连接
                while (isRunning) {
                    try {
                        Socket socket = serverSocket.accept();
                        threadPool.execute(() -> handleClient(socket));
                    } catch (IOException e) {
                        // 如果是正常停止，不回调错误
                        if (isRunning && callback != null) {
                            callback.onServerStartError(e.getMessage());
                        }
                        break;
                    }
                }
            } catch (IOException e) {
                if (callback != null) {
                    callback.onServerStartError(e.getMessage());
                }
            }
        });
    }
    
    /**
     * 停止服务器
     * <p>
     * 停止监听，关闭ServerSocket，关闭线程池。
     */
    public void stop() {
        isRunning = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (threadPool != null) {
            threadPool.shutdownNow();
        }
    }
    
    /**
     * 获取服务器运行状态
     *
     * @return true表示服务器正在运行，false表示已停止
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * 获取服务器监听端口
     *
     * @return 端口号
     */
    public int getPort() {
        return PORT;
    }
    
    /**
     * 处理客户端请求
     * <p>
     * 解析HTTP请求，根据请求方法和路径分发到相应的处理方法。
     *
     * @param socket 客户端Socket连接
     */
    private void handleClient(Socket socket) {
        try {
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();
            
            byte[] requestBuffer = new byte[BUFFER_SIZE];
            int bytesRead = input.read(requestBuffer);
            
            // 无效请求
            if (bytesRead <= 0) {
                socket.close();
                return;
            }
            
            String requestStr = new String(requestBuffer, 0, bytesRead, StandardCharsets.UTF_8);
            int firstLineEnd = requestStr.indexOf("\r\n");
            
            // 无效请求格式
            if (firstLineEnd < 0) {
                socket.close();
                return;
            }
            
            // 解析HTTP请求行
            String firstLine = requestStr.substring(0, firstLineEnd);
            String[] parts = firstLine.split(" ");
            
            if (parts.length < 2) {
                socket.close();
                return;
            }
            
            String method = parts[0];
            String path = parts[1].split("\\?")[0];
            
            // 路由分发
            if ("GET".equals(method) && "/".equals(path)) {
                sendHtmlPage(output);
            } else if ("POST".equals(method) && "/upload".equals(path)) {
                handleUpload(input, output, requestBuffer, bytesRead);
            } else {
                send404(output);
            }
            
            output.flush();
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
            try {
                socket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
    
    /**
     * 发送HTML上传页面
     * <p>
     * 返回一个支持拖拽上传和点击选择文件的Web页面。
     *
     * @param output 输出流
     * @throws IOException IO异常
     */
    private void sendHtmlPage(OutputStream output) throws IOException {
        String html = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>WLAN Transfer</title>\n" +
                "    <style>\n" +
                "        body { font-family: Arial, sans-serif; max-width: 600px; margin: 50px auto; padding: 20px; }\n" +
                "        h1 { color: #333; text-align: center; }\n" +
                "        .upload-area { border: 2px dashed #ccc; border-radius: 10px; padding: 40px; text-align: center; margin: 20px 0; }\n" +
                "        .upload-area.dragover { border-color: #4CAF50; background-color: #f5f5f5; }\n" +
                "        input[type=file] { display: none; }\n" +
                "        .btn { background-color: #4CAF50; color: white; padding: 15px 30px; border: none; border-radius: 5px; cursor: pointer; font-size: 16px; }\n" +
                "        .btn:hover { background-color: #45a049; }\n" +
                "        .status { margin-top: 20px; padding: 10px; border-radius: 5px; text-align: center; display: none; }\n" +
                "        .success { background-color: #d4edda; color: #155724; display: block; }\n" +
                "        .error { background-color: #f8d7da; color: #721c24; display: block; }\n" +
                "        .file-list { margin-top: 20px; }\n" +
                "        .file-item { padding: 10px; border-bottom: 1px solid #eee; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <h1>WLAN Transfer</h1>\n" +
                "    <div class=\"upload-area\" id=\"uploadArea\">\n" +
                "        <p>Drag files here</p>\n" +
                "        <p>or</p>\n" +
                "        <button class=\"btn\" onclick=\"document.getElementById('fileInput').click()\">Select Files</button>\n" +
                "        <input type=\"file\" id=\"fileInput\" multiple accept=\".epub,.pdf,.txt,.fb2,.mobi,.azw3,.djvu,.doc,.docx,.rtf\">\n" +
                "    </div>\n" +
                "    <div id=\"status\" class=\"status\"></div>\n" +
                "    <div class=\"file-list\" id=\"fileList\"></div>\n" +
                "    <script>\n" +
                "        const uploadArea = document.getElementById('uploadArea');\n" +
                "        const fileInput = document.getElementById('fileInput');\n" +
                "        const statusDiv = document.getElementById('status');\n" +
                "        const fileList = document.getElementById('fileList');\n" +
                "\n" +
                "        uploadArea.addEventListener('dragover', (e) => {\n" +
                "            e.preventDefault();\n" +
                "            uploadArea.classList.add('dragover');\n" +
                "        });\n" +
                "\n" +
                "        uploadArea.addEventListener('dragleave', () => {\n" +
                "            uploadArea.classList.remove('dragover');\n" +
                "        });\n" +
                "\n" +
                "        uploadArea.addEventListener('drop', (e) => {\n" +
                "            e.preventDefault();\n" +
                "            uploadArea.classList.remove('dragover');\n" +
                "            uploadFiles(e.dataTransfer.files);\n" +
                "        });\n" +
                "\n" +
                "        fileInput.addEventListener('change', () => {\n" +
                "            uploadFiles(fileInput.files);\n" +
                "        });\n" +
                "\n" +
                "        function uploadFiles(files) {\n" +
                "            for (let i = 0; i < files.length; i++) {\n" +
                "                uploadFile(files[i]);\n" +
                "            }\n" +
                "        }\n" +
                "\n" +
                "        function uploadFile(file) {\n" +
                "            const formData = new FormData();\n" +
                "            formData.append('file', file);\n" +
                "\n" +
                "            showStatus('Uploading: ' + file.name, '');\n" +
                "\n" +
                "            fetch('/upload', {\n" +
                "                method: 'POST',\n" +
                "                body: formData\n" +
                "            }).then(response => response.json())\n" +
                "            .then(data => {\n" +
                "                if (data.success) {\n" +
                "                    showStatus('Upload successful: ' + data.fileName, 'success');\n" +
                "                    addFileToList(data.fileName);\n" +
                "                } else {\n" +
                "                    showStatus('Upload failed: ' + data.error, 'error');\n" +
                "                }\n" +
                "            }).catch(error => {\n" +
                "                showStatus('Upload failed: ' + error.message, 'error');\n" +
                "            });\n" +
                "        }\n" +
                "\n" +
                "        function showStatus(message, type) {\n" +
                "            statusDiv.className = 'status ' + type;\n" +
                "            statusDiv.textContent = message;\n" +
                "        }\n" +
                "\n" +
                "        function addFileToList(fileName) {\n" +
                "            const item = document.createElement('div');\n" +
                "            item.className = 'file-item';\n" +
                "            item.textContent = fileName;\n" +
                "            fileList.insertBefore(item, fileList.firstChild);\n" +
                "        }\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
        
        byte[] data = html.getBytes(StandardCharsets.UTF_8);
        sendResponse(output, "200 OK", "text/html; charset=UTF-8", data);
    }
    
    /**
     * 处理文件上传请求
     * <p>
     * 解析multipart/form-data格式的HTTP请求，提取文件名和文件数据，
     * 保存到上传目录，并通过回调通知结果。
     *
     * @param input            输入流
     * @param output           输出流
     * @param initialBuffer    初始读取的缓冲区
     * @param initialBytesRead 初始读取的字节数
     * @throws IOException IO异常
     */
    private void handleUpload(InputStream input, OutputStream output, byte[] initialBuffer, int initialBytesRead) throws IOException {
        String requestStr = new String(initialBuffer, 0, initialBytesRead, StandardCharsets.UTF_8);
        
        // 查找HTTP头部结束位置
        int headerEnd = requestStr.indexOf("\r\n\r\n");
        if (headerEnd < 0) {
            sendJsonResponse(output, false, null, context.getString(R.string.request_format_error));
            return;
        }
        
        // 解析HTTP头部
        String headersStr = requestStr.substring(0, headerEnd);
        int contentLength = 0;
        String contentType = null;
        
        String[] headerLines = headersStr.split("\r\n");
        for (String line : headerLines) {
            if (line.toLowerCase().startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.substring(16).trim());
            } else if (line.toLowerCase().startsWith("content-type:")) {
                contentType = line.substring(14).trim();
            }
        }
        
        // 验证内容类型
        if (contentType == null || !contentType.contains("multipart/form-data")) {
            sendJsonResponse(output, false, null, context.getString(R.string.unsupported_content_type));
            return;
        }
        
        // 提取boundary分隔符
        String boundary = null;
        int boundaryIndex = contentType.indexOf("boundary=");
        if (boundaryIndex >= 0) {
            boundary = contentType.substring(boundaryIndex + 9).trim();
            if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
                boundary = boundary.substring(1, boundary.length() - 1);
            }
        }
        
        if (boundary == null) {
            sendJsonResponse(output, false, null, context.getString(R.string.boundary_not_found));
            return;
        }
        
        // 读取完整的请求体
        int bodyStart = headerEnd + 4;
        int remainingBodySize = contentLength - (initialBytesRead - bodyStart);
        
        byte[] bodyBytes = new byte[contentLength];
        System.arraycopy(initialBuffer, bodyStart, bodyBytes, 0, initialBytesRead - bodyStart);
        
        int totalRead = initialBytesRead - bodyStart;
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        
        while (totalRead < contentLength && (bytesRead = input.read(buffer, 0, Math.min(buffer.length, contentLength - totalRead))) > 0) {
            System.arraycopy(buffer, 0, bodyBytes, totalRead, bytesRead);
            totalRead += bytesRead;
        }
        
        // 提取文件名和文件数据
        String fileName = null;
        byte[] fileData = null;
        
        byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.UTF_8);
        int boundaryStart = findBoundary(bodyBytes, boundaryBytes);
        
        if (boundaryStart >= 0) {
            int headerEndPos = findHeaderEnd(bodyBytes, boundaryStart + boundaryBytes.length);
            if (headerEndPos >= 0) {
                byte[] headerBytes = new byte[headerEndPos - boundaryStart];
                System.arraycopy(bodyBytes, boundaryStart, headerBytes, 0, headerBytes.length);
                String headerStr = new String(headerBytes, StandardCharsets.UTF_8);
                
                // 提取文件名
                int filenameIndex = headerStr.indexOf("filename=\"");
                if (filenameIndex >= 0) {
                    int filenameStart = filenameIndex + 10;
                    int filenameEnd = headerStr.indexOf("\"", filenameStart);
                    if (filenameEnd >= 0) {
                        fileName = URLDecoder.decode(headerStr.substring(filenameStart, filenameEnd), "UTF-8");
                    }
                }
                
                // 提取文件数据
                int fileStart = headerEndPos + 4;
                int nextBoundary = findBoundary(bodyBytes, fileStart, boundaryBytes);
                
                if (nextBoundary >= 0) {
                    int fileEnd = nextBoundary - 2;
                    int dataLength = fileEnd - fileStart;
                    fileData = new byte[dataLength];
                    System.arraycopy(bodyBytes, fileStart, fileData, 0, dataLength);
                }
            }
        }
        
        // 保存文件并返回结果
        if (fileName != null && fileData != null) {
            File outputFile = new File(uploadDir, sanitizeFileName(fileName));
            FileOutputStream fos = new FileOutputStream(outputFile);
            fos.write(fileData);
            fos.close();
            
            if (callback != null) {
                callback.onUploadSuccess(fileName);
            }
            
            sendJsonResponse(output, true, fileName, null);
        } else {
            sendJsonResponse(output, false, null, context.getString(R.string.file_not_found));
        }
    }
    
    /**
     * 从数据中查找boundary位置（从起始位置开始）
     *
     * @param data     数据数组
     * @param boundary boundary字节数组
     * @return boundary起始位置，未找到返回-1
     */
    private int findBoundary(byte[] data, byte[] boundary) {
        return findBoundary(data, 0, boundary);
    }
    
    /**
     * 从数据中查找boundary位置（从指定位置开始）
     *
     * @param data     数据数组
     * @param start    起始查找位置
     * @param boundary boundary字节数组
     * @return boundary起始位置，未找到返回-1
     */
    private int findBoundary(byte[] data, int start, byte[] boundary) {
        for (int i = start; i <= data.length - boundary.length; i++) {
            boolean match = true;
            for (int j = 0; j < boundary.length; j++) {
                if (data[i + j] != boundary[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * 查找multipart头部结束位置（\r\n\r\n）
     *
     * @param data  数据数组
     * @param start 起始查找位置
     * @return 头部结束位置，未找到返回-1
     */
    private int findHeaderEnd(byte[] data, int start) {
        for (int i = start; i <= data.length - 4; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n' && data[i + 2] == '\r' && data[i + 3] == '\n') {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * 清理文件名中的非法字符
     *
     * @param fileName 原始文件名
     * @return 清理后的文件名
     */
    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
    
    /**
     * 发送JSON格式响应
     *
     * @param output    输出流
     * @param success   是否成功
     * @param fileName  文件名（成功时）
     * @param error     错误信息（失败时）
     * @throws IOException IO异常
     */
    private void sendJsonResponse(OutputStream output, boolean success, String fileName, String error) throws IOException {
        String json = "{\"success\":" + success + 
                      (fileName != null ? ",\"fileName\":\"" + escapeJson(fileName) + "\"" : "") +
                      (error != null ? ",\"error\":\"" + escapeJson(error) + "\"" : "") +
                      "}";
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        sendResponse(output, "200 OK", "application/json; charset=UTF-8", data);
    }
    
    /**
     * JSON字符串转义
     *
     * @param str 原始字符串
     * @return 转义后的字符串
     */
    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
    }
    
    /**
     * 发送404响应
     *
     * @param output 输出流
     * @throws IOException IO异常
     */
    private void send404(OutputStream output) throws IOException {
        String body = "404 Not Found";
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        sendResponse(output, "404 Not Found", "text/plain; charset=UTF-8", data);
    }
    
    /**
     * 发送HTTP响应
     *
     * @param output      输出流
     * @param status      HTTP状态码
     * @param contentType Content-Type
     * @param data        响应数据
     * @throws IOException IO异常
     */
    private void sendResponse(OutputStream output, String status, String contentType, byte[] data) throws IOException {
        String header = "HTTP/1.1 " + status + "\r\n" +
                        "Content-Type: " + contentType + "\r\n" +
                        "Content-Length: " + data.length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n";
        output.write(header.getBytes(StandardCharsets.UTF_8));
        output.write(data);
    }
}