package com.jarvis.breeno.assistant.ui.theme

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

private val JarvisButtonPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)

/** Compact HUD-styled filled button (38dp, single-weight label, cut/rounded corners from the theme). */
@Composable
fun JarvisButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val palette = LocalJarvisPalette.current
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 38.dp),
        enabled = enabled,
        shape = jarvisTileShape(palette, 10.dp),
        colors = colors ?: ButtonDefaults.buttonColors(
            containerColor = palette.primaryAccent,
            contentColor = Color(0xFF04101B),
        ),
        contentPadding = JarvisButtonPadding,
    ) {
        val rowScope = this
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) {
            rowScope.content()
        }
    }
}

/** Compact HUD-styled outlined button with a thin accent border on a translucent glass background. */
@Composable
fun JarvisOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val palette = LocalJarvisPalette.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 38.dp),
        enabled = enabled,
        shape = jarvisTileShape(palette, 10.dp),
        colors = colors ?: ButtonDefaults.outlinedButtonColors(
            containerColor = palette.tileBg.copy(alpha = 0.55f),
            contentColor = palette.textPrimary,
        ),
        border = BorderStroke(
            1.dp,
            if (enabled) palette.primaryAccent.copy(alpha = 0.45f) else palette.tileBorder.copy(alpha = 0.35f),
        ),
        contentPadding = JarvisButtonPadding,
    ) {
        val rowScope = this
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            rowScope.content()
        }
    }
}

/** Small pill-like HUD chip (≈32dp) for quick shortcuts. */
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
    Surface(
        onClick = onClick,
        shape = jarvisTileShape(palette, 10.dp),
        color = palette.tileBg.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, resolvedAccent.copy(alpha = 0.40f)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = resolvedAccent,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = label,
                color = palette.textPrimary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
    val shape = jarvisTileShape(palette, 12.dp)

    Surface(
        onClick = onClick,
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    resolvedAccent.copy(alpha = 0.55f),
                    palette.secondaryAccent.copy(alpha = 0.25f),
                ),
            ),
        ),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(palette.tileBgAlt.copy(alpha = 0.85f), palette.tileBg.copy(alpha = 0.85f)),
                    ),
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(resolvedAccent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = resolvedAccent,
                        modifier = Modifier.size(15.dp),
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
                    color = resolvedAccent.copy(alpha = 0.16f),
                ) {
                    Text(
                        text = badge,
                        color = resolvedAccent,
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
