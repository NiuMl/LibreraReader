package com.foobnix.ui2.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.foobnix.android.utils.LOG;

import org.librera.JSONArray;
import org.librera.JSONException;
import org.librera.LinkedJSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PrivateDomainApiService {
    private static final String TAG = "PrivateDomainApi";
    private static String baseUrl = "http://192.168.1.8:5000/api";
    private static String token = null;
    private static String username = "";
    private static String password = "";
    private static String host = "192.168.1.8";
    private static String port = "5000";
    private static SharedPreferences sharedPreferences;

    public static void init(Context context) {
        sharedPreferences = context.getSharedPreferences("PrivateDomain", Context.MODE_PRIVATE);
        token = sharedPreferences.getString("token", null);
        username = sharedPreferences.getString("username", "admin");
        password = sharedPreferences.getString("password", "123456");
        host = sharedPreferences.getString("host", "192.168.1.8");
        port = sharedPreferences.getString("port", "5000");
        baseUrl = "http://" + host + ":" + port + "/api";
    }

    public static void saveConfig(String newHost, String newPort, String newUsername, String newPassword) {
        host = newHost;
        port = newPort;
        username = newUsername;
        password = newPassword;
        baseUrl = "http://" + host + ":" + port + "/api";
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("host", host);
        editor.putString("port", port);
        editor.putString("username", username);
        editor.putString("password", password);
        editor.apply();
        Log.d(TAG, "Config saved: host=" + host + ", port=" + port + ", username=" + username);
    }

    public static String getHost() {
        return host;
    }

    public static String getPort() {
        return port;
    }

    public static String getUsername() {
        return username;
    }

    public static String getPassword() {
        return password;
    }

    private static OkHttpClient getClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .addInterceptor(new AuthInterceptor())
                .build();
    }

    private static class AuthInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request originalRequest = chain.request();

            Request requestWithAuth = originalRequest.newBuilder()
                    .header("Authorization", token != null ? token : "")
                    .build();

            Response response = chain.proceed(requestWithAuth);

            if (response.code() == 401 && username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                response.close();

                String loginResult = loginSync(username, password);
                if (loginResult != null) {
                    token = loginResult;
                    if (sharedPreferences != null) {
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("token", token);
                        editor.apply();
                    }
                    requestWithAuth = originalRequest.newBuilder()
                            .header("Authorization", loginResult)
                            .build();
                    response = chain.proceed(requestWithAuth);
                }
            }

            return response;
        }
    }

    public static class LoginResponse {
        public int code;
        public String message;
        public String token;

        public LoginResponse(int code, String message, String token) {
            this.code = code;
            this.message = message;
            this.token = token;
        }
    }

    public static LoginResponse login(String user, String pass) {
        try {
            String result = loginSync(user, pass);
            if (result != null) {
                token = result;
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("token", token);
                editor.apply();
                return new LoginResponse(0, "success", token);
            }
        } catch (Exception e) {
            Log.e(TAG, "Login failed", e);
            LOG.e(e);
        }
        return new LoginResponse(-1, "login failed", null);
    }

    private static String loginSync(String user, String pass) {
        String url = baseUrl.replace("/api", "") + "/api/login";
        String jsonBody = "{\"username\": \"" + user + "\", \"password\": \"" + pass + "\"}";

        Request request = new Request.Builder()
                .url(url)
                .post(okhttp3.RequestBody.create(jsonBody, okhttp3.MediaType.parse("application/json")))
                .build();

        try {
            Response response = new OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                    .newCall(request)
                    .execute();

            if (response.isSuccessful() && response.body() != null) {
                String jsonString = response.body().string();
                Log.d(TAG, "Login response: " + jsonString);
                LinkedJSONObject obj = new LinkedJSONObject(jsonString);
                int code = obj.optInt("code", -1);
                if (code == 0) {
                    return obj.optString("token", null);
                }
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Login sync failed", e);
            LOG.e(e);
        }
        return null;
    }

    public static class Category {
        public String id;
        public String name;

        public Category(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static class Novel {
        public String id;
        public String title;
        public String author;
        public String cover;
        public boolean isInShelf;
        public String filePath;

        public Novel(String id, String title, String author, String cover, boolean isInShelf, String filePath) {
            this.id = id;
            this.title = title;
            this.author = author;
            this.cover = cover;
            this.isInShelf = isInShelf;
            this.filePath = filePath;
        }
    }

    public static class NovelResponse {
        public List<Novel> novels;
        public int total;
        public int page;
        public int pageSize;

        public NovelResponse(List<Novel> novels, int total, int page, int pageSize) {
            this.novels = novels;
            this.total = total;
            this.page = page;
            this.pageSize = pageSize;
        }
    }

    public static class NovelContentResponse {
        public String id;
        public String title;
        public String content;

        public NovelContentResponse(String id, String title, String content) {
            this.id = id;
            this.title = title;
            this.content = content;
        }
    }

    public static List<Category> getCategories() {
        String url = baseUrl + "/categories";
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try {
            Response response = getClient().newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                String jsonString = response.body().string();
                Log.d(TAG, "getCategories response: " + jsonString);

                LinkedJSONObject obj = new LinkedJSONObject(jsonString);
                int code = obj.optInt("code", -1);
                if (code == 0) {
                    JSONArray categoriesArray = obj.optJSONArray("categories");
                    List<Category> categories = new ArrayList<>();
                    for (int i = 0; i < categoriesArray.length(); i++) {
                        LinkedJSONObject catObj = categoriesArray.optJSONObject(i);
                        String id = catObj.optString("id", "");
                        String name = catObj.optString("name", "");
                        categories.add(new Category(id, name));
                    }
                    return categories;
                }
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "getCategories failed", e);
            LOG.e(e);
        }
        return new ArrayList<>();
    }

    public static NovelResponse getNovels(int page, int pageSize, String search, String category) {
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(baseUrl).append("/novels?page=").append(page).append("&page_size=").append(pageSize);
        if (search != null && !search.isEmpty()) {
            urlBuilder.append("&search=").append(search);
        }
        if (category != null && !category.isEmpty()) {
            urlBuilder.append("&category=").append(category);
        }

        String url = urlBuilder.toString();
        Log.d(TAG, "getNovels URL: " + url);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try {
            Response response = getClient().newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                String jsonString = response.body().string();
                Log.d(TAG, "getNovels response: " + jsonString);

                LinkedJSONObject obj = new LinkedJSONObject(jsonString);
                JSONArray novelsArray = obj.optJSONArray("novels");
                int total = obj.optInt("total", 0);
                int pageNum = obj.optInt("page", 1);
                int pageSizeNum = obj.optInt("page_size", 10);

                List<Novel> novels = new ArrayList<>();
                for (int i = 0; i < novelsArray.length(); i++) {
                    LinkedJSONObject novelObj = novelsArray.optJSONObject(i);
                    String id = novelObj.optString("id", "");
                    String title = novelObj.optString("title", "");
                    String author = novelObj.optString("author", "");
                    String cover = novelObj.optString("cover", "");
                    boolean isInShelf = novelObj.optBoolean("isInShelf", false);
                    String filePath = novelObj.optString("filePath", "");
                    novels.add(new Novel(id, title, author, cover, isInShelf, filePath));
                }

                return new NovelResponse(novels, total, pageNum, pageSizeNum);
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "getNovels failed", e);
            LOG.e(e);
        }
        return null;
    }

    public static NovelContentResponse getNovelContent(int novelId) {
        String url = baseUrl + "/novel/" + novelId;
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try {
            Response response = getClient().newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                String jsonString = response.body().string();
                LinkedJSONObject obj = new LinkedJSONObject(jsonString);
                String id = obj.optString("id", "");
                String title = obj.optString("title", "");
                String content = obj.optString("content", "");
                return new NovelContentResponse(id, title, content);
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "getNovelContent failed", e);
            LOG.e(e);
        }
        return null;
    }

    public static java.io.File downloadNovel(int novelId, String novelTitle, java.io.File saveDir) {
        String url = baseUrl + "/download/" + novelId;
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try {
            Response response = getClient().newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                java.io.InputStream inputStream = response.body().byteStream();
                
                // 优先使用传入的小说标题作为文件名
                String safeTitle = novelTitle.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
                String fileName = safeTitle + ".txt";
                
                // 尝试从 Content-Disposition 获取原始文件名，但要做清理
                String contentDisposition = response.header("Content-Disposition");
                if (contentDisposition != null) {
                    // 尝试多种格式解析
                    String extractedFileName = extractFileName(contentDisposition);
                    if (extractedFileName != null && !extractedFileName.isEmpty()) {
                        // 使用原始文件名，但保留扩展名
                        String extractedName = extractedFileName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
                        int dotIndex = extractedName.lastIndexOf('.');
                        if (dotIndex > 0) {
                            String extension = extractedName.substring(dotIndex);
                            fileName = safeTitle + extension;
                        }
                    }
                }

                java.io.File saveFile = new java.io.File(saveDir, fileName);
                
                java.io.FileOutputStream fos = new java.io.FileOutputStream(saveFile);
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                fos.close();
                inputStream.close();
                
                Log.d(TAG, "Novel downloaded to: " + saveFile.getAbsolutePath());
                return saveFile;
            }
        } catch (IOException e) {
            Log.e(TAG, "downloadNovel failed", e);
            LOG.e(e);
        }
        return null;
    }
    
    private static String extractFileName(String contentDisposition) {
        if (contentDisposition == null || contentDisposition.isEmpty()) {
            return null;
        }
        
        // 尝试 "filename=" 格式
        int filenameIndex = contentDisposition.indexOf("filename=");
        if (filenameIndex >= 0) {
            String fileNamePart = contentDisposition.substring(filenameIndex + 9).trim();
            // 去掉可能的前缀（如 attachment; 或 inline;）
            int semicolonIndex = fileNamePart.indexOf(';');
            if (semicolonIndex > 0) {
                fileNamePart = fileNamePart.substring(0, semicolonIndex).trim();
            }
            // 去掉引号
            fileNamePart = fileNamePart.replace("\"", "").replace("'", "").trim();
            // URL解码
            try {
                fileNamePart = java.net.URLDecoder.decode(fileNamePart, "UTF-8");
            } catch (Exception e) {
                // 忽略解码错误
            }
            return fileNamePart;
        }
        
        // 尝试 "filename*=" 格式 (RFC 5987)
        int filenameStarIndex = contentDisposition.indexOf("filename*=");
        if (filenameStarIndex >= 0) {
            String fileNamePart = contentDisposition.substring(filenameStarIndex + 10).trim();
            int semicolonIndex = fileNamePart.indexOf(';');
            if (semicolonIndex > 0) {
                fileNamePart = fileNamePart.substring(0, semicolonIndex).trim();
            }
            fileNamePart = fileNamePart.replace("\"", "").replace("'", "").trim();
            // 格式可能是 UTF-8''encoded
            if (fileNamePart.contains("''")) {
                fileNamePart = fileNamePart.substring(fileNamePart.indexOf("''") + 2);
            }
            try {
                fileNamePart = java.net.URLDecoder.decode(fileNamePart, "UTF-8");
            } catch (Exception e) {
                // 忽略解码错误
            }
            return fileNamePart;
        }
        
        return null;
    }
}
