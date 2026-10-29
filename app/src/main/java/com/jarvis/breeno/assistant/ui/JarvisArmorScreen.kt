package com.jarvis.breeno.assistant.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

/*
 * Экран «Броня»: перенос макета orb-A.html на Compose.
 * Сфера, кольца, пыль, вспышки и дождь рисуются формулами от времени:
 * списков с изменяемым состоянием нет, перерисовывается только холст.
 */

/** Оттенки по кругу; долгое нажатие переключает их. */
private val PALETTE_HUES = listOf(40f, 196f, 272f, 152f, 338f)
private val ArmorBackground = Color(0xFF05080C)
private val AiOffColor = Color(0xFF55606E)
private const val GLYPHS = "01◊∆▯▮░▒"
private const val TAP_AREA_DP = 260
private const val TAU = 6.2831855f
private const val PI_F = 3.1415927f

private class ArmorRing(val rx: Float, val ry: Float, val w: Float)

private val GREAT_RINGS = listOf(
    ArmorRing(0f, 0f, 1.0f),
    ArmorRing((PI / 2).toFloat(), 0f, 0.9f),
    ArmorRing((PI / 4).toFloat(), (PI / 4).toFloat(), 0.8f),
    ArmorRing((-PI / 4).toFloat(), (PI / 3).toFloat(), 0.7f),
    ArmorRing((PI / 3).toFloat(), (-PI / 5).toFloat(), 0.6f),
    ArmorRing(0.3f, (PI / 2.4).toFloat(), 0.5f),
)

/**
 * Экран-шар с окантовкой в стиле брони.
 * Короткое нажатие в центре (260dp) вызывает onTap, долгое нажатие меняет палитру.
 */
@Composable
fun JarvisArmorScreen(
    state: OrbState,
    aiReady: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeSec = remember { mutableFloatStateOf(0f) }
    val kickSec = remember { mutableFloatStateOf(-100f) }
    val hueBase = remember { Animatable(PALETTE_HUES[0]) }
    var paletteIndex by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val currentOnTap by rememberUpdatedState(onTap)
    val rainPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
    }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> timeSec.floatValue = (now - start) / 1_000_000_000f }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArmorScene(
                t = timeSec.floatValue,
                state = state,
                aiReady = aiReady,
                hueBase = hueBase.value,
                kickAge = timeSec.floatValue - kickSec.floatValue,
                rainPaint = rainPaint,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(TAP_AREA_DP.dp)
                .semantics {
                    contentDescription = "Говорить с Джарвисом"
                    role = Role.Button
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            kickSec.floatValue = timeSec.floatValue
                            currentOnTap()
                        },
                        onLongPress = {
                            paletteIndex = (paletteIndex + 1) % PALETTE_HUES.size
                            val from = hueBase.value
                            val target = from + shortestDelta(from, PALETTE_HUES[paletteIndex])
                            kickSec.floatValue = timeSec.floatValue
                            scope.launch { hueBase.animateTo(target, tween(700)) }
                        },
                    )
                },
        )
    }
}

private fun shortestDelta(from: Float, to: Float): Float =
    ((to - from) % 360f + 540f) % 360f - 180f

private class Mode(val spin: Float, val act: Float)

private fun modeOf(state: OrbState): Mode = when (state) {
    OrbState.IDLE -> Mode(0.3f, 1.0f)
    OrbState.LISTENING -> Mode(0.5f, 1.3f)
    OrbState.SPEAKING -> Mode(0.55f, 1.5f)
}

/** Громкость «голоса» по формуле от времени: в покое 0, при слушании и ответе колеблется. */
private fun ampOf(state: OrbState, t: Float): Float = when (state) {
    OrbState.IDLE -> 0f
    OrbState.LISTENING -> 0.2f + 0.3f * abs(sin(t * 6.3f) * sin(t * 1.9f))
    OrbState.SPEAKING -> 0.15f + 0.5f * abs(sin(t * 9f) * sin(t * 3.1f) + 0.3f * sin(t * 15f))
}

