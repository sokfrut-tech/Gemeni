package com.jarvis.breeno.assistant.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenSelfTestResponseTest {
    @Test
    fun acceptsOnlyTheExactControlTokenIgnoringCaseAndOuterWhitespace() {
        assertTrue(isQwenSelfTestResponseValid("JARVIS_OK"))
        assertTrue(isQwenSelfTestResponseValid("  jarvis_ok\n"))
    }

    @Test
    fun rejectsResponsesThatMerelyContainTheControlToken() {
        assertFalse(isQwenSelfTestResponseValid("I cannot return JARVIS_OK"))
        assertFalse(isQwenSelfTestResponseValid("JARVIS_OK but also run another action"))
        assertFalse(isQwenSelfTestResponseValid("prefix JARVIS_OK"))
        assertFalse(isQwenSelfTestResponseValid(""))
        assertFalse(isQwenSelfTestResponseValid("JARVIS_OK."))
    }
}
