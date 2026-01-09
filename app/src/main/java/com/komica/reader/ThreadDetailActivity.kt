package com.komica.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.komica.reader.adapter.PostAdapter
import com.komica.reader.databinding.ActivityThreadDetailBinding
import com.komica.reader.model.Post
import com.komica.reader.model.Thread
import com.komica.reader.service.KomicaService
import com.komica.reader.util.KLog
import com.komica.reader.viewmodel.ThreadDetailViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

@AndroidEntryPoint
class ThreadDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThreadDetailBinding
    private var adapter: PostAdapter? = null
    private val viewModel: ThreadDetailViewModel by viewModels()
    private var currentThread: Thread? = null

    private val replyResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // 繁體中文註解：回覆完成後重新整理討論串
            viewModel.refresh()
            scrollToBottom()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThreadDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize currentThread from intent for initial UI state
        if (intent.hasExtra("thread")) {
            currentThread = intent.getSerializableExtra("thread") as? Thread
        } else if (intent.hasExtra("thread_url")) {
            val url = intent.getStringExtra("thread_url")
            val title = intent.getStringExtra("thread_title")
            currentThread = Thread(
                System.currentTimeMillis().toString(),
                title ?: "Loading...",
                "",
                0,
                url
            )
        }

        if (currentThread == null) {
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayShowTitleEnabled(false)
            setDisplayHomeAsUpEnabled(true)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        binding.fabScrollDown.setOnClickListener { scrollToBottom() }
        binding.fabShare.setOnClickListener { shareThread() }
        binding.fabReply.setOnClickListener { openReplyWebView() }

        binding.threadTitle.text = currentThread!!.title

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.threadDetail.observe(this) { thread ->
            if (thread != null) {
                currentThread = thread // Update local reference with full details
                binding.threadTitle.text = thread.title
                updateUI(thread)
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(this) { error ->
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.replyStatus.observe(this) { success ->
            if (success != null) {
                if (success) {
                    Toast.makeText(this, R.string.msg_reply_success, Toast.LENGTH_SHORT).show()
                    scrollToBottom()
                } else {
                    Toast.makeText(this, R.string.msg_reply_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openReplyWebView() {
        val thread = currentThread ?: return
        
        // Use the explicit reply form URL to trigger timerecord cookie generation
        var baseUrl = thread.url
        baseUrl = baseUrl.replace("(index.html?|pixmicat.php.*)$".toRegex(), "")
        if (!baseUrl.endsWith("/")) baseUrl += "/"

        // Parse thread number safely
        var res = thread.postNumber.toString()
        if (res == "0" || res == "-1") {
            // Fallback: try parse from URL if model number is missing
            val p = Pattern.compile("res=(\\d+)")
            val m = p.matcher(thread.url)
            if (m.find()) {
                res = m.group(1) ?: "0"
            }
        }

        val formUrl = baseUrl + "pixmicat.php?res=" + res
        KLog.d("Reply WebView URL: $formUrl")

        // 繁體中文註解：回覆改用 WebView 讓使用者手動送出
        val intent = ReplyWebViewActivity.createIntent(this, formUrl)
        replyResultLauncher.launch(intent)
    }

    override fun onResume() {
        super.onResume()
        adapter?.notifyDataSetChanged()
    }

    private fun updateUI(thread: Thread) {
        val posts = thread.posts
        adapter = PostAdapter(posts, object : PostAdapter.OnQuoteInteractionListener {
            override fun onImageClick(imageIndex: Int, imageUrls: List<String>) {
                val intent = Intent(this@ThreadDetailActivity, ImagePreviewActivity::class.java)
                intent.putStringArrayListExtra("imageUrls", ArrayList(imageUrls))
                intent.putExtra("position", imageIndex)
                startActivity(intent)
            }

            override fun onImageLongClick(imageUrl: String) {
                shareImage(imageUrl)
            }

            override fun onQuoteClick(position: Int) {
                binding.recyclerView.smoothScrollToPosition(position)
            }

            override fun onQuoteLongClick(post: Post?) {
                if (post != null) {
                    binding.previewTitle.text = getString(R.string.fmt_post_number, post.number, post.author ?: "")
                    binding.previewContent.text = post.content
                    binding.previewCard.visibility = View.VISIBLE
                }
            }

            override fun onQuoteReleased() {
                binding.previewCard.visibility = View.GONE
            }
        })

        binding.recyclerView.adapter = adapter
    }

    private fun scrollToBottom() {
        adapter?.let {
            val itemCount = it.itemCount
            if (itemCount > 0) {
                binding.recyclerView.smoothScrollToPosition(itemCount - 1)
            }
        }
    }

    private fun shareThread() {
        currentThread?.let { thread ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                val shareText = "${thread.title}\n\n${KomicaService.resolveUrl(thread.url, "")}"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share_thread)))
        }
    }

    private fun shareImage(imageUrl: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file: File = Glide.with(this@ThreadDetailActivity)
                    .asFile()
                    .load(imageUrl)
                    .submit()
                    .get()

                val uri: Uri = FileProvider.getUriForFile(
                    this@ThreadDetailActivity,
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
                    Toast.makeText(this@ThreadDetailActivity, R.string.msg_share_image_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.thread_detail_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_gallery -> {
                currentThread?.let { thread ->
                    val intent = Intent(this, GalleryActivity::class.java)
                    intent.putExtra("thread_url", thread.url)
                    intent.putExtra("thread_title", thread.title)
                    startActivity(intent)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
