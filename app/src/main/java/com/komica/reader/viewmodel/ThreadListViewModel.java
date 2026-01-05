package com.komica.reader.viewmodel;

import android.app.Application;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.komica.reader.model.Board;
import com.komica.reader.model.Thread;
import com.komica.reader.repository.KomicaRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ThreadListViewModel extends ViewModel {
    private final KomicaRepository repository;
    private final Board board;
    private final MutableLiveData<List<Thread>> displayThreads = new MutableLiveData<>(new ArrayList<>());
    private final List<Thread> allThreads = new ArrayList<>();
    private final Set<String> existingThreadUrls = new HashSet<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> hasMore = new MutableLiveData<>(true);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    
    private int currentPage = 0;
    private int consecutiveEmptyPages = 0;
    private String currentSearchQuery = "";
    private boolean isRemoteSearchMode = false;
    
    private LiveData<List<Thread>> currentSource;
    private androidx.lifecycle.Observer<List<Thread>> currentObserver;

    public ThreadListViewModel(Application application, Board board) {
        this.board = board;
        this.repository = KomicaRepository.getInstance(application);
        loadThreads(0);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        detachCurrentObserver();
    }
    
    private void detachCurrentObserver() {
        if (currentSource != null && currentObserver != null) {
            currentSource.removeObserver(currentObserver);
        }
        currentSource = null;
        currentObserver = null;
    }

    public LiveData<List<Thread>> getThreads() {
        return displayThreads;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<Boolean> getHasMore() {
        return hasMore;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public boolean isRemoteSearchMode() {
        return isRemoteSearchMode;
    }

    public void loadMore() {
        if (Boolean.TRUE.equals(isLoading.getValue()) || Boolean.FALSE.equals(hasMore.getValue()) || isRemoteSearchMode) return;
        loadThreads(currentPage + 1);
    }

    public void refresh() {
        if (Boolean.TRUE.equals(isLoading.getValue())) return;
        // Reset states
        currentPage = 0;
        consecutiveEmptyPages = 0;
        allThreads.clear();
        existingThreadUrls.clear();
        hasMore.setValue(true);
        isRemoteSearchMode = false;
        loadThreads(0);
    }

    private void loadThreads(int page) {
        if (Boolean.TRUE.equals(isLoading.getValue())) return;
        
        isLoading.setValue(true);
        detachCurrentObserver();
        
        currentSource = repository.fetchThreads(board.getUrl(), page);
        currentObserver = new androidx.lifecycle.Observer<List<Thread>>() {
            @Override
            public void onChanged(List<Thread> newThreads) {
                detachCurrentObserver(); // One-shot
                isLoading.setValue(false);
                
                if (newThreads == null || newThreads.isEmpty()) {
                    consecutiveEmptyPages++;
                    if (page == 0) {
                        errorMessage.setValue("無法取得資料或該板塊目前沒有主題");
                    }
                    if (consecutiveEmptyPages >= 3) {
                        hasMore.setValue(false);
                    } else {
                        // Keep trying next page if previous were also empty
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> loadMore(), 500);
                    }
                } else {
                    consecutiveEmptyPages = 0; // Reset counter on success
                    currentPage = page;
                    List<Thread> uniqueThreads = new ArrayList<>();
                    for (Thread thread : newThreads) {
                        if (!existingThreadUrls.contains(thread.getUrl())) {
                            existingThreadUrls.add(thread.getUrl());
                            uniqueThreads.add(thread);
                        }
                    }
                    
                    if (!uniqueThreads.isEmpty()) {
                        allThreads.addAll(uniqueThreads);
                        if (!isRemoteSearchMode) {
                            applyFiltersAndSort();
                        }
                    }
                }
                
                // Always ensure displayThreads has something if it's the first load
                if (page == 0 && (displayThreads.getValue() == null || displayThreads.getValue().isEmpty())) {
                    applyFiltersAndSort();
                }
            }
        };
        currentSource.observeForever(currentObserver);
    }

    public void setSearchQuery(String query) {
        this.currentSearchQuery = query.toLowerCase().trim();
        if (currentSearchQuery.isEmpty()) {
            isRemoteSearchMode = false;
            hasMore.setValue(true);
            applyFiltersAndSort();
        } else {
            // Keep local filter for immediate response
            applyFiltersAndSort();
        }
    }

    public void performRemoteSearch() {
        if (currentSearchQuery.isEmpty()) return;
        
        isLoading.setValue(true);
        isRemoteSearchMode = true;
        hasMore.setValue(false);
        
        detachCurrentObserver();

        currentSource = repository.searchThreads(board.getUrl(), currentSearchQuery);
        currentObserver = new androidx.lifecycle.Observer<List<Thread>>() {
            @Override
            public void onChanged(List<Thread> results) {
                detachCurrentObserver(); // One-shot
                isLoading.setValue(false);
                if (results != null && !results.isEmpty()) {
                    displayThreads.setValue(results);
                } else {
                    displayThreads.setValue(new ArrayList<>());
                    errorMessage.setValue("找不到符合的搜尋結果");
                }
            }
        };
        currentSource.observeForever(currentObserver);
    }

    public void clearSearch() {
        currentSearchQuery = "";
        isRemoteSearchMode = false;
        hasMore.setValue(true);
        applyFiltersAndSort();
    }

    private void applyFiltersAndSort() {
        List<Thread> filtered = new ArrayList<>();
        if (currentSearchQuery.isEmpty()) {
            filtered.addAll(allThreads);
        } else {
            for (Thread t : allThreads) {
                String title = t.getTitle() != null ? t.getTitle().toLowerCase() : "";
                String preview = t.getContentPreview() != null ? t.getContentPreview().toLowerCase() : "";
                
                if (title.contains(currentSearchQuery) || preview.contains(currentSearchQuery)) {
                    filtered.add(t);
                }
            }
        }

        // Always sort by Post Number Descending (Newest post first)
        Collections.sort(filtered, Comparator.comparing(Thread::getPostNumber).reversed());

        displayThreads.setValue(filtered);
    }
}
