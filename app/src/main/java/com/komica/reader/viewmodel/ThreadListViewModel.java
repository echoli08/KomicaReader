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
    
    private int currentPage = 0;
    private String currentSearchQuery = "";
    private int currentSortMode = 0; // 0: Latest, 1: Last Reply
    private boolean isRemoteSearchMode = false;

    public ThreadListViewModel(Application application, Board board) {
        this.board = board;
        this.repository = KomicaRepository.getInstance(application);
        loadThreads(0);
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
        allThreads.clear();
        existingThreadUrls.clear();
        hasMore.setValue(true);
        isRemoteSearchMode = false;
        loadThreads(0);
    }

    private void loadThreads(int page) {
        if (Boolean.TRUE.equals(isLoading.getValue())) return;
        
        isLoading.setValue(true);
        LiveData<List<Thread>> source = repository.fetchThreads(board.getUrl(), page);
        source.observeForever(new androidx.lifecycle.Observer<List<Thread>>() {
            @Override
            public void onChanged(List<Thread> newThreads) {
                source.removeObserver(this);
                isLoading.setValue(false);
                
                if (newThreads == null || newThreads.isEmpty()) {
                    hasMore.setValue(false);
                } else {
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
                    } else if (page < 10) {
                        // Delay loading next page to give UI a chance to update and avoid recursion
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            loadMore();
                        }, 200);
                    }
                }
                
                // Always ensure displayThreads has something if it's the first load
                if (page == 0 && displayThreads.getValue() != null && displayThreads.getValue().isEmpty()) {
                    applyFiltersAndSort();
                }
            }
        });
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
        hasMore.setValue(false); // Search results usually don't support pagination easily here

        LiveData<List<Thread>> source = repository.searchThreads(board.getUrl(), currentSearchQuery);
        source.observeForever(new androidx.lifecycle.Observer<List<Thread>>() {
            @Override
            public void onChanged(List<Thread> results) {
                source.removeObserver(this);
                isLoading.setValue(false);
                if (results != null) {
                    displayThreads.setValue(results);
                } else {
                    displayThreads.setValue(new ArrayList<>());
                }
            }
        });
    }

    public void clearSearch() {
        currentSearchQuery = "";
        isRemoteSearchMode = false;
        hasMore.setValue(true);
        applyFiltersAndSort();
    }

    public void setSortMode(int mode) {
        this.currentSortMode = mode;
        applyFiltersAndSort();
    }

    private void applyFiltersAndSort() {
        List<Thread> filtered = new ArrayList<>();
        if (currentSearchQuery.isEmpty()) {
            filtered.addAll(allThreads);
        } else {
            for (Thread t : allThreads) {
                if (t.getTitle().toLowerCase().contains(currentSearchQuery) ||
                    t.getContentPreview().toLowerCase().contains(currentSearchQuery)) {
                    filtered.add(t);
                }
            }
        }

        if (currentSortMode == 0) {
            Collections.sort(filtered, Comparator.comparing(Thread::getPostNumber).reversed());
        } else {
            Collections.sort(filtered, (t1, t2) -> {
                String time1 = t1.getLastReplyTime();
                String time2 = t2.getLastReplyTime();
                if (time1 == null || time1.isEmpty()) return 1;
                if (time2 == null || time2.isEmpty()) return -1;
                return time2.compareTo(time1);
            });
        }

        displayThreads.setValue(filtered);
    }
}
