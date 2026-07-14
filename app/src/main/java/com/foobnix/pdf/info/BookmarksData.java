package com.foobnix.pdf.info;

import android.content.Context;

import com.foobnix.android.utils.IO;
import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.Objects;
import com.foobnix.model.AppBookmark;
import com.foobnix.model.AppData;
import com.foobnix.model.AppProfile;
import com.foobnix.model.AppState;

import org.librera.LinkedJSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 书签数据管理类
 * <p>
 * 采用单例模式，管理书籍书签的添加、删除、查询等操作。
 * 书签数据以JSON格式存储在文件中，支持按书籍分类查询和全局查询。
 */
public class BookmarksData {

    /** 单例实例 */
    final static BookmarksData instance = new BookmarksData();

    /**
     * 获取单例实例
     *
     * @return BookmarksData实例
     */
    public static BookmarksData get() {
        return instance;
    }

    /**
     * 添加书签
     * <p>
     * 将书签数据保存到JSON文件中，以书签时间戳作为键。
     * 如果页码超过1，则重置为0。
     *
     * @param bookmark 书签对象
     */
    public void add(AppBookmark bookmark) {
        LOG.d("BookmarksData", "add", bookmark.p, bookmark.text, bookmark.path);

        if (bookmark.p > 1) {
            bookmark.p = 0;
        }
        try {
            LinkedJSONObject obj = IO.readJsonObject(AppProfile.syncBookmarks);
            obj.put("" + bookmark.t, Objects.toJSONObject(bookmark));
            IO.writeObjAsync(AppProfile.syncBookmarks, obj);
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /**
     * 删除书签
     * <p>
     * 从JSON文件中移除指定的书签。
     *
     * @param bookmark 要删除的书签对象
     */
    public void remove(AppBookmark bookmark) {
        LOG.d("BookmarksData", "remove", bookmark.t, bookmark.file);

        try {
            LinkedJSONObject obj = IO.readJsonObject(bookmark.file);
            if (obj.has("" + bookmark.t)) {
                obj.remove("" + bookmark.t);
            }
            IO.writeObjAsync(bookmark.file, obj);
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /**
     * 根据文件获取该书的书签列表
     *
     * @param file 书籍文件
     * @return 书签列表
     */
    public List<AppBookmark> getBookmarksByBook(File file) {
        if (file == null) {
            return new ArrayList<>();
        }
        return getBookmarksByBook(file.getPath());
    }

    /**
     * 获取所有书签（带上下文，用于过滤快速书签）
     * <p>
     * 根据上下文获取本地化的快速书签文本，过滤掉快速书签。
     *
     * @param c 上下文
     * @return 过滤后的书签列表
     */
    public synchronized List<AppBookmark> getAll(Context c) {
        LOG.d("AppBookmark-get","getAll");

        final List<AppBookmark> all = getAll();
        final Iterator<AppBookmark> iterator = all.iterator();
        String fast = c.getString(R.string.fast_bookmark);
        while (iterator.hasNext()) {
            final AppBookmark next = iterator.next();

            if (AppState.get().isShowOnlyAvailabeBooks) {
                if (!new File(next.getPath()).isFile()) {
                    iterator.remove();
                    continue;
                }
            }

            if (!AppState.get().isShowFastBookmarks) {
                if (fast.equals(next.text)) {
                    iterator.remove();
                }

            }

        }
        return all;
    }


    public List<AppBookmark> getAll() {

        List<AppBookmark> all = new ArrayList<>();

        try {

            List<File> allFiles = AppProfile.getAllFiles(AppProfile.APP_BOOKMARKS_JSON);
            for (File file : allFiles) {
                LinkedJSONObject obj = IO.readJsonObject(file);


                final Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    final String next = keys.next();

                    AppBookmark appBookmark = new AppBookmark();
                    appBookmark.file = file;
                    final LinkedJSONObject local = obj.getJSONObject(next);
                    Objects.loadFromJson(appBookmark, local);
                    all.add(appBookmark);
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }


//        Iterator<AppBookmark> iterator = all.iterator();
//        while (iterator.hasNext()) {
//            AppBookmark next = iterator.next();
//            if (next.getText().equals(quick)) {
//                iterator.remove();
//            }
//        }

        LOG.d("getAll-size", all.size());
        Collections.sort(all, BY_TIME);
        return all;
    }


    public List<AppBookmark> getBookmarksByBook(String path) {

        List<AppBookmark> all = new ArrayList<>();


        List<File> allFiles = AppProfile.getAllFiles(AppProfile.APP_BOOKMARKS_JSON);
        for (File file : allFiles) {
            LinkedJSONObject obj = IO.readJsonObject(file);
            try {
                final Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    final String next = keys.next();

                    AppBookmark appBookmark = new AppBookmark();
                    appBookmark.file = file;
                    final LinkedJSONObject local = obj.getJSONObject(next);
                    Objects.loadFromJson(appBookmark, local);
                    String path1 = ExtUtils.getFileName(path);
                    String path2 = ExtUtils.getFileName(appBookmark.getPath());
                    if (path1.equals(path2)) {
                        appBookmark.path = path;//update path
                        all.add(appBookmark);
                    }
                }
            } catch (Exception e) {
                LOG.e(e);
            }
        }


        LOG.d("getBookmarksByBook", path, all.size());
        Collections.sort(all, BY_PERCENT);
        return all;
    }

    public boolean hasBookmark(String lastBookPath, int page, int pages) {
        final List<AppBookmark> bookmarksByBook = getBookmarksByBook(lastBookPath);
        for (AppBookmark appBookmark : bookmarksByBook) {
            if (appBookmark.getPercent() * pages == page) {
                return true;
            }
        }
        return false;
    }

    static final Comparator<AppBookmark> BY_PERCENT = new Comparator<AppBookmark>() {

        @Override
        public int compare(AppBookmark o1, AppBookmark o2) {
            return Float.compare(o1.getPercent(), o2.getPercent());
        }
    };

    static final Comparator<AppBookmark> BY_TIME = new Comparator<AppBookmark>() {

        @Override
        public int compare(AppBookmark o1, AppBookmark o2) {
            return Float.compare(o2.getTime(), o1.getTime());
        }
    };


    public Map<String, List<AppBookmark>> getBookmarksMap() {
        return null;
    }


    public void cleanBookmarks() {
        //IO.writeObj(AppProfile.syncBookmarks.getPath(), "{}");
        AppData.get().clearAll(AppProfile.APP_BOOKMARKS_JSON);
    }


}
