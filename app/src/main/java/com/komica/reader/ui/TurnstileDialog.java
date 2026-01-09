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
import android.widget.Toast;
import com.komica.reader.R;
import com.komica.reader.util.KLog;

public class TurnstileDialog extends Dialog {

    private static final int MAX_CHECK = 120; // 120 times * 1.5s = 3 minutes timeout
    private static final int CHECK_INTERVAL = 1500;
    private int checkCount = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable monitoringRunnable;

    public interface Callback {
        void onSuccess();
        void onTokenReceived(String token);
        void onCancelled();
        void onError(String error);
    }

    private WebView webView;
    private ProgressBar progressBar;
    private Callback callback;
    private String formUrl;
    private boolean isFinished = false;
    private boolean isFirstLoad = true;
    private String name, email, subject, comment;

    public TurnstileDialog(Context context, String formUrl,
                           String name, String email, String subject, String comment,
                           Callback callback) {
        super(context, android.R.style.Theme_Material_Light_Dialog_Alert);
        this.formUrl = formUrl;
        this.name = name;
        this.email = email;
        this.subject = subject;
        this.comment = comment;
        this.callback = callback;
        init();
    }

    public TurnstileDialog(Context context, String formUrl, Callback callback) {
        this(context, formUrl, "", "", "", "", callback);
    }

