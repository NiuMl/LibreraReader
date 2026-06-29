package com.foobnix.wifitransfer;

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

public class WifiTransferServer {
    
    public interface UploadCallback {
        void onUploadSuccess(String fileName);
        void onUploadError(String error);
        void onServerStartError(String error);
    }
    
    private static final int PORT = 18080;
    private static final int BUFFER_SIZE = 8192;
    
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private ExecutorService threadPool;
    private final File uploadDir;
    private final UploadCallback callback;
    
    public WifiTransferServer(File uploadDir, UploadCallback callback) {
        this.uploadDir = uploadDir;
        this.callback = callback;
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
    }
    
    public void start() {
        threadPool = Executors.newCachedThreadPool();
        threadPool.execute(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                isRunning = true;
                while (isRunning) {
                    try {
                        Socket socket = serverSocket.accept();
                        threadPool.execute(() -> handleClient(socket));
                    } catch (IOException e) {
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
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public int getPort() {
        return PORT;
    }
    
    private void handleClient(Socket socket) {
        try {
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();
            
            byte[] requestBuffer = new byte[BUFFER_SIZE];
            int bytesRead = input.read(requestBuffer);
            
            if (bytesRead <= 0) {
                socket.close();
                return;
            }
            
            String requestStr = new String(requestBuffer, 0, bytesRead, StandardCharsets.UTF_8);
            int firstLineEnd = requestStr.indexOf("\r\n");
            
            if (firstLineEnd < 0) {
                socket.close();
                return;
            }
            
            String firstLine = requestStr.substring(0, firstLineEnd);
            String[] parts = firstLine.split(" ");
            
            if (parts.length < 2) {
                socket.close();
                return;
            }
            
            String method = parts[0];
            String path = parts[1].split("\\?")[0];
            
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
    
    private void sendHtmlPage(OutputStream output) throws IOException {
        String html = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>WLAN传书</title>\n" +
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
                "    <h1>WLAN传书</h1>\n" +
                "    <div class=\"upload-area\" id=\"uploadArea\">\n" +
                "        <p>拖拽文件到此处</p>\n" +
                "        <p>或</p>\n" +
                "        <button class=\"btn\" onclick=\"document.getElementById('fileInput').click()\">选择文件</button>\n" +
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
                "            showStatus('正在上传: ' + file.name, '');\n" +
                "\n" +
                "            fetch('/upload', {\n" +
                "                method: 'POST',\n" +
                "                body: formData\n" +
                "            }).then(response => response.json())\n" +
                "            .then(data => {\n" +
                "                if (data.success) {\n" +
                "                    showStatus('上传成功: ' + data.fileName, 'success');\n" +
                "                    addFileToList(data.fileName);\n" +
                "                } else {\n" +
                "                    showStatus('上传失败: ' + data.error, 'error');\n" +
                "                }\n" +
                "            }).catch(error => {\n" +
                "                showStatus('上传失败: ' + error.message, 'error');\n" +
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
    
    private void handleUpload(InputStream input, OutputStream output, byte[] initialBuffer, int initialBytesRead) throws IOException {
        String requestStr = new String(initialBuffer, 0, initialBytesRead, StandardCharsets.UTF_8);
        
        int headerEnd = requestStr.indexOf("\r\n\r\n");
        if (headerEnd < 0) {
            sendJsonResponse(output, false, null, "请求格式错误");
            return;
        }
        
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
        
        if (contentType == null || !contentType.contains("multipart/form-data")) {
            sendJsonResponse(output, false, null, "不支持的内容类型");
            return;
        }
        
        String boundary = null;
        int boundaryIndex = contentType.indexOf("boundary=");
        if (boundaryIndex >= 0) {
            boundary = contentType.substring(boundaryIndex + 9).trim();
            if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
                boundary = boundary.substring(1, boundary.length() - 1);
            }
        }
        
        if (boundary == null) {
            sendJsonResponse(output, false, null, "未找到边界");
            return;
        }
        
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
                
                int filenameIndex = headerStr.indexOf("filename=\"");
                if (filenameIndex >= 0) {
                    int filenameStart = filenameIndex + 10;
                    int filenameEnd = headerStr.indexOf("\"", filenameStart);
                    if (filenameEnd >= 0) {
                        fileName = URLDecoder.decode(headerStr.substring(filenameStart, filenameEnd), "UTF-8");
                    }
                }
                
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
            sendJsonResponse(output, false, null, "未找到文件");
        }
    }
    
    private int findBoundary(byte[] data, byte[] boundary) {
        return findBoundary(data, 0, boundary);
    }
    
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
    
    private int findHeaderEnd(byte[] data, int start) {
        for (int i = start; i <= data.length - 4; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n' && data[i + 2] == '\r' && data[i + 3] == '\n') {
                return i;
            }
        }
        return -1;
    }
    
    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
    
    private void sendJsonResponse(OutputStream output, boolean success, String fileName, String error) throws IOException {
        String json = "{\"success\":" + success + 
                      (fileName != null ? ",\"fileName\":\"" + escapeJson(fileName) + "\"" : "") +
                      (error != null ? ",\"error\":\"" + escapeJson(error) + "\"" : "") +
                      "}";
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        sendResponse(output, "200 OK", "application/json; charset=UTF-8", data);
    }
    
    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
    }
    
    private void send404(OutputStream output) throws IOException {
        String body = "404 Not Found";
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        sendResponse(output, "404 Not Found", "text/plain; charset=UTF-8", data);
    }
    
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