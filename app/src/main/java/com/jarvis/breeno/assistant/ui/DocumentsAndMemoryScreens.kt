package com.jarvis.breeno.assistant.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.jarvis.breeno.assistant.tools.McpServerConfig
import com.jarvis.breeno.assistant.tools.McpToolInfo
import com.jarvis.breeno.assistant.tools.PhoneActionIntent
import com.jarvis.breeno.assistant.tools.SettingsSection
import kotlinx.coroutines.launch
import java.util.UUID

private val ModuleCardBg = Color(0xFF171A22)
private val ModuleCyan = Color(0xFF56E4FF)
private val ModuleGreen = Color(0xFF34D399)
private val ModuleRed = Color(0xFFFF8A8A)
private val ModuleGold = Color(0xFFFFB74D)
private val ModuleText = Color(0xFFF3F6FA)
private val ModuleMuted = Color(0xFF96A1B2)

@Composable
fun DocumentsScreen(
    documents: List<IndexedDocument>,
    onImportDocument: () -> Unit,
    onDeleteDocument: (String) -> Unit,
    onSearchDocuments: (String) -> List<DocumentChunkMatch>,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
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
                color = ModuleCyan,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Файлы индексируются локально на устройстве. Jarvis Ai автоматически подставляет нужные фрагменты при ответах.",
                color = ModuleMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item {
            Button(
                onClick = onImportDocument,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Импортировать файл (TXT, MD, JSON, CSV, PDF)")
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
                    color = ModuleGold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            items(searchResults) { match ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2330)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "${match.documentName} • фрагмент #${match.chunkIndex + 1}",
                            color = ModuleCyan,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = match.text.take(360),
                            color = ModuleText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Загруженные документы (${documents.size})",
                color = ModuleText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (documents.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ModuleCardBg),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Документы пока не добавлены. Нажмите «Импортировать файл» или скрепку 📎 в строке ввода на главном экране.",
                        color = ModuleMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        } else {
            items(documents, key = { it.id }) { doc ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = ModuleCardBg),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = ModuleCyan)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = doc.name,
                                color = ModuleText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${(doc.sizeBytes / 1024).coerceAtLeast(1)} КБ • фрагментов: ${doc.chunkCount}",
                                color = ModuleMuted,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        IconButton(onClick = { onDeleteDocument(doc.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить документ", tint = ModuleMuted)
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
                color = ModuleCyan,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Защита SecretGuard автоматически блокирует сохранение паролей, PIN-кодов, банковских карт и API-ключей.",
                color = ModuleMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ModuleCardBg),
                shape = RoundedCornerShape(16.dp),
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
                                Icon(Icons.Default.Security, contentDescription = null, tint = ModuleGreen, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Пауза записи в память", color = ModuleText, fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                text = if (memoryPaused) "Запись приостановлена" else "Запоминание активно",
                                color = if (memoryPaused) ModuleGold else ModuleGreen,
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
                        Text("Экспортировать память (JSON с проверкой SecretGuard)")
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ModuleCardBg),
                shape = RoundedCornerShape(16.dp),
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
                            color = if (ok) ModuleGreen else ModuleRed,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }

        if (lastDeletedFact != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF212B3A),
                    border = BorderStroke(1.dp, ModuleCyan.copy(alpha = 0.5f)),
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
                            color = ModuleText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Row {
                            TextButton(onClick = onUndoDelete) { Text("Отменить", color = ModuleCyan) }
                            TextButton(onClick = onDismissUndo) { Text("×", color = ModuleMuted) }
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
            Card(
                colors = CardDefaults.cardColors(containerColor = ModuleCardBg),
                shape = RoundedCornerShape(14.dp),
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
                        color = ModuleText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onDeleteFact(fact.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить факт", tint = ModuleMuted)
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
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var actionFeedback by remember { mutableStateOf<String?>(null) }
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
                color = ModuleCyan,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Быстрые команды для управления телефоном и подключение внешних инструментов по протоколу HTTP MCP (JSON-RPC 2.0).",
                color = ModuleMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ModuleCardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = ModuleCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Безопасные действия устройства", color = ModuleText, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "Голосовые и текстовые команды: таймер, будильник, фонарик, настройки Wi-Fi / Bluetooth / звука / экрана / батареи, безопасный калькулятор, ссылки https://.",
                        color = ModuleMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = {
                            actionFeedback = onExecutePhoneAction(PhoneActionIntent.ToggleFlashlight(true))
                                .fold({ it }, { it.message ?: "Ошибка" })
                        }) { Text("Фонарик Вкл") }
                        OutlinedButton(onClick = {
                            actionFeedback = onExecutePhoneAction(PhoneActionIntent.ToggleFlashlight(false))
                                .fold({ it }, { it.message ?: "Ошибка" })
                        }) { Text("Фонарик Выкл") }
                        OutlinedButton(onClick = {
                            actionFeedback = onExecutePhoneAction(PhoneActionIntent.OpenSettings(SettingsSection.WIFI))
                                .fold({ it }, { it.message ?: "Ошибка" })
                        }) { Text("Настройки Wi-Fi") }
                        OutlinedButton(onClick = {
                            actionFeedback = onExecutePhoneAction(PhoneActionIntent.SetTimer(300, "Чай"))
                                .fold({ it }, { it.message ?: "Ошибка" })
                        }) { Text("Таймер 5 мин") }
                    }
                    actionFeedback?.let {
                        Text(text = it, color = ModuleCyan, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ModuleCardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Добавить HTTP MCP-сервер", color = ModuleText, fontWeight = FontWeight.Bold)
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
                        Text(text = it, color = ModuleGold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        items(servers, key = { it.id }) { srv ->
            Card(
                colors = CardDefaults.cardColors(containerColor = ModuleCardBg),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(srv.name, color = ModuleText, fontWeight = FontWeight.SemiBold)
                            Text(srv.url, color = ModuleMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                Icon(Icons.Default.Delete, contentDescription = "Удалить MCP", tint = ModuleMuted)
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
