package com.komica.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.komica.reader.data.KomicaRepository
import com.komica.reader.model.ThreadDetail
import com.komica.reader.model.UiState
import com.komica.reader.util.ErrorMessage
import kotlinx.coroutines.launch

class ThreadDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = KomicaRepository()

    private val _state = MutableLiveData<UiState<ThreadDetail>>(UiState.Loading)
    val state: LiveData<UiState<ThreadDetail>> = _state

    fun LoadThread(threadUrl: String) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching { repository.LoadThreadDetail(threadUrl) }
                .onSuccess { _state.value = UiState.Content(it) }
                .onFailure { _state.value = UiState.Error(ErrorMessage.Build("載入討論串內容失敗", it), it) }
        }
    }
}
