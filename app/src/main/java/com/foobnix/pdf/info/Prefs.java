package com.foobnix.pdf.info;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.foobnix.android.utils.LOG;

/**
 * 页面错误记录管理类
 * <p>
 * 使用SharedPreferences存储已遇到错误的页面，用于避免重复显示错误提示。
 * 采用单例模式，管理书籍路径和页码的错误记录。
 */
public class Prefs {

    /** 单例实例 */
    static Prefs instance = new Prefs();

    /** SharedPreferences实例 */
    SharedPreferences sp;

    /**
     * 获取单例实例
     *
     * @return Prefs实例
     */
    public static synchronized Prefs get() {
        return instance;
    }

    /**
     * 初始化SharedPreferences
     *
     * @param c 上下文
     */
    public synchronized void init(Context c) {
        if (sp == null) {
            sp = c.getSharedPreferences("TextErrors", Context.MODE_PRIVATE);
        }
    }

    /**
     * 记录页面错误
     * <p>
     * 将指定书籍的指定页面标记为已遇到错误。
     *
     * @param path 文件路径
     * @param page 页码
     */
    public void put(String path, int page) {
        if (sp != null) {
            sp.edit()
              .putBoolean(makeHash(path, page), true)
              .commit();
        }
    }

    /**
     * 生成哈希键
     *
     * @param path 文件路径
     * @param page 页码
     * @return 哈希字符串
     */
    @NonNull private String makeHash(String path, int page) {
        return "" + path.hashCode() + page;
    }

    /**
     * 检查页面错误是否已存在
     * <p>
     * 如果SharedPreferences未初始化，默认返回true（视为已存在错误）。
     *
     * @param path 文件路径
     * @param page 页码
     * @return 是否已记录错误
     */
    public boolean isErrorExist(String path, int page) {
        if (sp != null) {
            boolean isErrorExist = sp.contains(makeHash(path, page));
            LOG.d("isErrorExist", isErrorExist, path + page);
            return isErrorExist;
        } else {
            return true;
        }
    }

    /**
     * 移除页面错误记录
     *
     * @param path 文件路径
     * @param page 页码
     */
    public void remove(String path, int page) {
        if (sp != null) {
            sp.edit()
              .remove(makeHash(path, page))
              .commit();
        }
    }

}
