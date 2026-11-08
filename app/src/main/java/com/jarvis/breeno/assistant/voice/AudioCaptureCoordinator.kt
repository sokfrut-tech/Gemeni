package com.jarvis.breeno.assistant.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single owner for microphone frame collection.
 *
 * The coordinator never creates an AudioRecord itself. Its source must be the
 * one application-scoped AudioRecordPipeline. All start/stop/suspend operations
 * are serialized, and stop does not return until the flow's finally block has
 * completed (therefore AudioRecordPipeline has released its recorder).
 *
 * Interruption is fail-closed: resume requires a fresh explicit opt-in and
 * returns to IDLE so callers must re-check permission, thermal policy and audio
 * availability before acquiring the microphone again.
 */
class AudioCaptureCoordinator(
    private val scope: CoroutineScope,
    private val frames: () -> Flow<ShortArray>,
    private val onFrame: (ShortArray) -> Unit,
) {
    enum class State {
        IDLE,
        CAPTURING,
        STOPPING,
        SUSPENDED_FOR_CALL,
        SUSPENDED_FOR_SYSTEM_AUDIO,
        FAILED,
    }

    private val gate = Mutex()
    private val mutableState = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = mutableState.asStateFlow()
    private var captureJob: Job? = null

    suspend fun start(): Boolean = gate.withLock {
        if (mutableState.value != State.IDLE && mutableState.value != State.FAILED) return@withLock false
        if (captureJob?.isActive == true) return@withLock false

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                frames().collect { frame ->
                    if (frame.isNotEmpty()) onFrame(frame)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.value = State.FAILED
                throw error
            } finally {
                if (mutableState.value == State.CAPTURING ||
                    mutableState.value == State.STOPPING
                ) {
                    mutableState.value = State.IDLE
                }
            }
        }
        captureJob = job
        mutableState.value = State.CAPTURING
        job.start()
        true
    }

    suspend fun stop() = gate.withLock {
        val job = captureJob
        if (job != null) {
            mutableState.value = State.STOPPING
            job.cancelAndJoin()
            captureJob = null
        }
        if (mutableState.value == State.STOPPING) mutableState.value = State.IDLE
    }

    suspend fun suspendForCall() = suspendCapture(State.SUSPENDED_FOR_CALL)

    suspend fun suspendForSystemAudio() = suspendCapture(State.SUSPENDED_FOR_SYSTEM_AUDIO)

    private suspend fun suspendCapture(reason: State) = gate.withLock {
        val job = captureJob
        if (job != null) {
            mutableState.value = State.STOPPING
            job.cancelAndJoin()
            captureJob = null
        }
        // No capture may remain active when a suspended state becomes visible.
        mutableState.value = reason
    }

    suspend fun resumeAfterInterruption(userOptInStillActive: Boolean): Boolean = gate.withLock {
        if (!userOptInStillActive) return@withLock false
        if (mutableState.value != State.SUSPENDED_FOR_CALL &&
            mutableState.value != State.SUSPENDED_FOR_SYSTEM_AUDIO
        ) return@withLock false
        mutableState.value = State.IDLE
        true
    }

    suspend fun close() = stop()
}
