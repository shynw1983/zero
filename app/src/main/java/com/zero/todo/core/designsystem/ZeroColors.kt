package com.zero.todo.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.zero.todo.R

/**
 * Calm, understated architectural accent options for ZERO.
 * Adapts subtly between Dark and Light modes for serene, non-sci-fi visual clarity.
 */
enum class ZeroAccent(
    val darkColor: Color,
    val onDarkAccent: Color,
    val lightColor: Color,
    val onLightAccent: Color,
    val darkHexLabel: String,
    @StringRes val labelRes: Int
) {
    SlateBlue(
        darkColor = Color(0xFF7B9CC2),
        onDarkAccent = Color(0xFF09090B),
        lightColor = Color(0xFF3B5B82),
        onLightAccent = Color(0xFFFFFFFF),
        darkHexLabel = "#7B9CC2",
        labelRes = R.string.settings_accent_slate
    ),
    Monochrome(
        darkColor = Color(0xFFE4E4E7),
        onDarkAccent = Color(0xFF09090B),
        lightColor = Color(0xFF18181B),
        onLightAccent = Color(0xFFFFFFFF),
        darkHexLabel = "#E4E4E7",
        labelRes = R.string.settings_accent_monochrome
    ),
    Sage(
        darkColor = Color(0xFF7AA38D),
        onDarkAccent = Color(0xFF09090B),
        lightColor = Color(0xFF3E6B54),
        onLightAccent = Color(0xFFFFFFFF),
        darkHexLabel = "#7AA38D",
        labelRes = R.string.settings_accent_sage
    ),
    WarmClay(
        darkColor = Color(0xFFC98365),
        onDarkAccent = Color(0xFF09090B),
        lightColor = Color(0xFFA85A3B),
        onLightAccent = Color(0xFFFFFFFF),
        darkHexLabel = "#C98365",
        labelRes = R.string.settings_accent_clay
    );

    fun colorFor(isDark: Boolean): Color = if (isDark) darkColor else lightColor
    fun onAccentFor(isDark: Boolean): Color = if (isDark) onDarkAccent else onLightAccent
    fun subtleContainerFor(isDark: Boolean): Color =
        colorFor(isDark).copy(alpha = if (isDark) 0.16f else 0.11f)

    fun next(): ZeroAccent {
        val list = entries
        return list[(ordinal + 1) % list.size]
    }

    companion object {
        fun fromStorage(raw: String?): ZeroAccent = when (raw) {
            "SignalOrange" -> WarmClay
            "CyberCyan" -> SlateBlue
            else -> entries.firstOrNull { it.name == raw } ?: SlateBlue
        }
    }
}

object ZeroPaletteTokens {
    // Dark Mode (Default)
    val DarkBackground = Color(0xFF09090B) // Matte Obsidian
    val DarkSurface = Color(0xFF141417) // Carbon
    val DarkSurfaceElevated = Color(0xFF1C1C21)
    val DarkHairlineBorder = Color(0xFF27272A)
    val DarkTextPrimary = Color(0xFFF4F4F5)
    val DarkTextSecondary = Color(0xFFA1A1AA)
    val DarkTextMuted = Color(0xFF63636B)

    // Light Mode
    val LightBackground = Color(0xFFF7F7F9) // Cool Paper
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceElevated = Color(0xFFF0F0F3)
    val LightHairlineBorder = Color(0xFFE4E4E7)
    val LightTextPrimary = Color(0xFF09090B)
    val LightTextSecondary = Color(0xFF71717A)
    val LightTextMuted = Color(0xFFA1A1AA)

    // Restrained, editorial priority tones (zero neon glare)
    val PriorityUrgentDark = Color(0xFFD66A6A)
    val PriorityUrgentLight = Color(0xFFB84343)
    val PriorityHighDark = Color(0xFFCFA052)
    val PriorityHighLight = Color(0xFFAE7B24)
    val PriorityMediumDark = Color(0xFF7B9CC2)
    val PriorityMediumLight = Color(0xFF3B5B82)
}

