package com.jarvis.breeno.assistant.control

import com.jarvis.breeno.assistant.core.DeviceHeatState
import com.jarvis.breeno.assistant.core.RuntimeSignals
import com.jarvis.breeno.assistant.savevideo.SaveVideoQuality
import com.jarvis.breeno.assistant.savevideo.SaveVideoSource
import com.jarvis.breeno.assistant.savevideo.SaveVideoStatus
import com.jarvis.breeno.assistant.savevideo.SaveVideoTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisControlCenterStoreTest {
    @Test
    fun syncsRealDeviceTelemetryVoiceCommandsAndSaveVideoQueue() {
        val store = JarvisControlCenterStore(maxEvents = 5)
        store.updateDeviceSignals(
            signals = RuntimeSignals(3500, 0.1f, 0, false, true, true),
            localAsrReady = true,
        )
        store.recordVoiceCommand("Открой центр управления", "Открываю центр управления")
        store.syncSaveVideoTasks(
            listOf(
                SaveVideoTask("1", "https://example.com/a.mp4", SaveVideoSource.DIRECT_MEDIA, SaveVideoQuality.AUTO, "A", SaveVideoStatus.DOWNLOADING),
                SaveVideoTask("2", "https://example.com/b.mp4", SaveVideoSource.DIRECT_MEDIA, SaveVideoQuality.HD_720, "B", SaveVideoStatus.COMPLETED),
                SaveVideoTask("3", "https://example.com/c.mp4", SaveVideoSource.WEB_PAGE, SaveVideoQuality.AUTO, "C", SaveVideoStatus.FAILED),
            )
        )

        val snap = store.snapshot.value
        assertTrue(snap.localAsrReady)
        assertEquals(DeviceHeatState.COOL, snap.heatState)
        assertEquals(2048, snap.maxContextTokens)
        assertEquals(1, snap.totalVoiceCommands)
        assertEquals(1, snap.videosInQueue)
        assertEquals(1, snap.videosCompleted)
        assertEquals(1, snap.failedTasks)
        assertEquals(2, snap.completedTasks)
        assertEquals(1, snap.events.size)

        store.clearEvents()
        assertTrue(store.snapshot.value.events.isEmpty())
    }
}
