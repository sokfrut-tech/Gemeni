package com.jarvis.breeno.assistant.ui.theme

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class JarvisThemePreset(
    val id: String,
    val title: String,
    val subtitle: String,
) {
    STARK_HUD(
        id = "stark_hud",
        title = "Железный человек HUD",
        subtitle = "Голограмма Старка • Сапфир, Циан и Золото",
    ),
    MARK_85(
        id = "mark_85",
        title = "Броня Mark LXXXV",
        subtitle = "Титан-Рубин и Золото Реактора",
    ),
    ARC_CYBER(
        id = "arc_cyber",
        title = "Кибер-Неон",
        subtitle = "Глубокий индиго, бирюза и фиолет",
    ),
    BREENO_GLASS(
        id = "breeno_glass",
        title = "Breeno Стекло",
        subtitle = "Мягкий графит и небесный неон",
    ),
    EMERALD_MATRIX(
        id = "emerald_matrix",
        title = "Изумрудный терминал",
        subtitle = "Ботанический неон и янтарь",
    );

    companion object {
        fun fromId(id: String?): JarvisThemePreset = entries.firstOrNull { it.id == id } ?: STARK_HUD
    }
}

enum class JarvisFontStyle(
    val id: String,
    val title: String,
) {
    TECH_HUD("tech_hud", "Техно HUD"),
    MODERN_SANS("modern_sans", "Современный Sans"),
    CLASSIC_SERIF("classic_serif", "Элегантный Serif");

    companion object {
        fun fromId(id: String?): JarvisFontStyle = entries.firstOrNull { it.id == id } ?: TECH_HUD
    }
}

data class JarvisAppearanceConfig(
    val theme: JarvisThemePreset = JarvisThemePreset.STARK_HUD,
    val fontStyle: JarvisFontStyle = JarvisFontStyle.TECH_HUD,
    val fontScale: Float = 1.0f,
)

class JarvisAppearanceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("jarvis_appearance", Context.MODE_PRIVATE)

    fun load(): JarvisAppearanceConfig = JarvisAppearanceConfig(
        theme = JarvisThemePreset.fromId(prefs.getString(KEY_THEME, JarvisThemePreset.STARK_HUD.id)),
        fontStyle = JarvisFontStyle.fromId(prefs.getString(KEY_FONT_STYLE, JarvisFontStyle.TECH_HUD.id)),
        fontScale = prefs.getFloat(KEY_FONT_SCALE, 1.0f).coerceIn(0.90f, 1.18f),
    )

    fun save(config: JarvisAppearanceConfig) {
        prefs.edit()
            .putString(KEY_THEME, config.theme.id)
            .putString(KEY_FONT_STYLE, config.fontStyle.id)
            .putFloat(KEY_FONT_SCALE, config.fontScale.coerceIn(0.90f, 1.18f))
            .apply()
    }

    companion object {
        private const val KEY_THEME = "theme_preset"
        private const val KEY_FONT_STYLE = "font_style"
        private const val KEY_FONT_SCALE = "font_scale"
    }
}

data class JarvisPalette(
    val preset: JarvisThemePreset,
    val bgTop: Color,
    val bgMid: Color,
    val bgBottom: Color,
    val drawerBg: Color,
    val topBarBg: Color,
    val tileBg: Color,
    val tileBgAlt: Color,
    val tileBorder: Color,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val successAccent: Color,
    val errorAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val bubbleUser: Color,
    val bubbleAssistant: Color,
    val useHudCutCorners: Boolean,
)

