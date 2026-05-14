package com.komica.reader.data

import android.content.Context
import com.komica.reader.model.KomicaThread

class HistoryStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("KomicaReaderV2", Context.MODE_PRIVATE)
    private val dao = KomicaDatabase.Get(appContext).HistoryDao()

    suspend fun Add(thread: KomicaThread) {
        MigrateLegacyHistoryIfNeeded()
        dao.Upsert(
            HistoryEntity(
                url = thread.url,
                title = thread.title,
                imageUrl = thread.imageUrl,
                visitedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun GetAll(): List<KomicaThread> {
        MigrateLegacyHistoryIfNeeded()
        return dao.GetAll()
            .map { entity ->
                KomicaThread(
                    id = entity.visitedAt.toString(),
                    title = entity.title,
                    author = "",
                    replyCount = 0,
                    url = entity.url,
                    imageUrl = entity.imageUrl,
                    lastReplyTime = "最近瀏覽"
                )
            }
    }

    suspend fun Clear() {
        dao.Clear()
        prefs.edit().remove(KeyHistory).apply()
    }

    suspend fun Remove(url: String) {
        MigrateLegacyHistoryIfNeeded()
        dao.DeleteByUrl(url)
    }

    private suspend fun MigrateLegacyHistoryIfNeeded() {
        if (prefs.getBoolean(KeyHistoryMigrated, false)) return
        prefs.getStringSet(KeyHistory, emptySet()).orEmpty()
            .mapNotNull { line ->
                val parts = line.split("\t")
                if (parts.size < 4) return@mapNotNull null
                HistoryEntity(
                    url = parts[2],
                    title = parts[1],
                    imageUrl = parts.getOrElse(3) { "" },
                    visitedAt = parts[0].toLongOrNull() ?: System.currentTimeMillis()
                )
            }
            .forEach { dao.Upsert(it) }
        prefs.edit()
            .putBoolean(KeyHistoryMigrated, true)
            .remove(KeyHistory)
            .apply()
    }

    companion object {
        private const val KeyHistory = "history"
        private const val KeyHistoryMigrated = "history_migrated_to_room"
    }
}
