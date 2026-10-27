package com.jarvis.breeno.assistant.voice

/**
 * Wake-word gate. The detector deliberately separates the trigger decision from
 * ASR text so a real Sherpa KWS backend can be plugged in without changing the
 * assistant state machine. Text fallback is retained for push-to-talk/testing.
 */
class HotwordDetector {
    @Volatile private var armed = true

    fun arm() { armed = true }
    fun disarm() { armed = false }
    fun isArmed(): Boolean = armed

    fun accepts(text: String): Boolean {
        if (!armed) return false
        val normalized = text.lowercase()
            .replace('ё', 'е')
            .replace(Regex("[^а-яa-z0-9]+"), " ")
            .trim()
        return normalized == "джарвис" || normalized.startsWith("джарвис ")
    }
}
