package com.jarvis.breeno.assistant

import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.breeno.assistant.memory.JarvisMemoryKind
import com.jarvis.breeno.assistant.memory.JarvisMemoryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JarvisMemoryRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferencesName = "jarvis-memory-repository-test"
    private lateinit var repository: JarvisMemoryRepository

    @Before
    fun setUp() {
        context.getSharedPreferences(preferencesName, 0).edit().clear().commit()
        repository = JarvisMemoryRepository(context, preferencesName)
    }

    @Test
    fun savesOnlyExplicitlySubmittedContentAndTrimsWhitespace() {
        val saved = repository.saveAfterUserConfirmation(
            kind = JarvisMemoryKind.NOTE,
            scope = " Jarvis ",
            text = "  Не отправлять данные в облако  ",
            nowEpochMs = 100L
        )

        assertNotNull(saved)
        assertEquals("Jarvis", saved!!.scope)
        assertEquals("Не отправлять данные в облако", saved.text)
        assertEquals(100L, saved.createdAtEpochMs)
        assertEquals(saved.createdAtEpochMs, saved.updatedAtEpochMs)
        assertEquals(1, repository.list().size)
    }

    @Test
    fun rejectsBlankAndOversizedInputs() {
        assertEquals(null, repository.saveAfterUserConfirmation(JarvisMemoryKind.NOTE, "Jarvis", "  "))
        assertEquals(null, repository.saveAfterUserConfirmation(JarvisMemoryKind.NOTE, "", "text"))
        assertEquals(null, repository.saveAfterUserConfirmation(JarvisMemoryKind.NOTE, "Jarvis", "x".repeat(4_001)))
        assertTrue(repository.list().isEmpty())
    }

    @Test
    fun scopesAndKindsFilterLocalSearch() {
        repository.saveAfterUserConfirmation(JarvisMemoryKind.PROJECT, "Jarvis", "Offline assistant", 1L)
        repository.saveAfterUserConfirmation(JarvisMemoryKind.NOTE, "Home", "Offline backup", 2L)
        repository.saveAfterUserConfirmation(JarvisMemoryKind.FACT, "Jarvis", "Phone model", 3L)

        assertEquals(2, repository.list(scope = "Jarvis").size)
        assertEquals(1, repository.list(kind = JarvisMemoryKind.NOTE).size)
        assertEquals(1, repository.search("offline", scope = "Jarvis").size)
        assertTrue(repository.search("missing").isEmpty())
    }

    @Test
    fun updateAndDeleteRequireExistingEntryAndPersistChanges() {
        val saved = repository.saveAfterUserConfirmation(JarvisMemoryKind.NOTE, "Jarvis", "Old", 10L)!!

        assertTrue(repository.updateAfterUserConfirmation(saved.id, "New", 20L))
        val updated = repository.list().single()
        assertEquals("New", updated.text)
        assertEquals(10L, updated.createdAtEpochMs)
        assertEquals(20L, updated.updatedAtEpochMs)

        assertTrue(repository.deleteAfterUserConfirmation(saved.id))
        assertFalse(repository.deleteAfterUserConfirmation(saved.id))
        assertTrue(repository.list().isEmpty())
    }

    @Test
    fun clearScopeDoesNotTouchOtherProjects() {
        repository.saveAfterUserConfirmation(JarvisMemoryKind.NOTE, "Jarvis", "One", 1L)
        repository.saveAfterUserConfirmation(JarvisMemoryKind.NOTE, "Antigravity", "Two", 2L)

        assertTrue(repository.clearScopeAfterUserConfirmation("Jarvis"))
        assertEquals(listOf("Antigravity"), repository.list().map { it.scope })
        assertFalse(repository.clearScopeAfterUserConfirmation("Jarvis"))
    }
}
