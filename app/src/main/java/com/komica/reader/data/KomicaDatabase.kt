package com.komica.reader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [HistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class KomicaDatabase : RoomDatabase() {
    abstract fun HistoryDao(): HistoryDao

    companion object {
        @Volatile
        private var instance: KomicaDatabase? = null

        fun Get(context: Context): KomicaDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KomicaDatabase::class.java,
                    "komica_reader.db"
                )
                    .allowMainThreadQueries()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
