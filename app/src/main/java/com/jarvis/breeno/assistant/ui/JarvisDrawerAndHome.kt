package com.jarvis.breeno.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.breeno.assistant.DownloadTaskState
import com.jarvis.breeno.assistant.core.JarvisOrbMode
import com.jarvis.breeno.assistant.memory.ChatSession
import com.jarvis.breeno.assistant.memory.DialogueMessage
import com.jarvis.breeno.assistant.memory.JarvisMemoryRepository
import com.jarvis.breeno.assistant.ui.theme.JarvisGlassTile
import com.jarvis.breeno.assistant.ui.theme.JarvisHudChip
import com.jarvis.breeno.assistant.ui.theme.LocalJarvisPalette
import com.jarvis.breeno.assistant.ui.theme.jarvisScreenBackground
import com.jarvis.breeno.assistant.ui.theme.jarvisTileShape

enum class JarvisSection(val title: String) {
    HOME("Ядро и чат"),
    DOCUMENTS("Документы (RAG)"),
    STUDIO("Студия ИИ"),
    SAVE_VIDEO("SaveVideo"),
    MEMORY("Память Джарвиса"),
    TOOLS_MCP("Инструменты и MCP"),
    CONTROL("Центр управления"),
    SETTINGS("Настройки"),
    MODELS("Модели и движки ИИ"),
}

private val PROMPT_IDEAS_SETS = listOf(
    listOf(
        "Поставь таймер на 5 минут",
        "Сколько времени",
        "Нарисуй футуристический город ночью",
    ),
    listOf(
        "Посчитай (1250 + 350) * 0.15",
        "Что ты помнишь обо мне?",
        "Найди в интернете курс биткоина сегодня",
    ),
    listOf(
        "Покажи статус системы",
        "Включи фонарик",
        "Переведи 42 км в мили",
    ),
)

