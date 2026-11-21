package com.jarvis.breeno.assistant.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.FolderOpen
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
import com.jarvis.breeno.assistant.DownloadTaskState
import com.jarvis.breeno.assistant.core.ExternalAiConfig
import com.jarvis.breeno.assistant.core.ExternalAiMode
import com.jarvis.breeno.assistant.core.JarvisPersona
import com.jarvis.breeno.assistant.core.PromptTemplateFormat
import com.jarvis.breeno.assistant.updater.LocalLlmModelManager
import com.jarvis.breeno.assistant.updater.LocalLlmPack
import com.jarvis.breeno.assistant.updater.PiperVoicePack
import kotlinx.coroutines.launch

private val CardBg = Color(0xCC0C1628)
private val CyanAccent = Color(0xFF00E5FF)
private val GoldAccent = Color(0xFFFFB300)
private val GreenReady = Color(0xFF34D399)
private val MutedText = Color(0xFF9AAABD)

@Composable
fun AiModelsScreen(
    status: AiStackStatus,
    downloadState: DownloadTaskState,
    llmCatalog: List<LocalLlmPack>,
    voiceCatalog: List<PiperVoicePack>,
    onDownloadStarterBundle: () -> Unit,
    onDownloadAsr: () -> Unit,
    onDownloadVad: () -> Unit,
    onDownloadVoice: (String) -> Unit,
    onCancelDownload: () -> Unit,
    onSelectVoice: (String) -> Unit,
    onSetSpeechSpeed: (Float) -> Unit,
    onDeleteVoice: (String) -> Unit,
    onTestVoice: () -> Unit,
    onDownloadLlm: (String) -> Unit,
    onSelectLlm: (String) -> Unit,
    onDeleteLlm: suspend (String) -> Unit,
    onImportCustomGguf: () -> Unit,
    onSelectPersona: (JarvisPersona) -> Unit,
    onSelectPromptTemplate: (PromptTemplateFormat) -> Unit,
    onSaveExternalAi: (ExternalAiConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var localFeedback by remember { mutableStateOf<String?>(null) }

    var extEnabled by remember(status.externalAiConfig) { mutableStateOf(status.externalAiConfig.enabled) }
    var extMode by remember(status.externalAiConfig) {
        mutableStateOf(
            if (status.externalAiConfig.mode == ExternalAiMode.POLLINATIONS_FREE) {
                ExternalAiMode.OPENAI_COMPATIBLE
            } else {
                status.externalAiConfig.mode
            }
        )
    }
    var extUrl by remember(status.externalAiConfig) { mutableStateOf(status.externalAiConfig.baseUrl) }
    var extModel by remember(status.externalAiConfig) { mutableStateOf(status.externalAiConfig.modelName) }
    var extKey by remember(status.externalAiConfig) { mutableStateOf(status.externalAiConfig.apiKey) }

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
            text = "Загрузки продолжаются в фоне при сворачивании или переключении вкладок.",
            color = MutedText,
            style = MaterialTheme.typography.bodySmall,
        )

        if (downloadState.active) {
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
                        Text(
                            text = downloadState.label,
                            color = CyanAccent,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onCancelDownload) {
                            Icon(Icons.Default.Close, contentDescription = "Отменить загрузку", tint = Color(0xFFFF8A8A))
                        }
                    }
                    LinearProgressIndicator(
                        progress = { (downloadState.percent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        val shownMsg = localFeedback ?: downloadState.message
        if (!shownMsg.isNullOrBlank()) {
            val isErr = localFeedback == null && downloadState.isError
            Text(
                text = shownMsg,
                color = if (isErr) Color(0xFFFF8A8A) else GreenReady,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        // 1. Offline Starter Bundle (works locally without external keys or HTTP 402 paywalls)
        val starterComplete = status.asrReady && status.ttsVoiceReady && status.vadReady && status.activeLlmId != null
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Полный офлайн Джарвис (без ключей и HTTP 402)", color = GoldAccent, fontWeight = FontWeight.Bold)
                Text(
                    text = "Слух: ${if (status.asrReady) "✓" else "✗"}  •  Голос: ${if (status.ttsVoiceReady) "✓" else "✗"}  •  VAD: ${if (status.vadReady) "✓" else "✗"}  •  Мозг: ${if (status.activeLlmId != null) "✓" else "✗"}",
                    color = if (starterComplete) GreenReady else MutedText,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (!starterComplete) {
                    Button(
                        onClick = onDownloadStarterBundle,
                        enabled = !downloadState.active,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text("Скачать офлайн-набор: Dolphin 3.0 + Дмитрий HD (~480 МБ)")
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
                        Text(
                            text = "Сейчас: ${status.speakerLabel}",
                            color = if (status.ttsVoiceReady) GreenReady else Color(0xFFFBBF24),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    OutlinedButton(onClick = onTestVoice) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 2.dp))
                        Text("Тест")
                    }
                }

                Text("Скорость речи:", color = MutedText, style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        0.75f to "0.75x",
                        0.85f to "0.85x (Джарвис)",
                        0.95f to "0.95x",
                        1.05f to "1.05x",
                    ).forEach { (speed, label) ->
                        FilterChip(
                            selected = kotlin.math.abs(status.speechSpeed - speed) < 0.04f,
                            onClick = { onSetSpeechSpeed(speed) },
                            label = { Text(label) },
                        )
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
                            Text(
                                text = if (isInstalled) "✓ Проверен и готов" else voice.description,
                                color = if (isInstalled) GreenReady else MutedText,
                                style = MaterialTheme.typography.labelSmall,
                            )
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
                            OutlinedButton(
                                onClick = { onDownloadVoice(voice.id) },
                                enabled = !downloadState.active,
                            ) {
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
                Text("Локальный ИИ-мозг (llama.cpp: Dolphin 3.0 / Hermes 3 / Qwen / свой GGUF)", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    text = if (status.nativeLlamaLoaded) {
                        "Движок libjarvis_llama.so (ARM64 dotprod+fp16): готов"
                    } else {
                        "Движок llama.cpp недоступен: ${status.nativeLlamaError ?: "ABI не поддерживается"}"
                    },
                    color = if (status.nativeLlamaLoaded) GreenReady else Color(0xFFFF8A8A),
                    style = MaterialTheme.typography.labelSmall,
                )

                Text("Характер ответов Джарвиса:", color = MutedText, style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    JarvisPersona.entries.forEach { persona ->
                        FilterChip(
                            selected = status.selectedPersona == persona,
                            onClick = { onSelectPersona(persona) },
                            label = { Text(persona.title) },
                        )
                    }
                }

                Text("Шаблон промпта (Prompt Format):", color = MutedText, style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PromptTemplateFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = status.selectedPromptTemplate == fmt,
                            onClick = { onSelectPromptTemplate(fmt) },
                            label = { Text(fmt.title) },
                        )
                    }
                }

                val familyOrder = listOf(
                    LocalLlmModelManager.FAMILY_OPEN_COMMUNITY,
                    LocalLlmModelManager.FAMILY_QWEN,
                    LocalLlmModelManager.FAMILY_CUSTOM,
                )
                familyOrder.forEach { familyName ->
                    val group = llmCatalog.filter { it.family == familyName }
                    if (group.isNotEmpty()) {
                        Text(
                            text = familyName,
                            color = if (familyName == LocalLlmModelManager.FAMILY_OPEN_COMMUNITY) GoldAccent else CyanAccent,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        group.forEach { pack ->
                            LlmPackRow(
                                pack = pack,
                                isInstalled = pack.id in status.readyLlmIds,
                                isActive = status.activeLlmId == pack.id,
                                downloadActive = downloadState.active,
                                onDownload = { onDownloadLlm(pack.id) },
                                onSelect = { onSelectLlm(pack.id) },
                                onDelete = { scope.launch { onDeleteLlm(pack.id) } },
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onImportCustomGguf,
                    enabled = !downloadState.active,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("Импортировать свой .gguf файл с телефона")
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
                        Text(if (status.asrReady) "✓ Установлена (офлайн)" else "Не скачана", color = if (status.asrReady) GreenReady else MutedText, style = MaterialTheme.typography.labelSmall)
                    }
                    if (!status.asrReady) {
                        OutlinedButton(onClick = onDownloadAsr, enabled = !downloadState.active) { Text("Скачать") }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Silero VAD (датчик окончания фразы, 2 МБ)", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(if (status.vadReady) "✓ Установлена (умный микрофон)" else "Не скачана (фикс. 5 сек)", color = if (status.vadReady) GreenReady else MutedText, style = MaterialTheme.typography.labelSmall)
                    }
                    if (!status.vadReady) {
                        OutlinedButton(onClick = onDownloadVad, enabled = !downloadState.active) { Text("Скачать") }
                    }
                }
            }
        }

        // 5. Optional Custom External AI / PC Bridge
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
                        Text("Свой сервер / Gemini API (опционально)", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Если хотите подключить свой ПК или личный ключ Gemini", color = MutedText, style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = extEnabled, onCheckedChange = { extEnabled = it })
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = extMode == ExternalAiMode.OPENAI_COMPATIBLE,
                        onClick = { extMode = ExternalAiMode.OPENAI_COMPATIBLE },
                        label = { Text("OpenAI / ПК Ollama") },
                    )
                    FilterChip(
                        selected = extMode == ExternalAiMode.GEMINI,
                        onClick = { extMode = ExternalAiMode.GEMINI },
                        label = { Text("Gemini API") },
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
                    label = { Text("API-ключ (хранится только локально)") },
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
                        localFeedback = "Настройки внешнего ИИ сохранены"
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Сохранить настройки внешнего ИИ")
                }
            }
        }

        // 6. OPPO ColorOS 12 GB RAM tip
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Совет для OPPO ColorOS (12 ГБ ОЗУ)", color = CyanAccent, fontWeight = FontWeight.Bold)
                Text(
                    text = "Для 12 ГБ ОЗУ используйте модели 3B (Q4_K_M) или L3-8B Stheno Q3_K_M (~3.74 ГБ). Чтобы ColorOS не закрывал нейросеть или Local Dream NPU в фоне, откройте список недавних приложений, потяните карточку вниз -> «Заблокировать» (значок замка) и разрешите фоновую работу в настройках батареи.",
                    color = MutedText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun LlmPackRow(
    pack: LocalLlmPack,
    isInstalled: Boolean,
    isActive: Boolean,
    downloadActive: Boolean,
    onDownload: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(pack.title, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = when {
                    isInstalled -> "✓ Проверена SHA-256 и готова"
                    pack.isCustom -> "Файл отсутствует — переимпортируйте"
                    else -> pack.description
                },
                color = if (isInstalled) GreenReady else MutedText,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (isInstalled || pack.isCustom) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = isActive,
                    onClick = onSelect,
                    label = { Text(if (isActive) "Активна" else "Выбрать") },
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить модель", tint = MutedText)
                }
            }
        } else {
            OutlinedButton(
                onClick = onDownload,
                enabled = !downloadActive,
            ) {
                Text("Скачать")
            }
        }
    }
}
