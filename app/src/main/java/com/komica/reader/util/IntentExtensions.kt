package com.komica.reader.util

import android.content.Intent
import android.os.Build
import java.io.Serializable

// 繁體中文註解：兼容舊版 API 的序列化讀取方式
inline fun <reified T : Serializable> Intent.getSerializableCompat(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSerializableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getSerializableExtra(key) as? T
    }
}
