package com.komica.reader

import android.app.AlertDialog
import android.app.WallpaperManager
import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.komica.reader.adapter.ImagePagerAdapter
import com.komica.reader.databinding.ActivityImagePreviewBinding
import com.komica.reader.util.ImageDownloadUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

class ImagePreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImagePreviewBinding
    private lateinit var prefs: SharedPreferences
    private var imageUrls: List<String> = emptyList()
    private var displayImageUrls: List<String> = emptyList()
    private var currentPosition: Int = 0
    private var isLoopEnabled: Boolean = false
    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null
    private var slideshowJob: Job? = null
    private var isSlideshowEnabled: Boolean = false
    private var pendingDownloadUrl: String? = null
    private var isVerticalSwipeHandled = false
    private var gestureDetector: GestureDetector? = null
    private var isTapHandled = false
    private var isImageZoomed = false

    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // 繁體中文註解：儲存權限核准後繼續下載圖片
        val url = pendingDownloadUrl
        pendingDownloadUrl = null
        if (isGranted && url != null) {
            downloadImage(url)
        } else if (!isGranted) {
            Toast.makeText(
                this,
                R.string.msg_storage_permission_required,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

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
                isLoopEnabled = imageUrls.size > 1
                displayImageUrls = if (isLoopEnabled) {
                    listOf(imageUrls.last()) + imageUrls + listOf(imageUrls.first())
                } else {
                    imageUrls
                }
                val imageMaxZoomScale = getImageMaxZoomScale()
                binding.viewPager.adapter = ImagePagerAdapter(
                    displayImageUrls,
                    { imageUrl ->
                        showImageOptionsDialog(imageUrl)
                    },
                    { adapterPosition, isZoomed ->
                        updateImageZoomState(adapterPosition, isZoomed)
                    },
                    imageMaxZoomScale
                )
            val initialPosition = if (isLoopEnabled) currentPosition + 1 else currentPosition
            binding.viewPager.setCurrentItem(initialPosition, false)
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
            setupPreviewGestureControls()

            pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    if (isLoopEnabled) {
                        val lastPosition = displayImageUrls.size - 1
                        when (position) {
                            0 -> binding.viewPager.post {
                                binding.viewPager.setCurrentItem(displayImageUrls.size - 2, false)
                            }
                            lastPosition -> binding.viewPager.post {
                                binding.viewPager.setCurrentItem(1, false)
                            }
                        }
                    }
                    updateCounter()
                    resetZoomStateForNewPage()
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
        val total = imageUrls.size
        val currentIndex = if (isLoopEnabled) {
            getRealPosition(binding.viewPager.currentItem)
        } else {
            binding.viewPager.currentItem.coerceAtLeast(0)
        }
        val current = currentIndex + 1
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
                showNextImage()
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

    private fun showImageOptionsDialog(imageUrl: String) {
        val options = arrayOf(
            getString(R.string.action_download_image),
            getString(R.string.action_share_image),
            getString(R.string.action_set_wallpaper)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_image_options_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> downloadImage(imageUrl)
                    1 -> shareImage(imageUrl)
                    2 -> setImageAsWallpaper(imageUrl)
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun setupPreviewGestureControls() {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val swipeThreshold = touchSlop * 3
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                isVerticalSwipeHandled = false
                isTapHandled = false
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                // 繁體中文註解：點擊左右半邊切換上一張或下一張
                if (e.x < binding.viewPager.width / 2f) {
                    showPreviousImage()
                } else {
                    showNextImage()
                }
                isTapHandled = true
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (e1 == null) return false
                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y
                val isVerticalSwipe = abs(deltaY) > abs(deltaX) * 1.2f && abs(deltaY) > swipeThreshold
                if (!isVerticalSwipeHandled && isVerticalSwipe) {
                    // 繁體中文註解：上下滑動切換圖片
                    if (deltaY > 0) {
                        showPreviousImage()
                    } else {
                        showNextImage()
                    }
                    isVerticalSwipeHandled = true
                    return true
                }
                return false
            }
        })
        binding.touchOverlay.setOnTouchListener { _, event ->
            if (event.pointerCount > 1 || isImageZoomed || imageUrls.size <= 1) {
                // 繁體中文註解：縮放或單張圖片時直接交給 ViewPager2 / ZoomableImageView 處理
                return@setOnTouchListener binding.viewPager.dispatchTouchEvent(event)
            }
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                isVerticalSwipeHandled = false
                isTapHandled = false
            }
            val handledByDetector = gestureDetector?.onTouchEvent(event) == true
            if (isVerticalSwipeHandled || isTapHandled) {
                return@setOnTouchListener true
            }
            val handledByPager = binding.viewPager.dispatchTouchEvent(event)
            handledByDetector || handledByPager
        }
    }

    private fun updateImageZoomState(adapterPosition: Int, isZoomed: Boolean) {
        if (adapterPosition != binding.viewPager.currentItem) return
        if (isImageZoomed == isZoomed) return
        // 繁體中文註解：縮放中暫停翻頁手勢，避免誤觸切換
        isImageZoomed = isZoomed
        binding.viewPager.isUserInputEnabled = !isZoomed
    }

    private fun resetZoomStateForNewPage() {
        // 繁體中文註解：切頁時重置縮放狀態，避免殘留影響操作
        isImageZoomed = false
        binding.viewPager.isUserInputEnabled = true
    }

    private fun getImageMaxZoomScale(): Float {
        val scale = prefs.getFloat(KEY_IMAGE_MAX_ZOOM, DEFAULT_IMAGE_MAX_ZOOM)
        // 繁體中文註解：避免異常設定值導致縮放失效
        return scale.coerceAtLeast(1.0f)
    }

    private fun showNextImage() {
        if (displayImageUrls.isEmpty()) return
        val next = binding.viewPager.currentItem + 1
        if (next < displayImageUrls.size) {
            binding.viewPager.setCurrentItem(next, true)
        }
    }

    private fun showPreviousImage() {
        if (displayImageUrls.isEmpty()) return
        val previous = binding.viewPager.currentItem - 1
        if (previous >= 0) {
            binding.viewPager.setCurrentItem(previous, true)
        }
    }

    private fun setImageAsWallpaper(imageUrl: String) {
        val wallpaperManager = WallpaperManager.getInstance(this)
        // 繁體中文註解：使用 Glide 取得 Bitmap，完成後設定為桌布
        Glide.with(this)
            .asBitmap()
            .load(imageUrl)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    try {
                        wallpaperManager.setBitmap(resource)
                        Toast.makeText(
                            this@ImagePreviewActivity,
                            R.string.msg_set_wallpaper_success,
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@ImagePreviewActivity,
                            R.string.msg_set_wallpaper_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // 繁體中文註解：載入被清除時不需額外處理
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    // 繁體中文註解：圖片載入失敗時提示使用者
                    Toast.makeText(
                        this@ImagePreviewActivity,
                        R.string.msg_set_wallpaper_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun shareImage(imageUrl: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 繁體中文註解：先下載成暫存檔再分享給其他應用程式
                val file: File = Glide.with(this@ImagePreviewActivity)
                    .asFile()
                    .load(imageUrl)
                    .submit()
                    .get()

                val uri: Uri = FileProvider.getUriForFile(
                    this@ImagePreviewActivity,
                    "${applicationContext.packageName}.fileprovider",
                    file
                )

                withContext(Dispatchers.Main) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share_image)))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ImagePreviewActivity,
                        R.string.msg_share_image_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun downloadImage(imageUrl: String) {
        if (needsLegacyStoragePermission() && !hasLegacyStoragePermission()) {
            // 繁體中文註解：Android 10 以下需先取得儲存權限
            pendingDownloadUrl = imageUrl
            requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        lifecycleScope.launch {
            val resultPath = withContext(Dispatchers.IO) {
                try {
                    // 繁體中文註解：預覽畫面下載使用固定資料夾名稱
                    val relativePath = ImageDownloadUtils.getDownloadRelativePath(
                        this@ImagePreviewActivity,
                        "preview"
                    )
                    val extension = extractImageExtension(imageUrl)
                    val fileName = "image_" + System.currentTimeMillis() + "." + extension
                    val mimeType = ImageDownloadUtils.getMimeTypeFromExtension(extension)
                    val tempFile = Glide.with(this@ImagePreviewActivity)
                        .asFile()
                        .load(imageUrl)
                        .submit()
                        .get()
                    // 繁體中文註解：透過 MediaStore 寫入公開相簿
                    val uri = ImageDownloadUtils.saveImageToMediaStore(
                        this@ImagePreviewActivity,
                        tempFile,
                        fileName,
                        relativePath,
                        mimeType
                    )
                    if (uri == null) null else relativePath
                } catch (e: Exception) {
                    null
                }
            }

            if (resultPath == null) {
                Toast.makeText(this@ImagePreviewActivity, R.string.msg_download_image_failed, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this@ImagePreviewActivity,
                    getString(R.string.msg_download_image_success, resultPath),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun extractImageExtension(imageUrl: String): String {
        val lastSegment = Uri.parse(imageUrl).lastPathSegment ?: ""
        val extension = lastSegment.substringAfterLast('.', "")
        return if (extension.isBlank()) "jpg" else extension
    }

    private fun getRealPosition(position: Int): Int {
        if (!isLoopEnabled) return position
        val lastIndex = imageUrls.size - 1
        return when (position) {
            0 -> lastIndex
            displayImageUrls.size - 1 -> 0
            else -> position - 1
        }
    }

    private fun needsLegacyStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    }

    private fun hasLegacyStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
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
        private const val KEY_IMAGE_MAX_ZOOM = "image_max_zoom_scale"
        private const val DEFAULT_SLIDESHOW_INTERVAL_SECONDS = 3
        private const val DEFAULT_IMAGE_MAX_ZOOM = 4.0f
        private val SLIDESHOW_INTERVAL_LABELS = arrayOf("關閉", "1 秒", "2 秒", "3 秒", "5 秒", "8 秒", "10 秒")
        private val SLIDESHOW_INTERVAL_VALUES = intArrayOf(0, 1, 2, 3, 5, 8, 10)
    }
}
