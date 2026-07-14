package com.foobnix.pdf.info.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.foobnix.LibreraApp;
import com.foobnix.android.utils.BaseItemLayoutAdapter;
import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.android.utils.Views;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.DictsHelper;
import com.foobnix.pdf.info.DictsHelper.DictItem;
import com.foobnix.pdf.info.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 翻译语言选择对话框
 * <p>
 * 提供翻译源语言、目标语言选择和词典选择功能。支持在线和离线词典切换，
 * 支持语言方向反转。主要用于阅读时的单词翻译功能配置。
 */
public class DialogTranslateFromTo {
    /** 简体中文语言代码 */
    public static final String CHINESE_SIMPLE = "zh-rCN";
    /** 繁体中文语言代码 */
    public static final String CHINESE_TRADITIOANAL = "zh-rTW";

    /** 支持的语言列表（语言名称 -> 语言代码） */
    static Map<String, String> langs = new LinkedHashMap<String, String>();
    static {
        langs.put("Afrikaans", "af");
        langs.put("Albanian", "sq");
        langs.put("Arabic", "ar");
        langs.put("Armenian", "hy");
        langs.put("Azerbaijani", "az");
        langs.put("Basque", "eu");
        langs.put("Belarusian", "be");
        langs.put("Bengali", "bn");
        langs.put("Bosnian", "bs");
        langs.put("Bulgarian", "bg");
        langs.put("Catalan", "ca");
        langs.put("Cebuano", "ceb");
        langs.put("Chichewa", "ny");
        langs.put("Chinese", "zh");
        langs.put("Chinese Traditional", "zh-TW");
        langs.put("Croatian", "hr");
        langs.put("Czech", "cs");
        langs.put("Danish", "da");
        langs.put("Dutch", "nl");
        langs.put("English", "en");
        langs.put("Esperanto", "eo");
        langs.put("Estonian", "et");
        langs.put("Filipino", "tl");
        langs.put("Finnish", "fi");
        langs.put("French", "fr");
        langs.put("Galician", "gl");
        langs.put("Georgian", "ka");
        langs.put("German", "de");
        langs.put("Greek", "el");
        langs.put("Gujarati", "gu");
        langs.put("Haitian Creole", "ht");
        langs.put("Hausa", "ha");
        langs.put("Hebrew", "iw");
        langs.put("Hindi", "hi");
        langs.put("Hmong", "hmn");
        langs.put("Hungarian", "hu");
        langs.put("Icelandic", "is");
        langs.put("Igbo", "ig");
        langs.put("Indonesian", "id");
        langs.put("Irish", "ga");
        langs.put("Italian", "it");
        langs.put("Japanese", "ja");
        langs.put("Javanese", "jw");
        langs.put("Kannada", "kn");
        langs.put("Kazakh", "kk");
        langs.put("Khmer", "km");
        langs.put("Korean", "ko");
        langs.put("Lao", "lo");
        langs.put("Latin", "la");
        langs.put("Latvian", "lv");
        langs.put("Lithuanian", "lt");
        langs.put("Macedonian", "mk");
        langs.put("Malagasy", "mg");
        langs.put("Malay", "ms");
        langs.put("Malayalam", "ml");
        langs.put("Maltese", "mt");
        langs.put("Maori", "mi");
        langs.put("Marathi", "mr");
        langs.put("Mongolian", "mn");
        langs.put("Myanmar (Burmese)", "my");
        langs.put("Nepali", "ne");
        langs.put("Norwegian", "no");
        langs.put("Persian", "fa");
        langs.put("Polish", "pl");
        langs.put("Portuguese", "pt");
        langs.put("Punjabi", "ma");
        langs.put("Romanian", "ro");
        langs.put("Russian", "ru");
        langs.put("Serbian", "sr");
        langs.put("Sesotho", "st");
        langs.put("Sinhala", "si");
        langs.put("Slovak", "sk");
        langs.put("Slovenian", "sl");
        langs.put("Somali", "so");
        langs.put("Spanish", "es");
        langs.put("Sudanese", "su");
        langs.put("Swahili", "sw");
        langs.put("Swedish", "sv");
        langs.put("Tajik", "tg");
        langs.put("Tamil", "ta");
        langs.put("Telugu", "te");
        langs.put("Thai", "th");
        langs.put("Turkish", "tr");
        langs.put("Ukrainian", "uk");
        langs.put("Urdu", "ur");
        langs.put("Uzbek", "uz");
        langs.put("Vietnamese", "vi");
        langs.put("Welsh", "cy");
        langs.put("Yiddish", "yi");
        langs.put("Yoruba", "yo");
        langs.put("Zulu", "zu");
        langs.put("Irish", "ga");
        langs.put("Български", "bg");
        langs.put("Ελληνικά", "el");
    }

