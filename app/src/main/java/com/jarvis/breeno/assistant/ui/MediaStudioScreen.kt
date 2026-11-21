package com.jarvis.breeno.assistant.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.breeno.assistant.media.GeneratedMediaItem
import com.jarvis.breeno.assistant.media.GeneratedMediaType
import com.jarvis.breeno.assistant.media.JarvisMediaGenerator
import com.jarvis.breeno.assistant.media.MediaGenBackend
import com.jarvis.breeno.assistant.media.MediaGenConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

private val StudioCardBg = Color(0xCC111C2A)
private val StudioCyan = Color(0xFF75E5FF)
private val StudioMuted = Color(0xFF9AAABD)

@Composable
fun MediaStudioScreen(
    items: List<GeneratedMediaItem>,
    initialConfig: MediaGenConfig,
    pendingPromptRequest: Pair<GeneratedMediaType, String>?,
    onClearPendingRequest: () -> Unit,
    onGenerateImage: suspend (prompt: String, style: String, width: Int, height: Int, onProgress: (String) -> Unit) -> Result<GeneratedMediaItem>,
    onGenerateVideo: suspend (prompt: String, style: String, width: Int, height: Int, motionPrompt: String, onProgress: (String) -> Unit) -> Result<GeneratedMediaItem>,
    onOpenItem: (GeneratedMediaItem) -> Unit,
    onDeleteItem: (String) -> Unit,
    onSaveConfig: (MediaGenConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var motionPrompt by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf("Фото") }
    var selectedAspect by remember { mutableStateOf("1:1") }
    var generating by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showServerSettings by remember { mutableStateOf(false) }

    var cfgBackend by remember(initialConfig) { mutableStateOf(initialConfig.backend) }
    var cfgImgUrl by remember(initialConfig) { mutableStateOf(initialConfig.customImageUrl) }
    var cfgVidUrl by remember(initialConfig) { mutableStateOf(initialConfig.customVideoUrl) }
    var cfgModel by remember(initialConfig) { mutableStateOf(initialConfig.modelName) }
    var cfgKey by remember(initialConfig) { mutableStateOf(initialConfig.apiKey) }
    var cfgNegPrompt by remember(initialConfig) { mutableStateOf(initialConfig.negativePrompt) }
    var cfgSteps by remember(initialConfig) { mutableStateOf(initialConfig.steps.toString()) }
    var cfgScale by remember(initialConfig) { mutableStateOf(initialConfig.cfgScale.toString()) }
    var cfgCheckpoint by remember(initialConfig) { mutableStateOf(initialConfig.checkpointName) }
    var cfgPonyClipSkip2 by remember(initialConfig) { mutableStateOf(initialConfig.comfyPonyClipSkip2) }
    var cfgWanEnabled by remember(initialConfig) { mutableStateOf(initialConfig.comfyWanI2vEnabled) }
    var cfgWanHigh by remember(initialConfig) { mutableStateOf(initialConfig.comfyWanHighNoiseModel) }
    var cfgWanLow by remember(initialConfig) { mutableStateOf(initialConfig.comfyWanLowNoiseModel) }
    var cfgWanClip by remember(initialConfig) { mutableStateOf(initialConfig.comfyWanTextEncoder) }
    var cfgWanVae by remember(initialConfig) { mutableStateOf(initialConfig.comfyWanVae) }
    var cfgWanTurbo by remember(initialConfig) { mutableStateOf(initialConfig.comfyWanTurboLora) }
    var cfgVideoFrames by remember(initialConfig) { mutableStateOf(initialConfig.comfyVideoFrames) }
    var cfgLocalDreamUrl by remember(initialConfig) { mutableStateOf(initialConfig.localDreamUrl) }

    fun imageDimensions(): Pair<Int, Int> = when {
        cfgBackend == MediaGenBackend.LOCAL_DREAM_NPU -> when (selectedAspect) {
            "16:9" -> 768 to 512
            "9:16" -> 512 to 768
            else -> 512 to 512
        }
        cfgBackend == MediaGenBackend.CUSTOM_COMFYUI && cfgPonyClipSkip2 -> when (selectedAspect) {
            "16:9" -> 1216 to 832
            "9:16" -> 832 to 1216
            else -> 1024 to 1024
        }
        else -> when (selectedAspect) {
            "16:9" -> 768 to 432
            "9:16" -> 432 to 768
            else -> 768 to 768
        }
    }

    fun videoDimensions(): Pair<Int, Int> = if (cfgBackend == MediaGenBackend.CUSTOM_COMFYUI && cfgWanEnabled) {
        when (selectedAspect) {
            "16:9" -> 832 to 480
            "9:16" -> 480 to 832
            else -> 640 to 640
        }
    } else {
        when (selectedAspect) {
            "16:9" -> 640 to 368
            "9:16" -> 368 to 640
            else -> 512 to 512
        }
    }

    fun startGeneration(type: GeneratedMediaType, overridePrompt: String? = null) {
        if (generating) return
        val targetPrompt = (overridePrompt ?: prompt).trim()
        if (targetPrompt.isEmpty()) {
            errorText = "Введите описание для генерации"
            return
        }
        val (imgW, imgH) = imageDimensions()
        val (vidW, vidH) = videoDimensions()
        generating = true
        errorText = null
        progressText = if (type == GeneratedMediaType.IMAGE) "Создаю картинку…" else "Создаю MP4 видео…"
        scope.launch {
            val res = if (type == GeneratedMediaType.IMAGE) {
                onGenerateImage(targetPrompt, selectedStyle, imgW, imgH) { msg ->
                    scope.launch(Dispatchers.Main.immediate) { progressText = msg }
                }
            } else {
                onGenerateVideo(targetPrompt, selectedStyle, vidW, vidH, motionPrompt.trim()) { msg ->
                    scope.launch(Dispatchers.Main.immediate) { progressText = msg }
                }
            }
            generating = false
            progressText = null
            res.onFailure { err ->
                errorText = err.message ?: err.javaClass.simpleName
            }
        }
    }

    LaunchedEffect(pendingPromptRequest) {
        if (pendingPromptRequest != null) {
            val (reqType, reqPrompt) = pendingPromptRequest
            prompt = reqPrompt
            onClearPendingRequest()
            startGeneration(reqType, overridePrompt = reqPrompt)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "СТУДИЯ ИИ — КАРТИНКИ И ВИДЕО",
                        color = StudioCyan,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = cfgBackend.title,
                        color = StudioMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                IconButton(onClick = { showServerSettings = !showServerSettings }) {
                    Icon(Icons.Default.Settings, contentDescription = "Сервер генерации", tint = StudioCyan)
                }
            }
        }

        if (showServerSettings) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Движок генерации", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Встроенный онлайн-движок ограничен правилами внешнего сервиса. Для полного контроля над чекпоинтами (SDXL / Flux / локальные модели) выберите ComfyUI или SD / A1111 на вашем ПК.",
                            color = StudioMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilterChip(
                                selected = cfgBackend == MediaGenBackend.POLLINATIONS_ONLINE,
                                onClick = { cfgBackend = MediaGenBackend.POLLINATIONS_ONLINE },
                                label = { Text("Встроенный") },
                            )
                            FilterChip(
                                selected = cfgBackend == MediaGenBackend.LOCAL_DREAM_NPU,
                                onClick = { cfgBackend = MediaGenBackend.LOCAL_DREAM_NPU },
                                label = { Text("Local Dream NPU") },
                            )
                            FilterChip(
                                selected = cfgBackend == MediaGenBackend.CUSTOM_COMFYUI,
                                onClick = { cfgBackend = MediaGenBackend.CUSTOM_COMFYUI },
                                label = { Text("ComfyUI") },
                            )
                            FilterChip(
                                selected = cfgBackend == MediaGenBackend.CUSTOM_A1111_SD,
                                onClick = { cfgBackend = MediaGenBackend.CUSTOM_A1111_SD },
                                label = { Text("SD / A1111") },
                            )
                            FilterChip(
                                selected = cfgBackend == MediaGenBackend.CUSTOM_OPENAI_IMAGES,
                                onClick = { cfgBackend = MediaGenBackend.CUSTOM_OPENAI_IMAGES },
                                label = { Text("OpenAI API") },
                            )
                        }
                        if (cfgBackend == MediaGenBackend.LOCAL_DREAM_NPU) {
                            Text(
                                text = "Полностью офлайн на Snapdragon 8 Gen 2 NPU (512×512 за ~8–15 сек). Запустите приложение Local Dream с SD 1.5 моделью (Absolute Reality / DreamShaper / Anything V5) и оставьте его в фоне.",
                                color = Color(0xFF34D399),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            OutlinedButton(
                                onClick = {
                                    cfgLocalDreamUrl = JarvisMediaGenerator.DEFAULT_LOCAL_DREAM_URL
                                    cfgSteps = "24"
                                    cfgScale = "7.0"
                                    cfgNegPrompt = JarvisMediaGenerator.DEFAULT_LOCAL_DREAM_NEGATIVE
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Пресет: SD 1.5 NPU (512×512, 24 шага)")
                            }
                            OutlinedTextField(
                                value = cfgLocalDreamUrl,
                                onValueChange = { cfgLocalDreamUrl = it },
                                label = { Text("URL Local Dream NPU (по умолчанию http://127.0.0.1:8081)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (cfgBackend != MediaGenBackend.POLLINATIONS_ONLINE && cfgBackend != MediaGenBackend.LOCAL_DREAM_NPU) {
                            OutlinedTextField(
                                value = cfgImgUrl,
                                onValueChange = { cfgImgUrl = it },
                                label = {
                                    Text(
                                        when (cfgBackend) {
                                            MediaGenBackend.CUSTOM_COMFYUI -> "URL ComfyUI (напр. https://.../comfy или http://PC:8188)"
                                            MediaGenBackend.CUSTOM_A1111_SD -> "URL A1111 / Forge (напр. http://PC:7860)"
                                            else -> "URL API изображений"
                                        }
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (cfgBackend == MediaGenBackend.CUSTOM_COMFYUI) {
                            OutlinedButton(
                                onClick = {
                                    cfgCheckpoint = JarvisMediaGenerator.DEFAULT_PONY_CHECKPOINT
                                    cfgSteps = "30"
                                    cfgScale = "5.5"
                                    cfgNegPrompt = JarvisMediaGenerator.DEFAULT_PONY_NEGATIVE
                                    cfgPonyClipSkip2 = true
                                    cfgWanEnabled = true
                                    cfgWanHigh = JarvisMediaGenerator.DEFAULT_WAN_HIGH_MODEL
                                    cfgWanLow = JarvisMediaGenerator.DEFAULT_WAN_LOW_MODEL
                                    cfgWanClip = JarvisMediaGenerator.DEFAULT_WAN_TEXT_ENCODER
                                    cfgWanVae = JarvisMediaGenerator.DEFAULT_WAN_VAE
                                    cfgWanTurbo = true
                                    cfgVideoFrames = 49
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Пресет: CyberRealistic Pony V18 + Wan 2.2 I2V-14B")
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                FilterChip(
                                    selected = cfgPonyClipSkip2,
                                    onClick = { cfgPonyClipSkip2 = !cfgPonyClipSkip2 },
                                    label = { Text("Pony Clip Skip 2") },
                                )
                                FilterChip(
                                    selected = cfgWanEnabled,
                                    onClick = { cfgWanEnabled = !cfgWanEnabled },
                                    label = { Text("Wan 2.2 I2V-14B (Видео)") },
                                )
                                if (cfgWanEnabled) {
                                    FilterChip(
                                        selected = cfgWanTurbo,
                                        onClick = { cfgWanTurbo = !cfgWanTurbo },
                                        label = { Text("4-step Turbo LoRA") },
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = cfgCheckpoint,
                                onValueChange = { cfgCheckpoint = it },
                                label = { Text("Чекпоинт .safetensors (Pony V18 / пусто = авто)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (cfgWanEnabled) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Длина Wan 2.2:", color = StudioMuted, style = MaterialTheme.typography.labelSmall)
                                    listOf(49 to "49 кадр. (3с)", 81 to "81 кадр. (5с)").forEach { (cnt, lbl) ->
                                        FilterChip(
                                            selected = cfgVideoFrames == cnt,
                                            onClick = { cfgVideoFrames = cnt },
                                            label = { Text(lbl) },
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = cfgWanHigh,
                                    onValueChange = { cfgWanHigh = it },
                                    label = { Text("Wan 2.2 High-Noise UNET (.safetensors)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = cfgWanLow,
                                    onValueChange = { cfgWanLow = it },
                                    label = { Text("Wan 2.2 Low-Noise UNET (.safetensors)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = cfgWanClip,
                                    onValueChange = { cfgWanClip = it },
                                    label = { Text("Wan Text Encoder (umt5_xxl...)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = cfgWanVae,
                                    onValueChange = { cfgWanVae = it },
                                    label = { Text("Wan VAE (wan_2.1_vae.safetensors)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        if (cfgBackend == MediaGenBackend.CUSTOM_COMFYUI ||
                            cfgBackend == MediaGenBackend.CUSTOM_A1111_SD ||
                            cfgBackend == MediaGenBackend.LOCAL_DREAM_NPU
                        ) {
                            OutlinedTextField(
                                value = cfgNegPrompt,
                                onValueChange = { cfgNegPrompt = it },
                                label = { Text("Негативный промпт (Negative prompt)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = cfgSteps,
                                    onValueChange = { cfgSteps = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Шаги (Steps)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = cfgScale,
                                    onValueChange = { cfgScale = it },
                                    label = { Text("CFG Scale") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (cfgBackend != MediaGenBackend.POLLINATIONS_ONLINE && cfgBackend != MediaGenBackend.LOCAL_DREAM_NPU) {
                            OutlinedTextField(
                                value = cfgKey,
                                onValueChange = { cfgKey = it },
                                label = { Text("API-ключ (если требуется)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        OutlinedTextField(
                            value = cfgVidUrl,
                            onValueChange = { cfgVidUrl = it },
                            label = { Text("URL видео-API (пусто = встроенный MP4)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (cfgBackend == MediaGenBackend.POLLINATIONS_ONLINE || cfgBackend == MediaGenBackend.CUSTOM_OPENAI_IMAGES) {
                            OutlinedTextField(
                                value = cfgModel,
                                onValueChange = { cfgModel = it },
                                label = { Text("Модель (пусто = по умолчанию)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Button(
                            onClick = {
                                onSaveConfig(
                                    MediaGenConfig(
                                        backend = cfgBackend,
                                        customImageUrl = cfgImgUrl,
                                        customVideoUrl = cfgVidUrl,
                                        apiKey = cfgKey,
                                        modelName = cfgModel,
                                        negativePrompt = cfgNegPrompt,
                                        steps = cfgSteps.toIntOrNull()?.coerceIn(1, 100) ?: 24,
                                        cfgScale = cfgScale.replace(',', '.').toFloatOrNull()?.coerceIn(1f, 30f) ?: 7.0f,
                                        checkpointName = cfgCheckpoint,
                                        comfyPonyClipSkip2 = cfgPonyClipSkip2,
                                        comfyWanI2vEnabled = cfgWanEnabled,
                                        comfyWanHighNoiseModel = cfgWanHigh,
                                        comfyWanLowNoiseModel = cfgWanLow,
                                        comfyWanTextEncoder = cfgWanClip,
                                        comfyWanVae = cfgWanVae,
                                        comfyWanTurboLora = cfgWanTurbo,
                                        comfyVideoFrames = cfgVideoFrames,
                                        localDreamUrl = cfgLocalDreamUrl,
                                    )
                                )
                                showServerSettings = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Применить настройки") }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it; errorText = null },
                label = { Text("Что создать? (например: футуристичный город ночью)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (cfgBackend == MediaGenBackend.CUSTOM_COMFYUI && cfgWanEnabled) {
            item {
                OutlinedTextField(
                    value = motionPrompt,
                    onValueChange = { motionPrompt = it },
                    label = { Text("Движение камеры / сцены для Wan 2.2 (опционально)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("Фото", "Кино", "Киберпанк", "Аниме", "3D Арт").forEach { style ->
                    FilterChip(
                        selected = selectedStyle == style,
                        onClick = { selectedStyle = style },
                        label = { Text(style) },
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Формат:", color = StudioMuted, style = MaterialTheme.typography.labelMedium)
                listOf("1:1", "16:9", "9:16").forEach { ratio ->
                    FilterChip(
                        selected = selectedAspect == ratio,
                        onClick = { selectedAspect = ratio },
                        label = { Text(ratio) },
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { startGeneration(GeneratedMediaType.IMAGE) },
                    enabled = !generating,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 3.dp))
                    Text("Картинка")
                }
                OutlinedButton(
                    onClick = { startGeneration(GeneratedMediaType.VIDEO) },
                    enabled = !generating,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 3.dp))
                    Text("Видео MP4")
                }
            }
        }

        if (generating && progressText != null) {
            item {
                Column {
                    Text(progressText!!, color = StudioCyan, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (!errorText.isNullOrBlank()) {
            item {
                Text(errorText!!, color = Color(0xFFFF8A8A), style = MaterialTheme.typography.labelMedium)
            }
        }

        item {
            Text(
                text = "Галерея (${items.size})",
                color = Color(0xFFE5F3FF),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (items.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Здесь появятся созданные картинки и MP4-видео. Также можно сказать голосом: «Нарисуй…» или «Создай видео…».",
                        color = StudioMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        } else {
            items(items, key = { it.id }) { item ->
                MediaItemCard(
                    item = item,
                    onOpen = { onOpenItem(item) },
                    onDelete = { onDeleteItem(item.id) },
                )
            }
        }
    }
}

@Composable
private fun MediaItemCard(
    item: GeneratedMediaItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val previewBitmap = remember(item.filePath, item.type) {
        if (item.type == GeneratedMediaType.IMAGE && File(item.filePath).isFile) {
            runCatching { BitmapFactory.decodeFile(item.filePath)?.asImageBitmap() }.getOrNull()
        } else null
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = StudioCardBg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap,
                    contentDescription = item.prompt,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.prompt,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${item.type.label} • ${item.style} • ${item.sizeBytes / 1024} КБ",
                        color = StudioMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Row {
                    TextButton(onClick = onOpen) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Открыть", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.padding(horizontal = 2.dp))
                        Text("Открыть")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = StudioMuted)
                    }
                }
            }
        }
    }
}
