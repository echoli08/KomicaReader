package com.komica.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.komica.reader.db.HistoryEntry
import com.komica.reader.repository.HistoryRepository
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HistoryRepository.getInstance(application)
    
    val historyList: LiveData<List<HistoryEntry>> = repository.allHistory

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }
    
    fun deleteHistory(url: String) {
        viewModelScope.launch {
            repository.deleteHistory(url)
        }
    }
}
