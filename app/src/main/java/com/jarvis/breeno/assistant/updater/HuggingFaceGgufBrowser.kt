package com.jarvis.breeno.assistant.updater

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class HfRepoSummary(
    val repoId: String,
    val downloads: Long,
    val likes: Int,
    val tags: List<String>,
)

data class HfGgufFile(
    val repoId: String,
    val fileName: String,
    val sizeBytes: Long,
    val downloadUrl: String,
)

/**
 * Searches public GGUF repositories on Hugging Face (with hf-mirror fallback)
 * so the user can download open / Russian GGUF models directly into LocalLlmModelManager custom packs.
 */
object HuggingFaceGgufBrowser {
    private val VALID_REPO_ID = Regex("""^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$""")

    suspend fun searchGgufRepos(query: String, limit: Int = 12): Result<List<HfRepoSummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(query.trim(), "UTF-8")
                val safeLimit = limit.coerceIn(1, 25)
                val path = "/api/models?search=$encoded&filter=gguf&sort=downloads&direction=-1&limit=$safeLimit"
                val json = getFromPrimaryOrMirror(path)
                parseSearchResults(json)
            }
        }

    suspend fun listGgufFiles(repoId: String): Result<List<HfGgufFile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanRepo = repoId.trim()
                require(VALID_REPO_ID.matches(cleanRepo)) { "Некорректный идентификатор репозитория Hugging Face" }
                val path = "/api/models/$cleanRepo/tree/main"
                val json = getFromPrimaryOrMirror(path)
                parseRepoGgufFiles(cleanRepo, json)
            }
        }

    fun parseSearchResults(json: String): List<HfRepoSummary> {
        val array = JsonParser.parseString(json).asJsonArray
        return (0 until array.size()).mapNotNull { idx ->
            val obj = array[idx].asJsonObject
            val id = obj.get("id")?.asString?.trim().orEmpty()
            if (id.isEmpty()) return@mapNotNull null
            val downloads = obj.get("downloads")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
            val likes = obj.get("likes")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
            val tags = obj.getAsJsonArray("tags")?.mapNotNull { tag ->
                tag.takeIf { it.isJsonPrimitive }?.asString
            } ?: emptyList()
            HfRepoSummary(repoId = id, downloads = downloads, likes = likes, tags = tags.take(6))
        }
    }

    fun parseRepoGgufFiles(repoId: String, treeJson: String): List<HfGgufFile> {
        val array = JsonParser.parseString(treeJson).asJsonArray
        return (0 until array.size()).mapNotNull { idx ->
            val obj = array[idx].asJsonObject
            val type = obj.get("type")?.asString.orEmpty()
            val path = obj.get("path")?.asString?.trim().orEmpty()
            if (type != "file" || !path.endsWith(".gguf", ignoreCase = true)) {
                return@mapNotNull null
            }
            val size = obj.get("size")?.takeIf { it.isJsonPrimitive }?.asLong ?: -1L
            val encodedPath = path.split('/').joinToString("/") { segment ->
                URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
            }
            HfGgufFile(
                repoId = repoId,
                fileName = path.substringAfterLast('/'),
                sizeBytes = size,
                downloadUrl = "https://huggingface.co/$repoId/resolve/main/$encodedPath",
            )
        }.sortedBy { it.sizeBytes.takeIf { s -> s > 0 } ?: Long.MAX_VALUE }
    }

    private fun getFromPrimaryOrMirror(apiPath: String): String {
        val hosts = listOf("https://huggingface.co", "https://hf-mirror.com")
        var lastErr: Throwable? = null
        for (base in hosts) {
            try {
                val conn = (URL("$base$apiPath").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 15_000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "Jarvis-Android/1.0")
                }
                return try {
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    check(code in 200..299) { "HTTP $code от $base" }
                    body
                } finally {
                    conn.disconnect()
                }
            } catch (err: Throwable) {
                lastErr = err
            }
        }
        throw lastErr ?: IllegalStateException("Не удалось подключиться к Hugging Face")
    }
}
