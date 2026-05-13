package com.komica.reader.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    var themeMode: String
        get() = prefs.getString(KeyThemeMode, ThemeSystem) ?: ThemeSystem
        set(value) = prefs.edit().putString(KeyThemeMode, value).apply()

    var postFontSizeSp: Int
        get() = prefs.getInt(KeyPostFontSize, 16)
        set(value) = prefs.edit().putInt(KeyPostFontSize, value.coerceIn(13, 24)).apply()

    var imageZoomMax: Float
        get() = prefs.getFloat(KeyImageZoomMax, 4f)
        set(value) = prefs.edit().putFloat(KeyImageZoomMax, value.coerceIn(2f, 8f)).apply()

    var slideshowSeconds: Int
        get() = prefs.getInt(KeySlideshowSeconds, 3)
        set(value) = prefs.edit().putInt(KeySlideshowSeconds, value.coerceIn(1, 15)).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KeyKeepScreenOn, true)
        set(value) = prefs.edit().putBoolean(KeyKeepScreenOn, value).apply()

    var slimReplyPage: Boolean
        get() = prefs.getBoolean(KeySlimReplyPage, true)
        set(value) = prefs.edit().putBoolean(KeySlimReplyPage, value).apply()

    var downloadFolderName: String
        get() = prefs.getString(KeyDownloadFolder, "KomicaReader") ?: "KomicaReader"
        set(value) = prefs.edit().putString(KeyDownloadFolder, value.ifBlank { "KomicaReader" }).apply()

    fun ApplyTheme() {
        AppCompatDelegate.setDefaultNightMode(
            when (themeMode) {
                ThemeLight -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    companion object {
        private const val PrefsName = "komica_reader_settings"
        private const val KeyThemeMode = "theme_mode"
        private const val KeyPostFontSize = "post_font_size"
        private const val KeyImageZoomMax = "image_zoom_max"
        private const val KeySlideshowSeconds = "slideshow_seconds"
        private const val KeyKeepScreenOn = "keep_screen_on"
        private const val KeySlimReplyPage = "slim_reply_page"
        private const val KeyDownloadFolder = "download_folder"

        const val ThemeSystem = "system"
        const val ThemeLight = "light"
        const val ThemeDark = "dark"
    }
}
