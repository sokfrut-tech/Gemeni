package com.jarvis.breeno.assistant.ui

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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jarvis.breeno.assistant.control.JarvisControlSnapshot
import com.jarvis.breeno.assistant.control.JarvisEventLevel
import com.jarvis.breeno.assistant.control.JarvisEventLogEntry
import com.jarvis.breeno.assistant.core.DeviceHeatState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PanelColor = Color(0xCC111C2A)
private val CyanAccent = Color(0xFF75E5FF)
private val MutedLabel = Color(0xFF9AAABD)

@Composable
fun ControlCenterScreen(
    snapshot: JarvisControlSnapshot,
    onRefreshTelemetry: () -> Unit,
    onClearEvents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "ЦЕНТР УПРАВЛЕНИЯ JARVIS",
                    color = CyanAccent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Реальная телеметрия устройства и журнал задач",
                    color = MutedLabel,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row {
                IconButton(onClick = onRefreshTelemetry) {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить телеметрию", tint = CyanAccent)
                }
                IconButton(onClick = onClearEvents) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Очистить журнал", tint = MutedLabel)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricCard(
                title = "Голос",
                value = snapshot.totalVoiceCommands.toString(),
                subtitle = if (snapshot.localAsrReady) "ASR готов" else "ASR не загружен",
                valueColor = CyanAccent,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Видео",
                value = "${snapshot.videosCompleted}/${snapshot.videosInQueue}",
                subtitle = "Готово / в очереди",
                valueColor = Color(0xFF34D399),
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Ошибки",
                value = snapshot.failedTasks.toString(),
                subtitle = "Активных: ${snapshot.activeTasks}",
                valueColor = if (snapshot.failedTasks > 0) Color(0xFFFF8A8A) else MutedLabel,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val heatColor = when (snapshot.heatState) {
            DeviceHeatState.COOL -> Color(0xFF34D399)
            DeviceHeatState.WARM -> Color(0xFFFBBF24)
            DeviceHeatState.HOT, DeviceHeatState.CRITICAL -> Color(0xFFFF6B6B)
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = PanelColor),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Узел устройства (Android / OPPO)", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Свободно RAM: ${snapshot.availableMemoryMb} МБ  •  Нагрев: ${snapshot.heatState.name}",
                    color = heatColor,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = if (snapshot.allowLocalLlm) {
                        "Локальный LLM разрешён (контекст до ${snapshot.maxContextTokens} токенов)"
                    } else {
                        "Локальный LLM ограничен политикой ресурсов/нагрева"
                    },
                    color = MutedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Журнал событий (${snapshot.events.size})",
            color = Color(0xFFE5F3FF),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )

        if (snapshot.events.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = PanelColor),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(
                    text = "Событий пока нет. Выполните голосовую команду или добавьте видео в SaveVideo.",
                    color = MutedLabel,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(14.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(snapshot.events, key = { it.id }) { event ->
                    EventLogRow(event)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PanelColor),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, color = MutedLabel, style = MaterialTheme.typography.labelSmall)
            Text(value, color = valueColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MutedLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun EventLogRow(entry: JarvisEventLogEntry) {
    val badgeColor = when (entry.level) {
        JarvisEventLevel.SUCCESS -> Color(0xFF34D399)
        JarvisEventLevel.WARN -> Color(0xFFFBBF24)
        JarvisEventLevel.ERROR -> Color(0xFFFF8A8A)
        JarvisEventLevel.INFO -> CyanAccent
    }
    val timeText = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.timestampMillis))

    Card(
        colors = CardDefaults.cardColors(containerColor = PanelColor),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "[${entry.category}] ${entry.title}",
                    color = badgeColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(timeText, color = MutedLabel, style = MaterialTheme.typography.labelSmall)
            }
            if (entry.detail.isNotBlank()) {
                Text(
                    text = entry.detail,
                    color = Color(0xFFD5E3F0),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
