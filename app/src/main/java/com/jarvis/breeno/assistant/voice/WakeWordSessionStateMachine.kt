package com.jarvis.breeno.assistant.voice

/**
 * Lifecycle guard for an explicitly user-started wake-word session.
 *
 * This class owns no Android or native resources. The session coordinator must
 * release its microphone/KWS/VAD resources whenever capture is suspended,
 * stopped, or failed.
 */
internal class WakeWordSessionStateMachine {
    enum class State {
        STOPPED,
        PREPARING,
        WAITING_FOR_HOTWORD,
        CAPTURING_COMMAND,
        PROCESSING,
        SUSPENDED_FOR_CALL,
        SUSPENDED_FOR_SYSTEM_AUDIO,
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

    /** A completed command returns to detection only while the session remains active. */
    fun commandFinished(): Boolean = transition(
        allowed = setOf(State.PROCESSING),
        next = State.WAITING_FOR_HOTWORD
    )

    /**
     * Call state is a hard interruption: the resource owner must cancel capture
     * and release AudioRecord/native resources before publishing this state.
     */
    fun suspendForCall(): Boolean = transition(
        allowed = ACTIVE_CAPTURE_STATES,
        next = State.SUSPENDED_FOR_CALL
    )

    /** Yield to another app/system component that currently owns microphone capture. */
    fun suspendForSystemAudio(): Boolean = transition(
        allowed = ACTIVE_CAPTURE_STATES,
        next = State.SUSPENDED_FOR_SYSTEM_AUDIO
    )

    /**
     * Interruption never resumes implicitly. A fresh preparation transition is
     * allowed only when the caller proves the user's explicit opt-in is still active.
     */
    fun resumeAfterInterruption(userOptInStillActive: Boolean): Boolean {
        if (!userOptInStillActive) return false
        return transition(
            allowed = setOf(State.SUSPENDED_FOR_CALL, State.SUSPENDED_FOR_SYSTEM_AUDIO),
            next = State.PREPARING
        )
    }

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

    private companion object {
        val ACTIVE_CAPTURE_STATES = setOf(
            State.PREPARING,
            State.WAITING_FOR_HOTWORD,
            State.CAPTURING_COMMAND,
            State.PROCESSING
        )
    }
}
