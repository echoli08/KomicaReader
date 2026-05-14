package com.komica.reader.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC")
    suspend fun GetAll(): List<HistoryEntity>

    @Upsert
    suspend fun Upsert(entity: HistoryEntity)

    @Query("DELETE FROM history WHERE url = :url")
    suspend fun DeleteByUrl(url: String)

    @Query("DELETE FROM history")
    suspend fun Clear()
}
