package com.zero.todo.core.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Standard Spring physics for ZERO: crisp, non-bouncy, mechanical feel
 * (`Spring.DampingRatioNoBouncy`, `Spring.StiffnessMediumLow`).
 */
object ZeroMotion {
    const val DAMPING_RATIO = Spring.DampingRatioNoBouncy
    const val STIFFNESS = Spring.StiffnessMediumLow

    fun <T> defaultSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = DAMPING_RATIO,
        stiffness = STIFFNESS,
        visibilityThreshold = visibilityThreshold
    )

    val FloatSpring: SpringSpec<Float> = defaultSpring()
    val DpSpring: SpringSpec<Dp> = defaultSpring()
    val IntOffsetSpring: SpringSpec<IntOffset> = defaultSpring()
    val IntSizeSpring: SpringSpec<IntSize> = defaultSpring()
    val ColorSpring: SpringSpec<Color> = defaultSpring()
}
