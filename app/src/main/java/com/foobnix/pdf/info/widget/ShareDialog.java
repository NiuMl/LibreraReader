package com.foobnix.pdf.info.widget;

import static com.foobnix.android.utils.TxtUtils.iconText;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.util.Pair;

import com.foobnix.StringResponse;
import com.foobnix.android.utils.BaseItemLayoutAdapter;
import com.foobnix.android.utils.IO;
import com.foobnix.android.utils.Keyboards;
import com.foobnix.android.utils.LOG;
import com.foobnix.dao2.FileMeta;
import com.foobnix.drive.GFile;
import com.foobnix.mobi.parser.IOUtils;
import com.foobnix.model.AppBookmark;
import com.foobnix.model.AppData;
import com.foobnix.model.AppProfile;
import com.foobnix.model.AppSP;
import com.foobnix.model.AppState;
import com.foobnix.model.MyPath;
import com.foobnix.model.SimpleMeta;
import com.foobnix.model.TagData;
import com.foobnix.model.Tags2;
import com.foobnix.pdf.info.AppsConfig;
import com.foobnix.pdf.info.BookmarksData;
import com.foobnix.pdf.info.Clouds;
import com.foobnix.pdf.info.DialogSpeedRead;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.IMG;
import com.foobnix.pdf.info.Playlists;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.TintUtil;
import com.foobnix.pdf.info.Urls;
import com.foobnix.pdf.info.view.AlertDialogs;
import com.foobnix.pdf.info.view.Dialogs;
import com.foobnix.pdf.info.view.DialogsPlaylist;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.pdf.info.wrapper.UITab;
import com.foobnix.pdf.search.activity.HorizontalViewActivity;
import com.foobnix.pdf.search.activity.msg.NotifyAllFragments;
import com.foobnix.pdf.search.activity.msg.UpdateAllFragments;
import com.foobnix.sys.TempHolder;
import com.foobnix.ui2.AppDB;
import com.foobnix.ui2.FileMetaCore;
import com.foobnix.ui2.MainTabs2;
import com.foobnix.ui2.adapter.TabsAdapter2;

import org.ebookdroid.BookType;
import org.ebookdroid.common.settings.books.SharedBooks;
import org.ebookdroid.ui.viewer.VerticalViewActivity;
import org.greenrobot.eventbus.EventBus;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 分享对话框工具类
 * <p>
 * 提供书籍操作的上下文菜单功能，包括打开方式、删除、复制、移出书库、添加标签等操作。
 * 支持在阅读界面和书库界面中长按书籍时弹出操作菜单。
 */
public class ShareDialog {

    /**
     * 显示归档文件操作对话框
     * <p>
     * 针对归档文件（如ZIP）提供转换为EPUB/PDF、打开方式、删除、文件信息等操作选项。
     *
     * @param a              活动上下文
     * @param file           文件对象
     * @param onDeleteAction 删除后的回调操作
     */
    public static void showArchive(final Activity a, final File file, final Runnable onDeleteAction) {
        // 文件有效性检查
        if (ExtUtils.isNotValidFile(file)) {
            Toast.makeText(a, R.string.file_not_found, Toast.LENGTH_LONG)
                 .show();
            return;
        }

        List<String> items = new ArrayList<String>();

        // 非图片和EPUB文件可转换
        if (!ExtUtils.isImageOrEpub(file)) {
            items.add(a.getString(R.string.convert_to) + " EPUB");
            items.add(a.getString(R.string.convert_to) + " PDF");
        }

        // 判断是否可删除和显示文件信息
        final boolean canDelete = ExtUtils.isExteralSD(file.getPath()) ? true : file.canWrite();
        final boolean isShowInfo = !ExtUtils.isExteralSD(file.getPath());

        // 打开方式
        items.add(a.getString(R.string.open_with));

        // 删除选项
        if (canDelete) {
            items.add(a.getString(R.string.delete));
        }

        // 文件信息
        if (isShowInfo) {
            items.add(a.getString(R.string.file_info));
        }

        final String chooseTitle = file != null ? file.getPath() : a.getString(R.string.choose_);

        // 创建对话框
        final AlertDialog.Builder builder = new AlertDialog.Builder(a);
        builder.setTitle(R.string.choose_)//
               .setItems(items.toArray(new String[items.size()]), new DialogInterface.OnClickListener() {
                   @Override public void onClick(final DialogInterface dialog, final int which) {
                       int i = 0;
                       // 转换为EPUB
                       if (!ExtUtils.isImageOrEpub(file)) {
                           if (which == i++) {
                               showsItemsDialog(a, chooseTitle, AppState.CONVERTERS.get("EPUB"));
                           }
                           // 转换为PDF
                           if (which == i++) {
                               showsItemsDialog(a, chooseTitle, AppState.CONVERTERS.get("PDF"));
                           }
                       }
                       // 打开方式
                       if (which == i++) {
                           ExtUtils.openWith(a, file);
                       } else if (canDelete && which == i++) {
                           // 删除
                           FileInformationDialog.dialogDelete(a, file, onDeleteAction);
                       } else if (isShowInfo && which == i++) {
                           // 文件信息
                           FileInformationDialog.showFileInfoDialog(a, file, onDeleteAction);
                       }
                   }
               });
        builder.show();
    }

