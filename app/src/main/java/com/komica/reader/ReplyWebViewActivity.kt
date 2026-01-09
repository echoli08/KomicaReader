package com.komica.reader

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.komica.reader.databinding.ActivityReplyWebviewBinding
import java.io.ByteArrayInputStream

class ReplyWebViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReplyWebviewBinding
    private lateinit var prefs: SharedPreferences
    private var isSlimResourcesEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReplyWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        // 繁體中文註解：讀取回覆頁資源精簡設定，預設開啟
        isSlimResourcesEnabled = prefs.getBoolean(KEY_REPLY_WEBVIEW_SLIM, true)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.title_reply_webview)
        }

        val formUrl = intent.getStringExtra(EXTRA_FORM_URL)
        if (formUrl.isNullOrBlank()) {
            Toast.makeText(this, R.string.msg_reply_webview_missing_url, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupWebView(formUrl)
        setupBackPressed()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.replyWebView.canGoBack()) {
                    binding.replyWebView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    private fun setupWebView(formUrl: String) {
        val settings = binding.replyWebView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(binding.replyWebView, true)

        binding.replyWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.progressBar.visibility =
                    if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }

        binding.replyWebView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (!isSlimResourcesEnabled) return null
                if (request.isForMainFrame) return null
                val uri = request.url ?: return null
                if (shouldBlockResource(uri)) {
                    // 繁體中文註解：阻擋非必要的大型資源，讓回覆頁面載入更精簡
                    return createEmptyResponse()
                }
                return null
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // 繁體中文註解：頁面載入後檢查是否已回覆成功或被擋下
                checkReplyState()
            }
        }

        val referer = formUrl.substringBefore("pixmicat")
        binding.replyWebView.loadUrl(formUrl, mapOf("Referer" to referer))
    }

    private fun checkReplyState() {
        val js = """
            (function() {
              var t = document.body ? (document.body.innerText || '') : '';
              var hasForm = !!document.querySelector('form[action*=pixmicat]');
              var meta = document.querySelector('meta[http-equiv="refresh"]');
              if (meta && meta.content && meta.content.indexOf('pixmicat.php') >= 0) return 'SUCCESS';
              if (/Spambot|錯誤|失敗|Error/i.test(t)) return 'ERROR:' + t.substring(0, 80);
              if (hasForm) return 'FORM';
              if (t.indexOf('回到版面') >= 0 || t.indexOf('回上頁') >= 0) return 'SUCCESS';
              return 'UNKNOWN';
            })();
        """.trimIndent()

        binding.replyWebView.evaluateJavascript(js) { result ->
            val state = result?.trim('"') ?: return@evaluateJavascript
            when {
                state.startsWith("SUCCESS") -> {
                    // 繁體中文註解：成功後同步 Cookie 並回傳結果
                    CookieManager.getInstance().flush()
                    setResult(RESULT_OK)
                    Toast.makeText(this, R.string.msg_reply_success, Toast.LENGTH_SHORT).show()
                    finish()
                }
                state.startsWith("ERROR:") -> {
                    val msg = state.removePrefix("ERROR:")
                    Toast.makeText(this, getString(R.string.msg_error_prefix, msg), Toast.LENGTH_LONG).show()
                }
                else -> Unit
            }
        }
    }

    override fun onDestroy() {
        // 繁體中文註解：釋放 WebView 避免記憶體洩漏
        binding.replyWebView.stopLoading()
        binding.replyWebView.destroy()
        super.onDestroy()
    }

    private fun shouldBlockResource(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false

        val host = uri.host?.lowercase() ?: return false
        if (isCloudflareHost(host)) return false

        val path = (uri.path ?: "").lowercase()
        val query = (uri.query ?: "").lowercase()
        if (path.contains("captcha") || query.contains("captcha") ||
            path.contains("verify") || query.contains("verify")
        ) {
            return false
        }

        val ext = path.substringAfterLast('.', "")
        if (ext.isEmpty()) return false

        if (BLOCKED_FONT_EXTENSIONS.contains(ext)) return true
        if (BLOCKED_MEDIA_EXTENSIONS.contains(ext)) return true
        if (BLOCKED_IMAGE_EXTENSIONS.contains(ext)) return true

        return false
    }

    private fun isCloudflareHost(host: String): Boolean {
        return host == "cloudflare.com" ||
            host.endsWith(".cloudflare.com") ||
            host.endsWith(".cloudflareinsights.com")
    }

    private fun createEmptyResponse(): WebResourceResponse {
        val response = WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            response.setStatusCodeAndReasonPhrase(204, "No Content")
        }
        return response
    }

    companion object {
        private const val EXTRA_FORM_URL = "form_url"
        private const val PREFS_NAME = "KomicaReader"
        private const val KEY_REPLY_WEBVIEW_SLIM = "reply_webview_slim"
        private val BLOCKED_IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico"
        )
        private val BLOCKED_MEDIA_EXTENSIONS = setOf(
            "mp4", "webm", "mp3", "ogg", "wav", "flac", "m4a"
        )
        private val BLOCKED_FONT_EXTENSIONS = setOf(
            "woff", "woff2", "ttf", "otf", "eot"
        )

        fun createIntent(context: Context, formUrl: String): Intent {
            return Intent(context, ReplyWebViewActivity::class.java).apply {
                putExtra(EXTRA_FORM_URL, formUrl)
            }
        }
    }
}
