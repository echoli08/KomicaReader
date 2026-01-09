package com.komica.reader

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.komica.reader.adapter.BoardCategoryAdapter
import com.komica.reader.databinding.ActivityMainBinding
import com.komica.reader.model.Board
import com.komica.reader.model.BoardCategory
import com.komica.reader.util.FavoritesManager
import com.komica.reader.util.KLog
import com.komica.reader.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var categoryAdapter: BoardCategoryAdapter
    private var categories: MutableList<BoardCategory> = ArrayList()
    private lateinit var viewModel: MainViewModel
    
    @Inject
    lateinit var favoritesManager: FavoritesManager
    
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("KomicaReader", MODE_PRIVATE)
        val isNightMode = prefs.getBoolean("night_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isNightMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayShowTitleEnabled(false)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_settings)
        }

        // favoritesManager injected by Hilt
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadBoards(true)
        }

        categoryAdapter = BoardCategoryAdapter(
            categories,
            { board: Board ->
                val intent = Intent(this@MainActivity, ThreadListActivity::class.java)
                intent.putExtra("board", board)
                startActivity(intent)
            },
            { board: Board ->
                viewModel.toggleFavorite(board)
            }
        )

        binding.recyclerView.adapter = categoryAdapter

        observeViewModel()

        if (savedInstanceState == null) {
            viewModel.loadBoards()
        } else {
            KLog.d("MainActivity recreated, data should be in ViewModel")
        }
    }

    private fun observeViewModel() {
        viewModel.categories.observe(this) { newCategories ->
            if (!newCategories.isNullOrEmpty()) {
                categories.clear()
                categories.addAll(newCategories)
                categoryAdapter.notifyDataSetChanged()
            }
            binding.swipeRefreshLayout.isRefreshing = false
        }

        viewModel.isLoading.observe(this) { isLoading ->
            val hasData = categories.isNotEmpty()
            if (!binding.swipeRefreshLayout.isRefreshing) {
                binding.progressBar.visibility = if (isLoading && !hasData) View.VISIBLE else View.GONE
            }
        }

        viewModel.errorMessage.observe(this) { error ->
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshFavorites()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val item = menu.findItem(R.id.action_theme_toggle)
        val isNightMode = prefs.getBoolean("night_mode", false)
        item.setIcon(if (isNightMode) R.drawable.ic_mode_day else R.drawable.ic_mode_night)
        item.title = if (isNightMode) getString(R.string.action_switch_day_mode) else getString(R.string.action_switch_night_mode)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_theme_toggle -> {
                val isNightMode = prefs.getBoolean("night_mode", false)
                prefs.edit().putBoolean("night_mode", !isNightMode).apply()
                recreate()
                true
            }
            R.id.action_history -> {
                val intent = Intent(this, HistoryActivity::class.java)
                startActivity(intent)
                true
            }
            android.R.id.home -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}