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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    MECH_ARMOR(
        id = "mech_armor",
        title = "Меха-Броня 3D",
        subtitle = "Титан, бронзовые шестерни, рубин и реактор",
    ),
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
        fun fromId(id: String?): JarvisThemePreset = entries.firstOrNull { it.id == id } ?: MECH_ARMOR
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
    val theme: JarvisThemePreset = JarvisThemePreset.MECH_ARMOR,
    val fontStyle: JarvisFontStyle = JarvisFontStyle.TECH_HUD,
    val fontScale: Float = 1.0f,
)

class JarvisAppearanceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("jarvis_appearance", Context.MODE_PRIVATE)

    fun load(): JarvisAppearanceConfig = JarvisAppearanceConfig(
        theme = JarvisThemePreset.fromId(prefs.getString(KEY_THEME, JarvisThemePreset.MECH_ARMOR.id)),
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
    val useMechArmor: Boolean = false,
)

fun paletteForTheme(preset: JarvisThemePreset): JarvisPalette = when (preset) {
    JarvisThemePreset.MECH_ARMOR -> JarvisPalette(
        preset = preset,
        bgTop = Color(0xFF0E1116),
        bgMid = Color(0xFF1A1F28),
        bgBottom = Color(0xFF0A0C10),
        drawerBg = Color(0xFF12161D),
        topBarBg = Color(0xD8151A22),
        tileBg = Color(0xE01B202A),
        tileBgAlt = Color(0xE82A313E),
        tileBorder = Color(0xAA6A768C),
        primaryAccent = Color(0xFF38F2FF),
        secondaryAccent = Color(0xFFD9A054),
        successAccent = Color(0xFF34D399),
        errorAccent = Color(0xFFFF2438),
        textPrimary = Color(0xFFF3F6FA),
        textSecondary = Color(0xFFA2AEBF),
        bubbleUser = Color(0xE0252E3B),
        bubbleAssistant = Color(0xE0181D26),
        useHudCutCorners = true,
        useMechArmor = true,
    )

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
        useMechArmor = true,
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

val LocalJarvisPalette = staticCompositionLocalOf { paletteForTheme(JarvisThemePreset.MECH_ARMOR) }

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

private fun DrawScope.drawMechBolt(
    center: Offset,
    radius: Float,
    metalColor: Color = Color(0xFF8C97A8),
    ringColor: Color = Color(0xFFD9A054),
) {
    // Deep recessed socket shadow
    drawCircle(
        color = Color(0xFF040507),
        radius = radius * 1.38f,
        center = Offset(center.x, center.y + radius * 0.18f),
    )
    // Outer metallic collar
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(metalColor.lighten(0.32f), metalColor.darken(0.38f)),
            start = Offset(center.x - radius, center.y - radius),
            end = Offset(center.x + radius, center.y + radius),
        ),
        radius = radius * 1.15f,
        center = center,
    )
    // Bronze / titanium inner ring
    drawCircle(
        color = ringColor.copy(alpha = 0.85f),
        radius = radius * 0.72f,
        center = center,
        style = Stroke(width = (radius * 0.24f).coerceAtLeast(1f)),
    )
    // Dark hex core
    drawCircle(
        color = Color(0xFF0A0D12),
        radius = radius * 0.44f,
        center = center,
    )
    // Top-left specular catchlight
    drawCircle(
        color = Color.White.copy(alpha = 0.72f),
        radius = (radius * 0.22f).coerceAtLeast(0.8f),
        center = Offset(center.x - radius * 0.36f, center.y - radius * 0.36f),
    )
}

private fun DrawScope.drawRubyLed(
    center: Offset,
    radius: Float,
    pulse: Float,
) {
    // Outer ruby bloom
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFF2438).copy(alpha = 0.55f * pulse),
                Color(0xFFFF1428).copy(alpha = 0.14f * pulse),
                Color.Transparent,
            ),
            center = center,
            radius = radius * 2.5f,
        ),
        radius = radius * 2.5f,
        center = center,
    )
    // Steel bezel
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF6B7484), Color(0xFF151920)),
            start = Offset(center.x - radius, center.y - radius),
            end = Offset(center.x + radius, center.y + radius),
        ),
        radius = radius * 1.24f,
        center = center,
    )
    // Glossy 3D ruby dome
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFF8A96),
                Color(0xFFE6192E),
                Color(0xFF68040E),
            ),
            center = Offset(center.x - radius * 0.28f, center.y - radius * 0.28f),
            radius = radius * 1.1f,
        ),
        radius = radius,
        center = center,
    )
    // Crisp specular glass reflection
    drawCircle(
        color = Color.White.copy(alpha = 0.82f),
        radius = radius * 0.28f,
        center = Offset(center.x - radius * 0.32f, center.y - radius * 0.32f),
    )
}

