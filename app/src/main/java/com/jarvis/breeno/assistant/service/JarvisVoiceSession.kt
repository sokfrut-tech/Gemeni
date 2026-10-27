package com.jarvis.breeno.assistant.service
import android.service.voice.VoiceInteractionSession
class JarvisVoiceSession(service:JarvisVoiceInteractionService):VoiceInteractionSession(service){ override fun onCreate(){super.onCreate()} }
