package com.jarvis.breeno.assistant.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jarvis.breeno.assistant.core.JarvisOrbMode
import com.jarvis.breeno.assistant.ui.render.AlphaVideoSurfaceView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Interactive Orb (Живой цифровой мозг) combining the native OpenGL ES 2 sphere
 * with real-time Compose neural rings reacting to engine mode and microphone RMS.
 */
@Composable
fun JarvisInteractiveOrb(
    mode: JarvisOrbMode,
    audioRms: Float,
    orbSize: Dp,
    modifier: Modifier = Modifier,
) {
    val accentColor = when (mode) {
        JarvisOrbMode.IDLE -> Color(0xFF4CC9F0)
        JarvisOrbMode.LISTENING -> Color(0xFF75E5FF)
        JarvisOrbMode.THINKING -> Color(0xFFA78BFA)
        JarvisOrbMode.SEARCHING -> Color(0xFF38BDF8)
        JarvisOrbMode.EXECUTING -> Color(0xFF34D399)
        JarvisOrbMode.SPEAKING -> Color(0xFF60A5FA)
        JarvisOrbMode.LEARNING -> Color(0xFFFBBF24)
        JarvisOrbMode.ERROR -> Color(0xFFFF6B6B)
    }

    val smoothRms by animateFloatAsState(
        targetValue = if (mode == JarvisOrbMode.LISTENING) audioRms.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 90),
        label = "orbAudioRms",
    )

    val transition = rememberInfiniteTransition(label = "orbOrbit")
    val orbitAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mode) {
                    JarvisOrbMode.THINKING, JarvisOrbMode.SEARCHING, JarvisOrbMode.EXECUTING -> 2600
                    JarvisOrbMode.LISTENING, JarvisOrbMode.SPEAKING -> 3800
                    else -> 8000
                },
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbitAngle",
    )

    val listening = mode == JarvisOrbMode.LISTENING
    val speaking = mode == JarvisOrbMode.SPEAKING

    Box(
        modifier = modifier.size(orbSize + 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) - 10f
            val dynamicRadius = baseRadius * (0.90f + smoothRms * 0.10f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.24f + smoothRms * 0.22f),
                        accentColor.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = dynamicRadius,
                ),
                radius = dynamicRadius,
                center = center,
            )

            drawArc(
                color = accentColor.copy(alpha = 0.55f),
                startAngle = orbitAngle,
                sweepAngle = 110f,
                useCenter = false,
                style = Stroke(width = 3.5f + smoothRms * 4f, cap = StrokeCap.Round),
            )
            drawArc(
                color = accentColor.copy(alpha = 0.30f),
                startAngle = -orbitAngle * 0.7f,
                sweepAngle = 75f,
                useCenter = false,
                style = Stroke(width = 2.2f, cap = StrokeCap.Round),
            )

            // Neural node pulses on the perimeter for active states
            val nodes = if (mode == JarvisOrbMode.IDLE) 3 else 6
            for (i in 0 until nodes) {
                val rad = ((orbitAngle + i * (360f / nodes)) * PI / 180.0)
                val nodeOffset = Offset(
                    x = center.x + (dynamicRadius * 0.94f * cos(rad)).toFloat(),
                    y = center.y + (dynamicRadius * 0.94f * sin(rad)).toFloat(),
                )
                drawCircle(
                    color = accentColor.copy(alpha = 0.85f),
                    radius = 3.5f + smoothRms * 3.5f,
                    center = nodeOffset,
                )
            }
        }

        AndroidView(
            modifier = Modifier.size(orbSize),
            factory = { context ->
                AlphaVideoSurfaceView(context).apply {
                    contentDescription = "Интерактивное энергетическое ядро Джарвиса"
                }
            },
            update = {
                it.setListening(listening)
                it.setSpeaking(speaking)
            },
        )
    }
}
