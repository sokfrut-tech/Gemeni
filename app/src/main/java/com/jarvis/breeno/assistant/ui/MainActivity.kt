package com.jarvis.breeno.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jarvis.breeno.assistant.BuildConfig
import com.jarvis.breeno.assistant.core.AssistantState
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
    private var stateSink: ((AssistantState) -> Unit)? = null

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingListen) startRealListening()
        pendingListen = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = JarvisRuntimeController(this)
        setContent { JarvisTheme { JarvisHome() } }
    }

    private fun requestAndListen() {
        lifecycleScope.launch {
            val modelReady = withContext(Dispatchers.IO) { runtime.isRussianAsrReady() }
            if (!modelReady) {
                modelDownloadConsentSink?.invoke(true)
                return@launch
            }
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startRealListening()
            } else {
                pendingListen = true
                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private var modelDownloadConsentSink: ((Boolean) -> Unit)? = null
    private var modelDownloadBusySink: ((Boolean) -> Unit)? = null

    private fun downloadModelAfterUserConsent() {
        modelDownloadBusySink?.invoke(true)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    runtime.downloadRussianAsrAfterConsent { value ->
                        runOnUiThread { statusSink?.invoke(value) }
                    }
                }
            }.onSuccess {
                statusSink?.invoke("Модель установлена локально. Готов к работе.")
                modelDownloadConsentSink?.invoke(false)
                requestAndListen()
            }.onFailure { error ->
                statusSink?.invoke("Загрузка не выполнена: ${error.message ?: error.javaClass.simpleName}")
            }
            modelDownloadBusySink?.invoke(false)
        }
    }

    private fun startRealListening() {
        lifecycleScope.launch {
            runCatching {
                statusSink?.invoke("Подготавливаю локальный русский ASR…")
                withContext(Dispatchers.IO) { runtime.prepare { status -> runOnUiThread { statusSink?.invoke(status) } } }
                statusSink?.invoke("Микрофон активен — говорите")
                val text = withContext(Dispatchers.IO) {
                    runtime.listenOnce(
                        onProgress = { status -> runOnUiThread { statusSink?.invoke(status) } },
                        onState = { state -> runOnUiThread { stateSink?.invoke(state) } }
                    )
                }
                if (text.isNotBlank()) statusSink?.invoke("Распознано: $text")
            }.onFailure { error ->
                stateSink?.invoke(AssistantState.ERROR)
                statusSink?.invoke("Ошибка runtime: ${error.message ?: error.javaClass.simpleName}")
            }
        }
    }

    private fun openSettings() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    private fun runQuickCommand(command: String): String = runtime.runQuickCommand(command)

    override fun onDestroy() {
        statusSink = null
        stateSink = null
        runtime.close()
        super.onDestroy()
    }

    @Composable
    private fun JarvisHome() {
        var listening by remember { mutableStateOf(false) }
        var transcript by remember { mutableStateOf("Готов. Нажмите микрофон и скажите команду") }
        var status by remember { mutableStateOf("Runtime не запускался") }
        var assistantState by remember { mutableStateOf(AssistantState.IDLE) }
        var sphere by remember { mutableStateOf<AlphaVideoSurfaceView?>(null) }
        var showModelDownloadConsent by remember { mutableStateOf(false) }
        var modelDownloadInProgress by remember { mutableStateOf(false) }

        DisposableEffect(Unit) {
            statusSink = { value -> status = value; if (value.startsWith("Распознано:") || value.startsWith("Открываю") || value.startsWith("Строю маршрут") || value.startsWith("Сейчас ")) transcript = value }
            stateSink = { value -> assistantState = value; listening = value == AssistantState.LISTENING }
            modelDownloadConsentSink = { showModelDownloadConsent = it }
            modelDownloadBusySink = { modelDownloadInProgress = it }
            onDispose { statusSink = null; stateSink = null; modelDownloadConsentSink = null; modelDownloadBusySink = null }
        }

        if (showModelDownloadConsent) {
            AlertDialog(
                onDismissRequest = { if (!modelDownloadInProgress) showModelDownloadConsent = false },
                title = { Text("Загрузить русскую модель?") },
                text = {
                    Text(
                        "Для распознавания речи нужна локальная модель. Если продолжить, Джарвис подключится к Hugging Face и загрузит только файлы модели в память приложения. Ваш голос, сообщения, файлы и память Джарвиса не отправляются. Передача IP-адреса и технических данных соединения серверу неизбежна. Загрузка не начнётся без вашего нажатия."
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !modelDownloadInProgress,
                        onClick = {
                            modelDownloadInProgress = true
                            showModelDownloadConsent = false
                            downloadModelAfterUserConsent()
                        }
                    ) { Text("Разрешить загрузку") }
                },
                dismissButton = {
                    TextButton(
                        enabled = !modelDownloadInProgress,
                        onClick = { showModelDownloadConsent = false }
                    ) { Text("Не сейчас") }
                }
            )
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
                            Text(
                                when (assistantState) {
                                    AssistantState.LISTENING -> "Слушаю…"
                                    AssistantState.TRANSCRIBING -> "Распознаю речь…"
                                    AssistantState.UNDERSTANDING -> "Думаю…"
                                    AssistantState.EXECUTING -> "Выполняю команду…"
                                    AssistantState.SPEAKING -> "Отвечаю…"
                                    AssistantState.WAITING_FOR_APPROVAL -> "Жду подтверждения…"
                                    AssistantState.ERROR -> "Нужна проверка"
                                    AssistantState.IDLE -> "Я рядом"
                                },
                                color = Color.White,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold
                            )
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
                                QuickCommand("Навигатор") { transcript = runQuickCommand("Навигатор") }
                                QuickCommand("Поиск") { transcript = runQuickCommand("Поиск") }
                                QuickCommand("Телефон") { transcript = runQuickCommand("Телефон") }
                            }
                        }
                    }
                    if (assistantState == AssistantState.WAITING_FOR_APPROVAL) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    transcript = runtime.confirmPendingAction()
                                    status = transcript
                                    assistantState = AssistantState.IDLE
                                }
                            ) { Text("Подтвердить", color = Color(0xFF8DEBFF)) }
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    transcript = runtime.cancelPendingAction()
                                    status = transcript
                                    assistantState = AssistantState.IDLE
                                }
                            ) { Text("Отмена", color = Color(0xFFFFB38A)) }
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

    @Composable
    private fun QuickCommand(label: String, onClick: () -> Unit) {
        androidx.compose.material3.TextButton(onClick = onClick) { Text(label, color = Color(0xFF8DEBFF)) }
    }
}
