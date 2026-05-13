package com.komica.reader.data

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.ConcurrentHashMap

object KomicaSession {
    val CookieJar: CookieJar = SessionCookieJar()

    fun ImportWebViewCookies(url: String) {
        val httpUrl = url.toHttpUrl()
        val rawCookies = CookieManager.getInstance().getCookie(url).orEmpty()
        if (rawCookies.isBlank()) return
        val parsedCookies = rawCookies.split(';')
            .mapNotNull { Cookie.parse(httpUrl, it.trim()) }
        if (parsedCookies.isNotEmpty()) {
            CookieJar.saveFromResponse(httpUrl, parsedCookies)
        }
    }

    private class SessionCookieJar : CookieJar {
        private val cookies = ConcurrentHashMap<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val key = url.host
            val current = this.cookies.getOrPut(key) { mutableListOf() }
            cookies.forEach { newCookie ->
                current.removeAll { it.name == newCookie.name && it.domain == newCookie.domain && it.path == newCookie.path }
                current.add(newCookie)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            return cookies.values.flatten()
                .filter { it.expiresAt > now && url.host.endsWith(it.domain.removePrefix("."), ignoreCase = true) }
        }
    }
}
