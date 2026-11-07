package com.jarvis.breeno.assistant.ui

/**
 * Guards the shared ASR/VAD/audio pipeline against overlapping microphone
 * sessions and defers native-resource shutdown until an active session exits.
 */
internal class ListenSessionGate {
    private val lock = Any()
    private var active = false
    private var closing = false
    private var deferredClose: (() -> Unit)? = null

    fun tryEnter(): Boolean = synchronized(lock) {
        if (closing || active) return@synchronized false
        active = true
        true
    }

    fun leave() {
        val closeAction = synchronized(lock) {
            check(active) { "Listen session gate was released without an active session" }
            active = false
            if (closing) deferredClose.also { deferredClose = null } else null
        }
        closeAction?.invoke()
    }

    /**
     * Prevents new sessions immediately. If a session is active, its resources
     * are closed by leave() after cancellation/processing has unwound.
     */
    fun closeWhenIdle(action: () -> Unit) {
        val closeNow = synchronized(lock) {
            if (closing) return
            closing = true
            if (active) {
                deferredClose = action
                null
            } else {
                action
            }
        }
        closeNow?.invoke()
    }

    val isClosing: Boolean
        get() = synchronized(lock) { closing }
}
