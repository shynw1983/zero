package com.zero.todo.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

val ZeroShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/**
 * ZERO Near-Future Minimalism theme.
 * Defaults to Dark Mode (`#09090B` Matte Obsidian) with Signal Orange (`#FF4D00`) accent.
 */
@Composable
fun ZeroTheme(
    darkTheme: Boolean = true,
    accent: ZeroAccent = ZeroAccent.SlateBlue,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) {
        zeroDarkPalette(accent)
    } else {
        zeroLightPalette(accent)
    }
    val tabularTypography = ZeroTabularTypography()

    CompositionLocalProvider(
        LocalZeroColors provides palette,
        LocalZeroAccent provides accent,
        LocalZeroTabularTypography provides tabularTypography,
        LocalZeroHapticsEnabled provides hapticsEnabled
    ) {
        MaterialTheme(
            colorScheme = palette.toMaterialColorScheme(),
            typography = ZeroTypography,
            shapes = ZeroShapes,
            content = content
        )
    }
}

object ZeroTheme {
    val colors: ZeroColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalZeroColors.current

    val accent: ZeroAccent
        @Composable
        @ReadOnlyComposable
        get() = LocalZeroAccent.current

    val tabularTypography: ZeroTabularTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalZeroTabularTypography.current
}
