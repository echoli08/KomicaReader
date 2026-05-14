package com.komica.reader

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.komica.reader.adapter.ThreadListAdapter
import com.komica.reader.data.AppSettings
import com.komica.reader.databinding.ActivityBrowseBinding
import com.komica.reader.model.Board
import com.komica.reader.model.UiState
import com.komica.reader.util.GetSerializableCompat
import com.komica.reader.util.WindowInsetsUtil
import com.komica.reader.viewmodel.BrowseViewModel

class BrowseActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBrowseBinding
    private val viewModel: BrowseViewModel by viewModels()
    private lateinit var adapter: ThreadListAdapter
    private var board: Board? = null
    private var hasLoadedOnce = false
    private var pendingAnchorUrl: String? = null
    private var pendingAnchorOffset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings(this).ApplyTheme()
        super.onCreate(savedInstanceState)
        binding = ActivityBrowseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsUtil.ApplyBrandStatusBar(window)
        WindowInsetsUtil.ApplyToolbarInsets(binding.toolbar)

        board = intent.GetSerializableCompat(ExtraBoard)
        if (board == null) {
            finish()
            return
        }

        adapter = ThreadListAdapter { thread ->
            startActivity(Intent(this, ThreadDetailActivity::class.java).putExtra(ThreadDetailActivity.ExtraThread, thread))
        }

        binding.toolbar.title = board?.name ?: getString(R.string.title_browse)
        binding.threadRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.threadRecyclerView.adapter = adapter
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.kr_primary)
        binding.swipeRefreshLayout.setOnRefreshListener {
            board?.let { viewModel.LoadThreads(it) }
        }
        SetupScrollListener()
        ObserveState()
    }

    override fun onResume() {
        super.onResume()
        if (!hasLoadedOnce) board?.let {
            viewModel.LoadThreads(it)
            hasLoadedOnce = true
        }
    }

    private fun SetupScrollListener() {
        val layoutManager = binding.threadRecyclerView.layoutManager as LinearLayoutManager
        binding.threadRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - LoadMoreThreshold) {
                    SaveScrollAnchor(layoutManager)
                    viewModel.LoadMore()
                }
            }
        })
    }

    private fun ObserveState() {
        viewModel.state.observe(this) { state ->
            binding.progressBar.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
            binding.swipeRefreshLayout.isRefreshing = state is UiState.Loading && hasLoadedOnce
            binding.messageText.visibility = if (state is UiState.Empty || state is UiState.Error) View.VISIBLE else View.GONE
            binding.threadRecyclerView.visibility = if (state is UiState.Content) View.VISIBLE else View.GONE

            when (state) {
                is UiState.Content -> {
                    adapter.SubmitThreads(state.data)
                    RestoreScrollAnchor()
                }
                is UiState.Empty -> binding.messageText.text = state.message
                is UiState.Error -> {
                    binding.messageText.text = "${state.message}\n\n點擊重試"
                    binding.messageText.setOnClickListener { board?.let { viewModel.LoadThreads(it) } }
                }
                UiState.Loading -> Unit
            }
        }

        viewModel.isLoadingMore.observe(this) { isLoadingMore ->
            if (isLoadingMore) {
                binding.messageText.visibility = View.VISIBLE
                binding.messageText.text = "正在載入更多討論串..."
            } else if (viewModel.state.value is UiState.Content) {
                binding.messageText.visibility = View.GONE
            }
        }
    }

    companion object {
        const val ExtraBoard = "extra_board"
        private const val LoadMoreThreshold = 5
    }

    private fun SaveScrollAnchor(layoutManager: LinearLayoutManager) {
        if (pendingAnchorUrl != null) return
        val position = layoutManager.findFirstVisibleItemPosition()
        if (position == RecyclerView.NO_POSITION) return
        val view = layoutManager.findViewByPosition(position) ?: return
        pendingAnchorUrl = adapter.GetThreadAt(position)?.url
        pendingAnchorOffset = view.top - binding.threadRecyclerView.paddingTop
    }

    private fun RestoreScrollAnchor() {
        val url = pendingAnchorUrl ?: return
        pendingAnchorUrl = null
        val position = adapter.FindPositionByUrl(url)
        if (position < 0) return
        (binding.threadRecyclerView.layoutManager as? LinearLayoutManager)
            ?.scrollToPositionWithOffset(position, pendingAnchorOffset + binding.threadRecyclerView.paddingTop)
    }
}
