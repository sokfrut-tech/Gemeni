package com.jarvis.breeno.assistant.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.breeno.assistant.core.JarvisOrbMode
import com.jarvis.breeno.assistant.ui.theme.LocalJarvisPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class CosmicStar(
    val theta: Float,
    val phi: Float,
    val radiusScale: Float,
    val sizeDp: Float,
    val colorMix: Float,
    val phase: Float,
)

private val COSMIC_STARS: List<CosmicStar> = List(72) { idx ->
    // Deterministic golden-spiral spherical distribution so the galaxy looks natural and balanced.
    val f = (idx + 0.5f) / 72f
    val phi = kotlin.math.acos(1f - 2f * f)
    val theta = (PI * (1.0 + kotlin.math.sqrt(5.0)) * idx).toFloat()
    val layer = if (idx % 3 == 0) 0.58f else if (idx % 3 == 1) 0.78f else 0.92f
    val size = 1.2f + ((idx * 17) % 10) * 0.22f
    val colorMix = ((idx * 37) % 100) / 100f
    CosmicStar(theta, phi, layer, size, colorMix, idx * 0.61f)
}

/**
 * Pure Jetpack Compose GPU-accelerated Marvel Iron Man J.A.R.V.I.S. Interactive Universe Orb:
 * - Zero GLSurfaceView / AndroidView (eliminates black square and ColorOS minimize white-screen lockups)
 * - Outer Stark Arc-Reactor holographic HUD with 36 audio-reactive equalizer ticks
 * - Inner 3D crystal universe with rotating nebula clouds and 72 depth-projected galaxy stars
 */