fun paletteForTheme(preset: JarvisThemePreset): JarvisPalette = when (preset) {
    JarvisThemePreset.STARK_HUD -> JarvisPalette(
        preset = preset,
        bgTop = Color(0xFF04101F),
        bgMid = Color(0xFF0A1D36),
        bgBottom = Color(0xFF050E1B),
        drawerBg = Color(0xFF081629),
        topBarBg = Color(0xCC09182D),
        tileBg = Color(0xCC0D223D),
        tileBgAlt = Color(0xCC132C4D),
        tileBorder = Color(0x8800E5FF),
        primaryAccent = Color(0xFF00F0FF),
        secondaryAccent = Color(0xFFFFB700),
        successAccent = Color(0xFF34D399),
        errorAccent = Color(0xFFFF6E6E),
        textPrimary = Color(0xFFF1F7FF),
        textSecondary = Color(0xFF96B4D4),
        bubbleUser = Color(0xCC123960),
        bubbleAssistant = Color(0xCC0C203A),
        useHudCutCorners = true,
    )

    JarvisThemePreset.MARK_85 -> JarvisPalette(
        preset = preset,
        bgTop = Color(0xFF1A070D),
        bgMid = Color(0xFF2B0E17),
        bgBottom = Color(0xFF14060A),
        drawerBg = Color(0xFF1D0910),
        topBarBg = Color(0xCC240B14),
        tileBg = Color(0xCC2E1019),
        tileBgAlt = Color(0xCC3C1621),
        tileBorder = Color(0x88FFC107),
        primaryAccent = Color(0xFFFFC107),
        secondaryAccent = Color(0xFFFF4D6D),
        successAccent = Color(0xFF4ADE80),
        errorAccent = Color(0xFFFF8A8A),
        textPrimary = Color(0xFFFFF7F0),
        textSecondary = Color(0xFFD6A8B2),
        bubbleUser = Color(0xCC4B1927),
        bubbleAssistant = Color(0xCC290E17),
        useHudCutCorners = true,
    )

    JarvisThemePreset.ARC_CYBER -> JarvisPalette(
        preset = preset,
        bgTop = Color(0xFF0C0822),
        bgMid = Color(0xFF18113A),
        bgBottom = Color(0xFF0A071B),
        drawerBg = Color(0xFF110D2B),
        topBarBg = Color(0xCC140F33),
        tileBg = Color(0xCC1B1442),
        tileBgAlt = Color(0xCC241C55),
        tileBorder = Color(0x88A855F7),
        primaryAccent = Color(0xFF22D3EE),
        secondaryAccent = Color(0xFFC084FC),
        successAccent = Color(0xFF34D399),
        errorAccent = Color(0xFFFF7A90),
        textPrimary = Color(0xFFF5F3FF),
        textSecondary = Color(0xFFB4A9DA),
        bubbleUser = Color(0xCC32206B),
        bubbleAssistant = Color(0xCC19143B),
        useHudCutCorners = false,
    )

    JarvisThemePreset.BREENO_GLASS -> JarvisPalette(
        preset = preset,
        bgTop = Color(0xFF101828),
        bgMid = Color(0xFF1B283E),
        bgBottom = Color(0xFF111927),
        drawerBg = Color(0xFF141E30),
        topBarBg = Color(0xCC172338),
        tileBg = Color(0xCC1D2C45),
        tileBgAlt = Color(0xCC253754),
        tileBorder = Color(0x7760A5FA),
        primaryAccent = Color(0xFF38BDF8),
        secondaryAccent = Color(0xFFFBBF24),
        successAccent = Color(0xFF34D399),
        errorAccent = Color(0xFFFF8A8A),
        textPrimary = Color(0xFFF8FAFC),
        textSecondary = Color(0xFFA8B9D0),
        bubbleUser = Color(0xCC1E4470),
        bubbleAssistant = Color(0xCC1B283F),
        useHudCutCorners = false,
    )

    JarvisThemePreset.EMERALD_MATRIX -> JarvisPalette(
        preset = preset,
        bgTop = Color(0xFF051610),
        bgMid = Color(0xFF0B2A1E),
        bgBottom = Color(0xFF05130D),
        drawerBg = Color(0xFF071E16),
        topBarBg = Color(0xCC092319),
        tileBg = Color(0xCC0E2E22),
        tileBgAlt = Color(0xCC143B2C),
        tileBorder = Color(0x8810B981),
        primaryAccent = Color(0xFF34D399),
        secondaryAccent = Color(0xFFF59E0B),
        successAccent = Color(0xFF6EE7B7),
        errorAccent = Color(0xFFFF8A8A),
        textPrimary = Color(0xFFECFDF5),
        textSecondary = Color(0xFF8EC5B0),
        bubbleUser = Color(0xCC114734),
        bubbleAssistant = Color(0xCC0C281E),
        useHudCutCorners = false,
    )
}

val LocalJarvisPalette = staticCompositionLocalOf { paletteForTheme(JarvisThemePreset.STARK_HUD) }

fun jarvisTileShape(palette: JarvisPalette, cornerDp: Dp = 14.dp): Shape =
    if (palette.useHudCutCorners) CutCornerShape(topStart = cornerDp, bottomEnd = cornerDp, topEnd = 6.dp, bottomStart = 6.dp)
    else RoundedCornerShape(cornerDp)

