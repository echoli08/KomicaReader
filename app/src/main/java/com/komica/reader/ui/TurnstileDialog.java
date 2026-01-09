package com.komica.reader.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import com.komica.reader.R;
import com.komica.reader.util.KLog;

public class TurnstileDialog extends Dialog {

    private static final int MAX_CHECK = 120; // 120 times * 1.5s = 3 minutes timeout
    private static final int CHECK_INTERVAL = 1500;
    private static final int TOKEN_MIN_LENGTH = 10;
    private int checkCount = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onVerified(String token);
        void onCancelled();
        void onError(String error);
    }

    private WebView webView;
    private ProgressBar progressBar;
    private Callback callback;
    private String formUrl;
    private boolean isFinished = false;
    private boolean isFirstLoad = true;

    public TurnstileDialog(Context context, String formUrl, Callback callback) {
        super(context, android.R.style.Theme_Material_Light_Dialog_Alert);
        this.formUrl = formUrl;
        this.callback = callback;
        init();
    }

    private void init() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View view = inflater.inflate(R.layout.dialog_turnstile_verification, null);
        webView = view.findViewById(R.id.turnstileWebView);
        progressBar = view.findViewById(R.id.progressBar);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        btnCancel.setText("?–æ?");

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                KLog.d("WebView Console: " + cm.message());
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                KLog.d("WebView finish: " + url);

                // Fix: Inject onload callback to prevent Cloudflare Turnstile error
                // "Unable to find onload callback 'onloadTurnstileCallback'"
                view.evaluateJavascript("window.onloadTurnstileCallback = function() { console.log('Turnstile loaded callback triggered'); };", null);

                if (isFirstLoad) {
                    isFirstLoad = false;
                }
                // 繁體中文註解：頁面載入後統一進入狀態檢查流程
                checkPageState();
            }

            @Override
            public void onReceivedError(WebView view, int ec, String ds, String fu) {
                KLog.e("WebView Error: " + ds);
            }
        });

        btnCancel.setOnClickListener(v -> { dismiss(); if (callback != null) callback.onCancelled(); });
        setContentView(view);
        setCancelable(false);
        setCanceledOnTouchOutside(false);

        java.util.Map<String, String> extraHeaders = new java.util.HashMap<>();
        extraHeaders.put("Referer", formUrl.split("pixmicat")[0]);
        webView.loadUrl(formUrl, extraHeaders);
    }

    private void checkPageState() {
        if (isFinished) return;
        String js = "(function() { " +
                "  var tokenEl = document.querySelector('input[name=cf-turnstile-response], textarea[name=cf-turnstile-response]'); " +
                "  if (tokenEl) { " +
                "    var token = tokenEl.value || ''; " +
                "    if (token.length > " + TOKEN_MIN_LENGTH + ") return 'TOKEN:' + token; " +
                "    return 'WAIT_TOKEN'; " +
                "  } " +
                "  var t = document.body.innerText || ''; " +
                "  if (t.includes('?‡ç??å‡º') || t.includes('?žæ??å‡º') || t.includes('?å?')) return 'SUCCESS'; " +
                "  if (t.includes('Spambot') || t.includes('?¯èª¤') || t.includes('Error')) return 'ERROR:' + t.substring(0, 50); " +
                "  var ifs = document.querySelectorAll('iframe'); " +
                "  for(var i=0; i<ifs.length; i++) { if(ifs[i].src && ifs[i].src.includes('cloudflare')) return 'CHALLENGE'; } " +
                "  var cbs = document.querySelectorAll('input[type=checkbox]'); " +
                "  for(var j=0; j<cbs.length; j++) { " +
                "      var pt = cbs[j].parentElement ? cbs[j].parentElement.innerText : ''; " +
                "      if(/é©—è?|äºº|human/i.test(pt)) return 'CHALLENGE'; " +
                "  } " +
                "  var f = document.querySelector('form[action*=pixmicat]'); " +
                "  if (f && (f.querySelector('textarea[name=com]') || f.querySelector('input[name=com]'))) return 'FORM'; " +
                "  return 'LOADING'; " +
                "})();";

        webView.evaluateJavascript(js, result -> {
            if (result == null || isFinished) return;
            String state = result.replace("\"", "");
            String safeState = state.startsWith("TOKEN:")
                    ? "TOKEN(" + state.substring("TOKEN:".length()).length() + ")"
                    : state;
            // 繁體中文註解：避免在 Log 中輸出完整驗證 Token
            KLog.d("V4.2 State: " + safeState);

            if (state.startsWith("TOKEN:")) {
                String token = state.substring("TOKEN:".length());
                notifyVerified(token);
            } else if (state.equals("FORM") || state.equals("SUCCESS")) {
                notifyVerified("");
            } else if (state.equals("WAIT_TOKEN") || state.equals("CHALLENGE")) {
                progressBar.setVisibility(View.GONE);
                scheduleNextCheck();
            } else if (state.startsWith("ERROR")) {
                handleError(state);
            } else if (state.equals("LOADING")) {
                progressBar.setVisibility(View.VISIBLE);
                scheduleNextCheck();
            }
        });
    }

    private void scheduleNextCheck() {
        if (checkCount >= MAX_CHECK) {
            handleError("Timeout");
            return;
        }
        checkCount++;
        handler.postDelayed(this::checkPageState, CHECK_INTERVAL);
    }

    private void notifyVerified(String token) {
        if (isFinished) return;
        isFinished = true;
        handler.removeCallbacksAndMessages(null);
        // 繁體中文註解：同步 WebView Cookie，讓 OkHttp 發文可以沿用驗證狀態
        android.webkit.CookieManager.getInstance().flush();
        if (callback != null) callback.onVerified(token);
        dismiss();
    }

    private void handleError(String error) {
        if (isFinished) return;
        isFinished = true;
        handler.removeCallbacksAndMessages(null);
        KLog.e("Post failed: " + error);
        if (callback != null) callback.onError(error);
        dismiss();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacksAndMessages(null);
        isFinished = true;
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
    }
}
