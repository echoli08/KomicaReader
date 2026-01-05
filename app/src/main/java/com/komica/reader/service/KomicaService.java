package com.komica.reader.service;

import okhttp3.FormBody;
import okhttp3.RequestBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.Callable;
import com.komica.reader.model.Board;
import com.komica.reader.model.BoardCategory;
import com.komica.reader.model.Thread;
import com.komica.reader.model.Post;

import com.komica.reader.util.KLog;

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
    private static final String DEFAULT_TITLE = "Untitled";
    private static final String DEFAULT_AUTHOR = "Anonymous";

    public static class FetchBoardsTask implements Callable<List<BoardCategory>> {
        @Override
        public List<BoardCategory> call() throws Exception {
            Request request = new Request.Builder()
                    .url(BASE_URL + "/bbsmenu.html")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
                String html = response.body().string();
                return parseBoards(html);
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

        android.util.Log.d("Komica", "Original board URL: " + url);

        if (page > 0) {
            if (url.endsWith("index.htm")) {
                url = url.replace("index.htm", page + ".htm");
            } else if (url.endsWith("index.html")) {
                url = url.replace("index.html", page + ".htm");
            } else if (url.endsWith("/")) {
                url = url + page + ".htm";
            } else {
                // If it's something like .../pixmicat.php, we need to be careful
                if (url.contains(".php")) {
                    url = url.substring(0, url.lastIndexOf("/")) + "/" + page + ".htm";
                } else {
                    url = url + "/" + page + ".htm";
                }
            }
        }

        android.util.Log.d("Komica", "Fetching threads from: " + url + " (page " + page + ")");

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
                String html = response.body().string();
                
                KLog.d("Threads response length: " + html.length());
                return parseThreads(html, boardUrl);
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
            android.util.Log.d("Komica", "Fetching thread detail from: " + threadUrl);

            Request request = new Request.Builder()
                    .url(threadUrl)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
                String html = response.body().string();
                
                KLog.d("Thread detail response length: " + html.length());
                return parseThreadDetail(html, threadUrl);
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

            // Manually construct the byte stream for the form body
            // This is the most reliable way to handle Big5/UTF-8 mixtures in older PHP scripts
            StringBuilder sb = new StringBuilder();
            sb.append("mode=search");
            sb.append("&search_target=all");
            sb.append("&andor=and");
            sb.append("&keyword=").append(java.net.URLEncoder.encode(query, charset));
            // Gaia boards often REQUIRE the submit button value to be present
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
                if (!response.isSuccessful() && response.code() != 404) {
                     // 404 might be a valid state for fallback logic, but others are errors
                     // However, we want to try fallback anyway if results are empty
                }
                
                byte[] responseBytes = response.body().bytes();
                
                // Try auto-detect, then fallback to Big5 if it's Gaia
                Document doc = Jsoup.parse(new java.io.ByteArrayInputStream(responseBytes), null, baseUrl);
                String title = doc.title();
                // Check if title is garbled or if we are on a Gaia board known to be Big5
                if (isGaia || title.contains("\uFFFD")) {
                    KLog.w("Encoding issue suspected (title garbled or Gaia board), forcing Big5 decoding");
                    doc = Jsoup.parse(new java.io.ByteArrayInputStream(responseBytes), "Big5", baseUrl);
                }
                
                List<Thread> results = parseSearchResults(doc, boardUrl);
                
                if (results.isEmpty()) {
                    // Try fallback logic (GET request)
                    KLog.d("Search results empty via POST, trying GET fallback...");
                    String getSearchUrl = baseUrl + "pixmicat.php?mode=search&keyword=" + java.net.URLEncoder.encode(query, "UTF-8");
                    Request fallbackRequest = new Request.Builder().url(getSearchUrl).build();
                    
                    try (Response fallbackResponse = client.newCall(fallbackRequest).execute()) {
                         if (fallbackResponse.isSuccessful()) {
                             Document fallbackDoc = Jsoup.parse(fallbackResponse.body().byteStream(), null, baseUrl);
                             results = parseSearchResults(fallbackDoc, boardUrl);
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
                return parseSearchResults(doc, BASE_URL);
            }
        }
    }

    public static List<BoardCategory> parseBoards(String html) {
        List<BoardCategory> categories = new ArrayList<>();
        
        String[] excludedCategories = {"聊天室", "外部連結", "失效連結"};
        
        Document doc = Jsoup.parse(html);
        Elements ulElements = doc.select("#list ul");
        for (Element ul : ulElements) {
            Element categoryElement = ul.selectFirst("li.category");
            if (categoryElement == null) continue;
            
            String categoryName = categoryElement.text().trim();
            if (categoryName.isEmpty()) continue;
            
            boolean isExcluded = false;
            for (String excluded : excludedCategories) {
                if (categoryName.contains(excluded)) {
                    KLog.d("Excluding category: " + categoryName);
                    isExcluded = true;
                    break;
                }
            }
            if (isExcluded) continue;
            
            List<Board> boards = new ArrayList<>();
            Elements boardElements = ul.select("li:not(.category) a");
            
            for (Element boardElement : boardElements) {
                String boardName = boardElement.text().trim();
                String boardUrl = boardElement.attr("href");
                
                if (!boardName.isEmpty() && !boardUrl.isEmpty()) {
                    String cleanUrl = boardUrl.replaceAll("[?&].*$", "");
                    boards.add(new Board(boardName, cleanUrl, ""));
                }
            }
            
            if (!boards.isEmpty()) {
                categories.add(new BoardCategory(categoryName, boards));
            }
        }
        
        return categories;
    }

    public static List<Thread> parseSearchResults(Document doc, String boardUrl) {
        List<Thread> threads = new ArrayList<>();

        // 1. Validation
        String pageTitle = doc.title();
        KLog.d("parseSearchResults: Page title: " + pageTitle);
        
        // Relaxed search check: look for search markers in title or lack of index pagination
        boolean hasSearchMarker = pageTitle.contains("搜尋") || pageTitle.contains("Search") || pageTitle.contains("?");
        boolean hasIndexPagination = doc.selectFirst(".paginfo, .pages, .pginfo") != null;
        
        if (hasIndexPagination && !hasSearchMarker) {
            KLog.w("parseSearchResults: Detected index page instead of search results.");
            return threads;
        }

        // 2. Main Content
        Element mainForm = doc.selectFirst("form#delform, form[name='delform']");
        Elements postElements = (mainForm != null) 
            ? mainForm.select("div.post, div[id^='r'], div[id^='p'], td.reply, td.post-body")
            : doc.select("div.post, td.reply, td.post-body");

        for (Element postElement : postElements) {
            if (postElement.hasClass("nav") || postElement.id().equals("notice")) continue;

            String title = DEFAULT_TITLE;
            String author = DEFAULT_AUTHOR;
            int postNumber = 0;
            int parentThreadNo = 0;
            String contentPreview = "";
            String lastReplyTime = "";

            // res= link usually means parent thread
            Element resLink = postElement.selectFirst("a[href*='res=']");
            if (resLink != null) {
                Matcher m = Pattern.compile("res=(\\d+)").matcher(resLink.attr("href"));
                if (m.find()) parentThreadNo = Integer.parseInt(m.group(1));
            }

            // Number check
            Element qlink = postElement.selectFirst(".qlink, .now a, .relink");
            if (qlink != null) {
                String numText = qlink.text().replaceAll("[^0-9]", "");
                if (!numText.isEmpty()) {
                    try { postNumber = Integer.parseInt(numText); } catch (Exception ignored) {}
                }
            }
            
            if (postNumber <= 0) {
                Element checkbox = postElement.selectFirst("input[type='checkbox']");
                if (checkbox != null) {
                    String val = checkbox.attr("value");
                    if (val.matches("\\d+")) postNumber = Integer.parseInt(val);
                }
            }

            if (postNumber <= 0) continue;

            Element quoteElement = postElement.selectFirst("div.quote, .comment, blockquote");
            if (quoteElement != null) contentPreview = parseQuoteContent(quoteElement);
            
            Element thumbElement = postElement.selectFirst("img.img, .file-thumb img");
            if (contentPreview.trim().length() < 2 && thumbElement == null) continue;

            int finalThreadId = (parentThreadNo > 0) ? parentThreadNo : postNumber;

            Element titleElement = postElement.selectFirst("span.title, b, font[color='#cc1105']");
            if (titleElement != null && !titleElement.text().trim().isEmpty()) title = titleElement.text().trim();

            Element nameElement = postElement.selectFirst("span.name, .postername, font[color='#117743']");
            if (nameElement != null) author = nameElement.text().trim();

            String imageUrl = (thumbElement != null) ? thumbElement.attr("src") : "";

            if (title.equals(DEFAULT_TITLE) || title.isEmpty()) {
                if (parentThreadNo > 0) title = "回覆於 No." + parentThreadNo;
                else {
                    String[] lines = contentPreview.split("\n");
                    title = lines.length > 0 ? lines[0] : DEFAULT_TITLE;
                }
                if (title.length() > 40) title = title.substring(0, 40) + "...";
            }
            
            Element nowElement = postElement.selectFirst("span.now, .postertime, font[size='-1']");
            if (nowElement != null) lastReplyTime = nowElement.text().trim();

            String resolvedThreadUrl = resolveUrl(boardUrl, "pixmicat.php?res=" + finalThreadId);
            Thread thread = new Thread("search-" + postNumber, title, author, 0, resolvedThreadUrl);
            thread.setPostNumber(postNumber);
            thread.setImageUrl(resolveUrl(boardUrl, imageUrl));
            thread.setContentPreview(contentPreview);
            thread.setLastReplyTime(lastReplyTime);

            threads.add(thread);
        }

        KLog.d("parseSearchResults: Parsed " + threads.size() + " validated search results");
        return threads;
    }

    public static String resolveUrl(String baseUrl, String href) {
        if (href == null || href.trim().isEmpty()) return "";
        String trimmed = href.trim();

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.startsWith("//")) {
            return "https:" + trimmed;
        }
        if (trimmed.startsWith("/")) {
            return BASE_URL + trimmed;
        }

        String base = baseUrl;
        int queryIndex = base.indexOf('?');
        if (queryIndex >= 0) {
            base = base.substring(0, queryIndex);
        }
        int hashIndex = base.indexOf('#');
        if (hashIndex >= 0) {
            base = base.substring(0, hashIndex);
        }
        int lastSlash = base.lastIndexOf('/');
        String prefix = lastSlash >= 0 ? base.substring(0, lastSlash + 1) : base + "/";

        String result = prefix + trimmed;
        android.util.Log.d("Komica", "Resolved URL: " + href + " -> " + result);
        return result;
    }

    public static List<Thread> parseThreads(String html, String boardUrl) {
        List<Thread> threads = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        KLog.d("parseThreads: Parsing HTML (length=" + html.length() + ") from " + boardUrl);

        Elements threadElements = doc.select("div.thread");
        if (threadElements.isEmpty()) {
            KLog.w("parseThreads: No div.thread found! Possible layout change or parse error.");
            // Fallback: check if it's an old-style table layout (less common now but good for safety)
            threadElements = doc.select("form[name='delform'] > table");
            if (!threadElements.isEmpty()) KLog.i("parseThreads: Found old-style table layout");
        }
        
        KLog.d("parseThreads: Found " + threadElements.size() + " thread elements");

        for (int i = 0; i < threadElements.size(); i++) {
            Element threadElement = threadElements.get(i);
            Element postElement = threadElement.selectFirst("div.post");

            String title = DEFAULT_TITLE;
            String author = DEFAULT_AUTHOR;
            String threadUrl = "";
            String imageUrl = "";
            int postNumber = 0;
            int replyCount = 0;
            String lastReplyTime = "";
            String contentPreview = "";

            if (postElement != null) {
                Element titleElement = postElement.selectFirst("span.title");
                if (titleElement != null) {
                    title = titleElement.text().trim();
                }

                Element nameElement = postElement.selectFirst("span.name");
                if (nameElement != null) {
                    author = nameElement.text().trim();
                }

                Element qlinkElement = postElement.selectFirst("span.qlink");
                if (qlinkElement != null) {
                    String dataNo = qlinkElement.attr("data-no");
                    if (!dataNo.isEmpty()) {
                        try {
                            postNumber = Integer.parseInt(dataNo);
                        } catch (NumberFormatException e) {
                            postNumber = 0;
                        }
                        threadUrl = "pixmicat.php?res=" + dataNo;
                    }
                }

                Element thumbElement = postElement.selectFirst("a.file-thumb img.img");
                if (thumbElement != null) {
                    imageUrl = thumbElement.attr("src");
                }

                 Element nowElement = postElement.selectFirst("span.now");
                if (nowElement != null) {
                    lastReplyTime = nowElement.text().trim();
                }
                
                Element quoteElement = postElement.selectFirst("div.quote");
                if (quoteElement != null) {
                    String content = parseQuoteContent(quoteElement);
                    String[] lines = content.split("\n");
                    StringBuilder preview = new StringBuilder();
                    int nonEmptyLineCount = 0;
                    for (String line : lines) {
                        String trimmedLine = line.trim();
                        if (!trimmedLine.isEmpty()) {
                            nonEmptyLineCount++;
                            if (nonEmptyLineCount > 4) {
                                break;
                            }
                            if (preview.length() > 0) {
                                preview.append("\n");
                            }
                            preview.append(trimmedLine);
                        }
                    }
                    
                    if (nonEmptyLineCount == 0) {
                        contentPreview = "(無文字內容)";
                    } else {
                        contentPreview = preview.toString();
                    }
                }
                
                Elements replyPosts = threadElement.select("div.post.reply");
                int visibleReplyCount = replyPosts.size();
                
                int omittedCount = 0;
                Element warnElement = threadElement.selectFirst("span.warn_txt2");
                if (warnElement != null) {
                    String warnText = warnElement.text();
                    try {
                        // Text format is usually "有 19 篇回應被省略。"
                        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(warnText);
                        if (matcher.find()) {
                            omittedCount = Integer.parseInt(matcher.group(1));
                        }
                    } catch (Exception e) {
                        android.util.Log.e("Komica", "Error parsing omitted count: " + e.getMessage());
                    }
                }
                
                replyCount = visibleReplyCount + omittedCount;

                 if (!replyPosts.isEmpty()) {
                    int lastIndex = replyPosts.size() - 1;
                    Element lastReply = replyPosts.get(lastIndex);
                    Element lastReplyNowElement = lastReply.selectFirst("span.now");
                    if (lastReplyNowElement != null) {
                        lastReplyTime = lastReplyNowElement.text().trim();
                        android.util.Log.d("Komica", "Found last reply time: " + lastReplyTime);
                    } else {
                        android.util.Log.d("Komica", "Could not find span.now in last reply");
                    }
                } else {
                    android.util.Log.d("Komica", "No replies found for thread: " + title);
                }
            }

            if (!title.isEmpty() && !threadUrl.isEmpty()) {
                String resolvedThreadUrl = resolveUrl(boardUrl, threadUrl);
                android.util.Log.d("Komica", "Thread " + postNumber + ": " + title + " -> " + resolvedThreadUrl);
                
                Thread thread = new Thread(
                        String.valueOf(System.currentTimeMillis()) + "-" + i + "-" + (int)(Math.random() * 10000),
                        title,
                        author,
                        replyCount,
                        resolvedThreadUrl
                );
                thread.setPostNumber(postNumber);
                thread.setImageUrl(resolveUrl(boardUrl, imageUrl));
                thread.setLastReplyTime(lastReplyTime);
                thread.setContentPreview(contentPreview);

                threads.add(thread);
            }
        }

        android.util.Log.d("Komica", "Successfully parsed " + threads.size() + " threads");
        return threads;
    }

    public static Thread parseThreadDetail(String html, String threadUrl) {
        Document doc = Jsoup.parse(html);

        KLog.d("parseThreadDetail: Parsing HTML from: " + threadUrl);

        String title = DEFAULT_TITLE;
        String author = DEFAULT_AUTHOR;
        String imageUrl = "";

        Element threadContainer = doc.selectFirst("div.thread");
        if (threadContainer == null) {
            KLog.w("parseThreadDetail: div.thread container not found!");
        }
        
        if (threadContainer != null) {
            Element threadPost = threadContainer.selectFirst("div.post.threadpost");
            if (threadPost != null) {
                Element titleElement = threadPost.selectFirst("span.title");
                if (titleElement != null) {
                    title = titleElement.text().trim();
                }
                
                Element nameElement = threadPost.selectFirst("span.name");
                if (nameElement != null) {
                    author = nameElement.text().trim();
                }
                
                Element thumbElement = threadPost.selectFirst("a.file-thumb img.img");
                if (thumbElement != null) {
                    imageUrl = thumbElement.attr("src");
                    android.util.Log.d("Komica", "Thread post thumbnail: " + imageUrl);
                }
                
                Element thumbLinkElement = threadPost.selectFirst("a.file-thumb");
                if (thumbLinkElement != null) {
                    String originalImageUrl = thumbLinkElement.attr("href");
                    if (!originalImageUrl.isEmpty()) {
                        imageUrl = resolveUrl(threadUrl, originalImageUrl);
                        android.util.Log.d("Komica", "Thread post original image: " + imageUrl);
                    }
                }
            }
        }

        android.util.Log.d("Komica", "Thread title: " + title);

        List<Post> posts = new ArrayList<>();

        Elements allPosts = doc.select("div.post");
        android.util.Log.d("Komica", "Found " + allPosts.size() + " post elements");

         for (int i = 0; i < allPosts.size(); i++) {
             Element postElement = allPosts.get(i);
             
             int postNumber = 0;
             String dataNo = postElement.attr("data-no");
             if (!dataNo.isEmpty()) {
                 try {
                     postNumber = Integer.parseInt(dataNo);
                 } catch (NumberFormatException e) {
                     postNumber = posts.size() + 1;
                 }
             } else {
                 postNumber = posts.size() + 1;
             }
             
             String postAuthor = DEFAULT_AUTHOR;
             Element nameElement = postElement.selectFirst("span.name");
             if (nameElement != null) {
                 postAuthor = nameElement.text().trim();
             }

            String content = "";
            Element quoteElement = postElement.selectFirst("div.quote");
            if (quoteElement != null) {
                content = parseQuoteContent(quoteElement);
            }

            String postTime = "";
            Element timeElement = postElement.selectFirst("span.now");
            if (timeElement != null) {
                postTime = timeElement.text().trim();
            }

            String postImageUrl = "";
            String postThumbnailUrl = "";
            Element thumbElement = postElement.selectFirst("a.file-thumb img.img");
            if (thumbElement != null) {
                postThumbnailUrl = thumbElement.attr("src");
                android.util.Log.d("Komica", "Found thumbnail: " + postThumbnailUrl);
            }
            
            Element thumbLinkElement = postElement.selectFirst("a.file-thumb");
            if (thumbLinkElement != null) {
                postImageUrl = thumbLinkElement.attr("href");
                android.util.Log.d("Komica", "Found original image: " + postImageUrl);
            }

            if (content.length() > 0 || !postImageUrl.isEmpty()) {
                android.util.Log.d("Komica", "Post " + postNumber + " - Author: " + postAuthor + ", Content length: " + content.length());

                String postId = String.valueOf(System.currentTimeMillis()) + "-" + posts.size();
                Post post = new Post(postId, postAuthor, content, resolveUrl(threadUrl, postImageUrl), resolveUrl(threadUrl, postThumbnailUrl), postTime, postNumber);
                posts.add(post);
            }
        }

        android.util.Log.d("Komica", "Total posts parsed: " + posts.size());

        Thread thread = new Thread(
                String.valueOf(System.currentTimeMillis()),
                title,
                author,
                posts.size() - 1,
                threadUrl
        );
        thread.setPosts(posts);
        thread.setImageUrl(resolveUrl(threadUrl, imageUrl));

        android.util.Log.d("Komica", "Parsed thread detail: " + title + ", posts: " + posts.size());
        return thread;
    }

    private static String parseQuoteContent(Element quoteElement) {
        String html = quoteElement.html();
        
        String BR_PLACEHOLDER = "\uFFFF";
        
        html = html.replaceAll("<br\\s*/?>", BR_PLACEHOLDER);
        html = html.replaceAll("<p>", BR_PLACEHOLDER);
        html = html.replaceAll("</p>", BR_PLACEHOLDER);
        
        html = Jsoup.parse(html).text();
        
        html = html.replace(BR_PLACEHOLDER, "\n");
        
        html = html.trim();
        
        html = html.replaceAll(" +\n", "\n");
        html = html.replaceAll("\n +", "\n");
        
        while (html.contains("\n\n\n")) {
            html = html.replace("\n\n\n", "\n\n");
        }
        
        return html;
    }
}
