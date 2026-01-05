package com.komica.reader.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.komica.reader.model.Board

class ThreadListViewModelFactory(
    private val application: Application,
    private val board: Board
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ThreadListViewModel::class.java)) {
            return ThreadListViewModel(application, board) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
