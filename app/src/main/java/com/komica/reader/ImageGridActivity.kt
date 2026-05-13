package com.komica.reader

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.komica.reader.adapter.ImageGridAdapter
import com.komica.reader.databinding.ActivityImageGridBinding
import com.komica.reader.util.WindowInsetsUtil

class ImageGridActivity : AppCompatActivity() {
    private lateinit var binding: ActivityImageGridBinding
    private var imageUrls: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageGridBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsUtil.ApplyToolbarInsets(binding.toolbar)

        imageUrls = intent.getStringArrayListExtra(ImagePreviewActivity.ExtraImageUrls).orEmpty()
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = "圖片牆 (${imageUrls.size})"
        binding.imageGrid.layoutManager = GridLayoutManager(this, 3)
        binding.imageGrid.adapter = ImageGridAdapter(imageUrls) { position ->
            startActivity(Intent(this, ImagePreviewActivity::class.java).apply {
                putStringArrayListExtra(ImagePreviewActivity.ExtraImageUrls, ArrayList(imageUrls))
                putExtra(ImagePreviewActivity.ExtraPosition, position)
            })
        }
    }
}
