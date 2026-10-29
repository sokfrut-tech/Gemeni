package com.jarvis.breeno.assistant.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Состояния шара: простой (голубой), слушаю (золото), говорю (светлый). */
enum class OrbState { IDLE, LISTENING, SPEAKING }

private class OrbPalette(val ring: Color, val glow: Color, val core: Color)

private val Background = Color(0xFF05080C)

private fun paletteFor(state: OrbState): OrbPalette = when (state) {
    OrbState.IDLE -> OrbPalette(Color(0xFF5BE7FF), Color(0x593FD6FF), Color(0xFF0C3346))
    OrbState.LISTENING -> OrbPalette(Color(0xFFFFC45C), Color(0x73FFB446), Color(0xFF3A2A0A))
    OrbState.SPEAKING -> OrbPalette(Color(0xFFFFE7A8), Color(0x99FFCD78), Color(0xFF4A3510))
}

/**
 * Главный элемент экрана: шар с вращающимися кольцами и свечением.
 * Нажатие обрабатывает вызывающий код (только в состоянии IDLE запускается распознавание).
 */
@Composable
fun JarvisOrb(state: OrbState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = paletteFor(state)
    val ring by animateColorAsState(palette.ring, tween(400), label = "ring")
    val glow by animateColorAsState(palette.glow, tween(400), label = "glow")
    val core by animateColorAsState(palette.core, tween(400), label = "core")

    val transition = rememberInfiniteTransition(label = "orb")
    val spinA by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(26_000, easing = LinearEasing)), label = "spinA")
    val spinB by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(34_000, easing = LinearEasing)), label = "spinB")
    val spinC by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(42_000, easing = LinearEasing)), label = "spinC")
    val spinDash by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(20_000, easing = LinearEasing)), label = "spinDash")
    val pulse by transition.animateFloat(
        1f, 1.05f,
        infiniteRepeatable(tween(2_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .size(260.dp)
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .semantics {
                contentDescription = "Говорить с Джарвисом"
                role = Role.Button
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(260.dp)) {
            val k = size.minDimension / 260f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Свечение вокруг шара
            drawCircle(
                brush = Brush.radialGradient(
                    0f to glow, 1f to Color.Transparent,
                    center = center,
                    radius = 121f * k
                ),
                radius = 121f * k,
                center = center
            )

            // Ядро шара
            val coreRadius = 75f * k
            drawCircle(
                brush = Brush.radialGradient(
                    0f to core, 0.75f to Background,
                    center = Offset(center.x - coreRadius + coreRadius * 0.8f, center.y - coreRadius + coreRadius * 0.7f),
                    radius = coreRadius * 1.6f
                ),
                radius = coreRadius,
                center = center
            )

            // Кольца: эллипсы разных наклонов, вращаются с разной скоростью
            drawRing(center, k, 118f, 44f, 0f, spinA, ring, 0.85f, dotAt = 118f)
            drawRing(center, k, 118f, 44f, 60f, -spinB, ring, 0.6f, dotAt = -118f)
            drawRing(center, k, 118f, 44f, 120f, spinC, ring, 0.45f, dotAt = null)

            // Пунктирная окружность
            rotate(degrees = 30f + spinDash, pivot = center) {
                drawCircle(
                    color = ring.copy(alpha = 0.7f),
                    radius = 92f * k,
                    center = center,
                    style = Stroke(
                        width = 0.6f * k,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * k, 6f * k))
                    )
                )
            }
        }
    }
}

private fun DrawScope.drawRing(
    center: Offset,
    k: Float,
    rx: Float,
    ry: Float,
    baseAngle: Float,
    spin: Float,
    color: Color,
    alpha: Float,
    dotAt: Float?,
) {
    rotate(degrees = baseAngle + spin, pivot = center) {
        drawOval(
            color = color.copy(alpha = alpha),
            topLeft = Offset(center.x - rx * k, center.y - ry * k),
            size = Size(2 * rx * k, 2 * ry * k),
            style = Stroke(width = 1f * k)
        )
        if (dotAt != null) {
            drawCircle(color = color, radius = 2.2f * k, center = Offset(center.x + dotAt * k, center.y))
        }
    }
}
