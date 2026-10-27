package com.jarvis.breeno.assistant.ui.setup
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
class SetupAssistantActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{Text("Настройка Джарвиса: включите микрофон, голосовой сервис и доступность.")}}}