fun jarvisScreenBackground(palette: JarvisPalette): Brush = Brush.verticalGradient(
    colors = listOf(palette.bgTop, palette.bgMid, palette.bgBottom),
)

private fun buildJarvisTypography(appearance: JarvisAppearanceConfig): Typography {
    val scale = appearance.fontScale.coerceIn(0.90f, 1.18f)
    // Only large screen headings use the decorative family; buttons, labels and body
    // always use a compact proportional face so Cyrillic labels never wrap into tall buttons.
    val displayFamily: FontFamily
    val bodyFamily: FontFamily
    val headingSpacing: Float

    when (appearance.fontStyle) {
        JarvisFontStyle.TECH_HUD -> {
            displayFamily = FontFamily.Monospace
            bodyFamily = FontFamily.SansSerif
            headingSpacing = 0.6f
        }
        JarvisFontStyle.MODERN_SANS -> {
            displayFamily = FontFamily.SansSerif
            bodyFamily = FontFamily.SansSerif
            headingSpacing = 0.3f
        }
        JarvisFontStyle.CLASSIC_SERIF -> {
            displayFamily = FontFamily.Serif
            bodyFamily = FontFamily.Serif
            headingSpacing = 0.2f
        }
    }

    return Typography(
        headlineSmall = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (20f * scale).sp,
            lineHeight = (26f * scale).sp,
            letterSpacing = headingSpacing.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (18f * scale).sp,
            lineHeight = (24f * scale).sp,
            letterSpacing = (headingSpacing * 0.6f).sp,
        ),
        titleMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (15f * scale).sp,
            lineHeight = (20f * scale).sp,
            letterSpacing = 0.1.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (13.5f * scale).sp,
            lineHeight = (18f * scale).sp,
            letterSpacing = 0.1.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (15.5f * scale).sp,
            lineHeight = (22f * scale).sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (14f * scale).sp,
            lineHeight = (20f * scale).sp,
        ),
        bodySmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (12.5f * scale).sp,
            lineHeight = (17f * scale).sp,
        ),
        labelLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (13.5f * scale).sp,
            lineHeight = (18f * scale).sp,
            letterSpacing = 0.1.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (12f * scale).sp,
            lineHeight = (16f * scale).sp,
            letterSpacing = 0.2.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (11f * scale).sp,
            lineHeight = (14f * scale).sp,
            letterSpacing = 0.2.sp,
        ),
    )
}

private val JarvisButtonPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)

private fun Color.darken(factor: Float): Color = Color(
    red = (red * factor).coerceIn(0f, 1f),
    green = (green * factor).coerceIn(0f, 1f),
    blue = (blue * factor).coerceIn(0f, 1f),
    alpha = alpha,
)

private fun Color.lighten(amount: Float): Color = Color(
    red = (red + (1f - red) * amount).coerceIn(0f, 1f),
    green = (green + (1f - green) * amount).coerceIn(0f, 1f),
    blue = (blue + (1f - blue) * amount).coerceIn(0f, 1f),
    alpha = alpha,
)

private data class StageParticle(
    val xFrac: Float,
    val yOffset: Float,
    val speed: Float,
    val depth: Float,
    val phase: Float,
    val useSecondary: Boolean,
)

private val STAGE_PARTICLES: List<StageParticle> = List(22) { idx ->
    StageParticle(
        xFrac = ((idx * 37 + 13) % 96 + 2) / 100f,
        yOffset = ((idx * 53 + 7) % 100) / 100f,
        speed = 0.35f + ((idx * 19) % 10) * 0.08f,
        depth = 0.35f + ((idx * 29) % 10) * 0.065f,
        phase = idx * 0.72f,
        useSecondary = idx % 3 == 0,
    )
}

/**
 * Game-engine-style 3D stage background rendered on GPU Canvas (zero GLSurfaceView overhead):
 * - Deep atmospheric lighting gradient
 * - Drifting volumetric radial bloom lights in theme accent colors
 * - 3D perspective holographic floor grid with moving depth lines toward a vanishing point
 * - Multi-depth floating light particles and subtle HUD visor corner brackets
 */