    private void init() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View view = inflater.inflate(R.layout.dialog_turnstile_verification, null);
        webView = view.findViewById(R.id.turnstileWebView);
        progressBar = view.findViewById(R.id.progressBar);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        btnCancel.setText("取消"); 

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
                    checkPageState(); 
                } else { 
                    checkSubmissionStatus(); 
                    checkPageState(); 
                } 
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
                "  var t = document.body.innerText || ''; " +
                "  if (t.includes('文章送出') || t.includes('回應送出') || t.includes('成功')) return 'SUCCESS'; " +
                "  if (t.includes('Spambot') || t.includes('錯誤') || t.includes('Error')) return 'ERROR:' + t.substring(0, 50); " +
                "  var ifs = document.querySelectorAll('iframe'); " +
                "  for(var i=0; i<ifs.length; i++) { if(ifs[i].src.includes('cloudflare')) return 'CHALLENGE'; } " +
                "  var cbs = document.querySelectorAll('input[type=checkbox]'); " +
                "  for(var j=0; j<cbs.length; j++) { " +
                "      var pt = cbs[j].parentElement ? cbs[j].parentElement.innerText : ''; " +
                "      if(/驗證|人|human/i.test(pt)) return 'CHALLENGE'; " +
                "  } " +
                "  var f = document.querySelector('form[action*=pixmicat]'); " +
                "  if (f && (f.querySelector('textarea[name=com]') || f.querySelector('input[name=com]'))) return 'FORM'; " +
                "  return 'LOADING'; " +
                "})();";

        webView.evaluateJavascript(js, result -> {
            if (result == null || isFinished) return;
            String state = result.replace("\"", "");
            KLog.d("V4.2 State: " + state);
            
            if (state.equals("CHALLENGE")) {
                progressBar.setVisibility(View.GONE);
                clickCloudflareCheckbox();
                startMonitoring();
            } else if (state.equals("FORM")) {
                stopMonitoring();
                injectReplyLogic(0); // Start robust injection
            } else if (state.equals("SUCCESS")) {
                stopMonitoring();
                handleSuccess();
            } else if (state.startsWith("ERROR")) {
                stopMonitoring();
                KLog.e("Post failed: " + state);
                if (callback != null) callback.onError(state);
                dismiss();
            } else if (state.equals("LOADING")) {
                if (checkCount >= MAX_CHECK) {
                    stopMonitoring();
                    KLog.e("Timeout waiting for page load");
                    if (callback != null) callback.onError("Timeout");
                    dismiss();
                } else {
                    checkCount++;
                    handler.postDelayed(this::checkPageState, CHECK_INTERVAL);
                }
            }
        });
    }

    private void clickCloudflareCheckbox() {
        String js = "(function() { " +
                "  var cbs = document.querySelectorAll('input[type=checkbox]'); " +
                "  for(var j=0; j<cbs.length; j++) { " +
                "      var pt = cbs[j].parentElement ? cbs[j].parentElement.innerText : ''; " +
                "      if(/驗證|人|human/i.test(pt)) { " +
                "          cbs[j].click(); " +
                "          console.log('Cloudflare checkbox clicked'); " +
                "          return 'CLICKED'; " +
                "      } " +
                "  } " +
                "  return 'NOT_FOUND'; " +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private void startMonitoring() {
        stopMonitoring();
        checkCount = 0;
        monitoringRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isFinished) {
                    KLog.d("Monitoring... (" + checkCount + ")");
                    checkPageState();
                }
            }
        };
        handler.postDelayed(monitoringRunnable, CHECK_INTERVAL);
    }

    private void stopMonitoring() {
        if (monitoringRunnable != null) {
            handler.removeCallbacks(monitoringRunnable);
            monitoringRunnable = null;
        }
    }

    // Robust injection with verification and retry
    private void injectReplyLogic(int attempt) {
        if (comment == null || comment.isEmpty()) return;
        if (attempt > 5) {
            KLog.e("Failed to inject comment after 5 attempts");
            if (callback != null) callback.onError("表單填寫失敗，請重試");
            dismiss();
            return;
        }

        KLog.d("Injecting reply... Attempt " + attempt);
        String js = "(function() { " +
                "  var f = document.querySelector('form[action*=pixmicat]'); " +
                "  if (f) { " +
                "    var ni = f.querySelector('input[name=name]'); if(ni) ni.value = '" + escapeJs(name) + "'; " +
                "    var ei = f.querySelector('input[name=email]'); if(ei) ei.value = '" + escapeJs(email) + "'; " +
                "    var si = f.querySelector('input[name=sub]'); if(si) si.value = '" + escapeJs(subject) + "'; " +
                "    var ci = f.querySelector('textarea[name=com]') || f.querySelector('input[name=com]'); " +
                "    if(ci) { " +
                "       ci.value = '" + escapeJs(comment) + "'; " +
                // Verify if value stuck
                "       return ci.value === '" + escapeJs(comment) + "' ? 'SET_OK' : 'SET_FAIL'; " +
                "    } " +
                "  } " +
                "  return 'FORM_NOT_FOUND'; " +
                "})();";

        webView.evaluateJavascript(js, result -> {
            String res = result != null ? result.replace("\"", "") : "";
            KLog.d("Inject result: " + res);

            if ("SET_OK".equals(res)) {
                // Wait a bit to ensure JS processing then submit
                handler.postDelayed(() -> {
                    String submitJs = "var f = document.querySelector('form[action*=pixmicat]'); if(f) { var b = f.querySelector('input[type=submit]'); if(b) b.click(); else f.submit(); }";
                    webView.evaluateJavascript(submitJs, null);
                }, 800);
            } else {
                // Retry if failed
                handler.postDelayed(() -> injectReplyLogic(attempt + 1), 1000);
            }
        });
    }
    
    private void checkSubmissionStatus() {
        if (isFinished) return;
        String url = webView.getUrl();
        if (url != null && url.contains("res=") && !url.contains("mode=regist")) {
             KLog.d("URL Success redirect: " + url);
             handleSuccess();
        }
    }
    
    private void handleSuccess() {
        if (isFinished) return;
        isFinished = true;
        if (callback != null) callback.onSuccess();
        dismiss();
    }

    private String escapeJs(String text) {
        if (text == null) return "";
        char bs = (char)92;
        String bss = String.valueOf(bs);
        return text.replace(bss, bss + bss).replace("'", bss + "'").replace("\n", bss + "n").replace("\r", "");
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopMonitoring();
        isFinished = true;
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
    }
}