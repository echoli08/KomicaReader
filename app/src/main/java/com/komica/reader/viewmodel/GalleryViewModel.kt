package com.komica.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.komica.reader.model.Post
import com.komica.reader.repository.KomicaRepository
import kotlinx.coroutines.launch

class GalleryViewModel(application: Application, private val threadUrl: String) : AndroidViewModel(application) {
    private val repository = KomicaRepository.getInstance(application)
    
    private val _imagePosts = MutableLiveData<List<Post>>()
    val imagePosts: LiveData<List<Post>> = _imagePosts
    
    val allImageUrls: List<String>
        get() = _imagePosts.value?.map { it.imageUrl ?: "" } ?: emptyList()

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        loadImages()
    }

    private fun loadImages() {
        viewModelScope.launch {
            _isLoading.value = true
            // fetchThreadDetail with forceRefresh=false will use cache if available
            val thread = repository.fetchThreadDetail(threadUrl, false)
            
            val images = thread?.posts?.filter { !it.imageUrl.isNullOrEmpty() } ?: emptyList()
            _imagePosts.value = images
            
            _isLoading.value = false
        }
    }
}

class GalleryViewModelFactory(
    private val application: Application,
    private val threadUrl: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GalleryViewModel::class.java)) {
            return GalleryViewModel(application, threadUrl) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
