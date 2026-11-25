package com.zero.todo.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Functional single-accent options defined by the ZERO Near-Future Minimalism spec.
 * Strictly reserved for active focus state, Pomodoro progress, P1 priority indicator,
 * and active input cursor.
 */
enum class ZeroAccent(
    val color: Color,
    val onAccent: Color,
    val subtleContainerDark: Color,
    val subtleContainerLight: Color
) {
    SignalOrange(
        color = Color(0xFFFF4D00),
        onAccent = Color(0xFFFFFFFF),
        subtleContainerDark = Color(0x26FF4D00),
        subtleContainerLight = Color(0x1AFF4D00)
    ),
    CyberCyan(
        color = Color(0xFF00E5FF),
        onAccent = Color(0xFF09090B),
        subtleContainerDark = Color(0x2600E5FF),
        subtleContainerLight = Color(0x1F00E5FF)
    )
}

object ZeroPaletteTokens {
    // Dark Mode (Default)
    val DarkBackground = Color(0xFF09090B) // Matte Obsidian
    val DarkSurface = Color(0xFF141417) // Carbon
    val DarkSurfaceElevated = Color(0xFF1C1C21)
    val DarkHairlineBorder = Color(0xFF27272A)
    val DarkTextPrimary = Color(0xFFF4F4F5)
    val DarkTextSecondary = Color(0xFFA1A1AA)
    val DarkTextMuted = Color(0xFF52525B)

    // Light Mode
    val LightBackground = Color(0xFFF7F7F9) // Cool Paper
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceElevated = Color(0xFFF0F0F3)
    val LightHairlineBorder = Color(0xFFE4E4E7)
    val LightTextPrimary = Color(0xFF09090B)
    val LightTextSecondary = Color(0xFF71717A)
    val LightTextMuted = Color(0xFFA1A1AA)

    // Priority accents (restrained, functional)
    val PriorityHigh = Color(0xFFF59E0B)
    val PriorityMedium = Color(0xFF3B82F6)
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

fun zeroDarkPalette(accent: ZeroAccent = ZeroAccent.SignalOrange): ZeroColorPalette =
    ZeroColorPalette(
        isDark = true,
        background = ZeroPaletteTokens.DarkBackground,
        surface = ZeroPaletteTokens.DarkSurface,
        surfaceElevated = ZeroPaletteTokens.DarkSurfaceElevated,
        hairlineBorder = ZeroPaletteTokens.DarkHairlineBorder,
        textPrimary = ZeroPaletteTokens.DarkTextPrimary,
        textSecondary = ZeroPaletteTokens.DarkTextSecondary,
        textMuted = ZeroPaletteTokens.DarkTextMuted,
        accent = accent.color,
        onAccent = accent.onAccent,
        accentSubtle = accent.subtleContainerDark,
        priorityUrgent = accent.color,
        priorityHigh = ZeroPaletteTokens.PriorityHigh,
        priorityMedium = ZeroPaletteTokens.PriorityMedium,
        priorityNone = ZeroPaletteTokens.DarkTextMuted
    )

fun zeroLightPalette(accent: ZeroAccent = ZeroAccent.SignalOrange): ZeroColorPalette =
    ZeroColorPalette(
        isDark = false,
        background = ZeroPaletteTokens.LightBackground,
        surface = ZeroPaletteTokens.LightSurface,
        surfaceElevated = ZeroPaletteTokens.LightSurfaceElevated,
        hairlineBorder = ZeroPaletteTokens.LightHairlineBorder,
        textPrimary = ZeroPaletteTokens.LightTextPrimary,
        textSecondary = ZeroPaletteTokens.LightTextSecondary,
        textMuted = ZeroPaletteTokens.LightTextMuted,
        accent = accent.color,
        onAccent = accent.onAccent,
        accentSubtle = accent.subtleContainerLight,
        priorityUrgent = accent.color,
        priorityHigh = ZeroPaletteTokens.PriorityHigh,
        priorityMedium = ZeroPaletteTokens.PriorityMedium,
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
        error = accent,
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
        error = accent,
        onError = onAccent
    )
}

val LocalZeroColors = staticCompositionLocalOf<ZeroColorPalette> {
    zeroDarkPalette()
}

val LocalZeroAccent = staticCompositionLocalOf {
    ZeroAccent.SignalOrange
}
