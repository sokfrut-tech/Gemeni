package com.jarvis.breeno.assistant.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jarvis.breeno.assistant.control.JarvisControlSnapshot
import com.jarvis.breeno.assistant.control.JarvisEventLevel
import com.jarvis.breeno.assistant.control.JarvisEventLogEntry
import com.jarvis.breeno.assistant.core.DeviceHeatState
import com.jarvis.breeno.assistant.ui.theme.LocalJarvisPalette
import com.jarvis.breeno.assistant.ui.theme.jarvisTileShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ControlCenterScreen(
    snapshot: JarvisControlSnapshot,
    onRefreshTelemetry: () -> Unit,
    onClearEvents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalJarvisPalette.current
    val shape = jarvisTileShape(palette, 14.dp)

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
                    color = palette.primaryAccent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Реальная телеметрия устройства и журнал задач",
                    color = palette.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row {
                IconButton(onClick = onRefreshTelemetry) {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить телеметрию", tint = palette.primaryAccent)
                }
                IconButton(onClick = onClearEvents) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Очистить журнал", tint = palette.textSecondary)
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
                valueColor = palette.primaryAccent,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Видео",
                value = "${snapshot.videosCompleted}/${snapshot.videosInQueue}",
                subtitle = "Готово / в очереди",
                valueColor = palette.successAccent,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Ошибки",
                value = snapshot.failedTasks.toString(),
                subtitle = "Активных: ${snapshot.activeTasks}",
                valueColor = if (snapshot.failedTasks > 0) palette.errorAccent else palette.textSecondary,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val heatColor = when (snapshot.heatState) {
            DeviceHeatState.COOL -> palette.successAccent
            DeviceHeatState.WARM -> palette.secondaryAccent
            DeviceHeatState.HOT, DeviceHeatState.CRITICAL -> palette.errorAccent
        }
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
            ) {
                Text("Узел устройства (Android / OPPO)", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
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
                    color = palette.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Журнал событий (${snapshot.events.size})",
            color = palette.textPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )

        if (snapshot.events.isEmpty()) {
            Surface(
                shape = shape,
                color = palette.tileBg,
                border = BorderStroke(1.dp, palette.tileBorder.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(
                    text = "Событий пока нет. Выполните голосовую команду или добавьте видео в SaveVideo.",
                    color = palette.textSecondary,
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
    val palette = LocalJarvisPalette.current
    Surface(
        shape = jarvisTileShape(palette, 12.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, valueColor.copy(alpha = 0.45f)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(palette.tileBgAlt, palette.tileBg)))
                .padding(10.dp),
        ) {
            Text(title, color = palette.textSecondary, style = MaterialTheme.typography.labelSmall)
            Text(value, color = valueColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, color = palette.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun EventLogRow(entry: JarvisEventLogEntry) {
    val palette = LocalJarvisPalette.current
    val badgeColor = when (entry.level) {
        JarvisEventLevel.SUCCESS -> palette.successAccent
        JarvisEventLevel.WARN -> palette.secondaryAccent
        JarvisEventLevel.ERROR -> palette.errorAccent
        JarvisEventLevel.INFO -> palette.primaryAccent
    }
    val timeText = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.timestampMillis))

    Surface(
        shape = jarvisTileShape(palette, 10.dp),
        color = palette.tileBg,
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.30f)),
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
                Text(timeText, color = palette.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
            if (entry.detail.isNotBlank()) {
                Text(
                    text = entry.detail,
                    color = palette.textPrimary.copy(alpha = 0.90f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
