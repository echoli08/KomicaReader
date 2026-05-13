package com.komica.reader.data

import android.content.Context

class FavoritesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("KomicaReaderV2", Context.MODE_PRIVATE)

    fun IsFavorite(url: String): Boolean {
        return prefs.getStringSet(KeyFavorites, emptySet()).orEmpty().contains(url)
    }

    fun Toggle(url: String) {
        val current = prefs.getStringSet(KeyFavorites, emptySet()).orEmpty().toMutableSet()
        if (!current.add(url)) {
            current.remove(url)
        }
        prefs.edit().putStringSet(KeyFavorites, current).apply()
    }

    fun GetAll(): Set<String> {
        return prefs.getStringSet(KeyFavorites, emptySet()).orEmpty()
    }

    fun Import(urls: Collection<String>) {
        val current = GetAll().toMutableSet()
        current.addAll(urls.filter { it.isNotBlank() })
        prefs.edit().putStringSet(KeyFavorites, current).apply()
    }

    fun Replace(urls: Collection<String>) {
        prefs.edit().putStringSet(KeyFavorites, urls.filter { it.isNotBlank() }.toSet()).apply()
    }

    companion object {
        private const val KeyFavorites = "favorites"
    }
}
