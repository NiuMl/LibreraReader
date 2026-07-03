package com.foobnix.pdf.info.widget;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutManager;
import android.os.Build;

import com.foobnix.android.utils.LOG;
import com.foobnix.LibreraApp;
import com.foobnix.model.AppProfile;

public class RecentUpates {

    public static void updateAll() {
        Context c = LibreraApp.context;
        if (c == null) {
            return;
        }

        AppProfile.save(c);

        LOG.d("RecentUpates", "MUPDF!", c.getClass());
        try {

            {
                Intent intent = new Intent(c, RecentBooksWidget.class);
                intent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
                c.sendBroadcast(intent);
            }
            {
                Intent intent = new Intent(c, TTSWidget.class);
                intent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
                c.sendBroadcast(intent);
            }

        } catch (Exception e) {
            LOG.e(e);
        }

        if (Build.VERSION.SDK_INT >= 25) {
            try {
                ShortcutManager shortcutManager = c.getSystemService(ShortcutManager.class);
                if (shortcutManager != null) {
                    shortcutManager.removeAllDynamicShortcuts();
                }
            } catch (Exception e) {
                LOG.e(e);
            }
        }
    }

}
