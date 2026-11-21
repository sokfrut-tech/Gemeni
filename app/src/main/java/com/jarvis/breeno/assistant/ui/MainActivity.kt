package com.jarvis.breeno.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jarvis.breeno.assistant.control.JarvisControlCenterStore
import com.jarvis.breeno.assistant.control.JarvisEventLevel
import com.jarvis.breeno.assistant.core.AndroidRuntimeSignalsSource
import com.jarvis.breeno.assistant.core.JarvisOrbMode
import com.jarvis.breeno.assistant.savevideo.SaveVideoManager
import com.jarvis.breeno.assistant.ui.theme.JarvisTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Background = Color(0xFF070B12)
private val NavBarBg = Color(0xFF0C1420)
private val Cyan = Color(0xFF75E5FF)
private val Muted = Color(0xFF9AAABD)

private enum class JarvisTab(val title: String) {
    CORE("Ядро"),
    SAVEVIDEO("SaveVideo"),
    CONTROL("Центр"),
}

class MainActivity : ComponentActivity() {
    private lateinit var runtime: JarvisRuntimeController
    private lateinit var signalsSource: AndroidRuntimeSignalsSource
    private lateinit var controlCenter: JarvisControlCenterStore
    private lateinit var saveVideoManager: SaveVideoManager