@Composable
fun Jarvis3DStageBackground(
    modifier: Modifier = Modifier,
    showGrid: Boolean = true,
    showHudFrame: Boolean = true,
) {
    val palette = LocalJarvisPalette.current
    val transition = rememberInfiniteTransition(label = "jarvis3dStage")
    val gridProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "gridScroll",
    )
    val driftPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "lightDrift",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) return@Canvas

        // 1. Deep atmospheric vertical base
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to palette.bgTop.darken(0.85f),
                    0.42f to palette.bgMid,
                    0.78f to palette.bgBottom,
                    1.0f to palette.bgTop.darken(0.75f),
                ),
            ),
        )

        // 2. Volumetric radial light orbs (drifting smoothly in 3D space)
        val light1Center = Offset(
            x = w * (0.22f + 0.08f * cos(driftPhase)),
            y = h * (0.18f + 0.04f * sin(driftPhase)),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.primaryAccent.copy(alpha = 0.20f),
                    palette.primaryAccent.copy(alpha = 0.06f),
                    Color.Transparent,
                ),
                center = light1Center,
                radius = w * 0.72f,
            ),
            radius = w * 0.72f,
            center = light1Center,
        )

        val light2Center = Offset(
            x = w * (0.82f + 0.07f * sin(driftPhase * 0.8f)),
            y = h * (0.58f + 0.05f * cos(driftPhase * 0.8f)),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.secondaryAccent.copy(alpha = 0.16f),
                    palette.secondaryAccent.copy(alpha = 0.04f),
                    Color.Transparent,
                ),
                center = light2Center,
                radius = w * 0.65f,
            ),
            radius = w * 0.65f,
            center = light2Center,
        )

        // 3. 3D Perspective Holographic Floor Grid
        if (showGrid) {
            val horizonY = h * 0.64f
            val vanishX = w * 0.5f

            // Horizon volumetric glow strip
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette.primaryAccent.copy(alpha = 0.13f),
                        palette.secondaryAccent.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                    startY = horizonY - h * 0.06f,
                    endY = horizonY + h * 0.10f,
                ),
                topLeft = Offset(0f, horizonY - h * 0.06f),
                size = Size(w, h * 0.16f),
            )

            // Converging perspective rays
            val rayCount = 12
            for (i in -rayCount / 2..rayCount / 2) {
                val spread = i / (rayCount / 2f)
                val bottomX = vanishX + spread * w * 1.45f
                val alpha = (0.24f - kotlin.math.abs(spread) * 0.12f).coerceAtLeast(0.05f)
                drawLine(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            palette.primaryAccent.copy(alpha = alpha),
                            palette.primaryAccent.copy(alpha = alpha * 0.35f),
                        ),
                        startY = horizonY,
                        endY = h,
                    ),
                    start = Offset(vanishX + spread * w * 0.16f, horizonY),
                    end = Offset(bottomX, h),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            // Moving horizontal depth lines with perspective foreshortening (t^2)
            val hLines = 8
            for (i in 0 until hLines) {
                val t = ((i + gridProgress) / hLines).coerceIn(0f, 1f)
                val y = horizonY + (h - horizonY) * (t * t)
                val lineAlpha = (0.04f + t * 0.24f) * (1f - (t - 0.85f).coerceAtLeast(0f) * 4f).coerceIn(0f, 1f)
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            palette.primaryAccent.copy(alpha = lineAlpha),
                            palette.secondaryAccent.copy(alpha = lineAlpha * 0.7f),
                            palette.primaryAccent.copy(alpha = lineAlpha),
                            Color.Transparent,
                        ),
                    ),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = (0.7f + t * 0.8f).dp.toPx(),
                )
            }
        }

        // 4. Multi-depth 3D floating light particles
        STAGE_PARTICLES.forEach { p ->
            val yFrac = (1f - ((p.yOffset + gridProgress * p.speed) % 1f)) * 0.92f + 0.04f
            val xDrift = 0.015f * sin(driftPhase + p.phase) * p.depth
            val cx = w * (p.xFrac + xDrift).coerceIn(0.02f, 0.98f)
            val cy = h * yFrac
            val color = if (p.useSecondary) palette.secondaryAccent else palette.primaryAccent
            val radius = (1.1f + p.depth * 2.2f).dp.toPx()
            val alpha = 0.16f + p.depth * 0.36f
            drawCircle(
                color = color.copy(alpha = alpha * 0.28f),
                radius = radius * 2.6f,
                center = Offset(cx, cy),
            )
            drawCircle(
                color = color.lighten(0.35f).copy(alpha = alpha),
                radius = radius,
                center = Offset(cx, cy),
            )
        }

        // 5. Subtle 3D HUD corner visor brackets
        if (showHudFrame && palette.useHudCutCorners) {
            val inset = 10.dp.toPx()
            val arm = 18.dp.toPx()
            val stroke = 1.2.dp.toPx()
            val bracketColor = palette.primaryAccent.copy(alpha = 0.28f)
            // Top-left
            drawLine(bracketColor, Offset(inset, inset), Offset(inset + arm, inset), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(bracketColor, Offset(inset, inset), Offset(inset, inset + arm), strokeWidth = stroke, cap = StrokeCap.Round)
            // Top-right
            drawLine(bracketColor, Offset(w - inset - arm, inset), Offset(w - inset, inset), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(bracketColor, Offset(w - inset, inset), Offset(w - inset, inset + arm), strokeWidth = stroke, cap = StrokeCap.Round)
            // Bottom-left
            drawLine(bracketColor, Offset(inset, h - inset), Offset(inset + arm, h - inset), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(bracketColor, Offset(inset, h - inset - arm), Offset(inset, h - inset), strokeWidth = stroke, cap = StrokeCap.Round)
            // Bottom-right
            drawLine(bracketColor, Offset(w - inset - arm, h - inset), Offset(w - inset, h - inset), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(bracketColor, Offset(w - inset, h - inset - arm), Offset(w - inset, h - inset), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

/**
 * 3D holographic emitter pedestal rendered directly beneath the floating Jarvis orb.
 */
@Composable
fun JarvisHoloPedestal(
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    val palette = LocalJarvisPalette.current
    val c1 = accent ?: palette.primaryAccent
    val c2 = palette.secondaryAccent
    val transition = rememberInfiniteTransition(label = "holoPedestal")
    val pulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pedestalPulse",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.68f

        // Upward volumetric projection beam cone
        val beamPath = Path().apply {
            moveTo(cx - w * 0.24f, cy)
            lineTo(cx - w * 0.42f, 0f)
            lineTo(cx + w * 0.42f, 0f)
            lineTo(cx + w * 0.24f, cy)
            close()
        }
        drawPath(
            path = beamPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    c1.copy(alpha = 0.08f * pulse),
                    c1.copy(alpha = 0.22f * pulse),
                ),
            ),
        )

        // Concentric 3D elliptical projection rings
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    c1.copy(alpha = 0.34f * pulse),
                    c2.copy(alpha = 0.10f * pulse),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = w * 0.48f,
            ),
            topLeft = Offset(cx - w * 0.46f, cy - h * 0.26f),
            size = Size(w * 0.92f, h * 0.52f),
        )
        drawOval(
            color = c1.copy(alpha = 0.65f * pulse),
            topLeft = Offset(cx - w * 0.42f, cy - h * 0.22f),
            size = Size(w * 0.84f, h * 0.44f),
            style = Stroke(width = 1.3.dp.toPx()),
        )
        drawOval(
            color = c2.copy(alpha = 0.55f * pulse),
            topLeft = Offset(cx - w * 0.27f, cy - h * 0.14f),
            size = Size(w * 0.54f, h * 0.28f),
            style = Stroke(width = 1.1.dp.toPx()),
        )
    }
}

/**
 * Miniature live 3D scene preview for the theme selector cards in Settings.
 */
@Composable
fun JarvisTheme3DPreviewScene(
    preset: JarvisThemePreset,
    modifier: Modifier = Modifier,
) {
    val preview = paletteForTheme(preset)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val horizonY = h * 0.68f

        // Background depth gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(preview.bgTop.darken(0.8f), preview.bgMid, preview.bgBottom),
            ),
        )

        // Perspective mini-grid
        for (i in -3..3) {
            val spread = i / 3f
            drawLine(
                color = preview.primaryAccent.copy(alpha = 0.28f),
                start = Offset(cx + spread * w * 0.14f, horizonY),
                end = Offset(cx + spread * w * 0.72f, h),
                strokeWidth = 0.8.dp.toPx(),
            )
        }
        for (row in 1..3) {
            val t = row / 3f
            val y = horizonY + (h - horizonY) * (t * t)
            drawLine(
                color = preview.primaryAccent.copy(alpha = 0.18f + t * 0.16f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 0.8.dp.toPx(),
            )
        }

        // 3D floating orb with specular lighting + tilted energy ring
        val orbCenter = Offset(cx, h * 0.40f)
        val orbRadius = minOf(w, h) * 0.24f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    preview.primaryAccent.copy(alpha = 0.42f),
                    preview.secondaryAccent.copy(alpha = 0.14f),
                    Color.Transparent,
                ),
                center = orbCenter,
                radius = orbRadius * 2.1f,
            ),
            radius = orbRadius * 2.1f,
            center = orbCenter,
        )
        drawOval(
            color = preview.secondaryAccent.copy(alpha = 0.78f),
            topLeft = Offset(orbCenter.x - orbRadius * 1.45f, orbCenter.y - orbRadius * 0.42f),
            size = Size(orbRadius * 2.9f, orbRadius * 0.84f),
            style = Stroke(width = 1.4.dp.toPx()),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.92f),
                    preview.primaryAccent,
                    preview.bgTop.darken(0.6f),
                ),
                center = Offset(orbCenter.x - orbRadius * 0.34f, orbCenter.y - orbRadius * 0.34f),
                radius = orbRadius * 1.25f,
            ),
            radius = orbRadius,
            center = orbCenter,
        )
    }
}