private fun DrawScope.drawMechGear(
    center: Offset,
    outerRadius: Float,
    teeth: Int,
    angleRad: Float,
    metalColor: Color,
) {
    val rootRadius = outerRadius * 0.80f
    val innerRim = outerRadius * 0.58f
    val hubRadius = outerRadius * 0.28f
    val step = (2.0 * PI / teeth).toFloat()

    val gearPath = Path()
    for (i in 0 until teeth) {
        val base = angleRad + i * step
        val a0 = base
        val a1 = base + step * 0.18f
        val a2 = base + step * 0.44f
        val a3 = base + step * 0.62f

        val p0 = Offset(center.x + cos(a0) * rootRadius, center.y + sin(a0) * rootRadius)
        val p1 = Offset(center.x + cos(a1) * outerRadius, center.y + sin(a1) * outerRadius)
        val p2 = Offset(center.x + cos(a2) * outerRadius, center.y + sin(a2) * outerRadius)
        val p3 = Offset(center.x + cos(a3) * rootRadius, center.y + sin(a3) * rootRadius)

        if (i == 0) gearPath.moveTo(p0.x, p0.y) else gearPath.lineTo(p0.x, p0.y)
        gearPath.lineTo(p1.x, p1.y)
        gearPath.lineTo(p2.x, p2.y)
        gearPath.lineTo(p3.x, p3.y)
    }
    gearPath.close()

    // Soft drop shadow offset downward
    drawCircle(
        color = Color(0xAA030406),
        radius = outerRadius * 0.96f,
        center = Offset(center.x, center.y + outerRadius * 0.10f),
    )
    // Metallic 3D toothed gear body
    drawPath(
        path = gearPath,
        brush = Brush.linearGradient(
            colors = listOf(
                metalColor.lighten(0.32f),
                metalColor,
                metalColor.darken(0.38f),
            ),
            start = Offset(center.x - outerRadius, center.y - outerRadius),
            end = Offset(center.x + outerRadius, center.y + outerRadius),
        ),
    )
    drawPath(
        path = gearPath,
        color = metalColor.lighten(0.45f).copy(alpha = 0.55f),
        style = Stroke(width = 1.dp.toPx()),
    )
    // Recessed dark cavity
    drawCircle(
        color = Color(0xFF0A0D12),
        radius = innerRim,
        center = center,
    )
    // 5 metallic spokes
    for (s in 0 until 5) {
        val a = angleRad + s * (2.0 * PI / 5.0).toFloat()
        drawLine(
            color = metalColor.darken(0.75f),
            start = Offset(center.x + cos(a) * hubRadius, center.y + sin(a) * hubRadius),
            end = Offset(center.x + cos(a) * innerRim, center.y + sin(a) * innerRim),
            strokeWidth = (outerRadius * 0.10f).coerceAtLeast(2f),
            cap = StrokeCap.Round,
        )
    }
    // Beveled inner rim ring
    drawCircle(
        color = metalColor.lighten(0.20f),
        radius = innerRim,
        center = center,
        style = Stroke(width = (outerRadius * 0.06f).coerceAtLeast(1.5f)),
    )
    // Center 3D hub bolt
    drawMechBolt(center = center, radius = hubRadius * 0.72f, metalColor = metalColor, ringColor = metalColor.lighten(0.25f))
}

