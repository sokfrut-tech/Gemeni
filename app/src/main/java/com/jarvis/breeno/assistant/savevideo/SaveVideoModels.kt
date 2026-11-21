package com.jarvis.breeno.assistant.savevideo

import java.net.URI
import java.util.Locale

enum class SaveVideoSource(val displayName: String) {
    YOUTUBE("YouTube"),
    INSTAGRAM("Instagram"),
    TIKTOK("TikTok"),
    DIRECT_MEDIA("Прямое видео"),
    WEB_PAGE("Веб-ссылка"),
}

enum class SaveVideoQuality(val label: String) {
    AUTO("Авто"),
    HD_1080("1080p"),
    HD_720("720p"),
    AUDIO_ONLY("Аудио"),
}

enum class SaveVideoStatus(val label: String) {
    QUEUED("В очереди"),
    INSPECTING("Проверка"),
    DOWNLOADING("Загрузка"),
    COMPLETED("Готово"),
    FAILED("Ошибка"),
    CANCELLED("Отменено"),
}

data class SaveVideoMetadata(
    val normalizedUrl: String,
    val source: SaveVideoSource,
    val title: String,
    val contentType: String?,
    val contentLengthBytes: Long?,
    val directDownloadable: Boolean,
)

data class SaveVideoTask(
    val id: String,
    val url: String,
    val source: SaveVideoSource,
    val quality: SaveVideoQuality,
    val title: String,
    val status: SaveVideoStatus,
    val progressPercent: Int = 0,
    val contentType: String? = null,
    val totalBytes: Long? = null,
    val downloadedBytes: Long = 0L,
    val localFilePath: String? = null,
    val errorMessage: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

object SaveVideoUrlParser {
    private val directMediaExtensions = setOf(".mp4", ".webm", ".mkv", ".mov", ".m4v", ".mp3", ".m4a")

    fun normalizeAndValidate(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            return null
        }
        return runCatching {
            val uri = URI(withScheme)
            val host = uri.host?.lowercase(Locale.US)
            if (host.isNullOrBlank()) null else uri.toString()
        }.getOrNull()
    }

    fun detectSource(url: String): SaveVideoSource {
        val uri = runCatching { URI(url) }.getOrNull() ?: return SaveVideoSource.WEB_PAGE
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        val path = uri.path?.lowercase(Locale.US).orEmpty()
        if (directMediaExtensions.any { path.endsWith(it) }) {
            return SaveVideoSource.DIRECT_MEDIA
        }
        return when {
            host.contains("youtube.com") || host.contains("youtu.be") -> SaveVideoSource.YOUTUBE
            host.contains("instagram.com") || host.contains("instagr.am") -> SaveVideoSource.INSTAGRAM
            host.contains("tiktok.com") -> SaveVideoSource.TIKTOK
            else -> SaveVideoSource.WEB_PAGE
        }
    }

    fun extractHtmlTitle(html: String): String? {
        val ogTitle = Regex(
            """<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)["']""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)?.groupValues?.getOrNull(1)?.trim()
        if (!ogTitle.isNullOrBlank()) return decodeBasicEntities(ogTitle)

        val ogTitleRev = Regex(
            """<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:title["']""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)?.groupValues?.getOrNull(1)?.trim()
        if (!ogTitleRev.isNullOrBlank()) return decodeBasicEntities(ogTitleRev)

        val titleTag = Regex(
            """<title[^>]*>(.*?)</title>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)?.groupValues?.getOrNull(1)?.trim()
        return titleTag?.takeIf { it.isNotBlank() }?.let(::decodeBasicEntities)
    }

    private fun decodeBasicEntities(value: String): String =
        value.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
}
