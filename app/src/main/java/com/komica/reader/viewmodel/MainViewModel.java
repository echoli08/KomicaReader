package com.komica.reader.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.MediatorLiveData;

import com.komica.reader.model.Board;
import com.komica.reader.model.BoardCategory;
import com.komica.reader.repository.KomicaRepository;
import com.komica.reader.util.FavoritesManager;

import java.util.ArrayList;
import java.util.List;

public class MainViewModel extends AndroidViewModel {
    private final KomicaRepository repository;
    private final FavoritesManager favoritesManager;
    private final MutableLiveData<List<BoardCategory>> originalCategories = new MutableLiveData<>();
    private final MediatorLiveData<List<BoardCategory>> displayCategories = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public MainViewModel(@NonNull Application application) {
        super(application);
        repository = KomicaRepository.getInstance(application);
        favoritesManager = FavoritesManager.getInstance(application);
        
        displayCategories.addSource(originalCategories, categories -> updateDisplayCategories());
    }

    public LiveData<List<BoardCategory>> getCategories() {
        return displayCategories;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void loadBoards() {
        loadBoards(false);
    }

    public void loadBoards(boolean forceRefresh) {
        isLoading.setValue(true);
        LiveData<List<BoardCategory>> source = repository.fetchBoards(forceRefresh);
        displayCategories.addSource(source, result -> {
            displayCategories.removeSource(source);
            isLoading.setValue(false);
            if (result != null) {
                originalCategories.setValue(result);
            } else {
                errorMessage.setValue("載入板塊失敗");
            }
        });
    }

    public void toggleFavorite(Board board) {
        favoritesManager.toggleFavorite(board.getUrl());
        updateDisplayCategories();
    }

    public void refreshFavorites() {
        updateDisplayCategories();
    }

    private void updateDisplayCategories() {
        List<BoardCategory> original = originalCategories.getValue();
        if (original == null) return;

        List<BoardCategory> updated = new ArrayList<>();
        List<Board> favoriteBoards = new ArrayList<>();

        for (BoardCategory cat : original) {
            for (Board board : cat.getBoards()) {
                if (favoritesManager.isFavorite(board.getUrl())) {
                    boolean alreadyAdded = false;
                    for (Board fb : favoriteBoards) {
                        if (fb.getUrl().equals(board.getUrl())) {
                            alreadyAdded = true;
                            break;
                        }
                    }
                    if (!alreadyAdded) {
                        favoriteBoards.add(board);
                    }
                }
            }
        }

        if (!favoriteBoards.isEmpty()) {
            BoardCategory favCategory = new BoardCategory("我的最愛", favoriteBoards);
            favCategory.setExpanded(true);
            updated.add(favCategory);
        }

        updated.addAll(original);
        displayCategories.setValue(updated);
    }
}