private fun DrawScope.drawMechArmorStage(
    w: Float,
    h: Float,
    gearAngleRad: Float,
    pulse: Float,
    palette: JarvisPalette,
) {
    val titaniumLight = Color(0xFF394250)
    val titaniumMid = Color(0xFF1F242E)
    val titaniumDark = Color(0xFF11141B)
    val bronze = palette.secondaryAccent

    // 1. Deep brushed gunmetal base
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(palette.bgTop, palette.bgMid, palette.bgBottom),
        ),
    )

    // 2. Rotating 3D bronze & titanium gears (lower chassis + side bays)
    drawMechGear(
        center = Offset(w * 0.50f, h * 0.91f),
        outerRadius = w * 0.25f,
        teeth = 16,
        angleRad = gearAngleRad,
        metalColor = bronze,
    )
    drawMechGear(
        center = Offset(w * 0.06f, h * 0.76f),
        outerRadius = w * 0.11f,
        teeth = 12,
        angleRad = -gearAngleRad * 1.5f,
        metalColor = bronze.darken(0.85f),
    )
    drawMechGear(
        center = Offset(w * 0.94f, h * 0.75f),
        outerRadius = w * 0.13f,
        teeth = 12,
        angleRad = -gearAngleRad * 1.35f,
        metalColor = bronze,
    )
    drawMechGear(
        center = Offset(w * 0.96f, h * 0.89f),
        outerRadius = w * 0.11f,
        teeth = 10,
        angleRad = gearAngleRad * 1.65f,
        metalColor = titaniumLight,
    )

    // 3. Symmetrical top 3D armored titanium shoulder plates
    val topLeftPlate = Path().apply {
        moveTo(0f, 0f)
        lineTo(w * 0.40f, 0f)
        lineTo(w * 0.34f, h * 0.11f)
        lineTo(w * 0.18f, h * 0.135f)
        lineTo(0f, h * 0.10f)
        close()
    }
    val topRightPlate = Path().apply {
        moveTo(w, 0f)
        lineTo(w * 0.60f, 0f)
        lineTo(w * 0.66f, h * 0.11f)
        lineTo(w * 0.82f, h * 0.135f)
        lineTo(w, h * 0.10f)
        close()
    }
    listOf(topLeftPlate, topRightPlate).forEach { plate ->
        drawPath(
            path = plate,
            brush = Brush.verticalGradient(
                colors = listOf(titaniumLight, titaniumMid, titaniumDark),
                startY = 0f,
                endY = h * 0.14f,
            ),
        )
        drawPath(
            path = plate,
            color = titaniumLight.lighten(0.28f).copy(alpha = 0.65f),
            style = Stroke(width = 1.2.dp.toPx()),
        )
    }

    // 4. Top central 3D bronze hydraulic piston (matching reference)
    val pistonW = w * 0.115f
    val pistonH = h * 0.12f
    val pistonLeft = w * 0.5f - pistonW * 0.5f
    drawRect(
        color = Color(0xCC040507),
        topLeft = Offset(pistonLeft - 3.dp.toPx(), 0f),
        size = Size(pistonW + 6.dp.toPx(), pistonH + 4.dp.toPx()),
    )
    drawRect(
        brush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0.0f to bronze.darken(0.32f),
                0.35f to bronze.lighten(0.35f),
                0.62f to bronze,
                1.0f to bronze.darken(0.26f),
            ),
            startX = pistonLeft,
            endX = pistonLeft + pistonW,
        ),
        topLeft = Offset(pistonLeft, 0f),
        size = Size(pistonW, pistonH),
    )
    // Ribbed rings on the bronze piston
    for (r in 1..4) {
        val ry = pistonH * (r / 5.2f)
        drawLine(
            color = Color(0xBB0A0B0E),
            start = Offset(pistonLeft, ry),
            end = Offset(pistonLeft + pistonW, ry),
            strokeWidth = 2.dp.toPx(),
        )
    }
    // Conical bronze tip
    val tipPath = Path().apply {
        moveTo(pistonLeft, pistonH)
        lineTo(pistonLeft + pistonW, pistonH)
        lineTo(w * 0.5f, pistonH + w * 0.065f)
        close()
    }
    drawPath(
        path = tipPath,
        brush = Brush.horizontalGradient(
            colors = listOf(bronze.darken(0.35f), bronze.lighten(0.35f), bronze.darken(0.30f)),
            startX = pistonLeft,
            endX = pistonLeft + pistonW,
        ),
    )

    // 5. Left & right 3D side armor rails with machined grooves
    val railW = w * 0.085f
    val leftRail = Path().apply {
        moveTo(0f, h * 0.17f)
        lineTo(railW, h * 0.22f)
        lineTo(railW * 0.85f, h * 0.63f)
        lineTo(0f, h * 0.68f)
        close()
    }
    val rightRail = Path().apply {
        moveTo(w, h * 0.17f)
        lineTo(w - railW, h * 0.22f)
        lineTo(w - railW * 0.85f, h * 0.63f)
        lineTo(w, h * 0.68f)
        close()
    }
    listOf(leftRail, rightRail).forEach { rail ->
        drawPath(
            path = rail,
            brush = Brush.verticalGradient(
                colors = listOf(titaniumLight, titaniumMid, titaniumDark),
                startY = h * 0.17f,
                endY = h * 0.68f,
            ),
        )
        drawPath(
            path = rail,
            color = titaniumLight.lighten(0.24f).copy(alpha = 0.55f),
            style = Stroke(width = 1.dp.toPx()),
        )
    }

    // 6. Machined 3D bolts along the chassis
    val boltR = w * 0.022f
    drawMechBolt(Offset(w * 0.08f, h * 0.055f), boltR, titaniumLight, bronze)
    drawMechBolt(Offset(w * 0.25f, h * 0.075f), boltR * 0.85f, titaniumLight, bronze)
    drawMechBolt(Offset(w * 0.92f, h * 0.055f), boltR, titaniumLight, bronze)
    drawMechBolt(Offset(w * 0.75f, h * 0.075f), boltR * 0.85f, titaniumLight, bronze)
    drawMechBolt(Offset(w * 0.065f, h * 0.34f), boltR * 1.15f, titaniumLight, bronze)
    drawMechBolt(Offset(w * 0.935f, h * 0.34f), boltR * 1.15f, titaniumLight, bronze)
    drawMechBolt(Offset(w * 0.065f, h * 0.58f), boltR * 1.15f, titaniumLight, bronze)
    drawMechBolt(Offset(w * 0.935f, h * 0.58f), boltR * 1.15f, titaniumLight, bronze)

    // 7. Central ambient Arc-reactor backlight glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                palette.primaryAccent.copy(alpha = 0.18f * pulse),
                palette.primaryAccent.copy(alpha = 0.05f * pulse),
                Color.Transparent,
            ),
            center = Offset(w * 0.5f, h * 0.33f),
            radius = w * 0.55f,
        ),
        radius = w * 0.55f,
        center = Offset(w * 0.5f, h * 0.33f),
    )

    // 8. Signature glossy 3D ruby LEDs (top center + lower flanks)
    val ledR = w * 0.024f
    drawRubyLed(Offset(w * 0.50f, pistonH + w * 0.105f), ledR * 0.82f, pulse)
    drawRubyLed(Offset(w * 0.22f, h * 0.62f), ledR, pulse)
    drawRubyLed(Offset(w * 0.78f, h * 0.62f), ledR, pulse)
}

