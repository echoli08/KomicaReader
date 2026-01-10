package com.komica.reader.util

import com.komica.reader.model.Board
import com.komica.reader.model.BoardCategory
import com.komica.reader.model.Post
import com.komica.reader.model.Thread
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.regex.Pattern

object KomicaParser {
    private const val DEFAULT_TITLE = "Untitled"
    private const val DEFAULT_AUTHOR = "Anonymous"
    private const val BASE_URL = "http://komica1.org"

    @JvmStatic
    fun parseBoards(html: String): List<BoardCategory> {
        val categories = mutableListOf<BoardCategory>()
        val excludedCategories = arrayOf("聊天室", "外部連結", "失效連結")
        
        val doc = Jsoup.parse(html)
        val ulElements = doc.select("#list ul")
        for (ul in ulElements) {
            val categoryElement = ul.selectFirst("li.category") ?: continue
            
            val categoryName = categoryElement.text().trim()
            if (categoryName.isEmpty()) continue
            
            if (excludedCategories.any { categoryName.contains(it) }) {
                KLog.d("Excluding category: $categoryName")
                continue
            }
            
            val boards = mutableListOf<Board>()
            val boardElements = ul.select("li:not(.category) a")
            
            for (boardElement in boardElements) {
                val boardName = boardElement.text().trim()
                val boardUrl = boardElement.attr("href")
                
                if (boardName.isNotEmpty() && boardUrl.isNotEmpty()) {
                    val cleanUrl = boardUrl.replace("[?&].*$".toRegex(), "")
                    boards.add(Board(boardName, cleanUrl, ""))
                }
            }
            
            if (boards.isNotEmpty()) {
                categories.add(BoardCategory(categoryName, boards))
            }
        }
        
        return categories
    }

    @JvmStatic
    fun parseThreads(html: String, boardUrl: String): List<Thread> {
        val threads = mutableListOf<Thread>()
        val doc = Jsoup.parse(html)

        KLog.d("parseThreads: Parsing HTML (length=${html.length}) from $boardUrl")

        var threadElements = doc.select("div.thread")
        if (threadElements.isEmpty()) {
            KLog.w("parseThreads: No div.thread found! Trying fallback table layout.")
            threadElements = doc.select("form[name='delform'] > table")
        }
        
        KLog.d("parseThreads: Found ${threadElements.size} thread elements")

        for ((index, threadElement) in threadElements.withIndex()) {
            val postElement = threadElement.selectFirst("div.post")

            var title = DEFAULT_TITLE
            var author = DEFAULT_AUTHOR
            var threadUrl = ""
            var imageUrl = ""
            var postNumber = 0
            var replyCount = 0
            var lastReplyTime = ""
            var contentPreview = ""

            if (postElement != null) {
                postElement.selectFirst("span.title")?.let { title = it.text().trim() }
                postElement.selectFirst("span.name")?.let { author = it.text().trim() }

                postElement.selectFirst("span.qlink")?.let { qlink ->
                    val dataNo = qlink.attr("data-no")
                    if (dataNo.isNotEmpty()) {
                        postNumber = dataNo.toIntOrNull() ?: 0
                        threadUrl = "pixmicat.php?res=$dataNo"
                    }
                }

                postElement.selectFirst("a.file-thumb img.img")?.let { imageUrl = it.attr("src") }
                postElement.selectFirst("span.now")?.let { lastReplyTime = it.text().trim() }
                
                postElement.selectFirst("div.quote")?.let {
                    val content = parseQuoteContent(it)
                    val lines = content.split("\n")
                    val previewLines = lines.filter { it.trim().isNotEmpty() }.take(4)
                    
                    contentPreview = if (previewLines.isEmpty()) {
                        "(無文字內容)"
                    } else {
                        previewLines.joinToString("\n")
                    }
                }
                
                val replyPosts = threadElement.select("div.post.reply")
                val visibleReplyCount = replyPosts.size
                
                var omittedCount = 0
                threadElement.selectFirst("span.warn_txt2")?.let {
                    val matcher = Pattern.compile("""(\d+)""").matcher(it.text())
                    if (matcher.find()) {
                        // 繁體中文註解：避免 group 可能為空造成例外
                        omittedCount = matcher.group(1)?.toIntOrNull() ?: 0
                    }
                }
                
                replyCount = visibleReplyCount + omittedCount

                replyPosts.lastOrNull()?.selectFirst("span.now")?.let {
                    lastReplyTime = it.text().trim()
                }
            }

            if (title.isNotEmpty() && threadUrl.isNotEmpty()) {
                val resolvedThreadUrl = resolveUrl(boardUrl, threadUrl)
                val thread = Thread(
                    "${System.currentTimeMillis()}-$index-${(Math.random() * 10000).toInt()}",
                    title,
                    author,
                    replyCount,
                    resolvedThreadUrl
                ).apply {
                    this.postNumber = postNumber
                    this.imageUrl = resolveUrl(boardUrl, imageUrl)
                    this.lastReplyTime = lastReplyTime
                    this.contentPreview = contentPreview
                }

                threads.add(thread)
            }
        }

        return threads
    }

