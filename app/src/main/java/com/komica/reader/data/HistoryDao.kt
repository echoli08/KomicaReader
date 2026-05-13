package com.komica.reader.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC")
    fun GetAll(): List<HistoryEntity>

    @Upsert
    fun Upsert(entity: HistoryEntity)

    @Query("DELETE FROM history WHERE url = :url")
    fun DeleteByUrl(url: String)

    @Query("DELETE FROM history")
    fun Clear()
}
