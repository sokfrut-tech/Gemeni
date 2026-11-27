package com.jarvis.breeno.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import com.jarvis.breeno.assistant.ui.theme.JarvisButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.jarvis.breeno.assistant.ui.theme.JarvisOutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.breeno.assistant.DownloadTaskState
import com.jarvis.breeno.assistant.core.ExternalAiConfig
import com.jarvis.breeno.assistant.core.ExternalAiMode
import com.jarvis.breeno.assistant.core.JarvisPersona
import com.jarvis.breeno.assistant.core.PromptTemplateFormat
import com.jarvis.breeno.assistant.ui.theme.LocalJarvisPalette
import com.jarvis.breeno.assistant.ui.theme.jarvisTileShape
import com.jarvis.breeno.assistant.updater.HfGgufFile
import com.jarvis.breeno.assistant.updater.HfRepoSummary
import com.jarvis.breeno.assistant.updater.HuggingFaceGgufBrowser
import com.jarvis.breeno.assistant.updater.LlmGenerationSettings
import com.jarvis.breeno.assistant.updater.LocalLlmModelManager
import com.jarvis.breeno.assistant.updater.LocalLlmPack
import com.jarvis.breeno.assistant.updater.PiperVoicePack
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private enum class ModelFamilyFilter(val label: String) {
    ALL("Все"),
    OPEN("Dolphin / Hermes / Stheno"),
    QWEN("Qwen"),
    CUSTOM("Свои GGUF"),
}

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
    onDownloadHfGguf: (downloadUrl: String, fileName: String, sizeBytes: Long) -> Unit = { _, _, _ -> },
    onSaveGenerationSettings: (LlmGenerationSettings) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = LocalJarvisPalette.current
    val tileShape = jarvisTileShape(palette, 14.dp)
    val scope = rememberCoroutineScope()
    var localFeedback by remember { mutableStateOf<String?>(null) }

    // Compact dropdown states for LLM & Voices (no infinite scrolling)
    var familyFilter by remember { mutableStateOf(ModelFamilyFilter.ALL) }
    val filteredPacks = remember(llmCatalog, familyFilter) {
        when (familyFilter) {
            ModelFamilyFilter.ALL -> llmCatalog
            ModelFamilyFilter.OPEN -> llmCatalog.filter { it.family == LocalLlmModelManager.FAMILY_OPEN_COMMUNITY }
            ModelFamilyFilter.QWEN -> llmCatalog.filter { it.family == LocalLlmModelManager.FAMILY_QWEN }
            ModelFamilyFilter.CUSTOM -> llmCatalog.filter { it.isCustom || it.family == LocalLlmModelManager.FAMILY_CUSTOM }
        }
    }
    var inspectedPackId by remember(status.activeLlmId, filteredPacks) {
        mutableStateOf(
            status.activeLlmId?.takeIf { id -> filteredPacks.any { it.id == id } }
                ?: filteredPacks.firstOrNull { it.id in status.readyLlmIds }?.id
                ?: filteredPacks.firstOrNull()?.id
                ?: llmCatalog.firstOrNull()?.id
        )
    }
    val inspectedPack = filteredPacks.firstOrNull { it.id == inspectedPackId }
        ?: filteredPacks.firstOrNull()
        ?: llmCatalog.firstOrNull()

    var showModelDropdown by remember { mutableStateOf(false) }
    var showReadySwitchDropdown by remember { mutableStateOf(false) }
    var showVoiceDropdown by remember { mutableStateOf(false) }

    // Generation Settings state (collapsed by default)
    var genExpanded by remember { mutableStateOf(false) }
    var temperature by remember(status.generationSettings) { mutableFloatStateOf(status.generationSettings.temperature) }
    var topP by remember(status.generationSettings) { mutableFloatStateOf(status.generationSettings.topP) }
    var maxTokens by remember(status.generationSettings) { mutableIntStateOf(status.generationSettings.maxTokens) }
    var contextLength by remember(status.generationSettings) { mutableIntStateOf(status.generationSettings.contextLength) }
    var cpuThreads by remember(status.generationSettings) { mutableIntStateOf(status.generationSettings.cpuThreads) }
    var customSysPrompt by remember(status.generationSettings) { mutableStateOf(status.generationSettings.customSystemPrompt) }

    // Collapsed sections for HF / ASR / External AI
    var hfExpanded by remember { mutableStateOf(false) }
    var speechAsrExpanded by remember { mutableStateOf(false) }
    var externalExpanded by remember { mutableStateOf(false) }

    var hfQuery by remember { mutableStateOf("Dolphin") }
    var hfBusy by remember { mutableStateOf(false) }
    var hfRepos by remember { mutableStateOf<List<HfRepoSummary>>(emptyList()) }
    var hfSelectedRepo by remember { mutableStateOf<String?>(null) }
    var hfFiles by remember { mutableStateOf<List<HfGgufFile>>(emptyList()) }
    var hfStatus by remember { mutableStateOf<String?>(null) }

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

    val readyPacks = remember(llmCatalog, status.readyLlmIds) {
        llmCatalog.filter { it.id in status.readyLlmIds }
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
            color = palette.primaryAccent,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Компактный выбор локальных GGUF-моделей, голосов и параметров генерации.",
            color = palette.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )

        if (downloadState.active) {
            HudGlassCard(tileShape = tileShape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = downloadState.label,
                        color = palette.primaryAccent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onCancelDownload) {
                        Icon(Icons.Default.Close, contentDescription = "Отменить загрузку", tint = palette.errorAccent)
                    }
                }
                LinearProgressIndicator(
                    progress = { (downloadState.percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.primaryAccent,
                )
            }
        }

        val shownMsg = localFeedback ?: downloadState.message
        if (!shownMsg.isNullOrBlank()) {
            val isErr = localFeedback == null && downloadState.isError
            HudGlassCard(tileShape = tileShape) {
                Text(
                    text = shownMsg,
                    color = if (isErr) palette.errorAccent else palette.successAccent,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        // 1. Offline Starter Bundle
        val starterComplete = status.asrReady && status.ttsVoiceReady && status.vadReady && status.activeLlmId != null
        HudGlassCard(tileShape = tileShape) {
            Text("Полный офлайн Джарвис (без ключей и подписок)", color = palette.secondaryAccent, fontWeight = FontWeight.Bold)
            Text(
                text = "Слух: ${if (status.asrReady) "✓" else "✗"}  •  Голос: ${if (status.ttsVoiceReady) "✓" else "✗"}  •  VAD: ${if (status.vadReady) "✓" else "✗"}  •  Мозг: ${if (status.activeLlmId != null) "✓" else "✗"}",
                color = if (starterComplete) palette.successAccent else palette.textSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            if (!starterComplete) {
                JarvisButton(
                    onClick = onDownloadStarterBundle,
                    enabled = !downloadState.active,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primaryAccent, contentColor = Color(0xFF03101B)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Скачать офлайн-набор: Dolphin 3.0 + Дмитрий HD (~480 МБ)", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 2. Local LLM Brain — Compact Dropdown Picker!
        HudGlassCard(tileShape = tileShape) {
            Text(
                text = "Локальный ИИ-мозг (выпадающий список моделей)",
                color = palette.textPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (status.nativeLlamaLoaded) {
                    "Движок libjarvis_llama.so (ARM64): готов • Активна: ${status.activeLlmTitle ?: "не выбрана"}"
                } else {
                    "Движок llama.cpp недоступен: ${status.nativeLlamaError ?: "ABI не поддерживается"}"
                },
                color = if (status.nativeLlamaLoaded && status.activeLlmId != null) palette.successAccent else palette.textSecondary,
                style = MaterialTheme.typography.labelSmall,
            )

            // Quick switch dropdown if any models are already downloaded/imported
            if (readyPacks.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = { showReadySwitchDropdown = true },
                        shape = RoundedCornerShape(12.dp),
                        color = palette.tileBgAlt,
                        border = BorderStroke(1.dp, palette.successAccent.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Установленные модели (${readyPacks.size})", color = palette.successAccent, style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = status.activeLlmTitle ?: readyPacks.first().title,
                                    color = palette.textPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = palette.successAccent)
                        }
                    }
                    DropdownMenu(
                        expanded = showReadySwitchDropdown,
                        onDismissRequest = { showReadySwitchDropdown = false },
                    ) {
                        readyPacks.forEach { pack ->
                            val active = pack.id == status.activeLlmId
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (active) "✓ ${pack.title}" else pack.title,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                onClick = {
                                    showReadySwitchDropdown = false
                                    onSelectLlm(pack.id)
                                    inspectedPackId = pack.id
                                    localFeedback = "Активирована модель: ${pack.title}"
                                },
                            )
                        }
                    }
                }
            }

            // Family filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ModelFamilyFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = familyFilter == filter,
                        onClick = {
                            familyFilter = filter
                            val firstInFilter = when (filter) {
                                ModelFamilyFilter.ALL -> llmCatalog
                                ModelFamilyFilter.OPEN -> llmCatalog.filter { it.family == LocalLlmModelManager.FAMILY_OPEN_COMMUNITY }
                                ModelFamilyFilter.QWEN -> llmCatalog.filter { it.family == LocalLlmModelManager.FAMILY_QWEN }
                                ModelFamilyFilter.CUSTOM -> llmCatalog.filter { it.isCustom || it.family == LocalLlmModelManager.FAMILY_CUSTOM }
                            }.firstOrNull()
                            if (firstInFilter != null) inspectedPackId = firstInFilter.id
                        },
                        label = { Text(filter.label) },
                    )
                }
            }

            // Main Catalog Dropdown Selector
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    onClick = { if (filteredPacks.isNotEmpty()) showModelDropdown = true },
                    shape = RoundedCornerShape(12.dp),
                    color = palette.tileBgAlt,
                    border = BorderStroke(1.dp, palette.primaryAccent.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Выбрать из списка (${filteredPacks.size})",
                                color = palette.primaryAccent,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                text = inspectedPack?.title ?: "Нет моделей в этом фильтре — нажмите Импорт GGUF",
                                color = palette.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Открыть список моделей", tint = palette.primaryAccent)
                    }
                }

                DropdownMenu(
                    expanded = showModelDropdown,
                    onDismissRequest = { showModelDropdown = false },
                ) {
                    filteredPacks.forEach { pack ->
                        val ready = pack.id in status.readyLlmIds
                        val active = pack.id == status.activeLlmId
                        val sizeMb = pack.sizeBytes / (1024L * 1024L)
                        val badge = when {
                            active -> "★ Активна"
                            ready -> "✓ Готова"
                            else -> "${sizeMb} МБ"
                        }
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = "${pack.title}  [$badge]",
                                        fontWeight = if (active || ready) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    Text(
                                        text = pack.family,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.textSecondary,
                                    )
                                }
                            },
                            onClick = {
                                showModelDropdown = false
                                inspectedPackId = pack.id
                            },
                        )
                    }
                }
            }

            // Single detailed card ONLY for the currently selected model from dropdown
            if (inspectedPack != null) {
                val isInstalled = inspectedPack.id in status.readyLlmIds
                val isActive = status.activeLlmId == inspectedPack.id
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.bgBottom.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, if (isActive) palette.successAccent else palette.tileBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(inspectedPack.title, color = palette.textPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = when {
                                isActive -> "✓ Активна и готова к работе (${inspectedPack.sizeBytes / (1024 * 1024)} МБ)"
                                isInstalled -> "✓ Проверена SHA-256 и готова к активации"
                                inspectedPack.isCustom -> "Файл отсутствует — переимпортируйте"
                                else -> inspectedPack.description
                            },
                            color = if (isInstalled) palette.successAccent else palette.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isInstalled || inspectedPack.isCustom) {
                                JarvisButton(
                                    onClick = { onSelectLlm(inspectedPack.id) },
                                    enabled = isInstalled && !isActive,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isActive) palette.successAccent else palette.primaryAccent,
                                        contentColor = Color(0xFF04101B),
                                    ),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(if (isActive) "Активна" else "Активировать")
                                }
                                JarvisOutlinedButton(
                                    onClick = { scope.launch { onDeleteLlm(inspectedPack.id) } },
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить модель", tint = palette.errorAccent)
                                }
                            } else {
                                JarvisButton(
                                    onClick = { onDownloadLlm(inspectedPack.id) },
                                    enabled = !downloadState.active,
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.primaryAccent, contentColor = Color(0xFF04101B)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Скачать модель (${inspectedPack.sizeBytes / (1024 * 1024)} МБ)")
                                }
                            }
                        }
                    }
                }
            }

            JarvisOutlinedButton(
                onClick = onImportCustomGguf,
                enabled = !downloadState.active,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = palette.primaryAccent)
                Spacer(Modifier.width(6.dp))
                Text("Импортировать свой .gguf файл с телефона")
            }
            Text(
                text = "Поддерживаются чистые .gguf и .zip/.gz с .gguf внутри; при повреждённом файле покажется диагностика байтов.",
                color = palette.textSecondary,
                style = MaterialTheme.typography.labelSmall,
            )

            // Collapsed Generation Settings & System Prompt
            HorizontalDivider(color = palette.tileBorder.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { genExpanded = !genExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = palette.secondaryAccent)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Характер, свой промпт и параметры генерации",
                        color = palette.secondaryAccent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Icon(if (genExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = palette.textSecondary)
            }

            AnimatedVisibility(visible = genExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Text("Характер ответов:", color = palette.textSecondary, style = MaterialTheme.typography.labelSmall)
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

                    if (status.selectedPersona == JarvisPersona.CUSTOM) {
                        OutlinedTextField(
                            value = customSysPrompt,
                            onValueChange = { customSysPrompt = it },
                            label = { Text("Свой системный промпт (характер и правила ИИ)") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Text("Шаблон промпта (Prompt Format):", color = palette.textSecondary, style = MaterialTheme.typography.labelSmall)
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

                    Text("Температура: ${"%.2f".format(Locale.US, temperature)}", color = palette.textPrimary, style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = (it * 20f).roundToInt() / 20f },
                        valueRange = 0.10f..1.30f,
                        colors = SliderDefaults.colors(thumbColor = palette.primaryAccent, activeTrackColor = palette.primaryAccent),
                    )

                    Text("Макс. длина ответа: $maxTokens токенов", color = palette.textPrimary, style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = maxTokens.toFloat(),
                        onValueChange = { maxTokens = ((it / 64f).roundToInt() * 64).coerceIn(128, 1024) },
                        valueRange = 128f..1024f,
                        colors = SliderDefaults.colors(thumbColor = palette.primaryAccent, activeTrackColor = palette.primaryAccent),
                    )

                    Text("Окно контекста (n_ctx): $contextLength токенов", color = palette.textPrimary, style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = contextLength.toFloat(),
                        onValueChange = { contextLength = ((it / 256f).roundToInt() * 256).coerceIn(512, 4096) },
                        valueRange = 512f..4096f,
                        colors = SliderDefaults.colors(thumbColor = palette.primaryAccent, activeTrackColor = palette.primaryAccent),
                    )

                    Text("Потоки CPU: $cpuThreads", color = palette.textPrimary, style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = cpuThreads.toFloat(),
                        onValueChange = { cpuThreads = it.roundToInt().coerceIn(2, 6) },
                        valueRange = 2f..6f,
                        steps = 3,
                        colors = SliderDefaults.colors(thumbColor = palette.primaryAccent, activeTrackColor = palette.primaryAccent),
                    )

                    JarvisButton(
                        onClick = {
                            onSaveGenerationSettings(
                                LlmGenerationSettings(
                                    temperature = temperature,
                                    topP = topP,
                                    maxTokens = maxTokens,
                                    contextLength = contextLength,
                                    cpuThreads = cpuThreads,
                                    customSystemPrompt = customSysPrompt,
                                )
                            )
                            localFeedback = "Параметры генерации сохранены"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.secondaryAccent, contentColor = Color(0xFF04101B)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Сохранить параметры генерации", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Hugging Face Search Accordion
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { hfExpanded = !hfExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = palette.primaryAccent)
                    Spacer(Modifier.width(8.dp))
                    Text("Поиск GGUF на Hugging Face (Saiga, Dolphin, Qwen 3)", color = palette.primaryAccent, style = MaterialTheme.typography.labelLarge)
                }
                Icon(if (hfExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = palette.textSecondary)
            }

            AnimatedVisibility(visible = hfExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("Dolphin", "Saiga", "Qwen3", "Hermes", "Llama-3").forEach { tag ->
                            FilterChip(
                                selected = hfQuery.equals(tag, ignoreCase = true),
                                onClick = { hfQuery = tag },
                                label = { Text(tag) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = hfQuery,
                            onValueChange = { hfQuery = it },
                            label = { Text("Запрос Hugging Face") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        JarvisButton(
                            onClick = {
                                scope.launch {
                                    hfBusy = true
                                    hfSelectedRepo = null
                                    hfFiles = emptyList()
                                    hfStatus = "Ищу GGUF-репозитории…"
                                    HuggingFaceGgufBrowser.searchGgufRepos(hfQuery).fold(
                                        onSuccess = { repos ->
                                            hfRepos = repos
                                            hfStatus = "Найдено репозиториев: ${repos.size}"
                                        },
                                        onFailure = { err ->
                                            hfStatus = "Ошибка поиска: ${err.message ?: err.javaClass.simpleName}"
                                        },
                                    )
                                    hfBusy = false
                                }
                            },
                            enabled = !hfBusy && hfQuery.isNotBlank(),
                        ) { Text("Найти") }
                    }
                    hfStatus?.let {
                        Text(text = it, color = palette.primaryAccent, style = MaterialTheme.typography.labelSmall)
                    }
                    hfRepos.forEach { repo ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = palette.bgBottom.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(repo.repoId, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                        Text("↓ ${repo.downloads} • ♥ ${repo.likes}", color = palette.textSecondary, style = MaterialTheme.typography.labelSmall)
                                    }
                                    JarvisOutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                hfBusy = true
                                                hfSelectedRepo = repo.repoId
                                                hfStatus = "Загружаю список .gguf…"
                                                HuggingFaceGgufBrowser.listGgufFiles(repo.repoId).fold(
                                                    onSuccess = { files ->
                                                        hfFiles = files
                                                        hfStatus = "${repo.repoId}: ${files.size} .gguf файлов"
                                                    },
                                                    onFailure = { err ->
                                                        hfFiles = emptyList()
                                                        hfStatus = "Ошибка файлов: ${err.message}"
                                                    },
                                                )
                                                hfBusy = false
                                            }
                                        },
                                        enabled = !hfBusy,
                                    ) { Text("Файлы") }
                                }
                                if (hfSelectedRepo == repo.repoId && hfFiles.isNotEmpty()) {
                                    hfFiles.take(8).forEach { file ->
                                        val sizeLabel = if (file.sizeBytes > 0L) {
                                            "%.0f МБ".format(Locale.US, file.sizeBytes / (1024f * 1024f))
                                        } else "?"
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(file.fileName, color = palette.textPrimary, style = MaterialTheme.typography.labelMedium)
                                                Text(sizeLabel, color = palette.textSecondary, style = MaterialTheme.typography.labelSmall)
                                            }
                                            JarvisOutlinedButton(
                                                onClick = { onDownloadHfGguf(file.downloadUrl, file.fileName, file.sizeBytes) },
                                                enabled = !downloadState.active,
                                            ) { Text("Скачать") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Russian Piper TTS Voice — Compact Dropdown Picker!
        val selectedVoiceObj = voiceCatalog.firstOrNull { it.id == status.selectedVoiceId } ?: voiceCatalog.firstOrNull()
        var inspectedVoiceId by remember(status.selectedVoiceId) { mutableStateOf(selectedVoiceObj?.id) }
        val inspectedVoice = voiceCatalog.firstOrNull { it.id == inspectedVoiceId } ?: selectedVoiceObj

        HudGlassCard(tileShape = tileShape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Русский голос (Piper TTS)", color = palette.textPrimary, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Сейчас: ${status.speakerLabel}",
                        color = if (status.ttsVoiceReady) palette.successAccent else palette.secondaryAccent,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                JarvisOutlinedButton(onClick = onTestVoice) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = palette.primaryAccent)
                    Spacer(Modifier.width(4.dp))
                    Text("Тест")
                }
            }

            if (inspectedVoice != null) {
                val vInstalled = inspectedVoice.id in status.readyTtsVoiceIds
                val vSelected = vInstalled && status.selectedVoiceId == inspectedVoice.id

                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = { showVoiceDropdown = true },
                        shape = RoundedCornerShape(12.dp),
                        color = palette.tileBgAlt,
                        border = BorderStroke(1.dp, palette.tileBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Выбор голоса", color = palette.textSecondary, style = MaterialTheme.typography.labelSmall)
                                Text(inspectedVoice.title, color = palette.textPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = palette.primaryAccent)
                        }
                    }
                    DropdownMenu(
                        expanded = showVoiceDropdown,
                        onDismissRequest = { showVoiceDropdown = false },
                    ) {
                        voiceCatalog.forEach { v ->
                            val ready = v.id in status.readyTtsVoiceIds
                            DropdownMenuItem(
                                text = {
                                    Text(if (ready) "✓ ${v.title}" else v.title)
                                },
                                onClick = {
                                    showVoiceDropdown = false
                                    inspectedVoiceId = v.id
                                    if (ready) onSelectVoice(v.id)
                                },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (vInstalled) {
                        FilterChip(
                            selected = vSelected,
                            onClick = { onSelectVoice(inspectedVoice.id) },
                            label = { Text(if (vSelected) "✓ Активен" else "Активировать") },
                        )
                        IconButton(onClick = { onDeleteVoice(inspectedVoice.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить голос", tint = palette.errorAccent)
                        }
                    } else {
                        JarvisOutlinedButton(
                            onClick = { onDownloadVoice(inspectedVoice.id) },
                            enabled = !downloadState.active,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Скачать голос: ${inspectedVoice.title}") }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(0.75f to "0.75x", 0.85f to "0.85x (Джарвис)", 0.95f to "0.95x", 1.05f to "1.05x").forEach { (sp, label) ->
                    FilterChip(
                        selected = kotlin.math.abs(status.speechSpeed - sp) < 0.04f,
                        onClick = { onSetSpeechSpeed(sp) },
                        label = { Text(label) },
                    )
                }
            }
        }

        // 4. Hearing (ASR & VAD — collapsed accordion)
        HudGlassCard(tileShape = tileShape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { speechAsrExpanded = !speechAsrExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Слух и распознавание речи (ASR + VAD)", color = palette.textPrimary, fontWeight = FontWeight.Bold)
                    Text(
                        text = "ASR: ${if (status.asrReady) "✓ готов" else "не скачан"} • VAD: ${if (status.vadReady) "✓ готов" else "не скачан"}",
                        color = if (status.asrReady) palette.successAccent else palette.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Icon(if (speechAsrExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = palette.textSecondary)
            }
            AnimatedVisibility(visible = speechAsrExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    if (!status.asrReady) {
                        JarvisOutlinedButton(onClick = onDownloadAsr, enabled = !downloadState.active, modifier = Modifier.fillMaxWidth()) {
                            Text("Скачать Sherpa-ONNX Русский ASR")
                        }
                    }
                    if (!status.vadReady) {
                        JarvisOutlinedButton(onClick = onDownloadVad, enabled = !downloadState.active, modifier = Modifier.fillMaxWidth()) {
                            Text("Скачать Silero VAD (2 МБ)")
                        }
                    }
                }
            }
        }

        // 5. External AI / PC Ollama (collapsed accordion)
        HudGlassCard(tileShape = tileShape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { externalExpanded = !externalExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Свой сервер / ПК Ollama / Gemini API", color = palette.textPrimary, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (extEnabled) "Включён (${extModel.ifBlank { extMode.name }})" else "Выключен (полный офлайн)",
                        color = if (extEnabled) palette.successAccent else palette.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Switch(checked = extEnabled, onCheckedChange = { extEnabled = it; externalExpanded = true })
            }
            AnimatedVisibility(visible = externalExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
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
                            label = { Text("URL Chat Completions (https://... или http://127.0.0.1...)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = extModel,
                        onValueChange = { extModel = it },
                        label = { Text(if (extMode == ExternalAiMode.GEMINI) "Модель (напр. gemini-2.0-flash)" else "Модель (напр. jarvis-dolphin3-15b-soup)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = extKey,
                        onValueChange = { extKey = it },
                        label = { Text("API-ключ (локально)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    JarvisButton(
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
                    ) { Text("Сохранить настройки внешнего ИИ") }
                }
            }
        }

        // 6. OPPO ColorOS RAM Tip
        HudGlassCard(tileShape = tileShape) {
            Text("Совет для OPPO Find X6 Pro (ColorOS)", color = palette.primaryAccent, fontWeight = FontWeight.Bold)
            Text(
                text = "Для быстрой автономной работы лучше всего подходят Dolphin 3.0 1.5B (~940 МБ — 1.5 ГБ) и Qwen/Dolphin 3B (~1.8 ГБ). Закрепите карточку Jarvis в списке недавних приложений замком.",
                color = palette.textSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun HudGlassCard(
    tileShape: androidx.compose.ui.graphics.Shape,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val palette = LocalJarvisPalette.current
    Surface(
        shape = tileShape,
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(palette.primaryAccent.copy(alpha = 0.55f), palette.secondaryAccent.copy(alpha = 0.30f)),
            ),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(colors = listOf(palette.tileBgAlt, palette.tileBg)))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}
