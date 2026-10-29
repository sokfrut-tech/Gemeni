package com.jarvis.breeno.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.filled.Send
import com.jarvis.breeno.assistant.core.LocalLlmEngine
import com.jarvis.breeno.assistant.core.TextCommandRouter
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jarvis.breeno.assistant.BuildConfig
import com.jarvis.breeno.assistant.R
import com.jarvis.breeno.assistant.ui.render.AlphaVideoSurfaceView
import com.jarvis.breeno.assistant.ui.theme.JarvisTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var runtime: JarvisRuntimeController
    private var pendingListen = false
    private var statusSink: ((String) -> Unit)? = null

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingListen) startRealListening()
        pendingListen = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = JarvisRuntimeHolder.get(this)
        setContent { JarvisTheme { JarvisOrbHome() } }
    }

    private fun requestAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRealListening()
        } else {
            pendingListen = true
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRealListening() {
        lifecycleScope.launch {
            runCatching {
                statusSink?.invoke("Подготавливаю локальный русский ASR…")
                withContext(Dispatchers.IO) { runtime.prepare { status -> runOnUiThread { statusSink?.invoke(status) } } }
                statusSink?.invoke("Микрофон активен — говорите")
                val text = withContext(Dispatchers.IO) { runtime.listenOnce { status -> runOnUiThread { statusSink?.invoke(status) } } }
                if (text.isNotBlank()) statusSink?.invoke("Распознано: $text")
            }.onFailure { error -> statusSink?.invoke("Ошибка runtime: ${error.message ?: error.javaClass.simpleName}") }
        }
    }

    private fun openSettings() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    private fun runQuickCommand(command: String) {
        when (command) {
            "Навигатор" -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("yandexnavi://build_route_on_map"))) }
            "Поиск" -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=Jarvis+Android")))
            "Телефон" -> startActivity(Intent(Intent.ACTION_DIAL))
        }
    }

    override fun onDestroy() {
        statusSink = null
        super.onDestroy()
    }

    @Composable
    private fun JarvisHome() {
        var listening by remember { mutableStateOf(false) }
        var transcript by remember { mutableStateOf("Готов. Нажмите микрофон и скажите команду") }
        var status by remember { mutableStateOf("Runtime не запускался") }
        var sphere by remember { mutableStateOf<AlphaVideoSurfaceView?>(null) }

        DisposableEffect(Unit) {
            statusSink = { value -> status = value; listening = value.contains("Микрофон активен") || value.contains("говорите") }
            onDispose { statusSink = null }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF070A10)) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.size(330.dp).align(Alignment.TopCenter).padding(top = 112.dp),
                    factory = { context -> AlphaVideoSurfaceView(context).also { it.contentDescription = "JarvisSphere"; sphere = it } },
                    update = { it.setListening(listening) }
                )
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ДЖАРВИС", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                            Text("Русский ассистент • ColorOS", color = Color(0xFF9CA8B8), style = MaterialTheme.typography.bodySmall)
                            Text("BUILD ${BuildConfig.VERSION_CODE}", color = Color(0xFF566274), style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { openSettings() }) { Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = Color(0xFFB8C5D6)) }
                    }
                    Spacer(modifier = Modifier.height(330.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painter = painterResource(R.drawable.assistant_mascot), contentDescription = "Джарвис", modifier = Modifier.size(92.dp), contentScale = ContentScale.Fit)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(if (listening) "Слушаю…" else "Я рядом", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Text(transcript, color = Color(0xFF9AA7B8), textAlign = TextAlign.Start)
                            Text(status, color = Color(0xFF6ED8F5), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xCC111824))) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Рабочие команды", color = Color(0xFFB8C5D6), style = MaterialTheme.typography.labelLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                QuickCommand("Навигатор") { runQuickCommand("Навигатор"); transcript = "Запускаю навигацию" }
                                QuickCommand("Поиск") { runQuickCommand("Поиск"); transcript = "Открываю поиск" }
                                QuickCommand("Телефон") { runQuickCommand("Телефон"); transcript = "Открываю телефон" }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    FloatingActionButton(onClick = { requestAndListen() }, containerColor = if (listening) Color(0xFF4FD9FF) else Color(0xFF162536), contentColor = if (listening) Color(0xFF041017) else Color(0xFFBDEFFF)) {
                        Icon(Icons.Default.Mic, contentDescription = "Микрофон", modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }

    private val textRouter by lazy { TextCommandRouter(this) }

    /** Текстовая команда с клавиатуры: тот же маршрутизатор, ответ озвучивается. */
    private fun sendText(text: String) {
        val query = text.trim()
        if (query.isEmpty()) return
        val reply = textRouter.handle(query)
        statusSink?.invoke(reply)
        runtime.say(reply)
    }

    /**
     * Экран только с шаром по центру и полем ввода внизу.
     * Нажатие на шар запускает запись голоса; текст отправляется клавиатурой.
     */
    @Composable
    private fun JarvisOrbHome() {
        var orbState by remember { mutableStateOf(OrbState.IDLE) }
        var input by remember { mutableStateOf("") }
        val aiReady = remember { LocalLlmEngine().isInstalled }

        DisposableEffect(Unit) {
            statusSink = { value ->
                orbState = when {
                    value.contains("Микрофон активен") || value.contains("говорите") -> OrbState.LISTENING
                    value.startsWith("Подготавливаю") || value.startsWith("Распознано") || value.startsWith("Ошибка") -> OrbState.IDLE
                    value.isNotBlank() -> OrbState.SPEAKING
                    else -> OrbState.IDLE
                }
            }
            onDispose { statusSink = null }
        }

        val send = {
            sendText(input)
            input = ""
        }

        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF05080C))) {
            JarvisArmorScreen(
                state = orbState,
                aiReady = aiReady,
                onTap = { if (orbState == OrbState.IDLE) requestAndListen() },
                modifier = Modifier.fillMaxSize(),
            )
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xCC111824),
                    unfocusedContainerColor = Color(0xCC111824),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color(0xFF5BE7FF),
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                trailingIcon = {
                    IconButton(onClick = { send() }) {
                        Icon(Icons.Default.Send, contentDescription = "Отправить", tint = Color(0xFF5BE7FF))
                    }
                },
            )
        }
    }

    @Composable
    private fun QuickCommand(label: String, onClick: () -> Unit) {
        androidx.compose.material3.TextButton(onClick = onClick) { Text(label, color = Color(0xFF8DEBFF)) }
    }
}
