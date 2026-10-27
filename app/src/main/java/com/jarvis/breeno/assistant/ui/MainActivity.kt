package com.jarvis.breeno.assistant.ui
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.jarvis.breeno.assistant.ui.theme.JarvisTheme
class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{JarvisTheme{Surface{var t by remember{mutableStateOf("")};Column{Text("Джарвис",style=MaterialTheme.typography.headlineMedium);Text("Русский голосовой ассистент для ColorOS");Text(t)}}}}}}
