package com.jarvis.breeno.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.jarvis.breeno.assistant.JarvisApp
import com.jarvis.breeno.assistant.control.JarvisControlCenterStore
import com.jarvis.breeno.assistant.control.JarvisEventLevel
import com.jarvis.breeno.assistant.core.AndroidRuntimeSignalsSource
import com.jarvis.breeno.assistant.core.ExternalAiMode
import com.jarvis.breeno.assistant.core.JarvisOrbMode
import com.jarvis.breeno.assistant.media.GeneratedMediaItem
import com.jarvis.breeno.assistant.media.GeneratedMediaType
import com.jarvis.breeno.assistant.media.JarvisMediaGenerator
import com.jarvis.breeno.assistant.media.MediaGenConfigStore
import com.jarvis.breeno.assistant.memory.DialogueMessage
import com.jarvis.breeno.assistant.savevideo.SaveVideoManager
import com.jarvis.breeno.assistant.ui.theme.JarvisTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Background = Color(0xFF04070F)
private val NavBarBg = Color(0xFF080E1A)
private val PanelCard = Color(0xCC0C1628)
private val Cyan = Color(0xFF00E5FF)
private val Gold = Color(0xFFFFB300)
private val Muted = Color(0xFF9AAABD)

private enum class JarvisTab(val title: String) {
    CORE("Ядро"),
    MODELS("Модели"),
    STUDIO("Студия"),
    SAVEVIDEO("Видео"),
    CONTROL("Центр"),
}

class MainActivity : ComponentActivity() {
    private lateinit var jarvisApp: JarvisApp
    private lateinit var runtime: JarvisRuntimeController
    private lateinit var signalsSource: AndroidRuntimeSignalsSource
    private lateinit var controlCenter: JarvisControlCenterStore
    private lateinit var saveVideoManager: SaveVideoManager
    private lateinit var mediaGenerator: JarvisMediaGenerator

    private var pendingListen = false
    private var pendingModelDownloadConsent = false
    private var modelConsentSink: (() -> Unit)? = null
    private var statusSink: ((String) -> Unit)? = null
    private var busySink: ((Boolean) -> Unit)? = null
    private var replySink: ((String?) -> Unit)? = null
    private var dialogueSink: ((List<DialogueMessage>) -> Unit)? = null
    private var audioRmsSink: ((Float) -> Unit)? = null
    private var orbModeSink: ((JarvisOrbMode) -> Unit)? = null
    private var tabSink: ((JarvisTab) -> Unit)? = null
    private var stackStatusSink: ((AiStackStatus) -> Unit)? = null
    private var pendingStudioSink: ((Pair<GeneratedMediaType, String>) -> Unit)? = null
    private var autoResumeCheck: (() -> Boolean)? = null
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