    /**
     * 显示选项列表对话框
     * <p>
     * 创建一个简单的列表对话框，点击选项后打开对应的URL。
     *
     * @param a     活动上下文
     * @param title 对话框标题
     * @param items 选项数组
     */
    public static void showsItemsDialog(final Activity a, String title, final String[] items) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(a);
        builder.setTitle(title)//
               .setItems(items, new DialogInterface.OnClickListener() {
                   @Override public void onClick(final DialogInterface dialog, final int which) {
                       Urls.open(a, items[which]);
                   }
               });
        builder.show();
    }

    /**
     * 目录长按操作对话框
     * <p>
     * 提供粘贴、移动、取消操作，用于文件复制/移动到指定目录。
     *
     * @param a          活动上下文
     * @param to         目标目录路径
     * @param onRefresh  操作完成后的刷新回调
     */
    public static void dirLongPress(final Activity a, final String to, final Runnable onRefresh) {
        List<String> items = new ArrayList<String>();

        items.add(a.getString(R.string.paste));
        items.add(a.getString(R.string.move));
        items.add(a.getString(R.string.cancel));

        final AlertDialog.Builder builder = new AlertDialog.Builder(a);
        builder.setItems(items.toArray(new String[items.size()]), new DialogInterface.OnClickListener() {
            @Override public void onClick(final DialogInterface dialog, final int which) {
                int i = 0;
                // 粘贴操作
                if (which == i++) {
                    try {
                        String from = TempHolder.get().copyFromPath;
                        File fromFile = new File(from);
                        File toFile = new File(to, fromFile.getName());

                        // 文件已存在检查
                        if (toFile.exists()) {
                            Toast.makeText(a, R.string.the_file_already_exists_, Toast.LENGTH_SHORT)
                                 .show();
                            return;
                        }

                        LOG.d("Copy from to", from, ">>", to);

                        // 复制文件
                        InputStream input = new BufferedInputStream(new FileInputStream(from));
                        OutputStream output = new BufferedOutputStream(new FileOutputStream(toFile));
                        IOUtils.copyClose(input, output);

                        TempHolder.get().listHash++;

                        Toast.makeText(a, R.string.success, Toast.LENGTH_SHORT)
                             .show();
                        TempHolder.get().copyFromPath = null;
                        onRefresh.run();
                    } catch (Exception e) {
                        LOG.e(e);
                        Toast.makeText(a, R.string.msg_unexpected_error, Toast.LENGTH_SHORT)
                             .show();
                    }
                }
                // 移动操作
                if (which == i++) {
                    try {
                        String from = TempHolder.get().copyFromPath;
                        File fromFile = new File(from);
                        File toFile = new File(to, fromFile.getName());
                        LOG.d("Copy from to", from, ">>", to);

                        // 文件已存在检查
                        if (toFile.exists()) {
                            Toast.makeText(a, R.string.the_file_already_exists_, Toast.LENGTH_SHORT)
                                 .show();
                            return;
                        }

                        // 复制文件
                        InputStream input = new BufferedInputStream(new FileInputStream(from));
                        OutputStream output = new BufferedOutputStream(new FileOutputStream(toFile));
                        IOUtils.copyClose(input, output);

                        // 删除原文件
                        fromFile.delete();
                        AppDB.get().delete(new FileMeta(fromFile.getPath()));
                        TempHolder.get().listHash++;

                        Toast.makeText(a, R.string.success, Toast.LENGTH_SHORT)
                             .show();
                        TempHolder.get().copyFromPath = null;
                        onRefresh.run();
                    } catch (Exception e) {
                        LOG.e(e);
                        Toast.makeText(a, R.string.msg_unexpected_error, Toast.LENGTH_SHORT)
                             .show();
                    }
                }
                // 取消操作
                if (which == i++) {
                    TempHolder.get().copyFromPath = null;
                    onRefresh.run();
                }
            }
        });
        AlertDialog create = builder.create();
        create.setOnDismissListener(new OnDismissListener() {
            @Override public void onDismiss(DialogInterface dialog) {
                Keyboards.hideNavigation(a);
            }
        });
        create.show();
    }

    /**
     * 显示书籍操作上下文菜单
     * <p>
     * 根据书籍类型和当前上下文动态生成操作菜单，包括编辑、阅读模式切换、打开方式、
     * 删除、复制、移出书库、添加标签、上传云端、同步等操作。
     *
     * @param a              活动上下文
     * @param file           文件对象
     * @param onDeleteAction 删除后的回调操作
     * @param page           当前页码（用于PDF重排）
     * @param dc             文档控制器（用于阅读模式切换）
     * @param hideShow       快速阅读模式的隐藏/显示回调
     */
    public static void show(final Activity a, final File file, final Runnable onDeleteAction, final int page,
                            final DocumentController dc, final Runnable hideShow) {

        // 文件有效性检查
        if (file == null) {
            Toast.makeText(a, R.string.file_not_found, Toast.LENGTH_LONG)
                 .show();
            return;
        }

        if (!ExtUtils.isExteralSD(file.getPath()) && ExtUtils.isNotValidFile(file)) {
            Toast.makeText(a, R.string.file_not_found, Toast.LENGTH_LONG)
                 .show();
            return;
        }

        // 判断文件类型和当前界面
        final boolean isPDF = BookType.PDF.is(file.getPath());
        final boolean isLibrary = false;
        final boolean isMainTabs = a instanceof MainTabs2;

        // 使用Pair存储图标和文本，用于自定义布局
        List<Pair<String, String>> items = new ArrayList<Pair<String, String>>();

        // TXT文件：编辑选项
        final boolean isTxt = BookType.TXT.is(file.getPath());
        if (isTxt) {
            items.add(new Pair<String, String>("✎", a.getString(R.string.edit)));
        }

        // 阅读模式切换选项
        if (dc != null) {
            // 纵向模式下显示横向模式选项
            if (a instanceof VerticalViewActivity || dc.isMusicianMode()) {
                items.add(new Pair<String, String>("②", AppState.get().nameHorizontalMode));
            }
            // 横向模式下显示纵向模式选项
            if (a instanceof HorizontalViewActivity || dc.isMusicianMode()) {
                items.add(new Pair<String, String>("①", AppState.get().nameVerticalMode));
            }
        }

        // PDF文件：文本重排选项
        if (isPDF) {
            items.add(new Pair<String, String>("⌘", a.getString(R.string.make_text_reflow)));
        }

        // 打开方式
        items.add(new Pair<String, String>("⎘", a.getString(R.string.open_with)));

        // 判断权限和状态
        final boolean isExternalOrCloud = ExtUtils.isExteralSD(file.getPath()) || Clouds.isCloud(file.getPath());
        boolean canDelete1 = ExtUtils.isExteralSD(file.getPath()) || Clouds.isCloud(file.getPath()) ? true : file.canWrite();
        final boolean canCopy = !ExtUtils.isExteralSD(file.getPath()) && !Clouds.isCloud(file.getPath());
        final boolean isShowInfo = !ExtUtils.isExteralSD(file.getPath());

        // 判断书籍是否已移出书架
        final boolean isRemovedFromLibrary = AppData.get()
                                                    .getAllExcluded()
                                                    .contains(new SimpleMeta(file.getPath()));

        // 配置文件不能删除
        if (file.getPath().contains(AppProfile.PROFILE_PREFIX)) {
            canDelete1 = false;
        }
        final boolean canDelete = canDelete1;

        // 书库界面专属选项
        if (isMainTabs) {
            // 删除
            if (canDelete) {
                items.add(new Pair<String, String>("⊗", a.getString(R.string.delete)));
            }
            // 复制
            if (canCopy) {
                items.add(new Pair<String, String>("⧉", a.getString(R.string.copy)));
            }
            // 添加/移出书库
            if (!isRemovedFromLibrary) {
                items.add(new Pair<String, String>("-", a.getString(R.string.remove_from_library)));
            } else {
                items.add(new Pair<String, String>("+", a.getString(R.string.add_to_library)));
            }
        }

        // 添加标签
        if (!isExternalOrCloud) {
            items.add(new Pair<String, String>("#", a.getString(R.string.add_tags)));
        }

        // 上传到云端
        if (AppsConfig.isCloudsEnable) {
            items.add(new Pair<String, String>("☁", a.getString(R.string.upload_to_cloud)));
        }

        // 同步书籍（非FDroid版本且非同步文件）
        final boolean isSyncronized = AppsConfig.IS_FDROID || Clouds.isLibreraSyncFile(file);
        if (!isSyncronized) {
            items.add(new Pair<String, String>("↻", a.getString(R.string.sync_book)));
        }

        // 书库界面：重置阅读进度
        if (isMainTabs) {
            items.add(new Pair<String, String>("↶", a.getString(R.string.delete_reading_progress)));
        }

        // 文件信息
        if (isShowInfo) {
            items.add(new Pair<String, String>("ⓘ", a.getString(R.string.file_info)));
        }

        // 创建对话框，使用自定义布局适配器
        final AlertDialog.Builder builder = new AlertDialog.Builder(a);
        builder.setAdapter(new BaseItemLayoutAdapter<Pair<String, String>>(a, R.layout.dialog_menu_item, items) {
            @Override public void populateView(View layout, int position, Pair<String, String> item) {
                TextView iconView = (TextView) layout.findViewById(R.id.iconView);
                TextView textView = (TextView) layout.findViewById(R.id.textView);
                iconView.setText(item.first);
                textView.setText(item.second);
                // 根据主题设置文字颜色
                iconView.setTextColor(AppState.get().isDayNotInvert ? Color.BLACK : Color.WHITE);
                textView.setTextColor(AppState.get().isDayNotInvert ? Color.BLACK : Color.WHITE);
            }
        }, new DialogInterface.OnClickListener() {
            @Override public void onClick(final DialogInterface dialog, final int which) {
                int i = 0;

                // 编辑TXT文件
                if (isTxt && which == i++) {
                    AlertDialogs.editFileTxt(a, file, AppProfile.DOWNLOADS_DIR, new StringResponse() {
                        @Override public boolean onResultRecive(String string) {
                            if ((a instanceof HorizontalViewActivity || a instanceof VerticalViewActivity) && dc != null) {
                                dc.onCloseActivityFinal(new Runnable() {
                                    @Override public void run() {
                                        ExtUtils.openFile(a, new FileMeta(string));
                                    }
                                });
                            } else {
                                ExtUtils.openFile(a, new FileMeta(string));
                            }
                            return false;
                        }
                    });
                }

                // 返回书库（已禁用）
                if (isLibrary && which == i++) {
                    a.finish();
                    MainTabs2.startActivity(a, UITab.getCurrentTabIndex(UITab.SearchFragment));
                }

                // 切换到纵向/滚动模式
                if (dc != null && (a instanceof HorizontalViewActivity || dc.isMusicianMode()) && which == i++) {
                    dc.onCloseActivityFinal(new Runnable() {
                        @Override public void run() {
                            if (dc.isMusicianMode()) {
                                AppSP.get().readingMode = AppState.READING_MODE_BOOK;
                            } else {
                                AppSP.get().readingMode = AppState.READING_MODE_SCROLL;
                            }
                            ExtUtils.showDocumentWithoutDialog(a, file, a.getIntent()
                                                                         .getStringExtra(DocumentController.EXTRA_PLAYLIST));
                        }
                    });
                }

                // 切换到横向/书籍模式
                if (dc != null && (a instanceof VerticalViewActivity || dc.isMusicianMode()) && which == i++) {
                    dc.onCloseActivityFinal(new Runnable() {
                        @Override public void run() {
                            if (dc.isMusicianMode()) {
                                AppSP.get().readingMode = AppState.READING_MODE_SCROLL;
                            } else {
                                AppSP.get().readingMode = AppState.READING_MODE_BOOK;
                            }
                            ExtUtils.showDocumentWithoutDialog(a, file, a.getIntent()
                                                                         .getStringExtra(DocumentController.EXTRA_PLAYLIST));
                        }
                    });
                }

                // PDF文本重排
                if (isPDF && which == i++) {
                    ExtUtils.openPDFInTextReflow(a, file, page + 1, dc);
                }

                // 打开方式
                if (which == i++) {
                    ExtUtils.openWith(a, file);
                }
                // 删除
                else if (isMainTabs && canDelete && which == i++) {
                    FileInformationDialog.dialogDelete(a, file, onDeleteAction);
                }
                // 复制
                else if (isMainTabs && canCopy && which == i++) {
                    TempHolder.get().copyFromPath = file.getPath();
                    Toast.makeText(a, R.string.copy, Toast.LENGTH_SHORT).show();
                    EventBus.getDefault().post(new UpdateAllFragments());
                }
                // 添加/移出书库
                else if (isMainTabs && which == i++) {
                    if (isRemovedFromLibrary) {
                        // 添加到书库
                        FileMeta load = AppDB.get().load(file.getPath());
                        if (load != null) {
                            load.setIsSearchBook(true);
                            AppDB.get().update(load);
                            AppData.get().removeExcluded(load);
                        }
                    } else {
                        // 移出书库
                        FileMeta load = AppDB.get().load(file.getPath());
                        if (load != null) {
                            load.setIsSearchBook(false);
                            load.setIsStar(false);
                            AppDB.get().update(load);
                            AppData.get().removeFavorite(load);
                            AppData.get().addExclue(load.getPath());
                            Tags2.updateTagsDB();
                        }
                    }
                    EventBus.getDefault().post(new UpdateAllFragments());
                }
                // 添加标签
                else if (!isExternalOrCloud && which == i++) {
                    Dialogs.showTagsDialog(a, file, false, new Runnable() {
                        @Override public void run() {
                            Tags2.updateTagsDB();
                        }
                    });
                }
                // 上传到云端
                else if (AppsConfig.isCloudsEnable && which == i++) {
                    showAddToCloudDialog(a, file);
                }
                // 同步书籍
                else if (!isSyncronized && which == i++) {
                    final File to = new File(AppProfile.SYNC_FOLDER_BOOKS, file.getName());
                    boolean result = IO.copyFile(file, to);
                    if (result && AppSP.get().isEnableSync) {
                        AppDB.get().setIsSearchBook(file.getPath(), false);
                        FileMetaCore.createMetaIfNeed(to.getPath(), true);

                        // 同步最近阅读记录
                        boolean isRecent = AppData.contains(AppData.get().getAllRecent(false), file.getPath());
                        LOG.d("isRecent", isRecent, file.getPath());
                        if (isRecent) {
                            AppData.get().removeRecent(new FileMeta(file.getPath()));
                            final FileMeta load = AppDB.get().load(file.getPath());
                            if (load != null && load.getIsRecentTime() != null) {
                                AppData.get().addRecent(new SimpleMeta(to.getPath(), load.getIsRecentTime()));
                            } else {
                                AppData.get().addRecent(new SimpleMeta(to.getPath()));
                            }
                        }

                        // 同步书签
                        final List<AppBookmark> bookmarks = BookmarksData.get().getBookmarksByBook(file.getPath());
                        for (AppBookmark appBookmark : bookmarks) {
                            appBookmark.path = MyPath.toRelative(to.getPath());
                            BookmarksData.get().add(appBookmark);
                        }

                        // 启动同步服务
                        GFile.runSyncService(a);
                    }
                    TempHolder.listHash++;
                    EventBus.getDefault().post(new UpdateAllFragments());
                }
                // 删除阅读进度
                else if (isMainTabs && which == i++) {
                    SharedBooks.deleteProgress(file.getPath());
                    EventBus.getDefault().post(new UpdateAllFragments());
                }
                // 文件信息
                else if (isShowInfo && which == i++) {
                    FileInformationDialog.showFileInfoDialog(a, file, onDeleteAction);
                }
            }
        });

        AlertDialog create = builder.create();

        // 暂停图片加载
        IMG.pauseRequests(a);
        create.setOnDismissListener(new OnDismissListener() {
            @Override public void onDismiss(DialogInterface dialog) {
                IMG.resumeRequests(a);
                Keyboards.hideNavigation(a);
            }
        });

        // 设置对话框背景样式
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(24);
        drawable.setColor(AppState.get().isDayNotInvert ? Color.WHITE : Color.BLACK);
        create.getWindow().setBackgroundDrawable(drawable);

        create.show();
    }

    /**
     * 显示云存储选择对话框
     * <p>
     * 提供Dropbox、Google Drive、OneDrive三个云存储选项，支持上传书籍。
     * 如果未登录，先跳转到登录界面。
     *
     * @param a    活动上下文
     * @param file 要上传的文件
     */
    public static void showAddToCloudDialog(final Activity a, final File file) {
        final AlertDialog.Builder inner = new AlertDialog.Builder(a);
        inner.setTitle(R.string.upload_to_cloud);

        // 云存储选项列表：(名称资源ID, 图标资源ID)
        List<Pair<Integer, Integer>> list = Arrays.asList(//
                new Pair<Integer, Integer>(R.string.dropbox, R.drawable.dropbox), //
                new Pair<Integer, Integer>(R.string.google_drive, R.drawable.gdrive), //
                new Pair<Integer, Integer>(R.string.one_drive, R.drawable.onedrive)//
                                                         );

        inner.setAdapter(new BaseItemLayoutAdapter<Pair<Integer, Integer>>(a, R.layout.item_dict_line, list) {
            @Override public void populateView(View layout, int position, Pair<Integer, Integer> item) {
                ((TextView) layout.findViewById(R.id.text1)).setText(item.first);
                ImageView imageView = (ImageView) layout.findViewById(R.id.image1);
                imageView.setImageResource(item.second);

                TintUtil.setNoTintImage(imageView);

                // 未登录的云存储显示灰色图标
                if (R.string.dropbox == item.first && !Clouds.get().isDropbox()) {
                    TintUtil.setTintImageNoAlpha(imageView, Color.LTGRAY);
                }
                if (R.string.google_drive == item.first && !Clouds.get().isGoogleDrive()) {
                    TintUtil.setTintImageNoAlpha(imageView, Color.LTGRAY);
                }
                if (R.string.one_drive == item.first && !Clouds.get().isOneDrive()) {
                    TintUtil.setTintImageNoAlpha(imageView, Color.LTGRAY);
                }
            }
        }, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                // Dropbox
                if (which == 0) {
                    if (Clouds.get().isDropbox()) {
                        Clouds.get().syncronizeAdd(a, file, Clouds.get().dropbox);
                    } else {
                        Clouds.get().loginToDropbox(a, new Runnable() {
                            @Override public void run() {
                                Clouds.get().syncronizeAdd(a, file, Clouds.get().dropbox);
                            }
                        });
                    }
                }
                // Google Drive
                else if (which == 1) {
                    if (Clouds.get().isGoogleDrive()) {
                        Clouds.get().syncronizeAdd(a, file, Clouds.get().googleDrive);
                    } else {
                        Clouds.get().loginToDropbox(a, new Runnable() {
                            @Override public void run() {
                                Clouds.get().syncronizeAdd(a, file, Clouds.get().googleDrive);
                            }
                        });
                    }
                }
                // OneDrive
                else if (which == 2) {
                    if (Clouds.get().isOneDrive()) {
                        Clouds.get().syncronizeAdd(a, file, Clouds.get().oneDrive);
                    } else {
                        Clouds.get().loginToDropbox(a, new Runnable() {
                            @Override public void run() {
                                Clouds.get().syncronizeAdd(a, file, Clouds.get().oneDrive);
                            }
                        });
                    }
                }
            }
        });
        inner.show();
    }
}