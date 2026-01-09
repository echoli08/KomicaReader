package com.komica.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.komica.reader.model.Post
import com.komica.reader.model.Resource
import com.komica.reader.repository.KomicaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    application: Application,
    private val repository: KomicaRepository,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    
    private val threadUrl: String = savedStateHandle.get<String>("thread_url")
        ?: throw IllegalArgumentException("thread_url is required")
    
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
            val result = repository.fetchThreadDetail(threadUrl, false)
            
            val thread = if (result is Resource.Success) result.data else null
            
            val images = thread?.posts?.filter { !it.imageUrl.isNullOrEmpty() } ?: emptyList()
            _imagePosts.value = images
            
            _isLoading.value = false
        }
    }
}