/**
 * Volumetric 3D card surface with colored ambient shadow, 3D bottom thickness bevel,
 * convex metallic/glass gradient, top specular rim light and crisp accent border.
 */
@Composable
fun Jarvis3DCardSurface(
    modifier: Modifier = Modifier,
    cornerDp: Dp = 14.dp,
    accent: Color? = null,
    elevationDp: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalJarvisPalette.current
    val resolvedAccent = accent ?: palette.primaryAccent
    val shape = jarvisTileShape(palette, cornerDp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (onClick != null && pressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "card3dScale",
    )
    val tiltX by animateFloatAsState(
        targetValue = if (onClick != null && pressed) 2.5f else 0f,
        animationSpec = tween(durationMillis = 90),
        label = "card3dTilt",
    )
    val pressOffset by animateDpAsState(
        targetValue = if (onClick != null && pressed) 1.5.dp else 0.dp,
        animationSpec = tween(durationMillis = 90),
        label = "card3dOffset",
    )

    val outerModifier = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            rotationX = tiltX
            translationY = pressOffset.toPx()
            cameraDistance = 16f * density
        }
        .shadow(
            elevation = if (pressed) 3.dp else elevationDp,
            shape = shape,
            clip = false,
            ambientColor = resolvedAccent.copy(alpha = 0.55f),
            spotColor = resolvedAccent.copy(alpha = 0.65f),
        )

    val innerBody: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.bgTop.darken(0.55f))
                .padding(bottom = 2.5.dp)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            palette.tileBgAlt.lighten(0.08f).copy(alpha = 0.94f),
                            palette.tileBg.copy(alpha = 0.92f),
                            palette.bgBottom.darken(0.85f).copy(alpha = 0.96f),
                        ),
                    ),
                )
                .drawBehind {
                    // Top-left volumetric specular glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                resolvedAccent.copy(alpha = 0.14f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.15f, 0f),
                            radius = size.width * 0.65f,
                        ),
                        radius = size.width * 0.65f,
                        center = Offset(size.width * 0.15f, 0f),
                    )
                    // Specular top edge highlight (3D rim light)
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.34f),
                                resolvedAccent.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.24f),
                                Color.Transparent,
                            ),
                        ),
                        start = Offset(size.width * 0.04f, 1.dp.toPx()),
                        end = Offset(size.width * 0.96f, 1.dp.toPx()),
                        strokeWidth = 1.1.dp.toPx(),
                    )
                },
            content = content,
        )
    }

    val border = BorderStroke(
        width = 1.1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                resolvedAccent.copy(alpha = 0.72f),
                palette.secondaryAccent.copy(alpha = 0.30f),
                resolvedAccent.copy(alpha = 0.22f),
            ),
        ),
    )

    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = Color.Transparent,
            border = border,
            interactionSource = interactionSource,
            modifier = outerModifier,
            content = innerBody,
        )
    } else {
        Surface(
            shape = shape,
            color = Color.Transparent,
            border = border,
            modifier = outerModifier,
            content = innerBody,
        )
    }
}

