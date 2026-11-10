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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Background = Color(0xFF070B12)
private val Panel = Color(0xCC111C2A)
private val Cyan = Color(0xFF75E5FF)
private val Muted = Color(0xFF9AAABD)

class MainActivity : ComponentActivity() {
    private lateinit var runtime: JarvisRuntimeController
    private var pendingListen = false
    private var pendingModelDownloadConsent = false
    private var modelConsentSink: (() -> Unit)? = null
    private var statusSink: ((String) -> Unit)? = null
    private var listeningJob: Job? = null
    private var modelCheckJob: Job? = null

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingListen) startRealListening(pendingModelDownloadConsent)
        pendingListen = false
        pendingModelDownloadConsent = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = JarvisRuntimeController(this)
        setContent { JarvisTheme { JarvisHome() } }
    }

    private fun requestAndListen() {
        if (listeningJob != null || modelCheckJob != null) return
        modelCheckJob = lifecycleScope.launch {
            try {
                statusSink?.invoke("Проверяю локальную модель…")
                val ready = withContext(Dispatchers.IO) { runtime.isRussianAsrReady() }
                if (ready) requestMicrophoneAndListen(allowModelDownload = false)
                else modelConsentSink?.invoke()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                statusSink?.invoke("Ошибка проверки модели: ${error.message ?: error.javaClass.simpleName}")
            } finally {
                // Keep the job reference until cleanup finishes to prevent overlapping preflight work.
                modelCheckJob = null
            }
        }
    }

    private fun requestModelDownloadAndListen() {
        if (listeningJob != null) return
        requestMicrophoneAndListen(allowModelDownload = true)
    }

    private fun requestMicrophoneAndListen(allowModelDownload: Boolean) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRealListening(allowModelDownload)
        } else {
            pendingListen = true
            pendingModelDownloadConsent = allowModelDownload
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRealListening(allowModelDownload: Boolean) {
        if (listeningJob != null) return
        listeningJob = lifecycleScope.launch {
            try {
                statusSink?.invoke("Подготавливаю локальный русский ASR…")
                withContext(Dispatchers.IO) { runtime.prepare(allowModelDownload) { status -> runOnUiThread { statusSink?.invoke(status) } } }
                statusSink?.invoke("Микрофон активен — говорите")
                val text = withContext(Dispatchers.IO) { runtime.listenOnce { status -> runOnUiThread { statusSink?.invoke(status) } } }
                if (text.isNotBlank()) statusSink?.invoke("Распознано: $text")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                statusSink?.invoke("Ошибка runtime: ${error.message ?: error.javaClass.simpleName}")
            } finally {
                listeningJob = null
            }
        }
    }

    private fun stopListening() {
        pendingListen = false
        modelCheckJob?.cancel()
        listeningJob?.cancel()
        statusSink?.invoke("Прослушивание остановлено")
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
        modelCheckJob?.cancel()
        modelCheckJob = null
        listeningJob?.cancel()
        listeningJob = null
        runtime.close()
        super.onDestroy()
    }

    @Composable
    private fun JarvisHome() {
        var listening by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var showModelConsent by remember { mutableStateOf(false) }
        var transcript by remember { mutableStateOf("Готов к работе. Коснитесь ядра и скажите команду.") }
        var status by remember { mutableStateOf("ОЖИДАЮ КОМАНДУ") }

        DisposableEffect(Unit) {
            modelConsentSink = { showModelConsent = true }
            statusSink = { value ->
                status = value.uppercase()
                listening = value.contains("Микрофон активен") || value.contains("говорите")
                busy = !(
                    value == "Ожидаю команду" ||
                    value == "Прослушивание остановлено" ||
                    value == "Речь не распознана" ||
                    value.startsWith("Ошибка runtime:") ||
                    value.startsWith("Распознано:") ||
                    value.startsWith("Открываю") ||
                    value.startsWith("Вы сказали:")
                )
                if (value.startsWith("Распознано:") || value.startsWith("Открываю") || value.startsWith("Вы сказали:")) {
                    transcript = value
                }
            }
            onDispose { statusSink = null; modelConsentSink = null }
        }

        if (showModelConsent) {
            AlertDialog(
                onDismissRequest = { showModelConsent = false },
                title = { Text("Загрузить русскую голосовую модель?") },
                text = { Text("Для локального распознавания речи нужны файлы модели. Они будут загружены из интернета в память приложения. Без вашего подтверждения загрузка не начнётся.") },
                confirmButton = {
                    TextButton(onClick = {
                        showModelConsent = false
                        requestModelDownloadAndListen()
                    }) { Text("ЗАГРУЗИТЬ И ПРОДОЛЖИТЬ") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showModelConsent = false
                        status = "ЗАГРУЗКА МОДЕЛИ ОТМЕНЕНА — НИЧЕГО НЕ СКАЧАНО"
                    }) { Text("НЕ СЕЙЧАС") }
                }
            )
        }

        Surface(modifier = Modifier.fillMaxSize(), color = Background) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val compact = maxHeight < 720.dp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = if (compact) 10.dp else 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (busy || listening) Cyan else Color(0xFF52677B))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (listening) "LISTENING" else "JARVIS SYSTEM",
                            color = Muted,
                            style = MaterialTheme.typography.labelSmall
                        )
                        IconButton(onClick = { openSettings() }) {
                            Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = Muted)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clickable(
                                onClickLabel = if (busy || listening) "Остановить Джарвиса" else "Активировать Джарвиса",
                                onClick = { if (busy || listening) stopListening() else requestAndListen() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            modifier = Modifier
                                .size(if (compact) 286.dp else 340.dp)
                                .widthIn(max = maxWidth),
                            factory = { context ->
                                AlphaVideoSurfaceView(context).apply {
                                    contentDescription = "Интерактивное энергетическое ядро Джарвиса"
                                }
                            },
                            update = { it.setListening(listening) }
                        )
                    }

                    Text(
                        when {
                            listening -> "Я СЛУШАЮ"
                            busy -> "Я ДУМАЮ"
                            else -> "Я РЯДОМ"
                        },
                        color = if (listening) Cyan else Color(0xFFE5F3FF),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        status,
                        color = if (status.startsWith("ОШИБКА")) Color(0xFFFF9C9C) else Cyan,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    Text(
                        transcript,
                        color = Muted,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp)
                    )
                    Text(
                        if (busy || listening) "КОСНИТЕСЬ ЯДРА, ЧТОБЫ ОСТАНОВИТЬ" else "КОСНИТЕСЬ ЯДРА, ЧТОБЫ ГОВОРИТЬ",
                        color = Color(0xFF71879B),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }
        }
    }

}
