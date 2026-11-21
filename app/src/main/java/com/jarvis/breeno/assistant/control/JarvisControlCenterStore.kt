package com.jarvis.breeno.assistant.control

import com.jarvis.breeno.assistant.core.DeviceHeatState
import com.jarvis.breeno.assistant.core.JarvisFoldedRuntimePolicy
import com.jarvis.breeno.assistant.core.RuntimeSignals
import com.jarvis.breeno.assistant.savevideo.SaveVideoStatus
import com.jarvis.breeno.assistant.savevideo.SaveVideoTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

enum class JarvisEventLevel { INFO, SUCCESS, WARN, ERROR }

data class JarvisEventLogEntry(
    val id: Long,
    val timestampMillis: Long,
    val category: String,
    val title: String,
    val detail: String,
    val level: JarvisEventLevel = JarvisEventLevel.INFO,
)

data class JarvisControlSnapshot(
    val totalVoiceCommands: Int = 0,
    val completedTasks: Int = 0,
    val failedTasks: Int = 0,
    val activeTasks: Int = 0,
    val videosInQueue: Int = 0,
    val videosCompleted: Int = 0,
    val localAsrReady: Boolean = false,
    val heatState: DeviceHeatState = DeviceHeatState.COOL,
    val availableMemoryMb: Int = 0,
    val allowLocalLlm: Boolean = false,
    val maxContextTokens: Int = 0,
    val events: List<JarvisEventLogEntry> = emptyList(),
)

/**
 * Single source of truth for real Jarvis Control Center analytics and event history.
 * Reports only verified runtime metrics — never fabricated counters.
 */
class JarvisControlCenterStore(
    private val maxEvents: Int = 100,
    private val policy: JarvisFoldedRuntimePolicy = JarvisFoldedRuntimePolicy(),
) {
    private val idGen = AtomicLong(1L)
    private val _snapshot = MutableStateFlow(JarvisControlSnapshot())
    val snapshot: StateFlow<JarvisControlSnapshot> = _snapshot.asStateFlow()

    fun updateDeviceSignals(signals: RuntimeSignals, localAsrReady: Boolean) {
        val plan = policy.plan(signals)
        _snapshot.update {
            it.copy(
                localAsrReady = localAsrReady,
                heatState = plan.heatState,
                availableMemoryMb = signals.availableMemoryMb,
                allowLocalLlm = plan.allowLocalLlm,
                maxContextTokens = plan.maxContextTokens,
            )
        }
    }

    fun syncSaveVideoTasks(tasks: List<SaveVideoTask>) {
        val inQueue = tasks.count {
            it.status == SaveVideoStatus.QUEUED ||
                it.status == SaveVideoStatus.INSPECTING ||
                it.status == SaveVideoStatus.DOWNLOADING
        }
        val completed = tasks.count { it.status == SaveVideoStatus.COMPLETED }
        val failed = tasks.count { it.status == SaveVideoStatus.FAILED }
        _snapshot.update {
            it.copy(
                videosInQueue = inQueue,
                videosCompleted = completed,
                activeTasks = inQueue,
                completedTasks = it.totalVoiceCommands + completed,
                failedTasks = failed,
            )
        }
    }

    fun recordVoiceCommand(spokenText: String, responseText: String) {
        _snapshot.update { current ->
            val nextVoice = current.totalVoiceCommands + 1
            current.copy(
                totalVoiceCommands = nextVoice,
                completedTasks = nextVoice + current.videosCompleted,
            )
        }
        logEvent(
            category = "ГОЛОС",
            title = spokenText.take(60),
            detail = responseText,
            level = JarvisEventLevel.SUCCESS,
        )
    }

    fun logEvent(
        category: String,
        title: String,
        detail: String,
        level: JarvisEventLevel = JarvisEventLevel.INFO,
        timestampMillis: Long = System.currentTimeMillis(),
    ) {
        val entry = JarvisEventLogEntry(
            id = idGen.getAndIncrement(),
            timestampMillis = timestampMillis,
            category = category,
            title = title,
            detail = detail,
            level = level,
        )
        _snapshot.update { current ->
            current.copy(events = (listOf(entry) + current.events).take(maxEvents))
        }
    }

    fun clearEvents() {
        _snapshot.update { it.copy(events = emptyList()) }
    }
}
