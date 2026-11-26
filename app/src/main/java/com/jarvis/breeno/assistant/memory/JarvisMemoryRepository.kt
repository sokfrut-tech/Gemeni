package com.jarvis.breeno.assistant.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.GsonBuilder
import com.jarvis.breeno.assistant.core.ChatTurn
import java.util.Calendar
import java.util.UUID

data class DialogueMessage(
    val id: Long,
    val role: String,
    val content: String,
    val provider: String,
    val createdAtMillis: Long,
    val sessionId: String = "default",
)

data class ChatSession(
    val id: String,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val messageCount: Int = 0,
)

data class MemoryFact(
    val id: Long,
    val key: String,
    val content: String,
    val createdAtMillis: Long,
)

enum class DialogueDateBucket(val title: String) {
    TODAY("Сегодня"),
    YESTERDAY("Вчера"),
    LAST_30_DAYS("За 30 дней"),
    EARLIER("Ранее"),
}

/**
 * Durable on-device SQLite (+ FTS5 when supported) episodic, multi-session, and fact memory for Jarvis.
 * Enforces SecretGuard fail-closed screening and supports Memory Pause, 1-tap Undo delete, and JSON export.
 */
class JarvisMemoryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("jarvis_memory", Context.MODE_PRIVATE)
    private val dbHelper = MemoryDbHelper(appContext)
    @Volatile private var fts5Available: Boolean = dbHelper.hasFts5

    fun isMemoryPaused(): Boolean = prefs.getBoolean(KEY_MEMORY_PAUSED, false)

    fun setMemoryPaused(paused: Boolean) {
        prefs.edit().putBoolean(KEY_MEMORY_PAUSED, paused).apply()
    }

    fun activeSessionId(): String {
        val current = prefs.getString(KEY_ACTIVE_SESSION_ID, null).orEmpty()
        if (current.isNotBlank() && sessionExists(current)) {
            return current
        }
        return ensureActiveSession().id
    }

    fun setActiveSessionId(sessionId: String) {
        if (sessionExists(sessionId)) {
            prefs.edit().putString(KEY_ACTIVE_SESSION_ID, sessionId).apply()
        }
    }

    fun ensureActiveSession(): ChatSession {
        val currentId = prefs.getString(KEY_ACTIVE_SESSION_ID, null)
        if (!currentId.isNullOrBlank()) {
            findSession(currentId)?.let { return it }
        }
        listSessions().firstOrNull()?.let { existing ->
            prefs.edit().putString(KEY_ACTIVE_SESSION_ID, existing.id).apply()
            return existing
        }
        return createSession(DEFAULT_SESSION_TITLE)
    }

    fun createSession(title: String = DEFAULT_SESSION_TITLE): ChatSession {
        val now = System.currentTimeMillis()
        val id = "sess_${now}_${UUID.randomUUID().toString().take(6)}"
        val cleanTitle = title.trim().ifBlank { DEFAULT_SESSION_TITLE }.take(48)
        val db = dbHelper.writableDatabase
        db.insertWithOnConflict(
            "sessions",
            null,
            ContentValues().apply {
                put("id", id)
                put("title", cleanTitle)
                put("created_at", now)
                put("updated_at", now)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        prefs.edit().putString(KEY_ACTIVE_SESSION_ID, id).apply()
        return ChatSession(id = id, title = cleanTitle, createdAtMillis = now, updatedAtMillis = now, messageCount = 0)
    }

    fun listSessions(searchQuery: String = ""): List<ChatSession> {
        val db = dbHelper.readableDatabase
        val all = mutableListOf<ChatSession>()
        val trimmed = searchQuery.trim()
        val sql = if (trimmed.isEmpty()) {
            """
            SELECT s.id, s.title, s.created_at, s.updated_at, COUNT(m.id) AS msg_count
            FROM sessions s
            LEFT JOIN messages m ON m.session_id = s.id
            GROUP BY s.id
            ORDER BY s.updated_at DESC
            """.trimIndent()
        } else {
            """
            SELECT s.id, s.title, s.created_at, s.updated_at, COUNT(m.id) AS msg_count
            FROM sessions s
            LEFT JOIN messages m ON m.session_id = s.id
            WHERE LOWER(s.title) LIKE ? OR EXISTS (
                SELECT 1 FROM messages m2 WHERE m2.session_id = s.id AND LOWER(m2.content) LIKE ?
            )
            GROUP BY s.id
            ORDER BY s.updated_at DESC
            """.trimIndent()
        }
        val args = if (trimmed.isEmpty()) null else arrayOf("%${trimmed.lowercase()}%", "%${trimmed.lowercase()}%")
        db.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                all.add(
                    ChatSession(
                        id = c.getString(0),
                        title = c.getString(1),
                        createdAtMillis = c.getLong(2),
                        updatedAtMillis = c.getLong(3),
                        messageCount = c.getInt(4),
                    )
                )
            }
        }
        return all
    }

    fun deleteSession(sessionId: String) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("messages", "session_id = ?", arrayOf(sessionId))
            db.delete("sessions", "id = ?", arrayOf(sessionId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (prefs.getString(KEY_ACTIVE_SESSION_ID, null) == sessionId) {
            prefs.edit().remove(KEY_ACTIVE_SESSION_ID).apply()
            ensureActiveSession()
        }
    }

    private fun sessionExists(sessionId: String): Boolean = findSession(sessionId) != null

    private fun findSession(sessionId: String): ChatSession? {
        val db = dbHelper.readableDatabase
        db.rawQuery(
            "SELECT id, title, created_at, updated_at FROM sessions WHERE id = ? LIMIT 1",
            arrayOf(sessionId),
        ).use { c ->
            if (c.moveToFirst()) {
                return ChatSession(
                    id = c.getString(0),
                    title = c.getString(1),
                    createdAtMillis = c.getLong(2),
                    updatedAtMillis = c.getLong(3),
                )
            }
        }
        return null
    }

    fun put(k: String, v: String) {
        if (!isMemoryPaused() && JarvisSecretGuard.isSafeToPersist("$k $v")) {
            prefs.edit().putString(k, v).apply()
            rememberFact(k, v)
        }
    }

    fun get(k: String): String? = prefs.getString(k, null)

    fun saveMessage(
        role: String,
        content: String,
        provider: String = "user",
        sessionId: String = activeSessionId(),
    ) {
        val clean = content.trim()
        if (clean.isEmpty()) return
        val now = System.currentTimeMillis()
        val targetSessionId = sessionId.ifBlank { ensureActiveSession().id }
        val existing = findSession(targetSessionId)
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            if (existing == null) {
                db.insertWithOnConflict(
                    "sessions",
                    null,
                    ContentValues().apply {
                        put("id", targetSessionId)
                        put("title", if (role == "user") deriveSessionTitle(clean) else DEFAULT_SESSION_TITLE)
                        put("created_at", now)
                        put("updated_at", now)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
            } else {
                val updatedValues = ContentValues().apply {
                    put("updated_at", now)
                    if (role == "user" && (existing.title == DEFAULT_SESSION_TITLE || existing.title.isBlank())) {
                        put("title", deriveSessionTitle(clean))
                    }
                }
                db.update("sessions", updatedValues, "id = ?", arrayOf(targetSessionId))
            }

            db.insert(
                "messages",
                null,
                ContentValues().apply {
                    put("session_id", targetSessionId)
                    put("role", role)
                    put("content", clean)
                    put("provider", provider)
                    put("created_at", now)
                },
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun recentMessages(
        limit: Int = 10,
        sessionId: String = activeSessionId(),
    ): List<DialogueMessage> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<DialogueMessage>()
        db.rawQuery(
            "SELECT id, role, content, provider, created_at, session_id FROM messages WHERE session_id = ? ORDER BY id DESC LIMIT ?",
            arrayOf(sessionId, limit.coerceIn(1, 200).toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    DialogueMessage(
                        id = cursor.getLong(0),
                        role = cursor.getString(1),
                        content = cursor.getString(2),
                        provider = cursor.getString(3),
                        createdAtMillis = cursor.getLong(4),
                        sessionId = cursor.getString(5),
                    )
                )
            }
        }
        return list.reversed()
    }

    fun recentChatTurns(limit: Int = 6, sessionId: String = activeSessionId()): List<ChatTurn> =
        recentMessages(limit, sessionId).map { ChatTurn(role = it.role, content = it.content) }

    fun clearMessages(sessionId: String? = null) {
        val db = dbHelper.writableDatabase
        if (sessionId.isNullOrBlank()) {
            db.delete("messages", null, null)
            db.delete("sessions", null, null)
            ensureActiveSession()
        } else {
            db.delete("messages", "session_id = ?", arrayOf(sessionId))
        }
    }

    fun rememberFact(key: String, content: String) {
        rememberFactChecked(key, content)
    }

    fun rememberFactChecked(key: String, content: String): Pair<Boolean, String> {
        if (isMemoryPaused()) {
            return false to "Память на паузе — новые факты временно не сохраняются."
        }
        val cleanKey = key.trim().ifBlank { "fact_${System.currentTimeMillis()}" }
        val cleanContent = content.trim()
        if (cleanContent.isEmpty()) {
            return false to "Текст факта пуст."
        }
        val check = JarvisSecretGuard.inspect("$cleanKey $cleanContent")
        if (check.blocked) {
            return false to (check.reason ?: "SecretGuard заблокировал сохранение конфиденциальных данных.")
        }

        val now = System.currentTimeMillis()
        insertFactInternal(cleanKey, cleanContent, now)
        return true to "Факт сохранён в локальную память."
    }

    private fun insertFactInternal(cleanKey: String, cleanContent: String, timestampMillis: Long) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("fact_key", cleanKey)
            put("content", cleanContent)
            put("created_at", timestampMillis)
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

    fun listFacts(query: String = "", limit: Int = 200): List<MemoryFact> {
        val db = dbHelper.readableDatabase
        val trimmed = query.trim().lowercase()
        val out = mutableListOf<MemoryFact>()
        val sql = if (trimmed.isEmpty()) {
            "SELECT id, fact_key, content, created_at FROM facts ORDER BY created_at DESC LIMIT ?"
        } else {
            "SELECT id, fact_key, content, created_at FROM facts WHERE LOWER(content) LIKE ? OR LOWER(fact_key) LIKE ? ORDER BY created_at DESC LIMIT ?"
        }
        val args = if (trimmed.isEmpty()) {
            arrayOf(limit.coerceIn(1, 500).toString())
        } else {
            arrayOf("%$trimmed%", "%$trimmed%", limit.coerceIn(1, 500).toString())
        }
        db.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(
                    MemoryFact(
                        id = cursor.getLong(0),
                        key = cursor.getString(1),
                        content = cursor.getString(2),
                        createdAtMillis = cursor.getLong(3),
                    )
                )
            }
        }
        return out
    }

    fun deleteFactById(factId: Long): MemoryFact? {
        val db = dbHelper.writableDatabase
        var removed: MemoryFact? = null
        db.rawQuery(
            "SELECT id, fact_key, content, created_at FROM facts WHERE id = ? LIMIT 1",
            arrayOf(factId.toString()),
        ).use { c ->
            if (c.moveToFirst()) {
                removed = MemoryFact(
                    id = c.getLong(0),
                    key = c.getString(1),
                    content = c.getString(2),
                    createdAtMillis = c.getLong(3),
                )
            }
        }
        if (removed != null) {
            db.delete("facts", "id = ?", arrayOf(factId.toString()))
            if (fts5Available) {
                runCatching {
                    db.delete("facts_fts", "fact_key = ?", arrayOf(removed!!.key))
                }
            }
        }
        return removed
    }

    fun restoreFact(fact: MemoryFact): Boolean {
        if (!JarvisSecretGuard.isSafeToPersist("${fact.key} ${fact.content}")) {
            return false
        }
        insertFactInternal(fact.key, fact.content, fact.createdAtMillis)
        return true
    }

    fun exportFactsJson(): String {
        val safeFacts = listFacts(limit = 500).filter { fact ->
            JarvisSecretGuard.isSafeToPersist("${fact.key} ${fact.content}")
        }.map { fact ->
            mapOf(
                "key" to fact.key,
                "content" to fact.content,
                "createdAtMillis" to fact.createdAtMillis,
            )
        }
        val payload = mapOf(
            "app" to "Jarvis Ai",
            "exportedAtMillis" to System.currentTimeMillis(),
            "count" to safeFacts.size,
            "facts" to safeFacts,
        )
        return GsonBuilder().setPrettyPrinting().create().toJson(payload)
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

    private class MemoryDbHelper(context: Context) : SQLiteOpenHelper(context, "jarvis_memory.db", null, DB_VERSION) {
        var hasFts5: Boolean = false
            private set

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sessions (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id TEXT NOT NULL DEFAULT 'default',
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    provider TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_session_id ON messages(session_id, id)")
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

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sessions (
                        id TEXT PRIMARY KEY,
                        title TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                var hasSessionIdCol = false
                db.rawQuery("PRAGMA table_info(messages)", null).use { c ->
                    val nameIdx = c.getColumnIndex("name")
                    while (c.moveToNext()) {
                        if (nameIdx >= 0 && c.getString(nameIdx) == "session_id") {
                            hasSessionIdCol = true
                            break
                        }
                    }
                }
                if (!hasSessionIdCol) {
                    db.execSQL("ALTER TABLE messages ADD COLUMN session_id TEXT NOT NULL DEFAULT 'default'")
                }
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_session_id ON messages(session_id, id)")

                // Preserve existing v1 messages under a readable legacy default session.
                var legacyCount = 0L
                var firstUserTitle: String? = null
                var minTs = System.currentTimeMillis()
                var maxTs = minTs
                db.rawQuery("SELECT COUNT(*), MIN(created_at), MAX(created_at) FROM messages WHERE session_id = 'default'", null).use { c ->
                    if (c.moveToFirst()) {
                        legacyCount = c.getLong(0)
                        if (legacyCount > 0L) {
                            minTs = c.getLong(1)
                            maxTs = c.getLong(2)
                        }
                    }
                }
                if (legacyCount > 0L) {
                    db.rawQuery(
                        "SELECT content FROM messages WHERE session_id = 'default' AND role = 'user' ORDER BY id ASC LIMIT 1",
                        null,
                    ).use { c ->
                        if (c.moveToFirst()) {
                            firstUserTitle = deriveSessionTitle(c.getString(0))
                        }
                    }
                    db.insertWithOnConflict(
                        "sessions",
                        null,
                        ContentValues().apply {
                            put("id", "default")
                            put("title", firstUserTitle ?: "Предыдущий диалог")
                            put("created_at", minTs)
                            put("updated_at", maxTs)
                        },
                        SQLiteDatabase.CONFLICT_IGNORE,
                    )
                }
            }
        }
    }

    companion object {
        private const val DB_VERSION = 2
        private const val KEY_ACTIVE_SESSION_ID = "active_dialogue_session_id"
        private const val KEY_MEMORY_PAUSED = "memory_recording_paused"
        const val DEFAULT_SESSION_TITLE = "Новый диалог"

        fun deriveSessionTitle(firstUserPrompt: String): String {
            val singleLine = firstUserPrompt.replace(Regex("\\s+"), " ").trim()
            if (singleLine.isEmpty()) return DEFAULT_SESSION_TITLE
            return if (singleLine.length <= 42) singleLine else singleLine.take(41).trimEnd() + "…"
        }

        fun groupSessionsByBucket(
            sessions: List<ChatSession>,
            nowMillis: Long = System.currentTimeMillis(),
        ): List<Pair<DialogueDateBucket, List<ChatSession>>> {
            if (sessions.isEmpty()) return emptyList()

            val todayStart = startOfDayMillis(nowMillis, 0)
            val yesterdayStart = startOfDayMillis(nowMillis, -1)
            val thirtyDaysStart = startOfDayMillis(nowMillis, -30)

            val grouped = linkedMapOf<DialogueDateBucket, MutableList<ChatSession>>(
                DialogueDateBucket.TODAY to mutableListOf(),
                DialogueDateBucket.YESTERDAY to mutableListOf(),
                DialogueDateBucket.LAST_30_DAYS to mutableListOf(),
                DialogueDateBucket.EARLIER to mutableListOf(),
            )

            sessions.sortedByDescending { it.updatedAtMillis }.forEach { session ->
                val ts = session.updatedAtMillis
                val bucket = when {
                    ts >= todayStart -> DialogueDateBucket.TODAY
                    ts >= yesterdayStart -> DialogueDateBucket.YESTERDAY
                    ts >= thirtyDaysStart -> DialogueDateBucket.LAST_30_DAYS
                    else -> DialogueDateBucket.EARLIER
                }
                grouped[bucket]?.add(session)
            }

            return grouped.entries
                .filter { it.value.isNotEmpty() }
                .map { it.key to it.value.toList() }
        }

        private fun startOfDayMillis(nowMillis: Long, dayOffset: Int): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }
            return cal.timeInMillis
        }
    }
}
