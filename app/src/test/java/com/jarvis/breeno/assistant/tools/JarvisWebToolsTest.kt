package com.jarvis.breeno.assistant.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisWebToolsTest {
    @Test
    fun blocksPrivateLocalhostAndUnsafeUrlsAndAllowsPublicHttp() {
        assertNull(JarvisWebTools.validatePublicHttpUrl("http://localhost:8080/status", resolveDns = false))
        assertNull(JarvisWebTools.validatePublicHttpUrl("http://127.0.0.1/api", resolveDns = false))
        assertNull(JarvisWebTools.validatePublicHttpUrl("http://192.168.1.10/admin", resolveDns = false))
        assertNull(JarvisWebTools.validatePublicHttpUrl("http://10.0.0.5/", resolveDns = false))
        assertNull(JarvisWebTools.validatePublicHttpUrl("http://172.16.10.2/", resolveDns = false))
        assertNull(JarvisWebTools.validatePublicHttpUrl("http://169.254.169.254/latest/meta-data", resolveDns = false))
        assertNull(JarvisWebTools.validatePublicHttpUrl("http://100.64.1.2/", resolveDns = false))
        assertNull(JarvisWebTools.validatePublicHttpUrl("http://[::1]:11434/", resolveDns = false))
        assertNull(JarvisWebTools.validatePublicHttpUrl("file:///etc/passwd", resolveDns = false))
        assertNull(JarvisWebTools.validatePublicHttpUrl("javascript:alert(1)", resolveDns = false))

        val wiki = JarvisWebTools.validatePublicHttpUrl("https://ru.wikipedia.org/wiki/Qwen", resolveDns = false)
        assertNotNull(wiki)
        assertEquals("ru.wikipedia.org", wiki?.host)

        val bare = JarvisWebTools.validatePublicHttpUrl("huggingface.co/dphn/Dolphin3.0-Qwen2.5-1.5B", resolveDns = false)
        assertEquals("https://huggingface.co/dphn/Dolphin3.0-Qwen2.5-1.5B", bare?.toExternalForm())
    }

    @Test
    fun parsesDuckDuckGoAndWikipediaResultsCleanly() {
        val ddgHtml = """
            <div class="result">
              <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fhuggingface.co%2Fdphn%2FDolphin3.0-Qwen2.5-1.5B&amp;rut=1">Dolphin 3.0 &amp; Qwen2.5</a>
              <div class="result__snippet">Открытая модель &laquo;без цензуры&raquo; на <b>Qwen 2.5</b>.</div>
            </div>
        """.trimIndent()
        val ddgHits = JarvisWebTools.parseDuckDuckGoHtml(ddgHtml, maxResults = 3)
        assertEquals(1, ddgHits.size)
        assertEquals("https://huggingface.co/dphn/Dolphin3.0-Qwen2.5-1.5B", ddgHits[0].url)
        assertEquals("Dolphin 3.0 & Qwen2.5", ddgHits[0].title)
        assertEquals("Открытая модель «без цензуры» на Qwen 2.5.", ddgHits[0].snippet)

        val wikiJson = """["Qwen",["Qwen","Qwen2.5"],["Семейство LLM","Открытые модели"],["https://ru.wikipedia.org/wiki/Qwen","https://ru.wikipedia.org/wiki/Qwen2"]]"""
        val wikiHits = JarvisWebTools.parseWikipediaOpenSearchJson(wikiJson, maxResults = 5)
        assertEquals(2, wikiHits.size)
        assertEquals("Qwen", wikiHits[0].title)
        assertEquals("https://ru.wikipedia.org/wiki/Qwen", wikiHits[0].url)
        assertEquals("Семейство LLM", wikiHits[0].snippet)
    }

    @Test
    fun extractsTitleCleanPageTextAndConvertsUnitsOffline() {
        val html = """
            <html><head><title>Jarvis &mdash; Тест</title><style>.hidden{display:none;}</style></head>
            <body><script>console.log("secret");</script><h1>Заголовок</h1><p>Основной&nbsp;текст статьи про локальный ИИ.</p></body></html>
        """.trimIndent()
        assertEquals("Jarvis — Тест", JarvisWebTools.extractTitle(html))
        val text = JarvisWebTools.extractCleanPageText(html, maxChars = 120)
        assertTrue(text.contains("Заголовок"))
        assertTrue(text.contains("Основной текст статьи про локальный ИИ."))
        assertTrue(!text.contains("console.log"))
        assertTrue(!text.contains("display:none"))

        assertEquals("5 км = 5000 м", JarvisWebTools.convertUnitsOffline("5 км в м"))
        assertEquals("10 кг = 22.0462 фунты", JarvisWebTools.convertUnitsOffline("Переведи 10 кг в фунты"))
        assertEquals("100 °C = 212 °F", JarvisWebTools.convertUnitsOffline("100 °C в °F"))
        assertEquals("2 гб = 2048 мб", JarvisWebTools.convertUnitsOffline("2 гб в мб"))
        assertNull(JarvisWebTools.convertUnitsOffline("Какая сегодня погода в Алматы"))
    }
}
