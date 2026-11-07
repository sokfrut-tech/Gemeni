package com.jarvis.breeno.assistant.ui

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Guards the shared ASR/VAD/audio pipeline against overlapping microphone
 * sessions. A failed entry is non-blocking: a second tap never queues another
 * recording behind the first one.
 */
internal class ListenSessionGate {
    private val active = AtomicBoolean(false)

    fun tryEnter(): Boolean = active.compareAndSet(false, true)

    fun leave() {
        check(active.compareAndSet(true, false)) {
            "Listen session gate was released without an active session"
        }
    }
}
