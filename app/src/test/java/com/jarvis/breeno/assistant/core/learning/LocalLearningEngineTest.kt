package com.jarvis.breeno.assistant.core.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLearningEngineTest {
    private class MemoryStore : LocalLearningStore {
        private var enabled = false
        private var entries = emptyList<LearningCorrection>()
        override fun isEnabled() = enabled
        override fun setEnabled(enabled: Boolean) { this.enabled = enabled }
        override fun readAll() = entries
        override fun writeAll(entries: List<LearningCorrection>) { this.entries = entries }
        override fun clear() { entries = emptyList() }
    }

    @Test
    fun learningIsOffByDefaultAndDoesNotStoreUntilEnabled() {
        val store = MemoryStore()
        val engine = LocalLearningEngine(store)

        assertFalse(engine.isEnabled())
        assertFalse(engine.rememberCorrection("открой музыку", "open_media", 1L))
        assertNull(engine.findCorrection("открой музыку"))
    }

    @Test
    fun explicitCorrectionIsStoredAndMatchedAfterNormalization() {
        val engine = LocalLearningEngine(MemoryStore())
        engine.setEnabled(true)

        assertTrue(engine.rememberCorrection("  Открой   Музыку ", "open_media", 10L))
        assertEquals("open_media", engine.findCorrection("открой музыку"))
    }

    @Test
    fun newerCorrectionReplacesOlderCorrectionForSameInput() {
        val engine = LocalLearningEngine(MemoryStore())
        engine.setEnabled(true)
        engine.rememberCorrection("включи радио", "radio_station_a", 10L)
        engine.rememberCorrection("Включи радио", "radio_station_b", 20L)

        assertEquals("radio_station_b", engine.findCorrection("включи радио"))
    }

    @Test
    fun disablingLearningPreventsRecallAndNewWrites() {
        val engine = LocalLearningEngine(MemoryStore())
        engine.setEnabled(true)
        engine.rememberCorrection("поставь таймер", "create_timer", 10L)
        engine.setEnabled(false)

        assertNull(engine.findCorrection("поставь таймер"))
        assertFalse(engine.rememberCorrection("поставь таймер", "other", 20L))
    }

    @Test
    fun clearRemovesAllLearnedCorrections() {
        val engine = LocalLearningEngine(MemoryStore())
        engine.setEnabled(true)
        engine.rememberCorrection("открой карты", "open_maps", 10L)
        engine.clear()

        assertNull(engine.findCorrection("открой карты"))
    }

    @Test
    fun invalidAndOversizedValuesAreRejected() {
        val engine = LocalLearningEngine(MemoryStore())
        engine.setEnabled(true)

        assertFalse(engine.rememberCorrection(" ", "intent", 1L))
        assertFalse(engine.rememberCorrection("input", " ", 1L))
        assertFalse(engine.rememberCorrection("x".repeat(241), "intent", 1L))
        assertFalse(engine.rememberCorrection("input", "y".repeat(241), 1L))
    }

    @Test
    fun memoryIsBoundedToConfiguredEntryCount() {
        val engine = LocalLearningEngine(MemoryStore(), maxEntries = 2)
        engine.setEnabled(true)
        engine.rememberCorrection("first", "one", 1L)
        engine.rememberCorrection("second", "two", 2L)
        engine.rememberCorrection("third", "three", 3L)

        assertNull(engine.findCorrection("first"))
        assertEquals("two", engine.findCorrection("second"))
        assertEquals("three", engine.findCorrection("third"))
    }
}
