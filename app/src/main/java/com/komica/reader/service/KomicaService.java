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
        Document doc = Jsoup.parse(html);

        Elements ulElements = doc.select("#list ul");
        for (Element ul : ulElements) {
            Element categoryElement = ul.selectFirst("li.category");
            if (categoryElement == null) continue;

            String categoryName = categoryElement.text().trim();
            if (categoryName.isEmpty()) continue;

            List<Board> boards = new ArrayList<>();
            Elements boardElements = ul.select("li:not(.category) a");

            for (Element boardElement : boardElements) {
                String boardName = boardElement.text().trim();
                String boardUrl = boardElement.attr("href");

                if (!boardName.isEmpty() && !boardUrl.isEmpty()) {
                    boards.add(new Board(boardName, boardUrl, ""));
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
}