/**
 * Compact 3D game-engine-style filled button (38dp):
 * extruded 3D bottom lip, convex glossy surface gradient, top specular highlight,
 * neon ambient shadow and physical press-down depth animation.
 */
@Composable
fun JarvisButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val palette = LocalJarvisPalette.current
    val baseContainer = if (enabled) {
        colors?.containerColor ?: palette.primaryAccent
    } else {
        (colors?.disabledContainerColor ?: palette.tileBg).copy(alpha = 0.55f)
    }
    val contentColor = if (enabled) {
        colors?.contentColor ?: Color(0xFF04101B)
    } else {
        palette.textSecondary.copy(alpha = 0.65f)
    }
    val shape = jarvisTileShape(palette, 10.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.965f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "btn3dScale",
    )
    val tiltX by animateFloatAsState(
        targetValue = if (enabled && pressed) 4f else 0f,
        animationSpec = tween(durationMillis = 80),
        label = "btn3dTilt",
    )
    val lipHeight by animateDpAsState(
        targetValue = if (enabled && pressed) 1.dp else 3.dp,
        animationSpec = tween(durationMillis = 80),
        label = "btn3dLip",
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = Color.Transparent,
        interactionSource = interactionSource,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (enabled) 0.55f else 0.14f),
                    baseContainer.darken(0.55f),
                ),
            ),
        ),
        modifier = modifier
            .heightIn(min = 38.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationX = tiltX
                translationY = if (enabled && pressed) 2.dp.toPx() else 0f
                cameraDistance = 14f * density
            }
            .shadow(
                elevation = if (!enabled) 0.dp else if (pressed) 3.dp else 9.dp,
                shape = shape,
                clip = false,
                ambientColor = baseContainer.copy(alpha = 0.65f),
                spotColor = baseContainer.copy(alpha = 0.85f),
            ),
    ) {
        Box(
            modifier = Modifier
                .background(baseContainer.darken(0.45f))
                .padding(bottom = lipHeight)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to baseContainer.lighten(0.24f),
                            0.46f to baseContainer,
                            1.0f to baseContainer.darken(0.74f),
                        ),
                    ),
                )
                .drawWithContent {
                    // Upper glossy 3D highlight band
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (enabled) 0.30f else 0.06f),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = size.height * 0.48f,
                        ),
                    )
                    drawContent()
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.padding(JarvisButtonPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    ProvideTextStyle(
                        MaterialTheme.typography.labelLarge.copy(
                            color = contentColor,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * Compact 3D glass-bevel outlined button (38dp) with neon rim light, depth shadow and 3D press.
 */
@Composable
fun JarvisOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val palette = LocalJarvisPalette.current
    val contentColor = if (enabled) {
        colors?.contentColor ?: palette.textPrimary
    } else {
        palette.textSecondary.copy(alpha = 0.55f)
    }
    val shape = jarvisTileShape(palette, 10.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.965f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "outBtn3dScale",
    )
    val lipHeight by animateDpAsState(
        targetValue = if (enabled && pressed) 0.5.dp else 2.dp,
        animationSpec = tween(durationMillis = 80),
        label = "outBtn3dLip",
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = Color.Transparent,
        interactionSource = interactionSource,
        border = BorderStroke(
            width = 1.1.dp,
            brush = Brush.linearGradient(
                colors = if (enabled) {
                    listOf(
                        palette.primaryAccent.copy(alpha = 0.85f),
                        palette.secondaryAccent.copy(alpha = 0.45f),
                    )
                } else {
                    listOf(palette.tileBorder.copy(alpha = 0.25f), palette.tileBorder.copy(alpha = 0.15f))
                },
            ),
        ),
        modifier = modifier
            .heightIn(min = 38.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationX = if (enabled && pressed) 3f else 0f
                translationY = if (enabled && pressed) 1.5.dp.toPx() else 0f
                cameraDistance = 14f * density
            }
            .shadow(
                elevation = if (!enabled) 0.dp else if (pressed) 2.dp else 6.dp,
                shape = shape,
                clip = false,
                ambientColor = palette.primaryAccent.copy(alpha = 0.45f),
                spotColor = palette.primaryAccent.copy(alpha = 0.55f),
            ),
    ) {
        Box(
            modifier = Modifier
                .background(palette.bgTop.darken(0.55f))
                .padding(bottom = lipHeight)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.tileBgAlt.lighten(0.10f).copy(alpha = 0.88f),
                            palette.tileBg.copy(alpha = 0.84f),
                            palette.bgBottom.darken(0.85f).copy(alpha = 0.92f),
                        ),
                    ),
                )
                .drawBehind {
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = if (enabled) 0.34f else 0.08f),
                                Color.Transparent,
                            ),
                        ),
                        start = Offset(size.width * 0.08f, 1.dp.toPx()),
                        end = Offset(size.width * 0.92f, 1.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.padding(JarvisButtonPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(color = contentColor)) {
                        content()
                    }
                }
            }
        }
    }
}

