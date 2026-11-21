package com.jarvis.breeno.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisOrbStateTest {
    @Test
    fun silenceProducesZeroAudioRms() {
        assertEquals(0f, computeAudioRms(ShortArray(0)), 0.0001f)
        assertEquals(0f, computeAudioRms(ShortArray(160) { 0 }), 0.0001f)
    }

    @Test
    fun speechSignalProducesResponsiveRmsAndClampsAtOne() {
        val moderateSpeech = ShortArray(160) { 4096 }
        val moderateRms = computeAudioRms(moderateSpeech)
        assertTrue("Expected moderate RMS in (0.3, 0.8), got $moderateRms", moderateRms in 0.3f..0.8f)

        val loudClipping = ShortArray(160) { Short.MAX_VALUE }
        assertEquals(1f, computeAudioRms(loudClipping), 0.0001f)
    }
}
