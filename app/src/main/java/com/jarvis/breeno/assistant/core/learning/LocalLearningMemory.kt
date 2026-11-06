package com.jarvis.breeno.assistant.core.learning

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal on-device correction memory. It stores only explicitly approved
 * input -> correction pairs in app-private SharedPreferences. It has no
 * network client, analytics hook, or cloud sync path.
 *
 * This is memory/retrieval learning, NOT neural model-weight training.
 */
data class LearningCorrection(
    val normalizedInput: String,
    val correction: String,
    val updatedAtEpochMillis: Long
)

interface LocalLearningStore {
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
    fun readAll(): List<LearningCorrection>
    fun writeAll(entries: List<LearningCorrection>)
    fun clear()
}

class LocalLearningEngine(
    private val store: LocalLearningStore,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    init {
        require(maxEntries in 1..MAX_ALLOWED_ENTRIES)
    }

    fun isEnabled(): Boolean = store.isEnabled()

    fun setEnabled(enabled: Boolean) = store.setEnabled(enabled)

    fun rememberCorrection(
        originalUtterance: String,
        correctedInterpretation: String,
        nowEpochMillis: Long
    ): Boolean {
        if (!store.isEnabled()) return false
        val key = normalize(originalUtterance)
        val value = correctedInterpretation.trim()
        if (key.isBlank() || value.isBlank()) return false
        if (key.length > MAX_TEXT_LENGTH || value.length > MAX_TEXT_LENGTH) return false

        val entries = store.readAll()
            .filterNot { it.normalizedInput == key }
            .toMutableList()
        entries.add(LearningCorrection(key, value, nowEpochMillis))
        store.writeAll(entries.sortedByDescending { it.updatedAtEpochMillis }.take(maxEntries))
        return true
    }

    fun findCorrection(utterance: String): String? {
        if (!store.isEnabled()) return null
        val key = normalize(utterance)
        if (key.isBlank()) return null
        return store.readAll().firstOrNull { it.normalizedInput == key }?.correction
    }

    fun clear() = store.clear()

    private fun normalize(text: String): String =
        text.trim().lowercase().replace(Regex("\\s+"), " ")

    companion object {
        const val DEFAULT_MAX_ENTRIES = 100
        const val MAX_ALLOWED_ENTRIES = 500
        const val MAX_TEXT_LENGTH = 240
    }
}

/** App-private persistence only. Android backup/cloud transfer is not enabled here. */
class AndroidPrivateLearningStore(context: Context) : LocalLearningStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    override fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)

    override fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).commit()
    }

    override fun readAll(): List<LearningCorrection> {
        val raw = preferences.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val input = item.optString("input")
                    val correction = item.optString("correction")
                    val timestamp = item.optLong("updatedAt", 0L)
                    if (input.isNotBlank() && correction.isNotBlank()) {
                        add(LearningCorrection(input, correction, timestamp))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun writeAll(entries: List<LearningCorrection>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("input", entry.normalizedInput)
                    .put("correction", entry.correction)
                    .put("updatedAt", entry.updatedAtEpochMillis)
            )
        }
        preferences.edit().putString(KEY_ENTRIES, array.toString()).commit()
    }

    override fun clear() {
        preferences.edit().remove(KEY_ENTRIES).commit()
    }

    private companion object {
        const val PREFS_NAME = "jarvis_local_learning"
        const val KEY_ENABLED = "learning_enabled"
        const val KEY_ENTRIES = "corrections_v1"
    }
}
