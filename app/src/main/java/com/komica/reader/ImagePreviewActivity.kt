package com.komica.reader

import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.komica.reader.adapter.ImagePagerAdapter
import com.komica.reader.databinding.ActivityImagePreviewBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ImagePreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImagePreviewBinding
    private lateinit var prefs: SharedPreferences
    private var imageUrls: List<String> = emptyList()
    private var currentPosition: Int = 0
    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null
    private var slideshowJob: Job? = null
    private var isSlideshowEnabled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImagePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        imageUrls = intent.getStringArrayListExtra("imageUrls") ?: emptyList()
        currentPosition = intent.getIntExtra("position", 0)
        // 繁體中文註解：預設不自動啟動輪播，需由使用者手動開始
        isSlideshowEnabled = false

        if (imageUrls.isNotEmpty()) {
            binding.viewPager.adapter = ImagePagerAdapter(imageUrls)
            binding.viewPager.setCurrentItem(currentPosition, false)
            updateCounter()
            updateSlideshowToggle()
            binding.btnSlideshowToggle.visibility = if (imageUrls.size > 1) View.VISIBLE else View.GONE
            binding.btnSlideshowSpeed.visibility = if (imageUrls.size > 1) View.VISIBLE else View.GONE
            binding.btnSlideshowToggle.setOnClickListener { toggleSlideshow() }
            binding.btnSlideshowToggle.setOnLongClickListener {
                showSlideshowIntervalHint()
                true
            }
            binding.btnSlideshowSpeed.setOnClickListener { showSlideshowSpeedDialog() }

            pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    updateCounter()
                    if (isSlideshowEnabled) {
                        restartSlideshow()
                    }
                }
            }
            binding.viewPager.registerOnPageChangeCallback(pageChangeCallback!!)
        } else {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        updateKeepScreenOnState()
        if (isSlideshowEnabled) {
            startSlideshow()
        }
    }

    override fun onPause() {
        // 繁體中文註解：暫停輪播避免背景持續切換
        stopSlideshow()
        // 繁體中文註解：離開畫面時關閉常亮，避免背景耗電
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }

    override fun onDestroy() {
        // 繁體中文註解：解除註冊避免 ViewPager2 洩漏
        pageChangeCallback?.let { binding.viewPager.unregisterOnPageChangeCallback(it) }
        stopSlideshow()
        super.onDestroy()
    }

    private fun updateCounter() {
        val current = binding.viewPager.currentItem + 1
        val total = imageUrls.size
        binding.imageCounter.text = "$current / $total"
    }

    private fun startSlideshow() {
        if (!isSlideshowEnabled) return
        if (imageUrls.size <= 1) return
        val intervalSeconds = getSlideshowIntervalSeconds()
        if (intervalSeconds <= 0) return

        stopSlideshow()
        // 繁體中文註解：使用協程定時切換下一張圖片
        slideshowJob = lifecycleScope.launch {
            while (isActive) {
                delay(intervalSeconds * 1000L)
                if (imageUrls.isEmpty()) continue
                val next = (binding.viewPager.currentItem + 1) % imageUrls.size
                binding.viewPager.setCurrentItem(next, true)
            }
        }
    }

    private fun stopSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = null
    }

    private fun restartSlideshow() {
        stopSlideshow()
        startSlideshow()
    }

    private fun toggleSlideshow() {
        if (isSlideshowEnabled) {
            // 繁體中文註解：使用者手動暫停輪播
            isSlideshowEnabled = false
            stopSlideshow()
            updateSlideshowToggle()
            updateKeepScreenOnState()
            return
        }

        val intervalSeconds = getSlideshowIntervalSeconds()
        if (intervalSeconds <= 0) {
            Toast.makeText(this, R.string.msg_slideshow_disabled, Toast.LENGTH_SHORT).show()
            return
        }

        isSlideshowEnabled = true
        startSlideshow()
        updateSlideshowToggle()
        updateKeepScreenOnState()
    }

    private fun updateSlideshowToggle() {
        // 繁體中文註解：改用統一風格的自訂圖示
        val iconRes = if (isSlideshowEnabled) {
            R.drawable.ic_slideshow_pause
        } else {
            R.drawable.ic_slideshow_play
        }
        val descRes = if (isSlideshowEnabled) {
            R.string.content_desc_slideshow_pause
        } else {
            R.string.content_desc_slideshow_play
        }
        binding.btnSlideshowToggle.setImageResource(iconRes)
        binding.btnSlideshowToggle.contentDescription = getString(descRes)
    }

    private fun showSlideshowSpeedDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_slideshow_interval_title))
            .setItems(SLIDESHOW_INTERVAL_LABELS) { _, which ->
                val seconds = SLIDESHOW_INTERVAL_VALUES[which]
                // 繁體中文註解：儲存輪播秒數並依狀態更新輪播
                prefs.edit().putInt(KEY_SLIDESHOW_INTERVAL_SECONDS, seconds).apply()
                if (seconds <= 0) {
                    isSlideshowEnabled = false
                    stopSlideshow()
                } else if (isSlideshowEnabled) {
                    restartSlideshow()
                }
                updateSlideshowToggle()
                updateKeepScreenOnState()
                showSlideshowIntervalHint()
            }
            .show()
    }

    private fun getSlideshowIntervalSeconds(): Int {
        return prefs.getInt(KEY_SLIDESHOW_INTERVAL_SECONDS, DEFAULT_SLIDESHOW_INTERVAL_SECONDS)
    }

    private fun showSlideshowIntervalHint() {
        val intervalSeconds = getSlideshowIntervalSeconds()
        val message = if (intervalSeconds <= 0) {
            getString(R.string.msg_slideshow_disabled)
        } else {
            getString(R.string.msg_slideshow_interval_current, intervalSeconds)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun updateKeepScreenOnState() {
        val keepScreenOnForSlideshow = prefs.getBoolean(KEY_KEEP_SCREEN_ON_SLIDESHOW, false)
        val keepScreenOnForPreview = prefs.getBoolean(KEY_KEEP_SCREEN_ON_PREVIEW, false)
        val canSlideshowKeepOn = isSlideshowEnabled && imageUrls.size > 1 && keepScreenOnForSlideshow
        val shouldKeepOn = keepScreenOnForPreview || canSlideshowKeepOn
        if (shouldKeepOn) {
            // 繁體中文註解：依使用者設定啟用螢幕常亮
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    companion object {
        private const val PREFS_NAME = "KomicaReader"
        private const val KEY_SLIDESHOW_INTERVAL_SECONDS = "slideshow_interval_seconds"
        private const val KEY_KEEP_SCREEN_ON_SLIDESHOW = "keep_screen_on_slideshow"
        private const val KEY_KEEP_SCREEN_ON_PREVIEW = "keep_screen_on_preview"
        private const val DEFAULT_SLIDESHOW_INTERVAL_SECONDS = 3
        private val SLIDESHOW_INTERVAL_LABELS = arrayOf("關閉", "2 秒", "3 秒", "5 秒", "8 秒", "10 秒")
        private val SLIDESHOW_INTERVAL_VALUES = intArrayOf(0, 2, 3, 5, 8, 10)
    }
}
