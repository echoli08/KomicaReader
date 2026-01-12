package com.komica.reader

import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.komica.reader.databinding.ActivitySettingsBinding
import com.komica.reader.repository.KomicaRepository
import com.komica.reader.util.FavoritesManager
import com.komica.reader.util.ImageDownloadUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences
    private val favoritesManager: FavoritesManager by lazy { FavoritesManager.getInstance(this) }

    private val exportFavoritesLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        exportFavoritesToUri(uri)
    }

    private val importFavoritesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        importFavoritesFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayShowTitleEnabled(false)
            setDisplayHomeAsUpEnabled(true)
        }

        updateLabels()

        binding.btnFontSizeList.setOnClickListener { showSizeDialog("theme_font_size") }
        binding.btnFontSizePost.setOnClickListener { showSizeDialog("post_font_size") }
        binding.itemReplyWebviewSlim.setOnClickListener { binding.switchReplyWebviewSlim.toggle() }
        binding.btnClearCache.setOnClickListener { showClearCacheDialog() }
        binding.btnSlideshowInterval.setOnClickListener { showSlideshowIntervalDialog() }
        binding.itemKeepScreenOnSlideshow.setOnClickListener { binding.switchKeepScreenOnSlideshow.toggle() }
        binding.itemKeepScreenOnPreview.setOnClickListener { binding.switchKeepScreenOnPreview.toggle() }
        binding.btnImageMaxZoom.setOnClickListener { showImageMaxZoomDialog() }
        binding.btnDownloadPath.setOnClickListener { showDownloadPathDialog() }
        binding.btnExportFavorites.setOnClickListener { exportFavorites() }
        binding.btnImportFavorites.setOnClickListener { importFavorites() }

        val isSlimEnabled = prefs.getBoolean(KEY_REPLY_WEBVIEW_SLIM, true)
        binding.switchReplyWebviewSlim.isChecked = isSlimEnabled
        binding.switchReplyWebviewSlim.setOnCheckedChangeListener { _, isChecked ->
            // 儲存回覆頁資源精簡設定
            prefs.edit().putBoolean(KEY_REPLY_WEBVIEW_SLIM, isChecked).apply()
        }

        val isKeepScreenOnSlideshowEnabled = prefs.getBoolean(KEY_KEEP_SCREEN_ON_SLIDESHOW, false)
        binding.switchKeepScreenOnSlideshow.isChecked = isKeepScreenOnSlideshowEnabled
        binding.switchKeepScreenOnSlideshow.setOnCheckedChangeListener { _, isChecked ->
            // 繁體中文註解：儲存輪播時保持螢幕常亮設定
            prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON_SLIDESHOW, isChecked).apply()
        }

        val isKeepScreenOnPreviewEnabled = prefs.getBoolean(KEY_KEEP_SCREEN_ON_PREVIEW, false)
        binding.switchKeepScreenOnPreview.isChecked = isKeepScreenOnPreviewEnabled
        binding.switchKeepScreenOnPreview.setOnCheckedChangeListener { _, isChecked ->
            // 繁體中文註解：儲存預覽時保持螢幕常亮設定
            prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON_PREVIEW, isChecked).apply()
        }

        binding.btnAbout.setOnClickListener { showAboutDialog() }
    }

    private fun updateLabels() {
        val listSize = prefs.getFloat("theme_font_size", 16f)
        val postSize = prefs.getFloat("post_font_size", 16f)
        val slideshowInterval = prefs.getInt(KEY_SLIDESHOW_INTERVAL_SECONDS, DEFAULT_SLIDESHOW_INTERVAL_SECONDS)
        val imageMaxZoom = prefs.getFloat(KEY_IMAGE_MAX_ZOOM, DEFAULT_IMAGE_MAX_ZOOM)
        val downloadPath = ImageDownloadUtils.getDownloadPathType(this)

        binding.tvFontSizeListValue.text = getLabelForSize(listSize)
        binding.tvFontSizePostValue.text = getLabelForSize(postSize)
        binding.tvSlideshowIntervalValue.text = getSlideshowIntervalLabel(slideshowInterval)
        binding.tvImageMaxZoomValue.text = getImageMaxZoomLabel(imageMaxZoom)
        binding.tvDownloadPathValue.text = ImageDownloadUtils.getDownloadPathLabel(this, downloadPath)
    }

    private fun getLabelForSize(size: Float): String {
        for (i in SIZE_VALUES.indices) {
            if (kotlin.math.abs(size - SIZE_VALUES[i]) < 0.1f) {
                return SIZE_LABELS[i] + " (" + size.toInt() + "sp)"
            }
        }
        return "自訂 (" + size + "sp)"
    }

    private fun showSizeDialog(key: String) {
        AlertDialog.Builder(this)
            .setTitle("選擇字體大小")
            .setItems(SIZE_LABELS) { _, which ->
                val size = SIZE_VALUES[which]
                prefs.edit().putFloat(key, size).apply()
                updateLabels()
            }
            .show()
    }

    private fun showSlideshowIntervalDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_slideshow_interval_title))
            .setItems(SLIDESHOW_INTERVAL_LABELS) { _, which ->
                val seconds = SLIDESHOW_INTERVAL_VALUES[which]
                // 繁體中文註解：儲存輪播秒數設定
                prefs.edit().putInt(KEY_SLIDESHOW_INTERVAL_SECONDS, seconds).apply()
                updateLabels()
            }
            .show()
    }

    private fun getSlideshowIntervalLabel(seconds: Int): String {
        for (i in SLIDESHOW_INTERVAL_VALUES.indices) {
            if (seconds == SLIDESHOW_INTERVAL_VALUES[i]) {
                return SLIDESHOW_INTERVAL_LABELS[i]
            }
        }
        return seconds.toString() + " 秒"
    }

    private fun showImageMaxZoomDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.setting_image_max_zoom))
            .setItems(IMAGE_MAX_ZOOM_LABELS) { _, which ->
                val zoomScale = IMAGE_MAX_ZOOM_VALUES[which]
                // 繁體中文註解：儲存預覽最大縮放倍率設定
                prefs.edit().putFloat(KEY_IMAGE_MAX_ZOOM, zoomScale).apply()
                updateLabels()
            }
            .show()
    }

    private fun getImageMaxZoomLabel(scale: Float): String {
        for (i in IMAGE_MAX_ZOOM_VALUES.indices) {
            if (kotlin.math.abs(scale - IMAGE_MAX_ZOOM_VALUES[i]) < 0.01f) {
                return IMAGE_MAX_ZOOM_LABELS[i]
            }
        }
        return scale.toInt().toString() + " 倍"
    }

    private fun showDownloadPathDialog() {
        val labels = arrayOf(
            getString(R.string.setting_download_path_value_pictures),
            getString(R.string.setting_download_path_value_downloads)
        )
        val values = arrayOf(
            ImageDownloadUtils.DOWNLOAD_PATH_PICTURES,
            ImageDownloadUtils.DOWNLOAD_PATH_DOWNLOADS
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_download_path_title))
            .setItems(labels) { _, which ->
                // 繁體中文註解：儲存圖片下載路徑設定
                prefs.edit().putString(ImageDownloadUtils.KEY_IMAGE_DOWNLOAD_PATH, values[which]).apply()
                updateLabels()
            }
            .show()
    }

    private fun exportFavorites() {
        val favorites = favoritesManager.getFavorites()
        if (favorites.isEmpty()) {
            Toast.makeText(this, R.string.msg_export_favorites_empty, Toast.LENGTH_SHORT).show()
            return
        }
        // 繁體中文註解：建立匯出檔案，由使用者選擇儲存位置
        exportFavoritesLauncher.launch(DEFAULT_FAVORITES_EXPORT_NAME)
    }

    private fun exportFavoritesToUri(uri: android.net.Uri) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val favorites = favoritesManager.getFavorites().toList().sorted()
                    contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                        favorites.forEach { writer.appendLine(it) }
                    } ?: return@withContext false
                    true
                } catch (e: Exception) {
                    false
                }
            }
            val messageRes = if (success) {
                R.string.msg_export_favorites_success
            } else {
                R.string.msg_export_favorites_failed
            }
            Toast.makeText(this@SettingsActivity, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    private fun importFavorites() {
        // 繁體中文註解：從檔案匯入我的最愛
        importFavoritesLauncher.launch(arrayOf("text/*"))
    }

    private fun importFavoritesFromUri(uri: android.net.Uri) {
        lifecycleScope.launch {
            val imported = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                        parseFavorites(reader.readText())
                    } ?: emptySet()
                } catch (e: Exception) {
                    null
                }
            }

            if (imported == null) {
                Toast.makeText(this@SettingsActivity, R.string.msg_import_favorites_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (imported.isEmpty()) {
                Toast.makeText(this@SettingsActivity, R.string.msg_import_favorites_empty, Toast.LENGTH_SHORT).show()
                return@launch
            }
            showImportFavoritesDialog(imported)
        }
    }

    private fun showImportFavoritesDialog(imported: Set<String>) {
        val options = arrayOf(
            getString(R.string.action_import_favorites_merge),
            getString(R.string.action_import_favorites_replace)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_import_favorites_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> applyImportedFavorites(imported, true)
                    1 -> applyImportedFavorites(imported, false)
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun applyImportedFavorites(imported: Set<String>, shouldMerge: Boolean) {
        val result = if (shouldMerge) {
            val merged = HashSet(favoritesManager.getFavorites())
            merged.addAll(imported)
            merged
        } else {
            imported
        }
        // 繁體中文註解：以匯入結果覆蓋或合併現有我的最愛
        favoritesManager.replaceFavorites(result)
        Toast.makeText(this, R.string.msg_import_favorites_success, Toast.LENGTH_SHORT).show()
    }

    private fun parseFavorites(content: String): Set<String> {
        return content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun showClearCacheDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_clear_cache_title))
            .setMessage(getString(R.string.dialog_clear_cache_message))
            .setPositiveButton(getString(R.string.action_confirm)) { _, _ ->
                clearCache()
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun clearCache() {
        lifecycleScope.launch {
            val clearSuccess = withContext(Dispatchers.IO) {
                // 繁體中文註解：清除磁碟快取與 HTTP 快取，避免阻塞主執行緒
                val httpCacheCleared = deleteDirIfExists(File(cacheDir, "http_cache"))
                val externalCacheCleared = deleteDirIfExists(externalCacheDir)
                Glide.get(this@SettingsActivity).clearDiskCache()
                KomicaRepository.getInstance(applicationContext).clearMemoryCache()
                httpCacheCleared && externalCacheCleared
            }

            // 繁體中文註解：記憶體快取需在主執行緒清除
            Glide.get(this@SettingsActivity).clearMemory()
            val messageRes = if (clearSuccess) {
                R.string.msg_clear_cache_success
            } else {
                R.string.msg_clear_cache_failed
            }
            Toast.makeText(this@SettingsActivity, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteDirIfExists(dir: File?): Boolean {
        if (dir == null || !dir.exists()) {
            return true
        }
        return dir.deleteRecursively()
    }

    private fun showAboutDialog() {
        val versionName = BuildConfig.VERSION_NAME

        val message = "Komica Reader\n\n" +
            "本程式為 Komica 匿名板閱讀器。\n" +
            "由 echoli08 以研究興趣製作，" +
            "如有問題或建議歡迎回報。\n\n" +
            "版本： " + versionName + "\n" +
            "作者： echoli08\n\n" +
            "本程式僅供學術研究與個人使用。"

        AlertDialog.Builder(this)
            .setTitle("關於本程式")
            .setMessage(message)
            .setPositiveButton("確定") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val PREFS_NAME = "KomicaReader"
        private const val KEY_REPLY_WEBVIEW_SLIM = "reply_webview_slim"
        private const val KEY_SLIDESHOW_INTERVAL_SECONDS = "slideshow_interval_seconds"
        private const val KEY_KEEP_SCREEN_ON_SLIDESHOW = "keep_screen_on_slideshow"
        private const val KEY_KEEP_SCREEN_ON_PREVIEW = "keep_screen_on_preview"
        private const val KEY_IMAGE_MAX_ZOOM = "image_max_zoom_scale"
        private val SIZE_LABELS = arrayOf("小", "中", "大", "特大", "超大")
        private val SIZE_VALUES = floatArrayOf(14f, 16f, 18f, 20f, 24f)
        private val SLIDESHOW_INTERVAL_LABELS = arrayOf("關閉", "1 秒", "2 秒", "3 秒", "5 秒", "8 秒", "10 秒")
        private val SLIDESHOW_INTERVAL_VALUES = intArrayOf(0, 1, 2, 3, 5, 8, 10)
        private const val DEFAULT_SLIDESHOW_INTERVAL_SECONDS = 3
        private val IMAGE_MAX_ZOOM_LABELS = arrayOf("2 倍", "3 倍", "4 倍", "5 倍")
        private val IMAGE_MAX_ZOOM_VALUES = floatArrayOf(2f, 3f, 4f, 5f)
        private const val DEFAULT_IMAGE_MAX_ZOOM = 4.0f
        private const val DEFAULT_FAVORITES_EXPORT_NAME = "komica_favorites.txt"
    }
}
