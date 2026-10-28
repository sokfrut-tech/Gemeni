package com.jarvis.breeno.assistant.service

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.platform.ComposeView

/** Real assistant session surface shown by the system VoiceInteractionService. */
class JarvisVoiceSession(service: JarvisSessionService) : VoiceInteractionSession(service) {
    override fun onCreate() {
        super.onCreate()
        setUiEnabled(true)
    }

    override fun onCreateContentView(): View {
        return ComposeView(context).apply {
            setContent {
                MaterialTheme {
                    Surface {
                        Text("Джарвис готов. Скажите команду.")
                    }
                }
            }
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        window?.window?.decorView?.contentDescription = "JarvisVoiceSession"
    }
}
