package com.komica.reader.service;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.MultipartBody;
import okhttp3.MediaType;
import java.nio.charset.Charset;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.Callable;
import com.komica.reader.model.BoardCategory;
import com.komica.reader.model.Thread;
import com.komica.reader.util.KLog;
import com.komica.reader.util.KomicaParser;

public class KomicaService {
    private static final String BASE_URL = "http://komica1.org";
    private static final String MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static volatile OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(chain -> {
                Request original = chain.request();
                Request request = original.newBuilder()
                        .header("User-Agent", MOBILE_USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                        .header("Accept-Language", "zh-TW,zh;q=0.8,en-US;q=0.5,en;q=0.3")
                        .method(original.method(), original.body())
                        .build();
                return chain.proceed(request);
            })
            .build();

    public static synchronized void setClient(OkHttpClient customClient) {
        client = customClient;
    }

    public static class FetchBoardsTask implements Callable<List<BoardCategory>> {
        @Override
        public List<BoardCategory> call() throws Exception {
            Request request = new Request.Builder()
                    .url(BASE_URL + "/bbsmenu.html")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
                String html = response.body().string();
                return KomicaParser.parseBoards(html);
            }
        }
    }

    public static class FetchThreadsTask implements Callable<List<Thread>> {
        private String boardUrl;
        private int page;

        public FetchThreadsTask(String boardUrl, int page) {
            this.boardUrl = boardUrl;
            this.page = page;
        }

