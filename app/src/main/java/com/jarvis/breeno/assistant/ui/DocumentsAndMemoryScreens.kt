package com.jarvis.breeno.assistant.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.breeno.assistant.documents.DocumentChunkMatch
import com.jarvis.breeno.assistant.documents.IndexedDocument
import com.jarvis.breeno.assistant.memory.MemoryFact
import com.jarvis.breeno.assistant.tools.JarvisMcpClient
import com.jarvis.breeno.assistant.tools.JarvisWebTools
import com.jarvis.breeno.assistant.tools.McpServerConfig
import com.jarvis.breeno.assistant.tools.McpToolInfo
import com.jarvis.breeno.assistant.tools.PhoneActionIntent
import com.jarvis.breeno.assistant.tools.SettingsSection
import com.jarvis.breeno.assistant.ui.theme.JarvisGlassTile
import com.jarvis.breeno.assistant.ui.theme.LocalJarvisPalette
import com.jarvis.breeno.assistant.ui.theme.jarvisTileShape
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun DocumentsScreen(
    documents: List<IndexedDocument>,
    onImportDocument: () -> Unit,
    onDeleteDocument: (String) -> Unit,
    onSearchDocuments: (String) -> List<DocumentChunkMatch>,
    onAddTextNote: suspend (title: String, rawText: String) -> Result<IndexedDocument> = { _, _ -> Result.failure(IllegalStateException("Не поддерживается")) },
    modifier: Modifier = Modifier,
) {
    val palette = LocalJarvisPalette.current
    val shape = jarvisTileShape(palette, 14.dp)
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var showNoteForm by remember { mutableStateOf(false) }
    var noteTitle by remember { mutableStateOf("") }
    var noteBody by remember { mutableStateOf("") }
    var noteFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val searchResults = remember(searchQuery, documents) {
        if (searchQuery.trim().length >= 2) onSearchDocuments(searchQuery) else emptyList()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "ДОКУМЕНТЫ И БАЗА ЗНАНИЙ (RAG)",
                color = palette.primaryAccent,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Файлы и текстовые заметки индексируются локально на телефоне. Джарвис автоматически находит ответы в ваших материалах.",
                color = palette.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onImportDocument,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primaryAccent, contentColor = Color(0xFF04101B)),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Файл (PDF/TXT)")
                }
                OutlinedButton(
                    onClick = { showNoteForm = !showNoteForm },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = null, tint = palette.secondaryAccent)
                    Spacer(Modifier.width(6.dp))
                    Text("Заметка в RAG")
                }
            }
        }

        if (showNoteForm) {
            item {
                Surface(
                    shape = shape,
                    color = palette.tileBg,
                    border = BorderStroke(1.dp, palette.secondaryAccent.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Вставить текст / конспект в базу знаний", color = palette.secondaryAccent, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = noteTitle,
                            onValueChange = { noteTitle = it },
                            label = { Text("Название заметки (напр.: Инструкция)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = noteBody,
                            onValueChange = { noteBody = it },
                            label = { Text("Текст заметки для локального поиска RAG") },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    onAddTextNote(noteTitle, noteBody).fold(
                                        onSuccess = { doc ->
                                            noteFeedback = true to "Заметка «${doc.name}» добавлена (${doc.chunkCount} фрагм.)"
                                            noteTitle = ""
                                            noteBody = ""
                                        },
                                        onFailure = { err ->
                                            noteFeedback = false to (err.message ?: "Ошибка сохранения")
                                        },
                                    )
                                }
                            },
                            enabled = noteBody.trim().length >= 4,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Проиндексировать заметку") }
                        noteFeedback?.let { (ok, msg) ->
                            Text(msg, color = if (ok) palette.successAccent else palette.errorAccent, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Проверить локальный поиск по документам") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (searchQuery.trim().length >= 2) {
            item {
                Text(
                    text = if (searchResults.isEmpty()) "Совпадений не найдено" else "Найдено фрагментов: ${searchResults.size}",
                    color = palette.secondaryAccent,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            items(searchResults) { match ->
                Surface(
                    shape = shape,
                    color = palette.tileBgAlt,
                    border = BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "${match.documentName} • фрагмент #${match.chunkIndex + 1}",
                            color = palette.primaryAccent,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = match.text.take(360),
                            color = palette.textPrimary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Загруженные документы (${documents.size})",
                color = palette.textPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (documents.isEmpty()) {
            item {
                Surface(
                    shape = shape,
                    color = palette.tileBg,
                    border = BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Документы пока не добавлены. Загрузите файл или нажмите «Заметка в RAG».",
                        color = palette.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        } else {
            items(documents, key = { it.id }) { doc ->
                Surface(
                    shape = shape,
                    color = palette.tileBg,
                    border = BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.40f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = palette.primaryAccent)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = doc.name,
                                color = palette.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${(doc.sizeBytes / 1024).coerceAtLeast(1)} КБ • фрагментов: ${doc.chunkCount}",
                                color = palette.textSecondary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        IconButton(onClick = { onDeleteDocument(doc.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить документ", tint = palette.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryManagerScreen(
    facts: List<MemoryFact>,
    memoryPaused: Boolean,
    lastDeletedFact: MemoryFact?,
    onTogglePaused: (Boolean) -> Unit,
    onAddFact: (key: String, content: String) -> Pair<Boolean, String>,
    onDeleteFact: (Long) -> Unit,
    onUndoDelete: () -> Unit,
    onDismissUndo: () -> Unit,
    onExportJson: () -> String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalJarvisPalette.current
    val shape = jarvisTileShape(palette, 14.dp)
    val context = LocalContext.current
    var newFactText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val filteredFacts = remember(facts, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) facts else facts.filter { it.content.lowercase().contains(q) || it.key.lowercase().contains(q) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "ПАМЯТЬ ДЖАРВИСА",
                color = palette.primaryAccent,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Защита SecretGuard автоматически блокирует сохранение паролей, PIN-кодов, банковских карт и API-ключей.",
                color = palette.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item {
            Surface(
                shape = shape,
                color = palette.tileBg,
                border = BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = palette.successAccent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Пауза записи в память", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                text = if (memoryPaused) "Запись приостановлена" else "Запоминание активно",
                                color = if (memoryPaused) palette.secondaryAccent else palette.successAccent,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Switch(checked = memoryPaused, onCheckedChange = onTogglePaused)
                    }

                    OutlinedButton(
                        onClick = {
                            val json = onExportJson()
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_TEXT, json)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(send, "Экспорт памяти (JSON)"))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Экспортировать память (JSON для Soup / SecretGuard)")
                    }
                }
            }
        }

        item {
            Surface(
                shape = shape,
                color = palette.tileBg,
                border = BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newFactText,
                        onValueChange = { newFactText = it },
                        label = { Text("Добавить факт (напр.: Моя машина — Li Xiang L7)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            val res = onAddFact("manual_${System.currentTimeMillis()}", newFactText)
                            feedback = res
                            if (res.first) newFactText = ""
                        },
                        enabled = newFactText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Сохранить в память")
                    }
                    feedback?.let { (ok, msg) ->
                        Text(
                            text = msg,
                            color = if (ok) palette.successAccent else palette.errorAccent,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }

        if (lastDeletedFact != null) {
            item {
                Surface(
                    shape = shape,
                    color = palette.tileBgAlt,
                    border = BorderStroke(1.dp, palette.primaryAccent.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Факт удалён: ${lastDeletedFact.content.take(28)}",
                            color = palette.textPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Row {
                            TextButton(onClick = onUndoDelete) { Text("Отменить", color = palette.primaryAccent) }
                            TextButton(onClick = onDismissUndo) { Text("×", color = palette.textSecondary) }
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Фильтр фактов (${facts.size})") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        items(filteredFacts, key = { it.id }) { fact ->
            Surface(
                shape = shape,
                color = palette.tileBg,
                border = BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = fact.content,
                        color = palette.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onDeleteFact(fact.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить факт", tint = palette.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun ToolsAndMcpScreen(
    servers: List<McpServerConfig>,
    onSaveServers: (List<McpServerConfig>) -> Unit,
    onExecutePhoneAction: (PhoneActionIntent) -> Result<String>,
    onGetDeviceDiagnostics: () -> String = { "Диагностика доступна" },
    modifier: Modifier = Modifier,
) {
    val palette = LocalJarvisPalette.current
    val shape = jarvisTileShape(palette, 14.dp)
    val scope = rememberCoroutineScope()
    var actionFeedback by remember { mutableStateOf<String?>(null) }
    var quickInput by remember { mutableStateOf("") }
    var quickBusy by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }

    var showMcpForm by remember { mutableStateOf(false) }
    var serverName by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var serverKey by remember { mutableStateOf("") }
    var mcpToolsByServerId by remember { mutableStateOf<Map<String, List<McpToolInfo>>>(emptyMap()) }
    var mcpFeedback by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "ИНСТРУМЕНТЫ И MCP-СЕРВЕРЫ",
                color = palette.primaryAccent,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Рабочие инструменты (поиск в сети, чтение URL, расчёты/конвертер, управление телефоном и HTTP MCP) — всё бесплатно и без Pro-заглушек.",
                color = palette.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // Stylish 2-column Glass Tiles for working Tools
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JarvisGlassTile(
                        title = "Веб-поиск",
                        subtitle = "DuckDuckGo + Wiki",
                        icon = Icons.Default.Public,
                        accent = palette.primaryAccent,
                        badge = "0 ₽",
                        onClick = {
                            val q = quickInput.trim().ifBlank { "Новости технологий ИИ" }
                            quickBusy = true
                            scope.launch {
                                actionFeedback = JarvisWebTools.searchWeb(q, maxResults = 3).fold(
                                    onSuccess = { hits ->
                                        if (hits.isEmpty()) "Ничего не найдено по «$q»"
                                        else hits.joinToString("\n\n") { "${it.title}\n${it.url}\n${it.snippet}" }
                                    },
                                    onFailure = { "Ошибка веб-поиска: ${it.message}" },
                                )
                                quickBusy = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    JarvisGlassTile(
                        title = "Чтение URL",
                        subtitle = "Текст страницы",
                        icon = Icons.Default.Link,
                        accent = palette.secondaryAccent,
                        badge = "HTTPS",
                        onClick = {
                            val url = quickInput.trim().ifBlank { "https://ru.wikipedia.org/wiki/Искусственный_интеллект" }
                            quickBusy = true
                            scope.launch {
                                actionFeedback = JarvisWebTools.readWebPage(url, maxChars = 900).fold(
                                    onSuccess = { page -> "${page.title}\n${page.url}\n\n${page.text}" },
                                    onFailure = { "Ошибка чтения URL: ${it.message}" },
                                )
                                quickBusy = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JarvisGlassTile(
                        title = if (torchOn) "Фонарик: ВКЛ" else "Фонарик",
                        subtitle = "Камера вспышка",
                        icon = Icons.Default.FlashlightOn,
                        accent = palette.secondaryAccent,
                        onClick = {
                            val next = !torchOn
                            onExecutePhoneAction(PhoneActionIntent.ToggleFlashlight(next)).fold(
                                onSuccess = {
                                    torchOn = next
                                    actionFeedback = it
                                },
                                onFailure = { actionFeedback = it.message ?: "Ошибка фонарика" },
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    JarvisGlassTile(
                        title = "Таймер 5 мин",
                        subtitle = "Системные часы",
                        icon = Icons.Default.Timer,
                        accent = palette.primaryAccent,
                        onClick = {
                            actionFeedback = onExecutePhoneAction(PhoneActionIntent.SetTimer(300, "Джарвис 5 мин"))
                                .fold({ it }, { it.message ?: "Ошибка" })
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JarvisGlassTile(
                        title = "Диагностика",
                        subtitle = "RAM, заряд, нагрев",
                        icon = Icons.Default.BatteryChargingFull,
                        accent = palette.successAccent,
                        onClick = { actionFeedback = onGetDeviceDiagnostics() },
                        modifier = Modifier.weight(1f),
                    )
                    JarvisGlassTile(
                        title = "Настройки Wi-Fi",
                        subtitle = "Быстрый переход",
                        icon = Icons.Default.Wifi,
                        accent = palette.primaryAccent,
                        onClick = {
                            actionFeedback = onExecutePhoneAction(PhoneActionIntent.OpenSettings(SettingsSection.WIFI))
                                .fold({ it }, { it.message ?: "Ошибка" })
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Quick Tool Sandbox Bar (search / URL / math / unit converter)
        item {
            Surface(
                shape = shape,
                color = Color.Transparent,
                border = BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.50f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .background(Brush.verticalGradient(listOf(palette.tileBgAlt, palette.tileBg)))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Проверка веб-инструментов и конвертера", color = palette.textPrimary, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = quickInput,
                        onValueChange = { quickInput = it },
                        label = { Text("Запрос, URL (https://...), 42 км в мили или формула") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val src = quickInput.trim().ifBlank { "100 км в мили" }
                                val conv = JarvisWebTools.convertUnitsOffline(src)
                                val calc = JarvisWebTools.convertUnitsOffline(src)
                                    ?: com.jarvis.breeno.assistant.tools.JarvisPhoneActions.parseCommand("посчитай $src")?.let { act ->
                                        onExecutePhoneAction(act).getOrNull()
                                    }
                                actionFeedback = conv ?: calc ?: "Не удалось распознать выражение или конвертацию"
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Расчёт / Ед.")
                        }
                        Button(
                            onClick = {
                                val raw = quickInput.trim()
                                if (raw.isEmpty()) return@Button
                                quickBusy = true
                                scope.launch {
                                    actionFeedback = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) {
                                        JarvisWebTools.readWebPage(raw, maxChars = 1000).fold(
                                            onSuccess = { p -> "${p.title}\n${p.url}\n\n${p.text}" },
                                            onFailure = { "Ошибка: ${it.message}" },
                                        )
                                    } else {
                                        JarvisWebTools.searchWeb(raw, maxResults = 3).fold(
                                            onSuccess = { hits ->
                                                if (hits.isEmpty()) "Совпадений не найдено"
                                                else hits.joinToString("\n\n") { "${it.title}\n${it.url}\n${it.snippet}" }
                                            },
                                            onFailure = { "Ошибка: ${it.message}" },
                                        )
                                    }
                                    quickBusy = false
                                }
                            },
                            enabled = !quickBusy && quickInput.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (quickBusy) "Выполняю…" else "Найти / Открыть")
                        }
                    }
                    actionFeedback?.let { res ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = palette.bgBottom.copy(alpha = 0.72f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = res,
                                color = palette.primaryAccent,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }
        }

        // HTTP MCP Servers (collapsible)
        item {
            OutlinedButton(
                onClick = { showMcpForm = !showMcpForm },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Build, contentDescription = null, tint = palette.secondaryAccent)
                Spacer(Modifier.width(6.dp))
                Text(if (showMcpForm) "Скрыть добавление HTTP MCP-сервера" else "Подключить внешний HTTP MCP-сервер (${servers.size})")
            }
        }

        item {
            AnimatedVisibility(visible = showMcpForm) {
                Surface(
                    shape = shape,
                    color = palette.tileBg,
                    border = BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Добавить HTTP MCP-сервер (JSON-RPC 2.0)", color = palette.textPrimary, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = serverName,
                            onValueChange = { serverName = it },
                            label = { Text("Название (напр. Home Assistant / PC MCP)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("Endpoint URL (http:// или https://)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = serverKey,
                            onValueChange = { serverKey = it },
                            label = { Text("Bearer токен (необязательно)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                val validUrl = JarvisMcpClient.validateMcpEndpointUrl(serverUrl)
                                if (validUrl == null) {
                                    mcpFeedback = "Некорректный URL: требуется http:// или https://"
                                } else {
                                    val next = servers + McpServerConfig(
                                        id = "mcp_${UUID.randomUUID().toString().take(8)}",
                                        name = serverName.trim().ifBlank { "MCP-сервер" },
                                        url = validUrl,
                                        apiKey = serverKey.trim(),
                                        enabled = true,
                                    )
                                    onSaveServers(next)
                                    serverName = ""
                                    serverUrl = ""
                                    serverKey = ""
                                    mcpFeedback = "MCP-сервер добавлен"
                                }
                            },
                            enabled = serverUrl.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Сохранить MCP-сервер")
                        }
                        mcpFeedback?.let {
                            Text(text = it, color = palette.secondaryAccent, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        items(servers, key = { it.id }) { srv ->
            Surface(
                shape = shape,
                color = palette.tileBg,
                border = BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.40f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(srv.name, color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                            Text(srv.url, color = palette.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = srv.enabled,
                                onCheckedChange = { enabled ->
                                    onSaveServers(servers.map { if (it.id == srv.id) it.copy(enabled = enabled) else it })
                                },
                            )
                            IconButton(onClick = {
                                onSaveServers(servers.filterNot { it.id == srv.id })
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить MCP", tint = palette.textSecondary)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                mcpFeedback = "Проверяю ${srv.name} (tools/list)…"
                                val result = JarvisMcpClient.listTools(srv)
                                result.fold(
                                    onSuccess = { tools ->
                                        mcpToolsByServerId = mcpToolsByServerId + (srv.id to tools)
                                        mcpFeedback = "${srv.name}: найдено инструментов — ${tools.size}"
                                    },
                                    onFailure = { err ->
                                        mcpFeedback = "Ошибка ${srv.name}: ${err.message ?: err.javaClass.simpleName}"
                                    },
                                )
                            }
                        }) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Проверить tools/list")
                        }
                    }

                    mcpToolsByServerId[srv.id]?.take(6)?.forEach { tool ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                scope.launch {
                                    val res = JarvisMcpClient.callTool(srv, tool.name)
                                    mcpFeedback = res.fold(
                                        onSuccess = { "${tool.name}: $it" },
                                        onFailure = { "Ошибка ${tool.name}: ${it.message}" },
                                    )
                                }
                            },
                            label = { Text(tool.name) },
                        )
                    }
                }
            }
        }
    }
}
