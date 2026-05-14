package com.komica.reader

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.komica.reader.adapter.SettingsAdapter
import com.komica.reader.data.AppSettings
import com.komica.reader.data.HistoryStore
import com.komica.reader.data.JsonBackupStore
import com.komica.reader.databinding.ActivitySettingsBinding
import com.komica.reader.util.WindowInsetsUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: AppSettings
    private lateinit var adapter: SettingsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings(this).ApplyTheme()
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsUtil.ApplyBrandStatusBar(window)
        WindowInsetsUtil.ApplyToolbarInsets(binding.toolbar)
        settings = AppSettings(this)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.settingsRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SettingsAdapter { HandleSettingClick(it) }
        binding.settingsRecyclerView.adapter = adapter
        RefreshItems()
    }

    private fun HandleSettingClick(key: String) {
        when (key) {
            "theme" -> SelectTheme()
            "font" -> SelectFontSize()
            "zoom" -> SelectZoomMax()
            "slideshow" -> SelectSlideshowSeconds()
            "keep_screen_on" -> ToggleKeepScreenOn()
            "download_folder" -> EditDownloadFolder()
            "slim_reply" -> ToggleSlimReply()
            "favorites_backup" -> ShowJsonBackup()
            "clear_cache" -> ClearCache()
            "clear_history" -> ClearHistory()
            "about" -> ShowAbout()
        }
    }

    private fun RefreshItems() {
        adapter.SubmitItems(
            listOf(
                SettingsAdapter.SettingItem.Section("閱讀"),
                SettingsAdapter.SettingItem.Row("theme", "外觀主題", ThemeLabel(), "跟隨系統、淺色或深色"),
                SettingsAdapter.SettingItem.Row("font", "文章字體", "${settings.postFontSizeSp}sp", "影響討論串詳情內容"),
                SettingsAdapter.SettingItem.Section("圖片"),
                SettingsAdapter.SettingItem.Row("zoom", "預覽縮放上限", "${settings.imageZoomMax.toInt()} 倍", "雙指縮放最大倍率"),
                SettingsAdapter.SettingItem.Row("slideshow", "輪播秒數", "${settings.slideshowSeconds} 秒", "圖片預覽自動切換"),
                SettingsAdapter.SettingItem.Row("keep_screen_on", "圖片瀏覽保持螢幕常亮", if (settings.keepScreenOn) "開啟" else "關閉", "預覽與輪播時不自動熄屏"),
                SettingsAdapter.SettingItem.Row("download_folder", "圖片下載資料夾", settings.downloadFolderName, "儲存在 Pictures 下的子資料夾"),
                SettingsAdapter.SettingItem.Section("回覆"),
                SettingsAdapter.SettingItem.Row("slim_reply", "回覆頁資源精簡", if (settings.slimReplyPage) "開啟" else "關閉", "載入回覆頁時阻擋非必要圖片與媒體"),
                SettingsAdapter.SettingItem.Section("網路與資料"),
                SettingsAdapter.SettingItem.Row("favorites_backup", "資料備份", "JSON", "備份我的最愛、閱讀設定、圖片設定與介面狀態"),
                SettingsAdapter.SettingItem.Row("clear_cache", "清除快取", FormatBytes(GetCacheSize()), "清除 App 快取資料夾"),
                SettingsAdapter.SettingItem.Row("clear_history", "清除瀏覽歷史", "執行", "移除本機歷史紀錄"),
                SettingsAdapter.SettingItem.Row("about", "關於", AppVersionName(), "KomicaReader V2")
            )
        )
    }

    private fun SelectTheme() {
        val labels = arrayOf("跟隨系統", "淺色", "深色")
        val values = arrayOf(AppSettings.ThemeSystem, AppSettings.ThemeLight, AppSettings.ThemeDark)
        val checked = values.indexOf(settings.themeMode).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("外觀主題")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                settings.themeMode = values[which]
                settings.ApplyTheme()
                RefreshItems()
                dialog.dismiss()
            }
            .show()
    }

    private fun SelectFontSize() {
        val labels = arrayOf("13sp", "14sp", "16sp", "18sp", "20sp", "22sp", "24sp")
        val values = intArrayOf(13, 14, 16, 18, 20, 22, 24)
        ShowNumberPicker("文章字體", labels, values.indexOf(settings.postFontSizeSp)) {
            settings.postFontSizeSp = values[it]
            RefreshItems()
        }
    }

    private fun SelectZoomMax() {
        val labels = arrayOf("2 倍", "3 倍", "4 倍", "6 倍", "8 倍")
        val values = floatArrayOf(2f, 3f, 4f, 6f, 8f)
        val checked = values.indexOfFirst { it == settings.imageZoomMax }.coerceAtLeast(2)
        AlertDialog.Builder(this)
            .setTitle("預覽縮放上限")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                settings.imageZoomMax = values[which]
                RefreshItems()
                dialog.dismiss()
            }
            .show()
    }

    private fun SelectSlideshowSeconds() {
        val labels = arrayOf("1 秒", "2 秒", "3 秒", "5 秒", "8 秒", "10 秒", "15 秒")
        val values = intArrayOf(1, 2, 3, 5, 8, 10, 15)
        ShowNumberPicker("輪播秒數", labels, values.indexOf(settings.slideshowSeconds)) {
            settings.slideshowSeconds = values[it]
            RefreshItems()
        }
    }

    private fun ToggleKeepScreenOn() {
        settings.keepScreenOn = !settings.keepScreenOn
        RefreshItems()
    }

    private fun ToggleSlimReply() {
        settings.slimReplyPage = !settings.slimReplyPage
        RefreshItems()
    }

    private fun EditDownloadFolder() {
        val input = android.widget.EditText(this).apply {
            setText(settings.downloadFolderName)
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle("圖片下載資料夾")
            .setView(input)
            .setPositiveButton("儲存") { _, _ ->
                settings.downloadFolderName = input.text.toString()
                RefreshItems()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun ShowJsonBackup() {
        AlertDialog.Builder(this)
            .setTitle("JSON 備份")
            .setItems(arrayOf("匯出 JSON 檔案", "還原最新 JSON 檔案")) { _, which ->
                if (which == 0) ExportJsonBackup() else ImportJsonBackup()
            }
            .show()
    }

    private fun ExportJsonBackup() {
        val text = JsonBackupStore(this).ExportJson()
        val fileName = "komica_reader_backup_${System.currentTimeMillis()}.json"
        val success = runCatching { WriteBackupFile(fileName, text) }.isSuccess
        Toast.makeText(this, if (success) "已匯出到 Download/KomicaReader/$fileName" else "匯出 JSON 備份失敗", Toast.LENGTH_LONG).show()
    }

    private fun ImportJsonBackup() {
        val text = runCatching { ReadLatestBackupFile() }.getOrNull().orEmpty()
        val success = text.isNotBlank() && runCatching { JsonBackupStore(this).ImportJson(text) }.isSuccess
        if (success) {
            settings = AppSettings(this)
            RefreshItems()
        }
        Toast.makeText(this, if (success) "已還原最新 JSON 備份" else "還原失敗：Download/KomicaReader 找不到有效 JSON 備份", Toast.LENGTH_LONG).show()
    }

    private fun ShowNumberPicker(title: String, labels: Array<String>, checked: Int, onSelected: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels, checked.coerceAtLeast(0)) { dialog, which ->
                onSelected(which)
                dialog.dismiss()
            }
            .show()
    }

    private fun ThemeLabel(): String {
        return when (settings.themeMode) {
            AppSettings.ThemeLight -> "淺色"
            AppSettings.ThemeDark -> "深色"
            else -> "跟隨系統"
        }
    }

    private fun ClearCache() {
        runCatching { DeleteDir(cacheDir); externalCacheDir?.let { DeleteDir(it) }; Glide.get(this).clearMemory() }
        Toast.makeText(this, "快取已清除", Toast.LENGTH_SHORT).show()
        RefreshItems()
    }

    private fun ClearHistory() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { HistoryStore(this@SettingsActivity).Clear() }
            Toast.makeText(this@SettingsActivity, "瀏覽歷史已清除", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ShowAbout() {
        AlertDialog.Builder(this)
            .setTitle("KomicaReader")
            .setMessage(
                """
                KomicaReader V2 重製版
                版本：${AppVersionName()}

                主要功能：
                - 讀取 Komica 看板、主題列表與討論串內容
                - 主題列表支援向下自動載入更多討論串
                - 討論串支援引用跳轉、長壓引用預覽、圖片牆與批次下載
                - 圖片瀏覽支援手勢切換、輪播、下載與分享
                - 支援瀏覽歷史、我的最愛、深色模式與文章字體調整
                - 回覆功能使用網站原生 WebView 表單

                資料來源：
                - 看板與討論串內容來自 Komica 公開網頁
                - 歷史紀錄與設定僅儲存在本機
                """.trimIndent()
            )
            .setPositiveButton("確定", null)
            .show()
    }

    private fun AppVersionName(): String {
        return runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        }.getOrDefault("未知版本")
    }

    private fun DeleteDir(dir: File) {
        if (!dir.exists()) return
        dir.deleteRecursively()
    }

    private fun GetCacheSize(): Long {
        return GetDirSize(cacheDir) + (externalCacheDir?.let { GetDirSize(it) } ?: 0L)
    }

    private fun GetDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun FormatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < units.lastIndex) {
            size /= 1024
            unitIndex++
        }
        return if (unitIndex == 0) "${bytes} B" else String.format("%.1f %s", size, units[unitIndex])
    }

    private fun WriteBackupFile(fileName: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/KomicaReader")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("無法建立備份檔")
            contentResolver.openOutputStream(uri)?.use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
            } ?: error("無法寫入備份檔")
            return
        }

        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "KomicaReader")
        if (!dir.exists()) dir.mkdirs()
        File(dir, fileName).writeText(text, Charsets.UTF_8)
    }

    private fun ReadLatestBackupFile(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DATE_MODIFIED
            )
            val selection = "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
            val args = arrayOf("${Environment.DIRECTORY_DOWNLOADS}/KomicaReader/", "%.json")
            contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                "${MediaStore.Downloads.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    val uri = android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                    return contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                }
            }
        }

        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "KomicaReader")
        return dir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .maxByOrNull { it.lastModified() }
            ?.readText(Charsets.UTF_8)
            .orEmpty()
    }
}
