package com.jarvis.breeno.assistant.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.breeno.assistant.BuildConfig
import com.jarvis.breeno.assistant.core.LlamaNativeBridge
import com.jarvis.breeno.assistant.ui.theme.JarvisAppearanceConfig
import com.jarvis.breeno.assistant.ui.theme.JarvisFontStyle
import com.jarvis.breeno.assistant.ui.theme.JarvisThemePreset
import com.jarvis.breeno.assistant.ui.theme.LocalJarvisPalette
import com.jarvis.breeno.assistant.ui.theme.jarvisScreenBackground
import com.jarvis.breeno.assistant.ui.theme.jarvisTileShape
import com.jarvis.breeno.assistant.ui.theme.paletteForTheme
import java.util.Locale

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
    appearance: JarvisAppearanceConfig = JarvisAppearanceConfig(),
    onUpdateAppearance: (JarvisAppearanceConfig) -> Unit = {},
    webToolsEnabled: Boolean = true,
    onToggleWebTools: (Boolean) -> Unit = {},
) {
    val palette = LocalJarvisPalette.current
    val context = LocalContext.current
    var showBanner by remember { mutableStateOf(true) }
    var voiceExpanded by remember { mutableStateOf(false) }
    var asrExpanded by remember { mutableStateOf(false) }
    var modelsExpanded by remember { mutableStateOf(false) }
    var enginesExpanded by remember { mutableStateOf(false) }
    var appearanceExpanded by remember { mutableStateOf(false) }
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
            .background(jarvisScreenBackground(palette))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.topBarBg)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = palette.textPrimary,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Настройки",
                color = palette.textPrimary,
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
                        shape = jarvisTileShape(palette, 16.dp),
                        color = palette.tileBg,
                        border = BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.55f)),
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
                                tint = palette.primaryAccent,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Назначьте Jarvis Ai системным помощником Android для быстрого вызова кнопкой питания или жестом.",
                                    color = palette.textPrimary,
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
                                    Text("Включить", color = palette.primaryAccent, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            TextButton(onClick = { showBanner = false }) {
                                Text("×", color = palette.textSecondary)
                            }
                        }
                    }
                }
            }

            // Section 1: Activation & Voice
            item(key = "hdr_activation") {
                SectionHeader("Настройки активации и голоса")
            }
            item(key = "row_continuous") {
                SettingsSwitchRow(
                    title = "Непрерывные беседы",
                    subtitle = "После ответа Jarvis Ai автоматически продолжает слушать следующую команду.",
                    checked = continuousDialogue,
                    onCheckedChange = onToggleContinuous,
                )
            }
            item(key = "row_voice_mute") {
                SettingsSwitchRow(
                    title = "Озвучивать ответы голосом",
                    subtitle = "Потоковый локальный голос Piper TTS на русском языке.",
                    checked = !muted,
                    onCheckedChange = { enabled -> onToggleMuted(!enabled) },
                )
            }
            item(key = "row_tts_header") {
                RowCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CollapsibleRowHeader(
                            title = "Голос и скорость (Piper TTS)",
                            summary = "${stackStatus.speakerLabel} • ${"%.2fx".format(Locale.US, stackStatus.speechSpeed)}",
                            expanded = voiceExpanded,
                            onClick = { voiceExpanded = !voiceExpanded },
                            standaloneCard = false,
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
                            standaloneCard = false,
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
                                    color = if (stackStatus.asrReady) palette.successAccent else palette.textSecondary,
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
                SettingsLinkRow(
                    title = "Спецвозможности Android",
                    valueText = "Открыть",
                    onClick = { launchSystemAction(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                )
            }
            item(key = "row_default_assistant") {
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

            // Section 2: AI Models & Engines
            item(key = "hdr_models") {
                SectionHeader("Модели ИИ и движки")
            }
            item(key = "row_models_group") {
                CollapsibleRowHeader(
                    title = "Локальные модели и Hugging Face GGUF",
                    summary = stackStatus.activeLlmTitle ?: "Не выбрана",
                    expanded = modelsExpanded,
                    onClick = { modelsExpanded = !modelsExpanded },
                )
            }
            if (modelsExpanded) {
                item(key = "row_models_catalog") {
                    SettingsLinkRow(
                        title = "Каталог Dolphin / Hermes / Stheno / Qwen",
                        valueText = "${stackStatus.readyLlmIds.size} уст.",
                        onClick = { onNavigateSection(JarvisSection.MODELS) },
                    )
                }
                item(key = "row_models_hf") {
                    SettingsLinkRow(
                        title = "Поиск и загрузка с Hugging Face",
                        valueText = "GGUF",
                        onClick = { onNavigateSection(JarvisSection.MODELS) },
                    )
                }
                item(key = "row_models_import") {
                    SettingsLinkRow(
                        title = "Импортировать свой .gguf с телефона",
                        valueText = "Файл / ZIP",
                        onClick = onImportCustomGguf,
                    )
                }
            }
            item(key = "row_engines_group") {
                CollapsibleRowHeader(
                    title = "Внешние серверы и Студия ИИ",
                    summary = if (stackStatus.externalAiConfig.enabled) "Вкл." else "Автономно",
                    expanded = enginesExpanded,
                    onClick = { enginesExpanded = !enginesExpanded },
                )
            }
            if (enginesExpanded) {
                item(key = "row_engines_ext") {
                    SettingsLinkRow(
                        title = "Свой сервер / Ollama / Gemini API",
                        valueText = if (stackStatus.externalAiConfig.enabled) "Вкл." else "Выкл.",
                        onClick = { onNavigateSection(JarvisSection.MODELS) },
                    )
                }
                item(key = "row_engines_studio") {
                    SettingsLinkRow(
                        title = "Студия ИИ (Local Dream / ComfyUI / Horde)",
                        valueText = "Открыть",
                        onClick = { onNavigateSection(JarvisSection.STUDIO) },
                    )
                }
                item(key = "row_engines_mcp") {
                    SettingsLinkRow(
                        title = "Инструменты телефона и MCP-серверы",
                        valueText = if (mcpCount > 0) "$mcpCount MCP" else "Открыть",
                        onClick = { onNavigateSection(JarvisSection.TOOLS_MCP) },
                    )
                }
            }

            // Section 3: Appearance & Iron Man Themes (collapsed by default so smoke scroll stays intact)
            item(key = "hdr_appearance") {
                SectionHeader("Темы (Железный человек) и шрифт")
            }
            item(key = "row_appearance_group") {
                CollapsibleRowHeader(
                    title = "Оформление, темы HUD и типографика",
                    summary = "${appearance.theme.title} • ${appearance.fontStyle.title}",
                    expanded = appearanceExpanded,
                    onClick = { appearanceExpanded = !appearanceExpanded },
                )
            }
            if (appearanceExpanded) {
                item(key = "row_appearance_body") {
                    RowCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Palette, contentDescription = null, tint = palette.primaryAccent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Выберите тему оформления", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                            }
                            JarvisThemePreset.entries.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    pair.forEach { preset ->
                                        val preview = paletteForTheme(preset)
                                        val active = appearance.theme == preset
                                        Surface(
                                            onClick = { onUpdateAppearance(appearance.copy(theme = preset)) },
                                            shape = jarvisTileShape(preview, 12.dp),
                                            color = Color.Transparent,
                                            border = BorderStroke(
                                                width = if (active) 2.dp else 1.dp,
                                                color = if (active) preview.primaryAccent else preview.tileBorder.copy(alpha = 0.45f),
                                            ),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .background(Brush.verticalGradient(colors = listOf(preview.bgMid, preview.bgTop)))
                                                    .padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Box(Modifier.size(10.dp).clip(CircleShape).background(preview.primaryAccent))
                                                        Box(Modifier.size(10.dp).clip(CircleShape).background(preview.secondaryAccent))
                                                    }
                                                    if (active) {
                                                        Text("✓", color = preview.primaryAccent, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                Text(
                                                    text = preset.title,
                                                    color = preview.textPrimary,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = preset.subtitle,
                                                    color = preview.textSecondary,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }

                            Text("Шрифт интерфейса:", color = palette.textSecondary, style = MaterialTheme.typography.labelSmall)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                JarvisFontStyle.entries.forEach { fontStyle ->
                                    FilterChip(
                                        selected = appearance.fontStyle == fontStyle,
                                        onClick = { onUpdateAppearance(appearance.copy(fontStyle = fontStyle)) },
                                        label = { Text(fontStyle.title) },
                                    )
                                }
                            }

                            Text("Размер текста:", color = palette.textSecondary, style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0.92f to "Компактно", 1.00f to "Стандарт", 1.12f to "Крупнее").forEach { (sc, lbl) ->
                                    FilterChip(
                                        selected = kotlin.math.abs(appearance.fontScale - sc) < 0.03f,
                                        onClick = { onUpdateAppearance(appearance.copy(fontScale = sc)) },
                                        label = { Text(lbl) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 4: Web Tools, Memory, Documents & Privacy
            item(key = "hdr_memory") {
                SectionHeader("Инструменты сети, память и документы")
            }
            item(key = "row_web_tools") {
                SettingsSwitchRow(
                    title = "Веб-поиск и чтение ссылок (бесплатно)",
                    subtitle = "Позволяет Джарвису искать свежие факты в сети и читать URL при наличии интернета (без API-ключей).",
                    checked = webToolsEnabled,
                    onCheckedChange = onToggleWebTools,
                )
            }
            item(key = "row_memory_pause") {
                SettingsSwitchRow(
                    title = "Пауза записи в память",
                    subtitle = "Временно не сохранять новые факты о вас (SecretGuard всегда активен).",
                    checked = memoryPaused,
                    onCheckedChange = onToggleMemoryPaused,
                )
            }
            item(key = "row_memory_mgr") {
                SettingsLinkRow(
                    title = "Память Джарвиса и SecretGuard",
                    valueText = "$factsCount факт.",
                    onClick = { onNavigateSection(JarvisSection.MEMORY) },
                )
            }
            item(key = "row_documents_mgr") {
                SettingsLinkRow(
                    title = "Документы и База знаний (RAG)",
                    valueText = "$documentsCount файл.",
                    onClick = { onNavigateSection(JarvisSection.DOCUMENTS) },
                )
            }

            // Section 5: System & About
            item(key = "hdr_other") {
                SectionHeader("Другое")
            }
            item(key = "row_control_center") {
                SettingsLinkRow(
                    title = "Центр управления и телеметрия",
                    valueText = "Открыть",
                    onClick = { onNavigateSection(JarvisSection.CONTROL) },
                )
            }
            item(key = "row_cache_clear") {
                Surface(
                    onClick = {
                        val freed = onClearCache()
                        currentCacheBytes = 0L
                        cacheFeedback = "Очищено %.2f МБ".format(Locale("ru", "RU"), freed / (1024f * 1024f))
                    },
                    shape = jarvisTileShape(palette, 14.dp),
                    color = palette.tileBg,
                    border = BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Кэш приложения", color = palette.textPrimary, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = cacheFeedback ?: "%.2f МБ".format(Locale("ru", "RU"), currentCacheBytes / (1024f * 1024f)),
                                color = palette.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text("Очистить", color = palette.primaryAccent, style = MaterialTheme.typography.labelLarge)
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
                            standaloneCard = false,
                        )
                        if (aboutExpanded) {
                            HorizontalDivider(color = palette.tileBorder.copy(alpha = 0.35f), modifier = Modifier.padding(horizontal = 14.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "Версия: ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})",
                                    color = palette.textPrimary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = "libjarvis_llama.so: ${if (LlamaNativeBridge.isNativeLibraryLoaded) "активна" else "не загружена"}",
                                    color = if (LlamaNativeBridge.isNativeLibraryLoaded) palette.successAccent else palette.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = "Полная автономная версия Jarvis Ai без рекламы, ограничений и платных подписок.",
                                    color = palette.textSecondary,
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
    val palette = LocalJarvisPalette.current
    Surface(
        shape = jarvisTileShape(palette, 14.dp),
        color = palette.tileBg,
        border = BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.38f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

@Composable
private fun SectionHeader(title: String) {
    val palette = LocalJarvisPalette.current
    Text(
        text = title,
        color = palette.primaryAccent.copy(alpha = 0.90f),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
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
    val palette = LocalJarvisPalette.current
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = jarvisTileShape(palette, 14.dp),
        color = palette.tileBg,
        border = BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.38f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(title, color = palette.textPrimary, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SettingsLinkRow(
    title: String,
    valueText: String,
    onClick: () -> Unit,
) {
    val palette = LocalJarvisPalette.current
    Surface(
        onClick = onClick,
        shape = jarvisTileShape(palette, 14.dp),
        color = palette.tileBg,
        border = BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.38f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = palette.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = valueText,
                    color = palette.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = palette.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun CollapsibleRowHeader(
    title: String,
    summary: String,
    expanded: Boolean,
    onClick: () -> Unit,
    standaloneCard: Boolean = true,
) {
    val palette = LocalJarvisPalette.current
    Surface(
        onClick = onClick,
        shape = jarvisTileShape(palette, 14.dp),
        color = if (standaloneCard) palette.tileBg else Color.Transparent,
        border = if (standaloneCard) BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.38f)) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = palette.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summary,
                    color = palette.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = palette.textSecondary,
                )
            }
        }
    }
}
