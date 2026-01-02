package com.komica.reader.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.komica.reader.model.Board;

public class ThreadListViewModelFactory implements ViewModelProvider.Factory {
    private final Application application;
    private final Board board;

    public ThreadListViewModelFactory(Application application, Board board) {
        this.application = application;
        this.board = board;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ThreadListViewModel.class)) {
            return (T) new ThreadListViewModel(application, board);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
