package com.jarvis.breeno.assistant.core

import kotlin.math.sqrt

/**
 * Real engine-driven states for the Jarvis Interactive Orb.
 * Every state corresponds to actual runtime activity rather than decorative animation.
 */
enum class JarvisOrbMode {
    IDLE,
    LISTENING,
    THINKING,
    SEARCHING,
    EXECUTING,
    SPEAKING,
    LEARNING,
    ERROR,
}

data class JarvisOrbTelemetry(
    val mode: JarvisOrbMode = JarvisOrbMode.IDLE,
    val audioRms: Float = 0f,
    val activeTaskCount: Int = 0,
    val statusLabel: String = "ОЖИДАЮ КОМАНДУ",
    val errorReason: String? = null,
)

/**
 * Computes normalized microphone loudness in [0f, 1f] from raw 16-bit PCM samples.
 * Used to drive real-time Interactive Orb pulsation during live voice capture.
 */
fun computeAudioRms(pcm: ShortArray): Float {
    if (pcm.isEmpty()) return 0f
    var sumSquares = 0.0
    for (sample in pcm) {
        val normalized = sample.toDouble() / 32768.0
        sumSquares += normalized * normalized
    }
    val rms = sqrt(sumSquares / pcm.size)
    // Scale speech RMS (~0.02..0.25) into a responsive [0f, 1f] visual range.
    return (rms * 4.0).toFloat().coerceIn(0f, 1f)
}
