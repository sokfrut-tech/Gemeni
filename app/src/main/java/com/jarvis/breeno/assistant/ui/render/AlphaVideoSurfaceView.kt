package com.jarvis.breeno.assistant.ui.render

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class AlphaVideoSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {
    var onRendererUnavailable: (() -> Unit)? = null
    private val renderer = JarvisSphereRenderer { onRendererUnavailable?.invoke() }

    init {
        setEGLContextClientVersion(2)
        setZOrderOnTop(false)
        setRenderer(renderer)
        // Render on demand: continuous shader redraws starved compact emulators.
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setListening(listening: Boolean) {
        renderer.listening = listening
        requestRender()
    }

    private class JarvisSphereRenderer(private val onUnavailable: () -> Unit) : Renderer {
        var listening = false
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            val ready = runCatching { nativeCreate() != 0L }.getOrDefault(false)
            if (!ready) Handler(Looper.getMainLooper()).post(onUnavailable)
        }
        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            nativeResize(width, height)
        }
        override fun onDrawFrame(gl: GL10?) {
            nativeDraw(if (listening) 1 else 0, 0)
        }

        private external fun nativeCreate(): Long
        private external fun nativeResize(width: Int, height: Int)
        private external fun nativeDraw(texture: Int, matrix: Int)
    }

    companion object {
        init { System.loadLibrary("jarvis_native") }
    }
}
