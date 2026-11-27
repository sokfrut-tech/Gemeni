package com.jarvis.breeno.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import com.jarvis.breeno.assistant.ui.theme.JarvisButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.jarvis.breeno.assistant.ui.theme.JarvisOutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.breeno.assistant.savevideo.SaveVideoManager
import com.jarvis.breeno.assistant.savevideo.SaveVideoMetadata
import com.jarvis.breeno.assistant.savevideo.SaveVideoQuality
import com.jarvis.breeno.assistant.savevideo.SaveVideoStatus
import com.jarvis.breeno.assistant.savevideo.SaveVideoTask
import com.jarvis.breeno.assistant.ui.theme.LocalJarvisPalette
import kotlinx.coroutines.launch

private val CardBg: Color
    @Composable get() = LocalJarvisPalette.current.tileBg
private val AccentCyan: Color
    @Composable get() = LocalJarvisPalette.current.primaryAccent
private val MutedText: Color
    @Composable get() = LocalJarvisPalette.current.textSecondary

@Composable
fun SaveVideoScreen(
    tasks: List<SaveVideoTask>,
    onInspect: suspend (String) -> Result<SaveVideoMetadata>,
    onEnqueue: (String, SaveVideoQuality) -> Result<SaveVideoTask>,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var urlInput by remember { mutableStateOf("") }
    var selectedQuality by remember { mutableStateOf(SaveVideoQuality.AUTO) }
    var inspecting by remember { mutableStateOf(false) }
    var inspectedMeta by remember { mutableStateOf<SaveVideoMetadata?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = "SAVEVIDEO — ОЧЕРЕДЬ И МЕДИА",
            color = AccentCyan,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Проверка метаданных и загрузка разрешённых медиафайлов",
            color = MutedText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )

        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it; feedback = null },
            label = { Text("Ссылка на видео или медиапоток (https://...)") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = {
                    val clip = clipboard.getText()?.text?.trim()
                    if (!clip.isNullOrBlank()) {
                        urlInput = clip
                        feedback = "Ссылка вставлена из буфера обмена"
                    }
                }) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Вставить ссылку", tint = AccentCyan)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SaveVideoQuality.entries.forEach { quality ->
                FilterChip(
                    selected = selectedQuality == quality,
                    onClick = { selectedQuality = quality },
                    label = { Text(quality.label) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            JarvisOutlinedButton(
                onClick = {
                    if (inspecting) return@JarvisOutlinedButton
                    inspecting = true
                    inspectedMeta = null
                    feedback = "Проверяю метаданные…"
                    scope.launch {
                        val res = onInspect(urlInput)
                        inspecting = false
                        res.onSuccess { meta ->
                            inspectedMeta = meta
                            feedback = if (meta.directDownloadable) {
                                "Готово к загрузке: ${meta.title}"
                            } else {
                                "Обнаружена страница (${meta.source.displayName}): ${meta.title}"
                            }
                        }.onFailure { err ->
                            feedback = "Ошибка проверки: ${err.message ?: err.javaClass.simpleName}"
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 3.dp))
                Text(if (inspecting) "Проверка…" else "Проверить")
            }

            JarvisButton(
                onClick = {
                    val res = onEnqueue(urlInput, selectedQuality)
                    res.onSuccess {
                        feedback = "Добавлено в очередь (#${it.id})"
                        urlInput = ""
                        inspectedMeta = null
                    }.onFailure { err ->
                        feedback = err.message ?: "Не удалось добавить ссылку"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 3.dp))
                Text("В очередь")
            }
        }

        if (!feedback.isNullOrBlank()) {
            Text(
                text = feedback!!,
                color = if (feedback!!.startsWith("Ошибка")) Color(0xFFFF9C9C) else AccentCyan,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        inspectedMeta?.let { meta ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(meta.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sizeLabel = meta.contentLengthBytes?.let { SaveVideoManager.formatSize(it) } ?: "размер неизвестен"
                    Text(
                        "${meta.source.displayName} • ${meta.contentType ?: "unknown"} • $sizeLabel",
                        color = MutedText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Очередь и история (${tasks.size})",
            color = Color(0xFFE5F3FF),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )

        if (tasks.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(
                    text = "Очередь пуста. Вставьте ссылку на разрешённый медиапоток выше.",
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(14.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tasks, key = { it.id }) { task ->
                    SaveVideoTaskCard(
                        task = task,
                        onCancel = { onCancel(task.id) },
                        onRetry = { onRetry(task.id) },
                        onDelete = { onDelete(task.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveVideoTaskCard(
    task: SaveVideoTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val statusColor = when (task.status) {
        SaveVideoStatus.COMPLETED -> Color(0xFF34D399)
        SaveVideoStatus.DOWNLOADING, SaveVideoStatus.INSPECTING -> AccentCyan
        SaveVideoStatus.FAILED -> Color(0xFFFF8A8A)
        SaveVideoStatus.CANCELLED -> Color(0xFFFBBF24)
        SaveVideoStatus.QUEUED -> MutedText
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${task.source.displayName} • ${task.quality.label} • ${task.status.label}",
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Row {
                    if (task.status == SaveVideoStatus.DOWNLOADING || task.status == SaveVideoStatus.INSPECTING || task.status == SaveVideoStatus.QUEUED) {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "Отменить", tint = Color(0xFFFBBF24))
                        }
                    } else if (task.status == SaveVideoStatus.FAILED || task.status == SaveVideoStatus.CANCELLED) {
                        IconButton(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, contentDescription = "Повторить", tint = AccentCyan)
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MutedText)
                    }
                }
            }

            if (task.status == SaveVideoStatus.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { (task.progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }

            if (task.downloadedBytes > 0L) {
                Text(
                    text = "Сохранено: ${SaveVideoManager.formatSize(task.downloadedBytes)}",
                    color = MutedText,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (!task.errorMessage.isNullOrBlank()) {
                Text(
                    text = task.errorMessage,
                    color = Color(0xFFFF9C9C),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .background(Color(0x33FF5252), RoundedCornerShape(6.dp))
                        .padding(6.dp),
                )
            }
        }
    }
}
