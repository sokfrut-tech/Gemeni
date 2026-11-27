package com.jarvis.breeno.assistant.tools

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToLong

data class WebSearchHit(
    val title: String,
    val url: String,
    val snippet: String,
)

data class WebPageContent(
    val title: String,
    val url: String,
    val text: String,
)

/**
 * Zero-cost online web search / URL reader (DuckDuckGo HTML + Wikipedia OpenSearch + Brave fallback)
 * and deterministic offline unit converter ported safely from OGAM tool capabilities.
 */
object JarvisWebTools {
    private val RU_LOCALE = Locale("ru", "RU")
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; OPPO PGEM10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    private val DDG_BLOCK_REGEX = Regex(
        """<a[^>]*class="[^"]*result__a[^"]*"[^>]*href="([^"]+)"[^>]*>([\s\S]*?)</a>([\s\S]{0,1200}?)""",
        RegexOption.IGNORE_CASE,
    )
    private val DDG_SNIPPET_REGEX = Regex(
        """class="[^"]*result__snippet[^"]*"[^>]*>([\s\S]*?)</(?:a|div|span)>""",
        RegexOption.IGNORE_CASE,
    )
    private val BRAVE_LINK_REGEX = Regex(
        """<a[^>]*href="(https?://(?!(?:search\.)?brave\.com)[^"]+)"[^>]*>([\s\S]{4,140}?)</a>""",
        RegexOption.IGNORE_CASE,
    )
    private val TITLE_REGEX = Regex("""<title[^>]*>([\s\S]*?)</title>""", RegexOption.IGNORE_CASE)
    private val STRIP_SCRIPT_REGEX = Regex("""<(script|style|noscript|svg)[^>]*>[\s\S]*?</\1>""", RegexOption.IGNORE_CASE)
    private val ENTITY_REGEX = Regex("""&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z]+);""")

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
        "mdash" to "—",
        "ndash" to "–",
        "laquo" to "«",
        "raquo" to "»",
    )

    fun validatePublicHttpUrl(raw: String, resolveDns: Boolean = false): URL? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() || it.isISOControl() }) return null
        val lower = trimmed.lowercase(Locale.ROOT)
        val candidate = if (lower.startsWith("http://") || lower.startsWith("https://")) {
            trimmed
        } else if (!lower.contains("://") && lower.contains('.')) {
            "https://$trimmed"
        } else {
            return null
        }

        val parsed = runCatching { URL(candidate) }.getOrNull() ?: return null
        val scheme = parsed.protocol.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null

        val host = parsed.host?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (host.isEmpty() || isPrivateOrLocalHostname(host)) return null

        if (resolveDns) {
            val addrs = runCatching { InetAddress.getAllByName(host).toList() }.getOrNull() ?: return null
            if (addrs.any { isDisallowedAddress(it) }) return null
        }
        return parsed
    }

    internal fun isPrivateOrLocalHostname(rawHost: String): Boolean {
        val host = rawHost.trim('[', ']').lowercase(Locale.ROOT)
        if (host == "localhost" ||
            host == "ip6-localhost" ||
            host.endsWith(".local") ||
            host.endsWith(".internal") ||
            host == "0.0.0.0" ||
            host == "::1" ||
            host == "::"
        ) {
            return true
        }
        // Check literal IPv4 ranges directly without requiring DNS
        val ipv4Parts = host.split('.')
        if (ipv4Parts.size == 4 && ipv4Parts.all { it.toIntOrNull() in 0..255 }) {
            val a = ipv4Parts[0].toInt()
            val b = ipv4Parts[1].toInt()
            return when {
                a == 0 || a == 10 || a == 127 -> true
                a == 169 && b == 254 -> true
                a == 172 && b in 16..31 -> true
                a == 192 && b == 168 -> true
                a == 100 && b in 64..127 -> true
                else -> false
            }
        }
        if (host.contains(':') && (host.startsWith("fe80") || host.startsWith("fc") || host.startsWith("fd"))) {
            return true
        }
        return false
    }

    private fun isDisallowedAddress(addr: InetAddress): Boolean =
        addr.isAnyLocalAddress ||
            addr.isLoopbackAddress ||
            addr.isLinkLocalAddress ||
            addr.isSiteLocalAddress ||
            addr.isMulticastAddress ||
            isPrivateOrLocalHostname(addr.hostAddress.orEmpty())

    suspend fun searchWeb(query: String, maxResults: Int = 5): Result<List<WebSearchHit>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanQuery = query.trim()
            require(cleanQuery.isNotEmpty()) { "Пустой поисковый запрос" }
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")

            // 1. Primary: DuckDuckGo HTML endpoint (zero keys, zero billing)
            val ddgHits = runCatching {
                val html = fetchUrlText("https://html.duckduckgo.com/html/?q=$encoded", checkDns = false)
                parseDuckDuckGoHtml(html, maxResults)
            }.getOrDefault(emptyList())
            if (ddgHits.isNotEmpty()) return@runCatching ddgHits

            // 2. Fallback: Russian Wikipedia OpenSearch JSON API
            val wikiHits = runCatching {
                val json = fetchUrlText(
                    "https://ru.wikipedia.org/w/api.php?action=opensearch&search=$encoded&limit=$maxResults&namespace=0&format=json",
                    checkDns = false,
                )
                parseWikipediaOpenSearchJson(json, maxResults)
            }.getOrDefault(emptyList())
            if (wikiHits.isNotEmpty()) return@runCatching wikiHits

            // 3. Fallback: Brave HTML
            val braveHtml = fetchUrlText("https://search.brave.com/search?q=$encoded&source=web", checkDns = false)
            parseBraveHtmlFallback(braveHtml, maxResults)
        }
    }

    suspend fun readWebPage(rawUrl: String, maxChars: Int = 3500): Result<WebPageContent> = withContext(Dispatchers.IO) {
        runCatching {
            val validated = validatePublicHttpUrl(rawUrl, resolveDns = true)
                ?: throw IllegalArgumentException("Недопустимый или локальный URL: $rawUrl")
            val html = fetchUrlText(validated.toExternalForm(), checkDns = true)
            val title = extractTitle(html).ifBlank { validated.host }
            val body = extractCleanPageText(html, maxChars)
            check(body.isNotBlank()) { "Страница не содержит читаемого текста" }
            WebPageContent(
                title = title,
                url = validated.toExternalForm(),
                text = body,
            )
        }
    }

    private fun fetchUrlText(rawUrl: String, checkDns: Boolean): String {
        val url = validatePublicHttpUrl(rawUrl, resolveDns = checkDns)
            ?: throw IllegalArgumentException("Заблокированный URL: $rawUrl")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.7")
        }
        return try {
            val status = conn.responseCode
            if (status in 300..399) {
                val loc = conn.getHeaderField("Location").orEmpty()
                val redirected = validatePublicHttpUrl(loc, resolveDns = checkDns)
                    ?: throw IllegalStateException("Редирект на небезопасный или некорректный URL ($status)")
                conn.disconnect()
                val conn2 = (redirected.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                return try {
                    check(conn2.responseCode in 200..299) { "HTTP ${conn2.responseCode}" }
                    conn2.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val buf = CharArray(256 * 1024)
                        val n = reader.read(buf)
                        if (n <= 0) "" else String(buf, 0, n)
                    }
                } finally {
                    conn2.disconnect()
                }
            }
            check(status in 200..299) { "Сервер вернул HTTP $status" }
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val sb = StringBuilder()
                val buf = CharArray(8192)
                while (sb.length < 400_000) {
                    val n = reader.read(buf)
                    if (n <= 0) break
                    sb.appendRange(buf, 0, n)
                }
                sb.toString()
            }
        } finally {
            conn.disconnect()
        }
    }

    fun parseDuckDuckGoHtml(html: String, maxResults: Int = 5): List<WebSearchHit> {
        val out = mutableListOf<WebSearchHit>()
        for (match in DDG_BLOCK_REGEX.findAll(html)) {
            if (out.size >= maxResults) break
            val rawHref = decodeHtmlEntities(match.groupValues[1].trim())
            val resolvedUrl = resolveDdgRedirectUrl(rawHref)
            val title = normalizeWhitespace(decodeHtmlEntities(stripHtmlTags(match.groupValues[2])))
            val snippet = DDG_SNIPPET_REGEX.find(match.groupValues[3])?.let {
                normalizeWhitespace(decodeHtmlEntities(stripHtmlTags(it.groupValues[1])))
            }.orEmpty()
            if (resolvedUrl.startsWith("http") && title.isNotBlank()) {
                out.add(WebSearchHit(title = title, url = resolvedUrl, snippet = snippet))
            }
        }
        return out
    }

    private fun resolveDdgRedirectUrl(rawHref: String): String {
        val idx = rawHref.indexOf("uddg=")
        if (idx >= 0) {
            val encoded = rawHref.substring(idx + 5).substringBefore('&')
            val decoded = runCatching { java.net.URLDecoder.decode(encoded, "UTF-8") }.getOrNull()
            if (!decoded.isNullOrBlank()) return decoded
        }
        return if (rawHref.startsWith("//")) "https:$rawHref" else rawHref
    }

    fun parseWikipediaOpenSearchJson(json: String, maxResults: Int = 5): List<WebSearchHit> {
        val root = runCatching { JsonParser.parseString(json).asJsonArray }.getOrNull() ?: return emptyList()
        if (root.size() < 4) return emptyList()
        val titles = root[1].asJsonArray
        val descs = root[2].asJsonArray
        val urls = root[3].asJsonArray
        val out = mutableListOf<WebSearchHit>()
        val count = minOf(maxResults, titles.size(), urls.size())
        for (i in 0 until count) {
            val title = titles[i].asString.trim()
            val url = urls[i].asString.trim()
            val desc = if (i < descs.size()) descs[i].asString.trim() else ""
            if (title.isNotEmpty() && url.startsWith("http")) {
                out.add(WebSearchHit(title = title, url = url, snippet = desc))
            }
        }
        return out
    }

    fun parseBraveHtmlFallback(html: String, maxResults: Int = 5): List<WebSearchHit> {
        val out = mutableListOf<WebSearchHit>()
        val seenUrls = mutableSetOf<String>()
        for (match in BRAVE_LINK_REGEX.findAll(html)) {
            if (out.size >= maxResults) break
            val url = decodeHtmlEntities(match.groupValues[1].trim())
            val title = normalizeWhitespace(decodeHtmlEntities(stripHtmlTags(match.groupValues[2])))
            if (title.length >= 6 && seenUrls.add(url)) {
                out.add(WebSearchHit(title = title, url = url, snippet = ""))
            }
        }
        return out
    }

    fun extractTitle(html: String): String = TITLE_REGEX.find(html)?.let {
        normalizeWhitespace(decodeHtmlEntities(stripHtmlTags(it.groupValues[1])))
    }.orEmpty()

    fun extractCleanPageText(html: String, maxChars: Int = 3500): String {
        val noScripts = STRIP_SCRIPT_REGEX.replace(html, " ")
        val text = normalizeWhitespace(decodeHtmlEntities(stripHtmlTags(noScripts)))
        return if (text.length > maxChars) text.take(maxChars).trimEnd() + "…" else text
    }

    fun stripHtmlTags(raw: String): String {
        val sb = StringBuilder(raw.length)
        var inTag = false
        for (ch in raw) {
            when (ch) {
                '<' -> {
                    inTag = true
                    sb.append(' ')
                }
                '>' -> inTag = false
                else -> if (!inTag) sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun decodeHtmlEntities(text: String): String = ENTITY_REGEX.replace(text) { m ->
        val body = m.groupValues[1]
        when {
            body.startsWith("#x", ignoreCase = true) ->
                body.drop(2).toIntOrNull(16)?.let { code -> runCatching { String(Character.toChars(code)) }.getOrNull() } ?: m.value
            body.startsWith("#") ->
                body.drop(1).toIntOrNull()?.let { code -> runCatching { String(Character.toChars(code)) }.getOrNull() } ?: m.value
            else -> NAMED_ENTITIES[body.lowercase(Locale.ROOT)] ?: m.value
        }
    }

    private fun normalizeWhitespace(s: String): String = s.replace(Regex("\\s+"), " ").trim()

    fun convertUnitsOffline(rawQuery: String): String? {
        val clean = rawQuery.trim().lowercase(RU_LOCALE)
        val unitPattern = """(?:км|мм|см|м|мил[ьия]|кг|гр|г|фунт(?:ы|а|ов)?|°?\s*[cс]|°?\s*[fф]|гб|мб|кб)"""
        val match = Regex(
            """(?:сколько|конвертируй|переведи)?\s*(-?\d+(?:[.,]\d+)?)\s*($unitPattern)\s+(?:в|to|in)\s+($unitPattern)(?=$|[\s.,!?])""",
            RegexOption.IGNORE_CASE,
        ).find(clean) ?: return null

        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val from = normalizeUnit(match.groupValues[2])
        val to = normalizeUnit(match.groupValues[3])
        if (from == to) return "${formatNum(value)} $from = ${formatNum(value)} $to"

        val converted: Double? = when {
            // Length via meters
            from in setOf("мм", "см", "м", "км", "мили") && to in setOf("мм", "см", "м", "км", "мили") -> {
                val meters = when (from) {
                    "мм" -> value / 1000.0
                    "см" -> value / 100.0
                    "м" -> value
                    "км" -> value * 1000.0
                    "мили" -> value * 1609.344
                    else -> return null
                }
                when (to) {
                    "мм" -> meters * 1000.0
                    "см" -> meters * 100.0
                    "м" -> meters
                    "км" -> meters / 1000.0
                    "мили" -> meters / 1609.344
                    else -> null
                }
            }
            // Weight via grams
            from in setOf("г", "кг", "фунты") && to in setOf("г", "кг", "фунты") -> {
                val grams = when (from) {
                    "г" -> value
                    "кг" -> value * 1000.0
                    "фунты" -> value * 453.59237
                    else -> return null
                }
                when (to) {
                    "г" -> grams
                    "кг" -> grams / 1000.0
                    "фунты" -> grams / 453.59237
                    else -> null
                }
            }
            // Temperature
            from == "°C" && to == "°F" -> value * 9.0 / 5.0 + 32.0
            from == "°F" && to == "°C" -> (value - 32.0) * 5.0 / 9.0
            // Data size (binary MiB/KiB style 1024)
            from in setOf("кб", "мб", "гб") && to in setOf("кб", "мб", "гб") -> {
                val kb = when (from) {
                    "кб" -> value
                    "мб" -> value * 1024.0
                    "гб" -> value * 1024.0 * 1024.0
                    else -> return null
                }
                when (to) {
                    "кб" -> kb
                    "мб" -> kb / 1024.0
                    "гб" -> kb / (1024.0 * 1024.0)
                    else -> null
                }
            }
            else -> null
        }

        return converted?.let { "${formatNum(value)} $from = ${formatNum(it)} $to" }
    }

    private fun normalizeUnit(raw: String): String = when (raw.replace("°", "").trim().lowercase(RU_LOCALE)) {
        "миля", "мили", "миль" -> "мили"
        "гр", "г" -> "г"
        "фунт", "фунта", "фунтов" -> "фунты"
        "c", "с" -> "°C"
        "f", "ф" -> "°F"
        else -> raw.trim().lowercase(RU_LOCALE)
    }

    private fun formatNum(v: Double): String {
        val rounded = (v * 10_000.0).roundToLong() / 10_000.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }
}
