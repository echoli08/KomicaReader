package com.komica.reader

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.komica.reader.adapter.GalleryAdapter
import com.komica.reader.viewmodel.GalleryViewModel
import com.komica.reader.viewmodel.GalleryViewModelFactory

class GalleryActivity : AppCompatActivity() {
    private lateinit var viewModel: GalleryViewModel
    private lateinit var adapter: GalleryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        val threadUrl = intent.getStringExtra("thread_url")
        if (threadUrl == null) {
            finish()
            return
        }
        val threadTitle = intent.getStringExtra("thread_title") ?: "圖片牆"

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        findViewById<TextView>(R.id.toolbarTitle).text = threadTitle

        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val emptyView = findViewById<TextView>(R.id.emptyView)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)

        val factory = GalleryViewModelFactory(application, threadUrl)
        viewModel = ViewModelProvider(this, factory)[GalleryViewModel::class.java]

        // 3 columns grid
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        
        adapter = GalleryAdapter { position ->
            // On image click, open ImagePreviewActivity
            val urls = ArrayList(viewModel.allImageUrls)
            if (urls.isNotEmpty()) {
                val intent = Intent(this, ImagePreviewActivity::class.java).apply {
                    putStringArrayListExtra("imageUrls", urls)
                    putExtra("position", position)
                }
                startActivity(intent)
            }
        }
        recyclerView.adapter = adapter

        viewModel.imagePosts.observe(this) { posts ->
            adapter.submitList(posts)
            if (posts.isEmpty() && viewModel.isLoading.value == false) {
                emptyView.visibility = View.VISIBLE
            } else {
                emptyView.visibility = View.GONE
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.getItemId() == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
