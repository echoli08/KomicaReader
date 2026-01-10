package com.komica.reader.repository

import android.content.Context
import android.util.LruCache
import com.komica.reader.model.BoardCategory
import com.komica.reader.model.Thread
import com.komica.reader.model.Resource
import com.komica.reader.service.KomicaService
import com.komica.reader.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.util.concurrent.TimeUnit
import android.webkit.*

class KomicaRepository private constructor(context: Context) {

    private val threadDetailCache = LruCache<String, Thread>(20)
    private var boardCategoryCache: List<BoardCategory>? = null

    init {
        initOkHttp(context)
    }

    private lateinit var cookieJar: KomicaCookieJar

    private fun initOkHttp(context: Context) {
        val cacheDir = File(context.cacheDir, "http_cache")
        val cache = Cache(cacheDir, 10 * 1024 * 1024)

        cookieJar = KomicaCookieJar()

        val client = OkHttpClient.Builder()
            .cache(cache)
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val original = chain.request()
                // Use Mobile UA to match WebView and avoid token/session mismatch
                val request = original.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "zh-TW,zh;q=0.8,en-US;q=0.5,en;q=0.3")
                    .build()
                chain.proceed(request)
            }
            .build()

        KomicaService.setClient(client)
    }

    private class KomicaCookieJar : okhttp3.CookieJar {
        private val cookieStore = HashMap<String, MutableList<okhttp3.Cookie>>()

        @Synchronized
        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
            val host = url.host
            val currentCookies = cookieStore[host] ?: ArrayList()
            
            KLog.d("CookieJar: Saving ${cookies.size} cookies for $host")
            
            for (newCookie in cookies) {
                // Remove existing cookie with the same name/domain/path
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
            val result = ArrayList<okhttp3.Cookie>()
            val now = System.currentTimeMillis()
            
            for (cookies in cookieStore.values) {
                val it = cookies.iterator()
                while (it.hasNext()) {
                    val cookie = it.next()
                    if (cookie.expiresAt < now) {
                        it.remove()
                    } else if (cookie.matches(url)) {
                        result.add(cookie)
                    }
                }
            }
            
            val host = url.host
            KLog.d("CookieJar: Loading ${result.size} cookies for $host")
            for (c in result) {
                KLog.d("CookieJar: -> ${c.name}")
            }
            
            return result
        }

        fun addCookie(url: okhttp3.HttpUrl, cookie: okhttp3.Cookie) {
            saveFromResponse(url, listOf(cookie))
        }

        @Synchronized
        fun addRawCookies(url: okhttp3.HttpUrl, cookieString: String?) {
            if (cookieString == null || cookieString.isEmpty()) return
            val host = url.host
            val pairs = cookieString.split(";").map { it.trim() }
            val cookies = ArrayList<okhttp3.Cookie>()
            for (pair in pairs) {
                if (pair.isEmpty()) continue
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2) {
                    val name = parts[0]
                    val value = parts[1]
                    
                    val domainsToCheck = ArrayList<String>()
                    domainsToCheck.add(host)
                    
                    if ((name.startsWith("cf_") || name.startsWith("__cf")) && host.count { it == '.' } >= 2) {
                        val hostParts = host.split(".")
                        if (hostParts.size >= 2) {
                            val baseDomain = hostParts[hostParts.size - 2] + "." + hostParts[hostParts.size - 1]
                            domainsToCheck.add(baseDomain)
                            domainsToCheck.add(".$baseDomain")
                        }
                    }

                    for (d in domainsToCheck) {
                        try {
                            val cookie = okhttp3.Cookie.Builder()
                                .name(name)
                                .value(value)
                                .domain(d)
                                .path("/")
                                .build()
                            cookies.add(cookie)
                        } catch (e: Exception) {
                            // Ignore invalid domains
                        }
                    }
                }
            }
            if (cookies.isNotEmpty()) {
                KLog.d("CookieJar: Injected ${cookies.size} raw cookies from WebView for $host")
                saveFromResponse(url, cookies)
            }
        }
    }

    suspend fun fetchBoards(forceRefresh: Boolean): Resource<List<BoardCategory>> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            boardCategoryCache?.let { return@withContext Resource.Success(it) }
        }

        try {
            val result = KomicaService.FetchBoardsTask().call()
            boardCategoryCache = result
            Resource.Success(result)
        } catch (e: Exception) {
            KLog.e("Error fetching boards: ${e.message}")
            Resource.Error(e)
        }
    }

    suspend fun fetchThreads(boardUrl: String, page: Int): Resource<List<Thread>> = withContext(Dispatchers.IO) {
        try {
            val result = KomicaService.FetchThreadsTask(boardUrl, page).call()
            Resource.Success(result)
        } catch (e: Exception) {
            KLog.e("Error fetching threads: ${e.message}")
            Resource.Error(e)
        }
    }

    suspend fun fetchThreadDetail(threadUrl: String, forceRefresh: Boolean): Resource<Thread> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            threadDetailCache.get(threadUrl)?.let { return@withContext Resource.Success(it) }
        }

        try {
            val result = KomicaService.FetchThreadDetailTask(threadUrl).call()
            threadDetailCache.put(threadUrl, result)
            Resource.Success(result)
        } catch (e: Exception) {
            KLog.e("Error fetching thread detail: ${e.message}")
            Resource.Error(e)
        }
    }

    suspend fun searchThreads(boardUrl: String, query: String): Resource<List<Thread>> = withContext(Dispatchers.IO) {
        try {
            val result = KomicaService.BoardSearchTask(boardUrl, query).call()
            Resource.Success(result)
        } catch (e: Exception) {
            KLog.e("Error searching threads: ${e.message}")
            Resource.Error(e)
        }
    }

    suspend fun sendReply(boardUrl: String, resto: Int, name: String, email: String, subject: String, comment: String, turnstileToken: String?): Resource<Boolean> = withContext(Dispatchers.IO) {
        try {
            val urlObj = boardUrl.toHttpUrlOrNull()
            if (urlObj != null) {
                val cookieManager = CookieManager.getInstance()
                val cookieStr = cookieManager.getCookie(boardUrl)
                
                KLog.d("=== COOKIES SYNC CHECK ===")
                KLog.d("Target URL: $boardUrl")
                KLog.d("WebView Cookies: $cookieStr")
                
                if (cookieStr != null) {
                    cookieJar.addRawCookies(urlObj, cookieStr)
                } else {
                    KLog.w("⚠️ No cookies found in WebView for $boardUrl")
                }
            }

            val result = KomicaService.SendReplyTask(boardUrl, resto, name, email, subject, comment, turnstileToken).call()
            Resource.Success(result)
        } catch (e: Exception) {
            KLog.e("Error sending reply: ${e.message}")
            Resource.Error(e)
        }
    }

    private fun syncCookies(url: String, urlObj: okhttp3.HttpUrl?): Boolean {
        if (urlObj == null) return false
        val cookieManager = CookieManager.getInstance()
        val cookieStr = cookieManager.getCookie(url)
        cookieJar.addRawCookies(urlObj, cookieStr)
        return cookieStr?.contains("cf_clearance") == true
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