    @JvmStatic
    fun parseThreadDetail(html: String, threadUrl: String): Thread {
        val doc = Jsoup.parse(html)
        var title = DEFAULT_TITLE
        var author = DEFAULT_AUTHOR
        var imageUrl = ""

        val threadContainer = doc.selectFirst("div.thread")
        threadContainer?.selectFirst("div.post.threadpost")?.let { threadPost ->
            threadPost.selectFirst("span.title")?.let { title = it.text().trim() }
            threadPost.selectFirst("span.name")?.let { author = it.text().trim() }
            
            threadPost.selectFirst("a.file-thumb img.img")?.let {
                imageUrl = it.attr("src")
            }
            
            threadPost.selectFirst("a.file-thumb")?.let {
                val originalImageUrl = it.attr("href")
                if (originalImageUrl.isNotEmpty()) {
                    imageUrl = resolveUrl(threadUrl, originalImageUrl)
                }
            }
        }

        val posts = mutableListOf<Post>()
        val allPosts = doc.select("div.post")

        for (postElement in allPosts) {
            val dataNo = postElement.attr("data-no")
            val postNumber = dataNo.toIntOrNull() ?: (posts.size + 1)
            
            var postAuthor = DEFAULT_AUTHOR
            postElement.selectFirst("span.name")?.let { postAuthor = it.text().trim() }

            var content = ""
            postElement.selectFirst("div.quote")?.let { content = parseQuoteContent(it) }

            var postTime = ""
            postElement.selectFirst("span.now")?.let { postTime = it.text().trim() }

            var postImageUrl = ""
            var postThumbnailUrl = ""
            postElement.selectFirst("a.file-thumb img.img")?.let { postThumbnailUrl = it.attr("src") }
            postElement.selectFirst("a.file-thumb")?.let { postImageUrl = it.attr("href") }

            if (content.isNotEmpty() || postImageUrl.isNotEmpty()) {
                val postId = "${System.currentTimeMillis()}-${posts.size}"
                val post = Post(
                    postId, postAuthor, content, 
                    resolveUrl(threadUrl, postImageUrl), 
                    resolveUrl(threadUrl, postThumbnailUrl), 
                    postTime, postNumber
                )
                posts.add(post)
            }
        }

        return Thread(
            System.currentTimeMillis().toString(),
            title,
            author,
            posts.size - 1,
            threadUrl
        ).apply {
            this.posts = posts
            this.imageUrl = resolveUrl(threadUrl, imageUrl)
        }
    }