/**
 * 3D armored titanium aperture ring rendered around JarvisInteractiveOrb in MECH_ARMOR / MARK_85 themes.
 */
@Composable
fun JarvisMechApertureRing(
    ringSize: Dp,
    accent: Color? = null,
    modifier: Modifier = Modifier,
) {
    val palette = LocalJarvisPalette.current
    val resolvedAccent = accent ?: palette.primaryAccent
    val bronze = palette.secondaryAccent
    val transition = rememberInfiniteTransition(label = "mechAperture")
    val pulse by transition.animateFloat(
        initialValue = 0.78f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aperturePulse",
    )

    Canvas(modifier = modifier.size(ringSize)) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val outerR = size.minDimension * 0.49f
        val ringThick = size.minDimension * 0.078f
        val midR = outerR - ringThick * 0.5f

        // Deep drop shadow behind the armor ring
        drawCircle(
            color = Color(0xCC020305),
            radius = outerR + 3.dp.toPx(),
            center = Offset(cx, cy + 3.dp.toPx()),
        )
        // Dark void inside the aperture
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    resolvedAccent.copy(alpha = 0.18f * pulse),
                    Color(0xFF06080C),
                ),
                center = Offset(cx, cy),
                radius = outerR - ringThick,
            ),
            radius = outerR - ringThick,
            center = Offset(cx, cy),
        )
        // 3D beveled titanium main ring
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF5A6577),
                    Color(0xFF282F3B),
                    Color(0xFF13171E),
                    Color(0xFF3E4756),
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            ),
            radius = midR,
            center = Offset(cx, cy),
            style = Stroke(width = ringThick),
        )
        // Outer metallic bevel highlight
        drawCircle(
            color = Color.White.copy(alpha = 0.30f),
            radius = outerR,
            center = Offset(cx, cy),
            style = Stroke(width = 1.1.dp.toPx()),
        )
        // Inner reactor cyan glow rim
        drawCircle(
            color = resolvedAccent.copy(alpha = 0.72f * pulse),
            radius = outerR - ringThick,
            center = Offset(cx, cy),
            style = Stroke(width = 1.6.dp.toPx()),
        )
        // 8 machined bronze bolts evenly spaced around the titanium ring
        val boltR = ringThick * 0.30f
        for (i in 0 until 8) {
            val a = (i * 45.0 + 22.5) * PI / 180.0
            val bx = cx + cos(a).toFloat() * midR
            val by = cy + sin(a).toFloat() * midR
            drawMechBolt(Offset(bx, by), boltR, Color(0xFF7E899B), bronze)
        }
        // Two glossy ruby indicator gems at the lower flanks (matching reference)
        val rubyR = ringThick * 0.44f
        drawRubyLed(Offset(cx - outerR * 0.72f, cy + outerR * 0.72f), rubyR, pulse)
        drawRubyLed(Offset(cx + outerR * 0.72f, cy + outerR * 0.72f), rubyR, pulse)
    }
}

