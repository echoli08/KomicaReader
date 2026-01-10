package com.komica.reader

import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.komica.reader.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

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

        val isSlimEnabled = prefs.getBoolean(KEY_REPLY_WEBVIEW_SLIM, true)
        binding.switchReplyWebviewSlim.isChecked = isSlimEnabled
        binding.switchReplyWebviewSlim.setOnCheckedChangeListener { _, isChecked ->
            // 繁體中文註解：儲存回覆頁資源精簡開關
            prefs.edit().putBoolean(KEY_REPLY_WEBVIEW_SLIM, isChecked).apply()
        }

        binding.btnAbout.setOnClickListener { showAboutDialog() }
    }

    private fun updateLabels() {
        val listSize = prefs.getFloat("theme_font_size", 16f)
        val postSize = prefs.getFloat("post_font_size", 16f)

        binding.tvFontSizeListValue.text = getLabelForSize(listSize)
        binding.tvFontSizePostValue.text = getLabelForSize(postSize)
    }

    private fun getLabelForSize(size: Float): String {
        for (i in SIZE_VALUES.indices) {
            if (kotlin.math.abs(size - SIZE_VALUES[i]) < 0.1f) {
                return SIZE_LABELS[i] + " (" + size.toInt() + "sp)"
            }
        }
        return "?ªè? (" + size + "sp)"
    }

    private fun showSizeDialog(key: String) {
        AlertDialog.Builder(this)
            .setTitle("?¸æ?å­—é?å¤§å?")
            .setItems(SIZE_LABELS) { _, which ->
                val size = SIZE_VALUES[which]
                prefs.edit().putFloat(key, size).apply()
                updateLabels()
            }
            .show()
    }

    private fun showAboutDialog() {
        val versionName = BuildConfig.VERSION_NAME

        val message = "Komica Reader\n\n" +
            "å°ˆç‚º?è¦½ Komica ?¿å?è¨Žè??¿è¨­è¨ˆç??±è??¨ã€‚\n" +
            "?ä?æµæš¢?„é–±è®€é«”é??å??‡é?è¦½è?å¼•æ?è¿½è¹¤?Ÿèƒ½?‚\n\n" +
            "?ˆæœ¬: " + versionName + "\n" +
            "?‹ç™¼?? echoli08\n\n" +
            "?¬è?é«”å?ä¾›å­¸è¡“ç?ç©¶è?äº¤æ?ä½¿ç”¨??"

        AlertDialog.Builder(this)
            .setTitle("?œæ–¼è»Ÿé?")
            .setMessage(message)
            .setPositiveButton("?œé?") { dialog, _ -> dialog.dismiss() }
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
        private val SIZE_LABELS = arrayOf("?", "??", "?", "??", "??")
        private val SIZE_VALUES = floatArrayOf(14f, 16f, 18f, 20f, 24f)
    }
}
