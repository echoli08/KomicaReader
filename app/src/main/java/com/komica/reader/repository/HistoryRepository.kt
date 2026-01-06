package com.komica.reader.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.komica.reader.db.AppDatabase
import com.komica.reader.db.HistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HistoryRepository(context: Context) {
    private val historyDao = AppDatabase.getDatabase(context).historyDao()

    val allHistory: LiveData<List<HistoryEntry>> = historyDao.getAllHistory()

    suspend fun addOrUpdateHistory(title: String, url: String, thumbUrl: String?) = withContext(Dispatchers.IO) {
        val existing = historyDao.getEntryByUrl(url)
        if (existing != null) {
            historyDao.updateHistory(url, title, thumbUrl, System.currentTimeMillis())
        } else {
            val entry = HistoryEntry(title = title, url = url, thumbUrl = thumbUrl)
            historyDao.insert(entry)
        }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        historyDao.deleteAll()
    }
    
    suspend fun deleteHistory(url: String) = withContext(Dispatchers.IO) {
        historyDao.deleteByUrl(url)
    }

    companion object {
        @Volatile
        private var instance: HistoryRepository? = null

        fun getInstance(context: Context): HistoryRepository {
            return instance ?: synchronized(this) {
                instance ?: HistoryRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
