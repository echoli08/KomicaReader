package com.komica.reader.data

import android.content.Context

class BoardUiStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("KomicaReaderV2", Context.MODE_PRIVATE)

    fun IsCollapsed(categoryName: String): Boolean {
        return prefs.getStringSet(KeyCollapsedCategories, emptySet()).orEmpty().contains(categoryName)
    }

    fun ToggleCollapsed(categoryName: String) {
        val current = prefs.getStringSet(KeyCollapsedCategories, emptySet()).orEmpty().toMutableSet()
        if (!current.add(categoryName)) {
            current.remove(categoryName)
        }
        prefs.edit().putStringSet(KeyCollapsedCategories, current).apply()
    }

    fun GetCollapsedCategories(): Set<String> {
        return prefs.getStringSet(KeyCollapsedCategories, emptySet()).orEmpty()
    }

    fun ReplaceCollapsedCategories(categories: Collection<String>) {
        prefs.edit().putStringSet(KeyCollapsedCategories, categories.filter { it.isNotBlank() }.toSet()).apply()
    }

    companion object {
        private const val KeyCollapsedCategories = "collapsed_categories"
    }
}
