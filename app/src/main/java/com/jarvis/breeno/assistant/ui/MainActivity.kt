package com.jarvis.breeno.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jarvis.breeno.assistant.BuildConfig
import com.jarvis.breeno.assistant.R
import com.jarvis.breeno.assistant.ui.render.AlphaVideoSurfaceView
import com.jarvis.breeno.assistant.ui.theme.JarvisTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var runtime: JarvisRuntimeController
    private var pendingListen = false
    private var statusSink: ((String) -> Unit)? = null

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingListen) startRealListening()
        pendingListen = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = JarvisRuntimeController(this)
        setContent { JarvisTheme { JarvisHome() } }
    }

    private fun requestAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRealListening()
        } else {
            pendingListen = true
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRealListening() {
        lifecycleScope.launch {
            runCatching {
                statusSink?.invoke("Подготавливаю локальный русский ASR…")
                withContext(Dispatchers.IO) { runtime.prepare { status -> runOnUiThread { statusSink?.invoke(status) } } }
                statusSink?.invoke("Микрофон активен — говорите")
                val text = withContext(Dispatchers.IO) { runtime.listenOnce { status -> runOnUiThread { statusSink?.invoke(status) } } }
                if (text.isNotBlank()) statusSink?.invoke("Распознано: $text")
            }.onFailure { error -> statusSink?.invoke("Ошибка runtime: ${error.message ?: error.javaClass.simpleName}") }
        }
    }

    private fun openSettings() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    private fun runQuickCommand(command: String) {
        when (command) {
            "Навигатор" -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("yandexnavi://build_route_on_map"))) }
            "Поиск" -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=Jarvis+Android")))
            "Телефон" -> startActivity(Intent(Intent.ACTION_DIAL))
        }
    }

    override fun onDestroy() {
        statusSink = null
        runtime.close()
        super.onDestroy()
    }

    // Экран по решению владельца: только светящийся шар по центру, без текста и кнопок.
    // Нажатие на шар запускает запись голоса.
    @Composable
    private fun JarvisHome() {
        var listening by remember { mutableStateOf(false) }

        DisposableEffect(Unit) {
            statusSink = { value -> listening = value.contains("Микрофон активен") || value.contains("говорите") }
            onDispose { statusSink = null }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF070A10)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AndroidView(
                    modifier = Modifier.size(300.dp),
                    factory = { context ->
                        AlphaVideoSurfaceView(context).also {
                            it.contentDescription = "JarvisSphere"
                            it.setOnClickListener { requestAndListen() }
                        }
                    },
                    update = { it.setListening(listening) }
                )
            }
        }
    }

}
