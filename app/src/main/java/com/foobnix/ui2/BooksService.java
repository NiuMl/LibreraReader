package com.foobnix.ui2;

import android.os.Handler;

/**
 * 书籍服务类
 * <p>
 * 定义书籍管理相关的服务操作常量，包括搜索、同步、删除等操作的Action标识，
 * 以及操作结果的通知常量。用于Service与其他组件之间的通信。
 */
public class BooksService {

    /** 日志标签 */
    public static String TAG = "BooksService";
    /** Intent名称 */
    public static String INTENT_NAME = "BooksServiceIntent";
    /** 搜索所有书籍Action */
    public static String ACTION_SEARCH_ALL = "ACTION_SEARCH_ALL";
    /** 移除已删除书籍Action */
    public static String ACTION_REMOVE_DELETED = "ACTION_REMOVE_DELETED";
    /** 同步Dropbox Action */
    public static String ACTION_SYNC_DROPBOX = "ACTION_SYNC_DROPBOX";
    /** 运行自测Action */
    public static String ACTION_RUN_SELF_TEST = "ACTION_RUN_SELF_TEST";
    /** 运行同步Action */
    public static String ACTION_RUN_SYNCRONICATION = "ACTION_RUN_SYNCRONICATION";
    /** 同步完成结果 */
    public static String RESULT_SYNC_FINISH = "RESULT_SYNC_FINISH";
    /** 搜索完成结果 */
    public static String RESULT_SEARCH_FINISH = "RESULT_SEARCH_FINISH";
    /** 构建书库结果 */
    public static String RESULT_BUILD_LIBRARY = "RESULT_BUILD_LIBRARY";
    /** 搜索计数结果 */
    public static String RESULT_SEARCH_COUNT = "RESULT_SEARCH_COUNT";
    /** 通知所有组件结果 */
    public static String RESULT_NOTIFY_ALL = "RESULT_NOTIFY_ALL";

    /** 搜索消息文本结果 */
    public static String RESULT_SEARCH_MESSAGE_TXT = "RESULT_SEARCH_MESSAGE_TXT";

    /** 服务运行状态标志 */
    public static volatile boolean isRunning = false;
    /** 消息处理器 */
    Handler handler;
    /** 是否启动前台服务标志 */
    boolean isStartForeground = false;

}
