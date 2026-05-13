package com.komica.reader.data

import com.komica.reader.model.Board
import com.komica.reader.model.BoardCategory
import com.komica.reader.model.KomicaThread
import com.komica.reader.model.Post
import com.komica.reader.model.ReplyForm
import com.komica.reader.model.ThreadDetail
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object KomicaParser {
    private const val BaseUrl = "https://komica1.org"

    fun ParseBoards(html: String): List<BoardCategory> {
        val excludedCategories = setOf("聊天室", "外部連結", "失效連結")
        val document = Jsoup.parse(html, BaseUrl)
        return document.select("#list ul").mapNotNull { list ->
            val categoryName = list.selectFirst("li.category")?.text()?.trim().orEmpty()
            if (categoryName.isBlank() || excludedCategories.any { categoryName.contains(it) }) {
                return@mapNotNull null
            }

            val boards = list.select("li:not(.category) a")
                .mapNotNull { boardElement -> ParseBoard(categoryName, boardElement) }

            if (boards.isEmpty()) null else BoardCategory(categoryName, boards)
        }
    }

    fun ParseThreads(html: String, boardUrl: String): List<KomicaThread> {
        val document = Jsoup.parse(html, boardUrl)
        val threadElements = document.select("div.thread").ifEmpty {
            document.select("form[name='delform'] > table")
        }

        return threadElements.mapIndexedNotNull { index, threadElement ->
            val postElement = threadElement.selectFirst("div.post") ?: return@mapIndexedNotNull null
            val postNumber = postElement.selectFirst("span.qlink")
                ?.attr("data-no")
                ?.toIntOrNull()
                ?: return@mapIndexedNotNull null

            val title = postElement.selectFirst("span.title")?.text()?.trim().orEmpty()
                .ifBlank { "Untitled" }
            val author = postElement.selectFirst("span.name")?.text()?.trim().orEmpty()
                .ifBlank { "Anonymous" }
            val preview = postElement.selectFirst("div.quote")?.let { ParseQuoteContent(it) }.orEmpty()
            val replyPosts = threadElement.select("div.post.reply")
            val omittedCount = threadElement.selectFirst("span.warn_txt2")
                ?.text()
                ?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }
                ?: 0
            val lastReplyTime = replyPosts.lastOrNull()?.selectFirst("span.now")?.text()?.trim()
                ?: postElement.selectFirst("span.now")?.text()?.trim().orEmpty()
            val imageUrl = postElement.selectFirst("a.file-thumb img.img")?.attr("src").orEmpty()
            val sortKey = ParseKomicaTimeSortKey(lastReplyTime)

            KomicaThread(
                id = "$postNumber-$index",
                title = title,
                author = author,
                replyCount = replyPosts.size + omittedCount,
                url = ResolveUrl(boardUrl, "pixmicat.php?res=$postNumber"),
                postNumber = postNumber,
                imageUrl = ResolveUrl(boardUrl, imageUrl),
                contentPreview = preview.lineSequence().filter { it.isNotBlank() }.take(4).joinToString("\n"),
                lastReplyTime = lastReplyTime,
                lastReplySortKey = sortKey
            )
        }
    }

    fun ParseThreadDetail(html: String, threadUrl: String): ThreadDetail {
        val document = Jsoup.parse(html, threadUrl)
        val posts = document.select("div.post").mapIndexedNotNull { index, postElement ->
            ParsePost(index, postElement, threadUrl)
        }
        val title = document.selectFirst("div.post.threadpost span.title, div.post span.title")
            ?.text()
            ?.trim()
            .orEmpty()
            .ifBlank { posts.firstOrNull()?.content?.lineSequence()?.firstOrNull()?.take(40).orEmpty() }
            .ifBlank { "討論串" }

        return ThreadDetail(
            title = title,
            url = threadUrl,
            posts = posts
        )
    }

    fun ParseReplyForm(html: String, threadUrl: String): ReplyForm {
        val document = Jsoup.parse(html, threadUrl)
        val form = document.select("form[action]").firstOrNull { element ->
            element.select("textarea, input[type=file], input[name=com]").isNotEmpty()
        } ?: error("找不到網站回覆表單")

        val hiddenFields = form.select("input[type=hidden][name]").associate { input ->
            input.attr("name") to input.attr("value")
        }

        return ReplyForm(
            actionUrl = ResolveUrl(threadUrl, form.attr("action")),
            method = form.attr("method").ifBlank { "post" }.lowercase(),
            hiddenFields = hiddenFields,
            nameField = FindFieldName(form, "name", "fname"),
            emailField = FindFieldName(form, "email", "mail"),
            titleField = FindFieldName(form, "sub", "title"),
            contentField = FindFieldName(form, "com", "comment"),
            passwordField = FindFieldName(form, "pwd", "password"),
            fileField = FindFileFieldName(form)
        )
    }

    fun ParseQuoteContent(element: Element): String {
        val marker = "\uFFFF"
        val html = element.html()
            .replace("<br\\s*/?>".toRegex(), marker)
            .replace("<p>", marker)
            .replace("</p>", marker)
        return Jsoup.parse(html)
            .text()
            .replace(marker, "\n")
            .trim()
            .replace(" +\n".toRegex(), "\n")
            .replace("\n +".toRegex(), "\n")
    }

    fun ResolveUrl(baseUrl: String, href: String?): String {
        val value = href?.trim().orEmpty()
        if (value.isBlank()) return ""
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        if (value.startsWith("//")) return "https:$value"
        if (value.startsWith("/")) return BaseUrl + value

        val cleanBase = baseUrl.substringBefore('?').substringBefore('#')
        val prefix = cleanBase.substringBeforeLast('/', missingDelimiterValue = cleanBase) + "/"
        return prefix + value
    }

    private fun ParseBoard(categoryName: String, boardElement: Element): Board? {
        val name = boardElement.text().trim()
        val rawUrl = boardElement.attr("href").trim()
        if (name.isBlank() || rawUrl.isBlank()) return null
        val cleanUrl = NormalizeBoardUrl(rawUrl)
        val resolvedUrl = ResolveUrl(BaseUrl, cleanUrl)
        if (!IsSupportedBoardUrl(resolvedUrl)) return null
        return Board(
            name = name,
            url = resolvedUrl,
            description = "$categoryName 看板",
            categoryName = categoryName
        )
    }

    private fun FindFieldName(form: Element, vararg candidates: String): String {
        candidates.forEach { candidate ->
            form.select("[name]").firstOrNull { it.attr("name").equals(candidate, ignoreCase = true) }?.let {
                return it.attr("name")
            }
        }
        return ""
    }

    private fun FindFileFieldName(form: Element): String {
        return form.select("input[type=file][name]").firstOrNull()?.attr("name").orEmpty()
    }

    private fun NormalizeBoardUrl(rawUrl: String): String {
        val value = rawUrl.trim()
        if (value.endsWith("?")) return value.dropLast(1)
        return value
    }

    private fun IsSupportedBoardUrl(url: String): Boolean {
        return url.contains("index.htm", ignoreCase = true) ||
            url.contains("index.html", ignoreCase = true) ||
            url.contains("pixmicat.php", ignoreCase = true)
    }

    private fun ParsePost(index: Int, postElement: Element, threadUrl: String): Post? {
        val number = postElement.attr("data-no").toIntOrNull()
            ?: postElement.selectFirst("span.qlink")?.attr("data-no")?.toIntOrNull()
            ?: 0
        val author = postElement.selectFirst("span.name")?.text()?.trim().orEmpty()
            .ifBlank { "Anonymous" }
        val content = postElement.selectFirst("div.quote")?.let { ParseQuoteContent(it) }.orEmpty()
        val time = postElement.selectFirst("span.now")?.text()?.trim().orEmpty()
        val imageUrl = postElement.selectFirst("a.file-thumb")?.attr("href").orEmpty()
        val thumbnailUrl = postElement.selectFirst("a.file-thumb img.img")?.attr("src").orEmpty()

        if (content.isBlank() && imageUrl.isBlank() && thumbnailUrl.isBlank()) return null

        return Post(
            id = if (number > 0) number.toString() else "post-$index",
            author = author,
            content = content,
            imageUrl = ResolveUrl(threadUrl, imageUrl),
            thumbnailUrl = ResolveUrl(threadUrl, thumbnailUrl),
            time = time,
            number = number
        )
    }

    private fun ParseKomicaTimeSortKey(value: String): Long {
        val numbers = Regex("\\d+").findAll(value).map { it.value.toIntOrNull() ?: 0 }.toList()
        if (numbers.size < 3) return 0L

        val rawYear = numbers[0]
        val year = when {
            rawYear >= 2000 -> rawYear
            rawYear >= 70 -> 1900 + rawYear
            else -> 2000 + rawYear
        }
        val month = numbers.getOrElse(1) { 1 }.coerceIn(1, 12)
        val day = numbers.getOrElse(2) { 1 }.coerceIn(1, 31)
        val hour = numbers.getOrElse(3) { 0 }.coerceIn(0, 23)
        val minute = numbers.getOrElse(4) { 0 }.coerceIn(0, 59)
        val second = numbers.getOrElse(5) { 0 }.coerceIn(0, 59)

        return year * 10_000_000_000L +
            month * 100_000_000L +
            day * 1_000_000L +
            hour * 10_000L +
            minute * 100L +
            second
    }
}
