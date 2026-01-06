package com.komica.reader.repository

import android.content.Context
import android.util.LruCache
import com.komica.reader.model.BoardCategory
import com.komica.reader.model.Thread
import com.komica.reader.service.KomicaService
import com.komica.reader.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class KomicaRepository private constructor(context: Context) {

    private val threadDetailCache = LruCache<String, Thread>(20)
    private var boardCategoryCache: List<BoardCategory>? = null

    init {
        initOkHttp(context)
    }

    private fun initOkHttp(context: Context) {
        val cacheDir = File(context.cacheDir, "http_cache")
        val cache = Cache(cacheDir, 10 * 1024 * 1024)

        val client = OkHttpClient.Builder()
            .cache(cache)
            .cookieJar(object : okhttp3.CookieJar {
                private val cookieStore = HashMap<String, MutableList<okhttp3.Cookie>>()

                @Synchronized
                override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
                    val host = url.host
                    val currentCookies = cookieStore[host] ?: ArrayList()
                    
                    for (newCookie in cookies) {
                        // Remove existing cookie with the same name
                        val it = currentCookies.iterator()
                        while (it.hasNext()) {
                            val current = it.next()
                            if (current.name == newCookie.name) {
                                it.remove()
                            }
                        }
                        currentCookies.add(newCookie)
                    }
                    cookieStore[host] = currentCookies
                }

                @Synchronized
                override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
                    val host = url.host
                    val cookies = cookieStore[host] ?: return emptyList()
                    
                    // Filter expired cookies
                    val validCookies = ArrayList<okhttp3.Cookie>()
                    val it = cookies.iterator()
                    val now = System.currentTimeMillis()
                    while (it.hasNext()) {
                        val current = it.next()
                        if (current.expiresAt > now) {
                            validCookies.add(current)
                        } else {
                            it.remove()
                        }
                    }
                    return validCookies
                }
            })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "zh-TW,zh;q=0.8,en-US;q=0.5,en;q=0.3")
                    .build()
                chain.proceed(request)
            }
            .build()

        KomicaService.setClient(client)
    }

    suspend fun fetchBoards(forceRefresh: Boolean): List<BoardCategory> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            boardCategoryCache?.let { return@withContext it }
        }

        try {
            val result = KomicaService.FetchBoardsTask().call()
            if (result != null) {
                boardCategoryCache = result
            }
            result ?: emptyList()
        } catch (e: Exception) {
            KLog.e("Error fetching boards: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchThreads(boardUrl: String, page: Int): List<Thread> = withContext(Dispatchers.IO) {
        try {
            val result = KomicaService.FetchThreadsTask(boardUrl, page).call()
            result ?: emptyList()
        } catch (e: Exception) {
            KLog.e("Error fetching threads: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchThreadDetail(threadUrl: String, forceRefresh: Boolean): Thread? = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            threadDetailCache.get(threadUrl)?.let { return@withContext it }
        }

        try {
            val result = KomicaService.FetchThreadDetailTask(threadUrl).call()
            if (result != null) {
                threadDetailCache.put(threadUrl, result)
            }
            result
        } catch (e: Exception) {
            KLog.e("Error fetching thread detail: ${e.message}")
            null
        }
    }

    suspend fun searchThreads(boardUrl: String, query: String): List<Thread> = withContext(Dispatchers.IO) {
        try {
            val result = KomicaService.BoardSearchTask(boardUrl, query).call()
            result ?: emptyList()
        } catch (e: Exception) {
            KLog.e("Error searching threads: ${e.message}")
            emptyList()
        }
    }

    suspend fun sendReply(boardUrl: String, resto: Int, name: String, email: String, subject: String, comment: String): Boolean = withContext(Dispatchers.IO) {
        try {
            KomicaService.SendReplyTask(boardUrl, resto, name, email, subject, comment).call()
        } catch (e: Exception) {
            KLog.e("Error sending reply: ${e.message}")
            false
        }
    }

    /**
     * Compatibility method for Java callers.
     * Uses Dispatchers.IO to run the runnable.
     */
    fun execute(runnable: Runnable) {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            runnable.run()
        }
    }

    companion object {
        @Volatile
        private var instance: KomicaRepository? = null

        @JvmStatic
        fun getInstance(context: Context): KomicaRepository {
            return instance ?: synchronized(this) {
                instance ?: KomicaRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
