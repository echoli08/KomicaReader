package com.komica.reader.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY updatedAt DESC")
    fun getAllHistory(): LiveData<List<HistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntry)

    @Query("DELETE FROM history")
    suspend fun deleteAll()

    @Query("DELETE FROM history WHERE url = :url")
    suspend fun deleteByUrl(url: String)
    
    // Check if url exists and update timestamp if so
    @Query("SELECT * FROM history WHERE url = :url LIMIT 1")
    suspend fun getEntryByUrl(url: String): HistoryEntry?
    
    @Query("UPDATE history SET updatedAt = :timestamp, title = :title, thumbUrl = :thumbUrl WHERE url = :url")
    suspend fun updateHistory(url: String, title: String, thumbUrl: String?, timestamp: Long)
}
