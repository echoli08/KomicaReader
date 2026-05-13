package com.komica.reader

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import com.komica.reader.adapter.BoardListAdapter
import com.komica.reader.data.AppSettings
import com.komica.reader.data.BoardUiStore
import com.komica.reader.databinding.ActivityMainBinding
import com.komica.reader.model.UiState
import com.komica.reader.util.WindowInsetsUtil
import com.komica.reader.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: BoardListAdapter
    private lateinit var boardUiStore: BoardUiStore
    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings(this).ApplyTheme()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsUtil.ApplyBrandStatusBar(window)
        WindowInsetsUtil.ApplyToolbarInsets(binding.toolbar)
        settings = AppSettings(this)
        boardUiStore = BoardUiStore(this)

        adapter = BoardListAdapter(
            onBoardClick = { board ->
                startActivity(Intent(this, BrowseActivity::class.java).putExtra(BrowseActivity.ExtraBoard, board))
            },
            onCategoryClick = { category ->
                boardUiStore.ToggleCollapsed(category.name)
                adapter.RefreshCategoryState()
            },
            onFavoriteClick = { board -> viewModel.ToggleFavorite(board) },
            isCategoryCollapsed = { category -> boardUiStore.IsCollapsed(category.name) },
            isFavorite = { board -> viewModel.IsFavorite(board) }
        )

        binding.boardRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.boardRecyclerView.adapter = adapter
        binding.toolbar.inflateMenu(R.menu.main_toolbar_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_theme_toggle -> {
                    ToggleTheme()
                    true
                }
                R.id.action_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    true
                }
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
        ObserveState()
        viewModel.LoadBoards()
    }

    private fun ToggleTheme() {
        val isNight = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES ||
            (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM &&
                (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES)
        settings.themeMode = if (isNight) AppSettings.ThemeLight else AppSettings.ThemeDark
        settings.ApplyTheme()
    }


    private fun ObserveState() {
        viewModel.state.observe(this) { state ->
            binding.progressBar.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
            binding.messageText.visibility = if (state is UiState.Empty || state is UiState.Error) View.VISIBLE else View.GONE
            binding.boardRecyclerView.visibility = if (state is UiState.Content) View.VISIBLE else View.GONE

            when (state) {
                is UiState.Content -> adapter.SubmitCategories(state.data)
                is UiState.Empty -> binding.messageText.text = state.message
                is UiState.Error -> {
                    binding.messageText.text = "${state.message}\n\n點擊重試"
                    binding.messageText.setOnClickListener { viewModel.LoadBoards(forceRefresh = true) }
                }
                UiState.Loading -> Unit
            }
        }
    }
}