    @JvmStatic
    fun parseSearchResults(doc: Document, boardUrl: String): List<Thread> {
        val threads = mutableListOf<Thread>()
        val pageTitle = doc.title()
        
        val hasSearchMarker = pageTitle.contains("搜尋") || pageTitle.contains("Search") || pageTitle.contains("?")
        val hasIndexPagination = doc.selectFirst(".paginfo, .pages, .pginfo") != null
        
        if (hasIndexPagination && !hasSearchMarker) {
            KLog.w("parseSearchResults: Detected index page instead of search results.")
            return threads
        }

        val mainForm = doc.selectFirst("form#delform, form[name='delform']")
        val postElements = mainForm?.select("div.post, div[id^='r'], div[id^='p'], td.reply, td.post-body")
            ?: doc.select("div.post, td.reply, td.post-body")

        for (postElement in postElements) {
            if (postElement.hasClass("nav") || postElement.id() == "notice") continue

            var title = DEFAULT_TITLE
            var author = DEFAULT_AUTHOR
            var postNumber = 0
            var parentThreadNo = 0
            var contentPreview = ""
            var lastReplyTime = ""

            postElement.selectFirst("a[href*='res=']")?.let {
                val matcher = Pattern.compile("res=(\\d+)").matcher(it.attr("href"))
                if (matcher.find()) {
                    // 繁體中文註解：安全解析父串號
                    parentThreadNo = matcher.group(1)?.toIntOrNull() ?: 0
                }
            }

            postElement.selectFirst(".qlink, .now a, .relink")?.let {
                val numText = it.text().replace("[^0-9]".toRegex(), "")
                if (numText.isNotEmpty()) {
                    postNumber = numText.toIntOrNull() ?: 0
                }
            }
            
            if (postNumber <= 0) {
                postElement.selectFirst("input[type='checkbox']")?.let {
                    val valStr = it.attr("value")
                    if (valStr.matches("\\d+".toRegex())) postNumber = valStr.toInt()
                }
            }

            if (postNumber <= 0) continue

            postElement.selectFirst("div.quote, .comment, blockquote")?.let { contentPreview = parseQuoteContent(it) }
            
            val thumbElement = postElement.selectFirst("img.img, .file-thumb img")
            if (contentPreview.trim().length < 2 && thumbElement == null) continue

            val finalThreadId = if (parentThreadNo > 0) parentThreadNo else postNumber

            postElement.selectFirst("span.title, b, font[color='#cc1105']")?.let { 
                if (it.text().trim().isNotEmpty()) title = it.text().trim() 
            }

            postElement.selectFirst("span.name, .postername, font[color='#117743']")?.let { author = it.text().trim() }

            val imageUrl = thumbElement?.attr("src") ?: ""

            if (title == DEFAULT_TITLE || title.isEmpty()) {
                title = if (parentThreadNo > 0) {
                    "回覆於 No.$parentThreadNo"
                } else {
                    val lines = contentPreview.split("\n")
                    if (lines.isNotEmpty()) lines[0] else DEFAULT_TITLE
                }
                if (title.length > 40) title = title.substring(0, 40) + "..."
            }
            
            postElement.selectFirst("span.now, .postertime, font[size='-1']")?.let { lastReplyTime = it.text().trim() }

            val resolvedThreadUrl = resolveUrl(boardUrl, "pixmicat.php?res=$finalThreadId")
            val thread = Thread("search-$postNumber", title, author, 0, resolvedThreadUrl).apply {
                this.postNumber = postNumber
                this.imageUrl = resolveUrl(boardUrl, imageUrl)
                this.contentPreview = contentPreview
                this.lastReplyTime = lastReplyTime
            }

            threads.add(thread)
        }

        return threads
    }

    @JvmStatic
    fun parseQuoteContent(quoteElement: Element): String {
        var html = quoteElement.html()
        val BR_PLACEHOLDER = "\uFFFF"
        
        html = html.replace("<br\\s*/?>".toRegex(), BR_PLACEHOLDER)
        html = html.replace("<p>" , BR_PLACEHOLDER)
        html = html.replace("</p>" , BR_PLACEHOLDER)
        
        var text = Jsoup.parse(html).text()
        text = text.replace(BR_PLACEHOLDER, "\n")
        text = text.trim()
        text = text.replace(" +\n".toRegex(), "\n")
        text = text.replace("\n +".toRegex(), "\n")
        
        while (text.contains("\n\n\n")) {
            text = text.replace("\n\n\n", "\n\n")
        }
        
        return text
    }

    @JvmStatic
    fun resolveUrl(baseUrl: String, href: String?): String {
        if (href.isNullOrBlank()) return ""
        val trimmed = href.trim()

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        if (trimmed.startsWith("//")) {
            return "https:$trimmed"
        }
        if (trimmed.startsWith("/")) {
            return BASE_URL + trimmed
        }

        var base = baseUrl
        val queryIndex = base.indexOf('?')
        if (queryIndex >= 0) base = base.substring(0, queryIndex)
        val hashIndex = base.indexOf('#')
        if (hashIndex >= 0) base = base.substring(0, hashIndex)
        
        val lastSlash = base.lastIndexOf('/')
        val prefix = if (lastSlash >= 0) base.substring(0, lastSlash + 1) else "$base/"

        return prefix + trimmed
    }
}
