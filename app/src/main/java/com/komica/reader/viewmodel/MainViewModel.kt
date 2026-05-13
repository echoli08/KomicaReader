package com.komica.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.komica.reader.data.FavoritesStore
import com.komica.reader.data.KomicaRepository
import com.komica.reader.model.Board
import com.komica.reader.model.BoardCategory
import com.komica.reader.model.UiState
import com.komica.reader.util.ErrorMessage
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = KomicaRepository()
    private val favoritesStore = FavoritesStore(application)
    private val originalCategories = mutableListOf<BoardCategory>()
    private var query = ""

    private val _state = MutableLiveData<UiState<List<BoardCategory>>>(UiState.Loading)
    val state: LiveData<UiState<List<BoardCategory>>> = _state

    fun LoadBoards(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching { repository.LoadBoards(forceRefresh) }
                .onSuccess { categories ->
                    originalCategories.clear()
                    originalCategories.addAll(categories)
                    PublishCategories()
                }
                .onFailure { _state.value = UiState.Error(ErrorMessage.Build("載入看板失敗", it), it) }
        }
    }

    fun SetQuery(value: String) {
        query = value.trim()
        PublishCategories()
    }

    fun ToggleFavorite(board: Board) {
        favoritesStore.Toggle(board.url)
        PublishCategories()
    }

    fun IsFavorite(board: Board): Boolean {
        return favoritesStore.IsFavorite(board.url)
    }

    private fun PublishCategories() {
        val filtered = originalCategories.mapNotNull { category ->
            val boards = category.boards.filter { board ->
                query.isBlank() || board.name.contains(query, ignoreCase = true) ||
                    board.url.contains(query, ignoreCase = true) ||
                    board.categoryName.contains(query, ignoreCase = true)
            }
            if (boards.isEmpty()) null else category.copy(boards = boards)
        }

        val favorites = originalCategories
            .flatMap { it.boards }
            .filter { favoritesStore.IsFavorite(it.url) }
            .distinctBy { it.url }

        val result = buildList {
            if (favorites.isNotEmpty()) add(BoardCategory("我的最愛", favorites))
            addAll(filtered)
        }

        _state.value = if (result.isEmpty()) UiState.Empty("目前沒有看板資料") else UiState.Content(result)
    }
}
