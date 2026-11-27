package com.jarvis.breeno.assistant.tools

import com.jarvis.breeno.assistant.voice.JarvisSpeaker
import com.jarvis.breeno.assistant.voice.PiperTtsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisPhoneActionsTest {
    @Test
    fun parsesTimerAlarmFlashlightAndSettingsInRussian() {
        val timer = JarvisPhoneActions.parseCommand("Поставь таймер на 5 минут чай") as PhoneActionIntent.SetTimer
        assertEquals(300, timer.seconds)
        assertEquals("чай", timer.label)

        val alarm = JarvisPhoneActions.parseCommand("Разбуди меня в 7:30 работа") as PhoneActionIntent.SetAlarm
        assertEquals(7, alarm.hour)
        assertEquals(30, alarm.minute)
        assertEquals("работа", alarm.label)

        val torchOn = JarvisPhoneActions.parseCommand("Включи фонарик") as PhoneActionIntent.ToggleFlashlight
        assertTrue(torchOn.enabled)

        val torchOff = JarvisPhoneActions.parseCommand("Погаси фонарик") as PhoneActionIntent.ToggleFlashlight
        assertTrue(!torchOff.enabled)

        val wifi = JarvisPhoneActions.parseCommand("Открой настройки Wi-Fi") as PhoneActionIntent.OpenSettings
        assertEquals(SettingsSection.WIFI, wifi.section)
    }

    @Test
    fun validatesSafeHttpUrlsAndRejectsDangerousSchemes() {
        assertEquals("https://example.com/docs", JarvisPhoneActions.validateSafeHttpUrl("example.com/docs"))
        assertEquals("http://192.168.1.10/status", JarvisPhoneActions.validateSafeHttpUrl("http://192.168.1.10/status"))
        assertNull(JarvisPhoneActions.validateSafeHttpUrl("javascript:alert(1)"))
        assertNull(JarvisPhoneActions.validateSafeHttpUrl("file:///etc/passwd"))
        assertNull(JarvisPhoneActions.validateSafeHttpUrl("intent://foo#Intent"))
        assertNull(JarvisPhoneActions.validateSafeHttpUrl("tel:+79991234567"))
    }

    @Test
    fun evaluatesSafeArithmeticExpressionsWithoutEval() {
        val calc1 = JarvisPhoneActions.parseCommand("Посчитай (125 + 75) * 4 / 2") as PhoneActionIntent.Calculate
        assertEquals("400", calc1.formattedResult)

        val calc2 = JarvisPhoneActions.parseCommand("Сколько будет 15% от 2400") as PhoneActionIntent.Calculate
        assertEquals("360", calc2.formattedResult)

        assertNull(JarvisPhoneActions.evaluateMathExpression("10 / 0"))
        assertNull(JarvisPhoneActions.evaluateMathExpression("Runtime.getRuntime()"))
    }

    @Test
    fun parsesTomorrowAlarmFuzzyWakeWordsAndHandsFreeControls() {
        val tomorrowAlarm = JarvisPhoneActions.parseCommand("Джарвис поставь будильник завтра на 6:30") as PhoneActionIntent.SetAlarm
        assertEquals(6, tomorrowAlarm.hour)
        assertEquals(30, tomorrowAlarm.minute)
        assertEquals("Будильник на завтра", tomorrowAlarm.label)

        val eveningAlarm = JarvisPhoneActions.parseCommand("Разбуди меня завтра в 8 вечера") as PhoneActionIntent.SetAlarm
        assertEquals(20, eveningAlarm.hour)
        assertEquals(0, eveningAlarm.minute)

        assertTrue(JarvisPhoneActions.isOnlyWakeWord("жарюс"))
        assertTrue(JarvisPhoneActions.isOnlyWakeWord("Джарвис"))
        assertFalse(JarvisPhoneActions.isOnlyWakeWord("джервис нарисуй кота"))
        assertEquals("нарисуй кота", JarvisPhoneActions.stripWakeWordPrefix("джервис нарисуй кота"))

        assertTrue(JarvisPhoneActions.isStandaloneTurnOffCommand("выключить"))
        assertTrue(JarvisPhoneActions.isStandaloneTurnOffCommand("выключи"))
        assertTrue(JarvisPhoneActions.isStopListeningCommand("стоп"))
        assertTrue(JarvisPhoneActions.isStopListeningCommand("Джарвис, хватит"))

        assertEquals("Фонарик включён.", PiperTtsEngine.ensureTerminalPunctuation("Фонарик включён"))
        assertEquals("Готово!", PiperTtsEngine.ensureTerminalPunctuation("Готово!"))
        assertTrue(JarvisSpeaker.calculateMaxDrainWaitMs(22_050L, 22_050) in 1_500L..4_500L)
    }
}
