package com.komica.reader

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.komica.reader.adapter.GalleryAdapter
import com.komica.reader.viewmodel.GalleryViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@AndroidEntryPoint
class GalleryActivity : AppCompatActivity() {
    private val viewModel: GalleryViewModel by viewModels()
    private lateinit var adapter: GalleryAdapter
    private var threadUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        threadUrl = intent.getStringExtra("thread_url")
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

        // 3 columns grid
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        
        adapter = GalleryAdapter(
            onImageClick = { position ->
                // On image click, open ImagePreviewActivity
                val urls = ArrayList(viewModel.allImageUrls)
                if (urls.isNotEmpty()) {
                    val intent = Intent(this, ImagePreviewActivity::class.java).apply {
                        putStringArrayListExtra("imageUrls", urls)
                        putExtra("position", position)
                    }
                    startActivity(intent)
                }
            },
            onImageLongClick = { imageUrl ->
                showImageActionDialog(imageUrl)
            }
        )
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

    private fun showImageActionDialog(imageUrl: String) {
        val options = arrayOf(
            getString(R.string.action_download_image),
            getString(R.string.action_share_image)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_image_action_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> downloadImage(imageUrl)
                    1 -> shareImage(imageUrl)
                }
            }
            .show()
    }

    private fun shareImage(imageUrl: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file: File = Glide.with(this@GalleryActivity)
                    .asFile()
                    .load(imageUrl)
                    .submit()
                    .get()

                val uri: Uri = FileProvider.getUriForFile(
                    this@GalleryActivity,
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
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GalleryActivity, R.string.msg_share_image_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun downloadImage(imageUrl: String) {
        lifecycleScope.launch {
            val resultPath = withContext(Dispatchers.IO) {
                try {
                    val targetDir = getDownloadDir()
                    val extension = extractImageExtension(imageUrl)
                    val fileName = "image_" + System.currentTimeMillis() + "." + extension
                    val destFile = File(targetDir, fileName)
                    val tempFile = Glide.with(this@GalleryActivity)
                        .asFile()
                        .load(imageUrl)
                        .submit()
                        .get()
                    tempFile.copyTo(destFile, overwrite = false)
                    destFile.absolutePath
                } catch (e: Exception) {
                    null
                }
            }

            if (resultPath == null) {
                Toast.makeText(this@GalleryActivity, R.string.msg_download_image_failed, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this@GalleryActivity,
                    getString(R.string.msg_download_image_success, resultPath),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getDownloadDir(): File {
        // 繁體中文註解：依設定選擇下載路徑，外部儲存空間不可用時改用內部資料夾
        val baseDir = getDownloadBaseDir() ?: filesDir
        val folderName = getThreadFolderId(threadUrl) ?: "gallery"
        val targetDir = File(baseDir, "KomicaReader/$folderName")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        return targetDir
    }

    private fun getDownloadBaseDir(): File? {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val pathType = prefs.getString(KEY_IMAGE_DOWNLOAD_PATH, DOWNLOAD_PATH_PICTURES)
            ?: DOWNLOAD_PATH_PICTURES
        val directory = if (pathType == DOWNLOAD_PATH_DOWNLOADS) {
            Environment.DIRECTORY_DOWNLOADS
        } else {
            Environment.DIRECTORY_PICTURES
        }
        return getExternalFilesDir(directory)
    }

    private fun getThreadFolderId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val match = Regex("res=(\\d+)").find(url)
        return match?.groupValues?.getOrNull(1)?.let { "thread_$it" }
    }

    private fun extractImageExtension(imageUrl: String): String {
        val lastSegment = Uri.parse(imageUrl).lastPathSegment ?: ""
        val extension = lastSegment.substringAfterLast('.', "")
        return if (extension.isBlank()) "jpg" else extension
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.getItemId() == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val PREFS_NAME = "KomicaReader"
        private const val KEY_IMAGE_DOWNLOAD_PATH = "image_download_path"
        private const val DOWNLOAD_PATH_PICTURES = "pictures"
        private const val DOWNLOAD_PATH_DOWNLOADS = "downloads"
    }
}
