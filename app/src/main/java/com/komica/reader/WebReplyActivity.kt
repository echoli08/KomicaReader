package com.komica.reader

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.komica.reader.data.AppSettings
import com.komica.reader.data.KomicaSession
import com.komica.reader.databinding.ActivityWebReplyBinding
import com.komica.reader.util.WindowInsetsUtil

class WebReplyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWebReplyBinding
    private var url = ""
    private var hasLoadedReplyPage = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings(this).ApplyTheme()
        super.onCreate(savedInstanceState)
        binding = ActivityWebReplyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsUtil.ApplyToolbarInsets(binding.toolbar)

        url = intent.getStringExtra(ExtraUrl).orEmpty()
        if (url.isBlank()) {
            finish()
            return
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = intent.getStringExtra(ExtraTitle).orEmpty().ifBlank { "網站回覆" }
        ConfigureBackNavigation()
        ConfigureWebView()
        binding.replyWebView.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ConfigureWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.replyWebView, true)
        binding.replyWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
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

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                binding.progressBar.visibility = View.VISIBLE
                binding.statusText.text = ""
                binding.statusText.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                binding.progressBar.visibility = View.GONE
                pageUrl?.let { KomicaSession.ImportWebViewCookies(it) }
                if (pageUrl?.contains("pixmicat", ignoreCase = true) == true || pageUrl == url) {
                    hasLoadedReplyPage = true
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame && errorResponse.statusCode == 503) {
                    if (hasLoadedReplyPage) {
                        CompleteReplyFlow()
                        return
                    }
                    binding.statusText.visibility = View.VISIBLE
                    binding.statusText.text = "網站目前拒絕 WebView 連線，請返回討論串後稍後重試。"
                    return
                }
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
                if (request.isForMainFrame) {
                    binding.statusText.visibility = View.VISIBLE
                    binding.statusText.text = "網站回覆頁載入失敗，請返回討論串後稍後重試。"
                }
                super.onReceivedError(view, request, error)
            }
        }
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

    private fun IsBoardIndexUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty()
        val path = uri.path.orEmpty()
        return host.contains("komica", ignoreCase = true) &&
            (path.endsWith("/index.htm", ignoreCase = true) || path.endsWith("/index.html", ignoreCase = true))
    }

    private fun NormalizeKomicaUrl(url: String): String {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        val host = uri.host.orEmpty()
        if (!host.endsWith(".komica.org", ignoreCase = true)) return url
        val fixedHost = host.replace(".komica.org", ".komica1.org", ignoreCase = true)
        return uri.buildUpon().authority(fixedHost).build().toString()
    }

    private fun CompleteReplyFlow() {
        CookieManager.getInstance().flush()
        KomicaSession.ImportWebViewCookies(binding.replyWebView.url ?: url)
        setResult(Activity.RESULT_OK)
        finish()
    }

    companion object {
        const val ExtraUrl = "extra_url"
        const val ExtraTitle = "extra_title"
    }
}
