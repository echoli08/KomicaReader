package com.komica.reader.viewmodel;

import android.app.Application;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.komica.reader.model.Thread;
import com.komica.reader.repository.KomicaRepository;

public class ThreadDetailViewModel extends ViewModel {
    private final KomicaRepository repository;
    private final Thread initialThread;
    private final MutableLiveData<Thread> threadDetail = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final androidx.lifecycle.Observer<Thread> threadObserver;
    private LiveData<Thread> source;

    public ThreadDetailViewModel(Application application, Thread thread) {
        this.initialThread = thread;
        this.repository = KomicaRepository.getInstance(application);
        this.threadObserver = new androidx.lifecycle.Observer<Thread>() {
            @Override
            public void onChanged(Thread thread) {
                isLoading.setValue(false);
                if (thread != null) {
                    threadDetail.setValue(thread);
                } else {
                    errorMessage.setValue("載入討論串失敗");
                }
            }
        };
        loadThreadDetail(false);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (source != null) {
            source.removeObserver(threadObserver);
        }
    }

    public LiveData<Thread> getThreadDetail() {
        return threadDetail;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void refresh() {
        loadThreadDetail(true);
    }

    private void loadThreadDetail(boolean forceRefresh) {
        isLoading.setValue(true);
        if (source != null) {
            source.removeObserver(threadObserver);
        }
        source = repository.fetchThreadDetail(initialThread.getUrl(), forceRefresh);
        source.observeForever(threadObserver);
    }
}
