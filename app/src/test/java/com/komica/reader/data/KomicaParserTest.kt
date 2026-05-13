package com.komica.reader.data

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KomicaParserTest {
    @Test
    fun ParseQuoteContent_KeepsLineBreaks() {
        val element = Jsoup.parse("<div class=\"quote\">第一行<br>第二行<p>第三行</p></div>")
            .selectFirst("div.quote")!!

        assertEquals("第一行\n第二行\n第三行", KomicaParser.ParseQuoteContent(element))
    }

    @Test
    fun ResolveUrl_HandlesRelativeBoardImage() {
        val result = KomicaParser.ResolveUrl("https://komica1.org/00/index.htm", "src/123.jpg")

        assertEquals("https://komica1.org/00/src/123.jpg", result)
    }

    @Test
    fun ParseThreadDetail_ReadsPostsAndImages() {
        val html = """
            <html><body>
                <div class="post threadpost" data-no="100">
                    <span class="title">測試主題</span>
                    <span class="name">無名</span>
                    <span class="now">26/05/13</span>
                    <a class="file-thumb" href="src/100.jpg"><img class="img" src="thumb/100s.jpg"></a>
                    <div class="quote">本文<br>&gt;&gt;101</div>
                </div>
                <div class="post reply" data-no="101">
                    <span class="name">回文</span>
                    <div class="quote">回覆內容</div>
                </div>
            </body></html>
        """.trimIndent()

        val detail = KomicaParser.ParseThreadDetail(html, "https://komica1.org/00/pixmicat.php?res=100")

        assertEquals("測試主題", detail.title)
        assertEquals(2, detail.posts.size)
        assertEquals(100, detail.posts.first().number)
        assertTrue(detail.posts.first().imageUrl.endsWith("/00/src/100.jpg"))
        assertEquals("本文\n>>101", detail.posts.first().content)
    }
}
