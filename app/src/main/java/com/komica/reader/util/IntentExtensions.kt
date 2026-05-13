package com.komica.reader.util

import android.content.Intent
import android.os.Build
import java.io.Serializable

// 繁體中文註解：集中處理 Android 13 前後 Serializable API 差異。
inline fun <reified T : Serializable> Intent.GetSerializableCompat(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSerializableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getSerializableExtra(key) as? T
    }
}
