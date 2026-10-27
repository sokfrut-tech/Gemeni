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
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord initialization failed" }

        val buffer = ShortArray(bufferSize / 2)
        try {
            recorder.startRecording()
            while (currentCoroutineContext().isActive) {
                val read = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                when {
                    read > 0 -> emit(buffer.copyOf(read))
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
