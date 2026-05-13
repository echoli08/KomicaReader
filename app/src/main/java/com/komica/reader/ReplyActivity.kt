package com.komica.reader

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.komica.reader.data.AppSettings
import com.komica.reader.databinding.ActivityReplyBinding
import com.komica.reader.util.WindowInsetsUtil
import java.io.ByteArrayInputStream

class ReplyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReplyBinding
    private lateinit var settings: AppSettings

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        settings = AppSettings(this)
        settings.ApplyTheme()
        super.onCreate(savedInstanceState)
        binding = ActivityReplyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsUtil.ApplyToolbarInsets(binding.toolbar)

        val url = intent.getStringExtra(ExtraUrl).orEmpty()
        if (url.isBlank()) {
            finish()
            return
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = intent.getStringExtra(ExtraTitle).orEmpty().ifBlank { "回覆" }
        ConfigureWebView()
        ConfigureBackNavigation()
        binding.replyWebView.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ConfigureWebView() {
        binding.replyWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        binding.replyWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val normalizedUrl = NormalizeKomicaUrl(request.url.toString())
                if (request.isForMainFrame && IsBoardIndexUrl(normalizedUrl)) {
                    CompleteReplyFlow()
                    return true
                }
                if (normalizedUrl != request.url.toString()) {
                    view.loadUrl(normalizedUrl)
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (!settings.slimReplyPage) return null
                val url = NormalizeKomicaUrl(request.url.toString())
                if (IsCaptchaOrMainPage(url)) return null
                val accept = request.requestHeaders["Accept"].orEmpty()
                if (accept.startsWith("image/") || accept.contains("video/") || accept.contains("audio/")) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                binding.progressBar.visibility = View.VISIBLE
                val normalizedUrl = url?.let { NormalizeKomicaUrl(it) }.orEmpty()
                if (IsBoardIndexUrl(normalizedUrl)) {
                    CompleteReplyFlow()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBar.visibility = View.GONE
                RewriteLegacyKomicaHosts(view)
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame && errorResponse.statusCode == 522 && IsBoardIndexUrl(NormalizeKomicaUrl(request.url.toString()))) {
                    CompleteReplyFlow()
                    return
                }
                super.onReceivedHttpError(view, request, errorResponse)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame && IsBoardIndexUrl(NormalizeKomicaUrl(request.url.toString()))) {
                    CompleteReplyFlow()
                    return
                }
                super.onReceivedError(view, request, error)
            }
        }
    }

    private fun RewriteLegacyKomicaHosts(view: WebView?) {
        view?.evaluateJavascript(
            """
            (function() {
                document.querySelectorAll('form[action], a[href]').forEach(function(node) {
                    var attr = node.tagName.toLowerCase() === 'form' ? 'action' : 'href';
                    var value = node.getAttribute(attr);
                    if (value) {
                        node.setAttribute(attr, value.replace(/\.komica\.org/g, '.komica1.org'));
                    }
                });
            })();
            """.trimIndent(),
            null
        )
    }

    private fun ConfigureBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.replyWebView.canGoBack()) {
                    binding.replyWebView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun IsCaptchaOrMainPage(url: String): Boolean {
        return url.endsWith(".php", ignoreCase = true) ||
            url.contains("captcha", ignoreCase = true) ||
            url.contains("regist", ignoreCase = true) ||
            url.contains("pixmicat", ignoreCase = true)
    }

    private fun IsBoardIndexUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty()
        val path = uri.path.orEmpty()
        return host.contains("komica", ignoreCase = true) &&
            (path.endsWith("/index.htm", ignoreCase = true) || path.endsWith("/index.html", ignoreCase = true))
    }

    private fun CompleteReplyFlow() {
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun NormalizeKomicaUrl(url: String): String {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        val host = uri.host.orEmpty()
        if (!host.endsWith(".komica.org", ignoreCase = true)) return url
        val fixedHost = host.replace(".komica.org", ".komica1.org", ignoreCase = true)
        return uri.buildUpon().authority(fixedHost).build().toString()
    }

    companion object {
        const val ExtraUrl = "extra_url"
        const val ExtraTitle = "extra_title"
    }
}
