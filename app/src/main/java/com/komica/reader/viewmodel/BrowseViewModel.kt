package com.komica.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.komica.reader.data.KomicaRepository
import com.komica.reader.model.Board
import com.komica.reader.model.KomicaThread
import com.komica.reader.model.UiState
import com.komica.reader.util.ErrorMessage
import kotlinx.coroutines.launch

class BrowseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = KomicaRepository()
    private val allThreads = mutableListOf<KomicaThread>()
    private val existingThreadUrls = mutableSetOf<String>()
    private var currentBoard: Board? = null
    private var currentPage = 0
    private var consecutiveEmptyPages = 0

    private val _state = MutableLiveData<UiState<List<KomicaThread>>>(UiState.Loading)
    val state: LiveData<UiState<List<KomicaThread>>> = _state

    private val _isLoadingMore = MutableLiveData(false)
    val isLoadingMore: LiveData<Boolean> = _isLoadingMore

    private val _hasMore = MutableLiveData(true)
    val hasMore: LiveData<Boolean> = _hasMore

    fun LoadThreads(board: Board) {
        currentBoard = board
        currentPage = 0
        consecutiveEmptyPages = 0
        allThreads.clear()
        existingThreadUrls.clear()
        _hasMore.value = true
        LoadPage(board, 0, isRefresh = true)
    }

    fun LoadMore() {
        val board = currentBoard ?: return
        if (_isLoadingMore.value == true || _hasMore.value == false || _state.value is UiState.Loading) return
        LoadPage(board, currentPage + 1, isRefresh = false)
    }

    private fun LoadPage(board: Board, page: Int, isRefresh: Boolean) {
        viewModelScope.launch {
            if (isRefresh) {
                _state.value = UiState.Loading
            } else {
                _isLoadingMore.value = true
            }

            runCatching { repository.LoadThreads(board, page) }
                .onSuccess { threads -> HandleLoadedPage(page, threads) }
                .onFailure { throwable ->
                    if (isRefresh && allThreads.isEmpty()) {
                        _state.value = UiState.Error(ErrorMessage.Build("載入討論串失敗", throwable), throwable)
                    } else {
                        _hasMore.value = false
                        PublishThreads()
                    }
                }

            _isLoadingMore.value = false
        }
    }

    private fun HandleLoadedPage(page: Int, threads: List<KomicaThread>) {
        if (threads.isEmpty()) {
            consecutiveEmptyPages++
            if (consecutiveEmptyPages >= MaxEmptyPages) {
                _hasMore.value = false
            }
            PublishThreads()
            return
        }

        consecutiveEmptyPages = 0
        currentPage = page
        val uniqueThreads = threads.filter { existingThreadUrls.add(it.url) }
        if (uniqueThreads.isNotEmpty()) {
            allThreads.addAll(uniqueThreads)
        }
        PublishThreads()
    }

    private fun PublishThreads() {
        val result = allThreads
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<KomicaThread>> { it.value.lastReplySortKey }
                    .thenBy { it.index }
            )
            .map { it.value }
        _state.value = if (result.isEmpty()) UiState.Empty("目前沒有討論串") else UiState.Content(result)
    }

    companion object {
        private const val MaxEmptyPages = 3
    }
}
