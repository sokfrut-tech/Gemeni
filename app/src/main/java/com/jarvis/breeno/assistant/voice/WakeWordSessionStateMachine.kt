package com.jarvis.breeno.assistant.voice

/**
 * Lifecycle guard for an explicitly user-started wake-word session.
 *
 * This class owns no Android or native resources. The session coordinator must
 * release its microphone/KWS/VAD resources when [stop] or [fail] is called.
 */
internal class WakeWordSessionStateMachine {
    enum class State {
        STOPPED,
        PREPARING,
        WAITING_FOR_HOTWORD,
        CAPTURING_COMMAND,
        PROCESSING,
        ERROR
    }

    var state: State = State.STOPPED
        private set

    fun start(): Boolean = transition(
        allowed = setOf(State.STOPPED, State.ERROR),
        next = State.PREPARING
    )

    fun modelsReady(): Boolean = transition(
        allowed = setOf(State.PREPARING),
        next = State.WAITING_FOR_HOTWORD
    )

    fun hotwordDetected(): Boolean = transition(
        allowed = setOf(State.WAITING_FOR_HOTWORD),
        next = State.CAPTURING_COMMAND
    )

    fun commandCaptured(): Boolean = transition(
        allowed = setOf(State.CAPTURING_COMMAND),
        next = State.PROCESSING
    )

    /** A completed or empty command returns to hotword detection while opted in. */
    fun commandFinished(): Boolean = transition(
        allowed = setOf(State.PROCESSING),
        next = State.WAITING_FOR_HOTWORD
    )

    fun stop() {
        state = State.STOPPED
    }

    fun fail() {
        state = State.ERROR
    }

    private fun transition(allowed: Set<State>, next: State): Boolean {
        if (state !in allowed) return false
        state = next
        return true
    }
}
