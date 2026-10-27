package com.jarvis.breeno.assistant.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jarvis.breeno.assistant.R
import com.jarvis.breeno.assistant.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JarvisTheme {
                JarvisHome()
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun JarvisHome() {
    var listening by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("Готов. Скажите «Джарвис»") }
    val pulse by rememberInfiniteTransition(label = "jarvis-pulse").animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "pulse"
    )

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF070A10)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0x334AD9FF), Color(0x00101928)),
                        center = Offset(size.width / 2f, size.height * 0.38f),
                        radius = size.minDimension * 0.62f
                    ),
                    radius = size.minDimension * 0.62f,
                    center = Offset(size.width / 2f, size.height * 0.38f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ДЖАРВИС", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Text("Русский ассистент • ColorOS", color = Color(0xFF9CA8B8), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = Color(0xFFB8C5D6))
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .size((250 * pulse).dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            brush = Brush.radialGradient(listOf(Color(0xCC67E8F9), Color(0x2251C7FF), Color.Transparent)),
                            radius = size.minDimension * 0.48f
                        )
                        drawCircle(Color(0x664FD9FF), radius = size.minDimension * 0.43f, style = Stroke(width = 3.dp.toPx()))
                        drawArc(
                            color = Color(0xFFB7F7FF),
                            startAngle = -55f,
                            sweepAngle = 125f,
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Image(
                        painter = painterResource(R.drawable.assistant_mascot),
                        contentDescription = "Джарвис",
                        modifier = Modifier.width(180.dp)
                    )
                }

                Text(
                    text = if (listening) "Слушаю…" else "Я рядом",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = transcript,
                    color = Color(0xFF9AA7B8),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xCC111824))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Быстрые команды", color = Color(0xFFB8C5D6), style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            QuickCommand("Навигатор") { transcript = "Открыть Яндекс Навигатор" }
                            QuickCommand("Поиск") { transcript = "Искать в интернете" }
                            QuickCommand("Телефон") { transcript = "Позвонить" }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                androidx.compose.material3.FloatingActionButton(
                    onClick = {
                        listening = !listening
                        transcript = if (listening) "Говорите…" else "Готов. Скажите «Джарвис»"
                    },
                    containerColor = if (listening) Color(0xFF4FD9FF) else Color(0xFF162536),
                    contentColor = if (listening) Color(0xFF041017) else Color(0xFFBDEFFF)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Микрофон", modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun QuickCommand(label: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(label, color = Color(0xFF8DEBFF))
    }
}