    private val importGgufLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val displayName = resolveDocumentDisplayName(uri)
        jarvisApp.downloads.start("Импорт $displayName") { update ->
            runtime.importCustomGguf(uri, displayName) { pct ->
                update("Импорт $displayName: $pct%", pct)
            }
        }
    }

    private fun resolveDocumentDisplayName(uri: Uri): String {
        val queried = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull()
        return queried?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "custom_model.gguf"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        jarvisApp = application as JarvisApp
        runtime = JarvisRuntimeController(this)
        signalsSource = AndroidRuntimeSignalsSource(this)
        controlCenter = JarvisControlCenterStore()

        jarvisApp.onDownloadStateUpdated = { refreshTelemetry() }

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

        mediaGenerator = JarvisMediaGenerator(
            studioDir = File(filesDir, "studio"),
            historyFile = File(filesDir, "studio_history.json"),
            configStore = MediaGenConfigStore(this),
            onEvent = { title, detail, isError ->
                controlCenter.logEvent(
                    category = "СТУДИЯ",
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
        refreshDialogueFeed()

        setContent { JarvisTheme { JarvisAppRoot() } }
    }

    override fun onResume() {
        super.onResume()
        refreshTelemetry()
        refreshDialogueFeed()
    }

    private fun refreshTelemetry() {
        lifecycleScope.launch(Dispatchers.IO) {
            val signals = runCatching { signalsSource.snapshot() }.getOrNull() ?: return@launch
            val asrReady = runCatching { runtime.isRussianAsrReady() }.getOrDefault(false)
            controlCenter.updateDeviceSignals(signals, localAsrReady = asrReady)
            val stack = runtime.currentStackStatus()
            runOnUiThread { stackStatusSink?.invoke(stack) }
        }
    }

    private fun refreshDialogueFeed() {
        lifecycleScope.launch(Dispatchers.IO) {
            val recent = runCatching { runtime.recentDialogue(10) }.getOrDefault(emptyList())
            runOnUiThread { dialogueSink?.invoke(recent) }
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
                    runtime.listenOnce(
                        onProgress = { status -> runOnUiThread { statusSink?.invoke(status) } },
                        onTokenStream = { partial -> runOnUiThread { replySink?.invoke(partial) } },
                    )
                }
                if (text.isNotBlank()) {
                    refreshDialogueFeed()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val msg = error.message ?: error.javaClass.simpleName
                orbModeSink?.invoke(JarvisOrbMode.ERROR)
                statusSink?.invoke("Ошибка runtime: $msg")
                controlCenter.logEvent("RUNTIME", "Ошибка голосового ядра", msg, JarvisEventLevel.ERROR)
            } finally {
                runOnUiThread {
                    audioRmsSink?.invoke(0f)
                    busySink?.invoke(false)
                    replySink?.invoke(null)
                }
                listeningJob = null
            }
        }
    }

    private fun submitTextQuery(query: String) {
        val clean = query.trim()
        if (clean.isEmpty() || listeningJob != null) return
        listeningJob = lifecycleScope.launch {
            try {
                orbModeSink?.invoke(JarvisOrbMode.THINKING)
                busySink?.invoke(true)
                replySink?.invoke("Думаю над: «$clean»…")
                runtime.submitText(
                    rawText = clean,
                    onProgress = { status -> runOnUiThread { statusSink?.invoke(status) } },
                    onTokenStream = { partial -> runOnUiThread { replySink?.invoke(partial) } },
                )
                refreshDialogueFeed()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val msg = error.message ?: error.javaClass.simpleName
                orbModeSink?.invoke(JarvisOrbMode.ERROR)
                statusSink?.invoke("Ошибка: $msg")
            } finally {
                runOnUiThread {
                    busySink?.invoke(false)
                    replySink?.invoke(null)
                }
                listeningJob = null
            }
        }
    }

    private fun stopListening() {
        pendingListen = false
        modelCheckJob?.cancel()
        listeningJob?.cancel()
        runtime.cancelActiveWork()
        audioRmsSink?.invoke(0f)
        busySink?.invoke(false)
        replySink?.invoke(null)
        orbModeSink?.invoke(JarvisOrbMode.IDLE)
        statusSink?.invoke("Работа остановлена")
    }

    private fun openGeneratedMedia(item: GeneratedMediaItem) {
        val file = File(item.filePath)
        if (!file.isFile) return
        runCatching {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val mime = if (item.type == GeneratedMediaType.VIDEO) "video/mp4" else "image/jpeg"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Открыть файл"))
        }.onFailure { err ->
            statusSink?.invoke("Ошибка открытия: ${err.message ?: err.javaClass.simpleName}")
        }
    }

    private fun openSettings() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    override fun onDestroy() {
        jarvisApp.onDownloadStateUpdated = null
        statusSink = null
        busySink = null
        replySink = null
        dialogueSink = null
        modelConsentSink = null
        audioRmsSink = null
        orbModeSink = null
        tabSink = null
        stackStatusSink = null
        pendingStudioSink = null
        autoResumeCheck = null
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
        var muted by remember { mutableStateOf(runtime.isMuted()) }
        var continuousDialogue by remember { mutableStateOf(false) }
        var showModelConsent by remember { mutableStateOf(false) }
        var streamingReply by remember { mutableStateOf<String?>(null) }
        var dialogueFeed by remember { mutableStateOf(emptyList<DialogueMessage>()) }
        var status by remember { mutableStateOf("ОЖИДАЮ КОМАНДУ") }
        var stackStatus by remember { mutableStateOf(runtime.currentStackStatus()) }
        var pendingStudioRequest by remember { mutableStateOf<Pair<GeneratedMediaType, String>?>(null) }

        val saveVideoTasks by saveVideoManager.tasks.collectAsState()
        val studioItems by mediaGenerator.items.collectAsState()
        val controlSnapshot by controlCenter.snapshot.collectAsState()
        val downloadState by jarvisApp.downloads.state.collectAsState()

        DisposableEffect(Unit) {
            modelConsentSink = { showModelConsent = true }
            audioRmsSink = { rms -> audioRms = rms }
            orbModeSink = { mode -> orbMode = mode }
            tabSink = { tab -> activeTab = tab }
            stackStatusSink = { next -> stackStatus = next }
            busySink = { isBusy -> busy = isBusy }
            replySink = { text -> streamingReply = text }
            dialogueSink = { list -> dialogueFeed = list }
            pendingStudioSink = { req -> pendingStudioRequest = req }
            autoResumeCheck = { continuousDialogue && activeTab == JarvisTab.CORE }

            runtime.onAudioLevelChanged = { rms -> runOnUiThread { audioRmsSink?.invoke(rms) } }
            runtime.onOrbModeChanged = { mode -> runOnUiThread { orbModeSink?.invoke(mode) } }
            runtime.onStackStatusChanged = { refreshTelemetry() }
            runtime.onSpeakerError = { errMsg ->
                controlCenter.logEvent("ГОЛОС", "Ошибка озвучки", errMsg, JarvisEventLevel.ERROR)
                runOnUiThread { statusSink?.invoke(errMsg) }
            }
            runtime.onSpeakingFinished = {
                if (autoResumeCheck?.invoke() == true && runtime.isRussianAsrReady()) {
                    runOnUiThread { requestAndListen() }
                }
            }
            runtime.onRequestMediaGeneration = { type, prompt ->
                runOnUiThread {
                    val mediaType = if (type == "VIDEO") GeneratedMediaType.VIDEO else GeneratedMediaType.IMAGE
                    pendingStudioSink?.invoke(mediaType to prompt)
                    tabSink?.invoke(JarvisTab.STUDIO)
                }
            }
            runtime.onNavigateTab = { target ->
                runOnUiThread {
                    when (target) {
                        "SAVEVIDEO" -> tabSink?.invoke(JarvisTab.SAVEVIDEO)
                        "CONTROL" -> tabSink?.invoke(JarvisTab.CONTROL)
                        "MODELS" -> tabSink?.invoke(JarvisTab.MODELS)
                        "STUDIO" -> tabSink?.invoke(JarvisTab.STUDIO)
                        else -> tabSink?.invoke(JarvisTab.CORE)
                    }
                }
            }
            runtime.onCommandCompleted = { spoken, response ->
                controlCenter.recordVoiceCommand(spoken, response)
                refreshDialogueFeed()
            }
            runtime.setSpeechStateListener { active -> runOnUiThread { speaking = active } }
            statusSink = { value ->
                status = value.uppercase()
                listening = value.contains("Микрофон активен") || value.contains("говорите")
                busy = !(
                    value == "Ожидаю команду" ||
                        value == "Прослушивание остановлено" ||
                        value == "Работа остановлена" ||
                        value == "Речь не распознана" ||
                        value.startsWith("Ошибка") ||
                        value.startsWith("Русский голос") ||
                        value.startsWith("Нет разрешения") ||
                        value.startsWith("Нужна модель") ||
                        value.startsWith("Ответ готов") ||
                        value.startsWith("Распознано:") ||
                        value.startsWith("Открываю") ||
                        value.startsWith("Запомнил:") ||
                        value.startsWith("Сейчас ") ||
                        value.startsWith("Сегодня ") ||
                        value.startsWith("Вот что") ||
                        value.startsWith("Память ") ||
                        value.startsWith("Свободно ")
                    )
            }
            onDispose {
                statusSink = null
                busySink = null
                replySink = null
                dialogueSink = null
                modelConsentSink = null
                audioRmsSink = null
                orbModeSink = null
                tabSink = null
                stackStatusSink = null
                pendingStudioSink = null
                autoResumeCheck = null
                runtime.onAudioLevelChanged = null
                runtime.onOrbModeChanged = null
                runtime.onStackStatusChanged = null
                runtime.onSpeakerError = null
                runtime.onSpeakingFinished = null
                runtime.onRequestMediaGeneration = null
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
                        label = { Text(JarvisTab.CORE.title, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                    )
                    NavigationBarItem(
                        selected = activeTab == JarvisTab.MODELS,
                        onClick = {
                            activeTab = JarvisTab.MODELS
                            refreshTelemetry()
                        },
                        icon = { Icon(Icons.Default.Memory, contentDescription = JarvisTab.MODELS.title) },
                        label = { Text(JarvisTab.MODELS.title, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                    )
                    NavigationBarItem(
                        selected = activeTab == JarvisTab.STUDIO,
                        onClick = { activeTab = JarvisTab.STUDIO },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = JarvisTab.STUDIO.title) },
                        label = { Text(JarvisTab.STUDIO.title, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                    )
                    NavigationBarItem(
                        selected = activeTab == JarvisTab.SAVEVIDEO,
                        onClick = { activeTab = JarvisTab.SAVEVIDEO },
                        icon = { Icon(Icons.Default.Download, contentDescription = JarvisTab.SAVEVIDEO.title) },
                        label = {
                            val badge = if (controlSnapshot.videosInQueue > 0) "(${controlSnapshot.videosInQueue})" else ""
                            Text("${JarvisTab.SAVEVIDEO.title}$badge", fontSize = 11.sp, maxLines = 1, softWrap = false)
                        },
                    )
                    NavigationBarItem(
                        selected = activeTab == JarvisTab.CONTROL,
                        onClick = {
                            activeTab = JarvisTab.CONTROL
                            refreshTelemetry()
                        },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = JarvisTab.CONTROL.title) },
                        label = { Text(JarvisTab.CONTROL.title, fontSize = 11.sp, maxLines = 1, softWrap = false) },
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
                        muted = muted,
                        continuousDialogue = continuousDialogue,
                        status = status,
                        streamingReply = streamingReply,
                        dialogueFeed = dialogueFeed,
                        stackStatus = stackStatus,
                        downloadState = downloadState,
                        activeTasksCount = controlSnapshot.activeTasks,
                        onOrbClick = {
                            if (listeningJob != null || modelCheckJob != null || busy || listening || speaking) {
                                stopListening()
                            } else {
                                requestAndListen()
                            }
                        },
                        onSendText = { query -> submitTextQuery(query) },
                        onStopWork = { stopListening() },
                        onToggleMute = {
                            muted = !muted
                            runtime.setMuted(muted)
                        },
                        onToggleContinuous = { continuousDialogue = !continuousDialogue },
                        onOpenModels = {
                            activeTab = JarvisTab.MODELS
                            refreshTelemetry()
                        },
                        onOpenSettings = { openSettings() },
                    )

                    JarvisTab.MODELS -> AiModelsScreen(
                        status = stackStatus,
                        downloadState = downloadState,
                        llmCatalog = stackStatus.allLlmPacks,
                        voiceCatalog = runtime.ttsModels.catalog(),
                        onDownloadStarterBundle = {
                            jarvisApp.downloads.start("Базовый офлайн-набор") { update ->
                                runtime.downloadOfflineStarterBundle { stage, pct -> update(stage, pct) }
                            }
                        },
                        onDownloadAsr = {
                            jarvisApp.downloads.start("Русский слух (ASR)") { update ->
                                runtime.downloadAsr { pct -> update(null, pct) }
                            }
                        },
                        onDownloadVad = {
                            jarvisApp.downloads.start("Silero VAD") { update ->
                                runtime.downloadVad { pct -> update(null, pct) }
                            }
                        },
                        onDownloadVoice = { id ->
                            val title = runtime.ttsModels.findVoice(id)?.title ?: id
                            jarvisApp.downloads.start(title) { update ->
                                runtime.downloadTtsVoice(id) { pct -> update(null, pct) }
                            }
                        },
                        onCancelDownload = { jarvisApp.downloads.cancel() },
                        onSelectVoice = { id -> runtime.selectTtsVoice(id) },
                        onSetSpeechSpeed = { speed -> runtime.setSpeechSpeed(speed) },
                        onDeleteVoice = { id -> runtime.deleteTtsVoice(id) },
                        onTestVoice = { runtime.testRussianVoice() },
                        onDownloadLlm = { id ->
                            val title = runtime.llmModels.findPack(id)?.title ?: id
                            jarvisApp.downloads.start(title) { update ->
                                runtime.downloadLlmPack(id) { pct, dl, total ->
                                    val dlMb = dl / (1024 * 1024)
                                    val totalMb = (total / (1024 * 1024)).coerceAtLeast(1)
                                    update("$title: $dlMb/$totalMb МБ ($pct%)", pct)
                                }
                            }
                        },
                        onSelectLlm = { id -> runtime.selectLlmPack(id) },
                        onDeleteLlm = { id -> runtime.deleteLlmPack(id) },
                        onImportCustomGguf = { importGgufLauncher.launch(arrayOf("*/*")) },
                        onSelectPersona = { persona -> runtime.selectPersona(persona) },
                        onSaveExternalAi = { cfg -> runtime.saveExternalAiConfig(cfg) },
                    )

                    JarvisTab.STUDIO -> MediaStudioScreen(
                        items = studioItems,
                        initialConfig = mediaGenerator.currentConfig(),
                        pendingPromptRequest = pendingStudioRequest,
                        onClearPendingRequest = { pendingStudioRequest = null },
                        onGenerateImage = { p, s, w, h, cb -> mediaGenerator.generateImage(p, s, w, h, cb) },
                        onGenerateVideo = { p, s, w, h, mp, cb -> mediaGenerator.generateVideo(p, s, w, h, mp, cb) },
                        onOpenItem = { item -> openGeneratedMedia(item) },
                        onDeleteItem = { id -> mediaGenerator.deleteItem(id) },
                        onSaveConfig = { cfg -> mediaGenerator.saveConfig(cfg) },
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
        muted: Boolean,
        continuousDialogue: Boolean,
        status: String,
        streamingReply: String?,
        dialogueFeed: List<DialogueMessage>,
        stackStatus: AiStackStatus,
        downloadState: com.jarvis.breeno.assistant.DownloadTaskState,
        activeTasksCount: Int,
        onOrbClick: () -> Unit,
        onSendText: (String) -> Unit,
        onStopWork: () -> Unit,
        onToggleMute: () -> Unit,
        onToggleContinuous: () -> Unit,
        onOpenModels: () -> Unit,
        onOpenSettings: () -> Unit,
    ) {
        var textQuery by remember { mutableStateOf("") }
        val dialogueScroll = rememberScrollState()

        LaunchedEffect(dialogueFeed.size, streamingReply) {
            dialogueScroll.animateScrollTo(dialogueScroll.maxValue)
        }

        val activeLlm = stackStatus.activeLlmId
        val brainBadge = when {
            activeLlm?.startsWith("dolphin") == true -> "DOLPHIN ОФЛАЙН"
            activeLlm?.startsWith("hermes") == true -> "HERMES ОФЛАЙН"
            activeLlm?.startsWith("custom_") == true -> "СВОЙ GGUF"
            activeLlm != null -> "QWEN ОФЛАЙН"
            stackStatus.externalAiConfig.enabled -> "ВНЕШНИЙ ИИ"
            else -> "НАСТРОИТЬ ИИ"
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxHeight < 660.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = if (compact) 4.dp else 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (activeTasksCount > 0) "ЗАДАЧ: $activeTasksCount • $brainBadge" else brainBadge,
                        color = if (stackStatus.activeLlmId != null || stackStatus.externalAiConfig.enabled) {
                            Color(0xFF34D399)
                        } else {
                            Gold
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onOpenModels),
                    )
                    Row {
                        IconButton(onClick = onToggleContinuous) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = if (continuousDialogue) "Выключить живой диалог" else "Включить живой диалог",
                                tint = if (continuousDialogue) Gold else Muted,
                            )
                        }
                        IconButton(onClick = onToggleMute) {
                            Icon(
                                imageVector = if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = if (muted) "Включить звук" else "Выключить звук",
                                tint = if (muted) Color(0xFFFF9C9C) else Muted,
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = Muted)
                        }
                    }
                }

                if (downloadState.active) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenModels)
                            .padding(bottom = 2.dp),
                    ) {
                        Text(
                            text = "${downloadState.label} (${downloadState.percent}%)",
                            color = Cyan,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        LinearProgressIndicator(
                            progress = { (downloadState.percent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
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
                        orbSize = if (compact) 152.dp else 204.dp,
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
                    color = if (listening) Cyan else if (speaking) Gold else Color(0xFFE5F3FF),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = status,
                    color = if (status.startsWith("ОШИБКА") || status.startsWith("РУССКИЙ ГОЛОС")) Color(0xFFFF9C9C) else Cyan,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = PanelCard),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 4.dp)
                        .heightIn(min = 44.dp, max = if (compact) 74.dp else 118.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(dialogueScroll)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (dialogueFeed.isEmpty() && streamingReply.isNullOrBlank()) {
                            Text(
                                text = "J.A.R.V.I.S. готов. Коснитесь сферы или напишите запрос ниже.",
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            dialogueFeed.takeLast(4).forEach { msg ->
                                val isUser = msg.role == "user"
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(
                                            SpanStyle(
                                                color = if (isUser) Gold else Cyan,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        ) {
                                            append(if (isUser) "Вы: " else "Джарвис: ")
                                        }
                                        withStyle(SpanStyle(color = Color(0xFFDCE7F2))) {
                                            append(msg.content)
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (!streamingReply.isNullOrBlank()) {
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(color = Cyan, fontWeight = FontWeight.SemiBold)) {
                                            append("Джарвис: ")
                                        }
                                        withStyle(SpanStyle(color = Color.White)) {
                                            append(streamingReply)
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                Text(
                    text = if (busy || listening) "КОСНИТЕСЬ ЯДРА, ЧТОБЫ ОСТАНОВИТЬ" else "КОСНИТЕСЬ ЯДРА, ЧТОБЫ ГОВОРИТЬ",
                    color = Color(0xFF71879B),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 4.dp),
                )

                OutlinedTextField(
                    value = textQuery,
                    onValueChange = { textQuery = it },
                    placeholder = { Text("Спросите Джарвиса…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        val q = textQuery.trim()
                        if (q.isNotEmpty()) {
                            textQuery = ""
                            onSendText(q)
                        }
                    }),
                    trailingIcon = {
                        if (busy || listening) {
                            IconButton(onClick = onStopWork) {
                                Icon(Icons.Default.Close, contentDescription = "Остановить", tint = Color(0xFFFF9C9C))
                            }
                        } else {
                            IconButton(onClick = {
                                val q = textQuery.trim()
                                if (q.isNotEmpty()) {
                                    textQuery = ""
                                    onSendText(q)
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = Cyan)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                )
            }
        }
    }
}
