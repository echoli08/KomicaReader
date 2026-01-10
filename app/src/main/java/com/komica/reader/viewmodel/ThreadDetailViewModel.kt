package com.komica.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.komica.reader.R
import com.komica.reader.model.Resource
import com.komica.reader.model.Thread
import com.komica.reader.repository.HistoryRepository
import com.komica.reader.repository.KomicaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThreadDetailViewModel @Inject constructor(
    application: Application,
    private val repository: KomicaRepository,
    private val historyRepository: HistoryRepository,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    
    private val initialThread: Thread

    private val _threadDetail = MutableLiveData<Thread>()
    val threadDetail: LiveData<Thread> = _threadDetail

    private val _isLoading = MutableLiveData(false)
    @get:JvmName("getIsLoading")
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _replyStatus = MutableLiveData<Boolean?>()
    val replyStatus: LiveData<Boolean?> = _replyStatus

    init {
        val threadExtra = savedStateHandle.get<Thread>("thread")
        if (threadExtra != null) {
            initialThread = threadExtra
        } else {
            val url = savedStateHandle.get<String>("thread_url")
            val title = savedStateHandle.get<String>("thread_title")
            if (url != null) {
                initialThread = Thread(
                    System.currentTimeMillis().toString(),
                    title ?: "Loading...",
                    "",
                    0,
                    url
                )
            } else {
                // Should not happen if Activity checks intent
                initialThread = Thread("0", "Error", "", 0, "")
            }
        }
        
        loadThreadDetail(false)
    }

    fun refresh() {
        loadThreadDetail(true)
    }

    fun sendReply(content: String, turnstileToken: String?) {
        if (content.isBlank()) return

        viewModelScope.launch {
            _isLoading.value = true
            _replyStatus.value = null // Reset status
            try {
                var resto = initialThread.postNumber
                // If postNumber is invalid, try to parse from URL
                if (resto <= 0) {
                    val m = java.util.regex.Pattern.compile("res=(\\d+)").matcher(initialThread.url)
                    if (m.find()) {
                        resto = m.group(1)?.toIntOrNull() ?: 0
                    }
                }

                if (resto <= 0) {
                    _errorMessage.value = "無法取得討論串 ID"
                    _isLoading.value = false
                    return@launch
                }

                when (val result = repository.sendReply(initialThread.url, resto, "", "", "", content, turnstileToken)) {
                    is Resource.Success -> {
                        _replyStatus.value = result.data
                        if (result.data) {
                            refresh()
                        }
                    }
                    is Resource.Error -> {
                        _errorMessage.value = "回覆發生錯誤: ${result.message}"
                        _replyStatus.value = false
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _errorMessage.value = "回覆發生錯誤: ${e.message}"
                _replyStatus.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadThreadDetail(forceRefresh: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                when (val result = repository.fetchThreadDetail(initialThread.url, forceRefresh)) {
                    is Resource.Success -> {
                        val thread = result.data
                        _threadDetail.value = thread
                        // Save to history upon successful load
                        historyRepository.addOrUpdateHistory(
                            // 繁體中文註解：title 為非空字串，直接使用
                            title = thread.title,
                            url = thread.url,
                            thumbUrl = thread.imageUrl
                        )
                    }
                    is Resource.Error -> {
                        _errorMessage.value = result.message ?: getApplication<Application>().getString(R.string.error_load_thread_failed)
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
}
