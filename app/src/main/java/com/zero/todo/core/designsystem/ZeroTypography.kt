package com.zero.todo.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * UI / Body font family: System Sans / Noto Sans SC with clean weight contrast
 * (Medium 500 for titles, Regular 400 for body).
 */
val ZeroSansFontFamily: FontFamily = FontFamily.SansSerif

/**
 * Monospace font family used with tabular numerals (`tnum`) for all timestamps,
 * dates, counters, and timers.
 */
val ZeroMonoFontFamily: FontFamily = FontFamily.Monospace

const val TABULAR_NUMBERS_FEATURE = "tnum"

val ZeroTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = ZeroSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = ZeroSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = ZeroSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = ZeroSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    ),
    titleMedium = TextStyle(
        fontFamily = ZeroSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = ZeroSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = ZeroSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ZeroSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = ZeroSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp
    ),
    labelLarge = TextStyle(
        fontFamily = ZeroSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = ZeroSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = ZeroSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    )
)

/**
 * Mandatory Tabular Numeric Typography (`fontFeatureSettings = "tnum"`).
 * Guarantees pixel-perfect vertical alignment for timers, counters, dates, and timestamps.
 */
@Immutable
data class ZeroTabularTypography(
    val timerHero: TextStyle = TextStyle(
        fontFamily = ZeroMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 56.sp,
        lineHeight = 64.sp,
        letterSpacing = (-1.0).sp,
        fontFeatureSettings = TABULAR_NUMBERS_FEATURE
    ),
    val timerMedium: TextStyle = TextStyle(
        fontFamily = ZeroMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
        fontFeatureSettings = TABULAR_NUMBERS_FEATURE
    ),
    val counter: TextStyle = TextStyle(
        fontFamily = ZeroMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = TABULAR_NUMBERS_FEATURE
    ),
    val timestamp: TextStyle = TextStyle(
        fontFamily = ZeroMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = TABULAR_NUMBERS_FEATURE
    ),
    val dateCompact: TextStyle = TextStyle(
        fontFamily = ZeroMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
        fontFeatureSettings = TABULAR_NUMBERS_FEATURE
    ),
    val priorityTag: TextStyle = TextStyle(
        fontFamily = ZeroMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp,
        fontFeatureSettings = TABULAR_NUMBERS_FEATURE
    )
)

val LocalZeroTabularTypography = staticCompositionLocalOf {
    ZeroTabularTypography()
}
