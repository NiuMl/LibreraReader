package com.foobnix.pdf.info.wrapper;

import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.AppsConfig;
import com.foobnix.ui2.fragment.BookmarksFragment2;
import com.foobnix.ui2.fragment.BrowseFragment2;
import com.foobnix.ui2.fragment.FavoritesFragment2;
import com.foobnix.ui2.fragment.GoogleDriveFragment2;
import com.foobnix.ui2.fragment.OpdsFragment2;
import com.foobnix.ui2.fragment.PrefFragment2;
import com.foobnix.ui2.fragment.PrivateDomainFragment2;
import com.foobnix.ui2.fragment.RecentFragment2;
import com.foobnix.ui2.fragment.SearchFragment2;
import com.foobnix.ui2.fragment.UIFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * UI标签页枚举类
 * <p>
 * 定义应用的所有标签页，包括搜索、浏览、最近阅读、收藏、书签、OPDS、设置、私有域、Google云端硬盘等。
 * 支持标签页的自定义排序和显示/隐藏控制。
 */
public enum UITab {

    /** 搜索标签页 */
    SearchFragment(0, SearchFragment2.PAIR.first, SearchFragment2.PAIR.second, SearchFragment2.class, true), //
    /** 浏览标签页（本地书籍） */
    BrowseFragment(1, BrowseFragment2.PAIR.first, BrowseFragment2.PAIR.second, BrowseFragment2.class, true), //
    /** 最近阅读标签页 */
    RecentFragment(2, RecentFragment2.PAIR.first, RecentFragment2.PAIR.second, RecentFragment2.class, true), //
    /** 收藏标签页 */
    StarsFragment(3, FavoritesFragment2.PAIR.first, FavoritesFragment2.PAIR.second, FavoritesFragment2.class, true), //
    /** 书签标签页 */
    BookmarksFragment(4, BookmarksFragment2.PAIR.first, BookmarksFragment2.PAIR.second, BookmarksFragment2.class, true), //
    /** OPDS目录标签页 */
    OpdsFragment(5, OpdsFragment2.PAIR.first, OpdsFragment2.PAIR.second, OpdsFragment2.class, true), //
    /** 设置标签页 */
    PrefFragment(6, PrefFragment2.PAIR.first, PrefFragment2.PAIR.second, PrefFragment2.class, true), //
    /** 私有域标签页 */
    PrivateDomainFragment(8, PrivateDomainFragment2.PAIR.first, PrivateDomainFragment2.PAIR.second, PrivateDomainFragment2.class, true), //
    /** Google云端硬盘标签页 */
    GoogleDrive2Fragment(7, GoogleDriveFragment2.PAIR.first, GoogleDriveFragment2.PAIR.second, GoogleDriveFragment2.class, true); //

    /** 标签页索引 */
    public int index;
    /** 名称资源ID */
    private int name;
    /** 图标资源ID */
    private int icon;
    /** 对应的Fragment类 */
    private Class<? extends UIFragment> clazz;
    /** 是否可见 */
    private boolean isVisible;

    /**
     * 构造函数
     *
     * @param index    标签页索引
     * @param name     名称资源ID
     * @param icon     图标资源ID
     * @param clazz    Fragment类
     * @param isVisible 是否可见
     */
    private UITab(int index, int name, int icon, Class<? extends UIFragment> clazz, boolean isVisible) {
        this.index = index;
        this.name = name;
        this.icon = icon;
        this.clazz = clazz;
        this.isVisible = isVisible;
    }

    /**
     * 获取标签页索引
     *
     * @return 索引
     */
    public int getIndex() {
        return index;
    }

    /**
     * 获取名称资源ID
     *
     * @return 名称资源ID
     */
    public int getName() {
        return name;
    }

    /**
     * 获取图标资源ID
     *
     * @return 图标资源ID
     */
    public int getIcon() {
        return icon;
    }

    /**
     * 获取对应的Fragment类
     *
     * @return Fragment类
     */
    public Class<? extends UIFragment> getClazz() {
        return clazz;
    }

    /**
     * 根据索引获取标签页
     *
     * @param index 索引
     * @return UITab枚举值，默认为SearchFragment
     */
    public static UITab getByIndex(int index) {
        for (UITab tab : values()) {
            if (tab.index == index) {
                return tab;
            }
        }
        return SearchFragment;
    }

    /**
     * 获取排序后的标签页列表
     * <p>
     * 根据用户配置的标签页顺序返回列表，同时更新每个标签页的可见状态。
     *
     * @return 排序后的标签页列表
     */
    public static List<UITab> getOrdered() {
        synchronized (AppState.get().tabsOrder9) {
            String input = AppState.get().tabsOrder9;
            LOG.d("getOrdered", input);
            List<UITab> list = new ArrayList<UITab>();
            for (String pair : input.split(",")) {
                String[] tab = pair.split("#");
                int id = Integer.valueOf(tab[0]);
                boolean isVisible = tab[1].equals("1");
                UITab byIndex = getByIndex(id);
                byIndex.setVisible(isVisible);
                list.add(byIndex);
            }
            return list;
        }
    }

    /**
     * 获取标签页在当前可见列表中的位置
     * <p>
     * 只计算可见标签页的位置。
     *
     * @param tab 标签页
     * @return 可见列表中的位置
     */
    public static int getCurrentTabIndex(UITab tab) {
        List<UITab> ordered = getOrdered();
        int count = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).isVisible) {
                count++;
            }
            if (ordered.get(i) == tab) {
                return count;
            }
        }
        return 0;
    }

    /**
     * 是否显示最近阅读标签页
     *
     * @return 是否显示
     */
    public static boolean isShowRecent() {
        synchronized (AppState.get().tabsOrder9) {
            return AppState.get().tabsOrder9.contains(UITab.RecentFragment.index + "#1");
        }
    }

    /**
     * 是否显示搜索/书库标签页
     *
     * @return 是否显示
     */
    public static boolean isShowLibrary() {
        synchronized (AppState.get().tabsOrder9) {
            return AppState.get().tabsOrder9.contains(UITab.SearchFragment.index + "#1");
        }
    }

    /**
     * 是否显示设置标签页
     *
     * @return 是否显示
     */
    public static boolean isShowPreferences() {
        synchronized (AppState.get().tabsOrder9) {
            return AppState.get().tabsOrder9.contains(UITab.PrefFragment.index + "#1");
        }
    }

    /**
     * 是否显示云端硬盘标签页
     *
     * @return 是否显示
     */
    public static boolean isShowCloudsPreferences() {
        synchronized (AppState.get().tabsOrder9) {
            return AppState.get().tabsOrder9.contains(UITab.GoogleDrive2Fragment.index + "#1");
        }
    }

    /**
     * 判断标签页是否可见
     * <p>
     * F-Droid版本隐藏Google云端硬盘标签页。
     *
     * @return 是否可见
     */
    public boolean isVisible() {
        if (AppsConfig.IS_FDROID && (this == UITab.GoogleDrive2Fragment)) {
            return false;
        }
        return isVisible;
    }

    /**
     * 设置标签页可见性
     *
     * @param isVisible 是否可见
     */
    public void setVisible(boolean isVisible) {
        this.isVisible = isVisible;
    }

}
