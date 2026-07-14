package com.foobnix.pdf.info;

import java.io.File;
import java.util.List;

/**
 * 服务命令接口
 * <p>
 * 定义书籍扫描相关的命令接口，用于Service与UI组件之间的通信。
 */
public interface ServiceCommands {

    /**
     * 扫描书籍文件
     *
     * @param result 扫描结果文件列表
     * @param run    扫描完成后执行的回调
     */
    void scan(List<File> result, Runnable run);
}
