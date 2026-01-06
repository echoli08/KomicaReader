package com.komica.reader.service;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import com.komica.reader.model.BoardCategory;
import com.komica.reader.model.Thread;
import com.komica.reader.util.KLog;
import com.komica.reader.util.KomicaParser;

public class KomicaService {
    private static final String BASE_URL = "http://komica1.org";
    private static volatile OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(chain -> {
                Request original = chain.request();
                Request request = original.newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
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

        public SendReplyTask(String boardUrl, int resto, String name, String email, String subject, String comment) {
            this.boardUrl = boardUrl;
            this.resto = resto;
            this.name = name;
            this.email = email;
            this.subject = subject;
            this.comment = comment;
        }

        @Override
        public Boolean call() throws Exception {
            String baseUrl = boardUrl;
            // Remove filename and query params from boardUrl to get base path
            baseUrl = baseUrl.replaceAll("(index\\.html?|pixmicat\\.php.*)$", "");
            if (!baseUrl.endsWith("/")) baseUrl += "/";

            String postUrl = baseUrl + "pixmicat.php";
            boolean isGaia = baseUrl.contains("gaia.komica1.org") || baseUrl.contains("sora.komica.org");
            String charset = isGaia ? "Big5" : "UTF-8";

            KLog.d("Send Reply POST URL: " + postUrl + " | Resto: " + resto + " | Charset: " + charset);

            StringBuilder sb = new StringBuilder();
            sb.append("mode=regist");
            sb.append("&resto=").append(resto);
            sb.append("&name=").append(java.net.URLEncoder.encode(name != null ? name : "", charset));
            sb.append("&email=").append(java.net.URLEncoder.encode(email != null ? email : "", charset));
            sb.append("&sub=").append(java.net.URLEncoder.encode(subject != null ? subject : "", charset));
            sb.append("&com=").append(java.net.URLEncoder.encode(comment != null ? comment : "", charset));
            sb.append("&pwd=").append(java.net.URLEncoder.encode("komicareader", charset)); // default password
            // Some boards require the "send" button value
            sb.append("&send=").append(java.net.URLEncoder.encode("送出", charset));

            byte[] postBytes = sb.toString().getBytes();

            RequestBody body = RequestBody.create(
                okhttp3.MediaType.parse("application/x-www-form-urlencoded"),
                postBytes
            );

            Request request = new Request.Builder()
                    .url(postUrl)
                    .post(body)
                    .header("Referer", boardUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                // Read response body
                String responseBody = response.body().string();
                KLog.d("Reply response code: " + response.code());
                // KLog.d("Reply response body: " + responseBody); // Uncomment if needed, but might be large

                // Komica usually returns a 302 Redirect on success.
                // If 200 OK, it might be a meta-refresh (success) OR an error page.
                
                if (response.code() == 302 || response.code() == 303) {
                    KLog.d("Reply success (Redirect)");
                    return true;
                }

                if (response.isSuccessful()) {
                    // Check for error keywords in the body
                    if (responseBody.contains("錯誤") || responseBody.contains("失敗") || responseBody.contains("Error")) {
                        KLog.w("Reply failed (Error keywords found in body)");
                        // Try to extract error message
                        Document doc = Jsoup.parse(responseBody);
                        String errorText = doc.text();
                        if (errorText.length() > 100) errorText = errorText.substring(0, 100);
                        KLog.w("Error details: " + errorText);
                        return false;
                    }
                    
                    // Check for success indicators (e.g., meta refresh to the thread)
                    if (responseBody.contains("meta http-equiv=\"refresh\"") && responseBody.contains("pixmicat.php")) {
                         KLog.d("Reply success (Meta Refresh found)");
                         return true;
                    }
                    
                    // If no error found and it's 200 OK, assume success but log warning
                    KLog.i("Reply returned 200 OK without obvious error. Assuming success.");
                    return true;
                }
                
                KLog.w("Reply response failed, code: " + response.code());
                return false;
            }
        }
    }

    public static String resolveUrl(String baseUrl, String href) {
        return KomicaParser.resolveUrl(baseUrl, href);
    }
}
