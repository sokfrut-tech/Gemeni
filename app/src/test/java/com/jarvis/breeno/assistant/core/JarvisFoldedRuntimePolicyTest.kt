package com.jarvis.breeno.assistant.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class JarvisFoldedRuntimePolicyTest {
    @Test
    fun coolDeviceCanUseBoundedLocalModelWhenMemoryAllows(): Unit {
        val plan = JarvisFoldedRuntimePolicy().plan(
            RuntimeSignals(availableMemoryMb = 3_500, thermalHeadroom = 0.2f,
                thermalStatus = 0, batterySaver = false, charging = false, foreground = true)
        )
        assertTrue(plan.allowLocalLlm)
        assertEquals(DeviceHeatState.COOL, plan.heatState)
        assertTrue(plan.maxContextTokens <= 2048)
        assertEquals(1, plan.maxConcurrentNeuralEngines)
    }

    @Test
    fun hotDeviceSuspendsLlm(): Unit {
        val plan = JarvisFoldedRuntimePolicy().plan(
            RuntimeSignals(4_000, 0.86f, 3, false, true, true)
        )
        assertFalse(plan.allowLocalLlm)
        assertEquals(DeviceHeatState.HOT, plan.heatState)
        assertFalse(plan.allowLongReasoning)
    }

    @Test
    fun criticalDeviceReleasesNeuralResources(): Unit {
        val plan = JarvisFoldedRuntimePolicy().plan(
            RuntimeSignals(4_000, 0.97f, 5, false, true, true)
        )
        assertFalse(plan.allowLocalLlm)
        assertEquals(0L, plan.unloadAfterIdleMillis)
        assertEquals(DeviceHeatState.CRITICAL, plan.heatState)
    }

    @Test
    fun batterySaverDisablesLlmEvenWhenCool(): Unit {
        val plan = JarvisFoldedRuntimePolicy().plan(
            RuntimeSignals(4_000, 0.1f, 0, true, false, true)
        )
        assertFalse(plan.allowLocalLlm)
        assertFalse(plan.allowLongReasoning)
    }

    @Test
    fun contextFolderKeepsRecentTurnsAndRequiresNonEmptyCapsule(): Unit {
        runBlocking {
            val folder = JarvisContextFolder(ContextCompressor { older, max ->
                older.joinToString(" | ") { it.text }.take(max)
            })
            val turns = (1..6).map { ConversationTurn(if (it % 2 == 0) "assistant" else "user", "turn-$it") }
            val folded = folder.fold(turns, keepRecentTurns = 2)
            assertEquals(6, folded.originalTurnCount)
            assertEquals(listOf("turn-5", "turn-6"), folded.recentTurns.map { it.text })
            assertTrue(folded.carryCapsule.contains("turn-1"))
        }
    }

    @Test
    fun contextFolderRejectsEmptyCompression(): Unit {
        runBlocking {
            val folder = JarvisContextFolder(ContextCompressor { _, _ -> "" })
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    folder.fold(listOf(
                        ConversationTurn("user", "one"),
                        ConversationTurn("assistant", "two"),
                    ), keepRecentTurns = 1)
                }
            }
        }
    }
}
