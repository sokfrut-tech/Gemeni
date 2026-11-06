package com.jarvis.breeno.assistant.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvis.breeno.assistant.memory.JarvisMemoryEntry
import com.jarvis.breeno.assistant.memory.JarvisMemoryKind
import com.jarvis.breeno.assistant.memory.JarvisMemoryRepository
import com.jarvis.breeno.assistant.ui.theme.JarvisTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JarvisMemoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JarvisTheme { JarvisMemoryScreen() } }
    }
}

@Composable
private fun JarvisMemoryScreen() {
    val context = LocalContext.current
    val repository = remember { JarvisMemoryRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    var entries by remember { mutableStateOf(emptyList<JarvisMemoryEntry>()) }
    var scope by remember { mutableStateOf("Jarvis") }
    var text by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(JarvisMemoryKind.NOTE) }
    var kindMenuExpanded by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var confirmSave by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    fun reload() {
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) { repository.list() }
            entries = result
        }
    }

    LaunchedEffect(Unit) { reload() }

    val visibleEntries = entries.filter { entry ->
        (scope.isBlank() || entry.scope.equals(scope.trim(), ignoreCase = true)) &&
            (search.isBlank() || entry.text.contains(search.trim(), ignoreCase = true))
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Локальная память", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Записи хранятся на телефоне. Ничего не сохраняется без подтверждения.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = scope,
                onValueChange = { scope = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Проект / раздел") },
                singleLine = true
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Что запомнить") },
                minLines = 2
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    TextButton(onClick = { kindMenuExpanded = true }) {
                        Text("Тип: ${kind.name.lowercase()}")
                    }
                    DropdownMenu(
                        expanded = kindMenuExpanded,
                        onDismissRequest = { kindMenuExpanded = false }
                    ) {
                        JarvisMemoryKind.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.name.lowercase()) },
                                onClick = { kind = option; kindMenuExpanded = false }
                            )
                        }
                    }
                }
                Button(
                    onClick = { if (text.isNotBlank() && scope.isNotBlank()) confirmSave = true },
                    enabled = text.isNotBlank() && scope.isNotBlank()
                ) {
                    Text(if (editingId == null) "Сохранить" else "Обновить")
                }
                if (editingId != null) {
                    TextButton(onClick = { editingId = null; text = "" }) { Text("Отмена") }
                }
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Поиск в этом разделе") },
                singleLine = true
            )
            Text("Записей: ${visibleEntries.size}", style = MaterialTheme.typography.labelLarge)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visibleEntries, key = { it.id }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("${entry.kind.name.lowercase()} • ${entry.scope}", style = MaterialTheme.typography.labelSmall)
                            Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    editingId = entry.id
                                    kind = entry.kind
                                    scope = entry.scope
                                    text = entry.text
                                }) { Text("Изменить") }
                                TextButton(onClick = { pendingDeleteId = entry.id }) { Text("Удалить") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmSave) {
        AlertDialog(
            onDismissRequest = { confirmSave = false },
            title = { Text(if (editingId == null) "Подтвердить сохранение" else "Подтвердить изменение") },
            text = { Text("Сохранить именно этот текст в локальную память раздела «${scope.trim()}»? Данные останутся в приложении на этом телефоне.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSave = false
                    val targetId = editingId
                    val approvedText = text
                    val approvedScope = scope
                    val approvedKind = kind
                    coroutineScope.launch {
                        val success = withContext(Dispatchers.IO) {
                            if (targetId == null) {
                                repository.saveAfterUserConfirmation(approvedKind, approvedScope, approvedText) != null
                            } else {
                                repository.updateAfterUserConfirmation(targetId, approvedText)
                            }
                        }
                        if (success) {
                            editingId = null
                            text = ""
                            reload()
                        }
                    }
                }) { Text("Подтверждаю") }
            },
            dismissButton = { TextButton(onClick = { confirmSave = false }) { Text("Не сохранять") } }
        )
    }

    val deleteId = pendingDeleteId
    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Удалить запись?") },
            text = { Text("Запись будет удалена из локальной памяти. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteId = null
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) { repository.deleteAfterUserConfirmation(deleteId) }
                        reload()
                    }
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Оставить") } }
        )
    }
}