        @Override
        public List<Thread> call() throws Exception {
            String url = boardUrl;
            KLog.d("Original board URL: " + url);

            if (page > 0) {
                if (url.endsWith("index.htm")) {
                    url = url.replace("index.htm", page + ".htm");
                } else if (url.endsWith("index.html")) {
                    url = url.replace("index.html", page + ".htm");
                } else if (url.endsWith("/")) {
                    url = url + page + ".htm";
                } else {
                    if (url.contains(".php")) {
                        url = url.substring(0, url.lastIndexOf("/")) + "/" + page + ".htm";
                    } else {
                        url = url + "/" + page + ".htm";
                    }
                }
            }

            KLog.d("Fetching threads from: " + url + " (page " + page + ")");

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
                String html = response.body().string();
                return KomicaParser.parseThreads(html, boardUrl);
            }
        }
    }

    public static class FetchThreadDetailTask implements Callable<Thread> {
        private String threadUrl;

        public FetchThreadDetailTask(String threadUrl) {
            this.threadUrl = threadUrl;
        }

        @Override
        public Thread call() throws Exception {
            KLog.d("Fetching thread detail from: " + threadUrl);

            Request request = new Request.Builder()
                    .url(threadUrl)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
                String html = response.body().string();
                return KomicaParser.parseThreadDetail(html, threadUrl);
            }
        }
    }

    public static class BoardSearchTask implements Callable<List<Thread>> {
        private String boardUrl;
        private String query;

        public BoardSearchTask(String boardUrl, String query) {
            this.boardUrl = boardUrl;
            this.query = query;
        }

        @Override
        public List<Thread> call() throws Exception {
            String baseUrl = boardUrl;
            baseUrl = baseUrl.replaceAll("(index\\.html?|pixmicat\\.php)$", "");
            if (!baseUrl.endsWith("/")) baseUrl += "/";

            String searchUrl = baseUrl + "pixmicat.php";
            boolean isGaia = baseUrl.contains("gaia.komica1.org") || baseUrl.contains("sora.komica.org");
            String charset = isGaia ? "Big5" : "UTF-8";
            
            KLog.d("Board Search POST URL: " + searchUrl + " | Query: " + query + " | Charset: " + charset);

            StringBuilder sb = new StringBuilder();
            sb.append("mode=search");
            sb.append("&search_target=all");
            sb.append("&andor=and");
            sb.append("&keyword=").append(java.net.URLEncoder.encode(query, charset));
            sb.append("&search=").append(java.net.URLEncoder.encode("搜尋", charset));
            
            byte[] postBytes = sb.toString().getBytes(); 

            RequestBody body = RequestBody.create(
                okhttp3.MediaType.parse("application/x-www-form-urlencoded"),
                postBytes
            );

            Request request = new Request.Builder()
                    .url(searchUrl)
                    .post(body)
                    .header("Referer", baseUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                byte[] responseBytes = response.body().bytes();
                Document doc = Jsoup.parse(new java.io.ByteArrayInputStream(responseBytes), null, baseUrl);
                String title = doc.title();
                
                if (isGaia || title.contains("\uFFFD")) {
                    KLog.w("Encoding issue suspected, forcing Big5 decoding");
                    doc = Jsoup.parse(new java.io.ByteArrayInputStream(responseBytes), "Big5", baseUrl);
                }
                
                List<Thread> results = KomicaParser.parseSearchResults(doc, boardUrl);
                
                if (results.isEmpty()) {
                    KLog.d("Search results empty via POST, trying GET fallback...");
                    String getSearchUrl = baseUrl + "pixmicat.php?mode=search&keyword=" + java.net.URLEncoder.encode(query, "UTF-8");
                    Request fallbackRequest = new Request.Builder().url(getSearchUrl).build();
                    
                    try (Response fallbackResponse = client.newCall(fallbackRequest).execute()) {
                         if (fallbackResponse.isSuccessful()) {
                             Document fallbackDoc = Jsoup.parse(fallbackResponse.body().byteStream(), null, baseUrl);
                             results = KomicaParser.parseSearchResults(fallbackDoc, boardUrl);
                         }
                    }
                }
                return results;
            }
        }
    }

    public static class SearchTask implements Callable<List<Thread>> {
        private String query;

        public SearchTask(String query) {
            this.query = query;
        }

        @Override
        public List<Thread> call() throws Exception {
            String searchUrl = BASE_URL + "/?mode=search&keyword=" + java.net.URLEncoder.encode(query, "UTF-8");
            KLog.d("Search URL: " + searchUrl);

            Request request = new Request.Builder()
                    .url(searchUrl)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
                Document doc = Jsoup.parse(response.body().byteStream(), null, BASE_URL);
                return KomicaParser.parseSearchResults(doc, BASE_URL);
            }
        }
    }

    public static class SendReplyTask implements Callable<Boolean> {
        private String boardUrl;
        private int resto;
        private String name;
        private String email;
        private String subject;
        private String comment;
        private String turnstileToken;

        public SendReplyTask(String boardUrl, int resto, String name, String email, String subject, String comment, String turnstileToken) {
            this.boardUrl = boardUrl;
            this.resto = resto;
            this.name = name;
            this.email = email;
            this.subject = subject;
            this.comment = comment;
            this.turnstileToken = turnstileToken;
        }

        // Backward compatibility constructor
        public SendReplyTask(String boardUrl, int resto, String name, String email, String subject, String comment) {
            this(boardUrl, resto, name, email, subject, comment, null);
        }

        @Override
        public Boolean call() throws Exception {
            String baseUrl = boardUrl;
            // Remove filename and query params from boardUrl to get base path
            baseUrl = baseUrl.replaceAll("(index\\.html?|pixmicat\\.php.*)$", "");
            if (!baseUrl.endsWith("/")) baseUrl += "/";

            String postUrl = baseUrl + "pixmicat.php";
            boolean isGaia = baseUrl.contains("gaia.komica1.org") || baseUrl.contains("sora.komica.org");
            String charsetName = isGaia ? "Big5" : "UTF-8";
            Charset charset = Charset.forName(charsetName);

            KLog.d("Send Reply POST URL: " + postUrl + " | Resto: " + resto + " | Charset: " + charsetName);

            Map<String, String> hiddenFields = new HashMap<>();

            // --- 1. Timerecord Cookie Validation & Warm-up ---
            // Pixmicat requires 'timerecord' cookie to calculate post time. Missing or too-fresh timerecord causes "Spambot" error.
            java.net.URL urlObj = new java.net.URL(postUrl);
            String host = urlObj.getHost();
            
            // Always perform warm-up to ensure fresh cookies and session
            KLog.w("⚠️ Performing warm-up request sequence...");

            // Step 1: Visit Index Page (simulating user entry)
            String indexUrl = baseUrl + "index.htm";
            Request indexRequest = new Request.Builder()
                    .url(indexUrl)
                    .header("User-Agent", MOBILE_USER_AGENT)
                    .build();
            
            try (Response response = client.newCall(indexRequest).execute()) {
                KLog.d("Index warm-up status: " + response.code());
            } catch (Exception e) {
                KLog.e("Index warm-up failed: " + e.getMessage());
            }
            
            try { java.lang.Thread.sleep(1000); } catch (InterruptedException e) {}

            // Step 2: Visit Reply Form Page (Actual Target)
            String formUrl = baseUrl + "pixmicat.php?res=" + resto;
            Request formRequest = new Request.Builder()
                    .url(formUrl)
                    .header("User-Agent", MOBILE_USER_AGENT)
                    .header("Referer", indexUrl)
                    .build();
            
            try (Response response = client.newCall(formRequest).execute()) {
                KLog.d("Form warm-up status: " + response.code());
                // 繁體中文註解：解析回文表單的隱藏欄位，降低 Spambot 觸發機率
                if (response.isSuccessful() && response.body() != null) {
                    byte[] responseBytes = response.body().bytes();
                    Document doc = Jsoup.parse(new ByteArrayInputStream(responseBytes), charsetName, formUrl);
                    Element form = doc.selectFirst("form[action*=pixmicat]");
                    if (form != null) {
                        Elements inputs = form.select("input[type=hidden]");
                        for (Element input : inputs) {
                            String name = input.attr("name");
                            if (!name.isEmpty()) {
                                hiddenFields.put(name, input.attr("value"));
                            }
                        }
                        KLog.d("Parsed hidden fields count: " + hiddenFields.size());
                    } else {
                        KLog.w("Reply form not found for hidden field parsing");
                    }
                }
            } catch (Exception e) {
                KLog.e("Form warm-up failed: " + e.getMessage());
            }
            
            // Re-check cookie and force refresh if needed
            List<okhttp3.Cookie> cookies = client.cookieJar().loadForRequest(okhttp3.HttpUrl.parse(postUrl));
            long currentTimerecordValue = 0;
            for (okhttp3.Cookie c : cookies) {
                if (c.name().equals("timerecord")) {
                    try {
                        currentTimerecordValue = Long.parseLong(c.value());
                    } catch (NumberFormatException e) {
                        currentTimerecordValue = 0;
                    }
                    break;
                }
            }
            
            long now = System.currentTimeMillis() / 1000;
            long minTimerecordAge = 120; // 2 minutes minimum
            
            if (currentTimerecordValue == 0 || (now - currentTimerecordValue) < minTimerecordAge) {
                KLog.w("⚠️ timerecord missing or too fresh. Injecting fresh value (target age: " + minTimerecordAge + "s)...");
                long freshTime = now - minTimerecordAge;
                okhttp3.Cookie freshCookie = new okhttp3.Cookie.Builder()
                        .name("timerecord")
                        .value(String.valueOf(freshTime))
                        .domain(host)
                        .path("/")
                        .build();
                
                client.cookieJar().saveFromResponse(
                    okhttp3.HttpUrl.parse(postUrl), 
                    java.util.Collections.singletonList(freshCookie)
                );
                KLog.d("Injected fresh timerecord: " + freshTime + " (age: " + minTimerecordAge + "s)");
            } else {
                KLog.d("Existing timerecord is acceptable (age: " + (now - currentTimerecordValue) + "s)");
            }
            
            // Log final cookie state before POST for debugging
            KLog.d("=== FINAL COOKIE STATE ===");
            cookies = client.cookieJar().loadForRequest(okhttp3.HttpUrl.parse(postUrl));
            for (okhttp3.Cookie c : cookies) {
                KLog.d("Cookie: " + c.name() + "=" + c.value() + " (domain=" + c.domain() + ", path=" + c.path() + ")");
            }
            for (okhttp3.Cookie c : cookies) {
                if (c.name().equals("timerecord")) {
                    try {
                        long age = (now - Long.parseLong(c.value()));
                        KLog.d("timerecord age at POST time: " + age + " seconds");
                    } catch (Exception e) {}
                }
            }

            // Simulate user typing delay (Increased to 8s for safety)
            try {
                KLog.d("Simulating user typing delay, sleeping for 8000ms...");
                java.lang.Thread.sleep(8000);
            } catch (InterruptedException e) {
                // ignore
            }

            MultipartBody.Builder builder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM);

            // 繁體中文註解：先加入伺服器提供的隱藏欄位，保留反垃圾機制所需參數
            Set<String> reservedFields = new HashSet<>(Arrays.asList(
                    "mode", "resto", "name", "email", "sub", "com", "pwd",
                    "upfile", "noimg", "send", "cf-turnstile-response",
                    "MAX_FILE_SIZE", "upfile_path"
            ));
            for (Map.Entry<String, String> entry : hiddenFields.entrySet()) {
                if (reservedFields.contains(entry.getKey())) continue;
                addStringPart(builder, entry.getKey(), entry.getValue(), charset);
            }

            // Set User Fields
            if (!hiddenFields.containsKey("mode")) addStringPart(builder, "mode", "regist", charset);
            
            // 繁體中文註解：使用表單提供的值，若缺失才回退預設值
            String maxFileSize = hiddenFields.containsKey("MAX_FILE_SIZE")
                    ? hiddenFields.get("MAX_FILE_SIZE") : "5242880";
            String upfilePath = hiddenFields.containsKey("upfile_path")
                    ? hiddenFields.get("upfile_path") : "";
            addStringPart(builder, "MAX_FILE_SIZE", maxFileSize, charset);
            addStringPart(builder, "upfile_path", upfilePath, charset);
            
            addStringPart(builder, "resto", String.valueOf(resto), charset);
            addStringPart(builder, "name", name != null ? name : "", charset);
            addStringPart(builder, "email", email != null ? email : "", charset);
            addStringPart(builder, "sub", subject != null ? subject : "", charset);
            addStringPart(builder, "com", comment != null ? comment : "", charset);
            addStringPart(builder, "pwd", "komicareader", charset);
            addStringPart(builder, "noimg", "on", charset); // Checkbox, usually not hidden
            
            // Add standard send button field
            String sendText = isGaia ? "送出" : "Submit";
            addStringPart(builder, "send", sendText, charset);

            // Add Turnstile Token if provided
            if (turnstileToken != null && !turnstileToken.isEmpty()) {
                addStringPart(builder, "cf-turnstile-response", turnstileToken, charset);
                KLog.d("Added Turnstile Token: " + turnstileToken.substring(0, Math.min(10, turnstileToken.length())) + "...");
            } else {
                KLog.w("Warning: No Turnstile Token provided for reply. This might cause 503 error on protected boards.");
            }

            // Add empty file part (Critical for anti-spam checks that expect multipart structure)
            // Fix parameter order for OkHttp 4.x (byte[], MediaType)
            builder.addFormDataPart("upfile", "", RequestBody.create(new byte[0], MediaType.parse("application/octet-stream")));

            RequestBody body = builder.build();

            String origin = "https://komica1.org";
            try {
                java.net.URL url = new java.net.URL(postUrl);
                origin = url.getProtocol() + "://" + url.getHost();
            } catch (Exception e) {
                // ignore
            }

            // Use the specific reply form URL as referer
            String refererUrl = baseUrl + "pixmicat.php?res=" + resto;

            Request request = new Request.Builder()
                    .url(postUrl)
                    .post(body)
                    .header("Referer", refererUrl)
                    .header("Origin", origin)
                    .header("Sec-Fetch-Site", "same-origin")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-User", "?1")
                    .header("Sec-Fetch-Dest", "document")
                    .header("User-Agent", MOBILE_USER_AGENT)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                // Komica usually returns a 302 Redirect on success.
                if (response.code() == 302 || response.code() == 303) {
                    KLog.d("Reply success (Redirect)");
                    return true;
                }

                // Decode body with smart encoding detection
                byte[] responseBytes = response.body().bytes();
                
                KLog.d("Reply response code: " + response.code());
                
                // Log hex for debugging
                StringBuilder hex = new StringBuilder();
                for (int i = 0; i < Math.min(32, responseBytes.length); i++) {
                    hex.append(String.format("%02X ", responseBytes[i]));
                }
                KLog.d("Response Hex (First 32 bytes): " + hex.toString());
                
                // Try decoding with target charset
                String responseBody = new String(responseBytes, charsetName);
                
                // Heuristic: If it's Gaia (Big5) but the result contains replacement chars and 
                // looks like valid UTF-8, retry with UTF-8.
                if (isGaia && (responseBody.contains("\uFFFD") || responseBody.contains("Spambot") || responseBody.contains("Cloudflare"))) {
                    try {
                        String utf8Body = new String(responseBytes, "UTF-8");
                        // If UTF-8 decoding doesn't have replacement chars, use it.
                        if (!utf8Body.contains("\uFFFD")) {
                            responseBody = utf8Body;
                            KLog.d("Heuristic: Switched to UTF-8 decoding for response");
                        }
                    } catch (Exception e) { /* fallback to original */ }
                }

                if (response.isSuccessful()) {
                    // Strict Success Check:
                    // 1. Meta Refresh to the thread
                    // 2. "文章送出" or "回應送出" (Success messages)
                    boolean hasMetaRefresh = responseBody.contains("meta http-equiv=\"refresh\"") && responseBody.contains("pixmicat.php");
                    boolean hasSuccessText = responseBody.contains("文章送出") || responseBody.contains("回應送出") || responseBody.contains("成功");
                    
                    if (hasMetaRefresh || hasSuccessText) {
                         KLog.d("Reply success (Verified content)");
                         return true;
                    }

                    // Check for common error keywords
                    if (responseBody.contains("錯誤") || responseBody.contains("失敗") || responseBody.contains("Error") || responseBody.contains("Spambot")) {
                        KLog.w("Reply failed (Error keywords found)");
                        logErrorBody(responseBody);
                        return false;
                    }
                    
                    // If neither success nor obvious error, it's likely a challenge page (Cloudflare) or unexpected state.
                    KLog.w("Reply failed (No success indicator found). Logging preview:");
                    logErrorBody(responseBody);
                    return false;
                } else if (response.code() == 503 || response.code() == 403) {
                    KLog.w("Reply blocked by WAF/Cloudflare (Code " + response.code() + ")");
                    logErrorBody(responseBody);
                }
                
                KLog.w("Reply response failed, code: " + response.code());
                return false;
            }
        }

        private void addStringPart(MultipartBody.Builder builder, String name, String value, Charset charset) {
            String logValue = name.equals("pwd") ? "***" : value;
            // Try to fix potential log encoding issues for debugging
            try {
                if (logValue.length() < 20) {
                     // Check if it's mojibake (contains many ?)
                     if (logValue.contains("??")) {
                         // KLog.d("Value suspicious, raw bytes: " + java.util.Arrays.toString(value.getBytes(charset)));
                     }
                }
            } catch (Exception e) {}

            KLog.d("Adding form field: " + name + " = " + logValue);
            // Fix parameter order for OkHttp 4.x (byte[], MediaType)
            builder.addFormDataPart(name, null, RequestBody.create(value.getBytes(charset), MediaType.parse("text/plain; charset=" + charset.name())));
        }

        private void logErrorBody(String body) {
            try {
                Document doc = Jsoup.parse(body);
                String text = doc.text();
                if (text.length() > 200) text = text.substring(0, 200);
                KLog.w("Response text preview: " + text);
            } catch (Exception e) {
                if (body.length() > 200) KLog.w("Response raw preview: " + body.substring(0, 200));
                else KLog.w("Response raw preview: " + body);
            }
        }
    }

    public static String resolveUrl(String baseUrl, String href) {
        return KomicaParser.resolveUrl(baseUrl, href);
    }
}
