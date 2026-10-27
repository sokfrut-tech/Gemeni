package com.jarvis.breeno.assistant.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jarvis.breeno.assistant.R
import com.jarvis.breeno.assistant.ui.render.AlphaVideoSurfaceView
import com.jarvis.breeno.assistant.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JarvisTheme { JarvisHome() } }
    }
}

@Composable
private fun JarvisHome() {
    var listening by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("Готов. Скажите «Джарвис»") }
    var sphere by remember { mutableStateOf<AlphaVideoSurfaceView?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF070A10)) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier
                    .size(330.dp)
                    .align(Alignment.TopCenter)
                    .padding(top = 112.dp),
                factory = { context ->
                    AlphaVideoSurfaceView(context).also { sphere = it }
                },
                update = { it.setListening(listening) }
            )
            DisposableEffect(Unit) {
                onDispose { sphere = null }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ДЖАРВИС", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text("Русский ассистент • ColorOS", color = Color(0xFF9CA8B8), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = Color(0xFFB8C5D6))
                    }
                }

                Spacer(modifier = Modifier.height(330.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.assistant_mascot),
                        contentDescription = "Джарвис",
                        modifier = Modifier.size(92.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(if (listening) "Слушаю…" else "Я рядом", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(transcript, color = Color(0xFF9AA7B8), textAlign = TextAlign.Start)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xCC111824))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Быстрые команды", color = Color(0xFFB8C5D6), style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            QuickCommand("Навигатор") { transcript = "Открыть Яндекс Навигатор" }
                            QuickCommand("Поиск") { transcript = "Искать в интернете" }
                            QuickCommand("Телефон") { transcript = "Позвонить" }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                FloatingActionButton(
                    onClick = {
                        listening = !listening
                        sphere?.setListening(listening)
                        transcript = if (listening) "Говорите…" else "Готов. Скажите «Джарвис»"
                    },
                    containerColor = if (listening) Color(0xFF4FD9FF) else Color(0xFF162536),
                    contentColor = if (listening) Color(0xFF041017) else Color(0xFFBDEFFF)
                ) { Icon(Icons.Default.Mic, contentDescription = "Микрофон", modifier = Modifier.size(28.dp)) }
            }
        }
    }
}

@Composable
private fun QuickCommand(label: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) { Text(label, color = Color(0xFF8DEBFF)) }
}