    /**
     * 根据语言代码获取语言名称
     * <p>
     * 优先从系统Locale获取显示名称，其次从预定义语言列表中查找。
     * 支持简体中文和繁体中文的特殊处理。
     *
     * @param code 语言代码（如en、zh、zh-rCN）
     * @return 语言名称
     */
    public static String getLanuageByCode(String code) {
        try {
            // 上下文为空或代码为空时返回空字符串
            if (LibreraApp.context == null || TxtUtils.isEmpty(code)) {
                return "";
            }

            // 系统语言特殊处理
            if (AppState.MY_SYSTEM_LANG.equals(code)) {
                return LibreraApp.context.getString(R.string.system_language);
            }

            try {
                // 中文特殊处理
                if (code.equals(CHINESE_SIMPLE)) {
                    return LibreraApp.context.getString(R.string.simplified_chinese);
                }
                if (code.equals(CHINESE_TRADITIOANAL)) {
                    return LibreraApp.context.getString(R.string.traditional_chinese);
                }
                // 截取前两位作为语言代码
                if (code.length() > 2) {
                    code = code.substring(0, 2);
                }
                // 使用Locale获取语言显示名称
                Locale l = new Locale(code);
                return TxtUtils.firstUppercase(l.getDisplayLanguage(l));
            } catch (Exception e) {
                LOG.e(e);
            }

            // 从预定义列表中查找
            for (String key : langs.keySet()) {
                String value = langs.get(key);
                if (code.equals(value)) {
                    return key;
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        return code;
    }

    /**
     * 获取带下划线的当前选中词典名称
     *
     * @return 带下划线的词典名称（Spanned格式）
     */
    public static Spanned getSelectedDictionaryUnderline() {
        return Html.fromHtml("<u>" + getSelectedDictionary() + "</u>", Html.FROM_HTML_MODE_LEGACY);
    }

    /**
     * 获取当前选中的词典名称
     *
     * @return 词典名称
     */
    public static String getSelectedDictionary() {
        return DictItem.fetchDictName(AppState.get().rememberDict1);
    }

    /**
     * 显示翻译语言选择对话框
     * <p>
     * 包含源语言选择、目标语言选择、词典列表选择。支持语言方向反转和在线/离线模式切换。
     *
     * @param a         上下文Activity
     * @param onlyoffline 是否仅显示离线词典
     * @param runnable  选择完成后的回调
     * @param isAddDict 是否为添加词典模式
     * @return AlertDialog实例
     */
    public static AlertDialog show(final Activity a, boolean onlyoffline, final Runnable runnable, final boolean isAddDict) {

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(a);
        alertDialog.setTitle(R.string.choose_);

        // 加载对话框布局
        View body = LayoutInflater.from(a).inflate(R.layout.dialog_translate_from_to, null, false);

        final Spinner spinnerFrom = (Spinner) body.findViewById(R.id.spinnerFrom);
        final Spinner spinnerTo = (Spinner) body.findViewById(R.id.spinnerTo);

        ImageView imageOk = (ImageView) body.findViewById(R.id.imageOK);
        View onReverse = body.findViewById(R.id.onReverse);

        // 离线模式下隐藏语言选择相关控件
        if (onlyoffline) {
            spinnerFrom.setVisibility(View.GONE);
            spinnerTo.setVisibility(View.GONE);
            imageOk.setVisibility(View.GONE);
            onReverse.setVisibility(View.GONE);
        }

        // 初始化语言列表
        final List<String> langNames = new ArrayList<String>(langs.keySet());
        final List<String> langCodes = new ArrayList<String>(langs.values());

        // 设置源语言选择器适配器
        spinnerFrom.setAdapter(new BaseItemLayoutAdapter<String>(a, android.R.layout.simple_spinner_dropdown_item, langNames) {
            @Override
            public void populateView(View inflate, int arg1, String value) {
                Views.text(inflate, android.R.id.text1, "" + value);
            }
        });
        spinnerFrom.setSelection(langCodes.indexOf(AppState.get().fromLang));

        // 设置目标语言选择器适配器
        spinnerTo.setAdapter(new BaseItemLayoutAdapter<String>(a, android.R.layout.simple_spinner_dropdown_item, langNames) {
            @Override
            public void populateView(View inflate, int arg1, String value) {
                Views.text(inflate, android.R.id.text1, "" + value);
            }
        });
        spinnerTo.setSelection(langCodes.indexOf(AppState.get().toLang));

        // 词典列表
        final ListView dictSpinner = (ListView) body.findViewById(R.id.dictionaries);

        // 获取词典列表（离线 + 在线）
        final List<DictItem> list = DictsHelper.getAllResolveInfoAsDictItem1(a, "");
        if (!onlyoffline) {
            list.addAll(DictsHelper.getOnlineDicts(a, ""));
        }

        // 设置词典列表适配器
        dictSpinner.setAdapter(new BaseItemLayoutAdapter<DictItem>(a, R.layout.item_dict_line, list) {
            @Override
            public void populateView(View layout, int position, DictItem item) {
                ((TextView) layout.findViewById(R.id.text1)).setText(item.name);
                ((TextView) layout.findViewById(R.id.type1)).setText(item.type);
                // 设置词典图标（在线/离线标识）
                if (item.image == null) {
                    ((ImageView) layout.findViewById(R.id.image1)).setImageResource(R.drawable.glyphicons_544_cloud);
                } else {
                    ((ImageView) layout.findViewById(R.id.image1)).setImageDrawable(item.image);
                }
            }
        });

        // 语言方向反转按钮点击事件
        onReverse.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                int p1 = spinnerFrom.getSelectedItemPosition();
                int p2 = spinnerTo.getSelectedItemPosition();
                spinnerTo.setSelection(p1);
                spinnerFrom.setSelection(p2);
            }
        });

        alertDialog.setView(body);

        // 取消按钮
        alertDialog.setNeutralButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });

        final AlertDialog show = alertDialog.show();

        // 词典列表点击事件
        dictSpinner.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // 根据模式保存不同的词典配置
                if (isAddDict) {
                    AppState.get().rememberDictHash2 = list.get(position).hash;
                } else {
                    AppState.get().rememberDict1Hash = list.get(position).hash;
                    AppState.get().rememberDict1 = list.get(position).toString();
                }
                // 保存语言配置
                AppState.get().fromLang = langCodes.get(spinnerFrom.getSelectedItemPosition());
                AppState.get().toLang = langCodes.get(spinnerTo.getSelectedItemPosition());
                try {
                    show.dismiss();
                } catch (Exception e) {
                }
                runnable.run();
            }
        });

        // OK按钮点击事件（仅保存语言配置，不选择词典）
        imageOk.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AppState.get().fromLang = langCodes.get(spinnerFrom.getSelectedItemPosition());
                AppState.get().toLang = langCodes.get(spinnerTo.getSelectedItemPosition());
                try {
                    show.dismiss();
                } catch (Exception e) {
                }
            }
        });

        return show;
    }
}