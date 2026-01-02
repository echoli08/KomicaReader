package com.komica.reader.util;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;

public class FavoritesManager {
    private static final String PREF_NAME = "KomicaFavorites";
    private static final String KEY_FAVORITES = "favorite_boards";
    private static FavoritesManager instance;
    private final SharedPreferences sharedPreferences;
    
    // Memory cache to avoid repeated disk reads
    private volatile Set<String> cachedFavorites;

    private FavoritesManager(Context context) {
        sharedPreferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        // Initial load
        cachedFavorites = new HashSet<>(sharedPreferences.getStringSet(KEY_FAVORITES, new HashSet<>()));
    }

    public static synchronized FavoritesManager getInstance(Context context) {
        if (instance == null) {
            instance = new FavoritesManager(context);
        }
        return instance;
    }

    public synchronized Set<String> getFavorites() {
        return Collections.unmodifiableSet(cachedFavorites);
    }

    public boolean isFavorite(String boardUrl) {
        return cachedFavorites.contains(boardUrl);
    }

    public synchronized void toggleFavorite(String boardUrl) {
        Set<String> newFavorites = new HashSet<>(cachedFavorites);
        if (newFavorites.contains(boardUrl)) {
            newFavorites.remove(boardUrl);
        } else {
            newFavorites.add(boardUrl);
        }
        
        cachedFavorites = newFavorites;
        sharedPreferences.edit().putStringSet(KEY_FAVORITES, newFavorites).apply();
    }
}