/** Детерминированное «случайное» число от 0 до 1: одинаковое для одних и тех же аргументов. */
private fun hash(a: Int, b: Int = 0, c: Int = 0): Float {
    var h = a * 374761393 + b * 668265263 + c * 1274126177
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and Int.MAX_VALUE).toFloat() / Int.MAX_VALUE.toFloat()
}

/** HSL в Color: в Compose такой функции нет. hue в градусах, light от 0 до 1. */
private fun hsl(hue: Float, sat: Float, light: Float, alpha: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val c = (1f - abs(2f * light - 1f)) * sat
    val hp = h / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val m = light - c / 2f
    val (r, g, b) = when (hp.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        (r + m).coerceIn(0f, 1f),
        (g + m).coerceIn(0f, 1f),
        (b + m).coerceIn(0f, 1f),
        alpha.coerceIn(0f, 1f),
    )
}

/** Поворот точки вокруг двух осей, как в макете. */
private fun rotate3(p: Triple<Float, Float, Float>, rx: Float, ry: Float): Triple<Float, Float, Float> {
    val (px, py, pz) = p
    val y1 = py * cos(rx) - pz * sin(rx)
    val z1 = py * sin(rx) + pz * cos(rx)
    val x1 = px
    val x2 = x1 * cos(ry) + z1 * sin(ry)
    val z2 = -x1 * sin(ry) + z1 * cos(ry)
    return Triple(x2, y1, z2)
}

/** Точка на поверхности сферы: экранные координаты и глубина (больше 0 значит перед сферой). */
private fun spot(lat: Float, lon0: Float, spin: Float, rr: Float, cx: Float, cy: Float): Pair<Offset, Float> {
    val lon = lon0 + spin * 1.4f
    val vz = cos(lat) * cos(lon)
    val pos = Offset(cx + cos(lat) * sin(lon) * rr, cy + sin(lat) * rr * 0.94f)
    return Pair(pos, vz)
}

