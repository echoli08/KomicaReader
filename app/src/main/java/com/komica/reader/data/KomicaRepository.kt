package com.komica.reader.data

import com.komica.reader.model.Board
import com.komica.reader.model.BoardCategory
import com.komica.reader.model.KomicaThread
import com.komica.reader.model.ThreadDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

class KomicaRepository(
    private val client: OkHttpClient = DefaultClient
) {
    private var boardCache: List<BoardCategory>? = null

    suspend fun LoadBoards(forceRefresh: Boolean = false): List<BoardCategory> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            boardCache?.let { return@withContext it }
        }

        val request = Request.Builder()
            .url("https://komica1.org/bbsmenu.html")
            .build()

        val result = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("看板選單讀取失敗：HTTP ${response.code}")
            val html = DecodeHtml(response.body, request.url.toString())
            KomicaParser.ParseBoards(html)
        }

        if (result.isEmpty()) error("看板選單解析結果為空")
        boardCache = result
        result
    }

    suspend fun LoadThreads(board: Board, page: Int = 0): List<KomicaThread> = withContext(Dispatchers.IO) {
        val pageUrl = BuildThreadPageUrl(board.url, page)
        val request = Request.Builder().url(pageUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("討論串讀取失敗：HTTP ${response.code}")
            val html = DecodeHtml(response.body, pageUrl)
            val threads = KomicaParser.ParseThreads(html, pageUrl)
            if (threads.isEmpty()) error("討論串解析結果為空")
            threads
        }
    }

    suspend fun LoadThreadDetail(threadUrl: String): ThreadDetail = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(threadUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("討論串內容讀取失敗：HTTP ${response.code}")
            val html = DecodeHtml(response.body, threadUrl)
            val detail = KomicaParser.ParseThreadDetail(html, threadUrl)
            if (detail.posts.isEmpty()) error("討論串內容解析結果為空")
            detail
        }
    }

    private fun DecodeHtml(body: ResponseBody?, url: String): String {
        val bytes = body?.bytes() ?: ByteArray(0)
        if (bytes.isEmpty()) return ""

        // 繁體中文註解：Komica 各看板不應靠網域硬判 Big5/UTF-8，優先使用 HTTP 與 HTML 宣告。
        val declaredCharset = body?.contentType()?.charset()
            ?: DetectMetaCharset(bytes)
        if (declaredCharset != null) {
            return String(bytes, declaredCharset)
        }

        return runCatching {
            Jsoup.parse(ByteArrayInputStream(bytes), null, url).outerHtml()
        }.getOrElse {
            String(bytes, Charsets.UTF_8)
        }
    }

    private fun DetectMetaCharset(bytes: ByteArray): Charset? {
        val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
        val charsetName = Regex("""(?i)charset\s*=\s*["']?([a-zA-Z0-9_\-]+)""")
            .find(head)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null
        return runCatching { Charset.forName(charsetName) }.getOrNull()
    }

    private fun BuildThreadPageUrl(boardUrl: String, page: Int): String {
        if (page <= 0) return boardUrl
        return when {
            boardUrl.endsWith("index.htm", ignoreCase = true) -> boardUrl.replace(Regex("index\\.htm$", RegexOption.IGNORE_CASE), "$page.htm")
            boardUrl.endsWith("index.html", ignoreCase = true) -> boardUrl.replace(Regex("index\\.html$", RegexOption.IGNORE_CASE), "$page.htm")
            boardUrl.endsWith("/") -> "$boardUrl$page.htm"
            boardUrl.contains("pixmicat.php", ignoreCase = true) -> boardUrl.substringBeforeLast('/') + "/$page.htm"
            else -> boardUrl.substringBeforeLast('/', missingDelimiterValue = boardUrl) + "/$page.htm"
        }
    }

    companion object {
        private val DefaultClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-TW,zh;q=0.8,en-US;q=0.5")
                    .build()
                chain.proceed(request)
            }
            .build()
    }
}
