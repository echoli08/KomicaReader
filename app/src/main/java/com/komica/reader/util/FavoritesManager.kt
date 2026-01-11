package com.komica.reader.util

import android.content.Context
import android.content.SharedPreferences
import java.util.Collections
import java.util.HashSet

// 繁體中文註解：我的最愛管理器，使用快取減少磁碟讀取
class FavoritesManager private constructor(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cachedFavorites: Set<String> =
        HashSet(sharedPreferences.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet())

    fun getFavorites(): Set<String> {
        return Collections.unmodifiableSet(cachedFavorites)
    }

    fun isFavorite(boardUrl: String): Boolean {
        return cachedFavorites.contains(boardUrl)
    }

    @Synchronized
    fun replaceFavorites(newFavorites: Set<String>) {
        cachedFavorites = HashSet(newFavorites)
        // 繁體中文註解：完整覆蓋最愛清單，確保還原內容一致
        sharedPreferences.edit().putStringSet(KEY_FAVORITES, cachedFavorites).commit()
    }

    @Synchronized
    fun toggleFavorite(boardUrl: String) {
        val newFavorites = HashSet(cachedFavorites)
        if (newFavorites.contains(boardUrl)) {
            newFavorites.remove(boardUrl)
        } else {
            newFavorites.add(boardUrl)
        }
        cachedFavorites = newFavorites
        // 繁體中文註解：資料量小，使用 commit 確保立即寫入
        sharedPreferences.edit().putStringSet(KEY_FAVORITES, newFavorites).commit()
    }

    companion object {
        private const val PREF_NAME = "KomicaFavorites"
        private const val KEY_FAVORITES = "favorite_boards"

        @Volatile
        private var instance: FavoritesManager? = null

        @JvmStatic
        fun getInstance(context: Context): FavoritesManager {
            return instance ?: synchronized(this) {
                instance ?: FavoritesManager(context).also { instance = it }
            }
        }
    }
}
