package com.komica.reader.util

import org.junit.Assert.*
import org.junit.Test

class KomicaParserTest {

    @Test
    fun testParseBoards() {
        val html = """
            <div id="list">
                <ul>
                    <li class="category">綜合</li>
                    <li><a href="pixmicat.php?board=all">綜合</a></li>
                    <li><a href="pixmicat.php?board=cat">貓</a></li>
                </ul>
                <ul>
                    <li class="category">聊天室</li>
                    <li><a href="chat/">聊天</a></li>
                </ul>
            </div>
        """.trimIndent()
        
        val categories = KomicaParser.parseBoards(html)
        
        assertNotNull(categories)
        assertEquals(1, categories.size) // "聊天室" should be excluded
        assertEquals("綜合", categories[0].name)
        assertEquals(2, categories[0].boards.size)
        assertEquals("綜合", categories[0].boards[0].name)
        assertEquals("pixmicat.php", categories[0].boards[0].url) // cleaned URL
    }

    @Test
    fun testParseThreads() {
        val html = """
            <div class="thread">
                <div class="post" data-no="12345">
                    <span class="title">測試標題</span>
                    <span class="name">測試名</span>
                    <span class="now">2026/01/05 12:00:00</span>
                    <span class="qlink" data-no="12345"></span>
                    <a class="file-thumb" href="src/123.jpg"><img class="img" src="thumb/123s.jpg"></a>
                    <div class="quote">這是內容第一行<br>這是內容第二行</div>
                </div>
                <div class="post reply" data-no="12346">
                    <span class="now">2026/01/05 12:05:00</span>
                    <div class="quote">回覆內容</div>
                </div>
            </div>
        """.trimIndent()
        
        val threads = KomicaParser.parseThreads(html, "http://komica1.org/test/")
        
        assertNotNull(threads)
        assertEquals(1, threads.size)
        val thread = threads[0]
        assertEquals("測試標題", thread.title)
        assertEquals(12345, thread.postNumber)
        assertEquals("2026/01/05 12:05:00", thread.lastReplyTime) // From the last reply
        assertTrue(thread.contentPreview.contains("這是內容第一行"))
        assertEquals("http://komica1.org/test/pixmicat.php?res=12345", thread.url)
    }

    @Test
    fun testResolveUrl() {
        val base = "http://example.com/board/index.html"
        
        assertEquals("http://example.com/board/test.jpg", KomicaParser.resolveUrl(base, "test.jpg"))
        assertEquals("http://komica1.org/global.css", KomicaParser.resolveUrl(base, "/global.css"))
        assertEquals("https://other.com/img.png", KomicaParser.resolveUrl(base, "https://other.com/img.png"))
        assertEquals("https://cdn.com/img.png", KomicaParser.resolveUrl(base, "//cdn.com/img.png"))
    }

    @Test
    fun testParseQuoteContent() {
        val html = "<div>第一行<br>第二行<p>段落</p>  多餘空白  </div>"
        val element = org.jsoup.Jsoup.parse(html).selectFirst("div")!!
        val content = KomicaParser.parseQuoteContent(element)
        
        assertEquals("第一行\n第二行\n段落\n多餘空白", content)
    }
}
