package com.jarvis.breeno.assistant.video

/**
 * Validated trim window in milliseconds. Keeps at least 500 ms to avoid empty or
 * trivially short exports. All values are clamped to the source duration.
 */
data class VideoTrimRange(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs

    companion object {
        const val MIN_CLIP_MS = 500L

        fun create(startMs: Long, endMs: Long, sourceDurationMs: Long): VideoTrimRange? {
            if (sourceDurationMs < MIN_CLIP_MS) return null
            val start = startMs.coerceIn(0L, sourceDurationMs - MIN_CLIP_MS)
            val end = endMs.coerceIn(start + MIN_CLIP_MS, sourceDurationMs)
            return if (end - start >= MIN_CLIP_MS) VideoTrimRange(start, end) else null
        }
    }
}
