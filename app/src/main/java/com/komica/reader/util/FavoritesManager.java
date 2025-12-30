package com.komica.reader.util;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public class FavoritesManager {
    private static final String PREF_NAME = "KomicaFavorites";
    private static final String KEY_FAVORITES = "favorite_boards";
    private static FavoritesManager instance;
    private SharedPreferences sharedPreferences;

    private FavoritesManager(Context context) {
        sharedPreferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized FavoritesManager getInstance(Context context) {
        if (instance == null) {
            instance = new FavoritesManager(context);
        }
        return instance;
    }

    public Set<String> getFavorites() {
        return sharedPreferences.getStringSet(KEY_FAVORITES, new HashSet<>());
    }

    public boolean isFavorite(String boardUrl) {
        return getFavorites().contains(boardUrl);
    }

    public void addFavorite(String boardUrl) {
        Set<String> favorites = new HashSet<>(getFavorites());
        favorites.add(boardUrl);
        sharedPreferences.edit().putStringSet(KEY_FAVORITES, favorites).apply();
    }

    public void removeFavorite(String boardUrl) {
        Set<String> favorites = new HashSet<>(getFavorites());
        favorites.remove(boardUrl);
        sharedPreferences.edit().putStringSet(KEY_FAVORITES, favorites).apply();
    }
    
    public void toggleFavorite(String boardUrl) {
        if (isFavorite(boardUrl)) {
            removeFavorite(boardUrl);
        } else {
            addFavorite(boardUrl);
        }
    }
}