@Composable
fun JarvisNavigationDrawerSheet(
    currentSection: JarvisSection,
    activeSessionId: String,
    sessions: List<ChatSession>,
    stackStatus: AiStackStatus,
    documentsCount: Int,
    factsCount: Int,
    videosInQueue: Int,
    onSelectSection: (JarvisSection) -> Unit,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
) {
    val palette = LocalJarvisPalette.current
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredSessions = remember(sessions, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) sessions else sessions.filter { it.title.lowercase().contains(q) }
    }
    val groupedSessions = remember(filteredSessions) {
        JarvisMemoryRepository.groupSessionsByBucket(filteredSessions)
    }

    ModalDrawerSheet(
        drawerContainerColor = palette.drawerBg,
        drawerContentColor = palette.textPrimary,
        modifier = Modifier.widthIn(max = 316.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            // Drawer Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(palette.primaryAccent, palette.secondaryAccent.copy(alpha = 0.45f), palette.bgTop),
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("J", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Jarvis Ai",
                            color = palette.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Text(
                            text = stackStatus.activeLlmTitle ?: if (stackStatus.externalAiConfig.enabled) {
                                "Внешний ИИ"
                            } else {
                                "Офлайн ядро"
                            },
                            color = palette.primaryAccent,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = { onSelectSection(JarvisSection.SETTINGS) }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Настройки из меню",
                        tint = palette.primaryAccent,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Primary Capabilities Modules
            DrawerModuleRow(
                icon = Icons.Default.AutoAwesome,
                title = "Ядро и чат",
                selected = currentSection == JarvisSection.HOME,
                onClick = { onSelectSection(JarvisSection.HOME) },
            )
            DrawerModuleRow(
                icon = Icons.Default.Description,
                title = "Документы (RAG)",
                badgeText = if (documentsCount > 0) documentsCount.toString() else null,
                selected = currentSection == JarvisSection.DOCUMENTS,
                onClick = { onSelectSection(JarvisSection.DOCUMENTS) },
            )
            DrawerModuleRow(
                icon = Icons.Default.Image,
                title = "Студия ИИ (Фото и Видео)",
                selected = currentSection == JarvisSection.STUDIO,
                onClick = { onSelectSection(JarvisSection.STUDIO) },
            )
            DrawerModuleRow(
                icon = Icons.Default.Download,
                title = "SaveVideo (Загрузка)",
                badgeText = if (videosInQueue > 0) videosInQueue.toString() else null,
                selected = currentSection == JarvisSection.SAVE_VIDEO,
                onClick = { onSelectSection(JarvisSection.SAVE_VIDEO) },
            )
            DrawerModuleRow(
                icon = Icons.Default.Psychology,
                title = "Память Джарвиса",
                badgeText = if (factsCount > 0) factsCount.toString() else null,
                selected = currentSection == JarvisSection.MEMORY,
                onClick = { onSelectSection(JarvisSection.MEMORY) },
            )
            DrawerModuleRow(
                icon = Icons.Default.Build,
                title = "Инструменты и MCP",
                selected = currentSection == JarvisSection.TOOLS_MCP,
                onClick = { onSelectSection(JarvisSection.TOOLS_MCP) },
            )
            DrawerModuleRow(
                icon = Icons.Default.Dashboard,
                title = "Центр управления",
                selected = currentSection == JarvisSection.CONTROL,
                onClick = { onSelectSection(JarvisSection.CONTROL) },
            )

            HorizontalDivider(
                color = palette.tileBorder.copy(alpha = 0.35f),
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Text(
                text = "Список диалогов",
                color = palette.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )

            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск по чатам…", fontSize = 13.sp) },
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Очистить", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (groupedSessions.isEmpty()) {
                    item {
                        Text(
                            text = "История диалогов пуста",
                            color = palette.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                        )
                    }
                } else {
                    groupedSessions.forEach { (bucket, itemsInBucket) ->
                        item(key = "bucket_${bucket.name}") {
                            Text(
                                text = bucket.title,
                                color = palette.textSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
                            )
                        }
                        items(itemsInBucket, key = { it.id }) { session ->
                            val isActive = session.id == activeSessionId && currentSection == JarvisSection.HOME
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isActive) palette.tileBgAlt else Color.Transparent)
                                    .clickable { onSelectSession(session.id) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = session.title,
                                    color = if (isActive) palette.primaryAccent else palette.textPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (sessions.size > 1 || session.messageCount > 0) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Удалить диалог",
                                        tint = palette.textSecondary.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { onDeleteSession(session.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = palette.tileBg,
                    border = BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.5f)),
                    modifier = Modifier.size(44.dp),
                    onClick = { showSearch = !showSearch },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Поиск диалогов",
                            tint = if (showSearch) palette.primaryAccent else palette.textPrimary,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = palette.tileBg,
                    border = BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    onClick = onNewSession,
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = palette.primaryAccent,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Новый диалог",
                            color = palette.textPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerModuleRow(
    icon: ImageVector,
    title: String,
    badgeText: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalJarvisPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(jarvisTileShape(palette, 10.dp))
            .background(if (selected) palette.tileBgAlt else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) palette.primaryAccent else palette.textPrimary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            color = if (selected) palette.primaryAccent else palette.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (!badgeText.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = palette.primaryAccent.copy(alpha = 0.16f),
            ) {
                Text(
                    text = badgeText,
                    color = palette.primaryAccent,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisHomeScreen(
    orbMode: JarvisOrbMode,
    audioRms: Float,
    listening: Boolean,
    speaking: Boolean,
    busy: Boolean,
    muted: Boolean,
    continuousDialogue: Boolean,
    status: String,
    streamingReply: String?,
    generationWarning: String?,
    dialogueFeed: List<DialogueMessage>,
    stackStatus: AiStackStatus,
    downloadState: DownloadTaskState,
    activeTasksCount: Int,
    onOpenDrawer: () -> Unit,
    onOrbClick: () -> Unit,
    onSendText: (String) -> Unit,
    onSpeakText: (String) -> Unit,
    onStopWork: () -> Unit,
    onNewDialogue: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleContinuous: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenSettings: () -> Unit,
    onAttachDocument: () -> Unit,
    onOpenSection: (JarvisSection) -> Unit,
    onRegenerateLast: () -> Unit = {},
    onExportDialogue: () -> Unit = {},
) {
    val palette = LocalJarvisPalette.current
    var textQuery by remember { mutableStateOf("") }
    var promptIdeaSetIndex by remember { mutableIntStateOf(0) }
    var showToolsSheet by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val chatListState = rememberLazyListState()

    LaunchedEffect(dialogueFeed.size, streamingReply) {
        val total = dialogueFeed.size + if (!streamingReply.isNullOrBlank()) 1 else 0
        if (total > 0) {
            chatListState.animateScrollToItem(total - 1)
        }
    }

    val activeLlm = stackStatus.activeLlmId
    val brainBadge = when {
        activeLlm?.startsWith("l3-8b-stheno") == true -> "STHENO 8B"
        activeLlm?.startsWith("dolphin") == true -> "DOLPHIN ОФЛАЙН"
        activeLlm?.startsWith("hermes") == true -> "HERMES ОФЛАЙН"
        activeLlm?.startsWith("custom_") == true -> "СВОЙ GGUF"
        activeLlm != null -> "QWEN ОФЛАЙН"
        stackStatus.externalAiConfig.enabled -> "ВНЕШНИЙ ИИ"
        else -> "МОДЕЛИ ИИ"
    }

    val orbTitle = when {
        listening -> "Я СЛУШАЮ"
        speaking -> "Я ОТВЕЧАЮ"
        orbMode == JarvisOrbMode.ERROR -> "ОШИБКА ЯДРА"
        orbMode == JarvisOrbMode.SEARCHING -> "ПОИСК В СЕТИ"
        orbMode == JarvisOrbMode.EXECUTING -> "ВЫПОЛНЯЮ"
        busy -> "Я ДУМАЮ"
        else -> "Я РЯДОМ"
    }
    val orbHint = if (busy || listening) {
        "КОСНИТЕСЬ ЯДРА, ЧТОБЫ ОСТАНОВИТЬ"
    } else {
        "КОСНИТЕСЬ ЯДРА, ЧТОБЫ ГОВОРИТЬ"
    }
    val resolvedOrbMode = when {
        listening -> JarvisOrbMode.LISTENING
        speaking -> JarvisOrbMode.SPEAKING
        orbMode != JarvisOrbMode.IDLE -> orbMode
        busy -> JarvisOrbMode.THINKING
        else -> JarvisOrbMode.IDLE
    }

    if (showToolsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showToolsSheet = false },
            containerColor = palette.drawerBg,
            contentColor = palette.textPrimary,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Модули Jarvis Ai",
                    color = palette.primaryAccent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JarvisGlassTile(
                        title = "Документ RAG",
                        subtitle = "TXT, MD, PDF",
                        icon = Icons.Default.AttachFile,
                        accent = palette.primaryAccent,
                        onClick = { showToolsSheet = false; onAttachDocument() },
                        modifier = Modifier.weight(1f),
                    )
                    JarvisGlassTile(
                        title = "Студия ИИ",
                        subtitle = "Фото и видео",
                        icon = Icons.Default.Image,
                        accent = palette.secondaryAccent,
                        onClick = { showToolsSheet = false; onOpenSection(JarvisSection.STUDIO) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JarvisGlassTile(
                        title = "Инструменты",
                        subtitle = "Поиск, URL, MCP",
                        icon = Icons.Default.Build,
                        accent = palette.successAccent,
                        onClick = { showToolsSheet = false; onOpenSection(JarvisSection.TOOLS_MCP) },
                        modifier = Modifier.weight(1f),
                    )
                    JarvisGlassTile(
                        title = "SaveVideo",
                        subtitle = "Очередь медиа",
                        icon = Icons.Default.Download,
                        accent = palette.primaryAccent,
                        onClick = { showToolsSheet = false; onOpenSection(JarvisSection.SAVE_VIDEO) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JarvisGlassTile(
                        title = "Память",
                        subtitle = "SecretGuard",
                        icon = Icons.Default.Psychology,
                        accent = palette.secondaryAccent,
                        onClick = { showToolsSheet = false; onOpenSection(JarvisSection.MEMORY) },
                        modifier = Modifier.weight(1f),
                    )
                    JarvisGlassTile(
                        title = "Модели ИИ",
                        subtitle = "GGUF и голоса",
                        icon = Icons.Default.AutoAwesome,
                        accent = palette.primaryAccent,
                        onClick = { showToolsSheet = false; onOpenModels() },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    val hasMessages = dialogueFeed.isNotEmpty() || !streamingReply.isNullOrBlank()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(jarvisScreenBackground(palette))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val compact = maxHeight < 660.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Меню возможностей",
                            tint = palette.textPrimary,
                        )
                    }
                    Surface(
                        shape = jarvisTileShape(palette, 12.dp),
                        color = palette.tileBg,
                        border = BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.65f)),
                        onClick = onOpenModels,
                    ) {
                        Text(
                            text = if (activeTasksCount > 0) "ЗАДАЧ: $activeTasksCount • $brainBadge" else brainBadge,
                            color = if (stackStatus.activeLlmId != null || stackStatus.externalAiConfig.enabled) {
                                palette.successAccent
                            } else {
                                palette.secondaryAccent
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasMessages) {
                        IconButton(onClick = onExportDialogue) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Экспорт диалога",
                                tint = palette.textSecondary,
                            )
                        }
                    }
                    IconButton(onClick = onToggleContinuous) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = if (continuousDialogue) "Выключить живой диалог" else "Включить живой диалог",
                            tint = if (continuousDialogue) palette.secondaryAccent else palette.textSecondary,
                        )
                    }
                    IconButton(onClick = onToggleMute) {
                        Icon(
                            imageVector = if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (muted) "Включить звук" else "Выключить звук",
                            tint = if (muted) palette.errorAccent else palette.textSecondary,
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Настройки",
                            tint = palette.textPrimary,
                        )
                    }
                }
            }

            if (downloadState.active) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenModels)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${downloadState.label} (${downloadState.percent}%)",
                        color = palette.primaryAccent,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LinearProgressIndicator(
                        progress = { (downloadState.percent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = palette.primaryAccent,
                    )
                }
            }

            if (!hasMessages) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier.clickable(
                                onClickLabel = if (busy || listening) "Остановить Джарвиса" else "Активировать Джарвиса",
                                onClick = onOrbClick,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            JarvisInteractiveOrb(
                                mode = resolvedOrbMode,
                                audioRms = audioRms,
                                orbSize = if (compact) 136.dp else 176.dp,
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = orbTitle,
                            color = if (listening) palette.primaryAccent else if (speaking) palette.secondaryAccent else palette.textPrimary,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = status,
                            color = if (status.startsWith("ОШИБКА") || status.startsWith("РУССКИЙ ГОЛОС")) {
                                palette.errorAccent
                            } else {
                                palette.primaryAccent
                            },
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Text(
                            text = orbHint,
                            color = palette.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 3.dp, bottom = if (compact) 6.dp else 10.dp),
                        )

                        // Prompt ideas
                        val ideas = PROMPT_IDEAS_SETS[promptIdeaSetIndex % PROMPT_IDEAS_SETS.size]
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ideas.forEach { suggestion ->
                                Surface(
                                    shape = jarvisTileShape(palette, 14.dp),
                                    color = palette.tileBg,
                                    border = BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.45f)),
                                    onClick = { onSendText(suggestion) },
                                ) {
                                    Text(
                                        text = suggestion,
                                        color = palette.textPrimary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
                                    )
                                }
                            }
                            TextButton(
                                onClick = { promptIdeaSetIndex = (promptIdeaSetIndex + 1) % PROMPT_IDEAS_SETS.size },
                            ) {
                                Text("Другие идеи", color = palette.textSecondary, style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Сменить идеи",
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        // Compact glass HUD tiles strip (never titled "БЫСТРЫЕ ДЕЙСТВИЯ")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            JarvisHudChip(
                                label = "Веб-поиск",
                                icon = Icons.Default.Public,
                                accent = palette.primaryAccent,
                                onClick = { onOpenSection(JarvisSection.TOOLS_MCP) },
                            )
                            JarvisHudChip(
                                label = "Модели",
                                icon = Icons.Default.AutoAwesome,
                                accent = palette.secondaryAccent,
                                onClick = onOpenModels,
                            )
                            JarvisHudChip(
                                label = "Документы",
                                icon = Icons.Default.Description,
                                accent = palette.successAccent,
                                onClick = { onOpenSection(JarvisSection.DOCUMENTS) },
                            )
                            JarvisHudChip(
                                label = "Студия ИИ",
                                icon = Icons.Default.Image,
                                accent = palette.primaryAccent,
                                onClick = { onOpenSection(JarvisSection.STUDIO) },
                            )
                            JarvisHudChip(
                                label = "Темы HUD",
                                icon = Icons.Default.Palette,
                                accent = palette.secondaryAccent,
                                onClick = onOpenSettings,
                            )
                        }
                    }
                }
            } else {
                // Active Dialogue View
                Surface(
                    shape = jarvisTileShape(palette, 16.dp),
                    color = palette.tileBg,
                    border = BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.clickable(onClick = onOrbClick),
                            contentAlignment = Alignment.Center,
                        ) {
                            JarvisInteractiveOrb(
                                mode = resolvedOrbMode,
                                audioRms = audioRms,
                                orbSize = 54.dp,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = orbTitle,
                                color = if (listening) palette.primaryAccent else palette.textPrimary,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = status,
                                color = palette.primaryAccent,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = orbHint,
                                color = palette.textSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = onNewDialogue) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Новый диалог",
                                tint = palette.textSecondary,
                            )
                        }
                    }
                }

                LazyColumn(
                    state = chatListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(dialogueFeed, key = { _, msg -> msg.id }) { idx, msg ->
                        val isLastAssistant = !busy && idx == dialogueFeed.lastIndex && msg.role != "user"
                        DialogueBubbleRow(
                            role = msg.role,
                            content = msg.content,
                            provider = msg.provider,
                            speedBadge = if (isLastAssistant) stackStatus.lastGenerationSpeed else null,
                            onCopy = { clipboard.setText(AnnotatedString(msg.content)) },
                            onSpeak = { onSpeakText(msg.content) },
                            onRegenerate = if (isLastAssistant) onRegenerateLast else null,
                        )
                    }
                    if (!streamingReply.isNullOrBlank()) {
                        item(key = "streaming_reply") {
                            DialogueBubbleRow(
                                role = "assistant",
                                content = streamingReply,
                                provider = "генерация…",
                                speedBadge = null,
                                onCopy = { clipboard.setText(AnnotatedString(streamingReply)) },
                                onSpeak = null,
                                onRegenerate = null,
                            )
                        }
                    }
                }
            }

            if (!generationWarning.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF332410),
                    border = BorderStroke(1.dp, palette.secondaryAccent.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = generationWarning,
                            color = palette.secondaryAccent,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onStopWork) {
                            Text("Стоп", color = palette.errorAccent)
                        }
                    }
                }
            }

            // Bottom Glass Capsule Input Bar
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = palette.tileBg,
                border = BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.65f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onAttachDocument) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Прикрепить документ",
                            tint = palette.textSecondary,
                        )
                    }

                    TextField(
                        value = textQuery,
                        onValueChange = { textQuery = it },
                        placeholder = {
                            Text(
                                text = "Спросить…",
                                color = palette.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                val q = textQuery.trim()
                                if (q.isNotEmpty()) {
                                    textQuery = ""
                                    onSendText(q)
                                }
                            },
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = palette.primaryAccent,
                        ),
                        modifier = Modifier.weight(1f),
                    )

                    if (busy || listening) {
                        IconButton(onClick = onStopWork) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Остановить",
                                tint = palette.errorAccent,
                            )
                        }
                    } else {
                        IconButton(onClick = onOrbClick) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Голосовой ввод",
                                tint = palette.primaryAccent,
                            )
                        }
                        if (textQuery.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val q = textQuery.trim()
                                    if (q.isNotEmpty()) {
                                        textQuery = ""
                                        onSendText(q)
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Отправить",
                                    tint = palette.primaryAccent,
                                )
                            }
                        } else {
                            IconButton(onClick = { showToolsSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.AddCircleOutline,
                                    contentDescription = "Быстрые возможности",
                                    tint = palette.textPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogueBubbleRow(
    role: String,
    content: String,
    provider: String,
    speedBadge: String?,
    onCopy: () -> Unit,
    onSpeak: (() -> Unit)?,
    onRegenerate: (() -> Unit)?,
) {
    val palette = LocalJarvisPalette.current
    val isUser = role == "user"
    val baseProvider = when (provider) {
        "deterministic" -> "Мгновенный ответ"
        "web-search" -> "Веб-поиск"
        "setup-hint" -> "Подсказка"
        "error" -> "Ошибка"
        "llama-cpp-local" -> "Локальный ИИ"
        "генерация…" -> "Генерация…"
        else -> provider
    }
    val displayProvider = if (!speedBadge.isNullOrBlank() && provider != "deterministic" && provider != "web-search" && provider != "setup-hint" && provider != "error") {
        "$baseProvider • $speedBadge"
    } else {
        baseProvider
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = jarvisTileShape(palette, 15.dp),
            color = if (isUser) palette.bubbleUser else palette.bubbleAssistant,
            border = BorderStroke(
                1.dp,
                if (isUser) palette.primaryAccent.copy(alpha = 0.50f) else palette.tileBorder.copy(alpha = 0.45f),
            ),
            modifier = Modifier.widthIn(max = 315.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                Text(
                    text = content,
                    color = palette.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!isUser) {
                    Spacer(Modifier.height(5.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = displayProvider,
                            color = palette.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Копировать ответ",
                                tint = palette.textSecondary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable(onClick = onCopy),
                            )
                            if (onSpeak != null) {
                                Spacer(Modifier.width(10.dp))
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Озвучить ответ",
                                    tint = palette.primaryAccent,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable(onClick = onSpeak),
                                )
                            }
                            if (onRegenerate != null) {
                                Spacer(Modifier.width(10.dp))
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Повторить ответ",
                                    tint = palette.secondaryAccent,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable(onClick = onRegenerate),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
