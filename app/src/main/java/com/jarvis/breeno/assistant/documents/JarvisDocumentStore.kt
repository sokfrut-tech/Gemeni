package com.jarvis.breeno.assistant.documents

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import kotlin.math.ln

data class IndexedDocument(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val chunkCount: Int,
    val importedAt: Long,
)

data class DocumentChunkMatch(
    val documentName: String,
    val chunkIndex: Int,
    val text: String,
    val score: Double,
)

/**
 * Local offline RAG knowledge store:
 * Imports user documents via SAF (.txt, .md, .csv, .json, .log, .xml, basic text PDF),
 * splits them into overlapping chunks, stores locally in SQLite, and injects top snippets into prompts.
 */
class JarvisDocumentStore(context: Context) {
    private val appContext = context.applicationContext
    private val dbHelper = DocumentsDbHelper(appContext)

    suspend fun importDocument(
        uri: Uri,
        displayName: String,
        mimeType: String = "",
    ): Result<IndexedDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanName = displayName.trim().ifBlank { "document.txt" }
            val resolvedMime = mimeType.ifBlank {
                appContext.contentResolver.getType(uri).orEmpty()
            }.ifBlank { "text/plain" }

            val rawBytes = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                readBoundedBytes(stream.readBytesCompat(MAX_FILE_BYTES + 1))
            } ?: error("Не удалось открыть выбранный файл")

            check(rawBytes.isNotEmpty()) { "Выбранный файл пуст" }
            check(rawBytes.size <= MAX_FILE_BYTES) { "Файл больше 4 МБ — выберите документ меньшего размера" }

            val isPdf = resolvedMime.equals("application/pdf", ignoreCase = true) ||
                cleanName.endsWith(".pdf", ignoreCase = true)

            val extractedText = if (isPdf) {
                extractPrintablePdfText(rawBytes)
            } else {
                rawBytes.toString(Charsets.UTF_8)
            }.trim()

            check(extractedText.length >= 4) {
                if (isPdf) {
                    "Не удалось извлечь текст из этого PDF (возможно, файл зашифрован или состоит из сканов). Используйте TXT/MD или текстовый PDF."
                } else {
                    "Файл не содержит читаемого текста"
                }
            }

            val chunks = chunkDocumentText(extractedText)
            check(chunks.isNotEmpty()) { "Не удалось разбить документ на текстовые фрагменты" }

            val doc = IndexedDocument(
                id = "doc_${UUID.randomUUID().toString().take(10)}",
                name = cleanName,
                mimeType = resolvedMime,
                sizeBytes = rawBytes.size.toLong(),
                chunkCount = chunks.size,
                importedAt = System.currentTimeMillis(),
            )

            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                db.insertOrThrow(
                    "documents",
                    null,
                    ContentValues().apply {
                        put("id", doc.id)
                        put("name", doc.name)
                        put("mime_type", doc.mimeType)
                        put("size_bytes", doc.sizeBytes)
                        put("chunk_count", doc.chunkCount)
                        put("imported_at", doc.importedAt)
                    },
                )
                chunks.forEachIndexed { idx, chunk ->
                    db.insertOrThrow(
                        "chunks",
                        null,
                        ContentValues().apply {
                            put("doc_id", doc.id)
                            put("doc_name", doc.name)
                            put("chunk_index", idx)
                            put("content", chunk)
                        },
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            doc
        }
    }

    suspend fun addTextNote(
        title: String,
        rawText: String,
    ): Result<IndexedDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanText = rawText.trim()
            check(cleanText.length >= 4) { "Текст заметки слишком короткий" }
            val utf8Bytes = cleanText.toByteArray(Charsets.UTF_8)
            check(utf8Bytes.size <= MAX_FILE_BYTES) { "Заметка превышает 4 МБ" }
            val cleanName = title.trim().ifBlank { "Заметка ${listDocuments().size + 1}" } + ".txt"
            val chunks = chunkDocumentText(cleanText)
            check(chunks.isNotEmpty()) { "Не удалось разбить заметку на фрагменты" }

            val doc = IndexedDocument(
                id = "note_${UUID.randomUUID().toString().take(10)}",
                name = cleanName,
                mimeType = "text/plain",
                sizeBytes = utf8Bytes.size.toLong(),
                chunkCount = chunks.size,
                importedAt = System.currentTimeMillis(),
            )
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                db.insertOrThrow(
                    "documents",
                    null,
                    ContentValues().apply {
                        put("id", doc.id)
                        put("name", doc.name)
                        put("mime_type", doc.mimeType)
                        put("size_bytes", doc.sizeBytes)
                        put("chunk_count", doc.chunkCount)
                        put("imported_at", doc.importedAt)
                    },
                )
                chunks.forEachIndexed { idx, chunk ->
                    db.insertOrThrow(
                        "chunks",
                        null,
                        ContentValues().apply {
                            put("doc_id", doc.id)
                            put("doc_name", doc.name)
                            put("chunk_index", idx)
                            put("content", chunk)
                        },
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            doc
        }
    }

    fun listDocuments(): List<IndexedDocument> {
        val db = dbHelper.readableDatabase
        val out = mutableListOf<IndexedDocument>()
        db.rawQuery(
            "SELECT id, name, mime_type, size_bytes, chunk_count, imported_at FROM documents ORDER BY imported_at DESC",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    IndexedDocument(
                        id = c.getString(0),
                        name = c.getString(1),
                        mimeType = c.getString(2),
                        sizeBytes = c.getLong(3),
                        chunkCount = c.getInt(4),
                        importedAt = c.getLong(5),
                    )
                )
            }
        }
        return out
    }

    fun deleteDocument(docId: String) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("chunks", "doc_id = ?", arrayOf(docId))
            db.delete("documents", "id = ?", arrayOf(docId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun searchMatches(query: String, limit: Int = 3): List<DocumentChunkMatch> {
        if (query.isBlank()) return emptyList()
        val db = dbHelper.readableDatabase
        val rows = mutableListOf<Triple<String, Int, String>>()
        db.rawQuery(
            "SELECT doc_name, chunk_index, content FROM chunks",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                rows.add(Triple(c.getString(0), c.getInt(1), c.getString(2)))
            }
        }
        return scoreIndexedChunks(query, rows, limit = limit)
    }

    fun searchRelevantSnippets(query: String, limit: Int = 3): List<String> =
        searchMatches(query, limit).map { match ->
            val snippet = match.text.replace(Regex("\\s+"), " ").trim().take(420)
            "[Документ: ${match.documentName}] $snippet"
        }

    fun close() {
        dbHelper.close()
    }

    private fun readBoundedBytes(bytes: ByteArray): ByteArray = bytes

    private fun java.io.InputStream.readBytesCompat(maxRead: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (total < maxRead) {
            val n = read(buf, 0, minOf(buf.size, maxRead - total))
            if (n <= 0) break
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    private class DocumentsDbHelper(context: Context) :
        SQLiteOpenHelper(context, "jarvis_documents.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS documents (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    mime_type TEXT NOT NULL,
                    size_bytes INTEGER NOT NULL,
                    chunk_count INTEGER NOT NULL,
                    imported_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS chunks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    doc_id TEXT NOT NULL,
                    doc_name TEXT NOT NULL,
                    chunk_index INTEGER NOT NULL,
                    content TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_chunks_doc_id ON chunks(doc_id)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object {
        const val MAX_FILE_BYTES = 4 * 1024 * 1024
        private const val MAX_CHUNKS_PER_DOC = 400
        private val RU_LOCALE = Locale("ru", "RU")
        private val TOKEN_SPLIT = Regex("[^\\p{L}\\p{Nd}]+")
        private val PDF_TEXT_OP_REGEX = Regex("""\(([^()\\]*(?:\\.[^()\\]*)*)\)\s*Tj""")

        fun chunkDocumentText(
            text: String,
            chunkSizeChars: Int = 750,
            overlapChars: Int = 120,
        ): List<String> {
            val normalized = text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
            if (normalized.isEmpty()) return emptyList()

            val safeChunkSize = chunkSizeChars.coerceIn(120, 4000)
            val safeOverlap = overlapChars.coerceIn(0, safeChunkSize / 2)
            val chunks = mutableListOf<String>()
            var start = 0
            val len = normalized.length

            while (start < len && chunks.size < MAX_CHUNKS_PER_DOC) {
                val rawEnd = minOf(start + safeChunkSize, len)
                var end = rawEnd
                if (rawEnd < len) {
                    val window = normalized.substring(start, rawEnd)
                    val breakIdx = listOf(
                        window.lastIndexOf("\n\n"),
                        window.lastIndexOf('\n'),
                        window.lastIndexOf(". "),
                        window.lastIndexOf(' '),
                    ).firstOrNull { it >= (safeChunkSize * 0.45f).toInt() }
                    if (breakIdx != null) {
                        end = start + breakIdx + 1
                    }
                }
                val slice = normalized.substring(start, end).trim()
                if (slice.isNotEmpty()) {
                    chunks.add(slice)
                }
                if (end >= len) break
                val nextStart = (end - safeOverlap).coerceAtLeast(start + 1)
                start = nextStart
            }
            return chunks
        }

        fun extractPrintablePdfText(bytes: ByteArray): String {
            if (bytes.isEmpty()) return ""
            val latin = bytes.toString(Charsets.ISO_8859_1)
            val tjMatches = PDF_TEXT_OP_REGEX.findAll(latin)
                .map { m ->
                    m.groupValues[1]
                        .replace("\\(", "(")
                        .replace("\\)", ")")
                        .replace("\\n", "\n")
                        .replace("\\r", " ")
                        .replace("\\\\", "\\")
                }
                .map { it.trim() }
                .filter { it.length >= 2 }
                .toList()
            if (tjMatches.isNotEmpty()) {
                return tjMatches.joinToString("\n")
            }

            // Fallback: scan readable UTF-8 sequences when plain text streams are uncompressed
            val utf8 = bytes.toString(Charsets.UTF_8)
            val readableLines = utf8.lineSequence()
                .map { it.trim() }
                .filter { line ->
                    line.length >= 12 &&
                        !line.startsWith("%PDF") &&
                        !line.startsWith("xref") &&
                        line.count { ch -> ch.isLetterOrDigit() || ch == ' ' } > line.length * 0.65
                }
                .take(400)
                .toList()
            return readableLines.joinToString("\n")
        }

        fun scoreChunks(
            query: String,
            chunks: List<Pair<String, String>>,
            limit: Int = 4,
        ): List<DocumentChunkMatch> = scoreIndexedChunks(
            query = query,
            chunks = chunks.mapIndexed { idx, pair -> Triple(pair.first, idx, pair.second) },
            limit = limit,
        )

        fun scoreIndexedChunks(
            query: String,
            chunks: List<Triple<String, Int, String>>,
            limit: Int = 4,
        ): List<DocumentChunkMatch> {
            val queryTokens = tokenize(query)
            if (queryTokens.isEmpty() || chunks.isEmpty()) return emptyList()

            val tokenizedDocs = chunks.map { (_, _, text) -> tokenize(text).toSet() }
            val totalDocs = chunks.size.toDouble()
            val idf = queryTokens.associateWith { token ->
                val df = tokenizedDocs.count { token in it }
                ln((totalDocs + 1.0) / (df + 1.0)) + 1.0
            }

            return chunks.mapIndexedNotNull { index, (docName, chunkIdx, content) ->
                val chunkTokens = tokenizedDocs[index]
                if (chunkTokens.isEmpty()) return@mapIndexedNotNull null
                val score = queryTokens.sumOf { q ->
                    when {
                        q in chunkTokens -> idf[q] ?: 1.0
                        q.length >= 5 && chunkTokens.any { it.startsWith(q.take(5)) } -> (idf[q] ?: 1.0) * 0.65
                        else -> 0.0
                    }
                }
                if (score <= 0.0) {
                    null
                } else {
                    DocumentChunkMatch(
                        documentName = docName,
                        chunkIndex = chunkIdx,
                        text = content,
                        score = score,
                    )
                }
            }
                .sortedByDescending { it.score }
                .take(limit.coerceIn(1, 10))
        }

        private fun tokenize(text: String): List<String> = text
            .lowercase(RU_LOCALE)
            .replace('ё', 'е')
            .split(TOKEN_SPLIT)
            .filter { it.length >= 2 }
            .distinct()
    }
}
