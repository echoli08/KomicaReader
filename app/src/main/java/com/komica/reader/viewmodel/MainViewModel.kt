package com.komica.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.komica.reader.model.Board
import com.komica.reader.model.BoardCategory
import com.komica.reader.repository.KomicaRepository
import com.komica.reader.util.FavoritesManager
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = KomicaRepository.getInstance(application)
    private val favoritesManager = FavoritesManager.getInstance(application)
    
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
                val result = repository.fetchBoards(forceRefresh)
                if (result.isNotEmpty()) {
                    _originalCategories.value = result
                } else {
                    _errorMessage.value = "載入板塊失敗"
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
        
        // 取得所有最愛看板並去重
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