/**
 * Game-engine-style 3D stage background rendered on GPU Canvas (zero GLSurfaceView overhead):
 * - In MECH_ARMOR / MARK_85 themes: full 3D titanium armor chassis with rotating bronze gears,
 *   central hydraulic piston, machined bolts and glossy ruby LEDs
 * - In Holographic / Cyber / Glass themes: atmospheric lighting, 3D perspective floor grid and particles
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
    val mechPulse by transition.animateFloat(
        initialValue = 0.76f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mechPulse",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) return@Canvas

        if (palette.useMechArmor) {
            drawMechArmorStage(
                w = w,
                h = h,
                gearAngleRad = driftPhase,
                pulse = mechPulse,
                palette = palette,
            )
            return@Canvas
        }

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

        if (preview.useMechArmor) {
            // Miniature mechanical armor scene: gear + armored ring + ruby LEDs + arc core
            drawMechGear(
                center = Offset(cx, h * 0.94f),
                outerRadius = h * 0.34f,
                teeth = 12,
                angleRad = 0.25f,
                metalColor = preview.secondaryAccent,
            )
            val ringR = h * 0.34f
            val orbCenter = Offset(cx, h * 0.44f)
            drawCircle(
                color = Color(0xFF3A4352),
                radius = ringR,
                center = orbCenter,
                style = Stroke(width = 4.5.dp.toPx()),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, preview.primaryAccent, Color.Transparent),
                    center = orbCenter,
                    radius = ringR * 0.72f,
                ),
                radius = ringR * 0.72f,
                center = orbCenter,
            )
            drawRubyLed(Offset(cx - ringR * 1.15f, h * 0.72f), 3.2.dp.toPx(), 1f)
            drawRubyLed(Offset(cx + ringR * 1.15f, h * 0.72f), 3.2.dp.toPx(), 1f)
            return@Canvas
        }

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
                    // Corner machined bolts on armored 3D themes
                    if (palette.useMechArmor) {
                        val bInset = 6.dp.toPx()
                        val bRad = 2.0.dp.toPx()
                        drawMechBolt(Offset(bInset, bInset), bRad, Color(0xFF7D889B), palette.secondaryAccent)
                        drawMechBolt(Offset(size.width - bInset, bInset), bRad, Color(0xFF7D889B), palette.secondaryAccent)
                        drawMechBolt(Offset(bInset, size.height - bInset), bRad, Color(0xFF7D889B), palette.secondaryAccent)
                        drawMechBolt(Offset(size.width - bInset, size.height - bInset), bRad, Color(0xFF7D889B), palette.secondaryAccent)
                    }
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
