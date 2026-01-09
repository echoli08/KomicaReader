package com.komica.reader.util

import android.util.Log
import com.komica.reader.BuildConfig

// 繁體中文註解：安全的日誌工具，Release 模式下避免輸出 Debug/Verbose
object KLog {
    private const val TAG = "KomicaReader"

    @JvmStatic
    fun v(msg: String) {
        if (BuildConfig.DEBUG) Log.v(TAG, msg)
    }

    @JvmStatic
    fun d(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    @JvmStatic
    fun i(msg: String) {
        Log.i(TAG, msg)
    }

    @JvmStatic
    fun w(msg: String) {
        Log.w(TAG, msg)
    }

    @JvmStatic
    fun e(msg: String) {
        Log.e(TAG, msg)
    }

    @JvmStatic
    fun e(msg: String, tr: Throwable) {
        Log.e(TAG, msg, tr)
    }
}
