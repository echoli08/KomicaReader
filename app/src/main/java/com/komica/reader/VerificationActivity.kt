package com.komica.reader

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.komica.reader.data.AppSettings
import com.komica.reader.data.KomicaSession
import com.komica.reader.databinding.ActivityVerificationBinding
import com.komica.reader.util.WindowInsetsUtil

class VerificationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVerificationBinding
    private var url = ""
    private var currentUrl = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings(this).ApplyTheme()
        super.onCreate(savedInstanceState)
        binding = ActivityVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsUtil.ApplyToolbarInsets(binding.toolbar)

        url = intent.getStringExtra(ExtraUrl).orEmpty()
        currentUrl = url
        if (url.isBlank()) {
            finish()
            return
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.doneButton.setOnClickListener { CompleteVerification() }
        ConfigureWebView()
        binding.verificationWebView.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ConfigureWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.verificationWebView, true)
        binding.verificationWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
        }
        binding.verificationWebView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                binding.progressBar.visibility = View.GONE
                currentUrl = pageUrl.orEmpty().ifBlank { currentUrl }
                KomicaSession.ImportWebViewCookies(currentUrl)
            }
        }
    }

    private fun CompleteVerification() {
        CookieManager.getInstance().flush()
        KomicaSession.ImportWebViewCookies(currentUrl)
        KomicaSession.ImportWebViewCookies(url)
        setResult(Activity.RESULT_OK)
        finish()
    }

    companion object {
        const val ExtraUrl = "extra_url"
    }
}
