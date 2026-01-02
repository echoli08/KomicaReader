package com.komica.reader.util;

import android.util.Log;
import com.komica.reader.BuildConfig;

/**
 * 安全日誌工具類。
 * 在 Release 模式下會自動過濾 Debug/Verbose 日誌，防止洩漏 URL 與敏感資訊。
 */
public class KLog {
    private static final String TAG = "KomicaReader";

    public static void v(String msg) {
        if (BuildConfig.DEBUG) Log.v(TAG, msg);
    }

    public static void d(String msg) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg);
    }

    public static void i(String msg) {
        Log.i(TAG, msg);
    }

    public static void w(String msg) {
        Log.w(TAG, msg);
    }

    public static void e(String msg) {
        Log.e(TAG, msg);
    }

    public static void e(String msg, Throwable tr) {
        Log.e(TAG, msg, tr);
    }
}
