package com.komica.reader.service

import com.komica.reader.model.BoardCategory
import com.komica.reader.model.Thread
import com.komica.reader.util.KLog
import com.komica.reader.util.KomicaParser
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

object KomicaService {
    private const val BASE_URL = "http://komica1.org"
    private const val MOBILE_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    @Volatile
    private var client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", MOBILE_USER_AGENT)
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
                )
                .header("Accept-Language", "zh-TW,zh;q=0.8,en-US;q=0.5,en;q=0.3")
                .method(original.method, original.body)
                .build()
            chain.proceed(request)
        }
        .build()

    @JvmStatic
    @Synchronized
    fun setClient(customClient: OkHttpClient) {
        client = customClient
    }

    class FetchBoardsTask : Callable<List<BoardCategory>> {
        override fun call(): List<BoardCategory> {
            val request = Request.Builder()
                .url("$BASE_URL/bbsmenu.html")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                val body = response.body ?: throw IOException("Empty body")
                val html = body.string()
                return KomicaParser.parseBoards(html)
            }
        }
    }

    class FetchThreadsTask(
        private val boardUrl: String,
        private val page: Int
    ) : Callable<List<Thread>> {
        override fun call(): List<Thread> {
            var url = boardUrl
            KLog.d("Original board URL: $url")

            if (page > 0) {
                url = when {
                    url.endsWith("index.htm") -> url.replace("index.htm", "$page.htm")
                    url.endsWith("index.html") -> url.replace("index.html", "$page.htm")
                    url.endsWith("/") -> url + "$page.htm"
                    url.contains(".php") -> url.substring(0, url.lastIndexOf("/")) + "/$page.htm"
                    else -> url + "/$page.htm"
                }
            }

            KLog.d("Fetching threads from: $url (page $page)")

            val request = Request.Builder()
                .url(url)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                val body = response.body ?: throw IOException("Empty body")
                val html = body.string()
                return KomicaParser.parseThreads(html, boardUrl)
            }
        }
    }

    class FetchThreadDetailTask(
        private val threadUrl: String
    ) : Callable<Thread> {
        override fun call(): Thread {
            KLog.d("Fetching thread detail from: $threadUrl")

            val request = Request.Builder()
                .url(threadUrl)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                val body = response.body ?: throw IOException("Empty body")
                val html = body.string()
                return KomicaParser.parseThreadDetail(html, threadUrl)
            }
        }
    }

    class BoardSearchTask(
        private val boardUrl: String,
        private val query: String
    ) : Callable<List<Thread>> {
        override fun call(): List<Thread> {
            var baseUrl = boardUrl
            baseUrl = baseUrl.replace("(index\\.html?|pixmicat\\.php)$".toRegex(), "")
            if (!baseUrl.endsWith("/")) baseUrl += "/"

            val searchUrl = baseUrl + "pixmicat.php"
            val isGaia = baseUrl.contains("gaia.komica1.org") || baseUrl.contains("sora.komica.org")
            val charsetName = if (isGaia) "Big5" else "UTF-8"

            KLog.d("Board Search POST URL: $searchUrl | Query: $query | Charset: $charsetName")

            val sb = StringBuilder()
            sb.append("mode=search")
            sb.append("&search_target=all")
            sb.append("&andor=and")
            sb.append("&keyword=").append(URLEncoder.encode(query, charsetName))
            sb.append("&search=").append(URLEncoder.encode("????", charsetName))

            val postBytes = sb.toString().toByteArray()
            val body = postBytes.toRequestBody("application/x-www-form-urlencoded".toMediaType())

            val request = Request.Builder()
                .url(searchUrl)
                .post(body)
                .header("Referer", baseUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body ?: throw IOException("Empty body")
                val responseBytes = responseBody.bytes()
                var doc = Jsoup.parse(ByteArrayInputStream(responseBytes), null, baseUrl)
                val title = doc.title()

                if (isGaia || title.contains("\uFFFD")) {
                    KLog.w("Encoding issue suspected, forcing Big5 decoding")
                    doc = Jsoup.parse(ByteArrayInputStream(responseBytes), "Big5", baseUrl)
                }

                var results = KomicaParser.parseSearchResults(doc, boardUrl)

                if (results.isEmpty()) {
                    KLog.d("Search results empty via POST, trying GET fallback...")
                    val getSearchUrl = baseUrl + "pixmicat.php?mode=search&keyword=" + URLEncoder.encode(query, "UTF-8")
                    val fallbackRequest = Request.Builder().url(getSearchUrl).build()

                    client.newCall(fallbackRequest).execute().use { fallbackResponse ->
                        if (fallbackResponse.isSuccessful) {
                            val fallbackBody = fallbackResponse.body
                            if (fallbackBody != null) {
                                val fallbackDoc = Jsoup.parse(fallbackBody.byteStream(), null, baseUrl)
                                results = KomicaParser.parseSearchResults(fallbackDoc, boardUrl)
                            }
                        }
                    }
                }
                return results
            }
        }
    }

    class SearchTask(
        private val query: String
    ) : Callable<List<Thread>> {
        override fun call(): List<Thread> {
            val searchUrl = BASE_URL + "/?mode=search&keyword=" + URLEncoder.encode(query, "UTF-8")
            KLog.d("Search URL: $searchUrl")

            val request = Request.Builder()
                .url(searchUrl)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                val body = response.body ?: throw IOException("Empty body")
                val doc = Jsoup.parse(body.byteStream(), null, BASE_URL)
                return KomicaParser.parseSearchResults(doc, BASE_URL)
            }
        }
    }

    class SendReplyTask @JvmOverloads constructor(
        private val boardUrl: String,
        private val resto: Int,
        private val name: String?,
        private val email: String?,
        private val subject: String?,
        private val comment: String?,
        private val turnstileToken: String? = null
    ) : Callable<Boolean> {
        override fun call(): Boolean {
            var baseUrl = boardUrl
            // Remove filename and query params from boardUrl to get base path
            baseUrl = baseUrl.replace("(index\\.html?|pixmicat\\.php.*)$".toRegex(), "")
            if (!baseUrl.endsWith("/")) baseUrl += "/"

            val postUrl = baseUrl + "pixmicat.php"
            val isGaia = baseUrl.contains("gaia.komica1.org") || baseUrl.contains("sora.komica.org")
            val charsetName = if (isGaia) "Big5" else "UTF-8"
            val charset = Charset.forName(charsetName)

            KLog.d("Send Reply POST URL: $postUrl | Resto: $resto | Charset: $charsetName")

            val hiddenFields = HashMap<String, String>()
            var submitFieldName: String? = null
            var submitFieldValue: String? = null

            // ???????????????? Cookie ?????????
            val host = URL(postUrl).host
            KLog.w("Performing warm-up request sequence...")

            val indexUrl = baseUrl + "index.htm"
            val indexRequest = Request.Builder()
                .url(indexUrl)
                .header("User-Agent", MOBILE_USER_AGENT)
                .build()

            try {
                client.newCall(indexRequest).execute().use { response ->
                    KLog.d("Index warm-up status: ${response.code}")
                }
            } catch (e: Exception) {
                KLog.e("Index warm-up failed: ${e.message}")
            }

            try {
                java.lang.Thread.sleep(1000)
            } catch (_: InterruptedException) {
            }

            val formUrl = baseUrl + "pixmicat.php?res=$resto"
            val formRequest = Request.Builder()
                .url(formUrl)
                .header("User-Agent", MOBILE_USER_AGENT)
                .header("Referer", indexUrl)
                .build()

            try {
                client.newCall(formRequest).execute().use { response ->
                    KLog.d("Form warm-up status: ${response.code}")
                    val responseBody = response.body
                    if (response.isSuccessful && responseBody != null) {
                        val responseBytes = responseBody.bytes()
                        val doc = Jsoup.parse(ByteArrayInputStream(responseBytes), charsetName, formUrl)
                        val form = doc.selectFirst("form[action*=pixmicat]")
                        if (form != null) {
                            val inputs: Elements = form.select("input[type=hidden]")
                            for (input in inputs) {
                                val fieldName = input.attr("name")
                                if (fieldName.isNotEmpty()) {
                                    hiddenFields[fieldName] = input.attr("value")
                                }
                            }
                            // ??????????????????????????
                            val submit = form.selectFirst("input[type=submit]")
                            if (submit != null) {
                                val value = submit.attr("value")
                                if (value.isNotEmpty()) {
                                    submitFieldName = submit.attr("name")
                                    submitFieldValue = value
                                }
                            }
                            KLog.d("Parsed hidden fields count: ${hiddenFields.size}")
                        } else {
                            KLog.w("Reply form not found for hidden field parsing")
                        }
                    }
                }
            } catch (e: Exception) {
                KLog.e("Form warm-up failed: ${e.message}")
            }

            val postHttpUrl = postUrl.toHttpUrl()
            var cookies = client.cookieJar.loadForRequest(postHttpUrl)
            var currentTimerecordValue = 0L
            for (c in cookies) {
                if (c.name == "timerecord") {
                    currentTimerecordValue = c.value.toLongOrNull() ?: 0L
                    break
                }
            }

            val now = System.currentTimeMillis() / 1000
            val minTimerecordAge = 120L

            if (currentTimerecordValue == 0L || (now - currentTimerecordValue) < minTimerecordAge) {
                KLog.w("timerecord missing or too fresh. Injecting fresh value (target age: ${minTimerecordAge}s)...")
                val freshTime = now - minTimerecordAge
                val freshCookie = Cookie.Builder()
                    .name("timerecord")
                    .value(freshTime.toString())
                    .domain(host)
                    .path("/")
                    .build()

                client.cookieJar.saveFromResponse(postHttpUrl, listOf(freshCookie))
                KLog.d("Injected fresh timerecord: $freshTime (age: ${minTimerecordAge}s)")
            } else {
                KLog.d("Existing timerecord is acceptable (age: ${now - currentTimerecordValue}s)")
            }

            KLog.d("=== FINAL COOKIE STATE ===")
            cookies = client.cookieJar.loadForRequest(postHttpUrl)
            for (c in cookies) {
                KLog.d("Cookie: ${c.name}=${c.value} (domain=${c.domain}, path=${c.path})")
            }
            for (c in cookies) {
                if (c.name == "timerecord") {
                    val age = now - (c.value.toLongOrNull() ?: now)
                    KLog.d("timerecord age at POST time: $age seconds")
                }
            }

            try {
                KLog.d("Simulating user typing delay, sleeping for 8000ms...")
                java.lang.Thread.sleep(8000)
            } catch (_: InterruptedException) {
            }

            val builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)

            val postFields = HashMap(hiddenFields)

            postFields["mode"] = "regist"
            postFields["resto"] = resto.toString()
            postFields["name"] = name ?: ""
            postFields["email"] = email ?: ""
            postFields["sub"] = subject ?: ""
            postFields["com"] = comment ?: ""
            postFields["pwd"] = "komicareader"
            postFields["noimg"] = "on"

            val maxFileSize = postFields["MAX_FILE_SIZE"] ?: "5242880"
            val upfilePath = postFields["upfile_path"] ?: ""
            postFields["MAX_FILE_SIZE"] = maxFileSize
            postFields["upfile_path"] = upfilePath

            if (!submitFieldValue.isNullOrEmpty()) {
                val submitName = if (!submitFieldName.isNullOrEmpty()) submitFieldName!! else "send"
                postFields[submitName] = submitFieldValue!!
                if (submitName != "send") {
                    postFields.remove("send")
                }
            } else {
                var sendText = postFields["send"] ?: ""
                if (sendText.isEmpty()) {
                    sendText = if (isGaia) "?????" else "Submit"
                }
                postFields["send"] = sendText
            }

            if (!turnstileToken.isNullOrEmpty()) {
                postFields["cf-turnstile-response"] = turnstileToken
                KLog.d("Added Turnstile Token length: ${turnstileToken.length}")
            } else {
                postFields.remove("cf-turnstile-response")
                KLog.w("Warning: No Turnstile Token provided for reply. This might cause 503 error on protected boards.")
            }

            for ((key, value) in postFields) {
                if (key == "upfile") continue
                addStringPart(builder, key, value, charset)
            }

            builder.addFormDataPart(
                "upfile",
                "",
                ByteArray(0).toRequestBody("application/octet-stream".toMediaType())
            )

            val body = builder.build()

            var origin = "https://komica1.org"
            try {
                val url = URL(postUrl)
                origin = url.protocol + "://" + url.host
            } catch (_: Exception) {
            }

            val refererUrl = baseUrl + "pixmicat.php?res=$resto"

            val request = Request.Builder()
                .url(postUrl)
                .post(body)
                .header("Referer", refererUrl)
                .header("Origin", origin)
                .header("Sec-Fetch-Site", "same-origin")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-User", "?1")
                .header("Sec-Fetch-Dest", "document")
                .header("User-Agent", MOBILE_USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 302 || response.code == 303) {
                    KLog.d("Reply success (Redirect)")
                    return true
                }

                val responseBytes = response.body?.bytes() ?: ByteArray(0)
                KLog.d("Reply response code: ${response.code}")

                val hex = StringBuilder()
                for (i in 0 until minOf(32, responseBytes.size)) {
                    hex.append(String.format("%02X ", responseBytes[i]))
                }
                KLog.d("Response Hex (First 32 bytes): $hex")

                var responseBody = String(responseBytes, charset)

                if (isGaia && (responseBody.contains("\uFFFD") || responseBody.contains("Spambot") || responseBody.contains("Cloudflare"))) {
                    try {
                        val utf8Body = String(responseBytes, Charsets.UTF_8)
                        if (!utf8Body.contains("\uFFFD")) {
                            responseBody = utf8Body
                            KLog.d("Heuristic: Switched to UTF-8 decoding for response")
                        }
                    } catch (_: Exception) {
                    }
                }

                if (response.isSuccessful) {
                    val hasMetaRefresh = responseBody.contains("meta http-equiv=\"refresh\"") && responseBody.contains("pixmicat.php")
                    val hasSuccessText = responseBody.contains("?????????") ||
                        responseBody.contains("?????????") ||
                        responseBody.contains("????")

                    if (hasMetaRefresh || hasSuccessText) {
                        KLog.d("Reply success (Verified content)")
                        return true
                    }

                    if (responseBody.contains("?????") || responseBody.contains("?????") ||
                        responseBody.contains("Error") || responseBody.contains("Spambot")
                    ) {
                        KLog.w("Reply failed (Error keywords found)")
                        logErrorBody(responseBody)
                        return false
                    }

                    KLog.w("Reply failed (No success indicator found). Logging preview:")
                    logErrorBody(responseBody)
                    return false
                } else if (response.code == 503 || response.code == 403) {
                    KLog.w("Reply blocked by WAF/Cloudflare (Code ${response.code})")
                    logErrorBody(responseBody)
                }

                KLog.w("Reply response failed, code: ${response.code}")
                return false
            }
        }

        private fun addStringPart(builder: MultipartBody.Builder, name: String, value: String, charset: Charset) {
            val logValue = if (name == "pwd") "***" else value
            try {
                if (logValue.length < 20 && logValue.contains("??")) {
                    // no-op
                }
            } catch (_: Exception) {
            }

            KLog.d("Adding form field: $name = $logValue")
            val mediaType = "text/plain; charset=${charset.name()}".toMediaType()
            val body = value.toByteArray(charset).toRequestBody(mediaType)
            builder.addFormDataPart(name, null, body)
        }

        private fun logErrorBody(body: String) {
            try {
                val doc = Jsoup.parse(body)
                var text = doc.text()
                if (text.length > 200) text = text.substring(0, 200)
                KLog.w("Response text preview: $text")
            } catch (_: Exception) {
                if (body.length > 200) {
                    KLog.w("Response raw preview: ${body.substring(0, 200)}")
                } else {
                    KLog.w("Response raw preview: $body")
                }
            }
        }
    }

    @JvmStatic
    fun resolveUrl(baseUrl: String, href: String?): String {
        return KomicaParser.resolveUrl(baseUrl, href)
    }
}
