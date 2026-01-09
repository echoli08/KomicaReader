package com.komica.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.komica.reader.R
import com.komica.reader.model.Board
import com.komica.reader.model.Resource
import com.komica.reader.model.Thread
import com.komica.reader.repository.KomicaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThreadListViewModel @Inject constructor(
    application: Application,
    private val repository: KomicaRepository,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val board: Board = savedStateHandle.get<Board>("board") 
        ?: throw IllegalArgumentException("Board is required")
    
    private val _displayThreads = MutableLiveData<List<Thread>>(emptyList())
    val threads: LiveData<List<Thread>> = _displayThreads

    private val allThreads = mutableListOf<Thread>()
    private val existingThreadUrls = mutableSetOf<String>()
    
    private val _isLoading = MutableLiveData(false)
    @get:JvmName("getIsLoading")
    val isLoading: LiveData<Boolean> = _isLoading

    private val _hasMore = MutableLiveData(true)
    @get:JvmName("getHasMore")
    val hasMore: LiveData<Boolean> = _hasMore

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    private var currentPage = 0
    private var consecutiveEmptyPages = 0
    private var currentSearchQuery = ""
    private var _isRemoteSearchMode = false
    val isRemoteSearchMode: Boolean get() = _isRemoteSearchMode

    init {
        loadThreads(0)
    }

    fun loadMore() {
        if (_isLoading.value == true || _hasMore.value == false || _isRemoteSearchMode) return
        loadThreads(currentPage + 1)
    }

    fun refresh() {
        if (_isLoading.value == true) return
        currentPage = 0
        consecutiveEmptyPages = 0
        allThreads.clear()
        existingThreadUrls.clear()
        _hasMore.value = true
        _isRemoteSearchMode = false
        loadThreads(0)
    }

    private fun loadThreads(page: Int) {
        if (_isLoading.value == true) return
        
        viewModelScope.launch {
            _isLoading.value = true
            
            val result = repository.fetchThreads(board.url, page)
            _isLoading.value = false
            
            when (result) {
                is Resource.Success -> {
                    val newThreads = result.data
                    if (newThreads.isEmpty()) {
                        consecutiveEmptyPages++
                        if (page == 0) {
                            _errorMessage.value = getApplication<Application>().getString(R.string.error_fetch_threads)
                        }
                        
                        if (consecutiveEmptyPages >= 3) {
                            _hasMore.value = false
                        } else {
                            // 自動嘗試下一頁
                            delay(500)
                            loadMore()
                        }
                    } else {
                        consecutiveEmptyPages = 0
                        currentPage = page
                        
                        val uniqueThreads = newThreads.filter { !existingThreadUrls.contains(it.url) }
                        uniqueThreads.forEach { existingThreadUrls.add(it.url) }
                        
                        if (uniqueThreads.isNotEmpty()) {
                            allThreads.addAll(uniqueThreads)
                            if (!_isRemoteSearchMode) {
                                applyFiltersAndSort()
                            }
                        }
                    }
                }
                is Resource.Error -> {
                    _errorMessage.value = result.message ?: "載入討論串失敗"
                }
                else -> {}
            }
            
            if (page == 0 && (_displayThreads.value?.isEmpty() == true)) {
                applyFiltersAndSort()
            }
        }
    }

    fun setSearchQuery(query: String) {
        currentSearchQuery = query.lowercase().trim()
        if (currentSearchQuery.isEmpty()) {
            _isRemoteSearchMode = false
            _hasMore.value = true
            applyFiltersAndSort()
        } else {
            applyFiltersAndSort()
        }
    }

    fun performRemoteSearch() {
        if (currentSearchQuery.isEmpty()) return
        
        viewModelScope.launch {
            _isLoading.value = true
            _isRemoteSearchMode = true
            _hasMore.value = false
            
            val result = repository.searchThreads(board.url, currentSearchQuery)
            _isLoading.value = false
            
            when (result) {
                is Resource.Success -> {
                    val results = result.data
                    if (results.isNotEmpty()) {
                        _displayThreads.value = results
                    } else {
                        _displayThreads.value = emptyList()
                        _errorMessage.value = getApplication<Application>().getString(R.string.error_no_search_results)
                    }
                }
                is Resource.Error -> {
                    _displayThreads.value = emptyList()
                    _errorMessage.value = result.message ?: "搜尋失敗"
                }
                else -> {}
            }
        }
    }

    fun clearSearch() {
        currentSearchQuery = ""
        _isRemoteSearchMode = false
        _hasMore.value = true
        applyFiltersAndSort()
    }

    private fun applyFiltersAndSort() {
        val filtered = if (currentSearchQuery.isEmpty()) {
            allThreads.toList()
        } else {
            allThreads.filter {
                it.title?.lowercase()?.contains(currentSearchQuery) == true ||
                it.contentPreview?.lowercase()?.contains(currentSearchQuery) == true
            }
        }

        _displayThreads.value = filtered
    }
}
