package com.jarvis.breeno.assistant.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.breeno.assistant.BuildConfig
import com.jarvis.breeno.assistant.core.LlamaNativeBridge
import java.util.Locale

private val SettingsBg = Color(0xFF0A0D14)
private val SettingsCardBg = Color(0xFF171A22)
private val SettingsDivider = Color(0xFF252A36)
private val SettingsCyan = Color(0xFF56E4FF)
private val SettingsText = Color(0xFFF3F6FA)
private val SettingsMuted = Color(0xFF96A1B2)
private val SettingsGreen = Color(0xFF34D399)

/**
 * Structured ColorOS/Breeno-inspired Settings screen (Фото 8) where every row/subgroup
 * is an independent LazyColumn item for reliable scrolling and tapping across all screen sizes.
 */
@Composable
fun JarvisSettingsScreen(
    stackStatus: AiStackStatus,
    continuousDialogue: Boolean,
    muted: Boolean,
    memoryPaused: Boolean,
    factsCount: Int,
    documentsCount: Int,
    mcpCount: Int,
    cacheBytes: Long,
    onBack: () -> Unit,
    onToggleContinuous: (Boolean) -> Unit,
    onToggleMuted: (Boolean) -> Unit,
    onSetSpeechSpeed: (Float) -> Unit,
    onTestVoice: () -> Unit,
    onToggleMemoryPaused: (Boolean) -> Unit,
    onClearCache: () -> Long,
    onNavigateSection: (JarvisSection) -> Unit,
    onImportCustomGguf: () -> Unit,
) {
    val context = LocalContext.current
    var showBanner by remember { mutableStateOf(true) }
    var voiceExpanded by remember { mutableStateOf(false) }
    var asrExpanded by remember { mutableStateOf(false) }
    var modelsExpanded by remember { mutableStateOf(false) }
    var enginesExpanded by remember { mutableStateOf(false) }
    var aboutExpanded by remember { mutableStateOf(false) }
    var currentCacheBytes by remember(cacheBytes) { mutableLongStateOf(cacheBytes) }
    var cacheFeedback by remember { mutableStateOf<String?>(null) }

    fun launchSystemAction(action: String, fallbackAction: String = Settings.ACTION_SETTINGS) {
        val primary = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val launched = runCatching {
            context.startActivity(primary)
            true
        }.getOrDefault(false)
        if (!launched) {
            runCatching {
                context.startActivity(Intent(fallbackAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = SettingsText,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Настройки",
                color = SettingsText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showBanner) {
                item(key = "assistant_banner") {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = SettingsCardBg,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = SettingsMuted,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Назначьте Jarvis Ai системным помощником Android для быстрого вызова кнопкой питания или жестом.",
                                    color = SettingsText,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                TextButton(
                                    onClick = {
                                        launchSystemAction(
                                            Settings.ACTION_VOICE_INPUT_SETTINGS,
                                            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
                                        )
                                    },
                                ) {
                                    Text("Включить", color = SettingsCyan, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            TextButton(onClick = { showBanner = false }) {
                                Text("×", color = SettingsMuted)
                            }
                        }
                    }
                }
            }

            // Section 1: Activation & Voice (each row is its own LazyColumn item)
            item(key = "hdr_activation") {
                SectionHeader("Настройки активации и голоса")
            }
            item(key = "row_continuous") {
                RowCard {
                    SettingsSwitchRow(
                        title = "Непрерывные беседы",
                        subtitle = "После ответа Jarvis Ai автоматически продолжает слушать следующую команду.",
                        checked = continuousDialogue,
                        onCheckedChange = onToggleContinuous,
                    )
                }
            }
            item(key = "row_voice_mute") {
                RowCard {
                    SettingsSwitchRow(
                        title = "Озвучивать ответы голосом",
                        subtitle = "Локальный голос Piper TTS на русском языке.",
                        checked = !muted,
                        onCheckedChange = { enabled -> onToggleMuted(!enabled) },
                    )
                }
            }
            item(key = "row_tts_header") {
                RowCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CollapsibleRowHeader(
                            title = "Голос и скорость (Piper TTS)",
                            summary = "${stackStatus.speakerLabel} • ${"%.2fx".format(Locale.US, stackStatus.speechSpeed)}",
                            expanded = voiceExpanded,
                            onClick = { voiceExpanded = !voiceExpanded },
                        )
                        if (voiceExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    listOf(0.85f to "0.85x", 0.95f to "0.95x", 1.05f to "1.05x", 1.2f to "1.20x").forEach { (sp, label) ->
                                        FilterChip(
                                            selected = kotlin.math.abs(stackStatus.speechSpeed - sp) < 0.04f,
                                            onClick = { onSetSpeechSpeed(sp) },
                                            label = { Text(label) },
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = onTestVoice) {
                                        Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Тест голоса")
                                    }
                                    OutlinedButton(onClick = { onNavigateSection(JarvisSection.MODELS) }) {
                                        Text("Голоса (Дмитрий / Руслан)")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "row_asr_header") {
                RowCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CollapsibleRowHeader(
                            title = "Распознавание речи (Sherpa ASR + VAD)",
                            summary = if (stackStatus.asrReady) "Установлено" else "Требуется загрузка",
                            expanded = asrExpanded,
                            onClick = { asrExpanded = !asrExpanded },
                        )
                        if (asrExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "ASR: ${if (stackStatus.asrReady) "готов (офлайн)" else "не скачан"} • Silero VAD: ${if (stackStatus.vadReady) "готов" else "не скачан"}",
                                    color = if (stackStatus.asrReady) SettingsGreen else SettingsMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                OutlinedButton(onClick = { onNavigateSection(JarvisSection.MODELS) }) {
                                    Text("Открыть загрузку моделей речи")
                                }
                            }
                        }
                    }
                }
            }
            item(key = "row_accessibility") {
                RowCard {
                    SettingsLinkRow(
                        title = "Спецвозможности Android",
                        valueText = "Открыть",
                        onClick = { launchSystemAction(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                    )
                }
            }
            item(key = "row_default_assistant") {
                RowCard {
                    SettingsLinkRow(
                        title = "Ассистент по умолчанию",
                        valueText = "Настроить",
                        onClick = {
                            launchSystemAction(
                                Settings.ACTION_VOICE_INPUT_SETTINGS,
                                Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
                            )
                        },
                    )
                }
            }

            // Section 2: AI Models & Engines (collapsed by default, each child link is an independent item when expanded)
            item(key = "hdr_models") {
                SectionHeader("Модели ИИ и движки")
            }
            item(key = "row_models_group") {
                RowCard {
                    CollapsibleRowHeader(
                        title = "Локальные модели и Hugging Face GGUF",
                        summary = stackStatus.activeLlmTitle ?: "Не выбрана",
                        expanded = modelsExpanded,
                        onClick = { modelsExpanded = !modelsExpanded },
                    )
                }
            }
            if (modelsExpanded) {
                item(key = "row_models_catalog") {
                    RowCard {
                        SettingsLinkRow(
                            title = "Каталог Dolphin / Hermes / Stheno / Qwen",
                            valueText = "${stackStatus.readyLlmIds.size} уст.",
                            onClick = { onNavigateSection(JarvisSection.MODELS) },
                        )
                    }
                }
                item(key = "row_models_hf") {
                    RowCard {
                        SettingsLinkRow(
                            title = "Поиск и загрузка с Hugging Face",
                            valueText = "GGUF",
                            onClick = { onNavigateSection(JarvisSection.MODELS) },
                        )
                    }
                }
                item(key = "row_models_import") {
                    RowCard {
                        SettingsLinkRow(
                            title = "Импортировать свой .gguf с телефона",
                            valueText = "Файл",
                            onClick = onImportCustomGguf,
                        )
                    }
                }
            }
            item(key = "row_engines_group") {
                RowCard {
                    CollapsibleRowHeader(
                        title = "Внешние серверы и Студия ИИ",
                        summary = if (stackStatus.externalAiConfig.enabled) "Вкл." else "Автономно",
                        expanded = enginesExpanded,
                        onClick = { enginesExpanded = !enginesExpanded },
                    )
                }
            }
            if (enginesExpanded) {
                item(key = "row_engines_ext") {
                    RowCard {
                        SettingsLinkRow(
                            title = "Свой сервер / Ollama / Gemini API",
                            valueText = if (stackStatus.externalAiConfig.enabled) "Вкл." else "Выкл.",
                            onClick = { onNavigateSection(JarvisSection.MODELS) },
                        )
                    }
                }
                item(key = "row_engines_studio") {
                    RowCard {
                        SettingsLinkRow(
                            title = "Студия ИИ (Local Dream / ComfyUI / Horde)",
                            valueText = "Открыть",
                            onClick = { onNavigateSection(JarvisSection.STUDIO) },
                        )
                    }
                }
                item(key = "row_engines_mcp") {
                    RowCard {
                        SettingsLinkRow(
                            title = "Инструменты телефона и MCP-серверы",
                            valueText = if (mcpCount > 0) "$mcpCount MCP" else "Открыть",
                            onClick = { onNavigateSection(JarvisSection.TOOLS_MCP) },
                        )
                    }
                }
            }

            // Section 3: Memory, Documents & Privacy
            item(key = "hdr_memory") {
                SectionHeader("Память, документы и приватность")
            }
            item(key = "row_memory_pause") {
                RowCard {
                    SettingsSwitchRow(
                        title = "Пауза записи в память",
                        subtitle = "Временно не сохранять новые факты о вас (SecretGuard всегда активен).",
                        checked = memoryPaused,
                        onCheckedChange = onToggleMemoryPaused,
                    )
                }
            }
            item(key = "row_memory_mgr") {
                RowCard {
                    SettingsLinkRow(
                        title = "Память Джарвиса и SecretGuard",
                        valueText = "$factsCount факт.",
                        onClick = { onNavigateSection(JarvisSection.MEMORY) },
                    )
                }
            }
            item(key = "row_documents_mgr") {
                RowCard {
                    SettingsLinkRow(
                        title = "Документы и База знаний (RAG)",
                        valueText = "$documentsCount файл.",
                        onClick = { onNavigateSection(JarvisSection.DOCUMENTS) },
                    )
                }
            }

            // Section 4: System & About
            item(key = "hdr_other") {
                SectionHeader("Другое")
            }
            item(key = "row_control_center") {
                RowCard {
                    SettingsLinkRow(
                        title = "Центр управления и телеметрия",
                        valueText = "Открыть",
                        onClick = { onNavigateSection(JarvisSection.CONTROL) },
                    )
                }
            }
            item(key = "row_cache_clear") {
                RowCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val freed = onClearCache()
                                currentCacheBytes = 0L
                                cacheFeedback = "Очищено %.2f МБ".format(Locale("ru", "RU"), freed / (1024f * 1024f))
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Кэш приложения", color = SettingsText, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = cacheFeedback ?: "%.2f МБ".format(Locale("ru", "RU"), currentCacheBytes / (1024f * 1024f)),
                                color = SettingsMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text("Очистить", color = SettingsCyan, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            item(key = "row_about") {
                RowCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CollapsibleRowHeader(
                            title = "О системе Jarvis Ai",
                            summary = BuildConfig.VERSION_NAME,
                            expanded = aboutExpanded,
                            onClick = { aboutExpanded = !aboutExpanded },
                        )
                        if (aboutExpanded) {
                            HorizontalDivider(color = SettingsDivider, modifier = Modifier.padding(horizontal = 14.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "Версия: ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})",
                                    color = SettingsText,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = "libjarvis_llama.so: ${if (LlamaNativeBridge.isNativeLibraryLoaded) "активна" else "не загружена"}",
                                    color = if (LlamaNativeBridge.isNativeLibraryLoaded) SettingsGreen else SettingsMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = "Полная автономная версия Jarvis Ai без рекламы, ограничений и платных подписок.",
                                    color = SettingsMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "footer_spacer") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun RowCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SettingsCardBg,
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = SettingsMuted,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = SettingsText, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = SettingsMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsLinkRow(
    title: String,
    valueText: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = SettingsText,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = valueText,
                color = SettingsMuted,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = SettingsMuted,
            )
        }
    }
}

@Composable
private fun CollapsibleRowHeader(
    title: String,
    summary: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = SettingsText,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = summary,
                color = SettingsMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = SettingsMuted,
            )
        }
    }
}
