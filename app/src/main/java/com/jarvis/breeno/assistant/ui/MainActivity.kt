package com.jarvis.breeno.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.jarvis.breeno.assistant.JarvisApp
import com.jarvis.breeno.assistant.control.JarvisControlCenterStore
import com.jarvis.breeno.assistant.control.JarvisEventLevel
import com.jarvis.breeno.assistant.core.AndroidRuntimeSignalsSource
import com.jarvis.breeno.assistant.core.JarvisOrbMode
import com.jarvis.breeno.assistant.media.GeneratedMediaItem
import com.jarvis.breeno.assistant.media.GeneratedMediaType
import com.jarvis.breeno.assistant.media.JarvisMediaGenerator
import com.jarvis.breeno.assistant.media.LocalStudioFileSlot
import com.jarvis.breeno.assistant.media.MediaGenBackend
import com.jarvis.breeno.assistant.media.MediaGenConfigStore
import com.jarvis.breeno.assistant.media.StableDiffusionNativeBridge
import com.jarvis.breeno.assistant.savevideo.SaveVideoManager
import com.jarvis.breeno.assistant.ui.theme.Jarvis3DStageBackground
import com.jarvis.breeno.assistant.ui.theme.JarvisTheme
import com.jarvis.breeno.assistant.ui.theme.LocalJarvisPalette
import com.jarvis.breeno.assistant.ui.theme.jarvisScreenBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    private var audioRmsSink: ((Float) -> Unit)? = null
    private var orbModeSink: ((JarvisOrbMode) -> Unit)? = null
    private var sectionSink: ((JarvisSection) -> Unit)? = null
    private var stackStatusSink: ((AiStackStatus) -> Unit)? = null
    private var pendingStudioSink: ((Pair<GeneratedMediaType, String>) -> Unit)? = null
    private var autoResumeCheck: (() -> Boolean)? = null
    private var listeningJob: Job? = null
    private var modelCheckJob: Job? = null
    private var pendingStudioSlot: LocalStudioFileSlot = LocalStudioFileSlot.MODEL

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
        val displayName = resolveDocumentDisplayName(uri, fallback = "custom_model.gguf")
        jarvisApp.downloads.start("Импорт $displayName") { update ->
            runtime.importCustomGguf(uri, displayName) { pct ->
                update("Импорт $displayName: $pct%", pct)
            }
        }
    }

    private val importStudioFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val slot = pendingStudioSlot
        val displayName = resolveDocumentDisplayName(uri, fallback = "${slot.folder}_file.safetensors")
        jarvisApp.downloads.start("Студия: $displayName") { update ->
            val saved = StableDiffusionNativeBridge.importLocalStudioFile(
                context = this@MainActivity,
                uri = uri,
                displayName = displayName,
                slot = slot,
            ) { pct ->
                update("Копирую $displayName: $pct%", pct)
            }
            val cur = mediaGenerator.currentConfig()
            val updatedPaths = when (slot) {
                LocalStudioFileSlot.MODEL -> cur.copy(
                    backend = MediaGenBackend.ON_DEVICE_SD_CPP,
                    localSdModelPath = saved.absolutePath,
                )
                LocalStudioFileSlot.VAE -> cur.copy(localSdVaePath = saved.absolutePath)
                LocalStudioFileSlot.CLIP1 -> cur.copy(localSdClip1Path = saved.absolutePath)
                LocalStudioFileSlot.CLIP2 -> cur.copy(localSdClip2Path = saved.absolutePath)
            }
            val finalCfg = if (updatedPaths.localSdModelPath.isNotBlank()) {
                val spec = StableDiffusionNativeBridge.resolveLocalModelSpec(
                    modelPath = updatedPaths.localSdModelPath,
                    vaePath = updatedPaths.localSdVaePath,
                    clip1Path = updatedPaths.localSdClip1Path,
                    clip2Path = updatedPaths.localSdClip2Path,
                )
                updatedPaths.copy(
                    steps = spec.recommendedSteps,
                    cfgScale = spec.recommendedCfg,
                    negativePrompt = spec.recommendedNegative,
                    uncensoredMode = true,
                )
            } else {
                updatedPaths
            }
            mediaGenerator.saveConfig(finalCfg)
            controlCenter.logEvent("СТУДИЯ", "Добавлен файл (${slot.labelRu})", saved.name, JarvisEventLevel.INFO)
        }
    }

    private val importRagDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val displayName = resolveDocumentDisplayName(uri, fallback = "document.txt")
        val mimeType = contentResolver.getType(uri).orEmpty()
        lifecycleScope.launch {
            statusSink?.invoke("Индексирую документ «$displayName»…")
            runtime.importDocument(uri, displayName, mimeType).fold(
                onSuccess = { doc ->
                    controlCenter.logEvent("ДОКУМЕНТЫ", "Добавлен файл RAG", "${doc.name} (${doc.chunkCount} фрагм.)", JarvisEventLevel.INFO)
                    statusSink?.invoke("Документ «${doc.name}» добавлен в базу знаний")
                },
                onFailure = { err ->
                    val msg = err.message ?: err.javaClass.simpleName
                    controlCenter.logEvent("ДОКУМЕНТЫ", "Ошибка импорта", msg, JarvisEventLevel.ERROR)
                    statusSink?.invoke("Ошибка документа: $msg")
                },
            )
        }
    }

    private fun resolveDocumentDisplayName(uri: Uri, fallback: String): String {
        val queried = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull()
        return queried?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: fallback
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
            beforeHeavyOnDeviceGen = { runtime.unloadLocalLlmForMemory() },
        )

        lifecycleScope.launch {
            saveVideoManager.tasks.collect { tasks ->
                controlCenter.syncSaveVideoTasks(tasks)
            }
        }
        refreshTelemetry()
        refreshDialogueFeed()

        setContent {
            val appearance by runtime.appearance.collectAsState()
            JarvisTheme(appearance = appearance) {
                JarvisAppRoot()
            }
        }
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
            runCatching { runtime.refreshWorkspaceState() }
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
                if (listeningJob == coroutineContext[Job]) {
                    listeningJob = null
                }
            }
        }
    }

    private fun submitTextQuery(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        // Barge-in: if continuous mic or prior turn is active, cancel it cleanly before submitting text.
        val previousJob = listeningJob
        if (previousJob != null) {
            previousJob.cancel()
            runtime.cancelActiveWork()
        }
        listeningJob = lifecycleScope.launch {
            try {
                previousJob?.join()
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
                if (listeningJob == coroutineContext[Job]) {
                    listeningJob = null
                }
            }
        }
    }

    private fun regenerateLastReply() {
        if (listeningJob != null) return
        listeningJob = lifecycleScope.launch {
            try {
                orbModeSink?.invoke(JarvisOrbMode.THINKING)
                busySink?.invoke(true)
                replySink?.invoke("Повторная генерация ответа…")
                runtime.regenerateLastReply(
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

    private fun shareActiveDialogue() {
        val md = runtime.exportActiveDialogueMarkdown()
        if (md.isBlank()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Диалог с Jarvis Ai")
            putExtra(Intent.EXTRA_TEXT, md)
        }
        runCatching {
            startActivity(Intent.createChooser(send, "Поделиться диалогом"))
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

    override fun onDestroy() {
        jarvisApp.onDownloadStateUpdated = null
        statusSink = null
        busySink = null
        replySink = null
        modelConsentSink = null
        audioRmsSink = null
        orbModeSink = null
        sectionSink = null
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
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val uiScope = rememberCoroutineScope()
        var currentSection by remember { mutableStateOf(JarvisSection.HOME) }
        var orbMode by remember { mutableStateOf(JarvisOrbMode.IDLE) }
        var audioRms by remember { mutableStateOf(0f) }
        var listening by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var speaking by remember { mutableStateOf(false) }
        var muted by remember { mutableStateOf(runtime.isMuted()) }
        var continuousDialogue by remember { mutableStateOf(runtime.isContinuousDialogueEnabled()) }
        var showModelConsent by remember { mutableStateOf(false) }
        var streamingReply by remember { mutableStateOf<String?>(null) }
        var status by remember { mutableStateOf("ОЖИДАЮ КОМАНДУ") }
        var stackStatus by remember { mutableStateOf(runtime.currentStackStatus()) }
        var pendingStudioRequest by remember { mutableStateOf<Pair<GeneratedMediaType, String>?>(null) }

        val sessions by runtime.sessions.collectAsState()
        val activeSessionId by runtime.activeSessionId.collectAsState()
        val dialogueFeed by runtime.dialogueMessages.collectAsState()
        val memoryFacts by runtime.memoryFacts.collectAsState()
        val memoryPaused by runtime.memoryPaused.collectAsState()
        val lastDeletedFact by runtime.lastDeletedFact.collectAsState()
        val indexedDocuments by runtime.documents.collectAsState()
        val mcpServers by runtime.mcpServers.collectAsState()
        val generationWarning by runtime.generationWarning.collectAsState()
        val appearance by runtime.appearance.collectAsState()
        val webToolsEnabled by runtime.webToolsEnabled.collectAsState()
        val palette = LocalJarvisPalette.current

        val saveVideoTasks by saveVideoManager.tasks.collectAsState()
        val studioItems by mediaGenerator.items.collectAsState()
        val studioConfig by mediaGenerator.config.collectAsState()
        val controlSnapshot by controlCenter.snapshot.collectAsState()
        val downloadState by jarvisApp.downloads.state.collectAsState()

        fun navigateTo(section: JarvisSection) {
            currentSection = section
            if (section == JarvisSection.MODELS || section == JarvisSection.CONTROL || section == JarvisSection.SETTINGS) {
                refreshTelemetry()
            }
            if (section == JarvisSection.MEMORY || section == JarvisSection.DOCUMENTS || section == JarvisSection.TOOLS_MCP || section == JarvisSection.HOME) {
                refreshDialogueFeed()
            }
        }

        BackHandler(enabled = drawerState.isOpen || currentSection != JarvisSection.HOME) {
            if (drawerState.isOpen) {
                uiScope.launch { drawerState.close() }
            } else if (currentSection == JarvisSection.MODELS) {
                navigateTo(JarvisSection.SETTINGS)
            } else {
                navigateTo(JarvisSection.HOME)
            }
        }

        DisposableEffect(Unit) {
            modelConsentSink = { showModelConsent = true }
            audioRmsSink = { rms -> audioRms = rms }
            orbModeSink = { mode -> orbMode = mode }
            sectionSink = { sec -> navigateTo(sec) }
            stackStatusSink = { next -> stackStatus = next }
            busySink = { isBusy -> busy = isBusy }
            replySink = { text -> streamingReply = text }
            pendingStudioSink = { req -> pendingStudioRequest = req }
            autoResumeCheck = { continuousDialogue && currentSection == JarvisSection.HOME }

            runtime.onAudioLevelChanged = { rms -> runOnUiThread { audioRmsSink?.invoke(rms) } }
            runtime.onOrbModeChanged = { mode -> runOnUiThread { orbModeSink?.invoke(mode) } }
            runtime.onStackStatusChanged = { refreshTelemetry() }
            runtime.onSpeakerError = { errMsg ->
                controlCenter.logEvent("ГОЛОС", "Ошибка озвучки", errMsg, JarvisEventLevel.ERROR)
                runOnUiThread { statusSink?.invoke(errMsg) }
            }
            runtime.onSpeakingFinished = {
                if (autoResumeCheck?.invoke() == true && runtime.isRussianAsrReady()) {
                    lifecycleScope.launch {
                        listeningJob?.join()
                        delay(260L)
                        if (autoResumeCheck?.invoke() == true &&
                            listeningJob == null &&
                            modelCheckJob == null
                        ) {
                            requestAndListen()
                        }
                    }
                }
            }
            runtime.onRequestMediaGeneration = { type, prompt ->
                runOnUiThread {
                    val mediaType = if (type == "VIDEO") GeneratedMediaType.VIDEO else GeneratedMediaType.IMAGE
                    pendingStudioSink?.invoke(mediaType to prompt)
                    sectionSink?.invoke(JarvisSection.STUDIO)
                }
            }
            runtime.onNavigateTab = { target ->
                runOnUiThread {
                    val next = when (target) {
                        "SAVEVIDEO" -> JarvisSection.SAVE_VIDEO
                        "CONTROL" -> JarvisSection.CONTROL
                        "MODELS" -> JarvisSection.MODELS
                        "STUDIO" -> JarvisSection.STUDIO
                        "DOCUMENTS" -> JarvisSection.DOCUMENTS
                        "MEMORY" -> JarvisSection.MEMORY
                        "TOOLS_MCP" -> JarvisSection.TOOLS_MCP
                        "SETTINGS" -> JarvisSection.SETTINGS
                        else -> JarvisSection.HOME
                    }
                    sectionSink?.invoke(next)
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
                        value.startsWith("Документ ") ||
                        value.startsWith("Фонарик ") ||
                        value.startsWith("Да, слушаю") ||
                        value.startsWith("Микрофон отключ") ||
                        value.startsWith("Хорошо, отключаю") ||
                        value.startsWith("Результат:") ||
                        value.startsWith("Память на паузе") ||
                        value.startsWith("Обнаружен ") ||
                        value.startsWith("Не удалось ") ||
                        value.startsWith("Сейчас ") ||
                        value.startsWith("Сегодня ") ||
                        value.startsWith("Вот что") ||
                        value.startsWith("Память ") ||
                        value.startsWith("Свободно ") ||
                        value.startsWith("Найдено в сети")
                    )
            }
            onDispose {
                statusSink = null
                busySink = null
                replySink = null
                modelConsentSink = null
                audioRmsSink = null
                orbModeSink = null
                sectionSink = null
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

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                JarvisNavigationDrawerSheet(
                    currentSection = currentSection,
                    activeSessionId = activeSessionId,
                    sessions = sessions,
                    stackStatus = stackStatus,
                    documentsCount = indexedDocuments.size,
                    factsCount = memoryFacts.size,
                    videosInQueue = controlSnapshot.videosInQueue,
                    onSelectSection = { target ->
                        uiScope.launch { drawerState.close() }
                        navigateTo(target)
                    },
                    onSelectSession = { sessionId ->
                        lifecycleScope.launch(Dispatchers.IO) { runtime.selectSession(sessionId) }
                        uiScope.launch { drawerState.close() }
                        navigateTo(JarvisSection.HOME)
                    },
                    onNewSession = {
                        lifecycleScope.launch(Dispatchers.IO) { runtime.startNewSession() }
                        uiScope.launch { drawerState.close() }
                        navigateTo(JarvisSection.HOME)
                    },
                    onDeleteSession = { sessionId ->
                        lifecycleScope.launch(Dispatchers.IO) { runtime.deleteSession(sessionId) }
                    },
                )
            },
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = palette.bgBottom,
            ) {
                when (currentSection) {
                    JarvisSection.HOME -> JarvisHomeScreen(
                        orbMode = orbMode,
                        audioRms = audioRms,
                        listening = listening,
                        speaking = speaking,
                        busy = busy,
                        muted = muted,
                        continuousDialogue = continuousDialogue,
                        status = status,
                        streamingReply = streamingReply,
                        generationWarning = generationWarning,
                        dialogueFeed = dialogueFeed,
                        stackStatus = stackStatus,
                        downloadState = downloadState,
                        activeTasksCount = controlSnapshot.activeTasks,
                        onOpenDrawer = { uiScope.launch { drawerState.open() } },
                        onOrbClick = {
                            if (listeningJob != null || modelCheckJob != null || busy || listening || speaking) {
                                stopListening()
                            } else {
                                requestAndListen()
                            }
                        },
                        onSendText = { query -> submitTextQuery(query) },
                        onSpeakText = { text -> runtime.speakerSpeakDirect(text) },
                        onStopWork = { stopListening() },
                        onNewDialogue = {
                            lifecycleScope.launch(Dispatchers.IO) { runtime.startNewSession() }
                        },
                        onToggleMute = {
                            muted = !muted
                            runtime.setMuted(muted)
                        },
                        onToggleContinuous = {
                            continuousDialogue = !continuousDialogue
                            runtime.setContinuousDialogueEnabled(continuousDialogue)
                        },
                        onOpenModels = { navigateTo(JarvisSection.MODELS) },
                        onOpenSettings = { navigateTo(JarvisSection.SETTINGS) },
                        onAttachDocument = { importRagDocumentLauncher.launch(arrayOf("*/*")) },
                        onOpenSection = { sec -> navigateTo(sec) },
                        onRegenerateLast = { regenerateLastReply() },
                        onExportDialogue = { shareActiveDialogue() },
                    )

                    JarvisSection.SETTINGS -> JarvisSettingsScreen(
                        stackStatus = stackStatus,
                        continuousDialogue = continuousDialogue,
                        muted = muted,
                        memoryPaused = memoryPaused,
                        factsCount = memoryFacts.size,
                        documentsCount = indexedDocuments.size,
                        mcpCount = mcpServers.size,
                        cacheBytes = runtime.calculateAppCacheBytes(),
                        onBack = { navigateTo(JarvisSection.HOME) },
                        onToggleContinuous = {
                            continuousDialogue = it
                            runtime.setContinuousDialogueEnabled(it)
                        },
                        onToggleMuted = { enable ->
                            muted = enable
                            runtime.setMuted(enable)
                        },
                        onSetSpeechSpeed = { speed -> runtime.setSpeechSpeed(speed) },
                        onTestVoice = { runtime.testRussianVoice() },
                        onToggleMemoryPaused = { paused ->
                            lifecycleScope.launch(Dispatchers.IO) { runtime.setMemoryPaused(paused) }
                        },
                        onClearCache = { runtime.clearAppCache() },
                        onNavigateSection = { sec -> navigateTo(sec) },
                        onImportCustomGguf = { importGgufLauncher.launch(arrayOf("*/*")) },
                        appearance = appearance,
                        onUpdateAppearance = { next -> runtime.updateAppearance(next) },
                        webToolsEnabled = webToolsEnabled,
                        onToggleWebTools = { enabled -> runtime.setWebToolsEnabled(enabled) },
                    )

                    else -> Box(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Jarvis3DStageBackground(
                            showGrid = false,
                            showHudFrame = true,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(palette.topBarBg)
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    IconButton(onClick = {
                                        if (currentSection == JarvisSection.MODELS) {
                                            navigateTo(JarvisSection.SETTINGS)
                                        } else {
                                            navigateTo(JarvisSection.HOME)
                                        }
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = palette.textPrimary)
                                    }
                                    IconButton(onClick = { uiScope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Меню возможностей", tint = palette.textPrimary)
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = currentSection.title,
                                        color = palette.textPrimary,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { navigateTo(JarvisSection.SETTINGS) }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = palette.textPrimary)
                                }
                            }

                            when (currentSection) {
                                JarvisSection.DOCUMENTS -> DocumentsScreen(
                                    documents = indexedDocuments,
                                    onImportDocument = { importRagDocumentLauncher.launch(arrayOf("*/*")) },
                                    onDeleteDocument = { docId ->
                                        lifecycleScope.launch(Dispatchers.IO) { runtime.deleteDocument(docId) }
                                    },
                                    onSearchDocuments = { q -> runtime.documentStore.searchMatches(q, limit = 5) },
                                    onAddTextNote = { title, body -> runtime.addTextDocumentNote(title, body) },
                                    modifier = Modifier.weight(1f),
                                )

                                JarvisSection.STUDIO -> MediaStudioScreen(
                                    items = studioItems,
                                    initialConfig = studioConfig,
                                    pendingPromptRequest = pendingStudioRequest,
                                    onClearPendingRequest = { pendingStudioRequest = null },
                                    onGenerateImage = { p, s, w, h, cb -> mediaGenerator.generateImage(p, s, w, h, cb) },
                                    onGenerateVideo = { p, s, w, h, mp, cb -> mediaGenerator.generateVideo(p, s, w, h, mp, cb) },
                                    onOpenItem = { item -> openGeneratedMedia(item) },
                                    onDeleteItem = { id -> mediaGenerator.deleteItem(id) },
                                    onSaveConfig = { cfg -> mediaGenerator.saveConfig(cfg) },
                                    onAutoDiscoverComfy = { url, key -> mediaGenerator.autoDiscoverComfyModels(url, key) },
                                    onPickLocalStudioFile = { slot ->
                                        pendingStudioSlot = slot
                                        importStudioFileLauncher.launch(arrayOf("*/*"))
                                    },
                                    modifier = Modifier.weight(1f),
                                )

                                JarvisSection.SAVE_VIDEO -> SaveVideoScreen(
                                    tasks = saveVideoTasks,
                                    onInspect = { url -> saveVideoManager.inspectMetadata(url) },
                                    onEnqueue = { url, quality -> saveVideoManager.enqueue(url, quality) },
                                    onCancel = { id -> saveVideoManager.cancelTask(id) },
                                    onRetry = { id -> saveVideoManager.startTask(id) },
                                    onDelete = { id -> saveVideoManager.deleteTask(id) },
                                    modifier = Modifier.weight(1f),
                                )

                                JarvisSection.MEMORY -> MemoryManagerScreen(
                                    facts = memoryFacts,
                                    memoryPaused = memoryPaused,
                                    lastDeletedFact = lastDeletedFact,
                                    onTogglePaused = { paused ->
                                        lifecycleScope.launch(Dispatchers.IO) { runtime.setMemoryPaused(paused) }
                                    },
                                    onAddFact = { key, content -> runtime.addMemoryFact(key, content) },
                                    onDeleteFact = { id ->
                                        lifecycleScope.launch(Dispatchers.IO) { runtime.deleteMemoryFact(id) }
                                    },
                                    onUndoDelete = {
                                        lifecycleScope.launch(Dispatchers.IO) { runtime.undoDeleteMemoryFact() }
                                    },
                                    onDismissUndo = { runtime.clearLastDeletedFact() },
                                    onExportJson = { runtime.exportMemoryJson() },
                                    modifier = Modifier.weight(1f),
                                )

                                JarvisSection.TOOLS_MCP -> ToolsAndMcpScreen(
                                    servers = mcpServers,
                                    onSaveServers = { updated ->
                                        lifecycleScope.launch(Dispatchers.IO) { runtime.saveMcpServers(updated) }
                                    },
                                    onExecutePhoneAction = { action -> runtime.executePhoneAction(action) },
                                    onGetDeviceDiagnostics = { runtime.deviceDiagnosticsText() },
                                    modifier = Modifier.weight(1f),
                                )

                                JarvisSection.CONTROL -> ControlCenterScreen(
                                    snapshot = controlSnapshot,
                                    onRefreshTelemetry = { refreshTelemetry() },
                                    onClearEvents = { controlCenter.clearEvents() },
                                    modifier = Modifier.weight(1f),
                                )

                                JarvisSection.MODELS -> AiModelsScreen(
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
                                    onSelectPromptTemplate = { fmt -> runtime.selectPromptTemplate(fmt) },
                                    onSaveExternalAi = { cfg -> runtime.saveExternalAiConfig(cfg) },
                                    onDownloadHfGguf = { url, fileName, sizeBytes ->
                                        jarvisApp.downloads.start(fileName) { update ->
                                            runtime.downloadCustomHfGguf(url, fileName, sizeBytes) { pct, dl, total ->
                                                val dlMb = dl / (1024 * 1024)
                                                val totalMb = (total / (1024 * 1024)).coerceAtLeast(1)
                                                update("$fileName: $dlMb/$totalMb МБ ($pct%)", pct)
                                            }
                                        }
                                    },
                                    onSaveGenerationSettings = { gen -> runtime.saveGenerationSettings(gen) },
                                    modifier = Modifier.weight(1f),
                                )

                                JarvisSection.HOME, JarvisSection.SETTINGS -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}
