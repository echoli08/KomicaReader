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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.util.concurrent.TimeUnit
import android.webkit.*
import kotlinx.coroutines.*

class KomicaRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
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

    private class KomicaCookieJar : okhttp3.CookieJar {
        private val cookieStore = HashMap<String, MutableList<okhttp3.Cookie>>()

        @Synchronized
        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
            val host = url.host
            val currentCookies = cookieStore[host] ?: ArrayList()
            
            KLog.d("CookieJar: Saving ${cookies.size} cookies for $host")
            for (c in cookies) {
                KLog.d("CookieJar: + ${c.name}")
            }
            
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
            
            KLog.d("CookieJar: Loading ${validCookies.size} cookies for $host")
            if (validCookies.isNotEmpty()) {
                for (c in validCookies) {
                    KLog.d("CookieJar: -> ${c.name}")
                }
            } else {
                KLog.w("CookieJar: No cookies found for $host!")
            }
            
            return validCookies
        }

        fun addCookie(url: okhttp3.HttpUrl, cookie: okhttp3.Cookie) {
            saveFromResponse(url, listOf(cookie))
        }

        @Synchronized
        fun addRawCookies(url: okhttp3.HttpUrl, cookieString: String?) {
            if (cookieString == null) return
            val host = url.host
            val pairs = cookieString.split(";").map { it.trim() }
            val cookies = ArrayList<okhttp3.Cookie>()
            for (pair in pairs) {
                if (pair.isEmpty()) continue
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2) {
                    val cookie = okhttp3.Cookie.Builder()
                        .name(parts[0])
                        .value(parts[1])
                        .domain(host)
                        .path("/")
                        .build()
                    cookies.add(cookie)
                }
            }
            if (cookies.isNotEmpty()) {
                KLog.d("CookieJar: Injected ${cookies.size} raw cookies from WebView for $host")
                saveFromResponse(url, cookies)
            }
        }
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
            // 1. Try to fetch Turnstile Token (Cloudflare) via WebView on Main thread
            // This is CRITICAL: it also populates the CookieJar with Cloudflare cookies
            val turnstileToken = fetchTurnstileToken(boardUrl)
            
            // 2. Inject timerecord cookie manually if not already present
            val urlObj = boardUrl.toHttpUrlOrNull()
            if (urlObj != null) {
                val timerecord = okhttp3.Cookie.Builder()
                    .name("timerecord")
                    .value((System.currentTimeMillis() / 1000 - 300).toString())
                    .domain(urlObj.host)
                    .path("/")
                    .build()
                cookieJar.addCookie(urlObj, timerecord)
            }
            
            // 3. Execute reply task with tokens and synced cookies
            KomicaService.SendReplyTask(boardUrl, resto, name, email, subject, comment, turnstileToken).call()
        } catch (e: Exception) {
            KLog.e("Error sending reply: ${e.message}")
            false
        }
    }

    private suspend fun fetchTurnstileToken(url: String): String? = withContext(Dispatchers.Main) {
        KLog.d("WebView: Initializing for Cloudflare verification...")
        val webView = try {
            WebView(appContext)
        } catch (e: Exception) {
            KLog.e("WebView: Failed to create WebView: ${e.message}")
            return@withContext null
        }
        
        val urlObj = url.toHttpUrlOrNull()
        val boardBaseUrl = url.replace(Regex("pixmicat\\.php.*$"), "")

        // Token storage for JS interface
        var capturedToken: String? = null

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }

        // Inject a real JS interface to handle the callback reliably
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onTokenCaptured(token: String) {
                KLog.d("WebView: [Interface] Token captured via callback!")
                capturedToken = token
            }
            @JavascriptInterface
            fun log(msg: String) {
                KLog.d("WebView: [JS Log] $msg")
            }
        }, "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Define the expected callback globally before script runs
                view?.evaluateJavascript(
                    "window.onloadTurnstileCallback = function() { " +
                    "  AndroidBridge.log('Turnstile Callback Triggered'); " +
                    "  turnstile.render('#turnstile-container', { " +
                    "    callback: function(token) { AndroidBridge.onTokenCaptured(token); } " +
                    "  }); " +
                    "};", null)
            }

            override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                super.onPageFinished(view, loadedUrl)
                KLog.d("WebView: Page finished loading. Syncing cookies...")
                syncCookies(loadedUrl ?: url, urlObj)
            }
        }

        val extraHeaders = HashMap<String, String>()
        extraHeaders["Referer"] = boardBaseUrl
        
        KLog.d("WebView: Loading URL: $url with Referer: $boardBaseUrl")
        webView.loadUrl(url, extraHeaders)

        // Poll for token or interface capture
        val result = withTimeoutOrNull(25000) {
            for (i in 1..15) {
                delay(1500)
                
                // 1. Check interface capture
                if (!capturedToken.isNullOrEmpty()) {
                    KLog.d("WebView: [Success] Token acquired via interface!")
                    return@withTimeoutOrNull capturedToken
                }

                // 2. Fallback: Manual DOM poll
                val t = pollTurnstileToken(webView)
                if (!t.isNullOrEmpty() && t != "null" && t != "undefined") {
                    KLog.d("WebView: [Success] Token acquired via DOM poll!")
                    return@withTimeoutOrNull t
                }
                
                // 3. Sync cookies (looking for cf_clearance)
                if (syncCookies(url, urlObj)) {
                    KLog.d("WebView: [Target Found] cf_clearance cookie acquired!")
                }
                
                KLog.d("WebView: Polling verification status ($i/15)...")
            }
            null
        }

        webView.stopLoading()
        webView.destroy()
        result
    }

    private fun syncCookies(url: String, urlObj: okhttp3.HttpUrl?): Boolean {
        if (urlObj == null) return false
        val cookieManager = CookieManager.getInstance()
        val cookieStr = cookieManager.getCookie(url)
        cookieJar.addRawCookies(urlObj, cookieStr)
        return cookieStr?.contains("cf_clearance") == true
    }

    private suspend fun pollTurnstileToken(webView: WebView): String? {
        val deferred = CompletableDeferred<String?>()
        webView.evaluateJavascript("(function() { " +
                "var input = document.getElementsByName('cf-turnstile-response')[0];" +
                "return input ? input.value : null;" +
                "})();") { value ->
            // evaluateJavascript returns JSON wrapped string (e.g. "\"token\"")
            val cleanValue = value?.trim('\"')
            deferred.complete(if (cleanValue == "null" || cleanValue == "undefined") null else cleanValue)
        }
        return deferred.await()
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