@Composable
fun JarvisInteractiveOrb(
    mode: JarvisOrbMode,
    audioRms: Float,
    orbSize: Dp,
    modifier: Modifier = Modifier,
) {
    val palette = LocalJarvisPalette.current
    val primaryAccent = when (mode) {
        JarvisOrbMode.IDLE -> palette.primaryAccent
        JarvisOrbMode.LISTENING -> Color(0xFF38F2FF)
        JarvisOrbMode.THINKING -> Color(0xFFA78BFA)
        JarvisOrbMode.SEARCHING -> Color(0xFF0EA5E9)
        JarvisOrbMode.EXECUTING -> palette.successAccent
        JarvisOrbMode.SPEAKING -> palette.secondaryAccent
        JarvisOrbMode.LEARNING -> Color(0xFFFBBF24)
        JarvisOrbMode.ERROR -> Color(0xFFFF5252)
    }

    val secondaryAccent = when (mode) {
        JarvisOrbMode.IDLE -> palette.secondaryAccent
        JarvisOrbMode.LISTENING -> palette.primaryAccent
        JarvisOrbMode.THINKING -> palette.primaryAccent
        JarvisOrbMode.SEARCHING -> Color(0xFF67E8F9)
        JarvisOrbMode.EXECUTING -> palette.primaryAccent
        JarvisOrbMode.SPEAKING -> palette.primaryAccent
        JarvisOrbMode.LEARNING -> palette.primaryAccent
        JarvisOrbMode.ERROR -> Color(0xFFFF8A80)
    }

    val smoothRms by animateFloatAsState(
        targetValue = when (mode) {
            JarvisOrbMode.LISTENING -> audioRms.coerceIn(0f, 1f)
            JarvisOrbMode.SPEAKING -> 0.45f
            JarvisOrbMode.THINKING, JarvisOrbMode.EXECUTING -> 0.25f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 85),
        label = "jarvisOrbRms",
    )

    val transition = rememberInfiniteTransition(label = "jarvisUniverse")
    val orbitDeg by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mode) {
                    JarvisOrbMode.THINKING, JarvisOrbMode.SEARCHING, JarvisOrbMode.EXECUTING -> 4200
                    JarvisOrbMode.LISTENING, JarvisOrbMode.SPEAKING -> 6200
                    else -> 12000
                },
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbitDeg",
    )

    val pulsePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulsePhase",
    )

    var userYaw by remember { mutableFloatStateOf(0f) }
    var userPitch by remember { mutableFloatStateOf(0.28f) }

    Box(
        modifier = modifier
            .size(orbSize + 44.dp)
            .semantics { contentDescription = "Интерактивное энергетическое ядро Джарвиса" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        userYaw += dragAmount.x * 0.012f
                        userPitch = (userPitch + dragAmount.y * 0.012f).coerceIn(-1.2f, 1.2f)
                    }
                },
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f
            val sphereRadius = maxRadius * 0.67f * (1f + smoothRms * 0.05f)
            val breathe = 0.5f + 0.5f * sin(pulsePhase)
            val orbitRad = (orbitDeg * PI / 180.0).toFloat()

            // 1. Soft atmospheric Stark bloom behind the orb (transparent outer edge -> no black square)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryAccent.copy(alpha = 0.34f + smoothRms * 0.24f),
                        secondaryAccent.copy(alpha = 0.14f + breathe * 0.06f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = maxRadius * 0.98f,
                ),
                radius = maxRadius * 0.98f,
                center = center,
            )

            // 2. Outer Stark HUD ring + 60 precision degree ticks + 36 audio equalizer rays
            val tickInner = sphereRadius * 1.15f
            for (i in 0 until 60) {
                val angle = ((i * 6f + orbitDeg * 0.2f) * PI / 180.0).toFloat()
                val isMajor = i % 5 == 0
                val wave = 0.5f + 0.5f * sin(pulsePhase * 2f + i * 0.45f)
                val extraLen = if (i % 2 == 0) smoothRms * (maxRadius * 0.14f) * wave else 0f
                val len = (if (isMajor) maxRadius * 0.075f else maxRadius * 0.038f) + extraLen
                val start = Offset(
                    center.x + tickInner * cos(angle),
                    center.y + tickInner * sin(angle),
                )
                val end = Offset(
                    center.x + (tickInner + len) * cos(angle),
                    center.y + (tickInner + len) * sin(angle),
                )
                drawLine(
                    color = (if (isMajor) secondaryAccent else primaryAccent).copy(alpha = if (isMajor) 0.78f else 0.42f),
                    start = start,
                    end = end,
                    strokeWidth = if (isMajor) 2.5f else 1.4f,
                    cap = StrokeCap.Round,
                )
            }

            // 3. Counter-rotating Stark Arc-Reactor holographic brackets
            val hudRadius1 = sphereRadius * 1.10f
            val hudRadius2 = sphereRadius * 1.28f
            for (arcIdx in 0 until 3) {
                val start = orbitDeg + arcIdx * 120f
                drawArc(
                    color = primaryAccent.copy(alpha = 0.78f),
                    startAngle = start,
                    sweepAngle = 68f,
                    useCenter = false,
                    topLeft = Offset(center.x - hudRadius1, center.y - hudRadius1),
                    size = androidx.compose.ui.geometry.Size(hudRadius1 * 2f, hudRadius1 * 2f),
                    style = Stroke(width = 3.2f + smoothRms * 2.5f, cap = StrokeCap.Round),
                )
            }
            for (arcIdx in 0 until 2) {
                val start = -orbitDeg * 1.35f + arcIdx * 180f
                drawArc(
                    color = secondaryAccent.copy(alpha = 0.72f),
                    startAngle = start,
                    sweepAngle = 52f,
                    useCenter = false,
                    topLeft = Offset(center.x - hudRadius2, center.y - hudRadius2),
                    size = androidx.compose.ui.geometry.Size(hudRadius2 * 2f, hudRadius2 * 2f),
                    style = Stroke(width = 2.4f, cap = StrokeCap.Round),
                )
            }

            // 4. Inner 3D Cosmic Universe Sphere (clipped strictly to circle)
            val spherePath = Path().apply {
                addOval(Rect(center = center, radius = sphereRadius))
            }
            clipPath(spherePath) {
                // Deep space backdrop
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0F2654),
                            Color(0xFF060E24),
                            Color(0xFF02040B),
                        ),
                        center = center + Offset(-sphereRadius * 0.18f, -sphereRadius * 0.18f),
                        radius = sphereRadius * 1.15f,
                    ),
                    radius = sphereRadius,
                    center = center,
                )

                // Swirling cosmic nebula clouds inside the sphere
                val nebula1Offset = Offset(
                    x = center.x + sphereRadius * 0.28f * cos(orbitRad + userYaw),
                    y = center.y + sphereRadius * 0.22f * sin(orbitRad + userPitch),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryAccent.copy(alpha = 0.34f + smoothRms * 0.16f),
                            Color(0xFF3B1D6E).copy(alpha = 0.28f),
                            Color.Transparent,
                        ),
                        center = nebula1Offset,
                        radius = sphereRadius * 0.85f,
                    ),
                    radius = sphereRadius * 0.85f,
                    center = nebula1Offset,
                )

                val nebula2Offset = Offset(
                    x = center.x - sphereRadius * 0.26f * cos(orbitRad * 0.7f - userYaw),
                    y = center.y - sphereRadius * 0.24f * sin(orbitRad * 0.7f + userPitch),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            secondaryAccent.copy(alpha = 0.26f + breathe * 0.10f),
                            Color(0xFF0B3A75).copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                        center = nebula2Offset,
                        radius = sphereRadius * 0.78f,
                    ),
                    radius = sphereRadius * 0.78f,
                    center = nebula2Offset,
                )

                // Rotating 3D galaxy particles & stars inside the universe
                val yaw = orbitRad + userYaw
                val pitch = userPitch
                val cosY = cos(yaw)
                val sinY = sin(yaw)
                val cosP = cos(pitch)
                val sinP = sin(pitch)

                for (star in COSMIC_STARS) {
                    val sx = sin(star.phi) * cos(star.theta)
                    val sy = cos(star.phi)
                    val sz = sin(star.phi) * sin(star.theta)

                    // Rotate around Y (spin) then X (tilt)
                    val x1 = sx * cosY - sz * sinY
                    val z1 = sx * sinY + sz * cosY
                    val y2 = sy * cosP - z1 * sinP
                    val z2 = sy * sinP + z1 * cosP

                    val depthNorm = ((z2 + 1f) * 0.5f).coerceIn(0.08f, 1f)
                    val perspective = 0.72f + 0.28f * depthNorm
                    val px = center.x + x1 * sphereRadius * star.radiusScale * perspective
                    val py = center.y + y2 * sphereRadius * star.radiusScale * perspective

                    val twinkle = 0.55f + 0.45f * sin(pulsePhase * 1.8f + star.phase)
                    val alpha = (0.25f + 0.75f * depthNorm) * twinkle
                    val starColor = when {
                        star.colorMix < 0.45f -> Color.White
                        star.colorMix < 0.78f -> primaryAccent
                        else -> secondaryAccent
                    }
                    drawCircle(
                        color = starColor.copy(alpha = alpha.coerceIn(0.1f, 0.98f)),
                        radius = star.sizeDp * (0.65f + 0.65f * depthNorm) + smoothRms * 1.2f,
                        center = Offset(px, py),
                    )
                }

                // Glowing J.A.R.V.I.S. energy core in the heart of the galaxy
                val coreRadius = sphereRadius * (0.26f + breathe * 0.05f + smoothRms * 0.14f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.92f),
                            primaryAccent.copy(alpha = 0.70f),
                            secondaryAccent.copy(alpha = 0.25f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = coreRadius * 1.8f,
                    ),
                    radius = coreRadius * 1.8f,
                    center = center,
                )

                // Internal Stark meridian wireframe rings
                drawCircle(
                    color = primaryAccent.copy(alpha = 0.28f),
                    radius = sphereRadius * 0.68f,
                    center = center,
                    style = Stroke(width = 1.4f),
                )
                drawCircle(
                    color = secondaryAccent.copy(alpha = 0.22f),
                    radius = sphereRadius * 0.44f,
                    center = center,
                    style = Stroke(width = 1.2f),
                )

                // Specular glass highlight on upper-left rim
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.24f), Color.Transparent),
                        center = center + Offset(-sphereRadius * 0.38f, -sphereRadius * 0.42f),
                        radius = sphereRadius * 0.55f,
                    ),
                    radius = sphereRadius * 0.55f,
                    center = center + Offset(-sphereRadius * 0.38f, -sphereRadius * 0.42f),
                )
            }

            // 5. Crisp crystal sphere rim
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        primaryAccent.copy(alpha = 0.92f),
                        secondaryAccent.copy(alpha = 0.78f),
                        primaryAccent.copy(alpha = 0.92f),
                    ),
                ),
                radius = sphereRadius,
                center = center,
                style = Stroke(width = 2.8f + smoothRms * 2.0f),
            )
        }
    }
}
