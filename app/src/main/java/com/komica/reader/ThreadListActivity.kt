package com.komica.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.komica.reader.adapter.ThreadAdapter
import com.komica.reader.databinding.ActivityThreadListBinding
import com.komica.reader.model.Board
import com.komica.reader.model.Thread
import com.komica.reader.util.FavoritesManager
import com.komica.reader.util.getSerializableCompat
import com.komica.reader.viewmodel.ThreadListViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ThreadListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThreadListBinding
    private lateinit var adapter: ThreadAdapter
    private val threads: MutableList<Thread> = ArrayList()
    private val viewModel: ThreadListViewModel by viewModels()
    private var currentBoard: Board? = null
    
    @Inject
    lateinit var favoritesManager: FavoritesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThreadListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // favoritesManager injected by Hilt
        
        currentBoard = intent.getSerializableCompat("board")

        if (currentBoard == null) {
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayShowTitleEnabled(false)
            setDisplayHomeAsUpEnabled(true)
        }

        binding.toolbarTitle.text = currentBoard!!.name

        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refresh()
        }

        adapter = ThreadAdapter(threads, object : ThreadAdapter.OnThreadClickListener {
            override fun onThreadClick(thread: Thread) {
                val intent = Intent(this@ThreadListActivity, ThreadDetailActivity::class.java)
                intent.putExtra("thread_url", thread.url)
                intent.putExtra("thread_title", thread.title)
                startActivity(intent)
            }

            override fun onShareClick(thread: Thread) {
                shareThread(thread)
            }
        })
        binding.recyclerView.adapter = adapter

        setupScrollListener()
        setupSearchListener()
        setupFilterButton()
        setupFavoriteListener()

        updateFavoriteIcon()
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.threads.observe(this) { newThreads ->
            adapter.updateThreads(newThreads)
            binding.swipeRefreshLayout.isRefreshing = false
        }

        viewModel.isLoading.observe(this) { isLoading ->
            if (!binding.swipeRefreshLayout.isRefreshing) {
                val isEmpty = threads.isEmpty()
                binding.progressBar.visibility = if (isLoading && isEmpty) View.VISIBLE else View.GONE
            }
        }

        viewModel.errorMessage.observe(this) { error ->
            if (error != null) {
                showErrorSnackBar(error)
            }
        }
    }

    private fun showErrorSnackBar(message: String) {
        Snackbar.make(binding.recyclerView, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.action_retry) { viewModel.refresh() }
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    private fun shareThread(thread: Thread) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            val shareText = "${thread.title}\n${thread.url}"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share_thread)))
    }

    private fun setupFavoriteListener() {
        binding.favoriteButton.setOnClickListener {
            currentBoard?.url?.let { url ->
                favoritesManager.toggleFavorite(url)
                updateFavoriteIcon()
            }
        }
    }

    private fun updateFavoriteIcon() {
        currentBoard?.url?.let { url ->
            val isFavorite = favoritesManager.isFavorite(url)
            binding.favoriteButton.setImageResource(if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_border)
        }
    }

    private fun setupFilterButton() {
        binding.filterButton.setOnClickListener { v ->
            val query = binding.searchEditText.text.toString().trim()
            if (query.isNotEmpty()) {
                viewModel.setSearchQuery(query)
                viewModel.performRemoteSearch()
            } else {
                viewModel.clearSearch()
            }
            hideKeyboard(v)
        }
    }

    private fun hideKeyboard(v: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(v.windowToken, 0)
    }

    private fun setupScrollListener() {
        val layoutManager = binding.recyclerView.layoutManager as LinearLayoutManager
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) {
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) {
                        viewModel.loadMore()
                    }
                }
            }
        })
    }

    private fun setupSearchListener() {
        binding.searchEditText.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.searchEditText.text.toString().trim()
                if (query.isNotEmpty()) {
                    viewModel.setSearchQuery(query)
                    viewModel.performRemoteSearch()
                } else {
                    viewModel.clearSearch()
                }
                hideKeyboard(v)
                true
            } else {
                false
            }
        }

        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                viewModel.setSearchQuery(query)
                if (query.isEmpty()) {
                    viewModel.clearSearch()
                }
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.thread_list_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_history -> {
                val intent = Intent(this, HistoryActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
