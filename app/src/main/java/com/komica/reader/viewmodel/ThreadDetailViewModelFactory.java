package com.komica.reader.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.komica.reader.model.Thread;

public class ThreadDetailViewModelFactory implements ViewModelProvider.Factory {
    private final Application application;
    private final Thread thread;

    public ThreadDetailViewModelFactory(Application application, Thread thread) {
        this.application = application;
        this.thread = thread;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ThreadDetailViewModel.class)) {
            return (T) new ThreadDetailViewModel(application, thread);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
