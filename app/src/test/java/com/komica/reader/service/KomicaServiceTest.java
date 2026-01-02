package com.komica.reader.service;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.List;
import com.komica.reader.model.BoardCategory;
import com.komica.reader.model.Thread;

public class KomicaServiceTest {

    @Test
    public void testParseBoards() {
        String html = "<div id=\"list\"><ul><li class=\"category\">綜合</li><li><a href=\"綜合/\">綜合</a></li></ul></div>";
        List<BoardCategory> categories = KomicaService.parseBoards(html);
        
        assertNotNull(categories);
        assertFalse(categories.isEmpty());
        assertEquals("綜合", categories.get(0).getName());
        assertEquals("綜合", categories.get(0).getBoards().get(0).getName());
    }

    @Test
    public void testParseThreads() {
        String html = "<div class=\"thread\"><div class=\"post\" data-no=\"123\"><span class=\"title\">標題</span><span class=\"name\">作者</span><span class=\"qlink\" data-no=\"123\"></span><div class=\"quote\">內容</div></div></div>";
        List<Thread> threads = KomicaService.parseThreads(html, "http://komica1.org/test/");
        
        assertNotNull(threads);
        assertFalse(threads.isEmpty());
        assertEquals("標題", threads.get(0).getTitle());
        assertEquals(123, threads.get(0).getPostNumber());
    }
}
