package com.jarvis.breeno.assistant.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * Hardware-accelerated H.264 MP4 video synthesizer using Android MediaCodec + MediaMuxer.
 * Turns storyboard keyframes into a smooth animated MP4 clip (Ken Burns zoom/pan + crossfade)
 * directly on the phone without external FFmpeg binaries.
 */
object JarvisMp4VideoSynthesizer {
    fun renderAnimatedMp4(
        keyframes: List<Bitmap>,
        outputFile: File,
        width: Int = 512,
        height: Int = 512,
        fps: Int = 24,
        secondsPerScene: Int = 2,
    ): File {
        require(keyframes.isNotEmpty()) { "Нужен хотя бы один кадр для создания видео" }
        // Snapdragon/MediaCodec H.264 requires dimensions aligned to 16 pixels.
        val safeW = ((width + 15) / 16) * 16
        val safeH = ((height + 15) / 16) * 16
        val framesPerScene = (fps * secondsPerScene).coerceAtLeast(fps)
        val crossfadeFrames = (fps / 2).coerceAtLeast(6)

        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, safeW, safeH).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_500_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        var inputSurface: android.view.Surface? = null

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = codec.createInputSurface()
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val matrix = Matrix()
            var globalFrame = 0L
            val frameDurationUs = 1_000_000L / fps

            fun drain(endOfStream: Boolean) {
                if (endOfStream) codec.signalEndOfInputStream()
                while (true) {
                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000L else 0L)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) break
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (!muxerStarted) {
                                trackIndex = muxer.addTrack(codec.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                        outIdx >= 0 -> {
                            val encoded = codec.getOutputBuffer(outIdx)
                            if (encoded != null && bufferInfo.size > 0 &&
                                (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 &&
                                muxerStarted
                            ) {
                                encoded.position(bufferInfo.offset)
                                encoded.limit(bufferInfo.offset + bufferInfo.size)
                                bufferInfo.presentationTimeUs = globalFrame * frameDurationUs
                                muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                break
                            }
                        }
                    }
                }
            }

            for (sceneIdx in keyframes.indices) {
                val currentBmp = keyframes[sceneIdx]
                val nextBmp = keyframes.getOrNull(sceneIdx + 1)
                val zoomIn = (sceneIdx % 2 == 0)

                for (localFrame in 0 until framesPerScene) {
                    val t = localFrame.toFloat() / (framesPerScene - 1).coerceAtLeast(1)
                    val canvas = inputSurface.lockCanvas(null)
                    try {
                        canvas.drawColor(Color.BLACK)
                        paint.alpha = 255
                        drawKenBurns(canvas, currentBmp, safeW, safeH, t, zoomIn, paint, matrix)

                        if (nextBmp != null && localFrame >= framesPerScene - crossfadeFrames) {
                            val fadeT = (localFrame - (framesPerScene - crossfadeFrames)).toFloat() /
                                crossfadeFrames.toFloat()
                            paint.alpha = (fadeT * 255f).toInt().coerceIn(0, 255)
                            drawKenBurns(canvas, nextBmp, safeW, safeH, 0f, !zoomIn, paint, matrix)
                        }
                    } finally {
                        inputSurface.unlockCanvasAndPost(canvas)
                    }
                    drain(endOfStream = false)
                    globalFrame++
                }
            }
            drain(endOfStream = true)
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { inputSurface?.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
        return outputFile
    }

    private fun drawKenBurns(
        canvas: Canvas,
        bmp: Bitmap,
        targetW: Int,
        targetH: Int,
        progress: Float,
        zoomIn: Boolean,
        paint: Paint,
        matrix: Matrix,
    ) {
        val baseScale = maxOf(
            targetW.toFloat() / bmp.width.coerceAtLeast(1),
            targetH.toFloat() / bmp.height.coerceAtLeast(1),
        )
        val zoom = if (zoomIn) 1.0f + 0.14f * progress else 1.14f - 0.14f * progress
        val scale = baseScale * zoom
        val scaledW = bmp.width * scale
        val scaledH = bmp.height * scale
        val panX = (progress - 0.5f) * (scaledW - targetW) * 0.4f
        val dx = (targetW - scaledW) / 2f - panX
        val dy = (targetH - scaledH) / 2f

        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)
        canvas.drawBitmap(bmp, matrix, paint)
    }
}
