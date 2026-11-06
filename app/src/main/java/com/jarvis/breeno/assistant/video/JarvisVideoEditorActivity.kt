package com.jarvis.breeno.assistant.video

import android.content.ContentValues
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvis.breeno.assistant.ui.theme.JarvisTheme
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@UnstableApi
class JarvisVideoEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JarvisTheme { JarvisVideoEditorScreen() } }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun JarvisVideoEditorScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var durationMs by remember { mutableStateOf(0L) }
    var startMs by remember { mutableStateOf(0f) }
    var endMs by remember { mutableStateOf(0f) }
    var status by remember { mutableStateOf("Выберите видео на телефоне") }
    var exporting by remember { mutableStateOf(false) }
    var transformer by remember { mutableStateOf<Transformer?>(null) }
    var outputUri by remember { mutableStateOf<Uri?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            sourceUri = uri
            outputUri = null
            status = "Читаю параметры видео…"
            scope.launch {
                val duration = withContext(Dispatchers.IO) {
                    runCatching {
                        MediaMetadataRetriever().let { retriever ->
                            try {
                                retriever.setDataSource(context, uri)
                                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                            } finally {
                                retriever.release()
                            }
                        }
                    }.getOrDefault(0L)
                }
                durationMs = duration
                startMs = 0f
                endMs = duration.toFloat()
                status = if (duration >= VideoTrimRange.MIN_CLIP_MS) {
                    "Видео загружено • ${formatDuration(duration)}"
                } else {
                    "Не удалось прочитать длительность или видео слишком короткое"
                }
            }
        }
    }

    fun exportTrim() {
        val uri = sourceUri ?: return
        val trim = VideoTrimRange.create(startMs.toLong(), endMs.toLong(), durationMs)
        if (trim == null) {
            errorMessage = "Выберите фрагмент не короче 0,5 секунды."
            return
        }
        val outputFile = File(context.cacheDir, "jarvis-trim-${System.currentTimeMillis()}.mp4")
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(trim.startMs)
                    .setEndPositionMs(trim.endMs)
                    .build()
            )
            .build()
        val editedItem = EditedMediaItem.Builder(mediaItem).build()
        exporting = true
        outputUri = null
        status = "Экспортирую фрагмент локально…"
        val worker = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            runCatching { copyToVideoLibrary(context, outputFile) }.getOrNull()
                        }
                        exporting = false
                        transformer = null
                        if (saved != null) {
                            outputUri = saved
                            status = "Готово • ${formatDuration(trim.durationMs)} • MP4 сохранён в галерею"
                        } else {
                            status = "Экспорт завершён, но не удалось сохранить результат в галерею"
                            errorMessage = "Файл создан во временной папке приложения, но перенос в галерею не удался."
                        }
                        outputFile.delete()
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    exporting = false
                    transformer = null
                    status = "Ошибка экспорта"
                    errorMessage = exportException.message ?: "Media3 не смог обработать этот файл."
                    outputFile.delete()
                }
            })
            .build()
        transformer = worker
        runCatching { worker.start(editedItem, outputFile.absolutePath) }
            .onFailure {
                worker.cancel()
                transformer = null
                exporting = false
                status = "Не удалось начать экспорт"
                errorMessage = it.message ?: it.javaClass.simpleName
                outputFile.delete()
            }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Видеостудия Джарвиса", style = MaterialTheme.typography.headlineSmall)
            Text("Первый офлайн-инструмент: точная обрезка и экспорт MP4. Исходное видео не отправляется в интернет.")
            Button(onClick = { picker.launch(arrayOf("video/*")) }, enabled = !exporting) {
                Text("Выбрать видео")
            }
            Text(status, style = MaterialTheme.typography.bodyMedium)
            if (durationMs >= VideoTrimRange.MIN_CLIP_MS) {
                Text("Начало: ${formatDuration(startMs.toLong())}")
                Slider(
                    value = startMs.coerceIn(0f, (endMs - VideoTrimRange.MIN_CLIP_MS).coerceAtLeast(0f)),
                    onValueChange = { startMs = it.coerceAtMost(endMs - VideoTrimRange.MIN_CLIP_MS) },
                    valueRange = 0f..(durationMs - VideoTrimRange.MIN_CLIP_MS).toFloat(),
                    enabled = !exporting
                )
                Text("Конец: ${formatDuration(endMs.toLong())}")
                Slider(
                    value = endMs.coerceIn((startMs + VideoTrimRange.MIN_CLIP_MS).coerceAtMost(durationMs.toFloat()), durationMs.toFloat()),
                    onValueChange = { endMs = it.coerceAtLeast(startMs + VideoTrimRange.MIN_CLIP_MS) },
                    valueRange = VideoTrimRange.MIN_CLIP_MS.toFloat()..durationMs.toFloat(),
                    enabled = !exporting
                )
                Text("Фрагмент: ${formatDuration((endMs - startMs).toLong().coerceAtLeast(0L))}")
                Button(onClick = { exportTrim() }, enabled = !exporting && sourceUri != null) {
                    Text(if (exporting) "Экспорт…" else "Обрезать и сохранить MP4")
                }
            }
            if (exporting) {
                OutlinedButton(onClick = { transformer?.cancel(); transformer = null; exporting = false; status = "Экспорт отменён" }) {
                    Text("Отменить экспорт")
                }
            }
            outputUri?.let { uri ->
                OutlinedButton(onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "Поделиться видео"))
                }) { Text("Поделиться результатом") }
            }
            Text(
                "Монтаж выполняется на устройстве. При экспорте создаётся новый MP4-файл; исходник не изменяется.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Видеостудия") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("Понятно") } }
        )
    }
}

private fun copyToVideoLibrary(context: android.content.Context, source: File): Uri? {
    if (!source.exists() || source.length() <= 0L) return null
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, "Jarvis-${System.currentTimeMillis()}.mp4")
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Jarvis")
        put(MediaStore.Video.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val destination = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    return runCatching {
        resolver.openOutputStream(destination)?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Unable to open MediaStore output")
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(destination, values, null, null)
        destination
    }.getOrElse {
        resolver.delete(destination, null, null)
        null
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}
