package com.jarvis.breeno.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.jarvis.breeno.assistant.core.QwenModelInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** User-controlled model panel. Download never starts until the consent checkbox and button. */
@Composable
fun QwenModelPanel(
    controller: JarvisRuntimeController,
    onClose: () -> Unit,
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var consent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(QwenModelInstaller.Progress(0L, QwenModelInstaller.MODEL_SIZE_BYTES)) }
    var message by remember { mutableStateOf("Qwen3.5 0.8B · Q4_0 · Apache-2.0") }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    val hasFile = remember(busy, message) { controller.hasQwenModelFile() }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("Локальный интеллект Джарвиса") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Qwen3.5 0.8B · GGUF Q4_0")
                Text("Размер: 563 МБ. Модель работает на устройстве после установки; интернет нужен только для первоначальной загрузки.")
                Text("Хранилище: закрытая папка приложения. SHA-256 проверяется до запуска.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consent, onCheckedChange = { consent = it }, enabled = !busy)
                    Text("Согласен загрузить модель (около 563 МБ)")
                }
                if (busy) {
                    LinearProgressIndicator(
                        progress = progress.fraction,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${progress.downloadedBytes / 1_000_000} / ${progress.totalBytes / 1_000_000} МБ · ${(progress.fraction * 100).toInt()}%"
                    )
                }
                Text(message)
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    enabled = consent && !busy,
                    onClick = {
                        downloadJob = scope.launch {
                            busy = true
                            message = "Подготовка безопасной загрузки…"
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    controller.installAndActivateQwen(userConsent = true) { value ->
                                        scope.launch { progress = value }
                                    }
                                }
                                message = result
                                onMessage(result)
                            } catch (cancelled: CancellationException) {
                                message = "Загрузка отменена; временный файл очищен."
                                throw cancelled
                            } catch (failure: Throwable) {
                                message = failure.message ?: "Не удалось установить модель"
                            } finally {
                                busy = false
                                downloadJob = null
                            }
                        }
                    }
                ) { Text("Загрузить и включить Qwen") }

                if (hasFile && !busy) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    message = withContext(Dispatchers.IO) {
                                        controller.activateInstalledQwen()
                                    }
                                    onMessage(message)
                                } catch (failure: Throwable) {
                                    message = failure.message ?: "Не удалось активировать модель"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) { Text("Проверить и активировать файл") }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                busy = true
                                message = "Проверяю нативный движок и выполняю безопасный пробный запрос…"
                                try {
                                    message = withContext(Dispatchers.IO) { controller.runQwenSelfTest() }
                                    onMessage(message)
                                } catch (failure: Throwable) {
                                    message = "Диагностика не пройдена: ${failure.message ?: failure.javaClass.simpleName}"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) { Text("Самодиагностика Qwen") }
                    TextButton(
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    message = withContext(Dispatchers.IO) {
                                        controller.removeQwenModel()
                                    }
                                    onMessage(message)
                                } catch (failure: Throwable) {
                                    message = failure.message ?: "Не удалось удалить модель"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) { Text("Удалить модель") }
                }
                if (busy) {
                    TextButton(onClick = { downloadJob?.cancel() }) { Text("Отменить загрузку") }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onClose, enabled = !busy) { Text("Закрыть") }
        }
    )
}