/** Small 3D beveled HUD chip (≈34dp) for quick shortcuts. */
@Composable
fun JarvisHudChip(
    label: String,
    icon: ImageVector? = null,
    accent: Color? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalJarvisPalette.current
    val resolvedAccent = accent ?: palette.primaryAccent
    val shape = jarvisTileShape(palette, 10.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "chip3dScale",
    )

    Surface(
        onClick = onClick,
        shape = shape,
        color = Color.Transparent,
        interactionSource = interactionSource,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    resolvedAccent.copy(alpha = 0.82f),
                    palette.secondaryAccent.copy(alpha = 0.35f),
                ),
            ),
        ),
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationX = if (pressed) 3.5f else 0f
                translationY = if (pressed) 1.5.dp.toPx() else 0f
                cameraDistance = 14f * density
            }
            .shadow(
                elevation = if (pressed) 2.dp else 6.dp,
                shape = shape,
                clip = false,
                ambientColor = resolvedAccent.copy(alpha = 0.50f),
                spotColor = resolvedAccent.copy(alpha = 0.65f),
            ),
    ) {
        Box(
            modifier = Modifier
                .background(palette.bgTop.darken(0.55f))
                .padding(bottom = if (pressed) 0.5.dp else 2.dp)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.tileBgAlt.lighten(0.10f).copy(alpha = 0.92f),
                            palette.tileBg.copy(alpha = 0.90f),
                        ),
                    ),
                )
                .drawBehind {
                    drawLine(
                        color = Color.White.copy(alpha = 0.28f),
                        start = Offset(size.width * 0.1f, 1.dp.toPx()),
                        end = Offset(size.width * 0.9f, 1.dp.toPx()),
                        strokeWidth = 0.9.dp.toPx(),
                    )
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        resolvedAccent.copy(alpha = 0.35f),
                                        resolvedAccent.copy(alpha = 0.10f),
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = resolvedAccent.lighten(0.15f),
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                Text(
                    text = label,
                    color = palette.textPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun JarvisGlassTile(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    accent: Color? = null,
    badge: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalJarvisPalette.current
    val resolvedAccent = accent ?: palette.primaryAccent

    Jarvis3DCardSurface(
        modifier = modifier,
        cornerDp = 12.dp,
        accent = resolvedAccent,
        elevationDp = 8.dp,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .shadow(
                            elevation = 4.dp,
                            shape = CircleShape,
                            clip = false,
                            ambientColor = resolvedAccent.copy(alpha = 0.6f),
                            spotColor = resolvedAccent.copy(alpha = 0.8f),
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    resolvedAccent.lighten(0.25f).copy(alpha = 0.40f),
                                    resolvedAccent.copy(alpha = 0.14f),
                                    palette.bgTop.copy(alpha = 0.85f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = resolvedAccent.lighten(0.18f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = title,
                    color = palette.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = palette.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!badge.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = resolvedAccent.copy(alpha = 0.18f),
                    border = BorderStroke(0.8.dp, resolvedAccent.copy(alpha = 0.55f)),
                ) {
                    Text(
                        text = badge,
                        color = resolvedAccent.lighten(0.15f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun JarvisTheme(
    appearance: JarvisAppearanceConfig = JarvisAppearanceConfig(),
    content: @Composable () -> Unit,
) {
    val palette = paletteForTheme(appearance.theme)
    val scheme = darkColorScheme(
        primary = palette.primaryAccent,
        onPrimary = Color(0xFF04101B),
        secondary = palette.secondaryAccent,
        background = palette.bgBottom,
        surface = palette.tileBg,
        surfaceVariant = palette.tileBgAlt,
        onBackground = palette.textPrimary,
        onSurface = palette.textPrimary,
        outline = palette.tileBorder,
        error = palette.errorAccent,
    )
    CompositionLocalProvider(LocalJarvisPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = buildJarvisTypography(appearance),
            content = content,
        )
    }
}
