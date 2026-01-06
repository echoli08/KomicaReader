package com.komica.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.komica.reader.R
import com.komica.reader.model.Thread
import com.komica.reader.repository.HistoryRepository
import com.komica.reader.repository.KomicaRepository
import kotlinx.coroutines.launch

class ThreadDetailViewModel(application: Application, private val initialThread: Thread) : AndroidViewModel(application) {
    private val repository = KomicaRepository.getInstance(application)
    private val historyRepository = HistoryRepository.getInstance(application)
    
    private val _threadDetail = MutableLiveData<Thread>()
    val threadDetail: LiveData<Thread> = _threadDetail

    private val _isLoading = MutableLiveData(false)
    @get:JvmName("getIsLoading")
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        loadThreadDetail(false)
    }

    fun refresh() {
        loadThreadDetail(true)
    }

    private fun loadThreadDetail(forceRefresh: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.fetchThreadDetail(initialThread.url, forceRefresh)
                if (result != null) {
                    _threadDetail.value = result
                    // Save to history upon successful load
                    historyRepository.addOrUpdateHistory(
                        title = result.title ?: "Untitled",
                        url = result.url,
                        thumbUrl = result.imageUrl
                    )
                } else {
                    _errorMessage.value = getApplication<Application>().getString(R.string.error_load_thread_failed)
                }
            } catch (e: Exception) {
                _errorMessage.value = "發生錯誤: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}