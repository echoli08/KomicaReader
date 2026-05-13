package com.komica.reader.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class JsonBackupStore(context: Context) {
    private val appContext = context.applicationContext
    private val settings = AppSettings(appContext)
    private val favoritesStore = FavoritesStore(appContext)
    private val boardUiStore = BoardUiStore(appContext)

    fun ExportJson(): String {
        return JSONObject()
            .put("format", "KomicaReaderBackup")
            .put("version", 1)
            .put("favorites", JSONArray(favoritesStore.GetAll().sorted()))
            .put("settings", BuildSettingsJson())
            .put("ui", BuildUiJson())
            .toString(2)
    }

    fun ImportJson(jsonText: String) {
        val root = JSONObject(jsonText)
        val favorites = root.optJSONArray("favorites") ?: JSONArray()
        favoritesStore.Replace((0 until favorites.length()).mapNotNull { favorites.optString(it).takeIf(String::isNotBlank) })

        root.optJSONObject("settings")?.let { ImportSettings(it) }
        root.optJSONObject("ui")?.let { ImportUi(it) }
        settings.ApplyTheme()
    }

    private fun BuildSettingsJson(): JSONObject {
        return JSONObject()
            .put("themeMode", settings.themeMode)
            .put("postFontSizeSp", settings.postFontSizeSp)
            .put("imageZoomMax", settings.imageZoomMax)
            .put("slideshowSeconds", settings.slideshowSeconds)
            .put("keepScreenOn", settings.keepScreenOn)
            .put("slimReplyPage", settings.slimReplyPage)
            .put("downloadFolderName", settings.downloadFolderName)
    }

    private fun BuildUiJson(): JSONObject {
        return JSONObject()
            .put("collapsedCategories", JSONArray(boardUiStore.GetCollapsedCategories().sorted()))
    }

    private fun ImportSettings(json: JSONObject) {
        settings.themeMode = json.optString("themeMode", settings.themeMode)
        settings.postFontSizeSp = json.optInt("postFontSizeSp", settings.postFontSizeSp)
        settings.imageZoomMax = json.optDouble("imageZoomMax", settings.imageZoomMax.toDouble()).toFloat()
        settings.slideshowSeconds = json.optInt("slideshowSeconds", settings.slideshowSeconds)
        settings.keepScreenOn = json.optBoolean("keepScreenOn", settings.keepScreenOn)
        settings.slimReplyPage = json.optBoolean("slimReplyPage", settings.slimReplyPage)
        settings.downloadFolderName = json.optString("downloadFolderName", settings.downloadFolderName)
    }

    private fun ImportUi(json: JSONObject) {
        val categories = json.optJSONArray("collapsedCategories") ?: JSONArray()
        boardUiStore.ReplaceCollapsedCategories(
            (0 until categories.length()).mapNotNull { categories.optString(it).takeIf(String::isNotBlank) }
        )
    }
}
