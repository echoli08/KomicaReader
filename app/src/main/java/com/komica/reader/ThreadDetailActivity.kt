package com.komica.reader

import android.content.Intent
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.komica.reader.adapter.PostAdapter
import com.komica.reader.data.AppSettings
import com.komica.reader.data.HistoryStore
import com.komica.reader.data.ImageStore
import com.komica.reader.databinding.ActivityThreadDetailBinding
import com.komica.reader.model.ThreadDetail
import com.komica.reader.model.KomicaThread
import com.komica.reader.model.UiState
import com.komica.reader.util.GetSerializableCompat
import com.komica.reader.util.WindowInsetsUtil
import com.komica.reader.viewmodel.ThreadDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ThreadDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityThreadDetailBinding
    private val viewModel: ThreadDetailViewModel by viewModels()
    private lateinit var adapter: PostAdapter
    private var thread: KomicaThread? = null
    private var detail: ThreadDetail? = null
    private var hasLoadedOnce = false
    private val replyLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            thread?.let { viewModel.LoadThread(it.url) }
            ScrollToBottom()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings(this).ApplyTheme()
        super.onCreate(savedInstanceState)
        binding = ActivityThreadDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsUtil.ApplyBrandStatusBar(window)
        WindowInsetsUtil.ApplyToolbarInsets(binding.toolbar)

        thread = intent.GetSerializableCompat(ExtraThread)
        if (thread == null) {
            finish()
            return
        }

        adapter = PostAdapter(
            postFontSizeSp = AppSettings(this).postFontSizeSp,
            onQuoteClick = { position -> ScrollToPost(position) },
            onQuotePreviewShow = { post, anchor, x, y -> ShowPostPreview(post.number, post.author, post.content, anchor, x, y) },
            onQuotePreviewHide = { HidePostPreview() },
            onImageClick = { position, urls ->
                startActivity(Intent(this, ImagePreviewActivity::class.java).apply {
                    putStringArrayListExtra(ImagePreviewActivity.ExtraImageUrls, ArrayList(urls))
                    putExtra(ImagePreviewActivity.ExtraPosition, position)
                })
            },
            onImageLongClick = { imageUrl ->
                startActivity(Intent(this, ImagePreviewActivity::class.java).apply {
                    putStringArrayListExtra(ImagePreviewActivity.ExtraImageUrls, arrayListOf(imageUrl))
                    putExtra(ImagePreviewActivity.ExtraPosition, 0)
                })
            }
        )

        binding.toolbar.title = thread?.title ?: "討論串"
        binding.toolbar.inflateMenu(R.menu.thread_detail_toolbar_menu)
        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_image_wall -> {
                    OpenImageWall()
                    true
                }
                R.id.action_download_all -> {
                    DownloadAllImages()
                    true
                }
                else -> false
            }
        }
        binding.fabReply.setOnClickListener { OpenReplyPage() }
        binding.fabShare.setOnClickListener { ShareThread() }
        binding.fabScrollDown.setOnClickListener { ScrollToBottom() }
        binding.postRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.postRecyclerView.adapter = adapter
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.kr_primary)
        binding.swipeRefreshLayout.setOnRefreshListener {
            thread?.let { viewModel.LoadThread(it.url) }
        }

        ObserveState()
    }

    override fun onResume() {
        super.onResume()
        thread?.let {
            viewModel.LoadThread(it.url)
            hasLoadedOnce = true
        }
    }

    private fun ObserveState() {
        viewModel.state.observe(this) { state ->
            binding.progressBar.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
            binding.swipeRefreshLayout.isRefreshing = state is UiState.Loading && hasLoadedOnce
            binding.messageText.visibility = if (state is UiState.Empty || state is UiState.Error) View.VISIBLE else View.GONE
            binding.postRecyclerView.visibility = if (state is UiState.Content) View.VISIBLE else View.GONE

            when (state) {
                is UiState.Content -> {
                    detail = state.data
                    binding.toolbar.title = state.data.title
                    adapter.SubmitPosts(state.data.posts)
                    thread?.let { HistoryStore(this).Add(it.copy(title = state.data.title)) }
                }
                is UiState.Empty -> binding.messageText.text = state.message
                is UiState.Error -> {
                    binding.messageText.text = "${state.message}\n\n點擊重試"
                    binding.messageText.setOnClickListener { thread?.let { viewModel.LoadThread(it.url) } }
                }
                UiState.Loading -> Unit
            }
        }
    }

    private fun ScrollToPost(position: Int) {
        binding.postRecyclerView.scrollToPosition(position)
        binding.postRecyclerView.post {
            binding.postRecyclerView.findViewHolderForAdapterPosition(position)?.itemView?.let { view ->
                val original = view.background
                view.setBackgroundColor(ContextCompat.getColor(this, R.color.kr_surface_alt))
                view.postDelayed({ view.background = original }, 900L)
            }
        }
    }

    private fun ScrollToBottom() {
        val count = adapter.itemCount
        if (count > 0) binding.postRecyclerView.smoothScrollToPosition(count - 1)
    }

    private fun ShowPostPreview(number: Int, author: String, content: String, anchor: View, touchX: Float, touchY: Float) {
        binding.previewTitle.text = "No.$number $author"
        binding.previewContent.text = content.ifBlank { "(無文字內容)" }
        binding.previewCard.visibility = View.VISIBLE
        binding.previewCard.post {
            val rootLocation = IntArray(2)
            val anchorLocation = IntArray(2)
            binding.root.getLocationOnScreen(rootLocation)
            anchor.getLocationOnScreen(anchorLocation)

            val pointerX = anchorLocation[0] - rootLocation[0] + touchX
            val pointerY = anchorLocation[1] - rootLocation[1] + touchY
            val margin = resources.displayMetrics.density * 12f
            val previewWidth = binding.previewCard.width.toFloat()
            val previewHeight = binding.previewCard.height.toFloat()
            val maxX = binding.root.width - previewWidth - margin
            val targetX = (pointerX - previewWidth / 2f).coerceIn(margin, maxX.coerceAtLeast(margin))
            val targetY = (pointerY - previewHeight - margin).coerceAtLeast(margin)

            binding.previewCard.x = targetX
            binding.previewCard.y = targetY
        }
    }

    private fun HidePostPreview() {
        binding.previewCard.visibility = View.GONE
    }

    private fun CurrentImageUrls(): List<String> {
        return detail?.posts.orEmpty().mapNotNull { it.imageUrl.ifBlank { it.thumbnailUrl }.takeIf(String::isNotBlank) }
    }

    private fun OpenImageWall() {
        val urls = CurrentImageUrls()
        if (urls.isEmpty()) {
            Toast.makeText(this, "此討論串沒有圖片", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, ImageGridActivity::class.java).apply {
            putStringArrayListExtra(ImagePreviewActivity.ExtraImageUrls, ArrayList(urls))
        })
    }

    private fun DownloadAllImages() {
        val urls = CurrentImageUrls()
        if (urls.isEmpty()) {
            Toast.makeText(this, "此討論串沒有圖片", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            Toast.makeText(this@ThreadDetailActivity, "開始下載 ${urls.size} 張圖片", Toast.LENGTH_SHORT).show()
            val successCount = withContext(Dispatchers.IO) {
                urls.count { url -> runCatching { ImageStore.DownloadImage(this@ThreadDetailActivity, url) }.getOrDefault(false) }
            }
            Toast.makeText(this@ThreadDetailActivity, "已下載 $successCount / ${urls.size} 張圖片", Toast.LENGTH_LONG).show()
        }
    }

    private fun ShareThread() {
        val currentThread = thread ?: return
        val title = detail?.title ?: currentThread.title
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n${currentThread.url}")
        }
        startActivity(Intent.createChooser(intent, "分享討論串"))
    }

    private fun OpenReplyPage() {
        val currentThread = thread ?: return
        replyLauncher.launch(Intent(this, ReplyActivity::class.java).apply {
            putExtra(ReplyActivity.ExtraUrl, currentThread.url)
            putExtra(ReplyActivity.ExtraTitle, "回覆：${detail?.title ?: currentThread.title}")
        })
    }

    companion object {
        const val ExtraThread = "extra_thread"
    }
}
