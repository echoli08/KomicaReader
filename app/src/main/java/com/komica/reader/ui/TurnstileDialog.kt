package com.komica.reader.ui

import android.app.Dialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.komica.reader.R
import com.komica.reader.databinding.DialogTurnstileVerificationBinding
import com.komica.reader.util.KLog

class TurnstileDialog(
    context: Context,
    private val formUrl: String,
    private val callback: Callback?
) : Dialog(context, android.R.style.Theme_Material_Light_Dialog_Alert) {

    interface Callback {
        fun onVerified(token: String)
        fun onCancelled()
        fun onError(error: String)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var checkCount = 0
    private var isFinished = false
    private var isFirstLoad = true
    private val binding = DialogTurnstileVerificationBinding.inflate(LayoutInflater.from(context))

    init {
        initView()
    }

    private fun initView() {
        binding.btnCancel.setText(R.string.action_cancel)
        binding.btnCancel.setOnClickListener {
            dismiss()
            callback?.onCancelled()
        }

        val settings = binding.turnstileWebView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(binding.turnstileWebView, true)

        binding.turnstileWebView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                KLog.d("WebView Console: ${cm.message()}")
                return true
            }
        }

        binding.turnstileWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                KLog.d("WebView finish: $url")

                // Fix: Inject onload callback to prevent Cloudflare Turnstile error
                // "Unable to find onload callback 'onloadTurnstileCallback'"
                view.evaluateJavascript(
                    "window.onloadTurnstileCallback = function() { console.log('Turnstile loaded callback triggered'); };",
                    null
                )

                if (isFirstLoad) {
                    isFirstLoad = false
                }
                // 繁體中文註解：頁面載入後檢查驗證狀態
                checkPageState()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                KLog.e("WebView Error: ${error.description}")
            }
        }

        setContentView(binding.root)
        setCancelable(false)
        setCanceledOnTouchOutside(false)

        val referer = formUrl.substringBefore("pixmicat")
        val extraHeaders = hashMapOf("Referer" to referer)
        binding.turnstileWebView.loadUrl(formUrl, extraHeaders)
    }

    private fun checkPageState() {
        if (isFinished) return
        val js = """
            (function() {
              var tokenEl = document.querySelector('input[name=cf-turnstile-response], textarea[name=cf-turnstile-response]');
              if (tokenEl) {
                var token = tokenEl.value || '';
                if (token.length > ${TOKEN_MIN_LENGTH}) return 'TOKEN:' + token;
                return 'WAIT_TOKEN';
              }
              var t = document.body.innerText || '';
              if (t.includes('??¡Ã???Ã¥?¡Â?) || t.includes('?Å¾Ã¦???Ã¥?¡Â?) || t.includes('??Ã¥?')) return 'SUCCESS';
              if (t.includes('Spambot') || t.includes('?Â¯Ã¨ÂªÂ¤') || t.includes('Error')) return 'ERROR:' + t.substring(0, 50);
              var ifs = document.querySelectorAll('iframe');
              for(var i=0; i<ifs.length; i++) { if(ifs[i].src && ifs[i].src.includes('cloudflare')) return 'CHALLENGE'; }
              var cbs = document.querySelectorAll('input[type=checkbox]');
              for(var j=0; j<cbs.length; j++) {
                  var pt = cbs[j].parentElement ? cbs[j].parentElement.innerText : '';
                  if(/Ã©Â©?”Ã?|Ã¤ÂºÂº|human/i.test(pt)) return 'CHALLENGE';
              }
              var f = document.querySelector('form[action*=pixmicat]');
              if (f && (f.querySelector('textarea[name=com]') || f.querySelector('input[name=com]'))) return 'FORM';
              return 'LOADING';
            })();
        """.trimIndent()

        binding.turnstileWebView.evaluateJavascript(js) { result ->
            if (result == null || isFinished) return@evaluateJavascript
            val state = result.replace("\"", "")
            val safeState = if (state.startsWith("TOKEN:")) {
                "TOKEN(" + state.substring("TOKEN:".length).length + ")"
            } else {
                state
            }
            // 繁體中文註解：避免在 Log 中輸出完整 Token
            KLog.d("V4.2 State: $safeState")

            when {
                state.startsWith("TOKEN:") -> {
                    val token = state.substring("TOKEN:".length)
                    notifyVerified(token)
                }
                state == "FORM" || state == "SUCCESS" -> {
                    notifyVerified("")
                }
                state == "WAIT_TOKEN" || state == "CHALLENGE" -> {
                    binding.progressBar.visibility = View.GONE
                    scheduleNextCheck()
                }
                state.startsWith("ERROR") -> {
                    handleError(state)
                }
                state == "LOADING" -> {
                    binding.progressBar.visibility = View.VISIBLE
                    scheduleNextCheck()
                }
            }
        }
    }

    private fun scheduleNextCheck() {
        if (checkCount >= MAX_CHECK) {
            handleError("Timeout")
            return
        }
        checkCount++
        handler.postDelayed({ checkPageState() }, CHECK_INTERVAL)
    }

    private fun notifyVerified(token: String) {
        if (isFinished) return
        isFinished = true
        handler.removeCallbacksAndMessages(null)
        // 繁體中文註解：同步 WebView Cookie 給後續請求
        CookieManager.getInstance().flush()
        callback?.onVerified(token)
        dismiss()
    }

    private fun handleError(error: String) {
        if (isFinished) return
        isFinished = true
        handler.removeCallbacksAndMessages(null)
        KLog.e("Post failed: $error")
        callback?.onError(error)
        dismiss()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
        isFinished = true
        binding.turnstileWebView.stopLoading()
        binding.turnstileWebView.destroy()
    }

    companion object {
        private const val MAX_CHECK = 120
        private const val CHECK_INTERVAL = 1500L
        private const val TOKEN_MIN_LENGTH = 10
    }
}
