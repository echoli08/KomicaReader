package com.komica.reader

import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.komica.reader.data.AppSettings
import com.komica.reader.data.ImageStore
import com.komica.reader.adapter.ImagePagerAdapter
import com.komica.reader.databinding.ActivityImagePreviewBinding
import com.komica.reader.util.WindowInsetsUtil
import com.komica.reader.widget.ZoomImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImagePreviewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityImagePreviewBinding
    private lateinit var settings: AppSettings
    private val slideshowHandler = Handler(Looper.getMainLooper())
    private val slideshowRunnable = Runnable { AdvanceSlideshow() }
    private var imageUrls: List<String> = emptyList()
    private var isSlideshowPlaying = false
    private var lastRightSwipeTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImagePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsUtil.ApplyTopMarginInsets(binding.imageCounter)
        settings = AppSettings(this)
        if (settings.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        imageUrls = intent.getStringArrayListExtra(ExtraImageUrls).orEmpty()
        val initialPosition = intent.getIntExtra(ExtraPosition, 0)
        if (imageUrls.isEmpty()) {
            finish()
            return
        }

        binding.viewPager.adapter = ImagePagerAdapter(
            imageUrls = imageUrls,
            maxScale = settings.imageZoomMax,
            onNavigate = { HandleImageNavigation(it) },
            onLongClick = { ShareImage(it) }
        )
        binding.viewPager.isUserInputEnabled = false
        binding.viewPager.setCurrentItem(initialPosition.coerceIn(imageUrls.indices), false)
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = UpdateCounter(position)
        })
        binding.downloadButton.setOnClickListener { DownloadImage(CurrentImageUrl()) }
        binding.shareButton.setOnClickListener { ShareImage(CurrentImageUrl()) }
        binding.slideshowButton.setOnClickListener { ToggleSlideshow() }
        UpdateCounter(initialPosition)
    }

    override fun onDestroy() {
        slideshowHandler.removeCallbacks(slideshowRunnable)
        super.onDestroy()
    }

    private fun UpdateCounter(position: Int) {
        binding.imageCounter.text = "${position + 1} / ${imageUrls.size}"
    }

    private fun ShowImageActions(imageUrl: String) {
        AlertDialog.Builder(this)
            .setTitle("圖片選項")
            .setItems(arrayOf("下載圖片", "分享圖片", "設成桌布")) { _, which ->
                when (which) {
                    0 -> DownloadImage(imageUrl)
                    1 -> ShareImage(imageUrl)
                    2 -> SetWallpaper(imageUrl)
                }
            }
            .show()
    }

    private fun DownloadImage(imageUrl: String) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching { ImageStore.DownloadImage(this@ImagePreviewActivity, imageUrl) }.getOrDefault(false)
            }
            Toast.makeText(this@ImagePreviewActivity, if (success) "圖片已下載" else "下載圖片失敗", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ShareImage(imageUrl: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val file = runCatching { Glide.with(this@ImagePreviewActivity).asFile().load(imageUrl).submit().get() }.getOrNull()
            withContext(Dispatchers.Main) {
                if (file == null) {
                    Toast.makeText(this@ImagePreviewActivity, "分享圖片失敗", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                val uri = FileProvider.getUriForFile(this@ImagePreviewActivity, "$packageName.fileprovider", File(file.absolutePath))
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "分享圖片"))
            }
        }
    }

    private fun SetWallpaper(imageUrl: String) {
        Glide.with(this).asBitmap().load(imageUrl).into(object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                runCatching { WallpaperManager.getInstance(this@ImagePreviewActivity).setBitmap(resource) }
                Toast.makeText(this@ImagePreviewActivity, "桌布已更新", Toast.LENGTH_SHORT).show()
            }
            override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) = Unit
        })
    }

    private fun CurrentImageUrl(): String = imageUrls[binding.viewPager.currentItem.coerceIn(imageUrls.indices)]

    private fun MovePage(delta: Int) {
        val next = (binding.viewPager.currentItem + delta).coerceIn(0, imageUrls.lastIndex)
        binding.viewPager.setCurrentItem(next, true)
    }

    private fun HandleImageNavigation(navigation: ZoomImageView.ImageNavigation) {
        when (navigation) {
            ZoomImageView.ImageNavigation.Next -> MovePage(1)
            ZoomImageView.ImageNavigation.Previous -> MovePage(-1)
            ZoomImageView.ImageNavigation.RightSwipePrevious -> HandleRightSwipePrevious()
            ZoomImageView.ImageNavigation.Exit -> finish()
        }
    }

    private fun HandleRightSwipePrevious() {
        val now = System.currentTimeMillis()
        if (now - lastRightSwipeTime <= DoubleRightSwipeWindowMs) {
            finish()
            return
        }
        lastRightSwipeTime = now
        MovePage(-1)
    }

    private fun ToggleSlideshow() {
        isSlideshowPlaying = !isSlideshowPlaying
        binding.slideshowButton.text = if (isSlideshowPlaying) "暫停" else "輪播"
        if (isSlideshowPlaying) {
            slideshowHandler.postDelayed(slideshowRunnable, settings.slideshowSeconds * 1000L)
        } else {
            slideshowHandler.removeCallbacks(slideshowRunnable)
        }
    }

    private fun AdvanceSlideshow() {
        if (!isSlideshowPlaying) return
        val next = if (binding.viewPager.currentItem >= imageUrls.lastIndex) 0 else binding.viewPager.currentItem + 1
        binding.viewPager.setCurrentItem(next, true)
        slideshowHandler.postDelayed(slideshowRunnable, settings.slideshowSeconds * 1000L)
    }

    companion object {
        const val ExtraImageUrls = "image_urls"
        const val ExtraPosition = "position"
        private const val DoubleRightSwipeWindowMs = 700L
    }
}
