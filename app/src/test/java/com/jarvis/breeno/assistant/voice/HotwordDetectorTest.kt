package com.jarvis.breeno.assistant.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HotwordDetectorTest {
    @Test
    fun acceptsRussianWakeWordWithPunctuationAndWhitespace() {
        val detector = HotwordDetector()
        assertTrue(detector.accepts("  Джарвис, открой навигатор! "))
        assertTrue(detector.accepts("ДЖАРВИС найди погоду"))
    }

    @Test
    fun normalizesYoAndRejectsWakeWordInsideAnotherWord() {
        val detector = HotwordDetector()
        assertTrue(detector.accepts("Джарвис, всё готово"))
        assertFalse(detector.accepts("поджарвис"))
        assertFalse(detector.accepts("эй джарвис"))
    }

    @Test
    fun disarmedDetectorRejectsTextUntilRearmed() {
        val detector = HotwordDetector()
        detector.disarm()
        assertFalse(detector.accepts("Джарвис, открой карты"))
        detector.arm()
        assertTrue(detector.accepts("Джарвис, открой карты"))
    }

    @Test
    fun acousticPathFailsClosedWhenNoKwsEngineIsBound() {
        val detector = HotwordDetector()
        assertFalse(detector.accepts(shortArrayOf(1, 2, 3)))
    }
}