    private var pendingListen = false
    private var pendingModelDownloadConsent = false
    private var modelConsentSink: (() -> Unit)? = null
    private var statusSink: ((String) -> Unit)? = null
    private var audioRmsSink: ((Float) -> Unit)? = null
    private var orbModeSink: ((JarvisOrbMode) -> Unit)? = null
    private var tabSink: ((JarvisTab) -> Unit)? = null
    private var listeningJob: Job? = null
    private var modelCheckJob: Job? = null

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingListen) {
            startRealListening(pendingModelDownloadConsent)
        } else if (!granted && pendingListen) {
            orbModeSink?.invoke(JarvisOrbMode.ERROR)
            statusSink?.invoke("Нет разрешения на микрофон")
            controlCenter.logEvent("ПРАВА", "Микрофон отклонен", "Требуется доступ к RECORD_AUDIO", JarvisEventLevel.WARN)
        }
        pendingListen = false
        pendingModelDownloadConsent = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = JarvisRuntimeController(this)
        signalsSource = AndroidRuntimeSignalsSource(this)
        controlCenter = JarvisControlCenterStore()

        val moviesBase = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        saveVideoManager = SaveVideoManager(
            downloadsDir = File(moviesBase, "JarvisSaveVideo"),
            historyFile = File(filesDir, "savevideo_history.json"),
            onEvent = { title, detail, isError ->
                controlCenter.logEvent(
                    category = "SAVEVIDEO",
                    title = title,
                    detail = detail,
                    level = if (isError) JarvisEventLevel.ERROR else JarvisEventLevel.INFO,
                )
            },
        )

        lifecycleScope.launch {
            saveVideoManager.tasks.collect { tasks ->
                controlCenter.syncSaveVideoTasks(tasks)
            }
        }
        refreshTelemetry()

        setContent { JarvisTheme { JarvisAppRoot() } }
    }

    private fun refreshTelemetry() {
        lifecycleScope.launch(Dispatchers.IO) {
            val signals = runCatching { signalsSource.snapshot() }.getOrNull() ?: return@launch
            val asrReady = runCatching { runtime.isRussianAsrReady() }.getOrDefault(false)
            controlCenter.updateDeviceSignals(signals, localAsrReady = asrReady)
        }
    }

    private fun requestAndListen() {
        if (listeningJob != null || modelCheckJob != null) return
        modelCheckJob = lifecycleScope.launch {
            try {
                orbModeSink?.invoke(JarvisOrbMode.THINKING)
                statusSink?.invoke("Проверяю локальную модель…")
                val ready = withContext(Dispatchers.IO) { runtime.isRussianAsrReady() }
                if (ready) requestMicrophoneAndListen(allowModelDownload = false)
                else {
                    orbModeSink?.invoke(JarvisOrbMode.IDLE)
                    modelConsentSink?.invoke()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                orbModeSink?.invoke(JarvisOrbMode.ERROR)
                statusSink?.invoke("Ошибка проверки модели: ${error.message ?: error.javaClass.simpleName}")
            } finally {
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
                orbModeSink?.invoke(JarvisOrbMode.LEARNING)
                statusSink?.invoke("Подготавливаю локальный русский ASR…")
                withContext(Dispatchers.IO) {
                    runtime.prepare(allowModelDownload) { status -> runOnUiThread { statusSink?.invoke(status) } }
                }
                refreshTelemetry()
                statusSink?.invoke("Микрофон активен — говорите")
                val text = withContext(Dispatchers.IO) {
                    runtime.listenOnce { status -> runOnUiThread { statusSink?.invoke(status) } }
                }
                if (text.isNotBlank()) statusSink?.invoke("Распознано: $text")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val msg = error.message ?: error.javaClass.simpleName
                orbModeSink?.invoke(JarvisOrbMode.ERROR)
                statusSink?.invoke("Ошибка runtime: $msg")
                controlCenter.logEvent("RUNTIME", "Ошибка голосового ядра", msg, JarvisEventLevel.ERROR)
            } finally {
                audioRmsSink?.invoke(0f)
                listeningJob = null
            }
        }
    }

    private fun stopListening() {
        pendingListen = false
        modelCheckJob?.cancel()
        listeningJob?.cancel()
        audioRmsSink?.invoke(0f)
        orbModeSink?.invoke(JarvisOrbMode.IDLE)
        statusSink?.invoke("Прослушивание остановлено")
    }

    private fun openSettings() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    override fun onDestroy() {
        statusSink = null
        audioRmsSink = null
        orbModeSink = null
        tabSink = null
        modelCheckJob?.cancel()
        modelCheckJob = null
        listeningJob?.cancel()
        listeningJob = null
        saveVideoManager.close()
        runtime.close()
        super.onDestroy()
    }

    @Composable
    private fun JarvisAppRoot() {
        var activeTab by remember { mutableStateOf(JarvisTab.CORE) }
        var orbMode by remember { mutableStateOf(JarvisOrbMode.IDLE) }
        var audioRms by remember { mutableStateOf(0f) }
        var listening by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var speaking by remember { mutableStateOf(false) }
        var showModelConsent by remember { mutableStateOf(false) }
        var transcript by remember { mutableStateOf("Готов к работе. Коснитесь ядра и скажите команду.") }
        var status by remember { mutableStateOf("ОЖИДАЮ КОМАНДУ") }

        val saveVideoTasks by saveVideoManager.tasks.collectAsState()
        val controlSnapshot by controlCenter.snapshot.collectAsState()

        DisposableEffect(Unit) {
            modelConsentSink = { showModelConsent = true }
            audioRmsSink = { rms -> audioRms = rms }
            orbModeSink = { mode -> orbMode = mode }
            tabSink = { tab -> activeTab = tab }

            runtime.onAudioLevelChanged = { rms -> runOnUiThread { audioRmsSink?.invoke(rms) } }
            runtime.onOrbModeChanged = { mode -> runOnUiThread { orbModeSink?.invoke(mode) } }
            runtime.onNavigateTab = { target ->
                runOnUiThread {
                    when (target) {
                        "SAVEVIDEO" -> tabSink?.invoke(JarvisTab.SAVEVIDEO)
                        "CONTROL" -> tabSink?.invoke(JarvisTab.CONTROL)
                        else -> tabSink?.invoke(JarvisTab.CORE)
                    }
                }
            }
            runtime.onCommandCompleted = { spoken, response ->
                controlCenter.recordVoiceCommand(spoken, response)
            }
            runtime.setSpeechStateListener { active -> runOnUiThread { speaking = active } }
            statusSink = { value ->
                status = value.uppercase()
                listening = value.contains("Микрофон активен") || value.contains("говорите")
                busy = !(
                    value == "Ожидаю команду" ||
                        value == "Прослушивание остановлено" ||
                        value == "Речь не распознана" ||
                        value.startsWith("Ошибка") ||
                        value.startsWith("Нет разрешения") ||
                        value.startsWith("Распознано:") ||
                        value.startsWith("Открываю") ||
                        value.startsWith("Вы сказали:")
                    )
                if (value.startsWith("Распознано:") || value.startsWith("Открываю") || value.startsWith("Вы сказали:")) {
                    transcript = value
                }
            }
            onDispose {
                statusSink = null
                modelConsentSink = null
                audioRmsSink = null
                orbModeSink = null
                tabSink = null
                runtime.onAudioLevelChanged = null
                runtime.onOrbModeChanged = null
                runtime.onNavigateTab = null
                runtime.onCommandCompleted = null
                runtime.setSpeechStateListener(null)
            }
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
                        orbMode = JarvisOrbMode.IDLE
                        status = "ЗАГРУЗКА МОДЕЛИ ОТМЕНЕНА — НИЧЕГО НЕ СКАЧАНО"
                    }) { Text("НЕ СЕЙЧАС") }
                },
            )
        }

        Scaffold(
            containerColor = Background,
            bottomBar = {
                NavigationBar(containerColor = NavBarBg) {
                    NavigationBarItem(
                        selected = activeTab == JarvisTab.CORE,
                        onClick = { activeTab = JarvisTab.CORE },
                        icon = { Icon(Icons.Default.Grain, contentDescription = JarvisTab.CORE.title) },
                        label = { Text(JarvisTab.CORE.title) },
                    )
                    NavigationBarItem(
                        selected = activeTab == JarvisTab.SAVEVIDEO,
                        onClick = { activeTab = JarvisTab.SAVEVIDEO },
                        icon = { Icon(Icons.Default.Download, contentDescription = JarvisTab.SAVEVIDEO.title) },
                        label = {
                            val badge = if (controlSnapshot.videosInQueue > 0) " (${controlSnapshot.videosInQueue})" else ""
                            Text("${JarvisTab.SAVEVIDEO.title}$badge")
                        },
                    )
                    NavigationBarItem(
                        selected = activeTab == JarvisTab.CONTROL,
                        onClick = {
                            activeTab = JarvisTab.CONTROL
                            refreshTelemetry()
                        },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = JarvisTab.CONTROL.title) },
                        label = { Text(JarvisTab.CONTROL.title) },
                    )
                }
            },
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = Background,
            ) {
                when (activeTab) {
                    JarvisTab.CORE -> JarvisCoreHomeTab(
                        orbMode = orbMode,
                        audioRms = audioRms,
                        listening = listening,
                        speaking = speaking,
                        busy = busy,
                        status = status,
                        transcript = transcript,
                        activeTasksCount = controlSnapshot.activeTasks,
                        onOrbClick = { if (busy || listening) stopListening() else requestAndListen() },
                        onOpenSettings = { openSettings() },
                    )

                    JarvisTab.SAVEVIDEO -> SaveVideoScreen(
                        tasks = saveVideoTasks,
                        onInspect = { url -> saveVideoManager.inspectMetadata(url) },
                        onEnqueue = { url, quality -> saveVideoManager.enqueue(url, quality) },
                        onCancel = { id -> saveVideoManager.cancelTask(id) },
                        onRetry = { id -> saveVideoManager.startTask(id) },
                        onDelete = { id -> saveVideoManager.deleteTask(id) },
                    )

                    JarvisTab.CONTROL -> ControlCenterScreen(
                        snapshot = controlSnapshot,
                        onRefreshTelemetry = { refreshTelemetry() },
                        onClearEvents = { controlCenter.clearEvents() },
                    )
                }
            }
        }
    }

    @Composable
    private fun JarvisCoreHomeTab(
        orbMode: JarvisOrbMode,
        audioRms: Float,
        listening: Boolean,
        speaking: Boolean,
        busy: Boolean,
        status: String,
        transcript: String,
        activeTasksCount: Int,
        onOrbClick: () -> Unit,
        onOpenSettings: () -> Unit,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxHeight < 640.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = if (compact) 8.dp else 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (activeTasksCount > 0) "АКТИВНЫХ ЗАДАЧ: $activeTasksCount" else "JARVIS CORE",
                        color = if (activeTasksCount > 0) Color(0xFF34D399) else Muted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = Muted)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(
                            onClickLabel = if (busy || listening) "Остановить Джарвиса" else "Активировать Джарвиса",
                            onClick = onOrbClick,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    JarvisInteractiveOrb(
                        mode = when {
                            listening -> JarvisOrbMode.LISTENING
                            speaking -> JarvisOrbMode.SPEAKING
                            orbMode != JarvisOrbMode.IDLE -> orbMode
                            busy -> JarvisOrbMode.THINKING
                            else -> JarvisOrbMode.IDLE
                        },
                        audioRms = audioRms,
                        orbSize = if (compact) 210.dp else 260.dp,
                    )
                }

                Text(
                    text = when {
                        listening -> "Я СЛУШАЮ"
                        speaking -> "Я ОТВЕЧАЮ"
                        orbMode == JarvisOrbMode.ERROR -> "ОШИБКА ЯДРА"
                        orbMode == JarvisOrbMode.EXECUTING -> "ВЫПОЛНЯЮ"
                        busy -> "Я ДУМАЮ"
                        else -> "Я РЯДОМ"
                    },
                    color = if (listening) Cyan else Color(0xFFE5F3FF),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = status,
                    color = if (status.startsWith("ОШИБКА")) Color(0xFFFF9C9C) else Cyan,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
                Text(
                    text = transcript,
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp),
                )
                Text(
                    text = if (busy || listening) "КОСНИТЕСЬ ЯДРА, ЧТОБЫ ОСТАНОВИТЬ" else "КОСНИТЕСЬ ЯДРА, ЧТОБЫ ГОВОРИТЬ",
                    color = Color(0xFF71879B),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
    }
}
