package com.jarvis.breeno.assistant.voice
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
class AudioRecordPipeline(private val context:Context){fun frames():Flow<ShortArray>=flow{val n=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);val r=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,n);val b=ShortArray(n/2);r.startRecording();try{while(true){val k=r.read(b,0,b.size);if(k>0)emit(b.copyOf(k))}}finally{r.stop();r.release()}}}
