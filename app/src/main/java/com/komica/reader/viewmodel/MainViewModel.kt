package com.komica.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.komica.reader.model.Board
import com.komica.reader.model.BoardCategory
import com.komica.reader.model.Resource
import com.komica.reader.repository.KomicaRepository
import com.komica.reader.util.FavoritesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val repository: KomicaRepository,
    private val favoritesManager: FavoritesManager
) : AndroidViewModel(application) {
    
    private val _originalCategories = MutableLiveData<List<BoardCategory>>()
    private val _displayCategories = MediatorLiveData<List<BoardCategory>>()
    val categories: LiveData<List<BoardCategory>> = _displayCategories

    private val _isLoading = MutableLiveData(false)
    @get:JvmName("getIsLoading")
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        _displayCategories.addSource(_originalCategories) { updateDisplayCategories() }
    }

    @JvmOverloads
    fun loadBoards(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                when (val result = repository.fetchBoards(forceRefresh)) {
                    is Resource.Success -> {
                        if (result.data.isNotEmpty()) {
                            _originalCategories.value = result.data
                        } else {
                            _errorMessage.value = "載入板塊失敗: 資料為空"
                        }
                    }
                    is Resource.Error -> {
                        _errorMessage.value = "載入板塊失敗: ${result.exception.message}"
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _errorMessage.value = "發生錯誤: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleFavorite(board: Board) {
        favoritesManager.toggleFavorite(board.url)
        updateDisplayCategories()
    }

    fun refreshFavorites() {
        updateDisplayCategories()
    }

    private fun updateDisplayCategories() {
        val original = _originalCategories.value ?: return

        val updated = mutableListOf<BoardCategory>()
        
        val favoriteBoards = original.flatMap { it.boards }
            .filter { favoritesManager.isFavorite(it.url) }
            .distinctBy { it.url }

        if (favoriteBoards.isNotEmpty()) {
            val favCategory = BoardCategory("我的最愛", favoriteBoards).apply {
                isExpanded = true
            }
            updated.add(favCategory)
        }

        updated.addAll(original)
        _displayCategories.value = updated
    }
}