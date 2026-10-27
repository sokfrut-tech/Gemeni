package com.jarvis.breeno.assistant.voice

/**
 * Wake-word gate. Acoustic KWS is preferred; text matching remains available
 * for push-to-talk/testing and as a controlled fallback when the KWS model is
 * not downloaded yet.
 */
class HotwordDetector {
    @Volatile private var armed = true
    @Volatile private var kws: SherpaJarvisKwsEngine? = null

    fun arm() { armed = true; kws?.reset() }
    fun disarm() { armed = false }
    fun isArmed(): Boolean = armed

    fun bindKws(engine: SherpaJarvisKwsEngine) { kws = engine }

    fun accepts(samples: ShortArray): Boolean {
        if (!armed) return false
        return kws?.accept(samples) == true
    }

    fun accepts(text: String): Boolean {
        if (!armed) return false
        val normalized = text.lowercase()
            .replace('ё', 'е')
            .replace(Regex("[^а-яa-z0-9]+"), " ")
            .trim()
        return normalized == "джарвис" || normalized.startsWith("джарвис ")
    }

    fun close() {
        kws?.close()
        kws = null
    }
}
