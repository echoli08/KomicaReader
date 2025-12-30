package com.komica.reader.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.komica.reader.model.Board;
import com.komica.reader.model.BoardCategory;
import com.komica.reader.model.Thread;
import com.komica.reader.model.Post;

public class KomicaService {
    private static final String BASE_URL = "https://komica1.org";
    private static final OkHttpClient client = new OkHttpClient.Builder()
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
    private static final String DEFAULT_TITLE = "Untitled";
    private static final String DEFAULT_AUTHOR = "Anonymous";

    public static class FetchBoardsTask implements Callable<List<BoardCategory>> {
        @Override
        public List<BoardCategory> call() throws Exception {
            Request request = new Request.Builder()
                    .url(BASE_URL + "/bbsmenu.html")
                    .build();

            Response response = client.newCall(request).execute();
            String html = response.body().string();

            return parseBoards(html);
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
            if (url.contains("index.htm")) {
                url = url.replace("index.htm", page + ".htm");
            } else if (url.endsWith("/")) {
                url = url + page + ".htm";
            } else {
                url = url + "/" + page + ".htm";
            }
        }

        android.util.Log.d("Komica", "Fetching threads from: " + url + " (page " + page + ")");

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            Response response = client.newCall(request).execute();
            String html = response.body().string();

            android.util.Log.d("Komica", "Threads response length: " + html.length());

            return parseThreads(html, boardUrl);
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

            Response response = client.newCall(request).execute();
            String html = response.body().string();

            android.util.Log.d("Komica", "Thread detail response length: " + html.length());

            return parseThreadDetail(html, threadUrl);
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

            android.util.Log.d("Komica", "Search URL: " + searchUrl);

            Request request = new Request.Builder()
                    .url(searchUrl)
                    .build();

            Response response = client.newCall(request).execute();
            String html = response.body().string();

            android.util.Log.d("Komica", "Search response length: " + html.length());

            return parseSearchResults(html, BASE_URL);
        }
    }

    private static List<BoardCategory> parseBoards(String html) {
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
                    android.util.Log.d("Komica", "Excluding category: " + categoryName);
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

    private static List<Thread> parseSearchResults(String html, String boardUrl) {
        List<Thread> threads = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        Elements threadElements = doc.select("#threads div.thread");
        for (Element threadElement : threadElements) {
            Element threadPost = threadElement.selectFirst("div.post");

            String title = DEFAULT_TITLE;
            String author = DEFAULT_AUTHOR;
            String threadUrl = "";
            String imageUrl = "";
            int replyCount = 0;

            if (threadPost != null) {
                title = threadPost.selectFirst("span.title") != null ?
                        threadPost.selectFirst("span.title").text().trim() : DEFAULT_TITLE;

                author = threadPost.selectFirst("span.name") != null ?
                        threadPost.selectFirst("span.name").text().trim() : DEFAULT_AUTHOR;

                String replyLink = threadPost.selectFirst("span.rlink a") != null ?
                        threadPost.selectFirst("span.rlink a").attr("href") : "";

                Element imgElement = threadPost.selectFirst("img.img");
                if (imgElement != null) {
                    imageUrl = imgElement.attr("src");
                }

                threadUrl = replyLink;
            }

            if (!title.isEmpty()) {
                String resolvedThreadUrl = resolveUrl(boardUrl, threadUrl);
                Thread thread = new Thread(
                        String.valueOf(System.currentTimeMillis()),
                        title,
                        author,
                        replyCount,
                        resolvedThreadUrl
                );
                thread.setImageUrl(resolveUrl(boardUrl, imageUrl));

                threads.add(thread);
            }
        }

        android.util.Log.d("Komica", "Parsed " + threads.size() + " search results");
        return threads;
    }

    private static String resolveUrl(String baseUrl, String href) {
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

    private static List<Thread> parseThreads(String html, String boardUrl) {
        List<Thread> threads = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        android.util.Log.d("Komica", "Starting to parse threads from board URL: " + boardUrl);

        Elements threadElements = doc.select("div.thread");
        android.util.Log.d("Komica", "Found " + threadElements.size() + " thread elements");

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
                replyCount = replyPosts.size() - 1;
                if (replyCount < 0) replyCount = 0;

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

    private static Thread parseThreadDetail(String html, String threadUrl) {
        Document doc = Jsoup.parse(html);

        android.util.Log.d("Komica", "Parsing thread detail from: " + threadUrl);

        String title = DEFAULT_TITLE;
        String author = DEFAULT_AUTHOR;
        String imageUrl = "";

        Element threadContainer = doc.selectFirst("div.thread");
        
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
