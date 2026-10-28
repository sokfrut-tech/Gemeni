package com.jarvis.breeno.assistant.ui.render

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class AlphaVideoSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {
    private val renderer = JarvisSphereRenderer()

    init {
        setEGLContextClientVersion(2)
        setZOrderOnTop(false)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setListening(listening: Boolean) {
        renderer.listening = listening
    }

    private class JarvisSphereRenderer : Renderer {
        var listening = false
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            nativeCreate()
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
