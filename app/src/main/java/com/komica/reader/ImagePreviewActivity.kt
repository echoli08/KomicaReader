package com.komica.reader

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.komica.reader.adapter.ImagePagerAdapter
import com.komica.reader.databinding.ActivityImagePreviewBinding

class ImagePreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImagePreviewBinding
    private var imageUrls: List<String> = emptyList()
    private var currentPosition: Int = 0
    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImagePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageUrls = intent.getStringArrayListExtra("imageUrls") ?: emptyList()
        currentPosition = intent.getIntExtra("position", 0)

        if (imageUrls.isNotEmpty()) {
            binding.viewPager.adapter = ImagePagerAdapter(imageUrls)
            binding.viewPager.setCurrentItem(currentPosition, false)
            updateCounter()

            pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    updateCounter()
                }
            }
            binding.viewPager.registerOnPageChangeCallback(pageChangeCallback!!)
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        // 繁體中文註解：解除註冊避免 ViewPager2 回呼洩漏
        pageChangeCallback?.let { binding.viewPager.unregisterOnPageChangeCallback(it) }
        super.onDestroy()
    }

    private fun updateCounter() {
        val current = binding.viewPager.currentItem + 1
        val total = imageUrls.size
        binding.imageCounter.text = "$current / $total"
    }
}
