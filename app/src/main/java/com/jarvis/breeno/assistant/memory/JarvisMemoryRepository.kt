package com.jarvis.breeno.assistant.memory

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * Explicitly user-approved, app-private memory.
 *
 * Call saveAfterUserConfirmation/updateAfterUserConfirmation/deleteAfterUserConfirmation only
 * after the UI has shown the exact content/action and the user has confirmed it.
 * This repository performs no network access, background capture, or implicit conversation
 * ingestion. Methods use synchronous SharedPreferences.commit(); call them from Dispatchers.IO.
 */
enum class JarvisMemoryKind {
    NOTE,
    PREFERENCE,
    FACT,
    PROJECT,
    TASK,
    REMINDER
}

data class JarvisMemoryEntry(
    val id: String,
    val kind: JarvisMemoryKind,
    val scope: String,
    val text: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

class JarvisMemoryRepository(
    context: Context,
    preferencesName: String = "jarvis_user_memory_v1"
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE
    )
    private val gson = Gson()
    private val listType = object : TypeToken<List<JarvisMemoryEntry>>() {}.type

    /**
     * Persist content only after the user has explicitly confirmed the exact text.
     * Returns null for invalid input, capacity exhaustion, or persistence failure.
     */
    @Synchronized
    fun saveAfterUserConfirmation(
        kind: JarvisMemoryKind,
        scope: String,
        text: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ): JarvisMemoryEntry? {
        val normalizedScope = scope.trim()
        val normalizedText = text.trim()
        if (normalizedScope.isEmpty() || normalizedScope.length > MAX_SCOPE_LENGTH) return null
        if (normalizedText.isEmpty() || normalizedText.length > MAX_TEXT_LENGTH) return null

        val current = readAll()
        if (current.size >= MAX_ENTRIES) return null

        val entry = JarvisMemoryEntry(
            id = UUID.randomUUID().toString(),
            kind = kind,
            scope = normalizedScope,
            text = normalizedText,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs
        )
        val updated = current + entry
        return if (persist(updated)) entry else null
    }

    @Synchronized
    fun list(scope: String? = null, kind: JarvisMemoryKind? = null): List<JarvisMemoryEntry> {
        val normalizedScope = scope?.trim()
        return readAll()
            .asSequence()
            .filter { normalizedScope.isNullOrEmpty() || it.scope == normalizedScope }
            .filter { kind == null || it.kind == kind }
            .sortedByDescending { it.updatedAtEpochMs }
            .toList()
    }

    /** Local literal search only; semantic/embedding search is a separate future capability. */
    @Synchronized
    fun search(query: String, scope: String? = null): List<JarvisMemoryEntry> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return emptyList()
        val normalizedScope = scope?.trim()
        return readAll()
            .asSequence()
            .filter { normalizedScope.isNullOrEmpty() || it.scope == normalizedScope }
            .filter { it.text.contains(normalizedQuery, ignoreCase = true) }
            .sortedByDescending { it.updatedAtEpochMs }
            .toList()
    }

    @Synchronized
    fun updateAfterUserConfirmation(
        id: String,
        replacementText: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Boolean {
        val normalizedText = replacementText.trim()
        if (normalizedText.isEmpty() || normalizedText.length > MAX_TEXT_LENGTH) return false
        val current = readAll()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return false
        val previous = current[index]
        val updated = current.toMutableList().apply {
            this[index] = previous.copy(text = normalizedText, updatedAtEpochMs = nowEpochMs)
        }
        return persist(updated)
    }

    @Synchronized
    fun deleteAfterUserConfirmation(id: String): Boolean {
        val current = readAll()
        val updated = current.filterNot { it.id == id }
        if (updated.size == current.size) return false
        return persist(updated)
    }

    @Synchronized
    fun clearScopeAfterUserConfirmation(scope: String): Boolean {
        val normalizedScope = scope.trim()
        if (normalizedScope.isEmpty()) return false
        val current = readAll()
        val updated = current.filterNot { it.scope == normalizedScope }
        if (updated.size == current.size) return false
        return persist(updated)
    }

    private fun readAll(): List<JarvisMemoryEntry> {
        val raw = preferences.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<JarvisMemoryEntry>>(raw, listType) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun persist(entries: List<JarvisMemoryEntry>): Boolean {
        return runCatching {
            preferences.edit().putString(KEY_ENTRIES, gson.toJson(entries)).commit()
        }.getOrDefault(false)
    }

    companion object {
        private const val KEY_ENTRIES = "entries"
        private const val MAX_ENTRIES = 500
        private const val MAX_SCOPE_LENGTH = 100
        private const val MAX_TEXT_LENGTH = 4_000
    }
}