@Immutable
data class ZeroColorPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val hairlineBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSubtle: Color,
    val priorityUrgent: Color,
    val priorityHigh: Color,
    val priorityMedium: Color,
    val priorityNone: Color
)

fun zeroDarkPalette(accent: ZeroAccent = ZeroAccent.SlateBlue): ZeroColorPalette =
    ZeroColorPalette(
        isDark = true,
        background = ZeroPaletteTokens.DarkBackground,
        surface = ZeroPaletteTokens.DarkSurface,
        surfaceElevated = ZeroPaletteTokens.DarkSurfaceElevated,
        hairlineBorder = ZeroPaletteTokens.DarkHairlineBorder,
        textPrimary = ZeroPaletteTokens.DarkTextPrimary,
        textSecondary = ZeroPaletteTokens.DarkTextSecondary,
        textMuted = ZeroPaletteTokens.DarkTextMuted,
        accent = accent.colorFor(isDark = true),
        onAccent = accent.onAccentFor(isDark = true),
        accentSubtle = accent.subtleContainerFor(isDark = true),
        priorityUrgent = ZeroPaletteTokens.PriorityUrgentDark,
        priorityHigh = ZeroPaletteTokens.PriorityHighDark,
        priorityMedium = ZeroPaletteTokens.PriorityMediumDark,
        priorityNone = ZeroPaletteTokens.DarkTextMuted
    )

fun zeroLightPalette(accent: ZeroAccent = ZeroAccent.SlateBlue): ZeroColorPalette =
    ZeroColorPalette(
        isDark = false,
        background = ZeroPaletteTokens.LightBackground,
        surface = ZeroPaletteTokens.LightSurface,
        surfaceElevated = ZeroPaletteTokens.LightSurfaceElevated,
        hairlineBorder = ZeroPaletteTokens.LightHairlineBorder,
        textPrimary = ZeroPaletteTokens.LightTextPrimary,
        textSecondary = ZeroPaletteTokens.LightTextSecondary,
        textMuted = ZeroPaletteTokens.LightTextMuted,
        accent = accent.colorFor(isDark = false),
        onAccent = accent.onAccentFor(isDark = false),
        accentSubtle = accent.subtleContainerFor(isDark = false),
        priorityUrgent = ZeroPaletteTokens.PriorityUrgentLight,
        priorityHigh = ZeroPaletteTokens.PriorityHighLight,
        priorityMedium = ZeroPaletteTokens.PriorityMediumLight,
        priorityNone = ZeroPaletteTokens.LightTextMuted
    )

fun ZeroColorPalette.toMaterialColorScheme(): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSubtle,
        onPrimaryContainer = textPrimary,
        secondary = textSecondary,
        onSecondary = background,
        secondaryContainer = surfaceElevated,
        onSecondaryContainer = textPrimary,
        tertiary = accent,
        onTertiary = onAccent,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceElevated,
        onSurfaceVariant = textSecondary,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceElevated,
        surfaceContainerHighest = surfaceElevated,
        outline = hairlineBorder,
        outlineVariant = hairlineBorder,
        error = priorityUrgent,
        onError = onAccent
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSubtle,
        onPrimaryContainer = textPrimary,
        secondary = textSecondary,
        onSecondary = surface,
        secondaryContainer = surfaceElevated,
        onSecondaryContainer = textPrimary,
        tertiary = accent,
        onTertiary = onAccent,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceElevated,
        onSurfaceVariant = textSecondary,
        surfaceContainerLowest = surface,
        surfaceContainerLow = background,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceElevated,
        surfaceContainerHighest = surfaceElevated,
        outline = hairlineBorder,
        outlineVariant = hairlineBorder,
        error = priorityUrgent,
        onError = onAccent
    )
}

val LocalZeroColors = staticCompositionLocalOf<ZeroColorPalette> {
    zeroDarkPalette()
}

val LocalZeroAccent = staticCompositionLocalOf {
    ZeroAccent.SlateBlue
}

fun parseFolderColorHex(hex: String?): Color? {
    val trimmed = hex?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { Color(android.graphics.Color.parseColor(trimmed)) }.getOrNull()
}
