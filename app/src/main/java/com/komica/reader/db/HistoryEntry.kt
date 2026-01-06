package com.komica.reader.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val thumbUrl: String?,
    val updatedAt: Long = System.currentTimeMillis()
)
