package com.jarvis.breeno.assistant.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.jarvis.breeno.assistant.core.ChatTurn

data class DialogueMessage(
    val id: Long,
    val role: String,
    val content: String,
    val provider: String,
    val createdAtMillis: Long,
)

/**
 * Durable on-device SQLite (+ FTS5 when supported) episodic and fact memory for Jarvis.
 * Retains dialogue context across turns/restarts and supports fast keyword fact recall.
 */
class JarvisMemoryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("jarvis_memory", Context.MODE_PRIVATE)
    private val dbHelper = MemoryDbHelper(appContext)
    @Volatile private var fts5Available: Boolean = dbHelper.hasFts5

    fun put(k: String, v: String) {
        prefs.edit().putString(k, v).apply()
        rememberFact(k, v)
    }

    fun get(k: String): String? = prefs.getString(k, null)

    fun saveMessage(role: String, content: String, provider: String = "user") {
        val clean = content.trim()
        if (clean.isEmpty()) return
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("role", role)
            put("content", clean)
            put("provider", provider)
            put("created_at", System.currentTimeMillis())
        }
        db.insert("messages", null, values)
    }

    fun recentMessages(limit: Int = 10): List<DialogueMessage> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<DialogueMessage>()
        db.rawQuery(
            "SELECT id, role, content, provider, created_at FROM messages ORDER BY id DESC LIMIT ?",
            arrayOf(limit.coerceIn(1, 100).toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    DialogueMessage(
                        id = cursor.getLong(0),
                        role = cursor.getString(1),
                        content = cursor.getString(2),
                        provider = cursor.getString(3),
                        createdAtMillis = cursor.getLong(4),
                    )
                )
            }
        }
        return list.reversed()
    }

    fun recentChatTurns(limit: Int = 6): List<ChatTurn> =
        recentMessages(limit).map { ChatTurn(role = it.role, content = it.content) }

    fun clearMessages() {
        dbHelper.writableDatabase.delete("messages", null, null)
    }

    fun rememberFact(key: String, content: String) {
        val cleanKey = key.trim().ifBlank { "fact_${System.currentTimeMillis()}" }
        val cleanContent = content.trim()
        if (cleanContent.isEmpty()) return

        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("fact_key", cleanKey)
            put("content", cleanContent)
            put("created_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("facts", null, values, SQLiteDatabase.CONFLICT_REPLACE)

        if (fts5Available) {
            runCatching {
                db.delete("facts_fts", "fact_key = ?", arrayOf(cleanKey))
                val ftsValues = ContentValues().apply {
                    put("fact_key", cleanKey)
                    put("content", cleanContent)
                }
                db.insert("facts_fts", null, ftsValues)
            }
        }
    }

    fun searchFacts(query: String, limit: Int = 5): List<String> {
        val tokens = query.lowercase()
            .split(Regex("[^\\p{L}\\p{Nd}]+"))
            .filter { it.length >= 3 }
            .distinct()
            .take(5)
        if (tokens.isEmpty()) {
            return recentFacts(limit)
        }

        val db = dbHelper.readableDatabase
        if (fts5Available) {
            val ftsMatch = tokens.joinToString(" OR ") { "$it*" }
            val matched = runCatching {
                val out = mutableListOf<String>()
                db.rawQuery(
                    "SELECT content FROM facts_fts WHERE facts_fts MATCH ? LIMIT ?",
                    arrayOf(ftsMatch, limit.toString()),
                ).use { cursor ->
                    while (cursor.moveToNext()) out.add(cursor.getString(0))
                }
                out
            }.getOrNull()
            if (!matched.isNullOrEmpty()) return matched
        }

        val likeResults = mutableListOf<String>()
        val where = tokens.joinToString(" OR ") { "LOWER(content) LIKE ?" }
        val args = tokens.map { "%$it%" }.toTypedArray() + limit.toString()
        db.rawQuery(
            "SELECT content FROM facts WHERE $where ORDER BY created_at DESC LIMIT ?",
            args,
        ).use { cursor ->
            while (cursor.moveToNext()) likeResults.add(cursor.getString(0))
        }
        return likeResults
    }

    fun recentFacts(limit: Int = 5): List<String> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<String>()
        db.rawQuery(
            "SELECT content FROM facts ORDER BY created_at DESC LIMIT ?",
            arrayOf(limit.coerceIn(1, 50).toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) list.add(cursor.getString(0))
        }
        return list
    }

    fun close() {
        dbHelper.close()
    }

    private class MemoryDbHelper(context: Context) : SQLiteOpenHelper(context, "jarvis_memory.db", null, 1) {
        var hasFts5: Boolean = false
            private set

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    provider TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS facts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    fact_key TEXT NOT NULL UNIQUE,
                    content TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            hasFts5 = runCatching {
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS facts_fts USING fts5(fact_key, content)")
            }.isSuccess
        }

        override fun onOpen(db: SQLiteDatabase) {
            super.onOpen(db)
            hasFts5 = runCatching {
                db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='facts_fts'", null).use { c ->
                    c.moveToFirst()
                }
            }.getOrDefault(false)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
