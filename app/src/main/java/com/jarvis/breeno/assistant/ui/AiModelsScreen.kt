package com.jarvis.breeno.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jarvis.breeno.assistant.core.ExternalAiConfig
import com.jarvis.breeno.assistant.core.ExternalAiMode
import com.jarvis.breeno.assistant.updater.LocalLlmPack
import com.jarvis.breeno.assistant.updater.PiperVoicePack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val CardBg = Color(0xCC111C2A)
private val CyanAccent = Color(0xFF75E5FF)
private val GreenReady = Color(0xFF34D399)
private val MutedText = Color(0xFF9AAABD)

@Composable
fun AiModelsScreen(
    status: AiStackStatus,
    llmCatalog: List<LocalLlmPack>,
    voiceCatalog: List<PiperVoicePack>,
    onDownloadStarterBundle: suspend (onProgress: (String, Int) -> Unit) -> Unit,
    onDownloadAsr: suspend (onProgress: (Int) -> Unit) -> Unit,
    onDownloadVad: suspend (onProgress: (Int) -> Unit) -> Unit,
    onDownloadVoice: suspend (String, (Int) -> Unit) -> Unit,
    onSelectVoice: (String) -> Unit,
    onDeleteVoice: (String) -> Unit,
    onTestVoice: () -> Unit,
    onDownloadLlm: suspend (String, (Int, Long, Long) -> Unit) -> Unit,
    onSelectLlm: (String) -> Unit,
    onDeleteLlm: suspend (String) -> Unit,
    onSaveExternalAi: (ExternalAiConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var activeDownloadJob by remember { mutableStateOf<Job?>(null) }
    var downloadLabel by remember { mutableStateOf<String?>(null) }
    var downloadPercent by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<String?>(null) }

    var extEnabled by remember(status.externalAiConfig) { mutableStateOf(status.externalAiConfig.enabled) }
    var extMode by remember(status.externalAiConfig) { mutableStateOf(status.externalAiConfig.mode) }
    var extUrl by remember(status.externalAiConfig) { mutableStateOf(status.externalAiConfig.baseUrl) }
    var extModel by remember(status.externalAiConfig) { mutableStateOf(status.externalAiConfig.modelName) }
    var extKey by remember(status.externalAiConfig) { mutableStateOf(status.externalAiConfig.apiKey) }

    fun launchDownload(initialLabel: String, block: suspend () -> Unit) {
        if (activeDownloadJob?.isActive == true) {
            feedback = "Дождитесь завершения или отмените текущую загрузку"
            return
        }
        downloadLabel = initialLabel
        downloadPercent = 0
        feedback = null
        activeDownloadJob = scope.launch {
            try {
                block()
                feedback = "Готово: $initialLabel"
            } catch (_: CancellationException) {
                feedback = "Загрузка отменена"
            } catch (err: Throwable) {
                feedback = "Ошибка загрузки: ${err.message ?: err.javaClass.simpleName}"
            } finally {
                downloadLabel = null
                downloadPercent = 0
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "МОДЕЛИ И ГОЛОС ДЖАРВИСА",
            color = CyanAccent,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Настоящий офлайн-ИИ (llama.cpp + Qwen 2.5) и русский голос (Piper TTS) прямо на телефоне.",
            color = MutedText,
            style = MaterialTheme.typography.bodySmall,
        )

        if (downloadLabel != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xCC162436)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(downloadLabel!!, color = CyanAccent, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { activeDownloadJob?.cancel() }) {
                            Icon(Icons.Default.Close, contentDescription = "Отменить загрузку", tint = Color(0xFFFF8A8A))
                        }
                    }
                    LinearProgressIndicator(
                        progress = { (downloadPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (!feedback.isNullOrBlank()) {
            Text(
                text = feedback!!,
                color = if (feedback!!.startsWith("Ошибка")) Color(0xFFFF8A8A) else GreenReady,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        fun updateProgressOnMain(label: String? = null, percent: Int) {
            scope.launch(Dispatchers.Main.immediate) {
                if (label != null) downloadLabel = label
                downloadPercent = percent
            }
        }

        // 1. Offline Starter Bundle
        val starterComplete = status.asrReady && status.ttsVoiceReady && status.vadReady && status.activeLlmId != null
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Быстрый старт: Полный офлайн Джарвис", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    text = "Слух: ${if (status.asrReady) "✓" else "✗"}  •  Голос: ${if (status.ttsVoiceReady) "✓" else "✗"}  •  VAD: ${if (status.vadReady) "✓" else "✗"}  •  Мозг: ${if (status.activeLlmId != null) "✓" else "✗"}",
                    color = if (starterComplete) GreenReady else MutedText,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (!starterComplete) {
                    Button(
                        onClick = {
                            launchDownload("Полный офлайн-набор") {
                                onDownloadStarterBundle { stage, pct ->
                                    updateProgressOnMain(stage, pct)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text("Скачать всё для офлайн-работы (~520 МБ)")
                    }
                }
            }
        }

        // 2. Russian Piper TTS Voice
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Русский голос Джарвиса (Piper TTS)", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Сейчас: ${status.speakerLabel}", color = if (status.ttsVoiceReady) GreenReady else Color(0xFFFBBF24), style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = onTestVoice) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 2.dp))
                        Text("Тест")
                    }
                }

                voiceCatalog.forEach { voice ->
                    val isInstalled = voice.id in status.readyTtsVoiceIds
                    val isSelected = isInstalled && status.selectedVoiceId == voice.id
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(voice.title, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(voice.description, color = MutedText, style = MaterialTheme.typography.labelSmall)
                        }
                        if (isInstalled) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSelectVoice(voice.id) },
                                    label = { Text(if (isSelected) "Активен" else "Выбрать") },
                                )
                                IconButton(onClick = { onDeleteVoice(voice.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить голос", tint = MutedText)
                                }
                            }
                        } else {
                            OutlinedButton(onClick = {
                                launchDownload(voice.title) {
                                    onDownloadVoice(voice.id) { pct -> updateProgressOnMain(percent = pct) }
                                }
                            }) {
                                Text("Скачать")
                            }
                        }
                    }
                }
            }
        }

        // 3. Local LLM Brain (llama.cpp GGUF)
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Локальный ИИ-мозг (llama.cpp + Qwen 2.5)", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    text = if (status.nativeLlamaLoaded) {
                        "Движок libjarvis_llama.so: готов к работе"
                    } else {
                        "Движок llama.cpp недоступен: ${status.nativeLlamaError ?: "ABI не поддерживается"}"
                    },
                    color = if (status.nativeLlamaLoaded) GreenReady else Color(0xFFFF8A8A),
                    style = MaterialTheme.typography.labelSmall,
                )

                llmCatalog.forEach { pack ->
                    val isInstalled = pack.id in status.readyLlmIds
                    val isActive = status.activeLlmId == pack.id
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pack.title, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(pack.description, color = MutedText, style = MaterialTheme.typography.labelSmall)
                        }
                        if (isInstalled) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = isActive,
                                    onClick = { onSelectLlm(pack.id) },
                                    label = { Text(if (isActive) "Активна" else "Выбрать") },
                                )
                                IconButton(onClick = { scope.launch { onDeleteLlm(pack.id) } }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить модель", tint = MutedText)
                                }
                            }
                        } else {
                            OutlinedButton(onClick = {
                                launchDownload(pack.title) {
                                    onDownloadLlm(pack.id) { pct, dl, total ->
                                        val dlMb = dl / (1024 * 1024)
                                        val totalMb = (total / (1024 * 1024)).coerceAtLeast(1)
                                        updateProgressOnMain("${pack.title}: $dlMb/$totalMb МБ ($pct%)", pct)
                                    }
                                }
                            }) {
                                Text("Скачать")
                            }
                        }
                    }
                }
            }
        }

        // 4. Hearing (ASR & Silero VAD)
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Слух и распознавание речи", color = Color.White, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sherpa-ONNX Русский ASR", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(if (status.asrReady) "Установлена (офлайн)" else "Не скачана", color = if (status.asrReady) GreenReady else MutedText, style = MaterialTheme.typography.labelSmall)
                    }
                    if (!status.asrReady) {
                        OutlinedButton(onClick = {
                            launchDownload("Русский ASR") { onDownloadAsr { pct -> updateProgressOnMain(percent = pct) } }
                        }) { Text("Скачать") }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Silero VAD (датчик окончания фразы, 2 МБ)", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(if (status.vadReady) "Установлена (умный микрофон)" else "Не скачана (фикс. 5 сек)", color = if (status.vadReady) GreenReady else MutedText, style = MaterialTheme.typography.labelSmall)
                    }
                    if (!status.vadReady) {
                        OutlinedButton(onClick = {
                            launchDownload("Silero VAD") { onDownloadVad { pct -> updateProgressOnMain(percent = pct) } }
                        }) { Text("Скачать") }
                    }
                }
            }
        }

        // 5. Optional External AI / PC Bridge
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Внешний ИИ / Облако (опционально)", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Запасной вариант при наличии сети (выкл. по умолчанию)", color = MutedText, style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = extEnabled, onCheckedChange = { extEnabled = it })
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = extMode == ExternalAiMode.OPENAI_COMPATIBLE,
                        onClick = { extMode = ExternalAiMode.OPENAI_COMPATIBLE },
                        label = { Text("OpenAI / Сервер") },
                    )
                    FilterChip(
                        selected = extMode == ExternalAiMode.GEMINI,
                        onClick = { extMode = ExternalAiMode.GEMINI },
                        label = { Text("Google Gemini") },
                    )
                }

                if (extMode == ExternalAiMode.OPENAI_COMPATIBLE) {
                    OutlinedTextField(
                        value = extUrl,
                        onValueChange = { extUrl = it },
                        label = { Text("URL Chat Completions (https://...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                OutlinedTextField(
                    value = extModel,
                    onValueChange = { extModel = it },
                    label = { Text(if (extMode == ExternalAiMode.GEMINI) "Модель (напр. gemini-2.0-flash)" else "Модель (напр. gpt-4o-mini)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = extKey,
                    onValueChange = { extKey = it },
                    label = { Text("API-ключ (хранится только локально на телефоне)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = {
                        onSaveExternalAi(
                            ExternalAiConfig(
                                enabled = extEnabled,
                                mode = extMode,
                                baseUrl = extUrl,
                                modelName = extModel,
                                apiKey = extKey,
                            )
                        )
                        feedback = "Настройки внешнего ИИ сохранены"
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Сохранить настройки внешнего ИИ")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
