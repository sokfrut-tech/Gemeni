package com.jarvis.breeno.assistant.tools

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

data class McpServerConfig(
    val id: String,
    val name: String,
    val url: String,
    val apiKey: String = "",
    val enabled: Boolean = true,
)

data class McpToolInfo(
    val name: String,
    val description: String,
)

/**
 * HTTP/HTTPS JSON-RPC 2.0 MCP client (tools/list and tools/call) ported from OGAM.
 * Enforces strict http/https URL validation and keeps server credentials local only.
 */
object JarvisMcpClient {
    private val gson = Gson()

    fun validateMcpEndpointUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() || it.isISOControl() }) return null
        val lower = trimmed.lowercase(Locale.ROOT)
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
        return runCatching {
            val uri = URI(trimmed)
            val host = uri.host
            if (!host.isNullOrBlank()) uri.toASCIIString() else null
        }.getOrNull()
    }

    fun buildListToolsRequest(id: Int = 1): String =
        """{"jsonrpc":"2.0","id":$id,"method":"tools/list","params":{}}"""

    fun buildCallToolRequest(toolName: String, argumentsJson: String = "{}", id: Int = 2): String {
        val parsedArgs = runCatching { JsonParser.parseString(argumentsJson.ifBlank { "{}" }) }
            .getOrElse { JsonParser.parseString("{}") }
        val payload = mapOf(
            "jsonrpc" to "2.0",
            "id" to id,
            "method" to "tools/call",
            "params" to mapOf(
                "name" to toolName,
                "arguments" to parsedArgs,
            ),
        )
        return gson.toJson(payload)
    }

    fun parseToolsListResponse(json: String): List<McpToolInfo> {
        val root = JsonParser.parseString(json).asJsonObject
        if (root.has("error") && !root.get("error").isJsonNull) {
            val errObj = root.getAsJsonObject("error")
            error("Ошибка MCP: ${errObj.get("message")?.asString ?: errObj}")
        }
        val tools = root.getAsJsonObject("result")?.getAsJsonArray("tools") ?: return emptyList()
        return (0 until tools.size()).mapNotNull { idx ->
            val obj = tools[idx].asJsonObject
            val name = obj.get("name")?.asString?.trim().orEmpty()
            if (name.isEmpty()) null else McpToolInfo(
                name = name,
                description = obj.get("description")?.asString?.trim().orEmpty(),
            )
        }
    }

    fun parseCallToolResponse(json: String): String {
        val root = JsonParser.parseString(json).asJsonObject
        if (root.has("error") && !root.get("error").isJsonNull) {
            val errObj = root.getAsJsonObject("error")
            error("Ошибка MCP: ${errObj.get("message")?.asString ?: errObj}")
        }
        val result = root.getAsJsonObject("result") ?: return json
        val contentArray = result.getAsJsonArray("content")
        if (contentArray != null && contentArray.size() > 0) {
            val textParts = (0 until contentArray.size()).mapNotNull { idx ->
                contentArray[idx].asJsonObject.get("text")?.asString?.trim()
            }
            if (textParts.isNotEmpty()) return textParts.joinToString("\n")
        }
        return gson.toJson(result)
    }

    suspend fun listTools(server: McpServerConfig): Result<List<McpToolInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val safeUrl = validateMcpEndpointUrl(server.url)
                ?: error("Некорректный URL MCP-сервера (разрешены http:// и https://)")
            val response = postJson(safeUrl, buildListToolsRequest(), server.apiKey)
            parseToolsListResponse(response)
        }
    }

    suspend fun callTool(
        server: McpServerConfig,
        toolName: String,
        argumentsJson: String = "{}",
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val safeUrl = validateMcpEndpointUrl(server.url)
                ?: error("Некорректный URL MCP-сервера (разрешены http:// и https://)")
            val response = postJson(safeUrl, buildCallToolRequest(toolName, argumentsJson), server.apiKey)
            parseCallToolResponse(response)
        }
    }

    private fun postJson(urlStr: String, body: String, apiKey: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 8_000
            readTimeout = 15_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            }
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            check(code in 200..299) { "MCP-сервер вернул HTTP $code: ${text.take(180)}" }
            text
        } finally {
            conn.disconnect()
        }
    }
}

class McpServerStore(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_mcp_servers", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun list(): List<McpServerConfig> {
        val raw = prefs.getString(KEY_SERVERS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<McpServerConfig>>() {}.type
            gson.fromJson<List<McpServerConfig>>(raw, type).orEmpty()
        }.getOrElse { emptyList() }
    }

    fun save(servers: List<McpServerConfig>) {
        prefs.edit().putString(KEY_SERVERS, gson.toJson(servers)).apply()
    }

    companion object {
        private const val KEY_SERVERS = "mcp_servers_json"
    }
}
