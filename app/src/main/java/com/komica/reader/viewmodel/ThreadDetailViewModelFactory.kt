package com.komica.reader.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.komica.reader.model.Thread

class ThreadDetailViewModelFactory(
    private val application: Application,
    private val thread: Thread
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ThreadDetailViewModel::class.java)) {
            return ThreadDetailViewModel(application, thread) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