/** Мягкое светящееся пятно: цвет от центра к прозрачному краю. */
private fun DrawScope.glowDot(center: Offset, radius: Float, color: Color) {
    drawCircle(
        brush = Brush.radialGradient(
            0f to color,
            1f to Color.Transparent,
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
        blendMode = BlendMode.Plus,
    )
}

private fun octagon(l: Float, t: Float, r: Float, b: Float, cut: Float): Path = Path().apply {
    moveTo(l + cut, t)
    lineTo(r - cut, t)
    lineTo(r, t + cut)
    lineTo(r, b - cut)
    lineTo(r - cut, b)
    lineTo(l + cut, b)
    lineTo(l, b - cut)
    lineTo(l, t + cut)
    close()
}

private fun DrawScope.drawArmorScene(
    t: Float,
    state: OrbState,
    aiReady: Boolean,
    hueBase: Float,
    kickAge: Float,
    rainPaint: Paint,
) {
    val w = size.width
    val hgt = size.height
    val cx = w / 2f
    val cy = hgt / 2f
    // S: масштаб всей сцены, как k в макете (сторона экрана / 520).
    val s = minOf(w, hgt) / 520f
    val mode = modeOf(state)
    val spin = t * mode.spin
    val amp = ampOf(state, t)
    val hueShift = if (state == OrbState.SPEAKING) amp * 22f else 0f
    val h = hueBase + hueShift

    // light в процентах, как в макете.
    fun col(alpha: Float, light: Float, dh: Float = 0f): Color = hsl(h + dh, 1f, light / 100f, alpha)

    // Вспышка: раз в ~4.5 с, момент сдвигается по формуле; плюс короткая вспышка по нажатию.
    val evN = floor(t / 4.5f).toInt()
    val evAge = t - (evN * 4.5f + hash(evN, 1) * 3f)
    var flash = if (evAge >= 0f) (1f - evAge / 0.9f).coerceAtLeast(0f) * (0.5f + 0.4f * hash(evN, 2)) else 0f
    if (kickAge >= 0f) flash += (1f - kickAge / 0.9f).coerceAtLeast(0f) * 0.6f
    flash = flash.coerceIn(0f, 1f)

    val r = (100f + amp * 3f + flash * 3f) * s
    // Фон.
    drawRect(
        brush = Brush.radialGradient(
            0f to Color(0xFF0D1A24),
            0.72f to ArmorBackground,
            center = Offset(cx, cy),
            radius = maxOf(w, hgt) * 0.7f,
        ),
    )

    // Звёзды.
    for (i in 0 until 160) {
        val sx = hash(i, 31) * w
        val sy = hash(i, 32) * hgt
        val a = 0.12f + 0.2f * (0.5f + 0.5f * sin(t * (0.6f + hash(i, 33)) + hash(i, 34) * TAU))
        drawRect(
            color = Color(1f, 0.94f, 0.82f, a * 0.5f),
            topLeft = Offset(sx, sy),
            size = androidx.compose.ui.geometry.Size(s, s),
            blendMode = BlendMode.Plus,
        )
    }

    // Дождь из символов снаружи сферы (через android.graphics: в Compose нет текста на холсте).
    val stepY = 14f * s
    rainPaint.textSize = 12f * s
    val colW = 22f * density
    val cols = ceil(w / colW).toInt()
    for (i in 0 until cols) {
        val x = i * colW + colW / 2f
        val len = 6 + (hash(i, 2) * 6f).toInt()
        val speed = (40f + 90f * hash(i, 1)) * density
        val span = hgt + len * stepY
        val head = (hash(i, 3) * span + t * speed) % span
        for (j in 0 until len) {
            val py = head - j * stepY
            if (py < 0f || py > hgt) continue
            val dx = x - cx
            val dy = py - cy
            if (sqrt(dx * dx + dy * dy) < r * 1.3f) continue
            val fade = 1f - j.toFloat() / len
            val a = 0.05f + 0.3f * fade * (if (j == 0) 1.6f else 1f)
            rainPaint.color = col(a, if (j == 0) 85f else 60f).toArgb()
            val glyphIndex = (hash(i, j, (t * 4f).toInt()) * GLYPHS.length).toInt().coerceAtMost(GLYPHS.length - 1)
            drawContext.canvas.nativeCanvas.drawText(GLYPHS[glyphIndex].toString(), x, py, rainPaint)
        }
    }

    // Ореол вокруг сферы.
    val halo = r * 1.6f
    drawCircle(
        brush = Brush.radialGradient(
            0.375f to col(0.14f + flash * 0.08f, 50f),
            0.75f to col(0.04f, 45f, 10f),
            1f to Color.Transparent,
            center = Offset(cx, cy),
            radius = halo,
        ),
        radius = halo,
        center = Offset(cx, cy),
        blendMode = BlendMode.Plus,
    )

    // Всё внутри сферы обрезается по её контуру.
    val canvas = drawContext.canvas
    canvas.save()
    canvas.clipPath(Path().apply { addOval(Rect(Offset(cx, cy), r)) })

    drawCircle(
        brush = Brush.radialGradient(
            0f to col(0.16f, 50f),
            0.7f to col(0.06f, 40f, 8f),
            1f to col(0.1f, 35f, 16f),
            center = Offset(cx - r * 0.3f, cy - r * 0.3f),
            radius = r * 1.3f,
        ),
        radius = r,
        center = Offset(cx, cy),
    )

    // Дымка: мягкие пятна, чуть дышат.
    for (i in 0 until 4) {
        val ang = i * 1.7f
        val bx = cx + cos(ang) * r * 0.35f
        val by = cy + sin(ang * 0.8f + i) * r * 0.3f
        val br = r * (0.45f + 0.1f * sin(t * 0.4f + i))
        glowDot(Offset(bx, by), br, col(0.07f, 60f, i * 12f - 18f))
    }

    // Спицы, вращаются со сферой.
    for (i in 0 until 12) {
        val a = spin * 0.6f + i * (PI_F / 6f)
        drawLine(
            color = col(0.09f, 70f),
            start = Offset(cx, cy),
            end = Offset(cx + cos(a) * r, cy + sin(a) * r * 0.94f),
            strokeWidth = 0.6f * s,
        )
    }

    // Ядро.
    val coreR = r * 0.4f
    drawCircle(
        brush = Brush.radialGradient(
            0f to col(0.32f + amp * 0.1f, 75f),
            0.5f to col(0.12f, 60f),
            1f to Color.Transparent,
            center = Offset(cx, cy),
            radius = coreR,
        ),
        radius = coreR,
        center = Offset(cx, cy),
        blendMode = BlendMode.Plus,
    )

    // Большие круги: вращаются с разной скоростью, передняя часть ярче.
    val segments = 110
    for (ring in GREAT_RINGS) {
        val ryTotal = ring.ry + spin * ring.w
        val u = rotate3(Triple(1f, 0f, 0f), ring.rx, ryTotal)
        val v = rotate3(Triple(0f, 1f, 0f), ring.rx, ryTotal)
        fun ringPoint(i: Int): Triple<Float, Float, Float> {
            val th = i.toFloat() / segments * TAU
            val c = cos(th)
            val sn = sin(th)
            return Triple(
                c * u.first + sn * v.first,
                c * u.second + sn * v.second,
                c * u.third + sn * v.third,
            )
        }
        var prev = ringPoint(0)
        for (i in 1..segments) {
            val cur = ringPoint(i)
            val zMid = (prev.third + cur.third) / 2f
            val front = zMid > 0f
            val alpha = if (front) 0.18f + 0.22f * zMid else 0.05f
            drawLine(
                color = col(alpha * (0.85f + amp * 0.3f), 66f, zMid * 6f),
                start = Offset(cx + prev.first * r, cy + prev.second * r * 0.94f),
                end = Offset(cx + cur.first * r, cy + cur.second * r * 0.94f),
                strokeWidth = (if (front) 0.8f else 0.5f) * s,
            )
            prev = cur
        }
    }

    // Пыль внутри сферы: вращается вместе с ней.
    for (i in 0 until 90) {
        val rd = Math.cbrt(hash(i, 11).toDouble()).toFloat() * 0.92f
        val u = hash(i, 12) * 2f - 1f
        val a0 = hash(i, 13) * TAU
        val baseSize = (0.5f + 1.6f * hash(i, 14)) * s
        val tint = hash(i, 15)
        val ph = hash(i, 16) * TAU
        val ang = a0 + spin * 0.6f
        val rr = rd * r
        val px = cx + cos(ang) * rr * sqrt(1f - u * u)
        val py = cy + u * rr * 0.94f
        val tw = 0.4f + 0.6f * (0.5f + 0.5f * sin(t * 1.5f + ph))
        glowDot(Offset(px, py), baseSize * (1f + amp * 0.3f) * 3f, col(0.22f * tw * mode.act, 75f, tint * 30f - 15f))
    }

    // Слияние: точки подлетают снаружи и растворяются в поверхности.
    for (i in 0 until 12) {
        val life = 1.4f + 1.2f * hash(i, 51)
        val tt = t + hash(i, 52) * life
        val cyc = floor(tt / life).toInt()
        val age = tt - cyc * life
        val p = age / life
        val lat = asin(2f * hash(i, cyc, 53) - 1f)
        val lon = hash(i, cyc, 54) * TAU
        val r0 = 1.5f + 0.7f * hash(i, cyc, 55)
        val tint = hash(i, cyc, 56)
        val sizeM = (0.6f + 1.3f * hash(i, cyc, 57)) * s
        val ease = p * p
        val rr = r * (1f + (r0 - 1f) * (1f - ease))
        val (pos, vz) = spot(lat, lon, spin, rr, cx, cy)
        val alpha = 0.55f * p * (if (vz > 0f) 1f else 0.5f)
        val sz = sizeM * (1.2f + 0.8f * (1f - p))
        glowDot(pos, sz * 3f, col(alpha * mode.act, 80f, tint * 20f - 10f))

        // Короткая вспышка на поверхности сразу после слияния (точка предыдущего цикла).
        if (age < 0.5f) {
            val pl = cyc - 1
            val lat2 = asin(2f * hash(i, pl, 53) - 1f)
            val lon2 = hash(i, pl, 54) * TAU
            val tint2 = hash(i, pl, 56)
            val size2 = (0.6f + 1.3f * hash(i, pl, 57)) * s
            val f = sin(PI_F * age / 0.5f)
            val (pos2, vz2) = spot(lat2, lon2, spin, r, cx, cy)
            glowDot(
                pos2,
                size2 * (0.7f + 0.8f * f) * 3f,
                col(0.35f * f * (if (vz2 > 0f) 1f else 0.4f), 82f, tint2 * 20f - 10f),
            )
        }
    }

    // Вспышки на поверхности.
    for (i in 0 until 16) {
        val life = 0.8f + 1.4f * hash(i, 41)
        val tt = t + hash(i, 42) * life
        val cyc = floor(tt / life).toInt()
        val age = tt - cyc * life
        val p = (age / life).coerceAtMost(1f)
        val fade = sin(PI_F * p)
        val lat = asin(2f * hash(i, cyc, 43) - 1f)
        val lon = hash(i, cyc, 44) * TAU
        val tint = hash(i, cyc, 45)
        val lift = 0.92f + 0.1f * hash(i, cyc, 46)
        val (pos, vz) = spot(lat, lon, spin, r * lift, cx, cy)
        val glow = (0.5f + 0.5f * sin(t * 6f + tint * 9f)) * fade
        val sz = (0.5f + 1.2f * hash(i, cyc, 47)) * s * (0.7f + glow * 0.8f)
        glowDot(pos, sz * 3f, col(0.35f * glow * (if (vz > 0f) 1f else 0.4f), 82f, tint * 20f - 10f))
    }

    // Общая вспышка сферы.
    if (flash > 0.01f) {
        glowDot(Offset(cx, cy), r, col(flash * 0.18f, 88f))
    }

    canvas.restore()

    // Контур сферы: тонкий светящийся край.
    drawCircle(
        color = col(0.55f + flash * 0.3f, 78f),
        radius = r,
        center = Offset(cx, cy),
        style = Stroke(width = 1.2f * s),
    )
    drawCircle(
        color = col(0.12f, 70f),
        radius = r + 2f * s,
        center = Offset(cx, cy),
        style = Stroke(width = 3f * s),
    )

    // Окантовка в стиле брони: скошенная рама с панельными швами.
    val m = 12f * s
    val c = 34f * s
    drawPath(octagon(m, m, w - m, hgt - m, c), color = col(0.45f, 70f), style = Stroke(width = 1.4f * s))
    val inset = 20f * s
    drawPath(octagon(inset, inset, w - inset, hgt - inset, c * 0.7f), color = col(0.22f, 66f), style = Stroke(width = 0.6f * s))
    for ((a, b) in listOf(0.18f to 0.3f, 0.7f to 0.82f)) {
        val x1 = m + (w - 2f * m) * a
        val x2 = m + (w - 2f * m) * b
        drawLine(color = col(0.7f, 80f), start = Offset(x1, m), end = Offset(x2, m), strokeWidth = 2.2f * s)
        drawLine(color = col(0.7f, 80f), start = Offset(x1, hgt - m), end = Offset(x2, hgt - m), strokeWidth = 2.2f * s)
    }

    // Индикатор статуса ИИ: кружок на верхней линии рамки по центру. Цветной: готов, серый: нет.
    val dotCenter = Offset(cx, m)
    val dotRadius = 4.dp.toPx()
    val dotColor = if (aiReady) col(1f, 66f) else AiOffColor
    drawCircle(color = ArmorBackground, radius = dotRadius + 2.dp.toPx(), center = dotCenter)
    drawCircle(color = dotColor.copy(alpha = 0.3f), radius = dotRadius * 2f, center = dotCenter)
    drawCircle(color = dotColor, radius = dotRadius, center = dotCenter)
}
