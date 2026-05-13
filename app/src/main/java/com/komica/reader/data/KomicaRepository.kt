package com.komica.reader.data

import android.content.Context
import com.komica.reader.model.Board
import com.komica.reader.model.BoardCategory
import com.komica.reader.model.KomicaThread
import com.komica.reader.model.ReplyForm
import com.komica.reader.model.ReplySubmitRequest
import com.komica.reader.model.ReplySubmitResult
import com.komica.reader.model.ThreadDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    suspend fun LoadReplyForm(threadUrl: String): ReplyForm = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(threadUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("回覆表單讀取失敗：HTTP ${response.code}")
            val html = DecodeHtml(response.body, threadUrl)
            if (IsCloudflareChallenge(response.code, html)) error("網站要求 Cloudflare 驗證，請改用外部瀏覽器完成")
            KomicaParser.ParseReplyForm(html, threadUrl)
        }
    }

    suspend fun SubmitReply(context: Context, requestData: ReplySubmitRequest): ReplySubmitResult = withContext(Dispatchers.IO) {
        val form = requestData.form
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply {
                form.hiddenFields.forEach { (name, value) -> addFormDataPart(name, value) }
                AddTextField(form.nameField, requestData.name)
                AddTextField(form.emailField, requestData.email)
                AddTextField(form.titleField, requestData.title)
                AddTextField(form.contentField, requestData.content)
                AddTextField(form.passwordField, requestData.password)
                AddImagePart(context, form.fileField, requestData.imageUri)
            }
            .build()

        val request = Request.Builder()
            .url(form.actionUrl)
            .header("Referer", requestData.threadUrl)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val html = DecodeHtml(response.body, form.actionUrl)
            when {
                IsCloudflareChallenge(response.code, html) -> ReplySubmitResult(false, true, "網站要求 Cloudflare 驗證，請改用外部瀏覽器完成")
                response.isSuccessful -> ReplySubmitResult(true, false, ParseSubmitMessage(html).ifBlank { "回覆已送出" })
                else -> ReplySubmitResult(false, false, "回覆送出失敗：HTTP ${response.code}")
            }
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

    private fun MultipartBody.Builder.AddTextField(name: String, value: String) {
        if (name.isNotBlank() && value.isNotBlank()) addFormDataPart(name, value)
    }

    private fun MultipartBody.Builder.AddImagePart(context: Context, fieldName: String, imageUri: android.net.Uri?) {
        if (fieldName.isBlank() || imageUri == null) return
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(imageUri)?.use { it.readBytes() } ?: return
        val mimeType = resolver.getType(imageUri) ?: "application/octet-stream"
        val fileName = imageUri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "upload" }
        addFormDataPart(fieldName, fileName, bytes.toRequestBody(mimeType.toMediaTypeOrNull()))
    }

    private fun IsCloudflareChallenge(code: Int, html: String): Boolean {
        if (code == 403 || code == 429) return true
        return html.contains("cf-chl", ignoreCase = true) ||
            html.contains("cloudflare", ignoreCase = true) ||
            html.contains("Just a moment", ignoreCase = true) ||
            html.contains("Checking your browser", ignoreCase = true)
    }

    private fun ParseSubmitMessage(html: String): String {
        return Jsoup.parse(html).body()?.text()?.take(120).orEmpty()
    }

    companion object {
        private val DefaultClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .cookieJar(KomicaSession.CookieJar)
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
