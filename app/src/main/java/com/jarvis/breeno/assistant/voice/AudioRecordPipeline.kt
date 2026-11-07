package com.jarvis.breeno.assistant.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class AudioRecordPipeline(private val context: Context) {
    companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL_COUNT = 1
    }

    fun frames(): Flow<ShortArray> = flow {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "RECORD_AUDIO permission is required"
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        check(minBuffer > 0) { "AudioRecord.getMinBufferSize failed: $minBuffer" }

        val bufferSize = maxOf(minBuffer, SAMPLE_RATE / 5 * 2)
        // Allocate the Kotlin buffer before opening the native recorder so an
        // allocation failure cannot strand an already-created AudioRecord.
        val buffer = ShortArray(bufferSize / 2)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        try {
            // Keep every operation after native resource creation inside the
            // release-protected block, including the initialization-state check.
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord initialization failed" }
            recorder.startRecording()
            while (currentCoroutineContext().isActive) {
                // Non-blocking reads keep coroutine cancellation and VAD deadlines responsive.
                val read = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                when {
                    read > 0 -> emit(buffer.copyOf(read))
                    read == 0 -> {
                        // Empty heartbeat lets the caller enforce no-speech/hard limits even
                        // if the audio HAL temporarily produces no samples.
                        delay(20)
                        emit(ShortArray(0))
                    }
                    read == AudioRecord.ERROR_DEAD_OBJECT -> break
                    read < 0 -> throw IllegalStateException("AudioRecord.read failed: $read")
                }
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }
}
